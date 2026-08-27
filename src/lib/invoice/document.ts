import type {
  invoice as invoiceTable,
  invoiceItem as invoiceItemTable,
  store as storeTable,
} from "#/lib/db/schema/index.ts";
import type { PrintFormat } from "#/lib/db/schema/types.ts";
import { formatBsLong, toAdDateString, toNptTimeString } from "#/lib/nepali/date.ts";
import { formatPaisa, formatQuantity } from "#/lib/nepali/money.ts";

/**
 * The printed bill.
 *
 * One builder produces the markup for both outputs: the browser print view and the
 * archived PDF that takumi renders. They cannot drift, because they are the same string.
 * Layout is flexbox only, no tables, since that is the subset both renderers agree on.
 */

type Store = typeof storeTable.$inferSelect;
type Invoice = typeof invoiceTable.$inferSelect;
type InvoiceItem = typeof invoiceItemTable.$inferSelect;

export interface InvoiceDocumentInput {
  store: Store;
  invoice: Invoice;
  items: InvoiceItem[];
  format: PrintFormat;
  /** 1 is the original. Anything higher prints as a copy, which the IRD requires. */
  copyNumber: number;
  /** Optional label such as "Buyer's Copy" or "Seller's Copy". */
  copyLabel?: string;
}

const escapeHtml = (value: string) =>
  value.replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char] ?? char,
  );

const e = (value: string | null | undefined) => (value ? escapeHtml(value) : "");

const DOCUMENT_TITLES: Record<Invoice["invoiceType"], { en: string; np: string }> = {
  tax_invoice: { en: "TAX INVOICE", np: "कर बीजक" },
  abbreviated_tax_invoice: { en: "ABBREVIATED TAX INVOICE", np: "संक्षिप्त कर बीजक" },
  credit_note: { en: "CREDIT NOTE", np: "क्रेडिट नोट" },
};

const PAYMENT_LABELS: Record<Invoice["paymentMethod"], string> = {
  cash: "Cash",
  credit: "Credit",
  card: "Card",
  bank: "Bank Transfer",
  cheque: "Cheque",
  esewa: "eSewa",
  khalti: "Khalti",
  fonepay: "Fonepay",
  connectips: "ConnectIPS",
};

function storeAddress(store: Store) {
  return [
    store.address,
    store.ward ? `Ward ${store.ward}` : "",
    store.municipality,
    store.district,
    store.province,
  ]
    .filter(Boolean)
    .join(", ");
}

function copyMarker(copyNumber: number, copyLabel?: string) {
  if (copyLabel) return copyLabel;
  return copyNumber <= 1 ? "Original" : `Copy of Original (#${copyNumber})`;
}

/* ------------------------------------------------------------------ A4 ------ */

