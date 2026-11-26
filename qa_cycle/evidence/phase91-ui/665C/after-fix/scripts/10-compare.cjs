// 665C OBS after-fix step 10 (new, offline, no app traffic): compares ../../after/after-checks.json with
// ../after-checks.json (07 re-run unchanged with PHASE=after-fix) over the UNION of surface × variant
// keys: h1 texts, h2 texts, settings-section / nested-frame / bordered-in-section / white-box counts,
// low-contrast counts, docHOverflowPx, table scroller widths. Writes ../compare.json (single line).
const fs = require("fs");
const path = require("path");
const A = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../after/after-checks.json"), "utf8"));
const F = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../after-checks.json"), "utf8"));
const keys = [...new Set([...Object.keys(A.surfaces), ...Object.keys(F.surfaces)])];
const rows = {};
const diffs = [];
for (const sk of keys) {
  const va = A.surfaces[sk] || {}, vf = F.surfaces[sk] || {};
  for (const v of [...new Set([...Object.keys(va), ...Object.keys(vf)])]) {
    const a = va[v], f = vf[v];
    if (!a || !f) { diffs.push({ sk, v, missing: !a ? "after" : "after-fix" }); continue; }
    const pick = (m) => ({ h1: m.h1, h2: m.h2, ss: m.settingsSections, nested: m.nestedFrames.length, bis: m.borderedInSection.length, white: m.whiteBoxesDark && m.whiteBoxesDark.length, low: m.lowContrast.below45, ov: m.hOverflow, tables: m.tables.map((t) => t.scroller && `${t.scroller.clientWidth}/${t.scroller.scrollWidth}`) });
    const pa = pick(a), pf = pick(f);
    const changed = Object.keys(pa).filter((k) => JSON.stringify(pa[k]) !== JSON.stringify(pf[k]));
    rows[`${sk}|${v}`] = { h1Same: JSON.stringify(pa.h1) === JSON.stringify(pf.h1), h1: pf.h1, ov: pf.ov, changed: Object.fromEntries(changed.map((k) => [k, { after: pa[k], afterFix: pf[k] }])) };
    if (changed.length) diffs.push({ sk, v, changed: rows[`${sk}|${v}`].changed });
  }
}
const tabs = ["templates", "naming", "custom-fields", "tags", "checklists", "request-templates", "request-settings", "take-on"];
const sweep = Object.fromEntries(tabs.flatMap((t) => ["1024-light", "1024-dark"].map((v) => { const r = rows[`${t}|${v}`]; return [`${t}|${v}`, r && { h1: r.h1, h1Count: r.h1.length, h1Same: r.h1Same, ov: r.ov }]; })));
const out = { at: new Date().toISOString(), surfacesCompared: Object.keys(rows).length, h1AllSame: Object.values(rows).every((r) => r.h1Same), maxOverflow: Math.max(...Object.values(rows).map((r) => r.ov)), sweep, diffs, console: { after: { errors: A.console.errors, hydration: A.console.hydration }, afterFix: { errors: F.console.errors, hydration: F.console.hydration, n: F.console.messages.length } } };
fs.writeFileSync(path.resolve(__dirname, "../compare.json"), JSON.stringify(out) + "\n");
console.log(JSON.stringify({ n: out.surfacesCompared, h1AllSame: out.h1AllSame, maxOverflow: out.maxOverflow, console: out.console }));
for (const d of diffs) console.log(JSON.stringify(d));
