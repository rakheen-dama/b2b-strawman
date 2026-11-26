// 665E OBS AFTER-FIX step 2 (NEW file): offline compare + artefacts. Reads $RAW/templates.json (01)
// and the frozen OBS-after JSON (../../after/measurements.json, ../../after/sel-compat.json); no app
// page is opened (the browser renders only the contact sheets / annotated crops from local PNGs).
//   - K-117: per persona × variant, every category table's header left x + width; spread per header
//     across tables (PASS if <= 1 px), the Word row's table and FORMAT fit (maxContentW <= innerW);
//   - overflow: scroller clientWidth / scrollWidth per table, before (OBS-after) → after-fix;
//     1440 must fit (FAIL if any table overflows at 1440);
//   - invariants: main#main-content = 1, <main> = 1, h1 in main = ["Templates"], doc h-overflow 0;
//   - gating parity vs OBS-after (selectedTab, tabs, rail, notice, url, status, main counts, h1 text,
//     table header lists per section; full-probe button names at 1440 light; SEL-COMPAT at 1024 light);
//   - console;
//   - sheet-templates-{alice,carol}.png (cols 1024 light | 1024 dark | 1440 light | 1440 dark) and
//     crop-k117-align-{1024,1440}.png: the raw clip from 01 with 1 px guide lines drawn OFFLINE at the
//     measured header left x of the Engagement Letter (Word-row) table, extended over both tables,
//     so any table whose header edge is off the guide shows it. The raw clips are kept in $RAW.
// Writes ../measurements.json (single line) and prints a summary.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const rd = (f) => JSON.parse(fs.readFileSync(f, "utf8"));
const AFTER = path.resolve(__dirname, "../../after");
const AM = rd(path.join(AFTER, "measurements.json"));
const AS = rd(path.join(AFTER, "sel-compat.json"));
const T = rd(path.join(L.RAW, "templates.json"));
const V = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const res = { capturedAt: T.capturedAt, phase: "after-fix", fixHead: "092a21309", api: T.api, k117: {}, overflow: {}, invariants: { failures: [], checked: 0 }, gating: { compared: 0, diffs: [], perVariant: {} }, fullProbeButtons: {}, selCompat: { compared: 0, diffs: [] }, console: { errors: T.console.errors, warnings: T.console.warnings, hydration: T.console.hydration, messages: T.console.messages } };

