import "@tanstack/react-start/server-only";
import { and, eq } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import { store, storeDocument } from "#/lib/db/schema/index.ts";
import type { StoreDocumentKind } from "#/lib/db/schema/types.ts";
import {
  deleteDocument,
  MAX_DOCUMENT_BYTES,
  putDocument,
  storeDocumentKey,
} from "#/lib/storage/documents.ts";

/**
 * Getting a business past review.
 *
 * The shape of it: a shop registers, uploads its PAN certificate, and waits for a person
 * to look. Until that person approves it nothing bills, because the PAN printed on a bill
 * is a claim about who issued it and the tax office holds the taxpayer to that claim. A
 * number nobody checked is a number somebody else can print.
 *
 * Approval also freezes the PAN. Bills already carry it, the series is numbered under it,
 * and letting it change afterwards would leave a register whose earlier pages belong to a
 * different taxpayer.
 */

export class ReviewError extends Error {
  constructor(
    readonly code: "not_pending" | "pan_locked" | "too_large" | "unsupported" | "missing_pan",
    message: string,
  ) {
    super(message);
    this.name = "ReviewError";
  }
}

/** The papers on file for a business, newest of each kind. */
export async function documentsFor(storeId: string) {
  return db.select().from(storeDocument).where(eq(storeDocument.storeId, storeId));
}

/**
 * Stores one paper, replacing whatever was in that slot.
 *
 * The old object is deleted after the row is repointed rather than before: if the write
 * fails the shop still has the document it uploaded last time, which is the version a
 * reviewer would otherwise be left without.
 */
export async function saveDocument({
  storeId,
  kind,
  body,
  mimeType,
  fileName,
}: {
  storeId: string;
  kind: StoreDocumentKind;
  body: ArrayBuffer;
  mimeType: string;
  fileName?: string;
}) {
  if (body.byteLength > MAX_DOCUMENT_BYTES) {
    throw new ReviewError("too_large", "That file is over 8 MB. A photo of the page is enough.");
  }

  const key = storeDocumentKey({ storeId, kind, mimeType });
  await putDocument({ key, body, mimeType, metadata: { storeId, kind } });

  const [existing] = await db
    .select()
    .from(storeDocument)
    .where(and(eq(storeDocument.storeId, storeId), eq(storeDocument.kind, kind)));

  const values = {
    storeId,
    kind,
    key,
    fileName: fileName?.slice(0, 200) ?? null,
    mimeType,
    sizeBytes: body.byteLength,
    uploadedAt: new Date(),
  };

  const [row] = existing
    ? await db
        .update(storeDocument)
        .set(values)
        .where(eq(storeDocument.id, existing.id))
        .returning()
    : await db.insert(storeDocument).values(values).returning();

  if (existing) await deleteDocument(existing.key).catch(() => {});

  // A business that had been refused goes back in the queue the moment it sends a new
  // paper. Making them press a second button after fixing what was asked for is a way to
  // lose people who did the work.
  await db
    .update(store)
    .set({ status: "pending", reviewNote: null })
    .where(and(eq(store.id, storeId), eq(store.status, "rejected")));

  return row;
}

/** A business may be submitted for review once its PAN certificate is on file. */
export async function readyForReview(storeId: string) {
  const papers = await documentsFor(storeId);
  return papers.some((paper) => paper.kind === "pan");
}

/** Approves a business. From here its PAN is frozen. */
export async function approveStore({
  storeId,
  reviewerId,
}: {
  storeId: string;
  reviewerId: string;
}) {
  if (!(await readyForReview(storeId))) {
    throw new ReviewError("missing_pan", "There is no PAN certificate on file to approve");
  }

  const [row] = await db
    .update(store)
    .set({ status: "approved", reviewedAt: new Date(), reviewedById: reviewerId, reviewNote: null })
    .where(eq(store.id, storeId))
    .returning();
  return row;
}

/** Refuses a business, with a reason it can act on. */
export async function rejectStore({
  storeId,
  reviewerId,
  note,
}: {
  storeId: string;
  reviewerId: string;
  note: string;
}) {
  const [row] = await db
    .update(store)
    .set({ status: "rejected", reviewedAt: new Date(), reviewedById: reviewerId, reviewNote: note })
    .where(eq(store.id, storeId))
    .returning();
  return row;
}

/**
 * Refuses a change to the PAN of an approved business.
 *
 * Called from the one place a PAN can be edited. Bills already carry it and the series is
 * numbered under it, so changing it would leave a register whose earlier pages belong to
 * somebody else.
 */
export function assertPanEditable(current: { status: string; pan: string }, next: string) {
  if (current.status === "approved" && next !== current.pan) {
    throw new ReviewError(
      "pan_locked",
      "The PAN cannot change once the business is approved. Contact support if it is wrong.",
    );
  }
}
