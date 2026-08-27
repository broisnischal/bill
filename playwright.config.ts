import { defineConfig, devices } from "@playwright/test";

const PORT = 3100;
const baseURL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  outputDir: ".cache/playwright",
  use: {
    baseURL,
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "vp run build && vp run start:e2e",
    // Secrets, the database and the bucket come from .env.e2e, which start:e2e loads.
    env: {
      NODE_ENV: "production",
      PORT: String(PORT),
      // Baked into the client bundle at build time, so it has to point at the E2E port.
      VITE_BASE_URL: baseURL,
    },
    url: baseURL,
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
