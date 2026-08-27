import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { DownloadIcon } from "lucide-react";
import { useState } from "react";

import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "#/components/ui/table.tsx";
import { salesAnalyticsQueryOptions } from "#/lib/analytics/queries.ts";
import { salesRegisterQueryOptions } from "#/lib/invoice/queries.ts";
import { fiscalYearFor, recentFiscalYears } from "#/lib/nepali/date.ts";
import { formatPaisa, formatQuantity } from "#/lib/nepali/money.ts";

export const Route = createFileRoute("/_auth/app/reports")({
  component: ReportsPage,
});

function ReportsPage() {
  const [fiscalYear, setFiscalYear] = useState(fiscalYearFor(new Date()));
  const analytics = useQuery(salesAnalyticsQueryOptions(fiscalYear));
  const register = useQuery(salesRegisterQueryOptions(fiscalYear));

  const summary = analytics.data?.summary;

  /** The sales book, in the column order an accountant expects to paste into a return. */
  const downloadCsv = () => {
    if (!register.data?.length) return;
    const header = [
      "Invoice number",
      "Type",
      "Miti (BS)",
      "Date (AD)",
      "Buyer",
      "Buyer PAN",
      "Status",
      "Taxable",
      "Exempt",
      "VAT",
      "Total",
      "CBMS",
    ];
    const rows = register.data.map((row) => [
      row.invoiceNumber,
      row.invoiceType,
      row.miti,
      new Date(row.issuedAt).toISOString().slice(0, 10),
      row.buyerName,
      row.buyerPan ?? "",
      row.status,
      formatPaisa(row.taxableAmountPaisa).replaceAll(",", ""),
      formatPaisa(row.nonTaxableAmountPaisa).replaceAll(",", ""),
      formatPaisa(row.vatAmountPaisa).replaceAll(",", ""),
      formatPaisa(row.totalPaisa).replaceAll(",", ""),
      row.irdSyncStatus,
    ]);

    const csv = [header, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(","))
      .join("\n");

    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `sales-register-${fiscalYear}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="grid gap-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Reports</h1>
          <p className="text-sm text-muted-foreground">
            Sales, VAT and the register for fiscal year {fiscalYear}.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="w-36">
            <NativeSelect
              value={fiscalYear}
              onChange={(event) => setFiscalYear(event.target.value)}
              aria-label="Fiscal year"
            >
              {recentFiscalYears().map((year) => (
                <option key={year} value={year}>
                  {year}
                </option>
              ))}
            </NativeSelect>
          </div>
          <Button variant="outline" onClick={downloadCsv} disabled={!register.data?.length}>
            <DownloadIcon aria-hidden="true" />
            Sales register CSV
          </Button>
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <Stat label="Sales" paisa={summary?.salesPaisa ?? 0} />
        <Stat label="Taxable" paisa={summary?.taxablePaisa ?? 0} />
        <Stat label="Exempt" paisa={summary?.exemptPaisa ?? 0} />
        <Stat label="VAT" paisa={summary?.vatPaisa ?? 0} />
        <Stat label="Discount given" paisa={summary?.discountPaisa ?? 0} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">By Nepali month</CardTitle>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">Bills</TableHead>
                  <TableHead className="text-right">Taxable</TableHead>
                  <TableHead className="text-right">VAT</TableHead>
                  <TableHead className="text-right">Sales</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {analytics.data?.byMonth.map((month) => (
                  <TableRow key={month.bsMonth}>
                    <TableCell>
                      {month.label}
                      <span className="ml-2 font-mono text-xs text-muted-foreground">
                        {month.bsMonth}
                      </span>
                    </TableCell>
                    <TableCell className="text-right">{month.documents}</TableCell>
                    <TableCell className="text-right">
                      <Money paisa={month.taxablePaisa} />
                    </TableCell>
                    <TableCell className="text-right">
                      <Money paisa={month.vatPaisa} />
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      <Money paisa={month.salesPaisa} />
                    </TableCell>
                  </TableRow>
                ))}
                {!analytics.data?.byMonth.length && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-muted-foreground">
                      Nothing billed yet.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">How customers paid</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            {analytics.data?.byPaymentMethod.map((method) => (
              <div key={method.paymentMethod} className="grid gap-1">
                <div className="flex items-baseline justify-between">
                  <span className="capitalize">{method.paymentMethod}</span>
                  <Money paisa={method.salesPaisa} />
                </div>
                <div className="h-1.5 rounded-full bg-muted">
                  <div
                    className="h-1.5 rounded-full bg-primary"
                    style={{
                      width: `${Math.max(
                        2,
                        (method.salesPaisa / Math.max(1, summary?.salesPaisa ?? 1)) * 100,
                      )}%`,
                    }}
                  />
                </div>
              </div>
            ))}
            {!analytics.data?.byPaymentMethod.length && (
              <p className="text-muted-foreground">Nothing billed yet.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Top items</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {analytics.data?.topItems.map((item) => (
              <div key={item.description} className="flex items-baseline justify-between gap-3">
                <span className="truncate">{item.description}</span>
                <span className="flex shrink-0 items-baseline gap-3 text-muted-foreground">
                  <span>{formatQuantity(item.quantityMilli)}</span>
                  <Money paisa={item.amountPaisa} className="text-foreground" />
                </span>
              </div>
            ))}
            {!analytics.data?.topItems.length && (
              <p className="text-muted-foreground">Nothing billed yet.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Top customers</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {analytics.data?.topCustomers.map((customer) => (
              <div
                key={`${customer.buyerName}-${customer.buyerPan ?? ""}`}
                className="flex items-baseline justify-between gap-3"
              >
                <span className="truncate">
                  {customer.buyerName}
                  {customer.buyerPan && (
                    <span className="ml-2 font-mono text-xs text-muted-foreground">
                      {customer.buyerPan}
                    </span>
                  )}
                </span>
                <span className="flex shrink-0 items-baseline gap-3 text-muted-foreground">
                  <span>{customer.documents} bills</span>
                  <Money paisa={customer.salesPaisa} className="text-foreground" />
                </span>
              </div>
            ))}
            {!analytics.data?.topCustomers.length && (
              <p className="text-muted-foreground">Nothing billed yet.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Sales register</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Miti</TableHead>
                <TableHead>Buyer</TableHead>
                <TableHead>PAN</TableHead>
                <TableHead className="text-right">Taxable</TableHead>
                <TableHead className="text-right">VAT</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {register.data?.map((row) => (
                <TableRow key={row.invoiceNumber}>
                  <TableCell className="font-mono text-xs">{row.invoiceNumber}</TableCell>
                  <TableCell className="text-muted-foreground">{row.miti}</TableCell>
                  <TableCell className="max-w-40 truncate">{row.buyerName}</TableCell>
                  <TableCell className="font-mono text-xs">{row.buyerPan ?? "-"}</TableCell>
                  <TableCell className="text-right">
                    <Money paisa={row.taxableAmountPaisa} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Money paisa={row.vatAmountPaisa} />
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    <Money paisa={row.totalPaisa} />
                  </TableCell>
                  <TableCell className="text-muted-foreground capitalize">{row.status}</TableCell>
                </TableRow>
              ))}
              {!register.data?.length && (
                <TableRow>
                  <TableCell colSpan={8} className="text-muted-foreground">
                    No documents in this fiscal year.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

function Stat({ label, paisa }: { label: string; paisa: number }) {
  return (
    <Card>
      <CardContent className="grid gap-1 pt-6">
        <span className="text-xs tracking-wide text-muted-foreground uppercase">{label}</span>
        <span className="text-xl font-semibold tracking-tight">
          <Money paisa={paisa} prefix />
        </span>
      </CardContent>
    </Card>
  );
}
