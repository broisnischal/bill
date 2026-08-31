import "@tanstack/react-start/server-only";
import { and, asc, eq, inArray, lt, lte } from "drizzle-orm";

import { db } from "#/lib/db/index.ts";
import { device, invoiceCounter, invoiceNumberLease } from "#/lib/db/schema/index.ts";
import type { InvoiceType } from "#/lib/db/schema/types.ts";

/**
 * Number leasing.
 *
 * A till that loses its network still has to hand the customer a bill carrying a real
 * IRD number, so numbers are taken from the store counter *before* they are needed and
 * parked against one device. The counter moves the moment a block is granted, which is
 * what keeps a leased number and a number the web app hands out from ever colliding.
 *
 * The cost is honest and bounded: numbers still unused when a block is closed are gone.
 * They are recorded as a closed range with a reason, so a gap in the series has a row
 * explaining it rather than looking like a deleted bill.
 */

/** Numbers per block. Small enough that an abandoned device wastes little. */
export const LEASE_BLOCK_SIZE = 50;
/** How long a device may print from a block before it has to come back online. */
export const LEASE_TTL_MS = 14 * 24 * 60 * 60 * 1000;
/** Leases are topped up so a device always leaves the network with at least this many. */
export const LEASE_LOW_WATER = 20;

type Tx = Parameters<Parameters<typeof db.transaction>[0]>[0];

/**
 * A number that cannot be filed under this store, ever.
 *
 * Distinct from a network failure: the bill is on paper with a number that belongs to a
 * series it is not part of, and no amount of retrying changes that. The till marks it and
 * shows it, so the shop can reverse it rather than watch it queue forever.
 */
export class LeaseError extends Error {
  constructor(
    message: string,
    readonly detail: Record<string, string | number>,
  ) {
    super(message);
    this.name = "LeaseError";
  }
}

export interface LeaseBlock {
  id: string;
  fiscalYear: string;
  invoiceType: InvoiceType;
  startSequence: number;
  endSequence: number;
  usedThrough: number;
  expiresAt: Date;
}

function toBlock(row: typeof invoiceNumberLease.$inferSelect): LeaseBlock {
  return {
    id: row.id,
    fiscalYear: row.fiscalYear,
    invoiceType: row.invoiceType,
    startSequence: row.startSequence,
    endSequence: row.endSequence,
    usedThrough: row.usedThrough,
    expiresAt: row.expiresAt,
  };
}

/** Numbers a device may still print from this block. */
function remaining(row: typeof invoiceNumberLease.$inferSelect) {
  const consumed = Math.max(row.usedThrough - row.startSequence + 1, 0);
  return row.endSequence - row.startSequence + 1 - consumed;
}

/**
 * Marks blocks a device can no longer use. Anything still unused inside them is voided;
 * the row stays as the record of the gap.
 */
async function closeStaleLeases(tx: Tx, deviceId: string, now: Date) {
  await tx
    .update(invoiceNumberLease)
    .set({ status: "closed", closedAt: now, closeReason: "lease expired" })
    .where(
      and(
        eq(invoiceNumberLease.deviceId, deviceId),
        eq(invoiceNumberLease.status, "open"),
        lte(invoiceNumberLease.expiresAt, now),
      ),
    );

  await tx
    .update(invoiceNumberLease)
    .set({ status: "exhausted", closedAt: now })
    .where(
      and(
        eq(invoiceNumberLease.deviceId, deviceId),
        eq(invoiceNumberLease.status, "open"),
        eq(invoiceNumberLease.usedThrough, invoiceNumberLease.endSequence),
      ),
    );
}

/** Takes `count` numbers off the store counter. Caller must already be in a transaction. */
async function takeFromCounter(
  tx: Tx,
  {
    storeId,
    fiscalYear,
    invoiceType,
    count,
  }: { storeId: string; fiscalYear: string; invoiceType: InvoiceType; count: number },
) {
  await tx
    .insert(invoiceCounter)
    .values({ storeId, fiscalYear, invoiceType, nextSequence: 1 })
    .onConflictDoNothing();

  const [counter] = await tx
    .select()
    .from(invoiceCounter)
    .where(
      and(
        eq(invoiceCounter.storeId, storeId),
        eq(invoiceCounter.fiscalYear, fiscalYear),
        eq(invoiceCounter.invoiceType, invoiceType),
      ),
    )
    .for("update");

  const start = counter.nextSequence;
  await tx
    .update(invoiceCounter)
    .set({ nextSequence: start + count })
    .where(
      and(
        eq(invoiceCounter.storeId, storeId),
        eq(invoiceCounter.fiscalYear, fiscalYear),
        eq(invoiceCounter.invoiceType, invoiceType),
      ),
    );

  return { start, end: start + count - 1 };
}

/**
 * Returns every block the device may print from for this series, granting another one
 * when what it holds has fallen below the low-water mark. Called on every sync, so a
 * device that bills all day is always topped up before it next goes dark.
 */
