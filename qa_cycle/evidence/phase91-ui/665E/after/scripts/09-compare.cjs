// 665E OBS-AFTER step 9 (NEW): the merge-gate comparisons, offline (reads JSON only, no browser):
//   - invariants per persona × surface × variant (main#main-content = 1, <main> = 1, h1 in main, doc
//     h-overflow), from $RAW/persona-*.json;
//   - gating parity vs ../../before/measurements.json: selectedTab / tabs / rail / notice / final url /
//     HTTP status for every persona × surface × variant (must be identical);
//   - full-probe parity at 1440 light: testids, button names, control ids/labels lost or added;
//   - K-117: per persona × viewport × theme, every Templates category table's header left x; spread
//     per header across tables (PASS if <= 1 px); category h2 labels + order; entity badges;
//     Word-badge fit in the Format column (maxContentW vs innerW);
//   - B1 / B2 table scrollers before → after;
//   - SEL-COMPAT diff vs ../../before/sel-compat.json (every key, both personas, + custom editor).
// Writes ../gates.json (single line) and prints a summary.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const rd = (f) => JSON.parse(fs.readFileSync(f, "utf8"));
const BEFORE = path.resolve(__dirname, "../../before");
const BM = rd(path.join(BEFORE, "measurements.json"));
const BS = rd(path.join(BEFORE, "sel-compat.json"));
const A = { alice: rd(path.join(L.RAW, "persona-alice.json")), carol: rd(path.join(L.RAW, "persona-carol.json")) };
const CE = rd(path.join(L.RAW, "custom-editor.json"));
const out = { at: new Date().toISOString(), invariants: { failures: [], editor: {} }, gating: { compared: 0, diffs: [] }, fullProbe: {}, k117: {}, b1: {}, b2: {}, selCompat: { compared: 0, diffs: [] } };

// ---------- invariants ----------
const EDITOR = ["tpl-edit-platform"];
for (const [who, R] of Object.entries(A))
  for (const [sk, vv] of Object.entries(R.variants))
    for (const [v, m] of Object.entries(vv)) {
      const bad = [];
      if (m.mainCount !== 1) bad.push(`main#main-content=${m.mainCount}`);
      if (m.mainElCount !== 1) bad.push(`<main>=${m.mainElCount}`);
      if (m.hOverflow !== 0) bad.push(`docHOverflow=${m.hOverflow}`);
      if (!EDITOR.includes(sk) && m.h1Count !== 1) bad.push(`h1=${m.h1Count}`);
      if (m.somethingWentWrong) bad.push("Something went wrong");
      if (bad.length) out.invariants.failures.push({ who, sk, v, bad });
      if (EDITOR.includes(sk)) (out.invariants.editor[`${who}|${sk}`] = out.invariants.editor[`${who}|${sk}`] || {})[v] = { h1Count: m.h1Count, h1: m.h1, beforeH1Count: BM.personas[who].variants[sk][v].h1Count };
    }
for (const r of CE.runs) out.invariants.editor[`${r.who}|tpl-edit-custom`] = { ...(out.invariants.editor[`${r.who}|tpl-edit-custom`] || {}), [r.viewport]: { h1Count: r.h1Count, pageH1: r.h1InMain.filter((x) => !r.h1InEditorBody.includes(x)), bodyH1: r.h1InEditorBody.length, hOverflow: r.hOverflow, mainCount: r.mainCount, saveInViewport: r.saveInViewport, beforeH1Count: ((BM.customEditor && BM.customEditor.runs) || []).filter((b) => b.who === r.who && b.viewport === r.viewport).map((b) => b.h1Count)[0] ?? null, beforeHOverflow: ((BM.customEditor && BM.customEditor.runs) || []).filter((b) => b.who === r.who && b.viewport === r.viewport).map((b) => b.hOverflow)[0] ?? null } };

// ---------- gating parity ----------
const GK = ["selectedTab", "tabs", "rail", "notice", "url", "status", "mainCount", "mainElCount"];
for (const [who, R] of Object.entries(A))
  for (const [sk, vv] of Object.entries(R.variants))
    for (const [v, m] of Object.entries(vv)) {
      const b = BM.personas[who].variants[sk] && BM.personas[who].variants[sk][v];
      if (!b) { out.gating.diffs.push({ who, sk, v, missingBefore: true }); continue; }
      out.gating.compared++;
      for (const k of GK) if (JSON.stringify(b[k]) !== JSON.stringify(m[k])) out.gating.diffs.push({ who, sk, v, k, before: b[k], after: m[k] });
      if ((b.h1 || []).join("|") !== (m.h1 || []).join("|")) out.gating.diffs.push({ who, sk, v, k: "h1 (text)", before: b.h1, after: m.h1, note: "h1 text" });
    }

