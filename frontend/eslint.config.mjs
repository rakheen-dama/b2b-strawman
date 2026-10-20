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
