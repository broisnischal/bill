import "@tanstack/react-start/server-only";
import { env } from "cloudflare:workers";

/**
 * Object storage for archived bills, on the R2 bucket bound to the Worker. The binding
 * is the whole client: no endpoint, no region and no key pair to hold, and nothing to
 * create on first use because the bucket exists before the Worker is deployed.
 */

/**
 * Where a bill's PDF lives. Keyed by store and fiscal year so a tax office asking for
 * one year of one taxpayer's bills is a prefix listing, not a database crawl.
 */
export function invoicePdfKey({
  storeId,
  fiscalYear,
  invoiceNumber,
  format,
}: {
  storeId: string;
  fiscalYear: string;
  invoiceNumber: string;
  format: string;
}) {
  const safeNumber = invoiceNumber.replace(/[^A-Za-z0-9._-]/g, "_");
  return `stores/${storeId}/${fiscalYear}/${safeNumber}-${format}.pdf`;
}

export async function putPdf({
  key,
  body,
  metadata,
}: {
  key: string;
  body: Uint8Array;
  metadata?: Record<string, string>;
}) {
  await env.ARCHIVE.put(key, body, {
    httpMetadata: { contentType: "application/pdf" },
    customMetadata: metadata,
  });
  return key;
}

export async function getPdf(key: string) {
  const object = await env.ARCHIVE.get(key);
  if (!object) throw new Error(`Archived PDF is empty: ${key}`);
  return new Uint8Array(await object.arrayBuffer());
}
