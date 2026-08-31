import { createFileRoute } from "@tanstack/react-router";

import { ApiError, json, run } from "#/lib/api/v1.ts";
import { nepaliMobileSchema } from "#/lib/nepali/validators.ts";
import { devInboxEnabled, readDevOtp } from "#/lib/sms/dev-inbox.ts";

/**
 * The code that was "sent" to a number, for development only.
 *
 * Without an SMS gateway there is no way to receive an OTP, which would make signing in
 * on a phone impossible locally. This hands back the code that was logged instead. It
 * answers only when there is genuinely no gateway configured and the build is not
 * production, so on a deployed server the route behaves as though it does not exist.
 */
export const Route = createFileRoute("/api/v1/dev/otp")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          if (!devInboxEnabled) throw new ApiError(404, "not_found", "Not found");

          const raw = new URL(request.url).searchParams.get("phone") ?? "";
          const phoneNumber = nepaliMobileSchema.parse(raw);
          const code = readDevOtp(phoneNumber);
          if (!code) throw new ApiError(404, "no_code", "No code has been sent to that number");

          return json({ phoneNumber, code });
        }),
    },
  },
});
