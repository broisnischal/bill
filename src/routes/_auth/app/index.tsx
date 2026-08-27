import { useQuery } from "@tanstack/react-query";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import {
  AlertTriangleIcon,
  ArrowRightIcon,
  CheckCircle2Icon,
  LoaderCircleIcon,
  PlusIcon,
} from "lucide-react";
import { useState } from "react";

import { InvoiceTypeBadge } from "#/components/invoice-badges.tsx";
import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import { toast } from "#/components/ui/toast.tsx";
import { salesAnalyticsQueryOptions } from "#/lib/analytics/queries.ts";
import { $retryIrdSync } from "#/lib/invoice/functions.ts";
import { invoicesQueryOptions, irdSyncStatusQueryOptions } from "#/lib/invoice/queries.ts";
import { fiscalYearFor, formatBsLong, recentFiscalYears } from "#/lib/nepali/date.ts";
import { storeQueryOptions } from "#/lib/store/queries.ts";

export const Route = createFileRoute("/_auth/app/")({
  component: DashboardPage,
});

function DashboardPage() {
  const [fiscalYear, setFiscalYear] = useState(fiscalYearFor(new Date()));
  const fiscalYears = recentFiscalYears();
  const queryClient = useQueryClient();

  const { data: membership } = useQuery(storeQueryOptions());
  const analytics = useQuery(salesAnalyticsQueryOptions(fiscalYear));
  const sync = useQuery(irdSyncStatusQueryOptions());
  const recent = useQuery(
    invoicesQueryOptions({ status: "all", page: 1, pageSize: 8, fiscalYear }),
  );

  const retryAll = useMutation({
    mutationFn: $retryIrdSync,
    onSuccess: (result) => {
      toast.add({ description: `Pushed ${result.pushed} document(s) to CBMS.` });
      queryClient.invalidateQueries({ queryKey: ["ird-sync-status"] });
      queryClient.invalidateQueries({ queryKey: ["invoices"] });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const summary = analytics.data?.summary;
  const pending = (sync.data?.counts.pending ?? 0) + (sync.data?.counts.failed ?? 0);

  return (
    <div className="grid gap-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {membership?.store.name ?? "Dashboard"}
          </h1>
          <p className="text-sm text-muted-foreground">
            Fiscal year {fiscalYear} · PAN {membership?.store.pan}
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="w-36">
            <NativeSelect
              value={fiscalYear}
              onChange={(event) => setFiscalYear(event.target.value)}
              aria-label="Fiscal year"
            >
              {fiscalYears.map((year) => (
                <option key={year} value={year}>
                  {year}
                </option>
              ))}
            </NativeSelect>
          </div>
          <Button render={<Link to="/app/invoices/new" />} nativeButton={false}>
            <PlusIcon aria-hidden="true" />
            New bill
          </Button>
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat
          label="Sales"
          value={<Money paisa={summary?.salesPaisa ?? 0} prefix />}
          hint="Net of credit notes"
        />
        <Stat
          label="VAT collected"
          value={<Money paisa={summary?.vatPaisa ?? 0} prefix />}
          hint="Payable to IRD"
        />
        <Stat
          label="Documents"
          value={String(summary?.documents ?? 0)}
          hint={`${summary?.cancelled ?? 0} cancelled · ${summary?.creditNotes ?? 0} credit notes`}
        />
        <Stat
          label="Average bill"
          value={<Money paisa={summary?.averageBillPaisa ?? 0} prefix />}
          hint="Per active document"
        />
      </div>

      <Card>
        <CardHeader className="flex-row items-center justify-between gap-3">
          <CardTitle className="text-base">IRD CBMS</CardTitle>
          {sync.data?.enabled && pending > 0 && (
            <Button
              variant="outline"
              size="sm"
              disabled={retryAll.isPending}
              onClick={() => retryAll.mutate({ data: undefined })}
            >
              {retryAll.isPending && (
                <LoaderCircleIcon className="animate-spin" aria-hidden="true" />
              )}
              Push {pending} queued
            </Button>
          )}
        </CardHeader>
        <CardContent className="text-sm">
          {!sync.data?.enabled ? (
            <p className="flex items-center gap-2 text-muted-foreground">
              <AlertTriangleIcon className="size-4" aria-hidden="true" />
              Real-time sync is off. Turn it on in{" "}
              <Link to="/app/settings" className="underline underline-offset-4">
                settings
              </Link>{" "}
              once the IRD has approved this software for your PAN.
            </p>
          ) : !sync.data.configured ? (
            <p className="flex items-center gap-2 text-destructive">
              <AlertTriangleIcon className="size-4" aria-hidden="true" />
              Sync is on but the CBMS username and password are missing.
            </p>
          ) : (
            <p className="flex items-center gap-2 text-muted-foreground">
              <CheckCircle2Icon className="size-4" aria-hidden="true" />
              {sync.data.counts.synced ?? 0} synced · {sync.data.counts.pending ?? 0} queued ·{" "}
              {sync.data.counts.failed ?? 0} failed
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-base">Recent bills</CardTitle>
            <Link
              to="/app/invoices"
              search={{ status: "all", page: 1 }}
              className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
            >
              All invoices
              <ArrowRightIcon className="size-3" aria-hidden="true" />
            </Link>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {recent.data?.rows.length === 0 && (
              <p className="text-muted-foreground">Nothing billed in this fiscal year yet.</p>
            )}
            {recent.data?.rows.map((invoice) => (
              <Link
                key={invoice.id}
                to="/app/invoices/$invoiceId"
                params={{ invoiceId: invoice.id }}
                search={{}}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl px-2 py-2 hover:bg-accent/60"
              >
                <span className="flex min-w-0 flex-col">
                  <span className="font-mono text-xs">{invoice.invoiceNumber}</span>
                  <span className="truncate text-muted-foreground">{invoice.buyerName}</span>
                </span>
                <span className="flex items-center gap-3">
                  <span className="text-xs text-muted-foreground">
                    {formatBsLong(invoice.miti)}
                  </span>
                  <InvoiceTypeBadge type={invoice.invoiceType} />
                  <Money paisa={invoice.totalPaisa} className="font-medium" />
                </span>
              </Link>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Last 30 days</CardTitle>
          </CardHeader>
          <CardContent>
            <DaySparkline data={analytics.data?.byDay ?? []} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <Card>
      <CardContent className="grid gap-1 pt-6">
        <span className="text-xs tracking-wide text-muted-foreground uppercase">{label}</span>
        <span className="text-2xl font-semibold tracking-tight">{value}</span>
        {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
      </CardContent>
    </Card>
  );
}

/** Daily takings as bars. Enough to spot a dead day without pulling in a chart library. */
function DaySparkline({ data }: { data: Array<{ day: string; salesPaisa: number }> }) {
  if (!data.length) return <p className="text-sm text-muted-foreground">No sales yet.</p>;
  const peak = Math.max(...data.map((entry) => entry.salesPaisa), 1);

  return (
    <div className="flex h-32 items-end gap-1">
      {data.map((entry) => (
        <div
          key={entry.day}
          className="max-w-8 min-w-1 flex-1 rounded-t bg-primary/70"
          style={{ height: `${Math.max(4, (entry.salesPaisa / peak) * 100)}%` }}
          title={`${entry.day}: Rs. ${(entry.salesPaisa / 100).toFixed(2)}`}
        />
      ))}
    </div>
  );
}
