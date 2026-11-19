import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/browser",
  use: {
    baseURL: "http://127.0.0.1:3080",
    reducedMotion: "reduce",
    viewport: { width: 1440, height: 1080 },
    trace: "retain-on-failure",
  },
  webServer: {
    command: "node server/index.mjs",
    url: "http://127.0.0.1:3080/healthz",
    env: {
      PORT: "3080",
      PANEL_AUTH_MODE: "local",
      LOCAL_PASSWORD: "test-only",
    },
    reuseExistingServer: !process.env.CI,
  },
});
