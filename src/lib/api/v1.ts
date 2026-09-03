import "@tanstack/react-start/server-only";
import { eq } from "drizzle-orm";
import * as z from "zod";

import { auth } from "#/lib/auth/auth.ts";
import { db } from "#/lib/db/index.ts";
import { device, store, storeMember } from "#/lib/db/schema/index.ts";
import type { StoreRole } from "#/lib/db/schema/types.ts";
import type { Actor } from "#/lib/invoice/service.ts";

/**
 * The HTTP surface the mobile apps talk to.
 *
 * The web app reaches the same services through server functions; this exists because a
 * phone holds a bearer token rather than a cookie and needs plain JSON. Handlers stay
 * thin: parse, authorise, call the service, serialise. Nothing here decides anything a
 * service does not already decide, so the web and the apps cannot drift apart.
 */

/** An error a client can act on. Anything else is a 500 with no detail. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly detail?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

/**
 * Runs a handler body so every failure leaves as `{ error: { code, message } }`. The app
 * shows `message` to the shopkeeper, so it is written for them and never leaks a stack.
 */
export async function run(fn: () => Promise<Response>) {
  try {
    return await fn();
  } catch (error) {
    if (error instanceof ApiError) {
      return json(
        { error: { code: error.code, message: error.message, detail: error.detail } },
        error.status,
      );
    }
    if (error instanceof z.ZodError) {
      return json(
        {
          error: {
            code: "invalid_request",
            message: error.issues[0]?.message ?? "That request was not valid",
            detail: {
              issues: error.issues.map((issue) => ({
                path: issue.path.join("."),
                message: issue.message,
              })),
            },
          },
        },
        422,
      );
    }
    console.error("[api/v1]", error);
    return json(
      { error: { code: "server_error", message: "Something went wrong. Try again." } },
      500,
    );
  }
}

export async function parseBody<T extends z.ZodType>(request: Request, schema: T) {
  const body = await request.json().catch(() => {
    throw new ApiError(400, "invalid_json", "The request body was not valid JSON");
  });
  return schema.parse(body) as z.output<T>;
}

/** The signed-in user, from the bearer token the app stores after OTP verification. */
export async function requireUser(request: Request) {
  const session = await auth.api.getSession({ headers: request.headers });
  if (!session?.user) {
    throw new ApiError(401, "unauthenticated", "Sign in to continue");
  }
  return session.user;
}

export interface StoreContext {
  user: Awaited<ReturnType<typeof requireUser>>;
  store: typeof store.$inferSelect;
  role: StoreRole;
  actor: Actor;
}

/** The caller plus the store they bill for. A user with no store gets a 409, not a 403. */
export async function requireStore(request: Request): Promise<StoreContext> {
  const user = await requireUser(request);

  const [membership] = await db
    .select({ store, role: storeMember.role })
    .from(storeMember)
    .innerJoin(store, eq(store.id, storeMember.storeId))
    .where(eq(storeMember.userId, user.id))
    .limit(1);

  if (!membership) {
    throw new ApiError(409, "no_store", "Register your business before billing");
  }

  return {
    user,
    store: membership.store,
    role: membership.role,
    actor: {
      id: user.id,
      name: user.name,
      ipAddress: request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? undefined,
      userAgent: request.headers.get("user-agent") ?? undefined,
    },
  };
}

/**
 * The store, and it has to have been approved.
 *
 * Everything that writes a bill goes through here. A business waiting on review can set
 * itself up — products, buyers, printer — because that is the useful thing to do while
 * waiting; what it cannot do is put a PAN on a document a tax office will read.
 */
export async function requireApprovedStore(request: Request): Promise<StoreContext> {
  const context = await requireStore(request);
  if (context.store.status !== "approved") {
    throw new ApiError(
      403,
      context.store.status === "rejected" ? "store_rejected" : "store_pending",
      context.store.status === "rejected"
        ? (context.store.reviewNote ??
            "The business was not approved. Check what was asked for and send it again.")
        : "The business is still being reviewed. Billing opens as soon as it is approved.",
    );
  }
  return context;
}

export function requireAdmin(context: StoreContext) {
  if (context.role !== "owner" && context.role !== "manager") {
    throw new ApiError(403, "forbidden", "This needs owner or manager access");
  }
}

/**
 * The till making the request. Sent as a header on every call so a bill can be traced
 * back to the phone that printed it without the client having to repeat it in bodies.
 */
export async function requireDevice(request: Request, context: StoreContext) {
  const deviceId = request.headers.get("x-device-id");
  if (!deviceId) {
    throw new ApiError(400, "device_required", "This device is not registered yet");
  }

  const [row] = await db.select().from(device).where(eq(device.id, deviceId));
  if (!row || row.storeId !== context.store.id) {
    throw new ApiError(409, "device_unknown", "This device is not registered for your business");
  }

  // Cheap presence signal: the shop can see which till last billed and when.
  void db
    .update(device)
    .set({ lastSeenAt: new Date() })
    .where(eq(device.id, deviceId))
    .catch(() => {});

  return row;
}
