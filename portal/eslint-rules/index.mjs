// Phase 88 628G (R-8): portal twin of frontend/eslint-rules/index.mjs for the Playwright rule.
// The rule file is a byte-identical copy of frontend/e2e/eslint/no-isvisible-catch.mjs (the portal
// does not import across apps — 628B R-1); __tests__/eslint-rules/no-isvisible-catch.test.ts fails
// when the two copies drift.
import noIsVisibleCatch from "../e2e/eslint/no-isvisible-catch.mjs";

const plugin = {
  meta: { name: "kazi-portal-local", version: "1.0.0" },
  rules: {
    "no-isvisible-catch": noIsVisibleCatch,
  },
};

export default plugin;
