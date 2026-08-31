import { createFileRoute } from "@tanstack/react-router";
import { and, count, desc, eq, gt, ilike, or } from "drizzle-orm";

import { run, json, requireStore } from "#/lib/api/v1.ts";
import { db } from "#/lib/db/index.ts";
import { invoice } from "#/lib/db/schema/index.ts";
import { invoiceListSchema } from "#/lib/invoice/schema.ts";

/**
 * The bill history, for the list screen and for a second till catching up on what the
 * first one wrote. `since` pulls only what changed, which is what the app uses; the
 * search and page parameters are for a person scrolling.
 */
export const Route = createFileRoute("/api/v1/invoices/")({
  server: {
    handlers: {
      GET: ({ request }) =>
        run(async () => {
          const context = await requireStore(request);
          const url = new URL(request.url);
          const query = invoiceListSchema.parse({
            fiscalYear: url.searchParams.get("fiscalYear") ?? undefined,
            status: url.searchParams.get("status") ?? undefined,
            search: url.searchParams.get("search") ?? undefined,
            page: url.searchParams.get("page") ?? undefined,
            pageSize: url.searchParams.get("pageSize") ?? undefined,
          });
          const since = url.searchParams.get("since");

          const filters = [eq(invoice.storeId, context.store.id)];
          if (query.fiscalYear) filters.push(eq(invoice.fiscalYear, query.fiscalYear));
          if (query.status !== "all") filters.push(eq(invoice.status, query.status));
          if (since) filters.push(gt(invoice.createdAt, new Date(since)));
          if (query.search) {
            const term = `%${query.search}%`;
            filters.push(
              or(
                ilike(invoice.invoiceNumber, term),
                ilike(invoice.buyerName, term),
                ilike(invoice.buyerPan, term),
              )!,
            );
          }

          const where = and(...filters);
          const [{ total }] = await db.select({ total: count() }).from(invoice).where(where);
          const rows = await db
            .select()
            .from(invoice)
            .where(where)
            .orderBy(desc(invoice.issuedAt), desc(invoice.sequence))
            .limit(query.pageSize)
            .offset((query.page - 1) * query.pageSize);

          return json({
            rows,
            total,
            page: query.page,
            pageSize: query.pageSize,
            serverTime: new Date().toISOString(),
          });
        }),
    },
  },
});
