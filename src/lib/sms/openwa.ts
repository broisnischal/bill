import "@tanstack/react-start/server-only";
import { env } from "#/env/server.ts";

/**
 * WhatsApp as the OTP channel, through an OpenWA server.
 *
 * A Nepali shopkeeper has WhatsApp before they have anything else, and a WhatsApp
 * message costs nothing where a Sparrow SMS costs per send and needs an account we do
 * not have yet. So this is the delivery path, not a fallback.
 *
 * What it talks to is somebody's own machine behind a Cloudflare tunnel: one linked
 * WhatsApp account, a browser engine driving web.whatsapp.com. That is worth saying out
 * loud, because it fails in ways a gateway does not. The laptop sleeps, the tunnel drops,
 * WhatsApp reloads its own page, the account gets unlinked. Every one of those is a
 * failure to deliver and none of them should look like a rejected number, which is why
 * `sendWhatsApp` reports why it failed instead of just that it did.
 */

/** Configured when there is somewhere to send, a key to send with, and a session to send from. */
export const openWaConfigured = Boolean(
  env.OPENWA_BASE_URL && env.OPENWA_API_KEY && env.OPENWA_SESSION_ID,
);

/**
 * How long to wait on the tunnel.
 *
 * Somebody is standing at a counter watching a spinner, and the request is going to a
 * machine on a domestic connection. Eight seconds is long enough for a working send and
 * short enough that a dead tunnel does not hold up the sign-in screen.
 */
const TIMEOUT_MS = 8000;

/**
 * How long to wait for a revived session, as polls of a fixed gap.
 *
 * Four polls three seconds apart, so twelve seconds at worst and usually much less. That
 * is a long time at a counter, which is why it only ever happens on the path where the
 * alternative is not delivering at all.
 */
const REVIVE_POLLS = 4;
const REVIVE_POLL_MS = 3000;

/**
 * The two things OpenWA answers 400 for, told apart by their message.
 *
 * One is a session that is not running and the other is a number the account cannot open
 * a conversation with, and NestJS gives neither a code of its own, so the body is the
 * only discriminator there is. Both strings are lifted from the server's own error
 * classes. If either is reworded, the send falls through to `rejected` and reports the
 * body verbatim, which is the safe way to be wrong: it says what happened instead of
 * restarting a session on a guess.
 */
const UNREACHABLE_RECIPIENT = "could not resolve the recipient";
const SESSION_NOT_STARTED = "Session is not started";

/** Why a send did not happen, for a log that has to be readable at a counter. */
export type WhatsAppFailure =
  | { kind: "unconfigured" }
  | { kind: "no_such_recipient" }
  | { kind: "no_session"; name: string }
  | { kind: "not_started"; detail: string }
  | { kind: "not_connected"; detail: string }
  | { kind: "unauthorized" }
  | { kind: "unreachable"; detail: string }
  | { kind: "rejected"; status: number; detail: string };

export type WhatsAppResult =
  | { ok: true; messageId?: string }
  | { ok: false; failure: WhatsAppFailure };

/** The base URL with any trailing slashes off, so paths concatenate cleanly. */
function baseUrl() {
  return env.OPENWA_BASE_URL!.replace(/\/+$/, "");
}

function headers() {
  return {
    "content-type": "application/json",
    // OpenWA names the header exactly this. It is an API key, not a bearer token.
    "X-API-Key": env.OPENWA_API_KEY!,
  };
}

/** The shape OpenWA's session ids come in. Anything else is read as a session name. */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Which session to send from, by name if that is what it was given.
 *
 * `OPENWA_SESSION_ID` may hold either a uuid or the name of a session. The uuid is what
 * OpenWA generates — `@PrimaryGeneratedColumn('uuid')` on its session entity — so it is
 * a value you can only learn after creating the session, on the server that created it.
 * Rebuild that server, or scan the QR again, and the uuid changes and this stops sending
 * until somebody remembers to update a secret.
 *
 * A name does not have that problem, because a name is something we choose. Pointing this
 * at `marketing` means the config survives a re-pair and a move to another host, and the
 * two things that have to agree are a name in a secret and a name in a dashboard.
 *
 * Held per isolate rather than per request: the answer only changes when somebody
 * re-creates the session, which is what `forgetSession` is for.
 */
let resolved: string | null = null;

