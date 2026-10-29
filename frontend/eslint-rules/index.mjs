import noAschildAdjacency from "./no-aschild-adjacency.mjs";
// Phase 88 628E: the Playwright rule lives with the e2e suite it polices.
import noIsVisibleCatch from "../e2e/eslint/no-isvisible-catch.mjs";

const plugin = {
  meta: { name: "kazi-frontend-local", version: "1.1.0" },
  rules: {
    "no-aschild-adjacency": noAschildAdjacency,
    "no-isvisible-catch": noIsVisibleCatch,
  },
};

export default plugin;
