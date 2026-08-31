import "@tanstack/react-start/server-only";
import { env } from "cloudflare:workers";
import { drizzle } from "drizzle-orm/d1";

import { authRelations } from "#/lib/db/schema/auth.schema.ts";
import { relations } from "#/lib/db/schema/relations.ts";

export const db = drizzle(env.DB, {
  // authRelations uses defineRelationsPart,
  // so it must come after the main relations.
  // https://orm.drizzle.team/docs/relations-v2#relations-parts
  relations: { ...relations, ...authRelations },
});

export type Db = typeof db;

/**
 * D1 has no interactive transactions: it speaks one statement at a time, so a `BEGIN`
 * issued from the Worker is rejected. Multi-statement writes therefore run unwrapped,
 * and the invariants that used to lean on the transaction are carried by the statements
 * themselves instead: an invoice number comes from a single atomic
 * `UPDATE ... RETURNING` on the counter, and a lease closes the same way. A write that
 * fails halfway leaves the number spent, which the series already models as a recorded
 * gap rather than as a number to reissue.
 */
export function withTransaction<T>(run: (tx: Db) => Promise<T>): Promise<T> {
  return run(db);
}