function a4Body({ store, invoice, items, copyNumber, copyLabel }: InvoiceDocumentInput) {
  const title = DOCUMENT_TITLES[invoice.invoiceType];
  const vatRate = (invoice.vatRateBp / 100).toFixed(invoice.vatRateBp % 100 === 0 ? 0 : 2);
  const isCredit = invoice.invoiceType === "credit_note";

  const rows = items
    .map(
      (line) => `
      <div class="row line">
        <span class="c-sn">${line.lineNo}</span>
        <span class="c-desc">${e(line.description)}${
          line.vatApplicable ? "" : '<span class="exempt">exempt</span>'
        }</span>
        <span class="c-hs">${e(line.hsCode)}</span>
        <span class="c-qty">${formatQuantity(line.quantityMilli)} ${e(line.unit)}</span>
        <span class="c-rate">${formatPaisa(line.unitPricePaisa)}</span>
        <span class="c-disc">${line.discountPaisa ? formatPaisa(line.discountPaisa) : "-"}</span>
        <span class="c-amt">${formatPaisa(line.lineTotalPaisa)}</span>
      </div>`,
    )
    .join("");

  const totalRow = (label: string, value: string, modifier = "") =>
    `<div class="total-row ${modifier}"><span>${label}</span><span>${value}</span></div>`;

  return `
<div class="sheet">
  ${invoice.status === "cancelled" ? '<div class="void-band">CANCELLED / रद्द गरिएको</div>' : ""}

  <div class="head">
    <div class="head-left">
      <div class="store-name">${e(store.name)}</div>
      ${store.nameNepali ? `<div class="store-name-np">${e(store.nameNepali)}</div>` : ""}
      <div class="muted">${e(storeAddress(store))}</div>
      <div class="muted">${[store.phone ? `Tel: ${store.phone}` : "", store.email ?? ""]
        .filter(Boolean)
        .map(e)
        .join(" · ")}</div>
      <div class="pan">PAN / VAT No: <b>${e(store.pan)}</b></div>
    </div>
    <div class="head-right">
      <div class="doc-title">${title.en}</div>
      <div class="doc-title-np">${title.np}</div>
      <div class="copy">${e(copyMarker(copyNumber, copyLabel))}</div>
    </div>
  </div>

  <div class="meta">
    <div class="meta-col">
      <div class="meta-label">Bill To / खरिदकर्ता</div>
      <div class="meta-strong">${e(invoice.buyerName)}</div>
      ${invoice.buyerAddress ? `<div>${e(invoice.buyerAddress)}</div>` : ""}
      ${invoice.buyerPhone ? `<div>Tel: ${e(invoice.buyerPhone)}</div>` : ""}
      <div>PAN: <b>${invoice.buyerPan ? e(invoice.buyerPan) : "-"}</b></div>
    </div>
    <div class="meta-col meta-right">
      <div class="kv"><span>Invoice No.</span><b>${e(invoice.invoiceNumber)}</b></div>
      <div class="kv"><span>Miti (BS)</span><b>${e(formatBsLong(invoice.miti))}</b></div>
      <div class="kv"><span>Date (AD)</span><b>${toAdDateString(invoice.issuedAt)} ${toNptTimeString(
        invoice.issuedAt,
      )}</b></div>
      <div class="kv"><span>Fiscal Year</span><b>${e(invoice.fiscalYear)}</b></div>
      <div class="kv"><span>Payment</span><b>${PAYMENT_LABELS[invoice.paymentMethod]}</b></div>
      ${
        invoice.refInvoiceNumber
          ? `<div class="kv"><span>Against Invoice</span><b>${e(invoice.refInvoiceNumber)}</b></div>`
          : ""
      }
    </div>
  </div>

  <div class="table">
    <div class="row header">
      <span class="c-sn">S.N.</span>
      <span class="c-desc">Particulars / विवरण</span>
      <span class="c-hs">HS Code</span>
      <span class="c-qty">Qty</span>
      <span class="c-rate">Rate</span>
      <span class="c-disc">Disc.</span>
      <span class="c-amt">Amount</span>
    </div>
    ${rows}
  </div>

  <div class="foot">
    <div class="foot-left">
      <div class="words-label">Amount in words</div>
      <div class="words">${e(invoice.amountInWords)}</div>
      ${invoice.notes ? `<div class="notes">Note: ${e(invoice.notes)}</div>` : ""}
      ${isCredit && invoice.reason ? `<div class="notes">Reason: ${e(invoice.reason)}</div>` : ""}
      ${store.bankDetails ? `<div class="notes">${e(store.bankDetails)}</div>` : ""}
    </div>
    <div class="totals">
      ${totalRow("Sub Total", formatPaisa(invoice.subTotalPaisa))}
      ${invoice.discountPaisa ? totalRow("Discount", `- ${formatPaisa(invoice.discountPaisa)}`) : ""}
      ${
        invoice.nonTaxableAmountPaisa
          ? totalRow("Non-taxable / Exempt", formatPaisa(invoice.nonTaxableAmountPaisa))
          : ""
      }
      ${totalRow("Taxable Amount", formatPaisa(invoice.taxableAmountPaisa))}
      ${totalRow(`VAT @ ${vatRate}%`, formatPaisa(invoice.vatAmountPaisa))}
      ${invoice.roundOffPaisa ? totalRow("Round Off", formatPaisa(invoice.roundOffPaisa)) : ""}
      ${totalRow("Grand Total (NPR)", formatPaisa(invoice.totalPaisa), "grand")}
    </div>
  </div>

  <div class="signs">
    <div class="sign">
      <div class="sign-line"></div>
      <div class="muted">Received by / प्राप्त गर्ने</div>
    </div>
    <div class="sign">
      <div class="sign-line"></div>
      <div class="muted">For ${e(store.name)} / अधिकृत हस्ताक्षर</div>
    </div>
  </div>

  <div class="legal">
    <span>Prepared by: ${e(invoice.enteredByName)}</span>
    <span>Prints: ${invoice.printCount}</span>
    <span>${e(store.printFooterNote ?? "This is a computer generated invoice.")}</span>
  </div>
</div>`;
}

