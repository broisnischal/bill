import { integer, sqliteTable, text } from "drizzle-orm/sqlite-core";

/**
 * The last verification code sent to a number, kept only while `OTP_DEBUG` is on and no
 * SMS gateway is configured.
 *
 * A Worker holds nothing in memory between requests, so the code a shopkeeper needs
 * cannot sit in a map on the server that happened to send it. It goes here, is read back
 * once over `/api/v1/dev/otp`, and expires on its own. Verification itself is untouched:
 * this only re-opens the envelope the SMS would have arrived in.
 */
export const devSmsCode = sqliteTable("dev_sms_code", {
  phoneNumber: text("phone_number").primaryKey(),
  code: text("code").notNull(),
  sentAt: integer("sent_at", { mode: "timestamp" }).notNull(),
});
