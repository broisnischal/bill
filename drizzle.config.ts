import type { Config } from "drizzle-kit";

// Read straight from the environment: drizzle-kit loads this config with its own
// resolver, outside the app's `#/` import map.
const url = process.env.DATABASE_URL;
if (!url) throw new Error("DATABASE_URL is not set. Copy .env.example to .env first.");

export default {
  out: "./drizzle",
  schema: "./src/lib/db/schema/index.ts",
  breakpoints: true,
  verbose: true,
  strict: true,

  dialect: "postgresql",
  dbCredentials: { url },
} satisfies Config;
