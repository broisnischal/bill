import "@tanstack/react-start/server-only";
import { env } from "#/env/server.ts";
import type { invoice as invoiceTable, store as storeTable } from "#/lib/db/schema/index.ts";
import { nptParts, toBsString } from "#/lib/nepali/date.ts";
import { paisaToDecimalString } from "#/lib/nepali/money.ts";

type Invoice = typeof invoiceTable.$inferSelect;
type Store = typeof storeTable.$inferSelect;

/**
 * Client for the IRD Central Billing Monitoring System.
 *
 * A VAT-registered taxpayer on approved billing software has to push every bill and
 * every credit note to CBMS as it is issued. The field names below are the ones the
 * IRD's API document specifies; they are snake_case and flat, and the service answers
 * with a JSON body carrying a status or a message.
 *
 * @see https://cbapi.ird.gov.np/api/bill
 */

const pad = (value: number) => String(value).padStart(2, "0");

/** CBMS wants dotted Bikram Sambat, e.g. 2082.05.10. */
function cbmsDate(at: Date) {
  return toBsString(at).replace(/-/g, ".");
}

/** Kathmandu wall clock, `YYYY-MM-DD HH:MM:SS`. */
function cbmsDateTime(at: Date) {
  const p = nptParts(at);
  return `${p.year}-${pad(p.month)}-${pad(p.day)} ${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}`;
}

export interface CbmsCredentials {
  username: string;
  password: string;
}

export function buildBillPayload({
  invoice,
  store,
  credentials,
}: {
  invoice: Invoice;
  store: Store;
  credentials: CbmsCredentials;
}) {
  return {
    username: credentials.username,
    password: credentials.password,
    seller_pan: store.pan,
    buyer_pan: invoice.buyerPan ?? "",
    buyer_name: invoice.buyerName,
    fiscal_year: invoice.fiscalYear,
    invoice_number: invoice.invoiceNumber,
    invoice_date: cbmsDate(invoice.issuedAt),
    total_sales: paisaToDecimalString(invoice.totalPaisa),
    taxable_sales_vat: paisaToDecimalString(invoice.taxableAmountPaisa),
    vat: paisaToDecimalString(invoice.vatAmountPaisa),
    excisable_amount: "0.00",
    excise: "0.00",
    taxable_sales_hst: "0.00",
    hst: "0.00",
    amount_for_esf: "0.00",
    esf: "0.00",
    export_sales: "0.00",
    tax_exempted_sales: paisaToDecimalString(invoice.nonTaxableAmountPaisa),
    isrealtime: invoice.isRealtime,
    datetime: cbmsDateTime(invoice.issuedAt),
    is_bill_printed: invoice.printCount > 0,
    is_bill_active: invoice.status === "active",
    printed_time: invoice.firstPrintedAt ? cbmsDateTime(invoice.firstPrintedAt) : "",
    entered_by: invoice.enteredByName,
    sync_with_ird: true,
    vat_refund_amount: "0.00",
    transaction_id: invoice.id,
    payment_method: invoice.paymentMethod,
  };
}

/** A credit note goes to the return endpoint and names the invoice it reverses. */
export function buildBillReturnPayload(args: {
  invoice: Invoice;
  store: Store;
  credentials: CbmsCredentials;
}) {
  const { invoice } = args;
  return {
    ...buildBillPayload(args),
    ref_invoice_number: invoice.refInvoiceNumber ?? "",
    credit_note_number: invoice.invoiceNumber,
    credit_note_date: cbmsDate(invoice.issuedAt),
    reason_for_return: invoice.reason ?? "",
    total_return_amount: paisaToDecimalString(invoice.totalPaisa),
  };
}

export interface CbmsResult {
  ok: boolean;
  status: number;
  body: unknown;
  error?: string;
}

/**
 * Posts one document. Network failure is not an error the biller should see: the bill is
 * already recorded, it simply stays queued and is retried, which is what the procedure
 * expects of software that has to keep selling when the link to Singha Durbar is down.
 */
export async function postToCbms({
  payload,
  isCreditNote = false,
  timeoutMs = 15_000,
}: {
  payload: Record<string, unknown>;
  isCreditNote?: boolean;
  timeoutMs?: number;
}): Promise<CbmsResult> {
  const url = isCreditNote ? env.IRD_CBMS_BILL_RETURN_URL : env.IRD_CBMS_BILL_URL;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    const text = await response.text();
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      // CBMS occasionally answers with a bare string; keep it as-is for the audit trail.
    }

    return {
      ok: response.ok,
      status: response.status,
      body,
      error: response.ok ? undefined : `CBMS responded ${response.status}: ${text.slice(0, 500)}`,
    };
  } catch (error) {
    return {
      ok: false,
      status: 0,
      body: null,
      error: error instanceof Error ? error.message : "CBMS request failed",
    };
  } finally {
    clearTimeout(timer);
  }
}
