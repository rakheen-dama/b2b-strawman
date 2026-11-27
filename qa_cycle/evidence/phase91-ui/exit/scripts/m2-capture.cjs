// 670A M2 capture runner (copy of m1-capture.cjs, targets from m2-targets.cjs): one group per invocation.
// Groups (m2-targets.cjs): fees, feenote, trust, sites, sec8, sec9. Interactions only click tabs or
// scroll (see m2-targets.cjs).
// Per persona: one login through /mock-login; one browser context per variant (viewport + stored
// kazi-theme [+ sidebar cookie]); each target loaded with waitUntil "load" + settle; measured
// (lib MEASURE); viewport tile → /tmp/670A-exit-raw/<group>/ (never committed); contact sheets →
// ../<group-dir>/<sheet>.png; records → ../measurements.json, console → ../console.json (this group's
// records replaced). Read-only: no form is saved, nothing is created (UI data: m2-data.cjs).
// Usage: node m2-capture.cjs <group>
const L = require("./lib.cjs");
const { GROUPS } = require("./m2-targets.cjs");

const g = process.argv[2];
if (!GROUPS[g]) { console.error(`unknown group ${g}; one of ${Object.keys(GROUPS).join(", ")}`); process.exit(2); }
const { targets, personas, meta } = GROUPS[g];
const ps = personas || [...new Set(targets.flatMap((t) => Object.keys(t.personas)))];
L.runGroup(g, targets, { personas: ps, meta: meta || {} })
  .then(({ recs }) => { console.log("DONE", g, recs.length); })
  .catch((e) => { console.error("CAPTURE FAIL", g, e); process.exit(1); });
