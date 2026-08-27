import "@tanstack/react-start/server-only";
import { createHash } from "node:crypto";

import interRegular from "@fontsource/inter/files/inter-latin-400-normal.woff2?inline";
import interSemibold from "@fontsource/inter/files/inter-latin-600-normal.woff2?inline";
import interBold from "@fontsource/inter/files/inter-latin-700-normal.woff2?inline";
import devanagariRegular from "@fontsource/noto-sans-devanagari/files/noto-sans-devanagari-devanagari-400-normal.woff2?inline";
import devanagariSemibold from "@fontsource/noto-sans-devanagari/files/noto-sans-devanagari-devanagari-600-normal.woff2?inline";
import devanagariBold from "@fontsource/noto-sans-devanagari/files/noto-sans-devanagari-devanagari-700-normal.woff2?inline";
import { render } from "takumi-pdf";

import { buildInvoiceDocument, type InvoiceDocumentInput } from "./document";

/**
 * PDF rendering with takumi, a wasm layout engine: no headless browser to install,
 * keep alive or sandbox on the server, and the text stays selectable and searchable
 * so an auditor can copy a figure out of an archived bill.
 *
 * Fonts are bundled, not fetched. A shop billing over a patchy connection still has to
 * be able to print, and there is no system font inside wasm to fall back on: an
 * unregistered script fails the render instead of printing blank boxes.
 */

function fontBytes(dataUri: string) {
  return Uint8Array.from(Buffer.from(dataUri.slice(dataUri.indexOf(",") + 1), "base64"));
}

const FONTS = [
  { name: "Inter", weight: 400, data: fontBytes(interRegular) },
  { name: "Inter", weight: 600, data: fontBytes(interSemibold) },
  { name: "Inter", weight: 700, data: fontBytes(interBold) },
  { name: "Noto Sans Devanagari", weight: 400, data: fontBytes(devanagariRegular) },
  { name: "Noto Sans Devanagari", weight: 600, data: fontBytes(devanagariSemibold) },
  { name: "Noto Sans Devanagari", weight: 700, data: fontBytes(devanagariBold) },
];

/**
 * Repeated on every page. Each sheet of a long bill identifies itself, so a page that
 * gets separated from the rest still says which invoice and which taxpayer it belongs to.
 * The band spans the full page, hence its own horizontal padding.
 */
function pageFooter(invoiceNumber: string, pan: string) {
  return `<div style="display:flex;width:100%;justify-content:space-between;padding:0 40px;font-size:8px;color:#6b7280">
  <span>Invoice ${invoiceNumber} · PAN ${pan}</span>
  <span style="display:flex;gap:3px">Page <span class="pageNumber"></span> of <span class="totalPages"></span></span>
</div>`;
}

export interface RenderedPdf {
  bytes: Uint8Array;
  sha256: string;
}

export async function renderInvoicePdf(input: InvoiceDocumentInput): Promise<RenderedPdf> {
  const document = buildInvoiceDocument(input);
  const shared = {
    fonts: FONTS,
    fontFamilies: ["Inter", "Noto Sans Devanagari", "sans-serif"],
    stylesheets: [document.css],
    lang: "en",
    backgroundColor: "#ffffff",
    metadata: {
      title: document.title,
      description: `${input.invoice.invoiceType} issued by ${input.store.name} (PAN ${input.store.pan})`,
      authors: [input.store.name],
      creator: "bill",
      // Fixed to the issue time, so re-archiving a bill produces the same bytes.
      creationDate: input.invoice.issuedAt.toISOString().slice(0, 19),
    },
  };

  const bytes =
    input.format === "thermal80"
      ? // 302px is 80mm at 96 dpi; no height, so the page grows with the basket.
        await render(document.html, { ...shared, viewport: { width: 302 } })
      : await render(document.html, {
          ...shared,
          size: "a4",
          margin: { top: 40, right: 40, bottom: 44, left: 40 },
          footer: pageFooter(input.invoice.invoiceNumber, input.store.pan),
        });

  return { bytes, sha256: createHash("sha256").update(bytes).digest("hex") };
}