for (const who of ["alice", "carol"]) {
  const P = T.personas[who];
  const A = AM.personas[who];
  res.k117[who] = {};
  res.overflow[who] = {};
  res.gating.perVariant[who] = {};
  for (const v of V) {
    const m = P.variants[v];
    const g = P.gate[v];
    // K-117
    const tables = m.tables.map((t) => ({ section: t.section, x: Object.fromEntries(t.cols.map((c) => [c.h, c.x])), w: Object.fromEntries(t.cols.map((c) => [c.h, c.w])), formatFit: (() => { const c = t.cols.find((c) => c.h === "FORMAT"); return c ? { innerW: c.innerW, maxContentW: c.maxContentW, fits: c.maxContentW <= c.innerW + 0.5 } : null; })() }));
    const headers = [...new Set(tables.flatMap((t) => Object.keys(t.x)))];
    const spread = Object.fromEntries(headers.map((h) => { const xs = tables.map((t) => t.x[h]).filter((x) => x != null); const ws = tables.map((t) => t.w[h]).filter((x) => x != null); return [h, { xMin: Math.min(...xs), xMax: Math.max(...xs), xSpread: Math.max(...xs) - Math.min(...xs), wMin: Math.min(...ws), wMax: Math.max(...ws), wSpread: Math.max(...ws) - Math.min(...ws) }]; }));
    const fail = Object.entries(spread).filter(([h, s]) => s.xSpread > 1 || s.wSpread > 1).map(([h, s]) => `${h} x ${s.xMin}..${s.xMax} w ${s.wMin}..${s.wMax}`);
    const beforeT = A.variants.templates[v].tables;
    const bSpread = {};
    for (const h of headers) { const xs = beforeT.map((t) => (t.cols.find((c) => c.h === h) || {}).x).filter((x) => x != null); bSpread[h] = xs.length ? Math.max(...xs) - Math.min(...xs) : null; }
    res.k117[who][v] = { pass: fail.length === 0 && tables.length === 7, tableCount: tables.length, fail, spread, beforeSpread: bSpread, wordTable: g.wordTable, wordRow: (g.tables.find((t) => t.word) || {}).word || null, tables };
    // overflow
    const sc = (arr) => arr.map((t) => ({ section: t.section, client: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, cut: t.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`).join(", ") }));
    const af = sc(m.tables);
    res.overflow[who][v] = { before: sc(beforeT), after: af, fits: af.every((t) => t.overflowPx === 0), mustFit: v.startsWith("1440") };
    // invariants
    res.invariants.checked++;
    const bad = [];
    if (m.mainCount !== 1) bad.push(`main#main-content=${m.mainCount}`);
    if (m.mainElCount !== 1) bad.push(`<main>=${m.mainElCount}`);
    if (JSON.stringify(m.h1) !== JSON.stringify(["Templates"])) bad.push(`h1=${JSON.stringify(m.h1)}`);
    if (m.hOverflow !== 0) bad.push(`docHOverflow=${m.hOverflow}`);
    if (m.somethingWentWrong) bad.push("Something went wrong");
    if (m.htmlTheme.split(/\s+/).includes("dark") !== v.endsWith("dark")) bad.push(`theme class ${m.htmlTheme}`);
    if (bad.length) res.invariants.failures.push({ who, v, bad });
    // gating parity vs OBS-after
    const b = A.variants.templates[v];
    for (const k of ["selectedTab", "tabs", "rail", "notice", "url", "status", "mainCount", "mainElCount", "h1"]) {
      res.gating.compared++;
      if (JSON.stringify(b[k]) !== JSON.stringify(m[k])) res.gating.diffs.push({ who, v, k, before: b[k], after: m[k] });
    }
    const hd = (arr) => arr.map((t) => `${t.section}:${t.cols.map((c) => c.h).join("|")}`);
    res.gating.compared++;
    if (JSON.stringify(hd(b.tables)) !== JSON.stringify(hd(m.tables))) res.gating.diffs.push({ who, v, k: "table headers", before: hd(b.tables), after: hd(m.tables) });
    res.gating.perVariant[who][v] = { uploadWord: g.uploadWord, newTemplateLink: g.newTemplateLink, saveBranding: g.saveBranding, brandingH2: g.brandingH2, actionsColumnTables: g.actionsColumnTables, rowButtons: g.rowButtons };
  }
  // full-probe buttons at 1440 light
  const btn = (arr) => [...new Set((arr || []).map((x) => x.name))];
  const bb = btn(A.surfaces.templates.buttons), ab = btn(P.surface1440light.buttons);
  res.fullProbeButtons[who] = { before: bb, after: ab, lost: bb.filter((x) => !ab.includes(x)), added: ab.filter((x) => !bb.includes(x)), headingsSame: JSON.stringify(A.surfaces.templates.headings) === JSON.stringify(P.surface1440light.headings), chipsSame: JSON.stringify(A.surfaces.templates.chips) === JSON.stringify(P.surface1440light.chips) };
  // SEL-COMPAT 1024 light
  const bs = AS.personas[who].templates;
  for (const [k, val] of Object.entries(P.selCompat1024light)) {
    res.selCompat.compared++;
    if (JSON.stringify(bs[k]) !== JSON.stringify(val)) res.selCompat.diffs.push({ who, k, before: bs[k], after: val });
  }
}
res.verdict = {
  k117: Object.values(res.k117).every((p) => Object.values(p).every((r) => r.pass)) ? "PASS" : "FAIL",
  overflow1440: Object.values(res.overflow).every((p) => V.filter((v) => v.startsWith("1440")).every((v) => p[v].fits)) ? "PASS" : "FAIL",
  invariants: res.invariants.failures.length === 0 ? "PASS" : "FAIL",
  gating: res.gating.diffs.length === 0 && res.selCompat.diffs.length === 0 && Object.values(res.fullProbeButtons).every((x) => !x.lost.length && !x.added.length) ? "PASS" : "FAIL",
  console: res.console.errors + res.console.hydration === 0 ? "PASS" : "FAIL",
};

(async () => {
  const browser = await L.chromium.launch();
  // annotated K-117 crops
  for (const w of ["1024", "1440"]) {
    const c = T.clips[w];
    const r = res.k117.alice[`${w}-light`];
    const el = r.tables.find((t) => t.section === "Engagement Letter");
    const xs = Object.entries(el.x).map(([h, x]) => [h, x - c.clip.x]);
    const p = await browser.newPage({ viewport: { width: 800, height: 600 }, deviceScaleFactor: 1 });
    await p.setContent("<html><body style='margin:0'><canvas id=c></canvas></body></html>");
    const data = await p.evaluate(async ({ src, xs }) => {
      const img = new Image();
      await new Promise((ok) => { img.onload = ok; img.src = src; });
      const cv = document.getElementById("c");
      cv.width = img.width; cv.height = img.height;
      const x = cv.getContext("2d");
      x.drawImage(img, 0, 0);
      x.fillStyle = "rgba(230,0,60,0.85)";
      x.font = "bold 10px sans-serif";
      for (const [h, px] of xs) { x.fillRect(Math.round(px), 0, 1, img.height); x.fillText(h, Math.round(px) + 2, 10); }
      return cv.toDataURL("image/png");
    }, { src: "data:image/png;base64," + fs.readFileSync(c.file).toString("base64"), xs });
    fs.writeFileSync(path.join(L.OUT, `crop-k117-align-${w}.png`), Buffer.from(data.split(",")[1], "base64"));
    await p.close();
  }
  for (const who of ["alice", "carol"]) {
    const files = V.map((v) => { const t = T.tiles.find((x) => x[0] === `s__templates-${who}-${v}`); return [t[2], t[1]]; });
    await L.sheet(browser, path.join(L.OUT, `sheet-templates-${who}.png`), `665E after-fix @092a21309 — Settings › Documents › Templates — ${who} (cols 1024 light | 1024 dark | 1440 light | 1440 dark)`, files, 4, 480);
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(res) + "\n");
  console.log("verdict", JSON.stringify(res.verdict));
  for (const who of ["alice", "carol"]) for (const v of V) { const r = res.k117[who][v]; console.log("k117", who, v, r.pass ? "PASS" : "FAIL", r.tableCount, JSON.stringify(Object.fromEntries(Object.entries(r.spread).map(([h, s]) => [h, `${s.xMin}/${s.xSpread} w${s.wMin}/${s.wSpread}`]))), "before", JSON.stringify(r.beforeSpread), "word", r.wordTable, JSON.stringify(r.wordRow), "fit", [...new Set(r.tables.map((t) => `${t.formatFit.innerW}/${t.formatFit.maxContentW}${t.formatFit.fits ? "" : "!"}`))].join(",")); }
  for (const who of ["alice", "carol"]) for (const v of V) { const o = res.overflow[who][v]; console.log("ovf", who, v, "before", [...new Set(o.before.map((t) => `${t.client}/${t.scroll} [${t.cut}]`))].join(" ; "), "→ after", [...new Set(o.after.map((t) => `${t.client}/${t.scroll} [${t.cut}]`))].join(" ; ")); }
  console.log("invariants", res.invariants.checked, JSON.stringify(res.invariants.failures));
  console.log("gating", res.gating.compared, JSON.stringify(res.gating.diffs), JSON.stringify(res.gating.perVariant));
  console.log("buttons", JSON.stringify(Object.fromEntries(Object.entries(res.fullProbeButtons).map(([k, x]) => [k, { lost: x.lost, added: x.added, h: x.headingsSame, c: x.chipsSame }]))));
  console.log("selCompat", res.selCompat.compared, JSON.stringify(res.selCompat.diffs));
  console.log("console", res.console.errors, res.console.warnings, res.console.hydration);
})();
