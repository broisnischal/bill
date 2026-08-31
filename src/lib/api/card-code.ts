import { createHmac, timingSafeEqual } from "node:crypto";

/**
 * The code on a shopper's card, and the handle a shop gets for scanning it.
 *
 * A QR that never changes is a QR that can be photographed once and used forever, so the
 * card shows a code derived from the profile and the clock: it is only valid for a few
 * minutes, and a picture of yesterday's card resolves to nothing.
 *
 * Scanning a valid code hands the shop a `link` instead — signed, longer-lived, and tied
 * to that one shopper. That is what a bill carries, because a till that was offline for a
 * week still has to be able to say who the bill was for when it finally syncs.
 *
 * The secret is passed in rather than read from the environment: this is the part worth
 * testing in isolation, and it has no business knowing where the app keeps its keys.
 */

/** How long one card code is good for. Long enough to hold a phone up, short enough to matter. */
export const CODE_WINDOW_MS = 5 * 60 * 1000;

/** How long a shop may use a scanned card. Covers a till that has been offline for weeks. */
export const LINK_TTL_MS = 30 * 24 * 60 * 60 * 1000;

function sign(secret: string, payload: string, purpose: string) {
  return createHmac("sha256", secret)
    .update(`${purpose}:${payload}`)
    .digest("base64url")
    .slice(0, 27);
}

function matches(a: string, b: string) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

/** The code the card shows right now, and when it stops working. */
export function cardCode(secret: string, token: string, now = Date.now()) {
  const window = Math.floor(now / CODE_WINDOW_MS);
  return {
    code: `${token}.${window}.${sign(secret, `${token}|${window}`, "card")}`,
    expiresAt: new Date((window + 1) * CODE_WINDOW_MS).toISOString(),
  };
}

/**
 * The profile token behind a scanned code, or null.
 *
 * The previous window is accepted as well as the current one, so a card held up a second
 * before the code rolls over still scans.
 */
export function readCardCode(secret: string, code: string, now = Date.now()) {
  const [token, windowText, signature] = code.trim().split(".");
  if (!token || !windowText || !signature) return null;

  const window = Number(windowText);
  if (!Number.isInteger(window)) return null;

  const current = Math.floor(now / CODE_WINDOW_MS);
  if (window !== current && window !== current - 1) return null;

  return matches(signature, sign(secret, `${token}|${window}`, "card")) ? token : null;
}

/** A handle the shop keeps, so a bill written offline can still name its buyer. */
export function issueShopperLink(secret: string, userId: string, now = Date.now()) {
  const expiry = now + LINK_TTL_MS;
  const payload = `${userId}|${expiry}`;
  return `${Buffer.from(payload).toString("base64url")}.${sign(secret, payload, "link")}`;
}

export function readShopperLink(secret: string, link: string, now = Date.now()) {
  const [encoded, signature] = link.trim().split(".");
  if (!encoded || !signature) return null;

  const payload = Buffer.from(encoded, "base64url").toString();
  if (!matches(signature, sign(secret, payload, "link"))) return null;

  const [userId, expiryText] = payload.split("|");
  const expiry = Number(expiryText);
  if (!userId || !Number.isInteger(expiry) || expiry < now) return null;

  return userId;
}
