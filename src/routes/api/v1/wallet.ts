import { createFileRoute } from "@tanstack/react-router";
import { desc, eq } from "drizzle-orm";
import * as z from "zod";

import { ApiError, run, json, parseBody, requireUser } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { invoice, savedBill, store } from "#/lib/db/schema/index.ts";

const saveSchema = z.object({ token: z.string().regex(/^[a-f0-9]{32}$/, "Malformed bill code") });

/**
 * Customer mode: the bills a shopper has kept.
 *
 * Filing a bill needs an account, because the point is that it is still there on a new
 * phone. Nothing here can reach a store; a shopper only ever sees documents they were
 * handed and scanned.
 */
export const Route = createFileRoute("/api/v1/wallet")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);

          // Two ways a bill lands here: the shopper scanned the QR on it, or the shop
          // scanned their card and the bill was made out to them from the start.
          const linked = await db
            .select({
              savedAt: invoice.createdAt,
              invoice: {
                id: invoice.id,
                invoiceNumber: invoice.invoiceNumber,
                shareToken: invoice.shareToken,
                issuedAt: invoice.issuedAt,
                miti: invoice.miti,
                totalPaisa: invoice.totalPaisa,
                status: invoice.status,
                paymentMethod: invoice.paymentMethod,
              },
              seller: { name: store.name, address: store.address, pan: store.pan },
            })
            .from(invoice)
            .innerJoin(store, eq(store.id, invoice.storeId))
            .where(eq(invoice.shopperUserId, user.id))
            .orderBy(desc(invoice.issuedAt))
            .limit(200);

          const rows = await db
            .select({
              savedAt: savedBill.savedAt,
              invoice: {
                id: invoice.id,
                invoiceNumber: invoice.invoiceNumber,
                shareToken: invoice.shareToken,
                issuedAt: invoice.issuedAt,
                miti: invoice.miti,
                totalPaisa: invoice.totalPaisa,
                status: invoice.status,
                paymentMethod: invoice.paymentMethod,
              },
              seller: { name: store.name, address: store.address, pan: store.pan },
            })
            .from(savedBill)
            .innerJoin(invoice, eq(invoice.id, savedBill.invoiceId))
            .innerJoin(store, eq(store.id, invoice.storeId))
            .where(eq(savedBill.userId, user.id))
            .orderBy(desc(savedBill.savedAt))
            .limit(200);

          // A bill can be both linked and saved; the shopper should see it once.
          const seen = new Set(rows.map((row) => row.invoice.id));
          const bills = [...rows, ...linked.filter((row) => !seen.has(row.invoice.id))].sort(
            (a, b) => b.invoice.issuedAt.getTime() - a.invoice.issuedAt.getTime(),
          );

          return json({ bills });
        }),

      POST: ({ request }) =>
        run(async () => {
          const user = await requireUser(request);
          const { token } = await parseBody(request, saveSchema);

          const [found] = await db
            .select({ id: invoice.id })
            .from(invoice)
            .where(eq(invoice.shareToken, token));
          if (!found) throw new ApiError(404, "not_found", "That bill could not be found");

          // Scanning the same QR twice keeps one copy rather than failing.
          await db
            .insert(savedBill)
            .values({ userId: user.id, invoiceId: found.id })
            .onConflictDoNothing();

          return json({ saved: true, invoiceId: found.id }, 201);
        }),
    },
  },
});
