# Billing Rules

Domain rules that are not negotiable in this codebase. Breaking one of these breaks the
compliance story, not just a test.

## Documents

- `tax_invoice` (कर बीजक), `abbreviated_tax_invoice` (Rule 18, capped at NPR 10,000) and
  `credit_note` (Rule 20) each keep their own number series per store and fiscal year.
- An issued document is immutable. No code path may `UPDATE` an amount, a line, a buyer
  or a date on `invoice` / `invoice_item`. Corrections are a cancellation with a reason,
  or a credit note.
- The only columns that change after issue are print counters, CBMS sync state, the
  archived PDF pointer, and the cancellation fields.
- Buyer details are snapshotted onto the invoice. Never join to `customer` for anything
  that gets printed.

## Numbering

- Allocate through `allocateSequence` inside the invoice transaction. It inserts the
  counter row with `onConflictDoNothing`, then locks it `FOR UPDATE`.
- Never derive a number from `count(*)` or from the last row: a gap is a compliance
  finding.

## Money and quantities

- Amounts are integer paisa (`bigint`, mode `number`). Quantities are thousandths of a
  unit. Parse user input with `parsePaisa` / `parseQuantityMilli`, never `parseFloat`.
- All arithmetic lives in `lib/invoice/calc.ts` so the screen, the PDF and the CBMS
  payload agree to the paisa.

## Dates

- `issuedAt` is an instant; `miti` is the Bikram Sambat date it falls on in Kathmandu.
- Fiscal year comes from `fiscalYearFor`, never from the Gregorian year.

## Printing and archiving

- `buildInvoiceDocument` is the single source for both the print page and the PDF. Layout
  is flexbox only, no tables and no dashed borders, since that is the subset takumi and
  the browser both render the same way.
- Record the print before serving the markup. The first copy is the original; later ones
  print as copies.

## CBMS

- Sync never blocks issuing a bill. Failures are recorded on the row and retried.
- Credentials are encrypted at rest and must never appear in `invoice.irdResponse`, the
  audit trail or a log line.