export async function ensureLeases({
  storeId,
  deviceId,
  fiscalYear,
  invoiceType,
  want = LEASE_LOW_WATER,
  now = new Date(),
}: {
  storeId: string;
  deviceId: string;
  fiscalYear: string;
  invoiceType: InvoiceType;
  want?: number;
  now?: Date;
}): Promise<LeaseBlock[]> {
  return db.transaction(async (tx) => {
    await closeStaleLeases(tx, deviceId, now);

    const open = await tx
      .select()
      .from(invoiceNumberLease)
      .where(
        and(
          eq(invoiceNumberLease.deviceId, deviceId),
          eq(invoiceNumberLease.storeId, storeId),
          eq(invoiceNumberLease.fiscalYear, fiscalYear),
          eq(invoiceNumberLease.invoiceType, invoiceType),
          eq(invoiceNumberLease.status, "open"),
        ),
      )
      .orderBy(asc(invoiceNumberLease.startSequence));

    const held = open.reduce((total, row) => total + remaining(row), 0);
    if (held >= want) return open.map(toBlock);

    const size = Math.max(want - held, LEASE_BLOCK_SIZE);
    const { start, end } = await takeFromCounter(tx, {
      storeId,
      fiscalYear,
      invoiceType,
      count: size,
    });

    const [granted] = await tx
      .insert(invoiceNumberLease)
      .values({
        storeId,
        deviceId,
        fiscalYear,
        invoiceType,
        startSequence: start,
        endSequence: end,
        // Nothing used yet: one below the start, so `remaining` counts the whole block.
        usedThrough: start - 1,
        expiresAt: new Date(now.getTime() + LEASE_TTL_MS),
      })
      .returning();

    return [...open, granted].map(toBlock);
  });
}

/**
 * Checks that a sequence a device printed really came from a block it holds, and moves
 * the block's watermark up. Runs inside the invoice transaction, so a bill and the proof
 * its number was legitimate are committed together.
 */
export async function consumeLeasedSequence(
  tx: Tx,
  {
    leaseId,
    deviceId,
    storeId,
    fiscalYear,
    invoiceType,
    sequence,
    now,
  }: {
    leaseId: string;
    deviceId: string;
    storeId: string;
    fiscalYear: string;
    invoiceType: InvoiceType;
    sequence: number;
    now: Date;
  },
) {
  const [lease] = await tx
    .select()
    .from(invoiceNumberLease)
    .where(eq(invoiceNumberLease.id, leaseId))
    .for("update");

  // These are permanent. A block that was never granted to this device, or a number
  // outside it, will not become valid by waiting, so the push has to fail in a way the
  // till records rather than one it retries for ever.
  if (
    !lease ||
    lease.deviceId !== deviceId ||
    lease.storeId !== storeId ||
    lease.fiscalYear !== fiscalYear ||
    lease.invoiceType !== invoiceType
  ) {
    throw new LeaseError("That number was not issued to this device", {
      leaseId,
      sequence,
      deviceId,
    });
  }
  if (sequence < lease.startSequence || sequence > lease.endSequence) {
    throw new LeaseError("That number is outside the block this device holds", {
      sequence,
      from: lease.startSequence,
      to: lease.endSequence,
    });
  }
  if (lease.status === "closed") {
    throw new LeaseError("The block this number came from was closed before the bill reached us", {
      leaseId,
      closeReason: lease.closeReason ?? "",
    });
  }

  if (sequence > lease.usedThrough) {
    const exhausted = sequence >= lease.endSequence;
    await tx
      .update(invoiceNumberLease)
      .set({
        usedThrough: sequence,
        status: exhausted ? "exhausted" : lease.status,
        closedAt: exhausted ? now : lease.closedAt,
      })
      .where(eq(invoiceNumberLease.id, leaseId));
  }

  return lease;
}

/**
 * Hands a store's numbers back when a device is retired or lost. The unused part of each
 * block is voided rather than reissued, so no two bills can ever carry the same number.
 */
export async function releaseDeviceLeases({
  deviceId,
  reason,
  now = new Date(),
}: {
  deviceId: string;
  reason: string;
  now?: Date;
}) {
  return db
    .update(invoiceNumberLease)
    .set({ status: "closed", closedAt: now, closeReason: reason })
    .where(and(eq(invoiceNumberLease.deviceId, deviceId), eq(invoiceNumberLease.status, "open")))
    .returning({ id: invoiceNumberLease.id });
}

/**
 * Ranges that were leased and never billed, for the fiscal-year report an auditor asks
 * for when the printed series skips a number.
 */
export async function voidedRanges(storeId: string, fiscalYear: string) {
  const rows = await db
    .select()
    .from(invoiceNumberLease)
    .where(
      and(
        eq(invoiceNumberLease.storeId, storeId),
        eq(invoiceNumberLease.fiscalYear, fiscalYear),
        inArray(invoiceNumberLease.status, ["closed", "exhausted"]),
        lt(invoiceNumberLease.usedThrough, invoiceNumberLease.endSequence),
      ),
    )
    .orderBy(asc(invoiceNumberLease.startSequence));

  return rows.map((row) => ({
    invoiceType: row.invoiceType,
    from: Math.max(row.usedThrough + 1, row.startSequence),
    to: row.endSequence,
    deviceId: row.deviceId,
    reason: row.closeReason ?? "device stopped billing from this block",
    closedAt: row.closedAt,
  }));
}

/** Registers or refreshes the till that is calling us. */
export async function upsertDevice({
  id,
  storeId,
  userId,
  name,
  platform,
  appVersion,
  pushToken,
}: typeof device.$inferInsert) {
  const [row] = await db
    .insert(device)
    .values({ id, storeId, userId, name, platform, appVersion, pushToken })
    .onConflictDoUpdate({
      target: device.id,
      set: { name, appVersion, pushToken, lastSeenAt: new Date(), storeId, userId },
    })
    .returning();
  return row;
}
