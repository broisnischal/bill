import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { PlusIcon, PrinterIcon, SearchIcon } from "lucide-react";

import {
  InvoiceStatusBadge,
  InvoiceTypeBadge,
  IrdSyncBadge,
} from "#/components/invoice-badges.tsx";
import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Input } from "#/components/ui/input.tsx";
import { NativeSelect } from "#/components/ui/native-select.tsx";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "#/components/ui/table.tsx";
import { invoicesQueryOptions } from "#/lib/invoice/queries.ts";
import { formatBsLong, recentFiscalYears } from "#/lib/nepali/date.ts";

interface InvoiceSearch {
  fiscalYear?: string;
  status: "all" | "active" | "cancelled";
  search?: string;
  page: number;
}

export const Route = createFileRoute("/_auth/app/invoices/")({
  component: InvoiceListPage,
  validateSearch: (search: Record<string, unknown>): InvoiceSearch => ({
    fiscalYear: typeof search.fiscalYear === "string" ? search.fiscalYear : undefined,
    status: search.status === "active" || search.status === "cancelled" ? search.status : "all",
    search: typeof search.search === "string" && search.search ? search.search : undefined,
    page: Number(search.page) > 0 ? Number(search.page) : 1,
  }),
});

function InvoiceListPage() {
  const filters = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  const fiscalYears = recentFiscalYears();
  const query = useQuery(
    invoicesQueryOptions({
      fiscalYear: filters.fiscalYear,
      status: filters.status,
      search: filters.search,
      page: filters.page,
      pageSize: 25,
    }),
  );

  const setFilter = (patch: Partial<InvoiceSearch>) =>
    navigate({ search: (current) => ({ ...current, page: 1, ...patch }) });

  const total = query.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / 25));

  return (
    <div className="grid gap-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Invoices</h1>
          <p className="text-sm text-muted-foreground">
            {total} document{total === 1 ? "" : "s"} on record. Nothing here can be deleted.
          </p>
        </div>
        <Button render={<Link to="/app/invoices/new" />} nativeButton={false}>
          <PlusIcon aria-hidden="true" />
          New bill
        </Button>
      </header>

      <div className="flex flex-wrap gap-3">
        <div className="relative min-w-56 flex-1">
          <SearchIcon
            className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            defaultValue={filters.search ?? ""}
            placeholder="Bill number, buyer or PAN"
            className="pl-9"
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                setFilter({ search: event.currentTarget.value || undefined });
              }
            }}
          />
        </div>
        <div className="w-40">
          <NativeSelect
            value={filters.fiscalYear ?? ""}
            onChange={(event) => setFilter({ fiscalYear: event.target.value || undefined })}
            aria-label="Fiscal year"
          >
            <option value="">All fiscal years</option>
            {fiscalYears.map((year) => (
              <option key={year} value={year}>
                {year}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="w-36">
          <NativeSelect
            value={filters.status}
            onChange={(event) =>
              setFilter({ status: event.target.value as InvoiceSearch["status"] })
            }
            aria-label="Status"
          >
            <option value="all">All statuses</option>
            <option value="active">Active</option>
            <option value="cancelled">Cancelled</option>
          </NativeSelect>
        </div>
      </div>

      <div className="overflow-x-auto rounded-2xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Number</TableHead>
              <TableHead>Miti</TableHead>
              <TableHead>Buyer</TableHead>
              <TableHead className="text-right">Taxable</TableHead>
              <TableHead className="text-right">VAT</TableHead>
              <TableHead className="text-right">Total</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.isPending && (
              <TableRow>
                <TableCell colSpan={8} className="text-muted-foreground">
                  Loading...
                </TableCell>
              </TableRow>
            )}
            {query.data?.rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} className="text-muted-foreground">
                  No bills yet. Issue the first one.
                </TableCell>
              </TableRow>
            )}
            {query.data?.rows.map((invoice) => (
              <TableRow key={invoice.id}>
                <TableCell>
                  <Link
                    to="/app/invoices/$invoiceId"
                    params={{ invoiceId: invoice.id }}
                    search={{}}
                    className="font-mono text-xs hover:underline"
                  >
                    {invoice.invoiceNumber}
                  </Link>
                </TableCell>
                <TableCell className="whitespace-nowrap text-muted-foreground">
                  {formatBsLong(invoice.miti)}
                </TableCell>
                <TableCell>
                  <span className="block max-w-40 truncate">{invoice.buyerName}</span>
                  {invoice.buyerPan && (
                    <span className="font-mono text-xs text-muted-foreground">
                      {invoice.buyerPan}
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <Money paisa={invoice.taxableAmountPaisa} />
                </TableCell>
                <TableCell className="text-right">
                  <Money paisa={invoice.vatAmountPaisa} />
                </TableCell>
                <TableCell className="text-right font-medium">
                  <Money paisa={invoice.totalPaisa} />
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-1">
                    <InvoiceTypeBadge type={invoice.invoiceType} />
                    {invoice.status === "cancelled" && (
                      <InvoiceStatusBadge status={invoice.status} />
                    )}
                    {invoice.irdSyncStatus === "failed" && (
                      <IrdSyncBadge status={invoice.irdSyncStatus} />
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Print ${invoice.invoiceNumber}`}
                    onClick={() =>
                      window.open(
                        `/print/${invoice.id}?format=thermal80`,
                        "_blank",
                        "noopener,width=420,height=900",
                      )
                    }
                  >
                    <PrinterIcon aria-hidden="true" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {pageCount > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Page {filters.page} of {pageCount}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={filters.page <= 1}
              onClick={() =>
                navigate({ search: (current) => ({ ...current, page: current.page - 1 }) })
              }
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={filters.page >= pageCount}
              onClick={() =>
                navigate({ search: (current) => ({ ...current, page: current.page + 1 }) })
              }
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
