import { describe, expect, it } from "vite-plus/test";

import { cardCode, issueShopperLink, readCardCode, readShopperLink } from "./card-code";

const SECRET = "test-secret-not-the-app-one";

/**
 * A customer's card is shown to strangers across a counter, so the only thing standing
 * between it and misuse is that a photograph of it stops working. These are the cases
 * that has to survive.
 */
describe("card codes", () => {
  const token = "9f2c1d4a6b8e40f3a1c5d7e9b2f4a6c8";

  it("resolves the code it just produced", () => {
    const { code } = cardCode(SECRET, token);
    expect(readCardCode(SECRET, code)).toBe(token);
  });

  it("still resolves a card held up as the code rolls over", () => {
    const now = Date.now();
    const { code } = cardCode(SECRET, token, now);
    // Scanned four minutes later, which may land in the following window.
    expect(readCardCode(SECRET, code, now + 4 * 60_000)).toBe(token);
  });

  it("refuses a photograph taken yesterday", () => {
    const now = Date.now();
    const { code } = cardCode(SECRET, token, now);
    expect(readCardCode(SECRET, code, now + 24 * 60 * 60_000)).toBeNull();
  });

  it("refuses a code whose signature was tampered with", () => {
    const { code } = cardCode(SECRET, token);
    const [head, window, signature] = code.split(".");
    expect(readCardCode(SECRET, `${head}.${window}.${signature.slice(0, -1)}x`)).toBeNull();
  });

  it("refuses a code someone assembled themselves", () => {
    expect(readCardCode(SECRET, token)).toBeNull();
    expect(readCardCode(SECRET, `${token}.999999.aaaaaaaaaaaaaaaaaaaaaaaaaaa`)).toBeNull();
    expect(readCardCode(SECRET, "")).toBeNull();
  });

  it("says when the code stops working", () => {
    const now = Date.now();
    const { expiresAt } = cardCode(SECRET, token, now);
    const remaining = new Date(expiresAt).getTime() - now;
    expect(remaining).toBeGreaterThan(0);
    expect(remaining).toBeLessThanOrEqual(5 * 60_000);
  });
});

/**
 * The link a shop keeps after scanning outlives the code, because a till can be offline
 * for weeks and still has to say whose bill it wrote.
 */
describe("shopper links", () => {
  const userId = "8c85df13-c6de-409f-8c06-dd8ee24d51dc";

  it("names the shopper it was issued for", () => {
    expect(readShopperLink(SECRET, issueShopperLink(SECRET, userId))).toBe(userId);
  });

  it("survives a till that has been offline for a fortnight", () => {
    const now = Date.now();
    const link = issueShopperLink(SECRET, userId, now);
    expect(readShopperLink(SECRET, link, now + 14 * 24 * 60 * 60_000)).toBe(userId);
  });

  it("expires eventually", () => {
    const now = Date.now();
    const link = issueShopperLink(SECRET, userId, now);
    expect(readShopperLink(SECRET, link, now + 31 * 24 * 60 * 60_000)).toBeNull();
  });

  it("cannot be forged by a till that wants to attribute a bill to someone", () => {
    const forged = Buffer.from(`${userId}|${Date.now() + 60_000}`).toString("base64url");
    expect(readShopperLink(SECRET, `${forged}.notarealsignature000000000`)).toBeNull();
    expect(readShopperLink(SECRET, userId)).toBeNull();
  });
});
