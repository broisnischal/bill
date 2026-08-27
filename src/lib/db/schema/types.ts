/**
 * Database value types.
 *
 * Following .agents/database.md, these are plain string unions applied to `text()`
 * columns with `$type<T>()` instead of native pg enums.
 */

/** Legal form of the registered business, as classified by the OCR/IRD registration. */
export type BusinessType =
  | "sole_proprietorship"
  | "partnership"
  | "private_limited"
  | "public_limited"
  | "cooperative"
  | "ngo"
  | "other";

/**
 * Whether the store is VAT registered or PAN only.
 * PAN-only taxpayers issue invoices without a VAT line and stay out of CBMS.
 */
export type TaxpayerType = "vat" | "pan";

/**
 * Document type.
 * - `tax_invoice` (कर बीजक): full VAT invoice, Rule 17 of the VAT Rules 2053.
 * - `abbreviated_tax_invoice` (संक्षिप्त कर बीजक): retail counter sale, Rule 18,
 *   allowed up to NPR 10,000 and never usable by the buyer to claim input credit.
 * - `credit_note` (क्रेडिट नोट): the only lawful way to reverse an issued invoice, Rule 20.
 */
export type InvoiceType = "tax_invoice" | "abbreviated_tax_invoice" | "credit_note";

/** An issued invoice is never deleted. It is active, or it is cancelled with a reason. */
export type InvoiceStatus = "active" | "cancelled";

export type PaymentMethod =
  | "cash"
  | "credit"
  | "card"
  | "bank"
  | "cheque"
  | "esewa"
  | "khalti"
  | "fonepay"
  | "connectips";

/** State of the real-time push to the IRD Central Billing Monitoring System. */
export type IrdSyncStatus = "not_applicable" | "pending" | "synced" | "failed";

/** Append-only audit trail actions. The e-billing procedure requires who did what, when. */
export type AuditAction =
  | "invoice_created"
  | "invoice_printed"
  | "invoice_reprinted"
  | "invoice_cancelled"
  | "credit_note_issued"
  | "pdf_archived"
  | "ird_synced"
  | "ird_sync_failed";

/** Audit metadata stays flat and printable: an auditor reads it, so nothing nests. */
export type AuditMeta = Record<string, string | number | boolean | null>;

export type StoreRole = "owner" | "manager" | "cashier";

/** Paper the bill is rendered for. A4 for the tax invoice, 80mm for the counter printer. */
export type PrintFormat = "a4" | "thermal80";
