import { createFileRoute, notFound } from "@tanstack/react-router";
import { createServerFn } from "@tanstack/react-start";
import { eq } from "drizzle-orm";
import * as z from "zod";

import { db } from "#/lib/db/index.ts";
import { invoice, invoiceItem, store } from "#/lib/db/schema/index.ts";
import { formatPaisa } from "#/lib/nepali/money.ts";

/**
 * A bill, opened from the QR printed on it.
 *
 * The paper carries a link, and until now that link went nowhere: the QR named a host
 * that answered nothing and a path no route served, so a buyer who scanned their receipt
 * with the phone's own camera got an error page. This is that page.
 *
 * It is public for the same reason the API behind it is: whoever holds the paper is
 * already reading these figures. Nothing here is not printed on the bill itself.
 */
const loadBill = createServerFn({ method: "GET" })
  .validator(z.object({ token: z.string().min(1) }))
  .handler(async ({ data }) => {
    const [row] = await db
      .select({
        invoice,
        seller: {
          name: store.name,
          nameNepali: store.nameNepali,
          pan: store.pan,
          address: store.address,
          phone: store.phone,
        },
      })
      .from(invoice)
      .innerJoin(store, eq(store.id, invoice.storeId))
      .where(eq(invoice.shareToken, data.token));

    if (!row) return null;

    const items = await db
      .select()
      .from(invoiceItem)
      .where(eq(invoiceItem.invoiceId, row.invoice.id))
      .orderBy(invoiceItem.lineNo);

    return { invoice: row.invoice, seller: row.seller, items };
  });

export const Route = createFileRoute("/b/$token")({
  loader: async ({ params }) => {
    const bill = await loadBill({ data: { token: params.token } });
    if (!bill) throw notFound();
    return bill;
  },
  component: PublicBill,
  notFoundComponent: () => (
    <main className="mx-auto max-w-md p-6 text-center">
      <h1 className="text-xl font-semibold">That bill could not be found</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        The code may have been mistyped, or the bill has not reached the server yet. A bill written
        while the till was offline appears here once the shop is back on signal.
      </p>
    </main>
  ),
});

function PublicBill() {
  const { invoice: bill, seller, items } = Route.useLoaderData();

  return (
    <main className="mx-auto max-w-md p-4">
      <header className="text-center">
        <h1 className="text-lg font-semibold">{seller.nameNepali ?? seller.name}</h1>
        <p className="text-sm text-muted-foreground">{seller.address}</p>
        <p className="text-sm text-muted-foreground">PAN {seller.pan}</p>
      </header>

      <dl className="mt-4 grid grid-cols-2 gap-1 text-sm">
        <dt className="text-muted-foreground">Bill no.</dt>
        <dd className="text-right font-medium">{bill.invoiceNumber}</dd>
        <dt className="text-muted-foreground">Miti</dt>
        <dd className="text-right">{bill.miti}</dd>
        <dt className="text-muted-foreground">Buyer</dt>
        <dd className="text-right">{bill.buyerName}</dd>
      </dl>

      <table className="mt-4 w-full text-sm">
        <thead>
          <tr className="border-b text-left">
            <th className="py-1 font-medium">Item</th>
            <th className="py-1 text-right font-medium">Qty</th>
            <th className="py-1 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody>
          {items.map((line) => (
            <tr key={line.id} className="border-b last:border-0">
              <td className="py-1">{line.description}</td>
              <td className="py-1 text-right tabular-nums">
                {(line.quantityMilli / 1000).toLocaleString()} {line.unit}
              </td>
              <td className="py-1 text-right tabular-nums">{formatPaisa(line.lineTotalPaisa)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <dl className="mt-4 space-y-1 text-sm">
        <Row label="Taxable" value={formatPaisa(bill.taxableAmountPaisa)} />
        {bill.nonTaxableAmountPaisa > 0 && (
          <Row label="Exempt" value={formatPaisa(bill.nonTaxableAmountPaisa)} />
        )}
        {bill.vatAmountPaisa > 0 && (
          <Row label={`VAT ${bill.vatRateBp / 100}%`} value={formatPaisa(bill.vatAmountPaisa)} />
        )}
        <Row label="Total" value={formatPaisa(bill.totalPaisa)} strong />
      </dl>

      <p className="mt-4 text-center text-xs text-muted-foreground">{bill.amountInWords}</p>
    </main>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between ${strong ? "border-t pt-1 font-semibold" : ""}`}>
      <dt>{label}</dt>
      <dd className="tabular-nums">Rs {value}</dd>
    </div>
  );
}
