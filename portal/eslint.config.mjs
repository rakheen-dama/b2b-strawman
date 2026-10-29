import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import localRules from "./eslint-rules/index.mjs";

const NO_VOCABULARY_LAYER =
  "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
  // Playwright strict assertions (Phase 88 628G, R-8; frontend twin 628E/628F): no `.catch()` on
  // isVisible & co. and no ignored `isVisible({ timeout })` in any portal e2e file. See
  // frontend/e2e/AUTOMATION-NOTES.md › Assertion rules.
  {
    files: ["e2e/**/*.ts"],
    plugins: { "kazi-portal-local": localRules },
    rules: {
      "kazi-portal-local/no-isvisible-catch": "error",
    },
  },
  // Single vocabulary (Phase 87 611E, ADR-338): the runtime terminology layer and the shared
  // terminology map are deleted — write the legal word. Same bans as frontend/eslint.config.mjs.
  // Keep every restricted import in THIS object: in flat config a later object that sets the
  // same rule REPLACES these options (no merge).
  {
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            { name: "@/lib/terminology", message: NO_VOCABULARY_LAYER },
            {
              name: "@/components/terminology-heading",
              message: NO_VOCABULARY_LAYER,
            },
            {
              name: "@/components/terminology-text",
              message: NO_VOCABULARY_LAYER,
            },
            {
              name: "@b2mash/shared/terminology-map",
              message: NO_VOCABULARY_LAYER,
            },
            {
              name: "@b2mash/shared",
              importNames: [
                "TERMINOLOGY",
                "TERMINOLOGY_BASE",
                "PORTAL_TERMINOLOGY",
                "auditTabLabel",
              ],
              message: NO_VOCABULARY_LAYER,
            },
          ],
        },
      ],
    },
  },
]);

export default eslintConfig;
