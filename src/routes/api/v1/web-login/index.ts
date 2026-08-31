import { createFileRoute } from "@tanstack/react-router";
import { serializeSignedCookie } from "better-call";
import * as z from "zod";

import { ApiError, json, parseBody, requireUser, run } from "#/lib/api/v1.ts";
import { auth } from "#/lib/auth/auth.ts";
import {
  approveWebLogin,
  beginWebLogin,
  claimWebLogin,
  describeWebLogin,
  noteFailedAttempt,
  pollWebLogin,
} from "#/lib/auth/web-login.ts";

/**
 * Signing a browser in from a phone.
 *
 * Three parties and one code: the browser starts a request and waits, the phone approves
 * it, and the browser collects a session. The code on its own is worth nothing — approving
 * it needs a device that already holds a session, so what is really being checked is that
 * someone has the shopkeeper's phone in their hand.
 *
 * It exists because the alternative is worse for the people using this. A shopkeeper at a
 * till with the app already open should not have to wait on an SMS that costs money and
 * arrives late, and typing a six-character code they can see into a phone they are
 * holding is the shortest honest path to a browser session.
 */

const approveSchema = z.object({
  code: z.string().trim().min(4).max(12),
  approve: z.boolean().default(true),
});

export const Route = createFileRoute("/api/v1/web-login/")({
  server: {
    handlers: {
      /**
       * Starts a request, or approves one.
       *
       * Starting needs no session, because the browser has none yet — that is the whole
       * point. Approving needs one, because that is the whole security model.
       */
      POST: ({ request }) =>
        run(async () => {
          const url = new URL(request.url);

          if (url.searchParams.get("action") !== "approve") {
            const started = await beginWebLogin({
              userAgent: request.headers.get("user-agent") ?? undefined,
              ipAddress: request.headers.get("x-forwarded-for")?.split(",")[0]?.trim(),
            });
            return json(started, 201);
          }

          const user = await requireUser(request);
          const { code, approve } = await parseBody(request, approveSchema);

          const result = await approveWebLogin({ code, userId: user.id, approve });
          if (!result.ok) {
            // A wrong code counts against the window it was aimed at, so guessing runs
            // out rather than continuing indefinitely.
            await noteFailedAttempt(code);
            throw new ApiError(
              result.reason === "too_many_attempts" ? 429 : 404,
              result.reason,
              refusal(result.reason),
            );
          }

          return json({ approved: approve });
        }),

      /**
       * The browser, waiting.
       *
       * Once approved it collects exactly one session and the code is spent, so a code
       * glimpsed over a shoulder cannot be redeemed a second time. The cookie set here is
       * signed the same way Better Auth signs its own, because it is the same session an
       * SMS sign-in would have produced.
       */
      GET: ({ request }) =>
        run(async () => {
          const url = new URL(request.url);
          const pollToken = z
            .string()
            .trim()
            .min(16)
            .max(64)
            .parse(url.searchParams.get("pollToken") ?? "");

          const state = await pollWebLogin(pollToken);
          if (!state) throw new ApiError(404, "not_found", "That sign-in has expired");
          if (state.status !== "approved") return json({ status: state.status });

          const userId = await claimWebLogin(pollToken);
          if (!userId) return json({ status: "claimed" });

          const context = await auth.$context;
          const session = await context.internalAdapter.createSession(userId, false);
          if (!session) throw new ApiError(500, "session_failed", "Could not sign you in");

          const cookie = context.authCookies.sessionToken;
          const header = await serializeSignedCookie(cookie.name, session.token, context.secret, {
            ...cookie.attributes,
            maxAge: context.sessionConfig.expiresIn,
          });

          return new Response(JSON.stringify({ status: "signed_in" }), {
            headers: {
              "content-type": "application/json; charset=utf-8",
              "set-cookie": header,
            },
          });
        }),
    },
  },
});

/** What to tell someone whose code did not work. Never says whether a code exists. */
function refusal(reason: "not_found" | "expired" | "too_many_attempts") {
  switch (reason) {
    case "expired":
      return "That code has expired. Ask the computer for a new one.";
    case "too_many_attempts":
      return "Too many tries. Ask the computer for a new code.";
    default:
      return "That code did not match anything. Check it and try again.";
  }
}

export { describeWebLogin };
