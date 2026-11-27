// 667A OBS-after-fix regression sweep: diff after/ (a5259de0b) vs after-fix/ (c8cbb4d53) JSON — every field the
// before→after comparison passed on (pager/links/buttons/headings/shell/interactions/sel-compat/console), table
// widths per page, and checks.json (sticky visibility, Date/Type lines, contrast, empty states, axe).
// Usage: node 08-diff-after.cjs   (prints; writes nothing)
const fs = require("fs"), path = require("path");
const D = path.resolve(__dirname, "../..");
const J = (p, f) => JSON.parse(fs.readFileSync(path.join(D, p, f), "utf8"));
const A = { m: J("after", "measurements.json"), s: J("after", "sel-compat.json"), c: J("after", "console.json"), k: J("after", "checks.json") };
const F = { m: J("after-fix", "measurements.json"), s: J("after-fix", "sel-compat.json"), c: J("after-fix", "console.json"), k: J("after-fix", "checks.json") };
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const strip = (o) => JSON.stringify(o, (k, v) => (k === "file" || k === "at" ? undefined : v));
console.log("== sel-compat after → after-fix");
let n = 0; for (const [name, per] of Object.entries(A.s.counts)) for (const [k, v] of Object.entries(per)) if (!eq((F.s.counts[name] || {})[k], v)) { n++; console.log(`  ${name} @ ${k}: ${v} -> ${(F.s.counts[name] || {})[k]}`); }
console.log("  diffs", n, "recordTxMenu", eq(A.s.recordTxMenu, F.s.recordTxMenu) ? "same" : "DIFF");
console.log("== console", JSON.stringify(A.c.totals), "->", JSON.stringify(F.c.totals));
console.log("== page fields (diffs only)");
const fields = ["pager", "found", "statusLinks", "typeLinks", "nonZeroLink", "ledgerTotals", "buttons", "headings", "genStatementTitle", "inputs", "h1Main", "h1Page", "mainIdCount", "mainElCount", "docHOverflowPx", "title", "httpStatus"];
let pf = 0; for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) { const f = (F.m.pages[k] || {})[pv] || {}; for (const x of fields) if (!eq(a[x], f[x])) { pf++; console.log(`  ${k} ${pv} ${x}\n     A ${JSON.stringify(a[x])}\n     F ${JSON.stringify(f[x])}`); } }
console.log("  page-field diffs", pf, "of", Object.values(A.m.pages).reduce((s, v) => s + Object.keys(v).length, 0), "page×variant");
console.log("== tables (scroller client/scroll) after → after-fix, diffs only");
for (const [k, per] of Object.entries(A.m.pages)) for (const [pv, a] of Object.entries(per)) { const f = (F.m.pages[k] || {})[pv] || { tables: [] }; const g = (t) => t.map((x) => `${x.testid} ${x.scroller ? x.scroller.clientWidth + "/" + x.scroller.scrollWidth : "-"} rows ${x.rowCount} [${x.headers.map((h) => h.text + ":" + h.w).join(", ")}]`).join(" ; "); if (g(a.tables || []) !== g(f.tables || [])) console.log(`  ${k} ${pv}\n     A ${g(a.tables || [])}\n     F ${g(f.tables || [])}`); }
console.log("== interactions after → after-fix");
let ni = 0; for (const [k, v] of Object.entries(A.m.interactions)) if (strip(v) !== strip(F.m.interactions[k])) { ni++; console.log(`  ${k}\n     A ${strip(v)}\n     F ${strip(F.m.interactions[k])}`); }
console.log("  diffs", ni, "of", Object.keys(A.m.interactions).length, "; api same:", eq(A.m.api, F.m.api));
console.log("== checks.json pages (s0/sMax visibility, date/type, sticky) diffs");
for (const [k, per] of Object.entries(A.k.pages)) for (const [pv, a] of Object.entries(per)) { const f = (F.k.pages[k] || {})[pv]; for (const x of ["scroller", "heads", "rowCount", "s0", "sMax", "dateMaxLines", "dateWrapped", "typeMinW", "typeBadgeOutside", "typeMaxLines", "actionsSticky", "shell", "approveButtons"]) if (!eq(a[x], f && f[x])) console.log(`  ${k} ${pv} ${x}\n     A ${JSON.stringify(a[x])}\n     F ${JSON.stringify(f && f[x])}`); }
console.log("== checks.json contrast / tiles / empty / axe / k89 diffs");
for (const sec of ["contrast", "tiles", "empty", "axe", "k89"]) for (const [k, a] of Object.entries(A.k[sec])) if (!eq(a, F.k[sec][k])) console.log(`  ${sec} ${k}\n     A ${JSON.stringify(a).slice(0, 600)}\n     F ${JSON.stringify(F.k[sec][k]).slice(0, 600)}`);
console.log("  checks console", A.k.console.length, "->", F.k.console.length);
