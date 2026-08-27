import * as z from "zod";

import { optionalPanSchema } from "#/lib/nepali/validators.ts";

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
