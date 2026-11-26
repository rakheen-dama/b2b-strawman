// 663C K-26 final — acceptance verdicts (NEW, offline; no browser). 03-capture.cjs keeps 01's per-capture verdict unchanged
// (like-for-like), and that verdict fails ANY truncated middle crumb. The final acceptance allows middle truncation on the
// 4- and 5-crumb routes (point 3), so this script scores the five acceptance points from ../measurements.json (final build),
// ../../k26-recheck/measurements.json (c4d1f7675) and ../mobile.json + ../mobile-c4d1f7675.json. Output: ../acceptance.json.
const path = require("path");
const fs = require("fs");
const OUT = path.resolve(__dirname, "..");
const read = (f) => JSON.parse(fs.readFileSync(path.resolve(OUT, f), "utf8"));
const FINAL = read("measurements.json");
const BEFORE = read("../k26-recheck/measurements.json");
const MOB = read("mobile.json");
const MOB0 = read("mobile-c4d1f7675.json");
const THREE = ["fee-note", "trust-transactions", "matter", "client-detail"];

const res = {};
for (const [key, m] of Object.entries(FINAL.captures)) {
  const pk = THREE.find((p) => key.includes(`-${p}-`)) || (key.includes("template-edit") ? "template-edit" : "checklist-edit");
  const b = BEFORE.captures[key];
  const last = m.crumbs[m.crumbs.length - 1];
  const bLast = b.crumbs[b.crumbs.length - 1];
  const org = m.crumbs[0];
  const mids = m.crumbs.filter((c) => c.role === "middle");
  // Sub-pixel cut: Chromium draws an ellipsis even when scrollWidth == clientWidth (prototype-iter2.json).
  const cut = (c) => c.truncated || c.labelW + 0.05 < Math.min(c.naturalW, 160);
  const p = {};
  p.P1 = m.navH === 20 && !m.wraps && !m.crumbsOverflowNav && !m.crumbsUnderControl;
  if (THREE.includes(pk)) {
    p.P2 = mids.every((c) => !cut(c)) && last.labelW + 0.5 >= bLast.labelW;
  } else {
    p.P3 = last.labelW >= 20 && m.lastVisible && (pk !== "template-edit" || !cut(last)) && m.crumbs.every((c) => !c.truncated || !!c.title);
  }
  p.P4 = org.w <= 160 && !!org.title && mids.every((c) => c.labelW <= 160.5 && !!c.title);
  res[key] = {
    pass: Object.values(p).every(Boolean),
    points: p,
    before: { org: b.crumbs[0].w, middle: b.crumbs.slice(1, -1).map((c) => `${c.text}${c.truncated ? "…" : ""} ${c.w}`), last: `${bLast.text}${bLast.truncated ? "…" : ""} ${bLast.w} (label ${bLast.labelW})`, overflow: b.crumbsOverflowNav || b.crumbsUnderControl },
    lastCut: cut(last),
    final: { org: `${org.w}${org.truncated ? "…" : ""}`, middle: mids.map((c) => `${c.text}${c.truncated ? "…" : ""} ${c.w} (label ${c.labelW})`), last: `${last.text}${last.truncated ? "…" : ""} ${last.w} (label ${last.labelW})`, navW: m.navW, maxRight: Math.max(...m.crumbs.map((c) => c.right)), navRight: m.navRight, neighbourLeft: m.neighbourLeft },
  };
}
const P5 = {};
for (const [k, m] of Object.entries(MOB.captures)) {
  const m0 = MOB0.captures[k];
  P5[k] = { pass: m.pass && m.lastText === m0.lastText && m.lastLabelW === m0.lastLabelW && m.lastTruncated === m0.lastTruncated, final: m, c4d1f7675: { lastLabelW: m0.lastLabelW, lastTruncated: m0.lastTruncated, navRight: m0.navRight } };
}
const vals = Object.values(res);
// Diagnostic, not an acceptance point: captures whose last crumb shows an ellipsis (real or sub-pixel).
const summary = {
  captures: vals.length,
  pass: vals.filter((r) => r.pass).length,
  byPoint: Object.fromEntries(["P1", "P2", "P3", "P4"].map((pt) => [pt, `${vals.filter((r) => r.points[pt] === true).length}/${vals.filter((r) => pt in r.points).length}`])),
  lastCut: vals.filter((r) => r.lastCut).length,
  P5: `${Object.values(P5).filter((r) => r.pass).length}/${Object.keys(P5).length}`,
};
fs.writeFileSync(path.join(OUT, "acceptance.json"), JSON.stringify({ evaluatedAt: new Date().toISOString(), summary, captures: res, mobile: P5 }) + "\n");
console.log(JSON.stringify(summary));
for (const [k, r] of Object.entries(res)) console.log(r.pass ? "PASS" : "FAIL", k, JSON.stringify(r.points), r.lastCut ? "LAST-CUT" : "", "| before", r.before.org, r.before.middle.join(" · "), "/", r.before.last, "| final", r.final.org, r.final.middle.join(" · "), "/", r.final.last, "max", r.final.maxRight, "nav", r.final.navRight);
