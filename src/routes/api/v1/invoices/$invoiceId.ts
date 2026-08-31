import { createFileRoute } from "@tanstack/react-router";
import { desc, eq } from "drizzle-orm";

import { ApiError, run, json, requireStore } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { invoice, invoiceAudit } from "#/lib/db/schema/index.ts";
import { loadInvoiceForPrint } from "#/lib/invoice/service.ts";

/** One bill with its lines, its trail and any credit note raised against it. */
export const Route = createFileRoute("/api/v1/invoices/$invoiceId")({
  server: {
    handlers: {
      GET: ({ request, params }) =>
        run(async () => {
          const context = await requireStore(request);
          const loaded = await loadInvoiceForPrint({
            storeId: context.store.id,
            invoiceId: params.invoiceId,
          });
          if (!loaded) throw new ApiError(404, "not_found", "That bill is not in this store");

          const [audits, creditNotes] = await Promise.all([
            db
              .select()
              .from(invoiceAudit)
              .where(eq(invoiceAudit.invoiceId, params.invoiceId))
              .orderBy(desc(invoiceAudit.at))
              .limit(50),
            db
              .select({
                id: invoice.id,
                invoiceNumber: invoice.invoiceNumber,
                issuedAt: invoice.issuedAt,
                totalPaisa: invoice.totalPaisa,
              })
              .from(invoice)
              .where(eq(invoice.refInvoiceId, params.invoiceId)),
          ]);

          return json({ ...loaded, audits, creditNotes });
        }),
    },
  },
});
