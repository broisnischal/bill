import { index, integer, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

import { user } from "./auth.schema";
import { invoice } from "./invoice.schema";
import { store } from "./store.schema";
import type { DevicePlatform, InvoiceType, LeaseStatus, WebLoginStatus } from "./types";

/**
 * A phone, tablet or till that bills for a store. The device identity is what a number
 * lease is granted to, so a lost phone's outstanding numbers can be closed without
 * touching any other till in the shop.
 */
export const device = sqliteTable(
  "device",
  {
    /** Generated on the device at first launch and sent with every request. */
    id: text("id").primaryKey(),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    platform: text("platform").$type<DevicePlatform>().notNull(),
    appVersion: text("app_version"),
    pushToken: text("push_token"),
    lastSeenAt: integer("last_seen_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
  },
  (table) => [
    index("device_store_idx").on(table.storeId),
    index("device_user_idx").on(table.userId),
  ],
);

/**
 * A block of invoice numbers handed to one device in advance so it can print a final,
 * IRD-valid number while it has no network.
 *
 * The store counter is advanced by the whole block the moment the lease is granted, so a
 * leased number can never collide with one the web app hands out. Numbers left unused
 * when a lease closes are not reissued: the closed range stays here as the auditable
 * record of why a series skips, which is the trade the offline requirement forces.
 */
export const invoiceNumberLease = sqliteTable(
  "invoice_number_lease",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    storeId: text("store_id")
      .notNull()
      .references(() => store.id, { onDelete: "cascade" }),
    deviceId: text("device_id")
      .notNull()
      .references(() => device.id, { onDelete: "cascade" }),
    fiscalYear: text("fiscal_year").notNull(),
    invoiceType: text("invoice_type").$type<InvoiceType>().notNull(),

    /** Inclusive range. `endSequence - startSequence + 1` numbers were taken from the counter. */
    startSequence: integer("start_sequence").notNull(),
    endSequence: integer("end_sequence").notNull(),
    /** Highest sequence in this lease that has reached the server on a real bill. */
    usedThrough: integer("used_through").notNull().default(0),

    status: text("status").$type<LeaseStatus>().notNull().default("open"),
    /** Why the lease was closed while numbers were still unused, for the auditor. */
    closeReason: text("close_reason"),

    issuedAt: integer("issued_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    expiresAt: integer("expires_at", { mode: "timestamp" }).notNull(),
    closedAt: integer("closed_at", { mode: "timestamp" }),
  },
  (table) => [
    uniqueIndex("lease_series_start_uidx").on(
      table.storeId,
      table.fiscalYear,
      table.invoiceType,
      table.startSequence,
    ),
    index("lease_device_open_idx").on(table.deviceId, table.status),
    index("lease_store_idx").on(table.storeId, table.fiscalYear),
  ],
);

/**
 * A shopper's own card.
 *
 * Customer mode shows this as a QR. A shop scans it instead of asking for a name and a
 * number at the counter, which is the slowest part of writing a bill for someone who is
 * not a regular. The token is opaque and only resolves for a signed-in store, so being
 * handed the code is what grants the lookup; nothing here is public.
 */
export const shopperProfile = sqliteTable(
  "shopper_profile",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    /** Printed into the QR. Rotatable, so a shopper can invalidate a shared code. */
    token: text("token")
      .notNull()
      .$defaultFn(() => crypto.randomUUID().replace(/-/g, "")),
    name: text("name").notNull(),
    phone: text("phone"),
    pan: text("pan"),
    address: text("address"),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .$onUpdate(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("shopper_profile_user_uidx").on(table.userId),
    uniqueIndex("shopper_profile_token_uidx").on(table.token),
  ],
);

/**
 * A browser waiting to be let in from a phone.
 *
 * Typing a number and waiting for an SMS works, but a shopkeeper standing at a till with
 * the app already open on their phone should not have to. The browser shows a short code,
 * they type it into the phone they are already signed in on, and the browser is in — the
 * same shape as signing a TV into an account, and for the same reason.
 *
 * The code is short because it is read off one screen and typed into another. What keeps
 * that safe is that it expires in minutes, dies after a handful of wrong guesses, and can
 * only ever be approved by someone already holding a signed-in phone.
 */
export const webLoginRequest = sqliteTable(
  "web_login_request",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    /** Six characters, no vowels and no look-alikes. Shown in the browser. */
    code: text("code").notNull(),
    /** Held by the browser and never displayed, so only the tab that asked can collect. */
    pollToken: text("poll_token").notNull(),
    status: text("status").$type<WebLoginStatus>().notNull().default("pending"),
    /** Who let it in. Null until someone does. */
    approvedByUserId: text("approved_by_user_id").references(() => user.id, {
      onDelete: "cascade",
    }),
    approvedAt: integer("approved_at", { mode: "timestamp" }),
    /** Wrong guesses. A few is a typo; more than that is someone trying codes. */
    attempts: integer("attempts").notNull().default(0),
    /** Where the browser was, so the phone can say what it is about to let in. */
    userAgent: text("user_agent"),
    ipAddress: text("ip_address"),
    expiresAt: integer("expires_at", { mode: "timestamp" }).notNull(),
    createdAt: integer("created_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("web_login_code_uidx").on(table.code),
    uniqueIndex("web_login_poll_uidx").on(table.pollToken),
    index("web_login_expiry_idx").on(table.expiresAt),
  ],
);

/**
 * A bill a shopper kept. Scanning the QR on a printed bill files it here, which is the
 * whole of customer mode: the shopper never gets access to the store, only to the
 * documents they were handed.
 */
export const savedBill = sqliteTable(
  "saved_bill",
  {
    id: text("id")
      .primaryKey()
      .$defaultFn(() => crypto.randomUUID()),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    invoiceId: text("invoice_id")
      .notNull()
      .references(() => invoice.id, { onDelete: "cascade" }),
    savedAt: integer("saved_at", { mode: "timestamp" })
      .$defaultFn(() => new Date())
      .notNull(),
  },
  (table) => [
    uniqueIndex("saved_bill_user_invoice_uidx").on(table.userId, table.invoiceId),
    index("saved_bill_user_idx").on(table.userId, table.savedAt),
  ],
);
