import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/** Development keeps API calls same-origin through the panel's constrained proxy. */
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://127.0.0.1:8080" } },
  build: { sourcemap: false },
});
