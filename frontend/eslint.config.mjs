import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import localRules from "./eslint-rules/index.mjs";

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
]);

export default eslintConfig;
