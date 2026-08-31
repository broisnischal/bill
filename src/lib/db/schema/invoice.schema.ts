import {
  bigint,
  boolean,
  index,
  integer,
  jsonb,
  pgTable,
  primaryKey,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core";

import { user } from "./auth.schema";
import { device, invoiceNumberLease } from "./device.schema";
import { customer, item, store } from "./store.schema";
import type {
  AuditAction,
  AuditMeta,
  InvoiceStatus,
  InvoiceType,
  IrdSyncStatus,
  PaymentMethod,
} from "./types";

/**
 * One row per (store, fiscal year, document type), holding the next number to hand out.
 * Numbers are allocated under `SELECT ... FOR UPDATE` inside the invoice transaction so
 * the series stays sequential with no gaps, which is what the e-billing procedure requires.
 */
export const invoiceCounter = pgTable(
  "invoice_counter",
  {
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    fiscalYear: text("fiscal_year").notNull(),
    invoiceType: text("invoice_type").$type<InvoiceType>().notNull(),
    nextSequence: integer("next_sequence").notNull().default(1),
  },
  (table) => [primaryKey({ columns: [table.storeId, table.fiscalYear, table.invoiceType] })],
);

/**
 * An issued document. Financial columns are written once and never updated: a mistake is
 * corrected by cancelling the bill or issuing a credit note against it, never by an edit.
 * Buyer details are snapshotted rather than joined, so a later customer edit cannot
 * change what a printed bill said.
 *
 * All money is stored in paisa as integers. No floats touch an amount.
 */
export const invoice = pgTable(
  "invoice",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "restrict" }),

    /** Nepali fiscal year in IRD notation, e.g. "2082.083" (Shrawan 1 to Ashad end). */
    fiscalYear: text("fiscal_year").notNull(),
    invoiceType: text("invoice_type").$type<InvoiceType>().notNull().default("tax_invoice"),
    /** Position in the series for this store, fiscal year and type. Starts at 1. */
    sequence: integer("sequence").notNull(),
    /** Printed number, e.g. "2082.083-000123". Unique within the store. */
    invoiceNumber: text("invoice_number").notNull(),

    /** Credit notes point back at the invoice they reverse. */
    refInvoiceId: text("ref_invoice_id"),
    refInvoiceNumber: text("ref_invoice_number"),
    /** Rule 20 requires the reason a bill was reversed or cancelled to be recorded. */
    reason: text("reason"),

    customerId: text("customer_id").references(() => customer.id, { onDelete: "set null" }),
    /**
     * The shopper this bill was made out to, when the shop scanned their card.
     *
     * It is what puts the bill in their own app without them scanning the printed QR,
     * and it is set from a signed link rather than from anything the till can invent.
     */
    shopperUserId: text("shopper_user_id").references(() => user.id, { onDelete: "set null" }),
    buyerName: text("buyer_name").notNull(),
    buyerPan: text("buyer_pan"),
    buyerAddress: text("buyer_address"),
    buyerPhone: text("buyer_phone"),

    issuedAt: timestamp("issued_at", { withTimezone: true }).notNull(),
    /** Bill date in Bikram Sambat, "YYYY-MM-DD", as printed (मिति). */
    miti: text("miti").notNull(),

    subTotalPaisa: bigint("sub_total_paisa", { mode: "number" }).notNull(),
    discountPaisa: bigint("discount_paisa", { mode: "number" }).notNull().default(0),
    taxableAmountPaisa: bigint("taxable_amount_paisa", { mode: "number" }).notNull(),
    nonTaxableAmountPaisa: bigint("non_taxable_amount_paisa", { mode: "number" })
      .notNull()
      .default(0),
    /** VAT rate in basis points at the time of issue: 1300 is 13%. */
    vatRateBp: integer("vat_rate_bp").notNull().default(1300),
    vatAmountPaisa: bigint("vat_amount_paisa", { mode: "number" }).notNull().default(0),
    roundOffPaisa: bigint("round_off_paisa", { mode: "number" }).notNull().default(0),
    totalPaisa: bigint("total_paisa", { mode: "number" }).notNull(),
    amountInWords: text("amount_in_words").notNull(),

    paymentMethod: text("payment_method").$type<PaymentMethod>().notNull().default("cash"),
    /**
     * What was handed over at the counter. A cash sale settles in full here; a credit
     * sale starts at zero or at a part payment, and the rest arrives as `invoice_payment`
     * rows. What is still owed is the total less this and less those.
     */
    paidAtIssuePaisa: bigint("paid_at_issue_paisa", { mode: "number" }).notNull().default(0),
    /** When the shop expects to be paid. Only meaningful on a credit sale. */
    dueMiti: text("due_miti"),
    notes: text("notes"),

    status: text("status").$type<InvoiceStatus>().notNull().default("active"),
    cancelledAt: timestamp("cancelled_at", { withTimezone: true }),
    cancelledBy: text("cancelled_by").references(() => user.id, { onDelete: "set null" }),

    /** Print tracking. The first copy is the original; every later one prints as a copy. */
    printCount: integer("print_count").notNull().default(0),
    firstPrintedAt: timestamp("first_printed_at", { withTimezone: true }),
    lastPrintedAt: timestamp("last_printed_at", { withTimezone: true }),

    enteredById: text("entered_by_id").references(() => user.id, { onDelete: "set null" }),
    /** Snapshot of the biller's name, so the bill still reads correctly if the user is removed. */
    enteredByName: text("entered_by_name").notNull(),

    irdSyncStatus: text("ird_sync_status").$type<IrdSyncStatus>().notNull().default("pending"),
    irdSyncedAt: timestamp("ird_synced_at", { withTimezone: true }),
    irdSyncAttempts: integer("ird_sync_attempts").notNull().default(0),
    irdLastError: text("ird_last_error"),
    /** What CBMS answered, kept verbatim as text so the audit record cannot be reshaped. */
    irdResponse: jsonb("ird_response").$type<{ status: number; body: string }>(),
    /** False when the bill was queued offline and pushed later, which CBMS wants to know. */
    isRealtime: boolean("is_realtime").notNull().default(true),

    /** Which till wrote the bill, and the lease its number came from when written offline. */
    deviceId: text("device_id").references(() => device.id, { onDelete: "restrict" }),
    leaseId: text("lease_id").references(() => invoiceNumberLease.id, { onDelete: "restrict" }),
    /** When the device wrote it, against `createdAt`, when the server received it. */
    queuedAt: timestamp("queued_at", { withTimezone: true }),

    /**
     * Opaque handle printed as a QR code so the buyer can file the bill in their own app.
     * Generated on the device, because the QR has to print with no network.
     */
    shareToken: text("share_token")
      .notNull()
      .$defaultFn(() => crypto.randomUUID().replace(/-/g, "")),

    /** Archived PDF in object storage, plus its digest so tampering is detectable. */
    pdfKey: text("pdf_key"),
    pdfSha256: text("pdf_sha256"),
    pdfBytes: integer("pdf_bytes"),

    createdAt: timestamp("created_at", { withTimezone: true }).defaultNow().notNull(),
  },
  (table) => [
    uniqueIndex("invoice_store_number_uidx").on(table.storeId, table.invoiceNumber),
    uniqueIndex("invoice_share_token_uidx").on(table.shareToken),
    uniqueIndex("invoice_series_uidx").on(
      table.storeId,
      table.fiscalYear,
      table.invoiceType,
      table.sequence,
    ),
    index("invoice_store_issued_idx").on(table.storeId, table.issuedAt),
    index("invoice_store_fy_idx").on(table.storeId, table.fiscalYear),
    index("invoice_sync_idx").on(table.irdSyncStatus),
    index("invoice_ref_idx").on(table.refInvoiceId),
    index("invoice_shopper_idx").on(table.shopperUserId, table.issuedAt),
  ],
);

