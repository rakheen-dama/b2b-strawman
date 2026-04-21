import { defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  test: {
    environment: "happy-dom",
    setupFiles: ["./vitest.setup.ts"],
    passWithNoTests: true,
    // Playwright e2e specs live in portal/e2e/ and must not be run by vitest —
    // they use @playwright/test's runner (see playwright.portal.config.ts).
    exclude: ["node_modules/**", "dist/**", ".next/**", "e2e/**"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "json"],
      include: ["app/**/*.{ts,tsx}", "components/**/*.{ts,tsx}", "hooks/**/*.{ts,tsx}", "lib/**/*.ts"],
      exclude: ["**/*.test.{ts,tsx}", "**/*.spec.{ts,tsx}", "components/ui/**"],
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "."),
    },
  },
});
