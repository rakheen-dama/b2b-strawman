import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const NO_VOCABULARY_LAYER =
  "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),
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
