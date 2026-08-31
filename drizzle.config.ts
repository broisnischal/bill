import type { Config } from "drizzle-kit";

// Migrations are generated against the schema alone and applied to D1 by
// `wrangler d1 migrations apply`, which is why there are no database credentials here.
export default {
  out: "./drizzle",
  schema: "./src/lib/db/schema/index.ts",
  breakpoints: true,
  verbose: true,
  strict: true,

  dialect: "sqlite",
} satisfies Config;
