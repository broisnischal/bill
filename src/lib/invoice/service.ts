import "@tanstack/react-start/server-only";
import { and, eq, sql } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import {
  customer,
  invoice,
  invoiceAudit,
  invoiceCounter,
  invoiceItem,
  store as storeTable,
} from "#/lib/db/schema/index.ts";
import type { AuditAction, AuditMeta, InvoiceType, PrintFormat } from "#/lib/db/schema/types.ts";
import { buildBillPayload, buildBillReturnPayload, postToCbms } from "#/lib/ird/cbms.ts";
import { decryptSecret } from "#/lib/ird/credentials.ts";
import { fiscalYearFor, toBsString } from "#/lib/nepali/date.ts";
import { amountInWords } from "#/lib/nepali/money.ts";
import { invoicePdfKey, putPdf } from "#/lib/storage/s3.ts";

import { ABBREVIATED_INVOICE_LIMIT_PAISA, computeInvoice } from "./calc";
import { renderInvoicePdf } from "./pdf";
import type { CreateInvoiceInput } from "./schema";

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

function formatInvoiceNumber({
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

/** Allocates the next number in a series. Callers must already be inside a transaction. */
async function allocateSequence(
  tx: Parameters<Parameters<typeof db.transaction>[0]>[0],
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
    .select()
    .from(invoiceCounter)
    .where(
      and(
        eq(invoiceCounter.storeId, storeId),
        eq(invoiceCounter.fiscalYear, fiscalYear),
        eq(invoiceCounter.invoiceType, invoiceType),
      ),
    )
    .for("update");

  const sequence = counter.nextSequence;
  await tx
    .update(invoiceCounter)
    .set({ nextSequence: sequence + 1 })
    .where(
      and(
        eq(invoiceCounter.storeId, storeId),
        eq(invoiceCounter.fiscalYear, fiscalYear),
        eq(invoiceCounter.invoiceType, invoiceType),
      ),
    );

  return sequence;
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
  const vatRateBp = store.taxpayerType === "vat" ? store.vatRateBp : 0;
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

  const created = await db.transaction(async (tx) => {
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
  const archived = await archiveInvoicePdf({ store, invoiceId: created.id, actor });
  void syncInvoiceToIrd({ store, invoiceId: created.id }).catch(() => {
    // Failures are recorded on the invoice row and retried; billing never blocks on CBMS.
  });

  return archived ?? created;
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
      firstPrintedAt: sql`coalesce(${invoice.firstPrintedAt}, ${now.toISOString()}::timestamptz)`,
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

  const created = await db.transaction(async (tx) => {
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

    await recordAudit(tx, {
      invoiceId: row.id,
      storeId: store.id,
      action: "credit_note_issued",
      actor,
      meta: { against: original.invoice.invoiceNumber, reason },
    });

    return row;
  });

  const archived = await archiveInvoicePdf({ store, invoiceId: created.id, actor });
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