const A4_CSS = `
* { box-sizing: border-box; }
body { margin: 0; }
.sheet { display: flex; flex-direction: column; width: 100%; font-size: 11px; line-height: 1.45; color: #111827; }
.void-band { display: flex; justify-content: center; padding: 4px 0; margin-bottom: 8px; border: 1px solid #b91c1c; color: #b91c1c; font-size: 14px; font-weight: 700; letter-spacing: 2px; }
.head { display: flex; justify-content: space-between; padding-bottom: 10px; border-bottom: 2px solid #111827; }
.head-left { display: flex; flex-direction: column; max-width: 62%; }
.head-right { display: flex; flex-direction: column; align-items: flex-end; }
.store-name { font-size: 19px; font-weight: 700; letter-spacing: -0.2px; }
.store-name-np { font-size: 13px; font-weight: 600; }
.muted { color: #4b5563; }
.pan { margin-top: 4px; }
.doc-title { font-size: 16px; font-weight: 700; letter-spacing: 1px; }
.doc-title-np { font-size: 13px; font-weight: 600; }
.copy { margin-top: 4px; padding: 1px 6px; border: 1px solid #9ca3af; border-radius: 3px; font-size: 9px; text-transform: uppercase; letter-spacing: 0.6px; color: #374151; }
.meta { display: flex; justify-content: space-between; gap: 20px; padding: 10px 0; border-bottom: 1px solid #d1d5db; }
.meta-col { display: flex; flex-direction: column; width: 48%; }
.meta-right { align-items: flex-end; }
.meta-label { font-size: 9px; text-transform: uppercase; letter-spacing: 0.7px; color: #6b7280; }
.meta-strong { font-size: 13px; font-weight: 600; }
.kv { display: flex; gap: 10px; justify-content: flex-end; }
.kv span { color: #6b7280; }
.table { display: flex; flex-direction: column; margin-top: 10px; }
.row { display: flex; padding: 5px 0; border-bottom: 1px solid #e5e7eb; }
.row.header { border-bottom: 1px solid #111827; font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; color: #374151; }
.row.line { break-inside: avoid; }
.c-sn { width: 6%; }
.c-desc { width: 36%; display: flex; gap: 6px; }
.c-hs { width: 10%; }
.c-qty { width: 12%; justify-content: flex-end; display: flex; }
.c-rate { width: 12%; justify-content: flex-end; display: flex; }
.c-disc { width: 10%; justify-content: flex-end; display: flex; }
.c-amt { width: 14%; justify-content: flex-end; display: flex; font-weight: 600; }
.exempt { padding: 0 4px; border: 1px solid #d1d5db; border-radius: 3px; font-size: 8px; color: #6b7280; }
.foot { display: flex; justify-content: space-between; gap: 24px; margin-top: 12px; break-inside: avoid; }
.foot-left { display: flex; flex-direction: column; width: 52%; }
.words-label { font-size: 9px; text-transform: uppercase; letter-spacing: 0.7px; color: #6b7280; }
.words { font-weight: 600; }
.notes { margin-top: 6px; color: #4b5563; }
.totals { display: flex; flex-direction: column; width: 42%; }
.total-row { display: flex; justify-content: space-between; padding: 3px 0; border-bottom: 1px solid #f3f4f6; }
.total-row.grand { margin-top: 4px; padding: 6px 0; border-top: 2px solid #111827; border-bottom: none; font-size: 13px; font-weight: 700; }
.signs { display: flex; justify-content: space-between; margin-top: 34px; break-inside: avoid; }
.sign { display: flex; flex-direction: column; width: 40%; }
.sign-line { border-top: 1px solid #9ca3af; margin-bottom: 4px; }
.legal { display: flex; justify-content: space-between; gap: 12px; margin-top: 16px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 9px; color: #6b7280; }
`;

