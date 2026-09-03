import "@tanstack/react-start/server-only";
import { and, eq, isNotNull, sql } from "drizzle-orm";

import { readShopperLink } from "#/lib/api/cards.ts";
import { type Db, db, withTransaction } from "#/lib/db/index.ts";
import {
  customer,
  invoice,
  invoiceAudit,
  invoiceCounter,
  invoiceItem,
  invoicePayment,
  item,
  store as storeTable,
} from "#/lib/db/schema/index.ts";
import type { AuditAction, AuditMeta, InvoiceType, PrintFormat } from "#/lib/db/schema/types.ts";
import { buildBillPayload, buildBillReturnPayload, postToCbms } from "#/lib/ird/cbms.ts";
import { decryptSecret } from "#/lib/ird/credentials.ts";
import { fiscalYearFor, toBsString } from "#/lib/nepali/date.ts";
import { amountInWords } from "#/lib/nepali/money.ts";
import { invoicePdfKey, putPdf } from "#/lib/storage/archive.ts";
import { vatRateFor } from "#/lib/tax/vat.ts";

import { ABBREVIATED_INVOICE_LIMIT_PAISA, computeInvoice } from "./calc";
import { consumeLeasedSequence } from "./lease";
import { renderInvoicePdf } from "./pdf";
import type { CreateInvoiceInput, DeviceInvoiceInput, PaymentInput } from "./schema";

type Store = typeof storeTable.$inferSelect;
type Invoice = typeof invoice.$inferSelect;

export interface Actor {
  id: string;
  name: string;
  ipAddress?: string;
  userAgent?: string;
}

/**
 * The billing service.
 *
 * Two rules shape everything here. A number series has no gaps, so numbers are handed
 * out under a row lock inside the same transaction that writes the bill. And an issued
 * bill is never edited or deleted, so corrections are a cancellation or a credit note,
 * and every one of those events lands in the audit trail.
 */

export function formatInvoiceNumber({
  prefix,
  fiscalYear,
  invoiceType,
  sequence,
}: {
  prefix: string;
  fiscalYear: string;
  invoiceType: InvoiceType;
  sequence: number;
}) {
  const head = [prefix || undefined, invoiceType === "credit_note" ? "CN" : undefined]
    .filter(Boolean)
    .join("-");
  const body = `${fiscalYear}-${String(sequence).padStart(6, "0")}`;
  return head ? `${head}-${body}` : body;
}

/**
 * Allocates the next number in a series.
 *
 * The read and the increment are one statement, not a `SELECT ... FOR UPDATE` followed
 * by an `UPDATE`, because D1 gives no interactive transaction to hold a row lock across
 * two round trips. SQLite applies a single `UPDATE ... RETURNING` atomically and hands
 * back the post-increment value, so two tills billing at once get consecutive numbers
 * and never the same one.
 */
async function allocateSequence(
  tx: Db,
  {
    storeId,
    fiscalYear,
    invoiceType,
  }: { storeId: string; fiscalYear: string; invoiceType: InvoiceType },
) {
  await tx
    .insert(invoiceCounter)
    .values({ storeId, fiscalYear, invoiceType, nextSequence: 1 })
    .onConflictDoNothing();

  const [counter] = await tx
    .update(invoiceCounter)
    .set({ nextSequence: sql`${invoiceCounter.nextSequence} + 1` })
    .where(
      and(
        eq(invoiceCounter.storeId, storeId),
        eq(invoiceCounter.fiscalYear, fiscalYear),
        eq(invoiceCounter.invoiceType, invoiceType),
      ),
    )
    .returning({ nextSequence: invoiceCounter.nextSequence });

  return counter.nextSequence - 1;
}

async function recordAudit(
  tx: { insert: typeof db.insert },
  {
    invoiceId,
    storeId,
    action,
    actor,
    meta,
  }: {
    invoiceId: string;
    storeId: string;
    action: AuditAction;
    actor?: Actor;
    meta?: AuditMeta;
  },
) {
  await tx.insert(invoiceAudit).values({
    invoiceId,
    storeId,
    action,
    actorId: actor?.id,
    actorName: actor?.name,
    ipAddress: actor?.ipAddress,
    userAgent: actor?.userAgent,
    meta,
  });
}