/** A line on a bill. Quantity is stored in thousandths so 2.5 kg is 2500. */
export const invoiceItem = pgTable(
  "invoice_item",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    invoiceId: text("invoice_id")
      .notNull()
      .references(() => invoice.id, { onDelete: "restrict" }),
    lineNo: integer("line_no").notNull(),
    itemId: text("item_id").references(() => item.id, { onDelete: "set null" }),

    description: text("description").notNull(),
    hsCode: text("hs_code"),
    unit: text("unit").notNull().default("pcs"),
    quantityMilli: bigint("quantity_milli", { mode: "number" }).notNull(),
    unitPricePaisa: bigint("unit_price_paisa", { mode: "number" }).notNull(),
    discountPaisa: bigint("discount_paisa", { mode: "number" }).notNull().default(0),
    /** False for exempt goods; those amounts land in the invoice's non-taxable total. */
    vatApplicable: boolean("vat_applicable").notNull().default(true),
    lineTotalPaisa: bigint("line_total_paisa", { mode: "number" }).notNull(),
  },
  (table) => [
    uniqueIndex("invoice_item_line_uidx").on(table.invoiceId, table.lineNo),
    index("invoice_item_invoice_idx").on(table.invoiceId),
  ],
);

/**
 * Money received against a bill.
 *
 * A shop that sells on credit is owed the difference between what a bill came to and
 * what has been paid against it, and that difference changes over time. An issued bill
 * is never edited, so payments are their own rows: the bill still says what it always
 * said, and what is outstanding is the sum of what has come in since.
 */
export const invoicePayment = pgTable(
  "invoice_payment",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    invoiceId: text("invoice_id")
      .notNull()
      .references(() => invoice.id, { onDelete: "restrict" }),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "restrict" }),
    amountPaisa: bigint("amount_paisa", { mode: "number" }).notNull(),
    method: text("method").$type<PaymentMethod>().notNull().default("cash"),
    receivedAt: timestamp("received_at", { withTimezone: true }).notNull(),
    /** Bikram Sambat date the money came in, which is how a shop's ledger reads. */
    miti: text("miti").notNull(),
    note: text("note"),
    recordedById: text("recorded_by_id").references(() => user.id, { onDelete: "set null" }),
    deviceId: text("device_id"),
    createdAt: timestamp("created_at", { withTimezone: true }).defaultNow().notNull(),
  },
  (table) => [
    index("invoice_payment_invoice_idx").on(table.invoiceId),
    index("invoice_payment_store_idx").on(table.storeId, table.receivedAt),
  ],
);

/**
 * Append-only trail. Rows are inserted, never updated or deleted, which is the part of
 * the e-billing procedure an auditor actually reads.
 */
export const invoiceAudit = pgTable(
  "invoice_audit",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    invoiceId: text("invoice_id")
      .notNull()
      .references(() => invoice.id, { onDelete: "restrict" }),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "restrict" }),
    action: text("action").$type<AuditAction>().notNull(),
    actorId: text("actor_id").references(() => user.id, { onDelete: "set null" }),
    actorName: text("actor_name"),
    at: timestamp("at", { withTimezone: true }).defaultNow().notNull(),
    ipAddress: text("ip_address"),
    userAgent: text("user_agent"),
    meta: jsonb("meta").$type<AuditMeta>(),
  },
  (table) => [
    index("invoice_audit_invoice_idx").on(table.invoiceId),
    index("invoice_audit_store_at_idx").on(table.storeId, table.at),
  ],
);
