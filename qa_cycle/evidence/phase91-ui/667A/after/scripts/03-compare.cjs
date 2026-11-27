// 667A OBS-after: diff before/ vs after/ JSON (sel-compat, k10, page shells, ledger totals, pager/links, interactions).
// Usage: node 03-compare.cjs [section]   (prints; writes nothing)
const fs = require("fs"), path = require("path");
const D = path.resolve(__dirname, "../..");
const J = (p, f) => JSON.parse(fs.readFileSync(path.join(D, p, f), "utf8"));
const B = { m: J("before", "measurements.json"), s: J("before", "sel-compat.json"), c: J("before", "console.json") };
const A = { m: J("after", "measurements.json"), s: J("after", "sel-compat.json"), c: J("after", "console.json") };
const sec = process.argv[2] || "all";
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
if (sec === "all" || sec === "sel") {
  console.log("== SEL-COMPAT diffs");
  for (const [name, per] of Object.entries(B.s.counts)) for (const [k, v] of Object.entries(per)) { const a = (A.s.counts[name] || {})[k]; if (!eq(a, v)) console.log(`  ${name} @ ${k}: ${JSON.stringify(v)} -> ${JSON.stringify(a)}`); }
  console.log("  recordTxMenu", eq(B.s.recordTxMenu, A.s.recordTxMenu) ? "same" : JSON.stringify([B.s.recordTxMenu, A.s.recordTxMenu]));
}
if (sec === "all" || sec === "console") console.log("== console", JSON.stringify(B.c.totals), "->", JSON.stringify(A.c.totals));
if (sec === "all" || sec === "shell") {
  console.log("== shell (h1Main / h1Page / mainId / mainEl / docHOverflow / title / http)");
  for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) { const b = (B.m.pages[k] || {})[pv] || {}; const f = (m) => [m.h1Main && m.h1Main.join("|"), m.h1Page, m.mainIdCount, m.mainElCount, m.docHOverflowPx, m.httpStatus].join(" "); if (f(a) !== f(b) || a.title !== b.title) console.log(`  ${k} ${pv}: ${f(b)} -> ${f(a)}${a.title !== b.title ? " title " + a.title : ""}`); }
  const bad = []; for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) if (a.mainIdCount !== 1 || a.mainElCount !== 1 || a.h1Main.length !== 1 || a.h1Page !== 1 || a.docHOverflowPx !== 0) bad.push(`${k} ${pv}`);
  console.log("  after shell violations:", bad.length ? bad : "none", "of", Object.values(A.m.pages).reduce((n, v) => n + Object.keys(v).length, 0));
}
if (sec === "all" || sec === "tables") {
  console.log("== tables (scroller client/scroll, headers w) before -> after");
  for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) { const b = (B.m.pages[k] || {})[pv] || { tables: [] };
    const f = (t) => t ? `${t.testid} ${t.scroller ? t.scroller.clientWidth + "/" + t.scroller.scrollWidth : "-"} rows ${t.rowCount} [${t.headers.map((h) => h.text + ":" + h.w).join(", ")}]` : "none";
    console.log(`  ${k} ${pv}\n     B ${(b.tables || []).map(f).join(" ; ")}\n     A ${a.tables.map(f).join(" ; ")}`); }
}
if (sec === "all" || sec === "k10") {
  console.log("== k10");
  for (const pv of Object.keys(A.m.k10)) for (const ph of [["B", B], ["A", A]]) { const x = ph[1].m.k10[pv]; if (!x) continue; console.log(`  ${ph[0]} ${pv}`); for (const r of x.rows || []) console.log(`     ${r.key} ref w ${r.refCell.w} lines ${r.refText.lines} widest ${r.refText.widestLine} date w ${r.dateCell.w} lines ${r.dateCell.lines} rowH ${r.rowH} table ${r.table.width} scroller ${r.scroller.clientWidth}/${r.scroller.scrollWidth} cells ${r.cells.map((c) => c.i + ":" + c.x + "+" + c.w + (c.rightBeyondScroller > 0 ? "!" + c.rightBeyondScroller : "")).join(" ")}`);
    if (x.spacedOnly) { const s = x.spacedOnly; const r = s.k10[0]; console.log(`     spacedOnly ref w ${r.refCell.w} lines ${r.refText.lines} rowH ${r.rowH} table ${s.table.width} scroller ${s.table.scroller && s.table.scroller.clientWidth}/${s.table.scroller && s.table.scroller.scrollWidth}`); } }
}
if (sec === "all" || sec === "links") {
  console.log("== pager / found / status+type+nonzero hrefs / ledgerTotals / buttons (diffs only)");
  for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) { const b = (B.m.pages[k] || {})[pv] || {};
    for (const f of ["pager", "found", "statusLinks", "typeLinks", "nonZeroLink", "ledgerTotals", "buttons", "headings", "genStatementTitle", "inputs"]) if (!eq(a[f], b[f])) console.log(`  ${k} ${pv} ${f}\n     B ${JSON.stringify(b[f])}\n     A ${JSON.stringify(a[f])}`); }
}
if (sec === "all" || sec === "inter") {
  console.log("== interactions diffs");
  for (const [k, v] of Object.entries(B.m.interactions)) { const a = A.m.interactions[k]; const strip = (o) => JSON.stringify(o, (kk, vv) => (kk === "file" ? undefined : vv)); if (strip(a) !== strip(v)) console.log(`  ${k}\n     B ${strip(v)}\n     A ${strip(a)}`); }
  console.log("  api same:", eq(B.m.api, A.m.api));
}
