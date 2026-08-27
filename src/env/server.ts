import { createEnv } from "@t3-oss/env-core";
import * as z from "zod";

export const env = createEnv({
  server: {
    DATABASE_URL: z.url(),
    VITE_BASE_URL: z.url().default("http://localhost:3000"),
    BETTER_AUTH_SECRET: z.string().min(1),

    // Object storage for archived invoice PDFs. MinIO locally, any S3 API in production.
    S3_ENDPOINT: z.url().default("http://localhost:9002"),
    S3_REGION: z.string().default("us-east-1"),
    S3_BUCKET: z.string().default("bill-invoices"),
    S3_ACCESS_KEY_ID: z.string().min(1),
    S3_SECRET_ACCESS_KEY: z.string().min(1),
    S3_FORCE_PATH_STYLE: z
      .string()
      .default("true")
      .transform((value) => value !== "false"),

    // IRD Central Billing Monitoring System
    IRD_CBMS_BILL_URL: z.url().default("https://cbapi.ird.gov.np/api/bill"),
    IRD_CBMS_BILL_RETURN_URL: z.url().default("https://cbapi.ird.gov.np/api/billreturn"),

    // OAuth2 providers, optional, update as needed
    GITHUB_CLIENT_ID: z.string().optional(),
    GITHUB_CLIENT_SECRET: z.string().optional(),
    GOOGLE_CLIENT_ID: z.string().optional(),
    GOOGLE_CLIENT_SECRET: z.string().optional(),
  },
  runtimeEnv: process.env,
});
