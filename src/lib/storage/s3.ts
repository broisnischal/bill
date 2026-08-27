import "@tanstack/react-start/server-only";
import {
  CreateBucketCommand,
  GetObjectCommand,
  HeadBucketCommand,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

import { env } from "#/env/server.ts";

/**
 * Object storage for archived bills. MinIO locally, any S3 API in production: the only
 * difference is the endpoint and whether the bucket lives in the path or the hostname.
 */
export const s3 = new S3Client({
  region: env.S3_REGION,
  endpoint: env.S3_ENDPOINT,
  forcePathStyle: env.S3_FORCE_PATH_STYLE,
  credentials: {
    accessKeyId: env.S3_ACCESS_KEY_ID,
    secretAccessKey: env.S3_SECRET_ACCESS_KEY,
  },
});

let bucketReady: Promise<void> | undefined;

/** Creates the bucket on first use so a fresh MinIO needs no manual setup. */
export function ensureBucket() {
  bucketReady ??= (async () => {
    try {
      await s3.send(new HeadBucketCommand({ Bucket: env.S3_BUCKET }));
    } catch {
      await s3.send(new CreateBucketCommand({ Bucket: env.S3_BUCKET }));
    }
  })().catch((error: unknown) => {
    bucketReady = undefined;
    throw error;
  });
  return bucketReady;
}

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
  await ensureBucket();
  await s3.send(
    new PutObjectCommand({
      Bucket: env.S3_BUCKET,
      Key: key,
      Body: body,
      ContentType: "application/pdf",
      Metadata: metadata,
    }),
  );
  return key;
}

export async function getPdf(key: string) {
  const result = await s3.send(new GetObjectCommand({ Bucket: env.S3_BUCKET, Key: key }));
  const bytes = await result.Body?.transformToByteArray();
  if (!bytes) throw new Error(`Archived PDF is empty: ${key}`);
  return bytes;
}

/** Short-lived direct download link, so large PDFs never round-trip through the app server. */
export function signedPdfUrl(key: string, expiresIn = 300) {
  return getSignedUrl(s3, new GetObjectCommand({ Bucket: env.S3_BUCKET, Key: key }), {
    expiresIn,
  });
}
