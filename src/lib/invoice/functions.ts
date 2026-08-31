import { createServerFn } from "@tanstack/react-start";
import { getRequest, setResponseStatus } from "@tanstack/react-start/server";
import { and, count, desc, eq, gte, like, lt, or, sql } from "drizzle-orm";
import * as z from "zod";

import { db } from "#/lib/db/index.ts";
import { invoice, invoiceAudit, invoiceItem } from "#/lib/db/schema/index.ts";
import { fiscalYearRange } from "#/lib/nepali/date.ts";
import { storeAdminMiddleware, storeMiddleware } from "#/lib/store/middleware.ts";

import {
  cancelInvoiceSchema,
  createInvoiceSchema,
  creditNoteSchema,
  invoiceListSchema,
} from "./schema";
import {
  archiveInvoicePdf,
  cancelInvoice,
  createCreditNote,
  createInvoice,
  loadInvoiceForPrint,
  pendingIrdInvoices,
  syncInvoiceToIrd,
  type Actor,
} from "./service";

/** Who did it and from where, for the audit trail. */
function currentActor(user: { id: string; name: string }): Actor {
  const request = getRequest();
  return {
    id: user.id,
    name: user.name,
    ipAddress:
      request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
      request.headers.get("x-real-ip") ??
      undefined,
    userAgent: request.headers.get("user-agent") ?? undefined,
  };
}

export const $createInvoice = createServerFn({ method: "POST" })
  .middleware([storeMiddleware])
  .validator(createInvoiceSchema)
  .handler(async ({ data, context }) =>
    createInvoice({ store: context.store, actor: currentActor(context.user), input: data }),
  );

export const $listInvoices = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(invoiceListSchema)
  .handler(async ({ data, context }) => {
    const filters = [eq(invoice.storeId, context.store.id)];

    if (data.fiscalYear) filters.push(eq(invoice.fiscalYear, data.fiscalYear));
    if (data.status !== "all") filters.push(eq(invoice.status, data.status));
    if (data.search) {
      const term = `%${data.search}%`;
      filters.push(
        or(
          like(invoice.invoiceNumber, term),
          like(invoice.buyerName, term),
          like(invoice.buyerPan, term),
        )!,
      );
    }

    const where = and(...filters);
    const [{ total }] = await db.select({ total: count() }).from(invoice).where(where);
    const rows = await db
      .select()
      .from(invoice)
      .where(where)
      .orderBy(desc(invoice.issuedAt), desc(invoice.sequence))
      .limit(data.pageSize)
      .offset((data.page - 1) * data.pageSize);

    return { rows, total, page: data.page, pageSize: data.pageSize };
  });

export const $getInvoice = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(z.object({ invoiceId: z.string().min(1) }))
  .handler(async ({ data, context }) => {
    const loaded = await loadInvoiceForPrint({
      storeId: context.store.id,
      invoiceId: data.invoiceId,
    });
    if (!loaded) {
      setResponseStatus(404);
      throw new Error("Invoice not found");
    }

    const audits = await db
      .select()
      .from(invoiceAudit)
      .where(eq(invoiceAudit.invoiceId, data.invoiceId))
      .orderBy(desc(invoiceAudit.at))
      .limit(50);

    const creditNotes = await db
      .select({
        id: invoice.id,
        invoiceNumber: invoice.invoiceNumber,
        issuedAt: invoice.issuedAt,
        totalPaisa: invoice.totalPaisa,
      })
      .from(invoice)
      .where(eq(invoice.refInvoiceId, data.invoiceId));

    return { ...loaded, audits, creditNotes, store: context.store };
  });

export const $cancelInvoice = createServerFn({ method: "POST" })
  .middleware([storeAdminMiddleware])
  .validator(cancelInvoiceSchema)
  .handler(async ({ data, context }) =>
    cancelInvoice({
      store: context.store,
      invoiceId: data.invoiceId,
      reason: data.reason,
      actor: currentActor(context.user),
    }),
  );