/**
 * Moves the shelf count as goods leave on a bill or come back on a credit note.
 *
 * Only for the products a shop actually counts. A null stock means nobody is keeping
 * track, and a sale must not quietly turn that into a number. The count is clamped at
 * zero: one that has drifted is put right by counting the shelf, not by carrying a
 * negative nobody can explain.
 */
async function moveStock(
  tx: { update: typeof db.update },
  lines: { itemId?: string | null; quantityMilli: number }[],
  direction: 1 | -1,
) {
  for (const line of lines) {
    if (!line.itemId) continue;
    await tx
      .update(item)
      .set({
        stockThousandths: sql`max(0, ${item.stockThousandths} + ${direction * line.quantityMilli})`,
      })
      .where(and(eq(item.id, line.itemId), isNotNull(item.stockThousandths)));
  }
}

export async function createInvoice({
  store,
  actor,
  input,
  issuedAt = new Date(),
}: {
  store: Store;
  actor: Actor;
  input: CreateInvoiceInput;
  issuedAt?: Date;
}) {
  // A taxpayer registered for PAN only does not charge VAT, whatever the form sent.
  const vatRateBp = vatRateFor(store);
  const totals = computeInvoice({
    lines: input.lines,
    invoiceDiscountPaisa: input.discountPaisa,
    vatRateBp,
  });

  if (totals.totalPaisa <= 0) throw new Error("A bill has to total more than zero");
  if (
    input.invoiceType === "abbreviated_tax_invoice" &&
    totals.totalPaisa > ABBREVIATED_INVOICE_LIMIT_PAISA
  ) {
    throw new Error(
      "An abbreviated tax invoice cannot exceed NPR 10,000. Issue a full tax invoice instead.",
    );
  }

  const fiscalYear = fiscalYearFor(issuedAt);

  const created = await withTransaction(async (tx) => {
    const sequence = await allocateSequence(tx, {
      storeId: store.id,
      fiscalYear,
      invoiceType: input.invoiceType,
    });

    let customerId = input.customerId;
    if (!customerId && input.saveCustomer) {
      const [saved] = await tx
        .insert(customer)
        .values({
          storeId: store.id,
          name: input.buyerName,
          pan: input.buyerPan,
          address: input.buyerAddress,
          phone: input.buyerPhone,
        })
        .returning({ id: customer.id });
      customerId = saved.id;
    }

    const [row] = await tx
      .insert(invoice)
      .values({
        storeId: store.id,
        fiscalYear,
        invoiceType: input.invoiceType,
        sequence,
        invoiceNumber: formatInvoiceNumber({
          prefix: store.invoicePrefix,
          fiscalYear,
          invoiceType: input.invoiceType,
          sequence,
        }),
        customerId,
        buyerName: input.buyerName,
        buyerPan: input.buyerPan,
        buyerAddress: input.buyerAddress,
        buyerPhone: input.buyerPhone,
        issuedAt,
        miti: toBsString(issuedAt),
        subTotalPaisa: totals.subTotalPaisa,
        discountPaisa: totals.discountPaisa,
        taxableAmountPaisa: totals.taxableAmountPaisa,
        nonTaxableAmountPaisa: totals.nonTaxableAmountPaisa,
        vatRateBp,
        vatAmountPaisa: totals.vatAmountPaisa,
        totalPaisa: totals.totalPaisa,
        amountInWords: amountInWords(totals.totalPaisa),
        paymentMethod: input.paymentMethod,
        notes: input.notes,
        enteredById: actor.id,
        enteredByName: actor.name,
        irdSyncStatus:
          store.cbmsEnabled && store.taxpayerType === "vat" ? "pending" : "not_applicable",
      })
      .returning();

    await tx.insert(invoiceItem).values(
      totals.lines.map((line) => ({
        invoiceId: row.id,
        lineNo: line.lineNo,
        itemId: line.itemId,
        description: line.description,
        hsCode: line.hsCode,
        unit: line.unit,
        quantityMilli: line.quantityMilli,
        unitPricePaisa: line.unitPricePaisa,
        discountPaisa: line.discountPaisa,
        vatApplicable: line.vatApplicable,
        lineTotalPaisa: line.lineTotalPaisa,
      })),
    );

    await moveStock(tx, totals.lines, -1);

    await recordAudit(tx, {
      invoiceId: row.id,
      storeId: store.id,
      action: "invoice_created",
      actor,
      meta: { totalPaisa: row.totalPaisa, lines: totals.lines.length },
    });

    return row;
  });

  // Archive the PDF straight away: the copy an auditor reads is written before the
  // cashier has finished handing over the change.
  const archived = await archiveInvoicePdf({ store, invoiceId: created.id, actor }).catch(
    (error: unknown) => {
      // The bill is issued and printable the moment the row is written. Archiving is a
      // separate obligation, so a renderer that will not start must not cost the shop a
      // sale: the row keeps a null pdfKey and the archive can be retaken later.
      console.error("Could not archive the invoice PDF", error);
      return null;
    },
  );
  void syncInvoiceToIrd({ store, invoiceId: created.id }).catch(() => {
    // Failures are recorded on the invoice row and retried; billing never blocks on CBMS.
  });

  return archived ?? created;
}