/* -------------------------------------------------------------- thermal ------ */

function thermalBody({ store, invoice, items, copyNumber, copyLabel }: InvoiceDocumentInput) {
  const title = DOCUMENT_TITLES[invoice.invoiceType];
  const vatRate = (invoice.vatRateBp / 100).toFixed(invoice.vatRateBp % 100 === 0 ? 0 : 2);

  const rows = items
    .map(
      (line) => `
      <div class="t-line">
        <div class="t-desc">${line.lineNo}. ${e(line.description)}</div>
        <div class="t-calc">
          <span>${formatQuantity(line.quantityMilli)} ${e(line.unit)} x ${formatPaisa(
            line.unitPricePaisa,
          )}</span>
          <span>${formatPaisa(line.lineTotalPaisa)}</span>
        </div>
      </div>`,
    )
    .join("");

  const totalRow = (label: string, value: string, modifier = "") =>
    `<div class="t-total ${modifier}"><span>${label}</span><span>${value}</span></div>`;

  return `
<div class="receipt">
  <div class="t-center t-bold t-lg">${e(store.name)}</div>
  ${store.nameNepali ? `<div class="t-center">${e(store.nameNepali)}</div>` : ""}
  <div class="t-center t-sm">${e(storeAddress(store))}</div>
  ${store.phone ? `<div class="t-center t-sm">Tel: ${e(store.phone)}</div>` : ""}
  <div class="t-center t-sm">PAN: ${e(store.pan)}</div>
  <div class="t-rule"></div>
  <div class="t-center t-bold">${title.en}</div>
  <div class="t-center t-sm">${title.np} · ${e(copyMarker(copyNumber, copyLabel))}</div>
  ${invoice.status === "cancelled" ? '<div class="t-center t-bold">** CANCELLED **</div>' : ""}
  <div class="t-rule"></div>
  <div class="t-kv"><span>Bill No</span><span>${e(invoice.invoiceNumber)}</span></div>
  <div class="t-kv"><span>Miti</span><span>${e(invoice.miti)} BS</span></div>
  <div class="t-kv"><span>Date</span><span>${toAdDateString(invoice.issuedAt)} ${toNptTimeString(
    invoice.issuedAt,
  )}</span></div>
  <div class="t-kv"><span>Customer</span><span>${e(invoice.buyerName)}</span></div>
  ${invoice.buyerPan ? `<div class="t-kv"><span>Buyer PAN</span><span>${e(invoice.buyerPan)}</span></div>` : ""}
  <div class="t-rule"></div>
  ${rows}
  <div class="t-rule"></div>
  ${totalRow("Sub Total", formatPaisa(invoice.subTotalPaisa))}
  ${invoice.discountPaisa ? totalRow("Discount", `-${formatPaisa(invoice.discountPaisa)}`) : ""}
  ${
    invoice.nonTaxableAmountPaisa
      ? totalRow("Exempt", formatPaisa(invoice.nonTaxableAmountPaisa))
      : ""
  }
  ${totalRow("Taxable", formatPaisa(invoice.taxableAmountPaisa))}
  ${totalRow(`VAT ${vatRate}%`, formatPaisa(invoice.vatAmountPaisa))}
  ${totalRow("TOTAL", `Rs. ${formatPaisa(invoice.totalPaisa)}`, "t-grand")}
  <div class="t-sm t-words">${e(invoice.amountInWords)}</div>
  <div class="t-rule"></div>
  <div class="t-kv t-sm"><span>Paid by</span><span>${PAYMENT_LABELS[invoice.paymentMethod]}</span></div>
  <div class="t-kv t-sm"><span>Billed by</span><span>${e(invoice.enteredByName)}</span></div>
  <div class="t-center t-sm t-thanks">${e(store.printFooterNote ?? "Thank you! / धन्यवाद")}</div>
  <div class="t-center t-xs">Computer generated bill · Prints: ${invoice.printCount}</div>
</div>`;
}