export const $createCreditNote = createServerFn({ method: "POST" })
  .middleware([storeAdminMiddleware])
  .validator(creditNoteSchema)
  .handler(async ({ data, context }) =>
    createCreditNote({
      store: context.store,
      invoiceId: data.invoiceId,
      reason: data.reason,
      actor: currentActor(context.user),
    }),
  );

/** A short-lived link to the archived copy, rebuilding it if the archive is missing. */
export const $getInvoicePdfUrl = createServerFn({ method: "POST" })
  .middleware([storeMiddleware])
  .validator(z.object({ invoiceId: z.string().min(1) }))
  .handler(async ({ data, context }) => {
    const loaded = await loadInvoiceForPrint({
      storeId: context.store.id,
      invoiceId: data.invoiceId,
    });
    if (!loaded) {
      setResponseStatus(404);
      throw new Error("Invoice not found");
    }

    const key =
      loaded.invoice.pdfKey ??
      (
        await archiveInvoicePdf({
          store: context.store,
          invoiceId: data.invoiceId,
          actor: currentActor(context.user),
        })
      )?.pdfKey;

    if (!key) throw new Error("Could not archive the PDF for this invoice");
    // R2 has no presigned URLs, so the download goes back through the app route that
    // already streams the archived bytes and checks the caller owns the bill.
    return { url: `/api/invoices/${data.invoiceId}/pdf`, key };
  });

export const $retryIrdSync = createServerFn({ method: "POST" })
  .middleware([storeMiddleware])
  .validator(z.object({ invoiceId: z.string().optional() }).optional())
  .handler(async ({ data, context }) => {
    if (data?.invoiceId) {
      const row = await syncInvoiceToIrd({
        store: context.store,
        invoiceId: data.invoiceId,
        force: true,
      });
      return { pushed: row ? 1 : 0, rows: row ? [row] : [] };
    }

    const pending = await pendingIrdInvoices(context.store.id);
    const rows = [];
    for (const row of pending) {
      const synced = await syncInvoiceToIrd({ store: context.store, invoiceId: row.id });
      if (synced) rows.push(synced);
    }
    return { pushed: rows.length, rows };
  });

/** Counts for the dashboard's compliance strip. */
export const $irdSyncStatus = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .handler(async ({ context }) => {
    const rows = await db
      .select({ status: invoice.irdSyncStatus, total: count() })
      .from(invoice)
      .where(eq(invoice.storeId, context.store.id))
      .groupBy(invoice.irdSyncStatus);

    return {
      enabled: context.store.cbmsEnabled && context.store.taxpayerType === "vat",
      configured: Boolean(context.store.cbmsUsername && context.store.cbmsPasswordEncrypted),
      counts: Object.fromEntries(rows.map((row) => [row.status, row.total])),
    };
  });

/**
 * The sales register for a fiscal year: every line of every bill, which is the shape
 * a tax office asks for and the one an accountant pastes into a return.
 */
export const $salesRegister = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(z.object({ fiscalYear: z.string() }))
  .handler(async ({ data, context }) => {
    const { start, end } = fiscalYearRange(data.fiscalYear);
    return db
      .select({
        invoiceNumber: invoice.invoiceNumber,
        invoiceType: invoice.invoiceType,
        miti: invoice.miti,
        issuedAt: invoice.issuedAt,
        buyerName: invoice.buyerName,
        buyerPan: invoice.buyerPan,
        status: invoice.status,
        taxableAmountPaisa: invoice.taxableAmountPaisa,
        nonTaxableAmountPaisa: invoice.nonTaxableAmountPaisa,
        vatAmountPaisa: invoice.vatAmountPaisa,
        totalPaisa: invoice.totalPaisa,
        irdSyncStatus: invoice.irdSyncStatus,
        lines: sql<number>`(select count(*) from ${invoiceItem} where ${invoiceItem.invoiceId} = ${invoice.id})`,
      })
      .from(invoice)
      .where(
        and(
          eq(invoice.storeId, context.store.id),
          gte(invoice.issuedAt, start),
          lt(invoice.issuedAt, end),
        ),
      )
      .orderBy(invoice.issuedAt);
  });