async function sessionId(): Promise<string | null> {
  const configured = env.OPENWA_SESSION_ID!;
  if (UUID.test(configured)) return configured;
  if (resolved) return resolved;

  try {
    const response = await fetch(`${baseUrl()}/api/sessions`, {
      headers: headers(),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) return null;
    const sessions = (await response.json()) as { id?: string; name?: string }[];
    // Only cache an answer, never the absence of one: a session created a minute from
    // now should be found without waiting for a new isolate.
    resolved = sessions.find((session) => session.name === configured)?.id ?? null;
    return resolved;
  } catch {
    return null;
  }
}

/** Forgets a resolved id, so a session that was re-created is looked up again. */
function forgetSession() {
  resolved = null;
}

/**
 * Brings a dead session back, and says whether it came back.
 *
 * The engine drives a real Chromium against web.whatsapp.com, and that page fails the
 * way pages do: one DNS lookup misses and the session sits in `failed` until somebody
 * notices. Nobody notices at 9pm. So a send that finds the session down starts it and
 * waits, rather than reporting a failure a human would have to go and read.
 *
 * The wait is deliberately short. Somebody is standing at a counter, and there is a way
 * in behind this that does not need WhatsApp at all.
 */
async function revive(session: string): Promise<boolean> {
  try {
    await fetch(`${baseUrl()}/api/sessions/${session}/start`, {
      method: "POST",
      headers: headers(),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch {
    return false;
  }

  // Starting is asynchronous: the call answers `initializing` and the engine loads after.
  for (let attempt = 0; attempt < REVIVE_POLLS; attempt++) {
    await new Promise((resolve) => setTimeout(resolve, REVIVE_POLL_MS));
    try {
      const response = await fetch(`${baseUrl()}/api/sessions/${session}`, {
        headers: headers(),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!response.ok) continue;
      const state = (await response.json()) as { status?: string };
      if (state.status === "ready") return true;
      // A session wanting a QR scan is not coming back without a person holding a phone.
      if (state.status === "failed" || state.status === "qr") return false;
    } catch {
      // A blip mid-poll is not an answer either way; keep waiting out the budget.
    }
  }
  return false;
}

/**
 * The chat id WhatsApp wants: the number in full international form with no plus, and
 * `@c.us` for an individual rather than a group.
 */
function chatIdFor(phoneNumber: string) {
  return `${phoneNumber.replace(/\D/g, "")}@c.us`;
}

export async function sendWhatsApp({
  to,
  text,
  allowRevive = true,
}: {
  to: string;
  text: string;
  allowRevive?: boolean;
}): Promise<WhatsAppResult> {
  if (!openWaConfigured) return { ok: false, failure: { kind: "unconfigured" } };

  const session = await sessionId();
  if (!session) {
    return { ok: false, failure: { kind: "no_session", name: env.OPENWA_SESSION_ID! } };
  }

  const url = `${baseUrl()}/api/sessions/${session}/messages/send-text`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: headers(),
      body: JSON.stringify({ chatId: chatIdFor(to), text }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (error) {
    // A sleeping laptop, a dropped tunnel, or the eight seconds running out all land
    // here, and none of them are distinguishable from the Worker's side.
    return {
      ok: false,
      failure: {
        kind: "unreachable",
        detail: error instanceof Error ? error.message : String(error),
      },
    };
  }

  if (response.ok) {
    const body = (await response.json().catch(() => null)) as {
      id?: string;
      messageId?: string;
    } | null;
    return { ok: true, messageId: body?.messageId ?? body?.id };
  }

  const detail = (await response.text().catch(() => "")).slice(0, 300);

  // The statuses OpenWA documents for this route, kept apart because they call for
  // different answers: a session nobody started needs a person, a session that is merely
  // reconnecting needs a retry, and a bad key needs a deploy.
  if (response.status === 401 || response.status === 403)
    return { ok: false, failure: { kind: "unauthorized" } };

  // A number this account cannot reach. Nothing is wrong with the session, so reviving it
  // buys nothing and costs plenty: every send to an unknown number restarted a working
  // session, held the shopkeeper at the counter for the twelve seconds that takes, then
  // failed identically and reported the session as dead. Every 400 was read that way,
  // because both answers are 400. Straight to the next channel instead.
  if (response.status === 400 && detail.includes(UNREACHABLE_RECIPIENT))
    return { ok: false, failure: { kind: "no_such_recipient" } };

  // A session that is down, or still connecting, is the one failure worth doing something
  // about rather than reporting. Once only: `allowRevive` is false on the retry, so a
  // session that will not come back cannot loop.
  const notStarted = response.status === 400 && detail.includes(SESSION_NOT_STARTED);
  if (notStarted || response.status === 409) {
    if (allowRevive && (await revive(session)))
      return sendWhatsApp({ to, text, allowRevive: false });
    return notStarted
      ? { ok: false, failure: { kind: "not_started", detail } }
      : { ok: false, failure: { kind: "not_connected", detail } };
  }
  // The session we were sending from is gone: re-created, or renamed out from under us.
  // Drop the cached id and let one retry resolve it again, which is what makes a re-pair
  // recover by itself instead of waiting for a deploy.
  if (response.status === 404 && allowRevive && !UUID.test(env.OPENWA_SESSION_ID!)) {
    forgetSession();
    return sendWhatsApp({ to, text, allowRevive: false });
  }

  return { ok: false, failure: { kind: "rejected", status: response.status, detail } };
}

/** One line for a log, so a failed sign-in can be explained without opening a dashboard. */
export function describeFailure(failure: WhatsAppFailure): string {
  switch (failure.kind) {
    case "unconfigured":
      return "WhatsApp is not configured (OPENWA_BASE_URL, OPENWA_API_KEY, OPENWA_SESSION_ID)";
    case "unauthorized":
      return "OpenWA refused the API key";
    case "no_such_recipient":
      return "that number is not on WhatsApp, or this account has never had a chat with it";
    case "no_session":
      return `the OpenWA server has no session called "${failure.name}"`;
    case "not_started":
      return `the WhatsApp session is not running: ${failure.detail}`;
    case "not_connected":
      return `the WhatsApp session is not connected yet: ${failure.detail}`;
    case "unreachable":
      return `could not reach the OpenWA server: ${failure.detail}`;
    case "rejected":
      return `OpenWA rejected the send (${failure.status}): ${failure.detail}`;
  }
}
