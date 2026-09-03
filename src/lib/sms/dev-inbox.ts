import "@tanstack/react-start/server-only";
import { eq, lt } from "drizzle-orm";

import { env } from "#/env/server.ts";
import { db } from "#/lib/db/index.ts";
import { devSmsCode } from "#/lib/db/schema/index.ts";

/**
 * The last code sent to each number, when there is no SMS gateway configured.
 *
 * Signing in needs an OTP, and a shop with no Sparrow account has no way to receive one.
 * Rather than weaken verification with a fixed code, the message that would have been
 * sent is held for a few minutes and served over a route that refuses to answer unless
 * this is switched on. The real verification path is untouched.
 */

/** Codes are short-lived anyway; this only stops the table growing without bound. */
const TTL_MS = 10 * 60 * 1000;

/** True when there is no gateway to send through and the deployment asked for the inbox. */
export const devInboxEnabled = !env.SPARROW_SMS_TOKEN && env.OTP_DEBUG;

/**
 * Whose code may be read back, and nobody else's.
 *
 * Without this the readable-OTP route is an account takeover: no SMS gateway plus
 * OTP_DEBUG means anyone who can reach the URL signs in as any Nepali mobile number,
 * reads that shop's bills and downloads its PAN certificate. The route existed so a
 * deployment with no gateway could still be signed into, and that only ever needed to be
 * true for the handful of numbers doing the testing.
 *
 * Empty list with the inbox on means nobody, which is the safe way round.
 */
export function devInboxAllows(phoneNumber: string): boolean {
  if (!devInboxEnabled) return false;
  return env.OTP_DEBUG_PHONES.split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .includes(phoneNumber);
}

export async function recordDevSms(phoneNumber: string, text: string) {
  if (!devInboxEnabled) return;

  const code = text.match(/\b(\d{4,8})\b/)?.[1];
  if (!code) return;

  const sentAt = new Date();
  await db
    .insert(devSmsCode)
    .values({ phoneNumber, code, sentAt })
    .onConflictDoUpdate({ target: devSmsCode.phoneNumber, set: { code, sentAt } });

  await db.delete(devSmsCode).where(lt(devSmsCode.sentAt, new Date(Date.now() - TTL_MS)));
}

export async function readDevOtp(phoneNumber: string) {
  if (!devInboxEnabled) return null;

  const [entry] = await db.select().from(devSmsCode).where(eq(devSmsCode.phoneNumber, phoneNumber));

  if (!entry) return null;
  if (Date.now() - entry.sentAt.getTime() > TTL_MS) return null;
  return entry.code;
}
