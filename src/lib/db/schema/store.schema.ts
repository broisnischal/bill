import { index, integer, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

import { user } from "./auth.schema";
import type { BusinessType, StoreRole, TaxpayerType } from "./types";

/**
 * A registered business. Everything else in the system hangs off a store, and a
 * store carries the details the IRD requires to be printed on every bill it issues.
 */
export const store = sqliteTable(
  "store",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    ownerId: text("owner_id")
      .notNull()
      .references(() => user.id, { onDelete: "restrict" }),

    // Identity as printed on the bill
    name: text("name").notNull(),
    nameNepali: text("name_nepali"),
    tradeName: text("trade_name"),

    /** Permanent Account Number: 9 digits. For a VAT taxpayer this doubles as the VAT number. */
    pan: text("pan").notNull(),
    taxpayerType: text("taxpayer_type").$type<TaxpayerType>().notNull().default("vat"),
    /** Date the business was registered for VAT/PAN, both calendars, as on the certificate. */
    registrationDate: text("registration_date").notNull(),
    registrationDateBs: text("registration_date_bs").notNull(),
    /** Company/firm registration number from OCR, DoI or the municipality. */
    registrationNumber: text("registration_number"),
    businessType: text("business_type")
      .$type<BusinessType>()
      .notNull()
      .default("sole_proprietorship"),
    /** IRD tax office the taxpayer files under, e.g. "IRO Kathmandu 2". */
    taxOffice: text("tax_office"),

    // Address, as printed
    address: text("address").notNull(),
    ward: integer("ward"),
    municipality: text("municipality"),
    district: text("district"),
    province: text("province"),
    country: text("country").notNull().default("Nepal"),

    phone: text("phone"),
    email: text("email"),
    website: text("website"),
    logoKey: text("logo_key"),

    // Billing behaviour
    /** Prefix in front of the sequence, e.g. "INV" gives INV-2082.083-000001. */
    invoicePrefix: text("invoice_prefix").notNull().default(""),
    vatRateBp: integer("vat_rate_bp").notNull().default(1300),
    printFooterNote: text("print_footer_note"),
    /** Free-text bank details printed on the A4 invoice. */
    bankDetails: text("bank_details"),

    // IRD Central Billing Monitoring System
    cbmsEnabled: integer("cbms_enabled", { mode: "boolean" }).notNull().default(false),
    cbmsUsername: text("cbms_username"),
    /** AES-256-GCM ciphertext. Never written or read in plaintext. See lib/ird/credentials.ts */
    cbmsPasswordEncrypted: text("cbms_password_encrypted"),

    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("store_pan_uidx").on(table.pan),
    index("store_owner_idx").on(table.ownerId),
  ],
);

/** Who may bill on behalf of a store. The owner row is created with the store. */
export const storeMember = sqliteTable(
  "store_member",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    role: text("role").$type<StoreRole>().notNull().default("cashier"),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("store_member_store_user_uidx").on(table.storeId, table.userId),
    index("store_member_user_idx").on(table.userId),
  ],
);

/** A buyer. Invoices snapshot these values, so editing a customer never rewrites history. */
export const customer = sqliteTable(
  "customer",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    pan: text("pan"),
    address: text("address"),
    phone: text("phone"),
    email: text("email"),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [
    index("customer_store_idx").on(table.storeId),
    index("customer_store_name_idx").on(table.storeId, table.name),
  ],
);

/** A sellable good or service. Prices are paisa, so 1 rupee is 100. */
export const item = sqliteTable(
  "item",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    description: text("description"),
    /** Harmonised System code, printed for goods where the buyer needs it. */
    hsCode: text("hs_code"),
    sku: text("sku"),
    /** EAN-13, UPC-A or whatever the packet carries, as scanned off the product. */
    barcode: text("barcode"),
    unit: text("unit").notNull().default("pcs"),
    unitPricePaisa: integer("unit_price_paisa").notNull().default(0),
    /** False for VAT-exempt goods listed in Schedule 1 of the VAT Act. */
    vatApplicable: integer("vat_applicable", { mode: "boolean" }).notNull().default(true),
    active: integer("active", { mode: "boolean" }).notNull().default(true),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [
    index("item_store_idx").on(table.storeId),
    uniqueIndex("item_store_sku_uidx").on(table.storeId, table.sku),
    uniqueIndex("item_store_barcode_uidx").on(table.storeId, table.barcode),
  ],
);
