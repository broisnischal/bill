import { createEnv } from "@t3-oss/env-core";
import * as z from "zod";

export const env = createEnv({
  server: {
    // Postgres and S3 are gone: the database is the D1 binding `DB` and archived PDFs
    // live in the R2 binding `ARCHIVE`, both declared in wrangler.jsonc.
    VITE_BASE_URL: z.url().default("http://localhost:3000"),
    BETTER_AUTH_SECRET: z.string().min(1),

    // IRD Central Billing Monitoring System.
    // Mocked unless explicitly turned live, so development and tests never post a bill
    // to a real tax authority. Set IRD_CBMS_LIVE=true against the sandbox or production.
    IRD_CBMS_LIVE: z
      .string()
      .default("false")
      .transform((value) => value === "true"),
    IRD_CBMS_BILL_URL: z.url().default("https://cbapi.ird.gov.np/api/bill"),
    IRD_CBMS_BILL_RETURN_URL: z.url().default("https://cbapi.ird.gov.np/api/billreturn"),

    // SMS gateway for phone-number signup. Without a token no SMS is sent: the code is
    // logged and held for the dev inbox instead, so signup works with no gateway account.
    SPARROW_SMS_TOKEN: z.string().optional(),
    SPARROW_SMS_FROM: z.string().default("Demo"),
    /**
     * Holds the code that would have been texted so `/api/v1/dev/otp` can hand it back.
     * Only has any effect while there is no gateway token, which is what makes a
     * deployment without an SMS account signable-in without weakening verification.
     */
    OTP_DEBUG: z
      .string()
      .default("false")
      .transform((value) => value === "true"),
    /**
     * WhatsApp, through an OpenWA server, which is the channel a code actually goes out
     * on. All three are needed before anything is sent.
     *
     * The base URL is a Cloudflare tunnel to somebody's own machine rather than a
     * gateway with an SLA, so treat a failure to send as normal and not exceptional:
     * see src/lib/sms/openwa.ts.
     */
    OPENWA_BASE_URL: z.string().optional(),
    OPENWA_API_KEY: z.string().optional(),
    OPENWA_SESSION_ID: z.string().optional(),

    /**
     * Whose code the debug route will hand back, comma separated, E.164.
     *
     * Empty means nobody, which is what a deployment that forgot to set it should get.
     */
    OTP_DEBUG_PHONES: z.string().default(""),

    /**
     * Who may review businesses, as mobile numbers in E.164, comma separated.
     *
     * A list rather than a role column because there are two of us and both sign in with
     * a phone. It is a var and not a secret: knowing who reviews grants nothing, and
     * seeing it in version control is how a deployment stops silently having no reviewer.
     */
    ADMIN_PHONES: z.string().default(""),

    // OAuth2 providers, optional, update as needed
    GITHUB_CLIENT_ID: z.string().optional(),
    GITHUB_CLIENT_SECRET: z.string().optional(),
    GOOGLE_CLIENT_ID: z.string().optional(),
    GOOGLE_CLIENT_SECRET: z.string().optional(),
  },
  runtimeEnv: process.env,
});
