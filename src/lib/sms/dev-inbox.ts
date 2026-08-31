import "@tanstack/react-start/server-only";
import { env } from "#/env/server.ts";

/**
 * The last code sent to each number, when there is no SMS gateway configured.
 *
 * Signing in needs an OTP, and a developer with no Sparrow account has no way to receive
 * one. Rather than weaken verification with a fixed code, the message that would have
 * been sent is kept here for a few minutes and served over a route that refuses to exist
 * outside development. The real verification path is untouched.
 */

interface Delivered {
  code: string;
  at: number;
}

const inbox = new Map<string, Delivered>();

/** Codes are short-lived anyway; this only stops the map growing without bound. */
const TTL_MS = 10 * 60 * 1000;
const MAX_ENTRIES = 100;

/** True when there is no gateway to send through, which is what makes the inbox safe. */
export const devInboxEnabled = !env.SPARROW_SMS_TOKEN && process.env.NODE_ENV !== "production";

export function recordDevSms(phoneNumber: string, text: string) {
  if (!devInboxEnabled) return;

  const code = text.match(/\b(\d{4,8})\b/)?.[1];
  if (!code) return;

  if (inbox.size >= MAX_ENTRIES) {
    const oldest = [...inbox.entries()].sort((a, b) => a[1].at - b[1].at)[0];
    if (oldest) inbox.delete(oldest[0]);
  }
  inbox.set(phoneNumber, { code, at: Date.now() });
}

export function readDevOtp(phoneNumber: string) {
  if (!devInboxEnabled) return null;
  const entry = inbox.get(phoneNumber);
  if (!entry) return null;
  if (Date.now() - entry.at > TTL_MS) {
    inbox.delete(phoneNumber);
    return null;
  }
  return entry.code;
}