/**
 * Raised when a bill a device already printed does not add up the way the server adds it
 * up. The paper cannot be recalled, so the push is refused and the shop is told to
 * reverse the bill with a credit note rather than have two versions of one document.
 */
export class InvoiceIntegrityError extends Error {
  constructor(
    message: string,
    readonly detail: Record<string, string | number>,
  ) {
    super(message);
    this.name = "InvoiceIntegrityError";
  }
}

/**
 * Files a bill a till already printed from a leased number.
 *
 * Nothing here is allowed to change what the customer is holding, so the number, the id,
 * the share token and the timestamps all arrive from the device. What the server does is
 * check them: that the number came from a block this device holds, that the totals match
 * a fresh calculation, and that the bill has not already been filed. Pushing the same
 * bill twice is a no-op, which is what lets the device retry a sync as often as it likes.
 */
export async function createLeasedInvoice({
  store,
  actor,
  deviceId,
  input,
  now = new Date(),
}: {
  store: Store;
  actor: Actor;
  deviceId: string;
  input: DeviceInvoiceInput;
  now?: Date;
}) {
  const [existing] = await db.select().from(invoice).where(eq(invoice.id, input.id));
  if (existing) {
    if (existing.storeId !== store.id) throw new Error("That bill belongs to another store");
    return { invoice: existing, filed: false as const };
  }

  const issuedAt = new Date(input.issuedAt);
  const vatRateBp = vatRateFor(store);
  const totals = computeInvoice({
    lines: input.lines,
    invoiceDiscountPaisa: input.discountPaisa,
    vatRateBp,
  });

  if (totals.totalPaisa !== input.totalPaisa) {
    throw new InvoiceIntegrityError("The printed total does not match what the bill adds up to", {
      printedPaisa: input.totalPaisa,
      computedPaisa: totals.totalPaisa,
    });
  }
  if (fiscalYearFor(issuedAt) !== input.fiscalYear) {
    throw new InvoiceIntegrityError(
      "The bill date does not fall in the fiscal year it was numbered in",
      {
        miti: toBsString(issuedAt),
        printedFiscalYear: input.fiscalYear,
        actualFiscalYear: fiscalYearFor(issuedAt),
      },
    );
  }
  if (
    input.invoiceType === "abbreviated_tax_invoice" &&
    totals.totalPaisa > ABBREVIATED_INVOICE_LIMIT_PAISA
  ) {
    throw new InvoiceIntegrityError("An abbreviated tax invoice cannot exceed NPR 10,000", {
      totalPaisa: totals.totalPaisa,
    });
  }

  const invoiceNumber = formatInvoiceNumber({
    prefix: store.invoicePrefix,
    fiscalYear: input.fiscalYear,
    invoiceType: input.invoiceType,
    sequence: input.sequence,
  });

  const created = await withTransaction(async (tx) => {
    await consumeLeasedSequence(tx, {
      leaseId: input.leaseId,
      deviceId,
      storeId: store.id,
      fiscalYear: input.fiscalYear,
      invoiceType: input.invoiceType,
      sequence: input.sequence,
      now,
    });

    let customerId = input.customerId;
    if (!customerId && input.saveCustomer) {
      const [saved] = await tx
        .insert(customer)
        .values({
          storeId: store.id,
          name: input.buyerName,
          pan: input.buyerPan,
          address: input.buyerAddress,
          phone: input.buyerPhone,
        })
        .returning({ id: customer.id });
      customerId = saved.id;
    }

    const [row] = await tx
      .insert(invoice)
      .values({
        id: input.id,
        shareToken: input.shareToken,
        storeId: store.id,
        fiscalYear: input.fiscalYear,
        invoiceType: input.invoiceType,
        sequence: input.sequence,
        invoiceNumber,
        deviceId,
        leaseId: input.leaseId,
        queuedAt: new Date(input.queuedAt),
        customerId,
        // A forged or stale link simply does not name anyone; it never fails the bill.
        shopperUserId: input.shopperLink ? readShopperLink(input.shopperLink) : null,
        buyerName: input.buyerName,
        buyerPan: input.buyerPan,
        buyerAddress: input.buyerAddress,
        buyerPhone: input.buyerPhone,
        issuedAt,
        miti: toBsString(issuedAt),
        subTotalPaisa: totals.subTotalPaisa,
        discountPaisa: totals.discountPaisa,
        taxableAmountPaisa: totals.taxableAmountPaisa,
        nonTaxableAmountPaisa: totals.nonTaxableAmountPaisa,
        vatRateBp,
        vatAmountPaisa: totals.vatAmountPaisa,
        totalPaisa: totals.totalPaisa,
        amountInWords: amountInWords(totals.totalPaisa),
        paymentMethod: input.paymentMethod,
        paidAtIssuePaisa: Math.min(input.paidAtIssuePaisa, totals.totalPaisa),
        dueMiti: input.dueMiti,
        notes: input.notes,
        enteredById: actor.id,
        enteredByName: actor.name,
        // CBMS distinguishes a bill that reached it as it was issued from one that was
        // queued on a till with no network, so the gap decides rather than a client flag.
        isRealtime: now.getTime() - issuedAt.getTime() < 60_000,
        irdSyncStatus:
          store.cbmsEnabled && store.taxpayerType === "vat" ? "pending" : "not_applicable",
      })
      .returning();

    await tx.insert(invoiceItem).values(
      totals.lines.map((line) => ({
        invoiceId: row.id,
        lineNo: line.lineNo,
        itemId: line.itemId,
        description: line.description,
        hsCode: line.hsCode,
        unit: line.unit,
        quantityMilli: line.quantityMilli,
        unitPricePaisa: line.unitPricePaisa,
        discountPaisa: line.discountPaisa,
        vatApplicable: line.vatApplicable,
        lineTotalPaisa: line.lineTotalPaisa,
      })),
    );

    await moveStock(tx, totals.lines, -1);

    await recordAudit(tx, {
      invoiceId: row.id,
      storeId: store.id,
      action: "invoice_created",
      actor,
      meta: {
        totalPaisa: row.totalPaisa,
        lines: totals.lines.length,
        deviceId,
        offlineForMs: now.getTime() - new Date(input.queuedAt).getTime(),
      },
    });

    return row;
  });

  const archived = await archiveInvoicePdf({ store, invoiceId: created.id, actor }).catch(
    (error: unknown) => {
      // The bill is issued and printable the moment the row is written. Archiving is a
      // separate obligation, so a renderer that will not start must not cost the shop a
      // sale: the row keeps a null pdfKey and the archive can be retaken later.
      console.error("Could not archive the invoice PDF", error);
      return null;
    },
  );
  void syncInvoiceToIrd({ store, invoiceId: created.id }).catch(() => {
    // Recorded on the row and retried. Billing never blocks on CBMS.
  });

  return { invoice: archived ?? created, filed: true as const };
}

