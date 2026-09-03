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

/** Why a send did not happen, for a log that has to be readable at a counter. */
export type WhatsAppFailure =
  | { kind: "unconfigured" }
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
async function revive(): Promise<boolean> {
  try {
    await fetch(`${baseUrl()}/api/sessions/${env.OPENWA_SESSION_ID}/start`, {
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
      const response = await fetch(`${baseUrl()}/api/sessions/${env.OPENWA_SESSION_ID}`, {
        headers: headers(),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      if (!response.ok) continue;
      const session = (await response.json()) as { status?: string };
      if (session.status === "ready") return true;
      // A session wanting a QR scan is not coming back without a person holding a phone.
      if (session.status === "failed" || session.status === "qr") return false;
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

  const url = `${baseUrl()}/api/sessions/${env.OPENWA_SESSION_ID}/messages/send-text`;

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
  // A session that is down, or still connecting, is the one failure worth doing something
  // about rather than reporting. Once only: `allowRevive` is false on the retry, so a
  // session that will not come back cannot loop.
  if (response.status === 400 || response.status === 409) {
    if (allowRevive && (await revive())) return sendWhatsApp({ to, text, allowRevive: false });
    return response.status === 400
      ? { ok: false, failure: { kind: "not_started", detail } }
      : { ok: false, failure: { kind: "not_connected", detail } };
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
