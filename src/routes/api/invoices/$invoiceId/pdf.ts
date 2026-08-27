import { createFileRoute } from "@tanstack/react-router";

import { _getUser } from "#/lib/auth/functions.ts";
import { renderInvoicePdf } from "#/lib/invoice/pdf.ts";
import { printFormatSchema } from "#/lib/invoice/schema.ts";
import { archiveInvoicePdf, loadInvoiceForPrint } from "#/lib/invoice/service.ts";
import { getPdf } from "#/lib/storage/s3.ts";
import { findStoreForUser } from "#/lib/store/service.ts";

/**
 * Serves the PDF through the app rather than handing out a bucket URL, so the archive
 * stays private wherever it is hosted. The A4 copy comes from storage; an 80mm receipt
 * is rendered on the spot, since nobody keeps a paper roll in an archive.
 */
export const Route = createFileRoute("/api/invoices/$invoiceId/pdf")({
  server: {
    handlers: {
      GET: async ({ params, request }) => {
        const user = await _getUser();
        if (!user) return new Response("Unauthorized", { status: 401 });

        const membership = await findStoreForUser(user.id);
        if (!membership) return new Response("No store registered", { status: 403 });

        const url = new URL(request.url);
        const format = printFormatSchema.catch("a4").parse(url.searchParams.get("format"));

        const loaded = await loadInvoiceForPrint({
          storeId: membership.store.id,
          invoiceId: params.invoiceId,
        });
        if (!loaded) return new Response("Invoice not found", { status: 404 });

        const filename = `${loaded.invoice.invoiceNumber.replace(/[^\w.-]/g, "_")}-${format}.pdf`;

        let bytes: Uint8Array;
        if (format === "a4") {
          const key =
            loaded.invoice.pdfKey ??
            (await archiveInvoicePdf({ store: membership.store, invoiceId: loaded.invoice.id }))
              ?.pdfKey;
          if (!key) return new Response("PDF unavailable", { status: 500 });
          bytes = await getPdf(key);
        } else {
          const rendered = await renderInvoicePdf({
            store: membership.store,
            invoice: loaded.invoice,
            items: loaded.items,
            format,
            copyNumber: Math.max(1, loaded.invoice.printCount),
          });
          bytes = rendered.bytes;
        }

        return new Response(new Blob([new Uint8Array(bytes)], { type: "application/pdf" }), {
          headers: {
            "content-type": "application/pdf",
            "content-disposition": `inline; filename="${filename}"`,
            "cache-control": "private, max-age=60",
          },
        });
      },
    },
  },
});