export async function loadInvoiceForPrint({
  storeId,
  invoiceId,
}: {
  storeId: string;
  invoiceId: string;
}) {
  const [row] = await db
    .select()
    .from(invoice)
    .where(and(eq(invoice.id, invoiceId), eq(invoice.storeId, storeId)));
  if (!row) return null;

  const items = await db
    .select()
    .from(invoiceItem)
    .where(eq(invoiceItem.invoiceId, invoiceId))
    .orderBy(invoiceItem.lineNo);

  return { invoice: row, items };
}

/** Renders the A4 copy and puts it in object storage, then records where it landed. */
export async function archiveInvoicePdf({
  store,
  invoiceId,
  actor,
}: {
  store: Store;
  invoiceId: string;
  actor?: Actor;
}) {
  const loaded = await loadInvoiceForPrint({ storeId: store.id, invoiceId });
  if (!loaded) return null;

  const { bytes, sha256 } = await renderInvoicePdf({
    store,
    invoice: loaded.invoice,
    items: loaded.items,
    format: "a4",
    copyNumber: 1,
  });

  const key = invoicePdfKey({
    storeId: store.id,
    fiscalYear: loaded.invoice.fiscalYear,
    invoiceNumber: loaded.invoice.invoiceNumber,
    format: "a4",
  });

  await putPdf({
    key,
    body: bytes,
    metadata: {
      invoice: loaded.invoice.invoiceNumber,
      pan: store.pan,
      fiscalyear: loaded.invoice.fiscalYear,
      sha256,
    },
  });

  const [updated] = await db
    .update(invoice)
    .set({ pdfKey: key, pdfSha256: sha256, pdfBytes: bytes.byteLength })
    .where(eq(invoice.id, invoiceId))
    .returning();

  await recordAudit(db, {
    invoiceId,
    storeId: store.id,
    action: "pdf_archived",
    actor,
    meta: { key, sha256, bytes: bytes.byteLength },
  });

  return updated;
}

