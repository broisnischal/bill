import * as z from "zod";

import { bsDateSchema, optionalPanSchema, panSchema } from "#/lib/nepali/validators.ts";

/** Shapes shared by the onboarding form, the settings form and the server functions. */

const optionalText = (max: number) =>
  z
    .string()
    .trim()
    .max(max)
    .optional()
    .transform((value) => value || undefined);

export const businessTypes = [
  { value: "sole_proprietorship", label: "Sole proprietorship" },
  { value: "partnership", label: "Partnership firm" },
  { value: "private_limited", label: "Private limited company" },
  { value: "public_limited", label: "Public limited company" },
  { value: "cooperative", label: "Cooperative" },
  { value: "ngo", label: "NGO / INGO" },
  { value: "other", label: "Other" },
] as const;

export const provinces = [
  "Koshi",
  "Madhesh",
  "Bagmati",
  "Gandaki",
  "Lumbini",
  "Karnali",
  "Sudurpashchim",
] as const;

export const storeRegistrationSchema = z.object({
  name: z.string().trim().min(2, "Business name is required").max(200),
  nameNepali: optionalText(200),
  tradeName: optionalText(200),
  pan: panSchema,
  /**
   * PAN unless the shop says otherwise. VAT registration is a separate filing with the
   * IRD that most small shops do not have, and defaulting to it had them charging 13%
   * on bills they are not registered to charge tax on.
   */
  taxpayerType: z.enum(["vat", "pan"]).default("pan"),
  registrationDateBs: bsDateSchema,
  registrationNumber: optionalText(60),
  businessType: z.enum(businessTypes.map((type) => type.value)),
  taxOffice: optionalText(120),

  address: z.string().trim().min(2, "Address is required").max(200),
  ward: z.coerce.number().int().min(1).max(35).optional(),
  municipality: optionalText(120),
  district: optionalText(120),
  province: z.enum(provinces).optional(),

  phone: optionalText(30),
  email: z
    .email()
    .optional()
    .or(z.literal("").transform(() => undefined)),
  website: optionalText(200),
});

export type StoreRegistrationInput = z.infer<typeof storeRegistrationSchema>;

export const storeSettingsSchema = storeRegistrationSchema.extend({
  invoicePrefix: z
    .string()
    .trim()
    .max(10)
    .regex(/^[A-Za-z0-9-]*$/, "Letters, digits and dashes only")
    .default(""),
  printFooterNote: optionalText(200),
  bankDetails: optionalText(300),
  cbmsEnabled: z.boolean().default(false),
  cbmsUsername: optionalText(120),
  /** Blank means "leave the stored password alone". */
  cbmsPassword: optionalText(200),
});

export type StoreSettingsInput = z.infer<typeof storeSettingsSchema>;

export const customerSchema = z.object({
  id: z.string().optional(),
  name: z.string().trim().min(1, "Customer name is required").max(200),
  pan: optionalPanSchema,
  address: optionalText(200),
  phone: optionalText(30),
  email: z
    .email()
    .optional()
    .or(z.literal("").transform(() => undefined)),
});

/** What a business may upload, and for which slot. */
export const storeDocumentKindSchema = z.enum(["pan", "registration", "tax_clearance"]);

/** A reviewer's decision. A refusal has to say why, because the shop reads it. */
export const storeReviewSchema = z.discriminatedUnion("decision", [
  z.object({ storeId: z.string().min(1), decision: z.literal("approved") }),
  z.object({
    storeId: z.string().min(1),
    decision: z.literal("rejected"),
    note: z.string().trim().min(10, "Say what has to be fixed, so the shop can fix it").max(500),
  }),
]);

export const itemSchema = z.object({
  id: z.string().optional(),
  name: z.string().trim().min(1, "Item name is required").max(200),
  description: optionalText(500),
  hsCode: optionalText(20),
  sku: optionalText(60),
  barcode: optionalText(40),
  unit: z.string().trim().min(1).max(20).default("pcs"),
  unitPricePaisa: z.int().nonnegative(),
  /** Thousandths of a unit. Null leaves the shelf count alone; this shop does not keep one. */
  stockThousandths: z.int().min(0).nullable().optional(),
  tags: z.array(z.string().trim().min(1).max(24)).max(8).optional(),
  vatApplicable: z.boolean().default(true),
  active: z.boolean().default(true),
});
