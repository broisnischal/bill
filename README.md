# bill

IRD-compliant invoicing for Nepali businesses. A shop registers once with its PAN, VAT
status and registration date, then raises bills that carry everything Rule 17 of the VAT
Rules 2053 asks for, prints them on A4 or an 80mm thermal roll, archives a PDF of each
one, and pushes them to the IRD Central Billing Monitoring System.

Built on [TanStarter](https://github.com/mugnavo/tanstarter): TanStack Start, Drizzle,
Better Auth, Tailwind and shadcn on Base UI. PDFs are rendered by
[takumi](https://takumi.kane.tw/docs/pdf/invoices), a wasm layout engine, so there is no
headless browser on the server. Archived copies go to MinIO locally and to any S3 API in
production.

## Running it

```sh
bun install
cp .env.example .env          # then fill BETTER_AUTH_SECRET
docker compose up -d          # Postgres on 5455, MinIO on 9002 (console 9003)
bun run db migrate
bun run dev                   # http://localhost:3000
```

Sign up, register the business, and the biller is one click away.

## What makes a bill compliant here

**Numbering.** Each store gets one series per fiscal year and document type. The next
number is handed out under `SELECT ... FOR UPDATE` inside the same transaction that
writes the bill, so five tills billing at once produce 1 to 5 with nothing skipped and
nothing duplicated. The integration test proves that with real concurrency.

**Immutability.** An issued bill is never updated or deleted. A mistake is either
cancelled with a reason, which keeps the row and marks it cancelled, or reversed with a
credit note in its own `CN-` series that points back at the original. Cancellation is
refused once the fiscal year has closed; a credit note is the only route then.

**Dates.** Bills are dated in Bikram Sambat (`miti`) and in Gregorian, both resolved
against Kathmandu wall-clock time. The fiscal year runs Shrawan 1 to the end of Ashad and
is stored in IRD notation, `2082.083`.

**Money.** Every amount is an integer number of paisa and every quantity is thousandths
of a unit. No float touches a total. VAT is charged at 13% on the taxable value after
discount, exempt lines are totalled separately, and an invoice-level discount is split
across lines so the parts always add back up to the whole.

**Print tracking.** The first copy prints as the original; every later one comes out
stamped `Copy of Original (#n)`, and the count plus who printed it lands in the audit
trail. The print route records the print before it returns the markup.

**Audit trail.** `invoice_audit` is append-only: created, printed, reprinted, cancelled,
credit note issued, PDF archived, CBMS synced or failed, each with the actor, the time,
the IP and the user agent.

**Archive.** The moment a bill is issued its A4 PDF is rendered and written to object
storage, keyed `stores/{store}/{fiscal year}/{number}-a4.pdf`, with the SHA-256 stored on
the row so tampering is detectable. Rendering is deterministic: the same bill produces
the same bytes.

**CBMS.** VAT-registered stores can turn on real-time sync in settings. Bills go to
`/api/bill` and credit notes to `/api/billreturn` with the field names the IRD's API
document specifies. The push happens in the background, so a slow or dead link never
blocks a sale: the bill is recorded, queued, and retried from the dashboard. The CBMS
password is encrypted with AES-256-GCM under a key derived from `BETTER_AUTH_SECRET`.

## Printing

One builder produces the markup for both outputs, so the browser print view and the
archived PDF cannot drift apart.

- **A4** for the tax invoice, with a repeating footer carrying the bill number, the PAN
  and the page count.
- **80mm** (302 px at 96 dpi, height grows with the basket) for a counter thermal
  printer. Print it from the browser to any ESC/POS printer with a driver, or hand the
  PDF to the printer directly.

`/print/{id}?format=a4|thermal80` returns the printable page and counts the print.
`/api/invoices/{id}/pdf?format=...` streams the PDF through the app, so the bucket never
has to be public.

## Reports

Sales, taxable value, exempt value, VAT and discounts for a fiscal year, broken down by
Nepali month, payment method, item and customer, with the sales register downloadable as
CSV in the column order a return is filed from. Cancelled bills are excluded and credit
notes are subtracted, so the figures are the ones that belong on the return rather than a
raw count of documents issued.

## Tests

```sh
bun run lint       # type-aware lint and type check
bun run test       # unit tests, plus integration tests against Postgres and MinIO
bun run test:e2e   # full browser journey: signup, onboarding, bill, print, reports
```

The integration test writes to the database from `.env` and skips itself if nothing is
listening. The E2E run builds for production and uses `.env.e2e`, which points at a
separate `bill_e2e` database and bucket; create it with
`docker exec bill-postgres psql -U postgres -c "CREATE DATABASE bill_e2e"` and migrate it
with `DATABASE_URL=... bun run db migrate`.

## Configuration

| Variable                                                                                                   | What it is                                                         |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `DATABASE_URL`                                                                                             | Postgres connection string                                         |
| `BETTER_AUTH_SECRET`                                                                                       | Session secret, also derives the CBMS credential key               |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION`, `S3_FORCE_PATH_STYLE` | Object storage for archived PDFs                                   |
| `IRD_CBMS_BILL_URL`, `IRD_CBMS_BILL_RETURN_URL`                                                            | CBMS endpoints; point them at `cbapitest.ird.gov.np` while testing |

Rotating `BETTER_AUTH_SECRET` invalidates stored CBMS passwords, which then have to be
re-entered in settings.

## Not done yet

- Real-time CBMS sync has been written against the published field list but not verified
  against a live IRD account, and the IRD still has to approve the software against each
  PAN before a taxpayer switches it on.
- Credit notes reverse a bill in full. Partial returns need line selection.
- Staff accounts exist in the schema (`store_member` with owner, manager and cashier) but
  there is no invite screen yet, so a store has one user.
- A retry cron for queued CBMS pushes: the retry runs from the dashboard button today.
