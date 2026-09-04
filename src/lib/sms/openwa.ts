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

/**
 * Configured when there is somewhere to send and a key to send with.
 *
 * `OPENWA_SESSION_ID` is deliberately not required. It used to be, and that made the
 * channel depend on a name somebody could delete: a session removed and recreated under
 * another name left the config pointing at nothing and no code went out, even though a
 * healthy linked account was sitting right there. It is a preference now, not a
 * prerequisite — name a session to be sent from first, or name none and let any account
 * that can send do it.
 */
export const openWaConfigured = Boolean(env.OPENWA_BASE_URL && env.OPENWA_API_KEY);

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
 * What OpenWA answers 400 for, told apart by their message.
 *
 * NestJS gives none of them a code of its own, so the body is the only discriminator
 * there is, and the strings are lifted from the server's own throw sites. If one is
 * reworded, the send falls through to `rejected` and reports the body verbatim, which is
 * the safe way to be wrong: it says what happened instead of restarting a session on a
 * guess.
 *
 * A number the account cannot open a conversation with.
 */
const UNREACHABLE_RECIPIENT = "could not resolve the recipient";

/**
 * A session the engine is not running, in the two wordings that mean it.
 *
 * `EngineRegistry.require` throws the first; `message-send.service.ts` throws the second
 * as `Session '<id>' is not active. Start the session first.` Only the first was matched,
 * so the window after a container restart — up to the 30s takeover sweep, before
 * AUTO_START_SESSIONS gets to it — reported a dead session as a rejected message and
 * never tried to revive it. Both are the same fact and both are worth one start.
 */
const SESSION_DOWN = ["Session is not started", "is not active. Start the session first"];

/** Why a send did not happen, for a log that has to be readable at a counter. */
export type WhatsAppFailure =
  | { kind: "unconfigured" }
  | { kind: "no_such_recipient" }
  | { kind: "no_session"; name?: string }
  | { kind: "not_delivered" }
  | { kind: "restricted"; code: string; until?: string }
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
 * How long the session's state is trusted before it is asked for again.
 *
 * Every send used to skip this lookup when the secret held a uuid, which is why a
 * restricted account went unnoticed for hours. Thirty seconds is short enough that a
 * restriction is caught almost immediately and long enough that a counter's burst of
 * sign-ins pays for one lookup rather than one each — 70ms against a 150ms send.
 */
const SESSION_TTL_MS = 30_000;

/**
 * WhatsApp's own penalty on the account, as OpenWA reports it.
 *
 * `reachout_timelock` / `RESTRICT_ALL_COMPANIONS` is what WhatsApp does to an account
 * that messages people who have never messaged it — which is exactly what sending a
 * verification code is. Every linked device stops delivering for the duration. Sends are
 * still accepted, still answer 201, and still come back with a message id; WhatsApp just
 * drops them. There is no way to tell from the send itself, which is the whole reason
 * this is checked separately.
 */
type Restriction = { kind?: string; code?: string; expiresAt?: string };

type SessionState = {
  id: string;
  /** What the shop calls it, which is what the config may name instead of a uuid. */
  name?: string;
  status?: string;
  restriction?: Restriction | null;
};

/**
 * Which session to send from, and what state it is in.
 *
 * `OPENWA_SESSION_ID` may hold either a uuid or the name of a session. The uuid is what
 * OpenWA generates — `@PrimaryGeneratedColumn('uuid')` on its session entity — so it is
 * a value you can only learn after creating the session, on the server that created it.
 * Rebuild that server, or scan the QR again, and the uuid changes and this stops sending
 * until somebody remembers to update a secret. A name is something we choose, so it
 * survives a re-pair and a move to another host.
 *
 * Cached for [SESSION_TTL_MS] rather than for the life of the isolate: the id barely
 * changes, but the restriction does, and one is no use without the other.
 */
let cached: { at: number; sessions: SessionState[] } | null = null;