const THERMAL_CSS = `
* { box-sizing: border-box; }
body { margin: 0; }
.receipt { display: flex; flex-direction: column; width: 100%; padding: 6px 8px; font-size: 11px; line-height: 1.35; color: #000; }
.t-center { display: flex; justify-content: center; text-align: center; }
.t-bold { font-weight: 700; }
.t-lg { font-size: 14px; }
.t-sm { font-size: 10px; }
.t-xs { font-size: 9px; }
.t-rule { border-top: 1px solid #000; margin: 4px 0; }
.t-kv { display: flex; justify-content: space-between; gap: 8px; }
.t-line { display: flex; flex-direction: column; margin-bottom: 2px; break-inside: avoid; }
.t-desc { font-weight: 600; }
.t-calc { display: flex; justify-content: space-between; gap: 8px; }
.t-total { display: flex; justify-content: space-between; gap: 8px; }
.t-total.t-grand { margin-top: 3px; padding-top: 3px; border-top: 1px solid #000; font-size: 13px; font-weight: 700; }
.t-words { margin-top: 3px; }
.t-thanks { margin-top: 6px; font-weight: 600; }
`;

/* ------------------------------------------------------------- assembly ------ */

export interface InvoiceDocument {
  title: string;
  /** Body markup, identical for the browser and the PDF renderer. */
  html: string;
  /** Layout stylesheet, handed to takumi and inlined in the print view. */
  css: string;
  /** Browser-only additions: paper size and screen chrome, which takumi has no use for. */
  printCss: string;
}

export function buildInvoiceDocument(input: InvoiceDocumentInput): InvoiceDocument {
  const { invoice, format } = input;
  const title = `${DOCUMENT_TITLES[invoice.invoiceType].en} ${invoice.invoiceNumber}`;

  if (format === "thermal80") {
    return {
      title,
      html: thermalBody(input),
      css: THERMAL_CSS,
      printCss: `
@page { size: 80mm auto; margin: 0; }
body { font-family: "Noto Sans", "Noto Sans Devanagari", system-ui, sans-serif; }
@media print { html, body { width: 80mm; background: #fff; } }
@media screen { body { display: flex; justify-content: center; background: #e5e7eb; padding: 16px 0; }
  .receipt { width: 80mm; background: #fff; box-shadow: 0 1px 6px rgba(0,0,0,.2); } }
`,
    };
  }

  return {
    title,
    html: a4Body(input),
    css: A4_CSS,
    printCss: `
@page { size: A4; margin: 14mm 12mm; }
html, body { background: #fff; }
body { font-family: "Inter", "Noto Sans Devanagari", system-ui, sans-serif; }
@media screen { body { background: #e5e7eb; padding: 24px 0; }
  .sheet { width: 210mm; min-height: 297mm; margin: 0 auto; padding: 14mm 12mm; background: #fff; box-shadow: 0 1px 10px rgba(0,0,0,.2); } }
`,
  };
}

/** A complete standalone HTML page, used by the print route and any manual download. */
export function renderPrintPage(document: InvoiceDocument, autoPrint: boolean) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${escapeHtml(document.title)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=Noto+Sans+Devanagari:wght@400;600;700&display=swap" rel="stylesheet" />
<style>${document.css}${document.printCss}</style>
</head>
<body>
${document.html}
${autoPrint ? "<script>window.addEventListener('load', () => window.print());</script>" : ""}
</body>
</html>`;
}
