// 665C OBS-after step 8 (after-only, offline, no browser): before vs after from the committed JSON.
// Reads ../../before/{measurements,sel-compat,saves}.json and ../{measurements,sel-compat,saves}.json
// and writes ../compare.json (single line): per persona × surface × variant the gate fields (tab bar,
// selected tab, notice, url), h1 text/count, #main-content/main counts, document h-overflow, table
// scroller widths; per persona × surface at 1440 light the heading list and the visible-string sets
// (buttons, links, chips, empty states, control labels / placeholders, status lines, table headers)
// as before-only / after-only diffs; every SEL-COMPAT key before vs after; the legacy routes.
const path = require("path");
const fs = require("fs");
const B = (f) => JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../before", f), "utf8"));
const A = (f) => JSON.parse(fs.readFileSync(path.resolve(__dirname, "..", f), "utf8"));
const mb = B("measurements.json"), ma = A("measurements.json");
const sb = B("sel-compat.json"), sa = A("sel-compat.json");
const out = { at: new Date().toISOString(), gate: { diffs: [], compared: 0 }, h1: { diffs: [], compared: 0, zero: [], multi: [] }, overflow: { after: {}, before: {} }, tables: {}, headings: {}, strings: {}, selCompat: { missingAfter: [], changed: [], compared: 0 }, legacy: { diffs: [], compared: 0 } };
const J = JSON.stringify;
for (const [k, pa] of Object.entries(ma.personas)) {
  const pb = mb.personas[k];
  if (!pb) continue;
  for (const [sk, va] of Object.entries(pa.variants)) {
    for (const [v, a] of Object.entries(va)) {
      const b = pb.variants[sk] && pb.variants[sk][v];
      const id = `${k}|${sk}|${v}`;
      (out.overflow.after[sk] = out.overflow.after[sk] || {})[`${k}|${v}`] = a.hOverflow;
      if (b) (out.overflow.before[sk] = out.overflow.before[sk] || {})[`${k}|${v}`] = b.hOverflow;
      if (a.h1Count !== 1) out.h1.zero.push(...(a.h1Count === 0 ? [id] : []));
      if (a.h1Count > 1) out.h1.multi.push(id);
      if (a.mainCount !== 1 || a.mainElCount !== 1) out.h1.diffs.push({ id, main: [a.mainCount, a.mainElCount] });
      if (v === "1024-light" || v === "1440-light") (out.tables[sk] = out.tables[sk] || {})[`${k}|${v}`] = { before: b ? b.tables.map((t) => t.scroller && `${t.scroller.clientWidth}/${t.scroller.scrollWidth}`) : null, after: a.tables.map((t) => t.scroller && `${t.scroller.clientWidth}/${t.scroller.scrollWidth}`), afterSlot: a.tables.map((t) => t.dataSlot) };
      if (!b) { out.gate.diffs.push({ id, missingBefore: true }); continue; }
      out.gate.compared++;
      out.h1.compared++;
      const g = (m) => J({ url: m.url, selectedTab: m.selectedTab, tabs: m.tabs, notice: m.notice, wrong: m.somethingWentWrong });
      if (g(a) !== g(b)) out.gate.diffs.push({ id, before: JSON.parse(g(b)), after: JSON.parse(g(a)) });
      if (J(a.h1) !== J(b.h1) || a.h1Count !== b.h1Count) out.h1.diffs.push({ id, before: b.h1, after: a.h1 });
    }
  }
  for (const [r, a] of Object.entries(pa.legacyRoutes || {})) {
    const b = pb.legacyRoutes && pb.legacyRoutes[r];
    out.legacy.compared++;
    if (J(a) !== J(b)) out.legacy.diffs.push({ persona: k, route: r, before: b, after: a });
  }
  for (const [sk, fa] of Object.entries(pa.surfaces || {})) {
    const fb = pb.surfaces && pb.surfaces[sk];
    if (!fb) continue;
    out.headings[`${k}|${sk}`] = { before: fb.headings.map((h) => `h${h.level} ${h.text}`), after: fa.headings.map((h) => `h${h.level} ${h.text}`) };
    const sets = (f) => ({
      buttons: f.buttons.filter((x) => x.visible).map((x) => x.name),
      buttonsSrOnly: f.buttons.filter((x) => !x.visible).map((x) => x.name),
      links: f.links.map((x) => x.name),
      chips: f.chips.map((x) => x.text),
      empty: f.emptyStates.map((x) => x.text),
      controls: f.controls.map((x) => `${x.label || ""}|${x.placeholder || ""}`),
      status: f.statusAlerts,
      tableHeaders: f.tables.flatMap((t) => t.headers),
      headings: f.headings.map((h) => h.text),
    });
    const sB = sets(fb), sA = sets(fa);
    const d = {};
    for (const key of Object.keys(sB)) {
      const bo = [...new Set(sB[key])].filter((x) => !sA[key].includes(x));
      const ao = [...new Set(sA[key])].filter((x) => !sB[key].includes(x));
      if (bo.length || ao.length) d[key] = { beforeOnly: bo, afterOnly: ao };
    }
    if (Object.keys(d).length) out.strings[`${k}|${sk}`] = d;
  }
}
for (const [k, pb] of Object.entries(sb.personas)) {
  const pa = sa.personas[k] || { surfaces: {} };
  for (const [sk, keys] of Object.entries(pb.surfaces || {})) {
    for (const [key, vb] of Object.entries(keys)) {
      out.selCompat.compared++;
      const va = pa.surfaces[sk] ? pa.surfaces[sk][key] : undefined;
      if (va === undefined) out.selCompat.missingAfter.push(`${k}|${sk}|${key}`);
      else if (J(va) !== J(vb)) out.selCompat.changed.push({ id: `${k}|${sk}|${key}`, before: vb, after: va });
    }
  }
}
fs.writeFileSync(path.resolve(__dirname, "..", "compare.json"), JSON.stringify(out) + "\n");
console.log("gate", out.gate.compared, "diffs", J(out.gate.diffs));
console.log("h1", out.h1.compared, "diffs", J(out.h1.diffs), "zero", J(out.h1.zero), "multi", J(out.h1.multi));
console.log("legacy", out.legacy.compared, "diffs", J(out.legacy.diffs));
console.log("selCompat", out.selCompat.compared, "missing", J(out.selCompat.missingAfter), "changed", J(out.selCompat.changed));
const ovA = Object.entries(out.overflow.after).flatMap(([sk, m]) => Object.entries(m).filter(([, v]) => v).map(([id, v]) => `${sk}|${id}=${v}`));
console.log("overflow nonzero after", J(ovA));
console.log("overflow before nonzero", J(Object.entries(out.overflow.before).flatMap(([sk, m]) => Object.entries(m).filter(([, v]) => v).map(([id, v]) => `${sk}|${id}=${v}`))));
