import "@tanstack/react-start/server-only";
import { env } from "#/env/server.ts";

import * as codes from "./card-code";

/**
 * The card helpers, bound to this app's secret.
 *
 * Rotating BETTER_AUTH_SECRET invalidates every outstanding card code and shopper link,
 * which is the same blast radius as rotating it invalidates sessions.
 */
export const cardCode = (token: string) => codes.cardCode(env.BETTER_AUTH_SECRET, token);

export const readCardCode = (code: string) => codes.readCardCode(env.BETTER_AUTH_SECRET, code);

export const issueShopperLink = (userId: string) =>
  codes.issueShopperLink(env.BETTER_AUTH_SECRET, userId);

export const readShopperLink = (link: string) =>
  codes.readShopperLink(env.BETTER_AUTH_SECRET, link);