/**
 * Records that a copy left the printer. The first one is the original; every later one
 * is marked as a copy on the page itself, which is what stops a reprint being passed
 * off as a second sale.
 */
export async function registerPrint({
  storeId,
  invoiceId,
  format,
  actor,
}: {
  storeId: string;
  invoiceId: string;
  format: PrintFormat;
  actor: Actor;
}) {
  const now = new Date();
  const [updated] = await db
    .update(invoice)
    .set({
      printCount: sql`${invoice.printCount} + 1`,
      firstPrintedAt: sql`coalesce(${invoice.firstPrintedAt}, ${Math.floor(now.getTime() / 1000)})`,
      lastPrintedAt: now,
    })
    .where(and(eq(invoice.id, invoiceId), eq(invoice.storeId, storeId)))
    .returning();

  if (!updated) return null;

  await recordAudit(db, {
    invoiceId,
    storeId,
    action: updated.printCount > 1 ? "invoice_reprinted" : "invoice_printed",
    actor,
    meta: { format, copyNumber: updated.printCount },
  });

  return updated;
}

export async function cancelInvoice({
  store,
  invoiceId,
  reason,
  actor,
}: {
  store: Store;
  invoiceId: string;
  reason: string;
  actor: Actor;
}) {
  const [existing] = await db
    .select()
    .from(invoice)
    .where(and(eq(invoice.id, invoiceId), eq(invoice.storeId, store.id)));

  if (!existing) throw new Error("Invoice not found");
  if (existing.status === "cancelled") throw new Error("This bill is already cancelled");
  if (existing.invoiceType === "credit_note") throw new Error("A credit note cannot be cancelled");
  if (existing.fiscalYear !== fiscalYearFor(new Date())) {
    throw new Error(
      "A bill from a closed fiscal year cannot be cancelled. Issue a credit note instead.",
    );
  }

  const [updated] = await db
    .update(invoice)
    .set({ status: "cancelled", cancelledAt: new Date(), cancelledBy: actor.id, reason })
    .where(eq(invoice.id, invoiceId))
    .returning();

  await recordAudit(db, {
    invoiceId,
    storeId: store.id,
    action: "invoice_cancelled",
    actor,
    meta: { reason },
  });

  // CBMS keeps the bill and flips is_bill_active, so the cancellation is on record there too.
  void syncInvoiceToIrd({ store, invoiceId }).catch(() => {});

  return updated;
}

