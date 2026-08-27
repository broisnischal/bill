import { Badge } from "#/components/ui/badge.tsx";
import type { InvoiceStatus, InvoiceType, IrdSyncStatus } from "#/lib/db/schema/types.ts";

const TYPE_LABELS: Record<InvoiceType, string> = {
  tax_invoice: "Tax invoice",
  abbreviated_tax_invoice: "Abbreviated",
  credit_note: "Credit note",
};

export function InvoiceTypeBadge({ type }: { type: InvoiceType }) {
  return (
    <Badge variant={type === "credit_note" ? "destructive" : "secondary"}>
      {TYPE_LABELS[type]}
    </Badge>
  );
}

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  if (status === "active") return <Badge variant="outline">Active</Badge>;
  return <Badge variant="destructive">Cancelled</Badge>;
}

const SYNC_LABELS: Record<
  IrdSyncStatus,
  { label: string; variant: "outline" | "secondary" | "destructive" }
> = {
  not_applicable: { label: "CBMS off", variant: "outline" },
  pending: { label: "CBMS queued", variant: "secondary" },
  synced: { label: "CBMS synced", variant: "outline" },
  failed: { label: "CBMS failed", variant: "destructive" },
};

export function IrdSyncBadge({ status }: { status: IrdSyncStatus }) {
  const { label, variant } = SYNC_LABELS[status];
  return <Badge variant={variant}>{label}</Badge>;
}
