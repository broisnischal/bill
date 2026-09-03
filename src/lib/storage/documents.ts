import "@tanstack/react-start/server-only";
import { env } from "cloudflare:workers";

import type { StoreDocumentKind } from "#/lib/db/schema/types.ts";

/**
 * The papers a business uploads for review, on the same R2 bucket the bills are archived
 * to.
 *
 * These are somebody's tax documents. The bucket is private and nothing here hands out a
 * URL: a reviewer's browser gets the bytes through the Worker, against their own session,
 * or it gets nothing.
 */

/** What may be uploaded. A reviewer needs to read it, not run it. */
export const ALLOWED_DOCUMENT_TYPES = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "application/pdf",
] as const;

/**
 * 8 MB.
 *
 * A photograph of a PAN certificate off a phone is one to three. The cap is here because
 * an upload with no ceiling is an upload somebody eventually uses as free storage, and
 * because a Worker holds the whole body in memory to write it.
 */
export const MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;

const EXTENSIONS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "application/pdf": "pdf",
};

/**
 * Where a document lives.
 *
 * Keyed by store and kind with a random suffix: the prefix makes one business's papers a
 * single listing, and the suffix means a replacement never overwrites the bytes a
 * reviewer might be reading in another tab.
 */
export function storeDocumentKey({
  storeId,
  kind,
  mimeType,
}: {
  storeId: string;
  kind: StoreDocumentKind;
  mimeType: string;
}) {
  const extension = EXTENSIONS[mimeType] ?? "bin";
  return `stores/${storeId}/documents/${kind}-${crypto.randomUUID()}.${extension}`;
}

export async function putDocument({
  key,
  body,
  mimeType,
  metadata,
}: {
  key: string;
  body: ArrayBuffer | Uint8Array;
  mimeType: string;
  metadata?: Record<string, string>;
}) {
  await env.ARCHIVE.put(key, body, {
    httpMetadata: { contentType: mimeType },
    customMetadata: metadata,
  });
  return key;
}

/** The stored object, for streaming to a reviewer. Null when the key is gone. */
export async function getDocument(key: string) {
  return env.ARCHIVE.get(key);
}

export async function deleteDocument(key: string) {
  await env.ARCHIVE.delete(key);
}
