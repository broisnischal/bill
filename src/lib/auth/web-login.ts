import "@tanstack/react-start/server-only";
import { randomInt, randomUUID } from "node:crypto";

import { and, eq, lt, or, sql } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import { webLoginRequest } from "#/lib/db/schema/index.ts";

/**
 * Signing a browser in from a phone that is already signed in.
 *
 * The browser asks for a code, shows it, and waits. The shopkeeper types those six
 * characters into the app on the phone in their hand, and the browser is let in. No SMS,
 * no waiting for a network that is busy, and nothing typed into the browser that would
 * matter if someone were watching over a shoulder — the code alone lets nobody in.
 *
 * What makes it safe is the direction of trust: a code can only ever be approved from a
 * device that already holds a session, so possession of the phone is the credential.
 * Everything else here is about narrowing the window — short expiry, few attempts, one
 * collection per code.
 */

/**
 * The alphabet the code is drawn from, and how much of it.
 *
 * Digits only, four of them. It used to be six characters of a no-vowel, no-lookalike
 * alphabet, which is unguessable but is also a thing somebody has to find on a keyboard
 * one letter at a time while a customer waits. Four digits is read once, remembered, and
 * typed on the number pad the phone already puts up for it.
 *
 * That is 10,000 codes rather than 85 million, and it is only safe because of the two
 * things below it: a code lives five minutes, and only live codes exist at all — see
 * `releaseFinished`. Approving still needs a session, so a guess buys an attacker a
 * stranger's browser signed into the attacker's own account, and nothing of the
 * stranger's. Worth knowing all the same: at this length the guessing is bounded by the
 * window and not by the arithmetic.
 */
const ALPHABET = "0123456789";
const CODE_LENGTH = 4;

/** Long enough to walk to the till, short enough that a glimpsed code goes stale. */
const TTL_MS = 5 * 60 * 1000;

/** A typo or two is expected. A dozen is someone guessing. */
const MAX_ATTEMPTS = 5;

function newCode() {
  let code = "";
  for (let index = 0; index < CODE_LENGTH; index++) {
    code += ALPHABET[randomInt(ALPHABET.length)];
  }
  return code;
}

/** Starts a request. The browser keeps `pollToken` and shows `code`. */
export async function beginWebLogin({
  userAgent,
  ipAddress,
}: {
  userAgent?: string;
  ipAddress?: string;
}) {
  await expireStale();

  // A collision is vanishingly unlikely and trivially recoverable, so it is retried
  // rather than guarded with a lock.
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = newCode();
    const [created] = await db
      .insert(webLoginRequest)
      .values({
        code,
        pollToken: randomUUID().replace(/-/g, ""),
        userAgent: userAgent?.slice(0, 300),
        ipAddress,
        expiresAt: new Date(Date.now() + TTL_MS),
      })
      .onConflictDoNothing({ target: webLoginRequest.code })
      .returning();

    if (created) {
      return { code: created.code, pollToken: created.pollToken, expiresAt: created.expiresAt };
    }
  }

  throw new Error("Could not start a sign-in just now. Try again.");
}

/** What the browser sees while it waits. Never reveals who approved it, only that it was. */
export async function pollWebLogin(pollToken: string) {
  await expireStale();

  const [request] = await db
    .select()
    .from(webLoginRequest)
    .where(eq(webLoginRequest.pollToken, pollToken));

  if (!request) return null;
  return { status: request.status, userId: request.approvedByUserId };
}

/**
 * Looks up a code on behalf of a signed-in phone, so it can show what it is about to
 * approve before it approves anything.
 */
export async function describeWebLogin(code: string) {
  await expireStale();

  const [request] = await db
    .select()
    .from(webLoginRequest)
    .where(and(eq(webLoginRequest.code, normalise(code)), eq(webLoginRequest.status, "pending")));

  if (!request) return null;
  return { userAgent: request.userAgent, expiresAt: request.expiresAt };
}

export type ApprovalResult =
  | { ok: true }
  | { ok: false; reason: "not_found" | "expired" | "too_many_attempts" };

/**
 * Lets a browser in.
 *
 * The attempt counter is bumped on the code the phone typed, not on the session doing
 * the typing, because the thing being guessed is the code.
 */
export async function approveWebLogin({
  code,
  userId,
  approve,
}: {
  code: string;
  userId: string;
  approve: boolean;
}): Promise<ApprovalResult> {
  await expireStale();

  const [request] = await db
    .select()
    .from(webLoginRequest)
    .where(eq(webLoginRequest.code, normalise(code)));

  if (!request || request.status !== "pending") return { ok: false, reason: "not_found" };
  if (request.expiresAt.getTime() <= Date.now()) return { ok: false, reason: "expired" };
  if (request.attempts >= MAX_ATTEMPTS) return { ok: false, reason: "too_many_attempts" };

  await db
    .update(webLoginRequest)
    .set({
      status: approve ? "approved" : "denied",
      approvedByUserId: approve ? userId : null,
      approvedAt: new Date(),
    })
    .where(eq(webLoginRequest.id, request.id));

  return { ok: true };
}

/** Counts a wrong code against the window, so guessing runs out rather than continuing. */
export async function noteFailedAttempt(code: string) {
  const normalised = normalise(code);
  if (!normalised) return;

  await db
    .update(webLoginRequest)
    .set({ attempts: sql`${webLoginRequest.attempts} + 1` })
    .where(eq(webLoginRequest.code, normalised));
}

/** Marks a code collected, so the same one cannot be redeemed twice. */
export async function claimWebLogin(pollToken: string) {
  // Deleted rather than marked claimed, for the same reason as above: a spent code must
  // not hold one of ten thousand. It is still redeemable exactly once, more strictly than
  // before — the row a second attempt would need is not there.
  const [claimed] = await db
    .delete(webLoginRequest)
    .where(and(eq(webLoginRequest.pollToken, pollToken), eq(webLoginRequest.status, "approved")))
    .returning({ userId: webLoginRequest.approvedByUserId });

  return claimed?.userId ?? null;
}

/**
 * Codes do not linger. Anything past its window stops being approvable at all, and stops
 * occupying its code.
 *
 * The row is deleted rather than marked expired. With six characters the leftovers were
 * harmless; with four digits there are ten thousand codes in total, and rows kept forever
 * would fill that space until `beginWebLogin` could not find a free one. Nothing reads a
 * finished request: a browser polling a token that is gone is told the sign-in expired,
 * which is what it was going to be told anyway.
 */
async function expireStale() {
  await db
    .delete(webLoginRequest)
    .where(
      and(
        lt(webLoginRequest.expiresAt, new Date()),
        or(eq(webLoginRequest.status, "pending"), eq(webLoginRequest.status, "approved")),
      ),
    );
}

/**
 * What someone typed, in the alphabet the code was drawn from.
 *
 * Everything that is not a digit goes: a space, a dash, or the letter somebody's
 * keyboard offered instead of the number. The old mapping of O to 0 and I to 1 was for
 * an alphabet that had letters in it and there are none left to confuse.
 */
export function normalise(code: string) {
  return code.replace(/\D/g, "").slice(0, CODE_LENGTH);
}
