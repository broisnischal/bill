import * as z from "zod";

import { fiscalYearSchema, optionalPanSchema } from "#/lib/nepali/validators.ts";

import { lineInputSchema } from "./calc";

export const paymentMethods = [
  { value: "cash", label: "Cash" },
  { value: "credit", label: "Credit" },
  { value: "card", label: "Card" },
  { value: "bank", label: "Bank transfer" },
  { value: "cheque", label: "Cheque" },
  { value: "esewa", label: "eSewa" },
  { value: "khalti", label: "Khalti" },
  { value: "fonepay", label: "Fonepay" },
  { value: "connectips", label: "ConnectIPS" },
] as const;

export const createInvoiceSchema = z.object({
  invoiceType: z.enum(["tax_invoice", "abbreviated_tax_invoice"]).default("tax_invoice"),
  customerId: z.string().optional(),
  buyerName: z.string().trim().min(1, "Buyer name is required").max(200),
  buyerPan: optionalPanSchema,
  buyerAddress: z.string().trim().max(200).optional(),
  buyerPhone: z.string().trim().max(30).optional(),
  paymentMethod: z.enum(paymentMethods.map((method) => method.value)).default("cash"),
  notes: z.string().trim().max(500).optional(),
  discountPaisa: z.int().nonnegative().default(0),
  /** Adds an unrecognised buyer to the customer list while the bill is written. */
  saveCustomer: z.boolean().default(false),
  lines: z.array(lineInputSchema).min(1, "Add at least one line"),
});

export type CreateInvoiceInput = z.infer<typeof createInvoiceSchema>;

/**
 * A bill a till already printed, arriving from the device.
 *
 * Everything that was on the paper is fixed by the time we see it, so the identifiers,
 * the number and the timestamps all come from the device and the server's job is to
 * prove they are legitimate rather than to invent them. The declared total is included
 * so a device whose arithmetic disagrees with ours is caught instead of silently
 * overwritten: a customer is holding that piece of paper.
 */
export const deviceInvoiceSchema = createInvoiceSchema.extend({
  /** Generated on the device, and the idempotency key for the whole push. */
  id: z.uuid(),
  shareToken: z.string().regex(/^[a-f0-9]{32}$/, "Malformed share token"),
  leaseId: z.uuid(),
  sequence: z.int().positive(),
  fiscalYear: fiscalYearSchema,
  /** The moment printed on the bill, and the moment the device queued it for sync. */
  issuedAt: z.iso.datetime(),
  queuedAt: z.iso.datetime(),
  /** What the device printed as the grand total, in paisa. Checked, not trusted. */
  totalPaisa: z.int().positive(),
  /** What was handed over at the counter. Zero on a bill sold entirely on credit. */
  paidAtIssuePaisa: z.int().nonnegative().default(0),
  /** When the shop expects the rest, in Bikram Sambat. Credit sales only. */
  dueMiti: z.string().trim().max(10).optional(),
  /** Signed handle from a scanned customer card, so the bill reaches their own app. */
  shopperLink: z.string().trim().max(200).optional(),
});

export type DeviceInvoiceInput = z.infer<typeof deviceInvoiceSchema>;

/** Money received against a bill, recorded on the till and pushed on the next sync. */
export const paymentSchema = z.object({
  id: z.uuid(),
  invoiceId: z.uuid(),
  amountPaisa: z.int().positive("A payment has to be more than zero"),
  method: z.enum(paymentMethods.map((method) => method.value)).default("cash"),
  receivedAt: z.iso.datetime(),
  miti: z.string().trim().max(10),
  note: z.string().trim().max(200).optional(),
});

export type PaymentInput = z.infer<typeof paymentSchema>;

export const cancelInvoiceSchema = z.object({
  invoiceId: z.string().min(1),
  reason: z.string().trim().min(5, "Give the reason the bill is being cancelled").max(300),
});

export const creditNoteSchema = z.object({
  invoiceId: z.string().min(1),
  reason: z.string().trim().min(5, "Give the reason for the return").max(300),
});

export const printFormatSchema = z.enum(["a4", "thermal80"]);

export const invoiceListSchema = z.object({
  fiscalYear: z.string().optional(),
  status: z.enum(["all", "active", "cancelled"]).default("all"),
  search: z.string().trim().max(100).optional(),
  page: z.int().min(1).default(1),
  pageSize: z.int().min(1).max(100).default(25),
});
