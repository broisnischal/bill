import { createFileRoute, Link } from "@tanstack/react-router";
import { CheckIcon, FileTextIcon, PrinterIcon, ReceiptIcon, ShieldCheckIcon } from "lucide-react";

import { ThemeToggle } from "#/components/theme-toggle.tsx";
import { Button } from "#/components/ui/button.tsx";

export const Route = createFileRoute("/")({
  component: HomePage,
});

const FEATURES = [
  {
    icon: FileTextIcon,
    title: "Bills that pass an audit",
    body: "Tax invoice, abbreviated invoice and credit note, each carrying the seller and buyer PAN, the miti in Bikram Sambat, the taxable value, 13% VAT and the amount in words.",
  },
  {
    icon: ShieldCheckIcon,
    title: "Numbers with no gaps",
    body: "Every bill takes the next number in its fiscal-year series under a database lock. Issued bills are never edited or deleted: they are cancelled with a reason, or reversed with a credit note.",
  },
  {
    icon: PrinterIcon,
    title: "Prints on the printer you own",
    body: "The same document renders as an A4 sheet and as an 80mm receipt for a counter thermal printer. Reprints come out marked as copies, and every print is counted.",
  },
  {
    icon: ReceiptIcon,
    title: "Archived and reportable",
    body: "A PDF of each bill is written to object storage with its SHA-256 the moment it is issued. Sales, VAT and the sales register are there by Nepali month, ready for the return.",
  },
];

function HomePage() {
  return (
    <div className="min-h-svh bg-background">
      <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-6 py-6">
        <span className="text-lg font-semibold tracking-tight">bill</span>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Button variant="ghost" render={<Link to="/login" />} nativeButton={false}>
            Log in
          </Button>
          <Button render={<Link to="/signup" />} nativeButton={false}>
            Register your store
          </Button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-6 pb-20">
        <section className="grid gap-6 py-16">
          <p className="w-fit rounded-full border px-3 py-1 text-xs text-muted-foreground">
            Billing for Nepali businesses
          </p>
          <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
            Invoicing built around the rules the IRD actually checks
          </h1>
          <p className="max-w-2xl text-lg text-muted-foreground">
            Register the business once with its PAN, VAT status and registration date. From then on
            it is one screen to raise a bill, print it, archive the PDF and push it to the Central
            Billing Monitoring System.
          </p>
          <div className="flex flex-wrap gap-3">
            <Button size="lg" render={<Link to="/signup" />} nativeButton={false}>
              Start billing
            </Button>
            <Button size="lg" variant="outline" render={<Link to="/login" />} nativeButton={false}>
              I already have an account
            </Button>
          </div>
          <ul className="flex flex-wrap gap-x-6 gap-y-2 pt-2 text-sm text-muted-foreground">
            {[
              "Bikram Sambat dates",
              "13% VAT and exempt goods",
              "Credit notes",
              "80mm and A4",
              "Audit trail",
            ].map((point) => (
              <li key={point} className="flex items-center gap-1.5">
                <CheckIcon className="size-4" aria-hidden="true" />
                {point}
              </li>
            ))}
          </ul>
        </section>

        <section className="grid gap-4 sm:grid-cols-2">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="grid gap-2 rounded-2xl border p-5">
              <feature.icon className="size-5" aria-hidden="true" />
              <h2 className="font-medium">{feature.title}</h2>
              <p className="text-sm text-muted-foreground">{feature.body}</p>
            </div>
          ))}
        </section>

        <section className="mt-16 rounded-2xl border border-dashed p-6 text-sm text-muted-foreground">
          Real-time CBMS sync is switched on per business, after the IRD approves the software
          against that PAN. Until then bills are still issued, numbered, printed and archived, and
          they queue for the day sync is turned on.
        </section>
      </main>
    </div>
  );
}
