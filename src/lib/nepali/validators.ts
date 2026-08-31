import * as z from "zod";

/** A PAN is exactly nine digits. A VAT-registered taxpayer's VAT number is that same PAN. */
export const panSchema = z
  .string()
  .trim()
  .regex(/^\d{9}$/, "PAN must be exactly 9 digits");

export const optionalPanSchema = z
  .string()
  .trim()
  .optional()
  .transform((value) => value || undefined)
  .pipe(panSchema.optional());

/** Nepali landline or mobile, with or without the +977 country code. */
export const phoneSchema = z
  .string()
  .trim()
  .regex(/^(\+977[- ]?)?\d{7,10}$/, "Enter a valid Nepali phone number");

/** Bikram Sambat date as `YYYY-MM-DD`, e.g. 2081-04-01. */
export const bsDateSchema = z
  .string()
  .trim()
  .regex(/^2[01]\d{2}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[0-2])$/, "Use a BS date like 2081-04-01");

/** Fiscal year in IRD notation, e.g. 2082.083. */
export const fiscalYearSchema = z
  .string()
  .trim()
  .regex(/^2\d{3}\.0?\d{2,3}$/, "Use a fiscal year like 2082.083");

/**
 * A Nepali mobile in E.164, e.g. `+9779812345678`. Phone signup stores this form, so a
 * number typed as `9812345678`, `098-1234 5678` or `+977 9812345678` is one account.
 * Returns null when the number is not a Nepali mobile.
 */
export function normalizeNepaliMobile(value: string) {
  const digits = value.replace(/\D/g, "");
  const national = (digits.startsWith("977") ? digits.slice(3) : digits).replace(/^0+/, "");
  return /^9\d{9}$/.test(national) ? `+977${national}` : null;
}

export const nepaliMobileSchema = z
  .string()
  .trim()
  .transform((value, ctx) => {
    const normalized = normalizeNepaliMobile(value);
    if (!normalized) {
      ctx.addIssue({ code: "custom", message: "Enter a 10-digit Nepali mobile number" });
      return z.NEVER;
    }
    return normalized;
  });
