import "@tanstack/react-start/server-only";
import { env } from "#/env/server.ts";

import { recordDevSms } from "./dev-inbox";

/**
 * Sparrow SMS, the gateway most Nepali businesses already hold an account with.
 * Delivery to a Nepali mobile is a single POST; anything else is treated as a failure
 * so the caller can tell the user the code did not go out.
 *
 * Without a token configured the code is logged instead of sent, which is how local
 * development and the e2e run get through signup with no gateway.
 */
export async function sendSms({ to, text }: { to: string; text: string }) {
  if (!env.SPARROW_SMS_TOKEN) {
    // No gateway account: the message goes to the console and to the dev inbox, which is
    // what lets the app sign in during development without one.
    console.info(`[sms] to ${to}: ${text}`);
    recordDevSms(to, text);
    return;
  }

  const response = await fetch("https://api.sparrowsms.com/v2/sms/", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      token: env.SPARROW_SMS_TOKEN,
      from: env.SPARROW_SMS_FROM,
      // Sparrow wants the national number without the country code.
      to: to.replace(/^\+977/, ""),
      text,
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`SMS gateway rejected the message (${response.status}): ${body.slice(0, 200)}`);
  }
}
