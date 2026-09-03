import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { FileTextIcon } from "lucide-react";
import * as z from "zod";

import { Badge } from "#/components/ui/badge.tsx";
import { Card } from "#/components/ui/card.tsx";
import { reviewQueueQueryOptions, type ReviewStatusFilter } from "#/lib/admin/queries.ts";

const FILTERS: { value: ReviewStatusFilter; label: string }[] = [
  { value: "pending", label: "Waiting" },
  { value: "approved", label: "Approved" },
  { value: "rejected", label: "Refused" },
  { value: "all", label: "Everything" },
];

export const Route = createFileRoute("/_auth/admin/")({
  component: ReviewQueue,
  validateSearch: z.object({
    status: z.enum(["pending", "approved", "rejected", "all"]).default("pending"),
  }),
  loaderDeps: ({ search }) => ({ status: search.status }),
  loader: ({ context, deps }) =>
    context.queryClient.query({ ...reviewQueueQueryOptions(deps.status), staleTime: "static" }),
});

function ReviewQueue() {
  const { status } = Route.useSearch();
  const { data: rows } = useQuery(reviewQueueQueryOptions(status));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-2">
        {FILTERS.map((filter) => (
          <Link
            key={filter.value}
            to="/admin"
            search={{ status: filter.value }}
            className={
              filter.value === status
                ? "rounded-full bg-foreground px-4 py-1.5 text-sm font-medium text-background"
                : "rounded-full border px-4 py-1.5 text-sm text-muted-foreground hover:text-foreground"
            }
          >
            {filter.label}
          </Link>
        ))}
      </div>

      {!rows?.length ? (
        <Card className="p-10 text-center text-sm text-muted-foreground">
          Nothing here. A business appears the moment it uploads its PAN certificate.
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {rows.map((row) => (
            <Link
              key={row.store.id}
              to="/admin/$storeId"
              params={{ storeId: row.store.id }}
              className="rounded-xl border bg-background p-4 transition-colors hover:border-foreground/30"
            >
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span className="font-medium tracking-tight">{row.store.name}</span>
                <span className="font-mono text-xs text-muted-foreground">PAN {row.store.pan}</span>
                <StatusBadge status={row.store.status} />
                <span className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground">
                  <FileTextIcon className="size-3.5" aria-hidden="true" />
                  {row.documents.length} of 3
                </span>
              </div>
              <p className="mt-1 text-sm text-muted-foreground">
                {row.store.municipality ?? row.store.district ?? row.store.address} ·{" "}
                {row.ownerName}
                {row.ownerPhone ? ` · ${row.ownerPhone}` : ""}
              </p>
              {/* The one thing that decides whether it can even be looked at. */}
              {!row.documents.some((paper) => paper.kind === "pan") && (
                <p className="mt-2 text-sm text-amber-700 dark:text-amber-500">
                  No PAN certificate uploaded yet.
                </p>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  if (status === "approved") return <Badge variant="secondary">Approved</Badge>;
  if (status === "rejected") return <Badge variant="destructive">Refused</Badge>;
  return <Badge>Waiting</Badge>;
}
