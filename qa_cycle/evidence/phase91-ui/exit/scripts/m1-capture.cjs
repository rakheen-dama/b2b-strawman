// 670A M1 capture runner: one group per invocation (so a failure costs one group, not the run).
// Groups (m1-targets.cjs): shell, settings-firm, settings-matters, settings-documents, settings-fees,
// settings-connections, settings-sub, platform-admin, marketing.
// Per persona: one login through /mock-login; one browser context per variant (viewport + stored
// kazi-theme [+ sidebar cookie]); each target loaded with waitUntil "load" + settle; measured
// (lib MEASURE); viewport tile → /tmp/670A-exit-raw/<group>/ (never committed); contact sheets →
// ../<group-dir>/<sheet>.png; records → ../measurements.json, console → ../console.json (this group's
// records replaced). Read-only: no form is saved, nothing is created.
// Usage: node m1-capture.cjs <group>
const L = require("./lib.cjs");
const { GROUPS } = require("./m1-targets.cjs");

const g = process.argv[2];
if (!GROUPS[g]) { console.error(`unknown group ${g}; one of ${Object.keys(GROUPS).join(", ")}`); process.exit(2); }
const { targets, personas, meta } = GROUPS[g];
const ps = personas || [...new Set(targets.flatMap((t) => Object.keys(t.personas)))];
L.runGroup(g, targets, { personas: ps, meta: meta || {} })
  .then(({ recs }) => { console.log("DONE", g, recs.length); })
  .catch((e) => { console.error("CAPTURE FAIL", g, e); process.exit(1); });
