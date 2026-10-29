import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import localRules from "./eslint-rules/index.mjs";

const NO_VOCABULARY_LAYER = "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // Local rules — Class-3 / OBS-2103 prevention. See eslint-rules/.
  {
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    plugins: { "kazi-frontend-local": localRules },
    rules: {
      "kazi-frontend-local/no-aschild-adjacency": "error",
    },
  },
  // Playwright strict assertions (Phase 88 628E/628F, U22, R10 §0 M1): no `.catch()` on isVisible &
  // co., no ignored `isVisible({ timeout })`. See e2e/AUTOMATION-NOTES.md › Assertion rules. Scope
  // grows directory by directory with the 628F default-config sweep; the slice ends on e2e/**.
  {
    files: [
      "e2e/tests/legal-lifecycle/**/*.ts",
      "e2e/tests/legal-depth-ii/**/*.ts",
      "e2e/helpers/**/*.ts",
      "e2e/fixtures/**/*.ts",
      "e2e/tests/customers/**/*.ts",
      "e2e/tests/projects/**/*.ts",
      "e2e/tests/settings/**/*.ts",
      "e2e/tests/navigation/**/*.ts",
    ],
    // KC-only demo recording scripts; cannot run on the mock stack; not converted (628F R-2) —
    // follow-up: convert under KC-STACK or delete.
    ignores: ["e2e/tests/demo-full-90day.spec.ts", "e2e/tests/demo-recording.spec.ts"],
    rules: {
      "kazi-frontend-local/no-isvisible-catch": "error",
    },
  },
  // Single vocabulary (Phase 87 611D, ADR-338): the runtime terminology layer is deleted — write the
  // legal word. Keep every restricted import in THIS object: in flat config a later object that sets
  // the same rule REPLACES these options (no merge).
  {
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            { name: "@/lib/terminology", message: NO_VOCABULARY_LAYER },
            { name: "@/components/terminology-heading", message: NO_VOCABULARY_LAYER },
            { name: "@/components/terminology-text", message: NO_VOCABULARY_LAYER },
            { name: "@b2mash/shared/terminology-map", message: NO_VOCABULARY_LAYER },
            // 611E deleted packages/shared/src/terminology-map.ts; the barrel names stay banned so
            // the map cannot come back.
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
