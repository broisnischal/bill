import { createFileRoute } from "@tanstack/react-router";
import { and, eq, gt } from "drizzle-orm";
import * as z from "zod";

import { run, json, parseBody, requireDevice, requireStore } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { customer, item } from "#/lib/db/schema/index.ts";
import type { InvoiceType } from "#/lib/db/schema/types.ts";
import { ensureLeases, LEASE_LOW_WATER, LeaseError } from "#/lib/invoice/lease.ts";
import { deviceInvoiceSchema, paymentSchema } from "#/lib/invoice/schema.ts";
import {
  cancelInvoice,
  createLeasedInvoice,
  InvoiceIntegrityError,
  recordPayment,
} from "#/lib/invoice/service.ts";
import { fiscalYearFor, toBsString } from "#/lib/nepali/date.ts";

/**
 * The whole of sync, in one request.
 *
 * A till in a Nepali shop is often on a connection that is up for ten seconds at a time,
 * so everything it needs to exchange happens in a single round trip: the bills it wrote
 * while offline go up, and the numbers, the catalogue and the store settings come back.
 * Every part is idempotent, which is what lets the app retry the same body until one
 * attempt lands.
 */

const invoiceTypes = ["tax_invoice", "abbreviated_tax_invoice", "credit_note"] as const;

const syncRequestSchema = z.object({
  /** Bills the device already printed. Capped so one sync stays inside a phone's patience. */
  invoices: z.array(deviceInvoiceSchema).max(100).default([]),
  cancellations: z
    .array(
      z.object({
        invoiceId: z.uuid(),
        reason: z.string().trim().min(5, "Give the reason the bill was cancelled").max(300),
      }),
    )
    .max(50)
    .default([]),
  /** Money received on the till, against bills that may have been issued days ago. */
  payments: z.array(paymentSchema).max(100).default([]),
  /** How many spare numbers the device wants to leave with, per series. */
  want: z.partialRecord(z.enum(invoiceTypes), z.int().min(0).max(500)).optional(),
  /** Catalogue cursor: send back what changed after this. Omit for a full pull. */
  catalogSince: z.iso.datetime().optional(),
});

/** Which of the three kinds of failure this was. */
function when(error: unknown) {
  if (error instanceof InvoiceIntegrityError) return "integrity" as const;
  if (error instanceof LeaseError) return "wrong_series" as const;
  return "sync_failed" as const;
}

export const Route = createFileRoute("/api/v1/sync")({
  server: {
    handlers: {
      POST: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          const device = await requireDevice(request, context);
          const body = await parseBody(request, syncRequestSchema);
          const now = new Date();

          /**
           * A business under review syncs, but does not bill.
           *
           * The endpoint stays open on purpose: this is how a till learns it has been
           * approved, and how it pulls the products and buyers the shop is setting up
           * while it waits. What it does not get is numbers, and any bill it sends is
           * refused with a reason rather than silently dropped.
           */
          const approved = context.store.status === "approved";
          const heldReason =
            context.store.status === "rejected"
              ? (context.store.reviewNote ??
                "The business was not approved. Send what was asked for again.")
              : "The business is still being reviewed. Billing opens once it is approved.";

          // Bills first: a cancellation in the same batch may be aimed at one of them.
          const results = [];
          for (const input of body.invoices) {
            if (!approved) {
              results.push({
                id: input.id,
                status: "rejected" as const,
                error: { code: "store_not_approved", message: heldReason },
              });
              continue;
            }
            try {
              const { invoice, filed } = await createLeasedInvoice({
                store: context.store,
                actor: context.actor,
                deviceId: device.id,
                input,
                now,
              });
              results.push({
                id: input.id,
                status: filed ? ("filed" as const) : ("duplicate" as const),
                invoiceNumber: invoice.invoiceNumber,
              });
            } catch (error) {
              // A rejected bill is already on paper in someone's hand, so it is reported
              // back rather than retried: the shop has to reverse it with a credit note.
              // A number from a block this store never held is as final as arithmetic
              // that does not add up: neither becomes valid by waiting.
              // Three different things, and a shop needs to be told which. Arithmetic
              // that disagrees is a bill to reverse. A number from a block this store
              // never held is a bill that belongs to a different series entirely, and
              // saying "raise a credit note" for that would be wrong advice. Anything
              // else is a bad line and will be tried again.
              const code = when(error);
              results.push({
                id: input.id,
                status: code === "sync_failed" ? ("failed" as const) : ("rejected" as const),
                error: {
                  code,
                  message: error instanceof Error ? error.message : "Could not file this bill",
                  detail:
                    error instanceof InvoiceIntegrityError || error instanceof LeaseError
                      ? error.detail
                      : undefined,
                },
              });
            }
          }

          const cancellations = [];
          for (const entry of body.cancellations) {
            try {
              await cancelInvoice({
                store: context.store,
                invoiceId: entry.invoiceId,
                reason: entry.reason,
                actor: context.actor,
              });
              cancellations.push({ invoiceId: entry.invoiceId, status: "cancelled" as const });
            } catch (error) {
              cancellations.push({
                invoiceId: entry.invoiceId,
                status: "failed" as const,
                message: error instanceof Error ? error.message : "Could not cancel this bill",
              });
            }
          }

          // Payments come after the bills, since one may be against a bill in this batch.
          const payments = [];
          for (const entry of body.payments) {
            try {
              const { filed } = await recordPayment({
                store: context.store,
                actor: context.actor,
                input: entry,
                deviceId: device.id,
              });
              payments.push({
                id: entry.id,
                status: filed ? ("filed" as const) : ("duplicate" as const),
              });
            } catch (error) {
              payments.push({
                id: entry.id,
                status: "failed" as const,
                message: error instanceof Error ? error.message : "Could not file this payment",
              });
            }
          }

          // Top up numbers only after the used ones are filed, so the watermark is current.
          const fiscalYear = fiscalYearFor(now);
          const wanted = approved ? (body.want ?? { tax_invoice: LEASE_LOW_WATER }) : {};
          const leases = [];
          for (const [type, want] of Object.entries(wanted)) {
            if (!want) continue;
            leases.push(
              ...(await ensureLeases({
                storeId: context.store.id,
                deviceId: device.id,
                fiscalYear,
                invoiceType: type as InvoiceType,
                want,
                now,
              })),
            );
          }

          const since = body.catalogSince ? new Date(body.catalogSince) : null;
          const [items, customers] = await Promise.all([
            db
              .select()
              .from(item)
              .where(
                since
                  ? and(eq(item.storeId, context.store.id), gt(item.updatedAt, since))
                  : eq(item.storeId, context.store.id),
              ),
            db
              .select()
              .from(customer)
              .where(
                since
                  ? and(eq(customer.storeId, context.store.id), gt(customer.updatedAt, since))
                  : eq(customer.storeId, context.store.id),
              ),
          ]);

          return json({
            serverTime: now.toISOString(),
            fiscalYear,
            miti: toBsString(now),
            results,
            cancellations,
            leases,
            catalog: { items, customers },
            /** Where review has got to, so a till can put itself behind the gate. */
            review: {
              status: context.store.status,
              note: context.store.reviewNote,
            },
            // Settings changed in the browser reach the till on its next sync.
            store: context.store,
          });
        }),
    },
  },
});