/** Reverses an issued bill in full, which is the lawful correction once it has gone out. */
export async function createCreditNote({
  store,
  invoiceId,
  reason,
  actor,
  issuedAt = new Date(),
}: {
  store: Store;
  invoiceId: string;
  reason: string;
  actor: Actor;
  issuedAt?: Date;
}) {
  const original = await loadInvoiceForPrint({ storeId: store.id, invoiceId });
  if (!original) throw new Error("Invoice not found");
  if (original.invoice.invoiceType === "credit_note") {
    throw new Error("A credit note cannot be issued against another credit note");
  }

  const fiscalYear = fiscalYearFor(issuedAt);

  const created = await withTransaction(async (tx) => {
    const sequence = await allocateSequence(tx, {
      storeId: store.id,
      fiscalYear,
      invoiceType: "credit_note",
    });

    const [row] = await tx
      .insert(invoice)
      .values({
        storeId: store.id,
        fiscalYear,
        invoiceType: "credit_note",
        sequence,
        invoiceNumber: formatInvoiceNumber({
          prefix: store.invoicePrefix,
          fiscalYear,
          invoiceType: "credit_note",
          sequence,
        }),
        refInvoiceId: original.invoice.id,
        refInvoiceNumber: original.invoice.invoiceNumber,
        reason,
        customerId: original.invoice.customerId,
        buyerName: original.invoice.buyerName,
        buyerPan: original.invoice.buyerPan,
        buyerAddress: original.invoice.buyerAddress,
        buyerPhone: original.invoice.buyerPhone,
        issuedAt,
        miti: toBsString(issuedAt),
        subTotalPaisa: original.invoice.subTotalPaisa,
        discountPaisa: original.invoice.discountPaisa,
        taxableAmountPaisa: original.invoice.taxableAmountPaisa,
        nonTaxableAmountPaisa: original.invoice.nonTaxableAmountPaisa,
        vatRateBp: original.invoice.vatRateBp,
        vatAmountPaisa: original.invoice.vatAmountPaisa,
        totalPaisa: original.invoice.totalPaisa,
        amountInWords: amountInWords(original.invoice.totalPaisa),
        paymentMethod: original.invoice.paymentMethod,
        enteredById: actor.id,
        enteredByName: actor.name,
        irdSyncStatus:
          store.cbmsEnabled && store.taxpayerType === "vat" ? "pending" : "not_applicable",
      })
      .returning();

    await tx.insert(invoiceItem).values(
      original.items.map((line) => ({
        invoiceId: row.id,
        lineNo: line.lineNo,
        itemId: line.itemId,
        description: line.description,
        hsCode: line.hsCode,
        unit: line.unit,
        quantityMilli: line.quantityMilli,
        unitPricePaisa: line.unitPricePaisa,
        discountPaisa: line.discountPaisa,
        vatApplicable: line.vatApplicable,
        lineTotalPaisa: line.lineTotalPaisa,
      })),
    );

    // A credit note reverses the sale in full, so the goods are back on the shelf.
    await moveStock(tx, original.items, 1);

    await recordAudit(tx, {
      invoiceId: row.id,
      storeId: store.id,
      action: "credit_note_issued",
      actor,
      meta: { against: original.invoice.invoiceNumber, reason },
    });

    return row;
  });

  const archived = await archiveInvoicePdf({ store, invoiceId: created.id, actor }).catch(
    (error: unknown) => {
      // The bill is issued and printable the moment the row is written. Archiving is a
      // separate obligation, so a renderer that will not start must not cost the shop a
      // sale: the row keeps a null pdfKey and the archive can be retaken later.
      console.error("Could not archive the invoice PDF", error);
      return null;
    },
  );
  void syncInvoiceToIrd({ store, invoiceId: created.id }).catch(() => {});

  return archived ?? created;
}

/**
 * Pushes one document to CBMS and records the outcome on the row. Safe to call again:
 * a bill that is already synced is skipped, and a failure only bumps the attempt count.
 */
