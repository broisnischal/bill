import "@tanstack/react-start/server-only";
import { createCipheriv, createDecipheriv, hkdfSync, randomBytes } from "node:crypto";

import { env } from "#/env/server.ts";

/**
 * CBMS passwords are credentials to a tax authority, so they are encrypted at rest with
 * AES-256-GCM under a key derived from the app secret. Rotating BETTER_AUTH_SECRET
 * invalidates stored CBMS passwords, which then have to be re-entered in settings.
 */
const key = Buffer.from(
  hkdfSync("sha256", env.BETTER_AUTH_SECRET, "bill.cbms.v1", "cbms-credential", 32),
);

export function encryptSecret(plaintext: string) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  return [
    iv.toString("base64"),
    cipher.getAuthTag().toString("base64"),
    ciphertext.toString("base64"),
  ].join(".");
}

export function decryptSecret(encoded: string) {
  const [iv, tag, ciphertext] = encoded.split(".");
  if (!iv || !tag || !ciphertext) throw new Error("Malformed encrypted secret");
  const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(iv, "base64"));
  decipher.setAuthTag(Buffer.from(tag, "base64"));
  return Buffer.concat([
    decipher.update(Buffer.from(ciphertext, "base64")),
    decipher.final(),
  ]).toString("utf8");
}