// ---------- full probe (1440 light) parity ----------
for (const [who, R] of Object.entries(A)) {
  out.fullProbe[who] = {};
  for (const [sk, f] of Object.entries(R.surfaces)) {
    const b = BM.personas[who].surfaces[sk];
    if (!b) continue;
    const keys = (o) => Object.keys(o || {});
    const btn = (arr) => [...new Set((arr || []).map((x) => x.name))];
    const ctl = (arr) => [...new Set((arr || []).map((x) => `${x.tag}#${x.id || ""}[${x.label || x.placeholder || x.testid || ""}]`))];
    const diff = (bb, aa) => ({ lost: bb.filter((x) => !aa.includes(x)), added: aa.filter((x) => !bb.includes(x)) });
    out.fullProbe[who][sk] = {
      testids: diff(keys(b.testids), keys(f.testids)),
      testidCounts: Object.fromEntries([...new Set([...keys(b.testids), ...keys(f.testids)])].filter((k) => (b.testids || {})[k] !== (f.testids || {})[k]).map((k) => [k, `${(b.testids || {})[k] ?? 0} → ${(f.testids || {})[k] ?? 0}`])),
      buttons: diff(btn(b.buttons), btn(f.buttons)),
      controls: diff(ctl(b.controls), ctl(f.controls)),
      headings: { before: (b.headings || []).map((h) => `h${h.level}:${h.text}`), after: (f.headings || []).map((h) => `h${h.level}:${h.text}`) },
      chips: { before: (b.chips || []).map((c) => `${c.text}×${c.n}`), after: (f.chips || []).map((c) => `${c.text}×${c.n}`) },
      title: b.title === f.title ? "same" : `${b.title} → ${f.title}`,
      tabBar: JSON.stringify(b.tabBar) === JSON.stringify(f.tabBar) ? "same" : { before: b.tabBar, after: f.tabBar },
    };
  }
}

// ---------- K-117 ----------
for (const [who, R] of Object.entries(A)) {
  const s = R.surfaces.templates;
  out.k117[who] = { categoryH2: s.headings.filter((h) => h.level === 2).map((h) => h.text), sortedByLabel: null, entityChips: s.chips.filter((c) => c.variant === "neutral").map((c) => `${c.text}×${c.n}`), rawEnumChips: s.chips.filter((c) => /^[A-Z_]{4,}$/.test(c.text)).map((c) => c.text), byVariant: {} };
  const cats = out.k117[who].categoryH2.filter((x) => x !== "Branding");
  out.k117[who].sortedByLabel = JSON.stringify(cats) === JSON.stringify([...cats].sort((a, b) => a.localeCompare(b)));
  for (const [v, m] of Object.entries(R.variants.templates)) {
    const tables = m.tables.map((t) => ({ section: t.section, tableWidth: t.tableWidth, scroller: t.scroller ? `${t.scroller.clientWidth}/${t.scroller.scrollWidth}` : null, x: Object.fromEntries(t.cols.map((c) => [c.h, c.x])), w: Object.fromEntries(t.cols.map((c) => [c.h, c.w])), formatFit: (() => { const c = t.cols.find((c) => c.h === "FORMAT"); return c ? { innerW: c.innerW, maxContentW: c.maxContentW, fits: c.maxContentW <= c.innerW + 0.5 } : null; })() }));
    const headers = [...new Set(tables.flatMap((t) => Object.keys(t.x)))];
    const spread = Object.fromEntries(headers.map((h) => { const xs = tables.map((t) => t.x[h]).filter((x) => x != null); return [h, { min: Math.min(...xs), max: Math.max(...xs), spread: Math.max(...xs) - Math.min(...xs) }]; }));
    const fail = Object.entries(spread).filter(([h, s]) => h !== "NAME" && s.spread > 1).map(([h, s]) => `${h} ${s.min}..${s.max} (${s.spread}px)`);
    out.k117[who].byVariant[v] = { pass: fail.length === 0, fail, spread, outliers: tables.filter((t) => headers.some((h) => h !== "NAME" && t.x[h] != null && t.x[h] !== spread[h].min && t.x[h] - spread[h].min > 1)).map((t) => t.section), tables };
  }
}