export async function syncInvoiceToIrd({
  store,
  invoiceId,
  force = false,
}: {
  store: Store;
  invoiceId: string;
  force?: boolean;
}) {
  if (!store.cbmsEnabled || store.taxpayerType !== "vat") return null;
  if (!store.cbmsUsername || !store.cbmsPasswordEncrypted) return null;

  const [row] = await db
    .select()
    .from(invoice)
    .where(and(eq(invoice.id, invoiceId), eq(invoice.storeId, store.id)));
  if (!row) return null;
  if (row.irdSyncStatus === "synced" && !force && row.status === "active") return row;

  const credentials = {
    username: store.cbmsUsername,
    password: decryptSecret(store.cbmsPasswordEncrypted),
  };
  const isCreditNote = row.invoiceType === "credit_note";
  const payload = isCreditNote
    ? buildBillReturnPayload({ invoice: row, store, credentials })
    : buildBillPayload({ invoice: row, store, credentials });

  const result = await postToCbms({ payload, isCreditNote });

  const [updated] = await db
    .update(invoice)
    .set({
      irdSyncStatus: result.ok ? "synced" : "failed",
      irdSyncedAt: result.ok ? new Date() : row.irdSyncedAt,
      irdSyncAttempts: row.irdSyncAttempts + 1,
      irdLastError: result.error ?? null,
      // The password never goes into the audit record.
      irdResponse: {
        status: result.status,
        body: typeof result.body === "string" ? result.body : JSON.stringify(result.body ?? null),
      },
    })
    .where(eq(invoice.id, invoiceId))
    .returning();

  await recordAudit(db, {
    invoiceId,
    storeId: store.id,
    action: result.ok ? "ird_synced" : "ird_sync_failed",
    meta: { status: result.status, error: result.error ?? null },
  });

  return updated;
}

/** Everything still owed to the IRD, oldest first. Drives the retry button and any cron. */
export async function pendingIrdInvoices(storeId: string, limit = 50) {
  return db
    .select()
    .from(invoice)
    .where(
      and(eq(invoice.storeId, storeId), sql`${invoice.irdSyncStatus} in ('pending', 'failed')`),
    )
    .orderBy(invoice.issuedAt)
    .limit(limit);
}

export type InvoiceRow = Invoice;

/**
 * Records money received against a bill.
 *
 * The bill itself is never touched: what is owed is the total less what was paid at the
 * counter less the payments filed here, so a ledger can always be rebuilt from rows that
 * were only ever inserted. Recording the same payment twice is a no-op, which is what
 * lets a till retry a sync.
 */
export async function recordPayment({
  store,
  actor,
  input,
  deviceId,
}: {
  store: Store;
  actor: Actor;
  input: PaymentInput;
  deviceId?: string;
}) {
  const [bill] = await db
    .select()
    .from(invoice)
    .where(and(eq(invoice.id, input.invoiceId), eq(invoice.storeId, store.id)));
  if (!bill) throw new Error("That bill is not in this store");
  if (bill.status === "cancelled") throw new Error("A cancelled bill cannot take a payment");

  const [existing] = await db
    .select({ id: invoicePayment.id })
    .from(invoicePayment)
    .where(eq(invoicePayment.id, input.id));
  if (existing) return { payment: existing, filed: false as const };

  const outstanding = await outstandingFor(bill);
  if (input.amountPaisa > outstanding) {
    throw new Error(
      `That is more than the ${(outstanding / 100).toFixed(2)} still owed on this bill`,
    );
  }

  const [payment] = await db
    .insert(invoicePayment)
    .values({
      id: input.id,
      invoiceId: input.invoiceId,
      storeId: store.id,
      amountPaisa: input.amountPaisa,
      method: input.method,
      receivedAt: new Date(input.receivedAt),
      miti: input.miti,
      note: input.note,
      recordedById: actor.id,
      deviceId,
    })
    .returning();

  await recordAudit(db, {
    invoiceId: bill.id,
    storeId: store.id,
    action: "payment_received",
    actor,
    meta: { amountPaisa: input.amountPaisa, method: input.method },
  });

  return { payment, filed: true as const };
}

/** What is still owed on a bill: its total, less the counter payment, less what came in. */
export async function outstandingFor(bill: Invoice) {
  if (bill.status === "cancelled") return 0;
  const [received] = await db
    .select({ total: sql<number>`coalesce(sum(${invoicePayment.amountPaisa}), 0)::int` })
    .from(invoicePayment)
    .where(eq(invoicePayment.invoiceId, bill.id));
  return bill.totalPaisa - bill.paidAtIssuePaisa - (received?.total ?? 0);
}
