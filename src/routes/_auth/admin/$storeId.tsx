import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useRouter } from "@tanstack/react-router";
import { ArrowLeftIcon, FileTextIcon } from "lucide-react";
import { useState } from "react";

import { Button } from "#/components/ui/button.tsx";
import { Card } from "#/components/ui/card.tsx";
import { Textarea } from "#/components/ui/textarea.tsx";
import { $reviewStore } from "#/lib/admin/functions.ts";
import { storeForReviewQueryOptions } from "#/lib/admin/queries.ts";

import { StatusBadge } from "./index";

const DOCUMENT_LABELS: Record<string, string> = {
  pan: "PAN certificate",
  registration: "Registration certificate",
  tax_clearance: "Tax clearance",
};

export const Route = createFileRoute("/_auth/admin/$storeId")({
  component: ReviewStore,
  loader: ({ context, params }) =>
    context.queryClient.query({
      ...storeForReviewQueryOptions(params.storeId),
      staleTime: "static",
    }),
});

function ReviewStore() {
  const { storeId } = Route.useParams();
  const { data } = useQuery(storeForReviewQueryOptions(storeId));
  const queryClient = useQueryClient();
  const router = useRouter();
  const [note, setNote] = useState("");

  const review = useMutation({
    mutationFn: $reviewStore,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin"] });
      await router.invalidate();
    },
  });

  if (!data) return null;
  const { store, documents, ownerName, ownerPhone } = data;
  const hasPan = documents.some((paper) => paper.kind === "pan");

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/admin"
        search={{ status: "pending" as const }}
        className="flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeftIcon className="size-4" aria-hidden="true" />
        Back to the queue
      </Link>

      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{store.name}</h1>
        <StatusBadge status={store.status} />
      </div>

      <Card className="grid gap-x-8 gap-y-4 p-6 sm:grid-cols-2">
        <Detail label="PAN" value={store.pan} mono />
        <Detail label="Taxpayer" value={store.taxpayerType === "vat" ? "VAT" : "PAN only"} />
        <Detail label="Registered on (BS)" value={store.registrationDateBs} />
        <Detail label="Business type" value={store.businessType.replaceAll("_", " ")} />
        <Detail label="Name in Nepali" value={store.nameNepali} />
        <Detail label="Registration number" value={store.registrationNumber} />
        <Detail label="Tax office" value={store.taxOffice} />
        <Detail label="Phone" value={store.phone} />
        <Detail
          label="Address"
          value={[store.address, store.municipality, store.district, store.province]
            .filter(Boolean)
            .join(", ")}
        />
        <Detail label="Signed up as" value={[ownerName, ownerPhone].filter(Boolean).join(" · ")} />
      </Card>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted-foreground">Papers</h2>
        {(["pan", "registration", "tax_clearance"] as const).map((kind) => {
          const paper = documents.find((document) => document.kind === kind);
          return (
            <Card key={kind} className="flex flex-wrap items-center gap-3 p-4">
              <FileTextIcon className="size-4 shrink-0" aria-hidden="true" />
              <span className="text-sm font-medium">{DOCUMENT_LABELS[kind]}</span>
              {kind === "pan" && <span className="text-xs text-muted-foreground">required</span>}
              {paper ? (
                <>
                  <span className="text-xs text-muted-foreground">
                    {Math.round(paper.sizeBytes / 1024)} KB · {paper.mimeType}
                  </span>
                  {/* Opens through the Worker against this session; the bucket is private. */}
                  <a
                    href={`/api/admin/documents/${paper.id}`}
                    target="_blank"
                    rel="noreferrer"
                    className="ml-auto text-sm underline underline-offset-4"
                  >
                    Open
                  </a>
                </>
              ) : (
                <span className="ml-auto text-sm text-muted-foreground">Not uploaded</span>
              )}
            </Card>
          );
        })}
      </section>

      {store.status === "rejected" && store.reviewNote && (
        <Card className="border-destructive/40 p-4 text-sm">
          <span className="font-medium">Refused: </span>
          {store.reviewNote}
        </Card>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted-foreground">Decision</h2>
        {!hasPan && (
          <p className="text-sm text-amber-700 dark:text-amber-500">
            No PAN certificate on file. Approving is refused until there is one, because the number
            printed on every bill is what it certifies.
          </p>
        )}
        <Textarea
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder="If you are refusing this, say what has to be fixed. The shop reads it."
          rows={3}
        />
        <div className="flex flex-wrap gap-3">
          <Button
            disabled={!hasPan || review.isPending || store.status === "approved"}
            onClick={() => review.mutate({ data: { storeId: store.id, decision: "approved" } })}
          >
            Approve
          </Button>
          <Button
            variant="outline"
            disabled={note.trim().length < 10 || review.isPending}
            onClick={() =>
              review.mutate({
                data: { storeId: store.id, decision: "rejected", note: note.trim() },
              })
            }
          >
            Refuse with this note
          </Button>
        </div>
        {/* Said before it is done, not after: approving is the one action here that
            cannot be undone by editing a field. */}
        <p className="text-xs text-muted-foreground">
          Approving lets this business bill and freezes its PAN. Refusing puts it back to the shop
          with your note, and it returns to the queue as soon as they send a new paper.
        </p>
        {review.error && (
          <p className="text-sm text-destructive">{(review.error as Error).message}</p>
        )}
      </section>
    </div>
  );
}

function Detail({
  label,
  value,
  mono = false,
}: {
  label: string;
  value?: string | null;
  mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={mono ? "font-mono text-sm" : "text-sm"}>{value || "—"}</span>
    </div>
  );
}
