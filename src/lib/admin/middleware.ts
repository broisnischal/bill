import { createMiddleware } from "@tanstack/react-start";
import { setResponseStatus } from "@tanstack/react-start/server";

import { env } from "#/env/server.ts";
import { authMiddleware } from "#/lib/auth/middleware.ts";

/**
 * Who may review a business.
 *
 * An allow-list of mobile numbers rather than a role column: everyone signs in with a
 * phone, there are very few reviewers, and a list in version control is one that cannot
 * be granted by accident. Adding a reviewer is a deploy, which is the right amount of
 * friction for the person who can turn a PAN into a billable taxpayer.
 */
export function adminPhones(): string[] {
  return env.ADMIN_PHONES.split(",")
    .map((phone) => phone.trim())
    .filter(Boolean);
}

export function isAdminPhone(phoneNumber: string | null | undefined): boolean {
  if (!phoneNumber) return false;
  return adminPhones().includes(phoneNumber);
}

export const adminMiddleware = createMiddleware()
  .middleware([authMiddleware])
  .server(async ({ next, context }) => {
    // Read from the session's own user rather than anything the caller sent.
    if (!isAdminPhone(context.user.phoneNumber)) {
      // 404 rather than 403: the review queue does not announce itself to accounts that
      // cannot see it.
      setResponseStatus(404);
      throw new Error("Not found");
    }
    return next({ context: { admin: context.user } });
  });
