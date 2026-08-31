import { createFileRoute } from "@tanstack/react-router";
import { eq } from "drizzle-orm";

import { ApiError, run, json } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { invoice, invoiceItem, store } from "#/lib/db/schema/index.ts";

/**
 * A bill, looked up by the token in the QR code printed on it.
 *
 * This is open on purpose: whoever is holding the paper is entitled to read it, and
 * requiring the shopper to sign in before they can even see what they scanned would
 * lose most of them. Only what is printed on the bill comes back, so the token grants
 * nothing the reader is not already looking at.
 */
export const Route = createFileRoute("/api/v1/bills/$token")({
  server: {
    handlers: {
      GET: ({ params }) =>
        run(async () => {
          const [row] = await db
            .select({
              invoice,
              seller: {
                name: store.name,
                nameNepali: store.nameNepali,
                pan: store.pan,
                taxpayerType: store.taxpayerType,
                address: store.address,
                phone: store.phone,
              },
            })
            .from(invoice)
            .innerJoin(store, eq(store.id, invoice.storeId))
            .where(eq(invoice.shareToken, params.token));

          if (!row) throw new ApiError(404, "not_found", "That bill could not be found");

          const items = await db
            .select()
            .from(invoiceItem)
            .where(eq(invoiceItem.invoiceId, row.invoice.id))
            .orderBy(invoiceItem.lineNo);

          return json({ invoice: row.invoice, seller: row.seller, items });
        }),
    },
  },
});
