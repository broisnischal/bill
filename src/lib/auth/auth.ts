import "@tanstack/react-start/server-only";
import { drizzleAdapter } from "@better-auth/drizzle-adapter/relations-v2";
import { betterAuth } from "better-auth/minimal";
import { bearer, phoneNumber } from "better-auth/plugins";
import { tanstackStartCookies } from "better-auth/tanstack-start";

import { env } from "#/env/server.ts";
import { db } from "#/lib/db/index.ts";
import * as schema from "#/lib/db/schema/index.ts";
import { normalizeNepaliMobile } from "#/lib/nepali/validators.ts";
import { sendSms } from "#/lib/sms/sparrow.ts";

export const auth = betterAuth({
  baseURL: env.VITE_BASE_URL,
  telemetry: {
    enabled: false,
  },
  database: drizzleAdapter(db, {
    provider: "pg",
    schema,
  }),

  plugins: [
    /**
     * The mobile apps hold a session token instead of a cookie, so every `/api/v1`
     * request authenticates with `Authorization: Bearer <token>`.
     * https://better-auth.com/docs/plugins/bearer
     */
    bearer(),

    /**
     * Phone-number signup. A Nepali shopkeeper has a mobile number long before an
     * email address, so the number is the account and the OTP is the only credential.
     * https://better-auth.com/docs/plugins/phone-number
     */
    phoneNumber({
      phoneNumberValidator: (value) => normalizeNepaliMobile(value) !== null,
      requireVerification: true,
      expiresIn: 5 * 60,
      sendOTP: async ({ phoneNumber: to, code }) => {
        await sendSms({
          to,
          text: `${code} is your Bill verification code. It expires in 5 minutes.`,
        });
      },
      // Verifying an unknown number creates the account, so there is no separate signup
      // step. The placeholder email exists only because the user table requires one.
      signUpOnVerification: {
        getTempEmail: (value) => `${value.replace(/\D/g, "")}@phone.bill.np`,
        getTempName: (value) => value,
      },
    }),

    /**
     * Last on purpose.
     *
     * This forwards Set-Cookie into TanStack's cookie store, and it only sees headers
     * from plugins that ran before it. With it first, a session cookie set by the phone
     * sign-in above never reached the browser — which is exactly the sign-in nearly
     * everyone uses.
     * https://better-auth.com/docs/integrations/tanstack#usage-tips
     */
    tanstackStartCookies(),
  ],

  // https://better-auth.com/docs/concepts/session-management#session-caching
  session: {
    cookieCache: {
      enabled: true,
      maxAge: 5 * 60, // 5 minutes
    },
  },

  // https://better-auth.com/docs/concepts/oauth
  socialProviders: {
    github: {
      clientId: env.GITHUB_CLIENT_ID!,
      clientSecret: env.GITHUB_CLIENT_SECRET!,
    },
    google: {
      clientId: env.GOOGLE_CLIENT_ID!,
      clientSecret: env.GOOGLE_CLIENT_SECRET!,
    },
  },

  // https://better-auth.com/docs/authentication/email-password
  emailAndPassword: {
    enabled: true,
  },

  advanced: {
    database: {
      // https://better-auth.com/docs/adapters/drizzle#joins
      joins: true,
    },
  },
});
