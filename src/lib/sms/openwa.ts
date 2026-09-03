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
}: {
  to: string;
  text: string;
}): Promise<WhatsAppResult> {
  if (!openWaConfigured) return { ok: false, failure: { kind: "unconfigured" } };

  const base = env.OPENWA_BASE_URL!.replace(/\/+$/, "");
  const url = `${base}/api/sessions/${env.OPENWA_SESSION_ID}/messages/send-text`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        // OpenWA names the header exactly this. It is an API key, not a bearer token.
        "X-API-Key": env.OPENWA_API_KEY!,
      },
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
  if (response.status === 400) return { ok: false, failure: { kind: "not_started", detail } };
  if (response.status === 409) return { ok: false, failure: { kind: "not_connected", detail } };
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
