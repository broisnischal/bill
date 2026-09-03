import "@tanstack/react-start/server-only";
import { recordDevSms } from "./dev-inbox";
import { describeFailure, openWaConfigured, sendWhatsApp } from "./openwa";
import { sendSms, sparrowConfigured } from "./sparrow";

/**
 * Getting a verification code to a shopkeeper.
 *
 * Three channels, tried in the order they cost: WhatsApp through our own OpenWA server,
 * which is free; Sparrow SMS, which is per-message and needs an account; and then the
 * inbox the code is merely written to, readable only by the numbers named in
 * OTP_DEBUG_PHONES.
 *
 * The last one is not a channel, it is the way in when the first two are down, and it is
 * the reason a WhatsApp session dying at 9pm does not lock everybody out. It is also the
 * dangerous one: a readable code is a way to sign in as whoever the number belongs to,
 * which is why it answers for a named list and never for whoever asks.
 *
 * The code is written to that inbox on every attempt, delivered or not. There is no
 * reason to make the allowlisted numbers depend on WhatsApp being up, and a code that
 * was successfully sent to somebody else's WhatsApp is still not readable by anyone.
 */
export type OtpChannel = "whatsapp" | "sms" | "inbox";

export async function deliverOtp({ to, code }: { to: string; code: string }): Promise<OtpChannel> {
  const text = `${code} is your Bill verification code. It expires in 5 minutes.`;

  // Held first, so the way in exists even if what follows throws.
  await recordDevSms(to, text);

  if (openWaConfigured) {
    const result = await sendWhatsApp({ to, text });
    if (result.ok) return "whatsapp";
    // Not thrown: there may be another channel, and if there is not, the caller still
    // wants the sign-in to proceed so an allowlisted number can read the code.
    console.warn(`[otp] WhatsApp to ${to} failed — ${describeFailure(result.failure)}`);
  }

  if (sparrowConfigured) {
    try {
      await sendSms({ to, text });
      return "sms";
    } catch (error) {
      console.warn(
        `[otp] SMS to ${to} failed — ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  console.info(
    `[otp] nothing delivered to ${to}; the code is readable only if that number is allowlisted`,
  );
  return "inbox";
}