// ---------- B1 / B2 ----------
for (const who of ["alice", "carol"])
  for (const v of ["1024-light", "1024-dark", "1440-light", "1440-dark"]) {
    const sc = (m) => (m ? m.tables.map((t) => ({ section: t.section || null, table: t.tableWidth, client: t.scroller && t.scroller.clientWidth, scroll: t.scroller && t.scroller.scrollWidth, overflowPx: t.scroller && t.scroller.overflowPx, cut: t.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}${c.hidden ? "(hidden)" : ""}`).join(", ") })) : null);
    (out.b1[who] = out.b1[who] || {})[v] = { before: sc(BM.personas[who].variants.templates[v]), after: sc(A[who].variants.templates[v]) };
    if (who === "alice") out.b2[v] = { before: sc(BM.personas.alice.variants["email-delivery-log"][v]), after: sc(A.alice.variants["email-delivery-log"][v]), beforeRows: BM.personas.alice.variants["email-delivery-log"][v].tables.map((t) => t.bodyRows), afterRows: A.alice.variants["email-delivery-log"][v].tables.map((t) => t.bodyRows) };
  }

// ---------- SEL-COMPAT ----------
const ACC_RENAMED = /Document Acceptance/;
for (const who of ["alice", "carol"])
  for (const [sk, kv] of Object.entries(A[who].selCompat)) {
    const b = BS.personas[who][sk] || {};
    for (const [k, val] of Object.entries(kv)) {
      if (!(k in b)) { out.selCompat.diffs.push({ who, sk, k, before: "(new key)", after: val }); continue; }
      out.selCompat.compared++;
      if (JSON.stringify(b[k]) !== JSON.stringify(val)) out.selCompat.diffs.push({ who, sk, k, before: b[k], after: val, k124: ACC_RENAMED.test(k) });
    }
  }
const bce = BS.customEditor || [];
for (const r of CE.runs) {
  const b = bce.find((x) => x.who === r.who && x.viewport === r.viewport);
  if (!b) continue;
  for (const [k, val] of Object.entries(r.selCompat)) {
    const bk = Object.keys(b).find((x) => x === k || (x.startsWith("heading h1 '") && k.startsWith("heading h1 '")));
    if (!bk) continue;
    out.selCompat.compared++;
    if (JSON.stringify(b[bk]) !== JSON.stringify(val)) out.selCompat.diffs.push({ who: r.who, sk: `tpl-edit-custom ${r.viewport}`, k, before: b[bk], after: val, note: bk !== k ? `before key: ${bk}` : undefined });
  }
}

fs.writeFileSync(path.join(L.OUT, "gates.json"), JSON.stringify(out) + "\n");
console.log("invariant failures", JSON.stringify(out.invariants.failures));
console.log("editor h1", JSON.stringify(out.invariants.editor));
console.log("gating compared", out.gating.compared, "diffs", JSON.stringify(out.gating.diffs));
for (const who of ["alice", "carol"]) for (const [sk, d] of Object.entries(out.fullProbe[who])) { const x = { t: d.testids, tc: d.testidCounts, b: d.buttons, c: d.controls, title: d.title, tab: d.tabBar }; if (d.testids.lost.length + d.testids.added.length + d.buttons.lost.length + d.buttons.added.length + d.controls.lost.length + d.controls.added.length + Object.keys(d.testidCounts).length) console.log("full", who, sk, JSON.stringify(x)); }
for (const who of ["alice", "carol"]) { const k = out.k117[who]; console.log("k117", who, JSON.stringify({ h2: k.categoryH2, sorted: k.sortedByLabel, chips: k.entityChips, raw: k.rawEnumChips })); for (const [v, r] of Object.entries(k.byVariant)) console.log("  ", v, r.pass ? "PASS" : "FAIL", JSON.stringify(r.fail), "outliers", JSON.stringify(r.outliers), "formatFit", JSON.stringify(r.tables.map((t) => `${t.section}:${t.formatFit && t.formatFit.innerW}/${t.formatFit && t.formatFit.maxContentW}${t.formatFit && t.formatFit.fits ? "" : "!"}`))); }
console.log("b2", JSON.stringify(out.b2));
console.log("selCompat compared", out.selCompat.compared, "diffs", JSON.stringify(out.selCompat.diffs));
