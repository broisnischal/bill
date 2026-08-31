import { createFileRoute } from "@tanstack/react-router";
import * as z from "zod";

import { ApiError, json, requireUser, run } from "#/lib/api/v1.ts";
import { describeWebLogin } from "#/lib/auth/web-login.ts";

/**
 * What a phone is about to let in.
 *
 * Approving a sign-in without being told what it is for is a habit worth not teaching, so
 * the phone looks the code up first and shows the browser it belongs to before it offers
 * an Approve button.
 */
export const Route = createFileRoute("/api/v1/web-login/lookup")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          await requireUser(request);

          const url = new URL(request.url);
          const code = z
            .string()
            .trim()
            .min(4)
            .max(12)
            .parse(url.searchParams.get("code") ?? "");

          const found = await describeWebLogin(code);
          if (!found) {
            throw new ApiError(404, "not_found", "That code did not match anything");
          }

          return json({ browser: describeBrowser(found.userAgent), expiresAt: found.expiresAt });
        }),
    },
  },
});

/**
 * A user agent, as something a shopkeeper can recognise.
 *
 * "Chrome on Windows" tells them whether this is their own computer. The raw string
 * tells them nothing and looks alarming.
 */
function describeBrowser(userAgent: string | null) {
  if (!userAgent) return "A computer";

  const browser = /Edg\//.test(userAgent)
    ? "Edge"
    : /OPR\//.test(userAgent)
      ? "Opera"
      : /Chrome\//.test(userAgent)
        ? "Chrome"
        : /Firefox\//.test(userAgent)
          ? "Firefox"
          : /Safari\//.test(userAgent)
            ? "Safari"
            : "A browser";

  const platform = /Windows/.test(userAgent)
    ? "Windows"
    : /Macintosh|Mac OS/.test(userAgent)
      ? "Mac"
      : /Android/.test(userAgent)
        ? "Android"
        : /iPhone|iPad/.test(userAgent)
          ? "iPhone"
          : /Linux/.test(userAgent)
            ? "Linux"
            : null;

  return platform ? `${browser} on ${platform}` : browser;
}
