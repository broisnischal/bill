import { createFileRoute } from "@tanstack/react-router";

import { _getUser } from "#/lib/auth/functions.ts";
import { buildInvoiceDocument, renderPrintPage } from "#/lib/invoice/document.ts";
import { printFormatSchema } from "#/lib/invoice/schema.ts";
import { loadInvoiceForPrint, registerPrint } from "#/lib/invoice/service.ts";
import { findStoreForUser } from "#/lib/store/service.ts";

/**
 * The printable bill.
 *
 * A server route rather than a page, because the print has to be recorded before the
 * markup is handed over: the first copy is the original, and every later one comes out
 * stamped as a copy. The same HTML the PDF archive is rendered from is what the browser
 * prints, so an 80mm counter printer and the archived copy cannot disagree.
 */
export const Route = createFileRoute("/print/$invoiceId")({
  server: {
    handlers: {
      GET: async ({ params, request }) => {
        const user = await _getUser();
        if (!user) return new Response("Sign in to print", { status: 401 });

        const membership = await findStoreForUser(user.id);
        if (!membership) return new Response("No store registered", { status: 403 });

        const url = new URL(request.url);
        const format = printFormatSchema.catch("a4").parse(url.searchParams.get("format"));
        const autoPrint = url.searchParams.get("auto") !== "0";
        const countThisPrint = url.searchParams.get("count") !== "0";

        const loaded = await loadInvoiceForPrint({
          storeId: membership.store.id,
          invoiceId: params.invoiceId,
        });
        if (!loaded) return new Response("Invoice not found", { status: 404 });

        const printed = countThisPrint
          ? await registerPrint({
              storeId: membership.store.id,
              invoiceId: params.invoiceId,
              format,
              actor: {
                id: user.id,
                name: user.name,
                ipAddress:
                  request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? undefined,
                userAgent: request.headers.get("user-agent") ?? undefined,
              },
            })
          : loaded.invoice;

        const invoice = printed ?? loaded.invoice;
        const document = buildInvoiceDocument({
          store: membership.store,
          invoice,
          items: loaded.items,
          format,
          copyNumber: countThisPrint ? invoice.printCount : 1,
        });

        return new Response(renderPrintPage(document, autoPrint), {
          headers: {
            "content-type": "text/html; charset=utf-8",
            "cache-control": "no-store",
          },
        });
      },
    },
  },
});
