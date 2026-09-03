import { createFileRoute } from "@tanstack/react-router";

import { ApiError, json, run } from "#/lib/api/v1.ts";
import { nepaliMobileSchema } from "#/lib/nepali/validators.ts";
import { devInboxAllows, readDevOtp } from "#/lib/sms/dev-inbox.ts";

/**
 * The code that was "sent" to a number, for development only.
 *
 * Without an SMS gateway there is no way to receive an OTP, which would make signing in
 * on a phone impossible locally. This hands back the code that was logged instead. It
 * It answers only when there is genuinely no gateway configured, `OTP_DEBUG` is on, and
 * the number asked about is one of `OTP_DEBUG_PHONES`. Anything else gets a 404: a
 * readable OTP for an arbitrary number is a way to sign in as that shop and read its
 * papers, which is not a trade any amount of local convenience is worth.
 */
export const Route = createFileRoute("/api/v1/dev/otp")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const raw = new URL(request.url).searchParams.get("phone") ?? "";
          const phoneNumber = nepaliMobileSchema.parse(raw);

          // Only the numbers doing the testing. Anyone else's code is not ours to hand
          // out, and this route is reachable by anybody who knows the path.
          if (!devInboxAllows(phoneNumber)) throw new ApiError(404, "not_found", "Not found");
          const code = await readDevOtp(phoneNumber);
          if (!code) throw new ApiError(404, "no_code", "No code has been sent to that number");

          return json({ phoneNumber, code });
        }),
    },
  },
});
