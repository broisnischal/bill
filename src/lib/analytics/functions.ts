import { createServerFn } from "@tanstack/react-start";
import { and, count, desc, eq, gte, lt, sql } from "drizzle-orm";
import * as z from "zod";

import { db } from "#/lib/db/index.ts";
import { invoice, invoiceItem } from "#/lib/db/schema/index.ts";
import { BS_MONTHS, fiscalYearRange } from "#/lib/nepali/date.ts";
import { storeMiddleware } from "#/lib/store/middleware.ts";

/**
 * Sales analysis over what has already been billed.
 *
 * Cancelled bills are excluded and credit notes are subtracted, so the figures here
 * are the ones that belong on a VAT return rather than a raw sum of documents issued.
 */

const toNumber = (value: string | number | null) => Number(value ?? 0);

/** Signed amount: a credit note takes away what the bill it reverses added. */
const signed = (column: unknown) =>
  sql<string>`coalesce(sum(case when ${invoice.invoiceType} = 'credit_note' then -1 else 1 end * ${column}), 0)`;

export const $salesAnalytics = createServerFn({ method: "GET" })
  .middleware([storeMiddleware])
  .validator(z.object({ fiscalYear: z.string() }))
  .handler(async ({ data, context }) => {
    const { start, end } = fiscalYearRange(data.fiscalYear);
    const inPeriod = and(
      eq(invoice.storeId, context.store.id),
      gte(invoice.issuedAt, start),
      lt(invoice.issuedAt, end),
    );
    const counted = and(inPeriod, eq(invoice.status, "active"));

    const [totals] = await db
      .select({
        documents: count(),
        salesPaisa: signed(invoice.totalPaisa),
        taxablePaisa: signed(invoice.taxableAmountPaisa),
        exemptPaisa: signed(invoice.nonTaxableAmountPaisa),
        vatPaisa: signed(invoice.vatAmountPaisa),
        discountPaisa: signed(invoice.discountPaisa),
      })
      .from(invoice)
      .where(counted);

    const statusRows = await db
      .select({
        invoiceType: invoice.invoiceType,
        status: invoice.status,
        documents: count(),
        totalPaisa: sql<string>`coalesce(sum(${invoice.totalPaisa}), 0)`,
      })
      .from(invoice)
      .where(inPeriod)
      .groupBy(invoice.invoiceType, invoice.status);

    // Bikram Sambat month, taken from the stored miti so the grouping matches the
    // month a return is filed for, not the Gregorian month it straddles.
    const monthRows = await db
      .select({
        bsMonth: sql<string>`substring(${invoice.miti} from 1 for 7)`,
        documents: count(),
        salesPaisa: signed(invoice.totalPaisa),
        vatPaisa: signed(invoice.vatAmountPaisa),
        taxablePaisa: signed(invoice.taxableAmountPaisa),
      })
      .from(invoice)
      .where(counted)
      .groupBy(sql`substring(${invoice.miti} from 1 for 7)`)
      .orderBy(sql`substring(${invoice.miti} from 1 for 7)`);

    const paymentRows = await db
      .select({
        paymentMethod: invoice.paymentMethod,
        documents: count(),
        salesPaisa: signed(invoice.totalPaisa),
      })
      .from(invoice)
      .where(counted)
      .groupBy(invoice.paymentMethod)
      .orderBy(desc(signed(invoice.totalPaisa)));

    const itemRows = await db
      .select({
        description: invoiceItem.description,
        quantityMilli: sql<string>`coalesce(sum(${invoiceItem.quantityMilli}), 0)`,
        amountPaisa: sql<string>`coalesce(sum(${invoiceItem.lineTotalPaisa}), 0)`,
      })
      .from(invoiceItem)
      .innerJoin(invoice, eq(invoice.id, invoiceItem.invoiceId))
      .where(and(counted, sql`${invoice.invoiceType} <> 'credit_note'`))
      .groupBy(invoiceItem.description)
      .orderBy(desc(sql`sum(${invoiceItem.lineTotalPaisa})`))
      .limit(10);

    const customerRows = await db
      .select({
        buyerName: invoice.buyerName,
        buyerPan: invoice.buyerPan,
        documents: count(),
        salesPaisa: signed(invoice.totalPaisa),
      })
      .from(invoice)
      .where(counted)
      .groupBy(invoice.buyerName, invoice.buyerPan)
      .orderBy(desc(signed(invoice.totalPaisa)))
      .limit(10);

    const dayRows = await db
      .select({
        day: sql<string>`to_char((${invoice.issuedAt} at time zone 'Asia/Kathmandu')::date, 'YYYY-MM-DD')`,
        salesPaisa: signed(invoice.totalPaisa),
        documents: count(),
      })
      .from(invoice)
      .where(and(counted, gte(invoice.issuedAt, new Date(Date.now() - 30 * 86_400_000))))
      .groupBy(sql`(${invoice.issuedAt} at time zone 'Asia/Kathmandu')::date`)
      .orderBy(sql`(${invoice.issuedAt} at time zone 'Asia/Kathmandu')::date`);

    const cancelled = statusRows
      .filter((row) => row.status === "cancelled")
      .reduce((sum, row) => sum + row.documents, 0);
    const creditNotes = statusRows
      .filter((row) => row.invoiceType === "credit_note")
      .reduce((sum, row) => sum + row.documents, 0);

    return {
      fiscalYear: data.fiscalYear,
      summary: {
        documents: totals.documents,
        cancelled,
        creditNotes,
        salesPaisa: toNumber(totals.salesPaisa),
        taxablePaisa: toNumber(totals.taxablePaisa),
        exemptPaisa: toNumber(totals.exemptPaisa),
        vatPaisa: toNumber(totals.vatPaisa),
        discountPaisa: toNumber(totals.discountPaisa),
        averageBillPaisa: totals.documents
          ? Math.round(toNumber(totals.salesPaisa) / totals.documents)
          : 0,
      },
      byMonth: monthRows.map((row) => ({
        bsMonth: row.bsMonth,
        label: BS_MONTHS[Number(row.bsMonth.slice(-2)) - 1] ?? row.bsMonth,
        documents: row.documents,
        salesPaisa: toNumber(row.salesPaisa),
        vatPaisa: toNumber(row.vatPaisa),
        taxablePaisa: toNumber(row.taxablePaisa),
      })),
      byPaymentMethod: paymentRows.map((row) => ({
        paymentMethod: row.paymentMethod,
        documents: row.documents,
        salesPaisa: toNumber(row.salesPaisa),
      })),
      topItems: itemRows.map((row) => ({
        description: row.description,
        quantityMilli: toNumber(row.quantityMilli),
        amountPaisa: toNumber(row.amountPaisa),
      })),
      topCustomers: customerRows.map((row) => ({
        buyerName: row.buyerName,
        buyerPan: row.buyerPan,
        documents: row.documents,
        salesPaisa: toNumber(row.salesPaisa),
      })),
      byDay: dayRows.map((row) => ({
        day: row.day,
        documents: row.documents,
        salesPaisa: toNumber(row.salesPaisa),
      })),
    };
  });
