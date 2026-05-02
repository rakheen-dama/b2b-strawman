import noAschildAdjacency from "./no-aschild-adjacency.mjs";

const plugin = {
  meta: { name: "kazi-frontend-local", version: "1.0.0" },
  rules: {
    "no-aschild-adjacency": noAschildAdjacency,
  },
};

export default plugin;
