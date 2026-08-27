import { expect, test } from "@playwright/test";

/**
 * The whole journey a shop goes through on day one: create an account, register the
 * business with its PAN, raise a bill, and get something printable out of it.
 */

const unique = () => Math.random().toString(36).slice(2, 10);

test("registers a store, issues a tax invoice and prints it", async ({ page, context }) => {
  const id = unique();
  const email = `owner-${id}@example.test`;
  // Nine digits, unique per run so the PAN uniqueness rule is not tripped by a rerun.
  const pan = `3${String(Date.now()).slice(-8)}`;

  await page.goto("/signup");
  await page.getByLabel("Name").fill("Nischal Dahal");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill("billing1234");
  await page.getByLabel("Confirm Password").fill("billing1234");
  await page.getByRole("button", { name: "Sign up" }).click();

  // A fresh account has no business on file, so onboarding comes first.
  await page.waitForURL("**/onboarding");
  await page.getByLabel(/Business name/).fill("Everest Traders Pvt. Ltd.");
  await page.getByLabel(/PAN \/ VAT number/).fill(pan);
  await page.getByLabel(/Registration date \(BS\)/).fill("2077-04-01");
  await page.getByLabel(/Street \/ locality/).fill("Naxal, Bhatbhateni Marg");
  await page.getByRole("button", { name: /Register and start billing/ }).click();

  await page.waitForURL("**/app");
  await expect(page.getByRole("heading", { name: "Everest Traders Pvt. Ltd." })).toBeVisible();
  await expect(page.getByText(`PAN ${pan}`).first()).toBeVisible();

  await page.getByRole("button", { name: "New bill" }).first().click();
  await page.waitForURL("**/app/invoices/new");

  await page.getByLabel("Particulars").fill("Himalayan green tea 250g");
  await page.getByLabel("Qty").fill("3");
  await page.getByLabel("Rate").fill("450");
  await page.getByLabel("Name", { exact: true }).fill("Sagarmatha Suppliers");
  await page.getByLabel("Buyer PAN").fill("609876543");

  // 3 x 450 = 1350, plus 13% VAT = 1525.50
  await expect(page.getByText("Rs. 1,525.50")).toBeVisible();

  const printPage = context.waitForEvent("page");
  await page.getByRole("button", { name: /Issue bill and print/ }).click();

  await page.waitForURL(/\/app\/invoices\/[0-9a-f-]{36}/);
  await expect(page.getByText("Tax invoice").first()).toBeVisible();
  await expect(page.getByText("Rs. 1,525.50")).toBeVisible();
  await expect(
    page.getByText("Rupees One Thousand Five Hundred Twenty Five and Fifty Paisa Only"),
  ).toBeVisible();

  // The receipt opens in its own tab, rendered from the same markup as the archived PDF.
  const receipt = await printPage;
  await receipt.waitForLoadState("domcontentloaded");
  await expect(receipt.getByText("TAX INVOICE")).toBeVisible();
  await expect(receipt.getByText(`PAN: ${pan}`)).toBeVisible();
  await expect(receipt.getByText("Sagarmatha Suppliers")).toBeVisible();
  await receipt.close();

  // The archived PDF is served back through the app.
  const pdfUrl = page.url().replace(/\/app\/invoices\//, "/api/invoices/") + "/pdf?format=a4";
  const pdf = await page.request.get(pdfUrl);
  expect(pdf.status()).toBe(200);
  expect(pdf.headers()["content-type"]).toContain("application/pdf");
  expect((await pdf.body()).subarray(0, 5).toString()).toBe("%PDF-");

  // The bill shows up in the register with its sequential number.
  await page.getByRole("link", { name: "All invoices" }).click();
  await page.waitForURL("**/app/invoices**");
  await expect(page.getByText(/2\d{3}\.0\d{2}-000001/).first()).toBeVisible();

  // And in the reports, where the VAT is what the return is filed on.
  await page.getByRole("link", { name: "Reports" }).click();
  await page.waitForURL("**/app/reports");
  await expect(page.getByText("Rs. 1,525.50").first()).toBeVisible();
  await expect(page.getByText("Rs. 175.50").first()).toBeVisible();
  await expect(page.getByText("Bhadra").first()).toBeVisible();

  // The catalogue feeds the biller.
  await page.getByRole("link", { name: "Items" }).click();
  await page.waitForURL("**/app/items");
  await page.getByLabel("Name", { exact: true }).fill("Himalayan green tea 250g");
  await page.getByLabel("Price").fill("450");
  await page.getByRole("button", { name: "Add item" }).click();
  await expect(page.getByRole("cell", { name: "Himalayan green tea 250g" })).toBeVisible();
});
