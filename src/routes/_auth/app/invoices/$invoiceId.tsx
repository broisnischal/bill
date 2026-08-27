import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import {
  ArrowLeftIcon,
  BanIcon,
  DownloadIcon,
  LoaderCircleIcon,
  PrinterIcon,
  ReceiptIcon,
  RefreshCwIcon,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";

import {
  InvoiceStatusBadge,
  InvoiceTypeBadge,
  IrdSyncBadge,
} from "#/components/invoice-badges.tsx";
import { Money } from "#/components/money.tsx";
import { Button } from "#/components/ui/button.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "#/components/ui/card.tsx";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "#/components/ui/dialog.tsx";
import { Label } from "#/components/ui/label.tsx";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "#/components/ui/table.tsx";
import { Textarea } from "#/components/ui/textarea.tsx";
import { toast } from "#/components/ui/toast.tsx";
import type { PrintFormat } from "#/lib/db/schema/types.ts";
import { $cancelInvoice, $createCreditNote, $retryIrdSync } from "#/lib/invoice/functions.ts";
import { invoiceQueryOptions } from "#/lib/invoice/queries.ts";
import { formatBsLong, toAdDateString, toNptTimeString } from "#/lib/nepali/date.ts";
import { formatQuantity } from "#/lib/nepali/money.ts";

export const Route = createFileRoute("/_auth/app/invoices/$invoiceId")({
  component: InvoiceDetailPage,
  validateSearch: (search: Record<string, unknown>): { print?: PrintFormat } => ({
    print: search.print === "a4" || search.print === "thermal80" ? search.print : undefined,
  }),
});

function openPrintWindow(invoiceId: string, format: PrintFormat) {
  window.open(`/print/${invoiceId}?format=${format}`, "_blank", "noopener,width=900,height=1000");
}

function InvoiceDetailPage() {
  const { invoiceId } = Route.useParams();
  const { print } = Route.useSearch();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const invoiceQuery = useQuery(invoiceQueryOptions(invoiceId));
  const autoPrinted = useRef(false);

  // Coming straight from the biller: open the printer once, then drop the flag so a
  // refresh does not silently count another copy.
  useEffect(() => {
    if (!print || autoPrinted.current) return;
    autoPrinted.current = true;
    openPrintWindow(invoiceId, print);
    navigate({ to: ".", params: { invoiceId }, search: {}, replace: true });
  }, [print, invoiceId, navigate]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["invoice", invoiceId] });
    queryClient.invalidateQueries({ queryKey: ["invoices"] });
    queryClient.invalidateQueries({ queryKey: ["sales-analytics"] });
    queryClient.invalidateQueries({ queryKey: ["ird-sync-status"] });
  };

  const cancelMutation = useMutation({
    mutationFn: $cancelInvoice,
    onSuccess: () => {
      toast.add({ description: "Bill cancelled. It stays on record with its reason." });
      invalidate();
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const creditNoteMutation = useMutation({
    mutationFn: $createCreditNote,
    onSuccess: (note) => {
      toast.add({ description: `Credit note ${note.invoiceNumber} issued.` });
      invalidate();
      navigate({ to: "/app/invoices/$invoiceId", params: { invoiceId: note.id }, search: {} });
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  const retryMutation = useMutation({
    mutationFn: $retryIrdSync,
    onSuccess: (result) => {
      toast.add({ description: `Pushed ${result.pushed} document(s) to CBMS.` });
      invalidate();
    },
    onError: (error: Error) => toast.add({ type: "error", description: error.message }),
  });

  if (invoiceQuery.isPending) {
    return <p className="text-sm text-muted-foreground">Loading bill...</p>;
  }
  if (invoiceQuery.isError || !invoiceQuery.data) {
    return <p className="text-sm text-destructive">This bill could not be loaded.</p>;
  }

  const { invoice, items, audits, creditNotes, store } = invoiceQuery.data;
  const isCreditNote = invoice.invoiceType === "credit_note";

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="grid gap-2">
          <Link
            to="/app/invoices"
            search={{ status: "all", page: 1 }}
            className="flex w-fit items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
          >
            <ArrowLeftIcon className="size-3" aria-hidden="true" />
            All invoices
          </Link>
          <h1 className="font-mono text-2xl font-semibold tracking-tight">
            {invoice.invoiceNumber}
          </h1>
          <div className="flex flex-wrap items-center gap-2">
            <InvoiceTypeBadge type={invoice.invoiceType} />
            <InvoiceStatusBadge status={invoice.status} />
            <IrdSyncBadge status={invoice.irdSyncStatus} />
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => openPrintWindow(invoiceId, "thermal80")}>
            <PrinterIcon aria-hidden="true" />
            80mm receipt
          </Button>
          <Button variant="outline" onClick={() => openPrintWindow(invoiceId, "a4")}>
            <PrinterIcon aria-hidden="true" />
            A4
          </Button>
          <Button
            variant="outline"
            render={
              <a
                href={`/api/invoices/${invoiceId}/pdf?format=a4`}
                target="_blank"
                rel="noreferrer"
                aria-label="Download the archived PDF"
              />
            }
            nativeButton={false}
          >
            <DownloadIcon aria-hidden="true" />
            PDF
          </Button>
          {!isCreditNote && invoice.status === "active" && (
            <>
              <ReasonDialog
                title="Cancel this bill"
                description="Cancelled bills are kept, marked cancelled, and reported to the IRD with the reason. Only bills from the current fiscal year can be cancelled."
                confirmLabel="Cancel bill"
                icon={<BanIcon aria-hidden="true" />}
                variant="destructive"
                pending={cancelMutation.isPending}
                onConfirm={(reason) => cancelMutation.mutate({ data: { invoiceId, reason } })}
              />
              <ReasonDialog
                title="Issue a credit note"
                description="A credit note reverses this bill in full and is the lawful correction once a bill has gone out."
                confirmLabel="Issue credit note"
                icon={<ReceiptIcon aria-hidden="true" />}
                variant="outline"
                pending={creditNoteMutation.isPending}
                onConfirm={(reason) => creditNoteMutation.mutate({ data: { invoiceId, reason } })}
              />
            </>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="grid gap-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Lines</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10">#</TableHead>
                      <TableHead>Particulars</TableHead>
                      <TableHead>HS</TableHead>
                      <TableHead className="text-right">Qty</TableHead>
                      <TableHead className="text-right">Rate</TableHead>
                      <TableHead className="text-right">Discount</TableHead>
                      <TableHead className="text-right">Amount</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((line) => (
                      <TableRow key={line.id}>
                        <TableCell>{line.lineNo}</TableCell>
                        <TableCell>
                          {line.description}
                          {!line.vatApplicable && (
                            <span className="ml-2 text-xs text-muted-foreground">exempt</span>
                          )}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {line.hsCode ?? "-"}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatQuantity(line.quantityMilli)} {line.unit}
                        </TableCell>
                        <TableCell className="text-right">
                          <Money paisa={line.unitPricePaisa} />
                        </TableCell>
                        <TableCell className="text-right">
                          {line.discountPaisa ? <Money paisa={line.discountPaisa} /> : "-"}
                        </TableCell>
                        <TableCell className="text-right font-medium">
                          <Money paisa={line.lineTotalPaisa} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Audit trail</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              {audits.map((entry) => (
                <div key={entry.id} className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-medium">{entry.action.replaceAll("_", " ")}</span>
                  <span className="text-xs text-muted-foreground">
                    {entry.actorName ?? "system"} · {toAdDateString(new Date(entry.at))}{" "}
                    {toNptTimeString(new Date(entry.at))}
                    {entry.ipAddress ? ` · ${entry.ipAddress}` : ""}
                  </span>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>

        <aside className="grid h-fit gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Bill</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              <Detail label="Miti (BS)" value={formatBsLong(invoice.miti)} />
              <Detail
                label="Date (AD)"
                value={`${toAdDateString(new Date(invoice.issuedAt))} ${toNptTimeString(
                  new Date(invoice.issuedAt),
                )}`}
              />
              <Detail label="Fiscal year" value={invoice.fiscalYear} />
              <Detail label="Buyer" value={invoice.buyerName} />
              <Detail label="Buyer PAN" value={invoice.buyerPan ?? "-"} />
              <Detail label="Seller PAN" value={store.pan} />
              <Detail label="Billed by" value={invoice.enteredByName} />
              {invoice.refInvoiceNumber && (
                <Detail label="Against" value={invoice.refInvoiceNumber} />
              )}
              {invoice.reason && <Detail label="Reason" value={invoice.reason} />}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Totals</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              <Detail label="Sub total" value={<Money paisa={invoice.subTotalPaisa} />} />
              {invoice.discountPaisa > 0 && (
                <Detail label="Discount" value={<Money paisa={invoice.discountPaisa} />} />
              )}
              {invoice.nonTaxableAmountPaisa > 0 && (
                <Detail label="Exempt" value={<Money paisa={invoice.nonTaxableAmountPaisa} />} />
              )}
              <Detail label="Taxable" value={<Money paisa={invoice.taxableAmountPaisa} />} />
              <Detail
                label={`VAT @ ${(invoice.vatRateBp / 100).toFixed(0)}%`}
                value={<Money paisa={invoice.vatAmountPaisa} />}
              />
              <div className="flex items-baseline justify-between border-t pt-2 text-base font-semibold">
                <span>Total</span>
                <Money paisa={invoice.totalPaisa} prefix />
              </div>
              <p className="text-xs text-muted-foreground">{invoice.amountInWords}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Compliance</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 text-sm">
              <Detail label="Prints" value={String(invoice.printCount)} />
              <Detail
                label="First printed"
                value={
                  invoice.firstPrintedAt
                    ? `${toAdDateString(new Date(invoice.firstPrintedAt))} ${toNptTimeString(
                        new Date(invoice.firstPrintedAt),
                      )}`
                    : "Not yet"
                }
              />
              <Detail label="CBMS attempts" value={String(invoice.irdSyncAttempts)} />
              {invoice.irdLastError && (
                <p className="text-xs text-destructive">{invoice.irdLastError}</p>
              )}
              {invoice.pdfSha256 && (
                <div className="grid gap-1">
                  <span className="text-xs text-muted-foreground">Archived PDF (SHA-256)</span>
                  <code className="truncate font-mono text-[10px]">{invoice.pdfSha256}</code>
                </div>
              )}
              {invoice.irdSyncStatus === "failed" || invoice.irdSyncStatus === "pending" ? (
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-1 w-fit"
                  disabled={retryMutation.isPending}
                  onClick={() => retryMutation.mutate({ data: { invoiceId } })}
                >
                  {retryMutation.isPending ? (
                    <LoaderCircleIcon className="animate-spin" aria-hidden="true" />
                  ) : (
                    <RefreshCwIcon aria-hidden="true" />
                  )}
                  Push to CBMS
                </Button>
              ) : null}
            </CardContent>
          </Card>

          {creditNotes.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Credit notes</CardTitle>
              </CardHeader>
              <CardContent className="grid gap-2 text-sm">
                {creditNotes.map((note) => (
                  <Link
                    key={note.id}
                    to="/app/invoices/$invoiceId"
                    params={{ invoiceId: note.id }}
                    search={{}}
                    className="flex items-baseline justify-between gap-2 hover:underline"
                  >
                    <span className="font-mono text-xs">{note.invoiceNumber}</span>
                    <Money paisa={note.totalPaisa} />
                  </Link>
                ))}
              </CardContent>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{value}</span>
    </div>
  );
}

function ReasonDialog({
  title,
  description,
  confirmLabel,
  icon,
  variant,
  pending,
  onConfirm,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  icon: React.ReactNode;
  variant: "destructive" | "outline";
  pending: boolean;
  onConfirm: (reason: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant={variant} />} nativeButton={false}>
        {icon}
        {confirmLabel}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="reason">Reason</Label>
          <Textarea
            id="reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            placeholder="Wrong quantity billed, goods returned, ..."
          />
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Keep as is
          </Button>
          <Button
            variant={variant === "destructive" ? "destructive" : "default"}
            disabled={pending || reason.trim().length < 5}
            onClick={() => {
              onConfirm(reason.trim());
              setOpen(false);
              setReason("");
            }}
          >
            {pending && <LoaderCircleIcon className="animate-spin" aria-hidden="true" />}
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