/** Everything the server has, cached briefly. */
async function allSessions(): Promise<SessionState[] | null> {
  if (cached && Date.now() - cached.at < SESSION_TTL_MS) return cached.sessions;

  try {
    const response = await fetch(`${baseUrl()}/api/sessions`, {
      headers: headers(),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) return null;

    const sessions = (await response.json()) as SessionState[];
    cached = { at: Date.now(), sessions };
    return sessions;
  } catch {
    // Never cache a failure: a session created a minute from now should be found without
    // waiting for a new isolate.
    return null;
  }
}

/** Whether this is the session the config names, by uuid or by name. */
function isConfigured(session: SessionState) {
  const configured = env.OPENWA_SESSION_ID;
  if (!configured) return false;
  return UUID.test(configured) ? session.id === configured : session.name === configured;
}

/**
 * Every session that could carry a code, best first.
 *
 * One linked account is one WhatsApp account, and WhatsApp restricts accounts: a
 * `reachout_timelock` takes every linked device of that account out for six hours at a
 * time, and it is triggered by exactly what an OTP does. With one session that is the
 * channel gone. With two it is a shrug, so long as something looks past the first.
 *
 * The configured one leads, so an ordinary send is predictable and lands on the account
 * a shopkeeper has seen before. Anything else `ready` and unrestricted follows, in the
 * order the server listed them.
 */
async function sendableSessions(): Promise<SessionState[] | null> {
  const sessions = await allSessions();
  if (!sessions) return null;

  const usable = sessions.filter((session) => session.status === "ready" && !session.restriction);
  usable.sort((a, b) => Number(isConfigured(b)) - Number(isConfigured(a)));

  if (usable.length > 0) return usable;

  // Nothing is ready. Fall back to the preferred session, or to whatever exists, so the
  // revive path below still has something to start — a session that is merely asleep is
  // the case that recovers on its own.
  const fallback = sessions.find(isConfigured) ?? sessions[0];
  return fallback ? [fallback] : [];
}

/** Forgets the cached session, so one that was re-created is looked up again. */
function forgetSession() {
  cached = null;
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

/**
 * One send, against one session.
 *
 * Split out from [sendWhatsApp] so the caller can walk several: this reports what
 * happened to this attempt and decides nothing about whether to try elsewhere.
 */
async function attemptSend({
  session,
  to,
  text,
  allowRevive = true,
}: {
  session: string;
  to: string;
  text: string;
  allowRevive?: boolean;
}): Promise<WhatsAppResult> {
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

    /**
     * A 2xx with no message id is not a send.
     *
     * The browser engine used to accept a send on a session whose WhatsApp client had
     * gone, queue it against a page that was never going to deliver it, and answer 201.
     * The Worker read that as delivered, `deliverOtp` reported "whatsapp", and a
     * shopkeeper was told a code was on its way while the message sat in the chat with a
     * warning triangle on it. Nothing downstream could tell, because the only signal was
     * the status code.
     *
     * An id is what proves WhatsApp took it. Without one this reports a failure so the
     * next channel gets a turn, which is the whole point of having them in order.
     */
    const messageId = body?.messageId ?? body?.id;
    if (!messageId) {
      return { ok: false, failure: { kind: "not_delivered" } };
    }
    return { ok: true, messageId };
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
  const notStarted =
    response.status === 400 && SESSION_DOWN.some((wording) => detail.includes(wording));
  if (notStarted || response.status === 409) {
    if (allowRevive && (await revive(session)))
      return attemptSend({ session, to, text, allowRevive: false });
    return notStarted
      ? { ok: false, failure: { kind: "not_started", detail } }
      : { ok: false, failure: { kind: "not_connected", detail } };
  }
  // The session we were sending from is gone: re-created, or renamed out from under us.
  // Drop the cached id and let one retry resolve it again, which is what makes a re-pair
  // recover by itself instead of waiting for a deploy.
  if (response.status === 404 && allowRevive) {
    forgetSession();
    return { ok: false, failure: { kind: "not_connected", detail } };
  }

  return { ok: false, failure: { kind: "rejected", status: response.status, detail } };
}

/** Which failures are about this session, and so worth trying another one for. */
function worthAnotherSession(failure: WhatsAppFailure) {
  switch (failure.kind) {
    // The account, the engine, or the recipient as this account can see it. Another
    // linked account may be restricted differently, running, or already know the number.
    case "restricted":
    case "not_started":
    case "not_connected":
    case "not_delivered":
    case "no_such_recipient":
    case "rejected":
      return true;
    // A bad key and an unreachable server are true of every session on that server, and
    // hammering the rest only makes a shopkeeper wait longer for the same answer.
    case "unauthorized":
    case "unreachable":
    case "unconfigured":
    case "no_session":
      return false;
  }
}

/**
 * Gets a code onto WhatsApp, from whichever linked account can still send one.
 *
 * A single account is a single point of failure, and WhatsApp is the thing that fails it:
 * `reachout_timelock` is a six-hour ban on reaching people who have not written to you,
 * which is the definition of sending a verification code. So this tries every session
 * that is ready and unrestricted, configured one first, and only gives up when none of
 * them took it.
 *
 * The revive path is kept for the case that recovers on its own — a session that is
 * merely down — and runs only when nothing was ready to begin with.
 */
export async function sendWhatsApp({
  to,
  text,
}: {
  to: string;
  text: string;
}): Promise<WhatsAppResult> {
  if (!openWaConfigured) return { ok: false, failure: { kind: "unconfigured" } };

  const candidates = await sendableSessions();
  if (!candidates) {
    return {
      ok: false,
      failure: { kind: "unreachable", detail: "could not list the sessions" },
    };
  }
  if (candidates.length === 0) {
    return { ok: false, failure: { kind: "no_session", name: env.OPENWA_SESSION_ID } };
  }

  let last: WhatsAppFailure = { kind: "no_session", name: env.OPENWA_SESSION_ID };

  for (const candidate of candidates) {
    const result = await attemptSend({ session: candidate.id, to, text });
    if (result.ok) return result;

    last = result.failure;
    if (!worthAnotherSession(result.failure)) return result;
  }

  return { ok: false, failure: last };
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
      return failure.name
        ? `the OpenWA server has no session called "${failure.name}"`
        : "the OpenWA server has no linked WhatsApp account to send from";
    case "not_delivered":
      return "OpenWA accepted the send but gave no message id, so WhatsApp did not take it";
    case "restricted":
      return (
        `WhatsApp has restricted this account (${failure.code})` +
        `${failure.until ? `, until ${failure.until}` : ""}` +
        " — it accepts sends and delivers none of them"
      );
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
