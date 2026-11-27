// 666B OBS-AFTER-FIX step 22 (new, read-only): a corrected line count for the line-items cells.
// 21-afterfix-lines.cjs counted Range client rects of the whole cell, which include the Description's
// <span class="flex"> element box (its top sits a few px above the first text line box), so it reports
// one line too many on that cell. Here: only TEXT-node rects (tops within 3 px merge), plus height ÷
// line-height as a cross-check. Same 4 targets × 1024/1440 × light/dark. Also looks for any fee note
// whose lines span >1 line type (the only case with section-header rows) and, if found, measures the
// section-header row against the header row at 1024 + 1440 light / dark.
// Usage: PHASE=after-fix node 22-line-counts.cjs → $RAW/line-counts.json (+ section-header crops).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const DRAFT_ID = "de6ac48f-bcfe-4c5e-b122-a8d012f152ec";
const out = { at: new Date().toISOString(), counts: {}, sectionHeaderCandidates: [], sectionHeaders: {} };
const sink = []; const lab = { label: "" }; const tiles = [];
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };

const COUNT = () => {
  const h = [...document.querySelectorAll("#main-content h2")].find((x) => window.__t(x) === "Line Items");
  const tb = h ? h.closest("div.space-y-4").querySelector("table") : null;
  if (!tb) return null;
  const textLines = (c) => {
    const tw = document.createTreeWalker(c, NodeFilter.SHOW_TEXT); const tops = [];
    for (let n = tw.nextNode(); n; n = tw.nextNode()) { if (!n.textContent.trim()) continue; const rg = document.createRange(); rg.selectNodeContents(n); for (const r of rg.getClientRects()) if (r.width > 0 && !tops.some((y) => Math.abs(y - r.top) <= 3)) tops.push(r.top); }
    return tops.length;
  };
  const heads = [...tb.querySelectorAll("thead th")].filter(window.__v).map(window.__t);
  const hr = tb.querySelector("thead tr").getBoundingClientRect();
  const th0 = tb.querySelector("thead th"); const g = document.createRange(); g.selectNodeContents(th0); const t0 = [...g.getClientRects()][0];
  const rows = [...tb.querySelectorAll("tbody tr")].filter(window.__v);
  return {
    headers: heads,
    headerLines: Object.fromEntries([...tb.querySelectorAll("thead th")].filter(window.__v).map((th, i) => [heads[i], textLines(th)])),
    rows: rows.filter((r) => !r.dataset.testid).map((r) => Object.fromEntries([...r.children].filter(window.__v).map((c, i) => { const cs = getComputedStyle(c); const inner = c.getBoundingClientRect().height - parseFloat(cs.paddingTop) - parseFloat(cs.paddingBottom); return [heads[i] || `c${i}`, { textLines: textLines(c), byHeight: Math.round(inner / (parseFloat(cs.lineHeight) || 20)), w: Math.round(c.getBoundingClientRect().width), text: window.__t(c).slice(0, 60) }]; }))),
    sectionHeaders: rows.filter((r) => (r.dataset.testid || "").startsWith("section-header-")).map((r) => { const c = r.children[0]; const b = c.getBoundingClientRect(); const rg = document.createRange(); rg.selectNodeContents(c); const tr = [...rg.getClientRects()][0]; return { testid: r.dataset.testid, text: window.__t(c), colSpan: c.colSpan, headerCols: heads.length, leftDelta: Math.round(b.left - hr.left), rightDelta: Math.round(b.right - hr.right), textLeft: tr && Math.round(tr.left), headerTextLeft: t0 && Math.round(t0.left), textAligned: tr && t0 ? Math.abs(tr.left - t0.left) <= 1 : null }; }),
  };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  for (const i of L.list((await L.apiGet(t, "/api/invoices")).json)) {
    const d = (await L.apiGet(t, `/api/invoices/${i.id}`)).json || {};
    const types = [...new Set((d.lines || []).map((l) => l.lineType))];
    if (types.length > 1) out.sectionHeaderCandidates.push({ id: i.id, number: i.invoiceNumber, status: i.status, types });
  }
  const sh = out.sectionHeaderCandidates.find((c) => c.status === "DRAFT") || out.sectionHeaderCandidates[0];
  const T = [["draft", DRAFT_ID], ["approved", ids.approved], ["partial", ids.partial], ["inv0009", ids["sent-overdue"]]];
  if (sh) T.push(["section", sh.id]);
  for (const [w, hgt] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: hgt, theme, extra: L.EXTRA });
      const p = await ctx.newPage(); L.watch(p, sink, lab);
      const v = `${w}-${theme}`;
      for (const [k, id] of T) {
        lab.label = `count|${k}|${v}`;
        await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" }); await settle(p);
        await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2);
        (out.counts[k] = out.counts[k] || {})[v] = await p.evaluate(COUNT);
        if (k === "section") { const sec = p.locator("#main-content h2", { hasText: "Line Items" }).locator("xpath=ancestor::div[contains(@class,'space-y-4')][1]"); const f = path.join(L.RAW, `section__${v}.png`); await sec.screenshot({ path: f }); tiles.push([`section__${v}`, f, `${sh.number || "Draft"} (${sh.status}, ${sh.types.join("+")}) ${w} ${theme} — section-header rows`]); }
      }
      await ctx.close();
    }
  await browser.close();
  out.console = sink; out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "line-counts.json"), JSON.stringify(out));
  console.log("candidates", JSON.stringify(out.sectionHeaderCandidates));
  for (const [k, vv] of Object.entries(out.counts)) for (const [v, m] of Object.entries(vv)) console.log(k, v, "hdrLines", JSON.stringify(m.headerLines), "rows", m.rows.map((r) => `D${r.DESCRIPTION.textLines}/${r.DESCRIPTION.byHeight} T${r.TAX ? `${r.TAX.textLines}/${r.TAX.byHeight}` : "-"} M${r.MATTER ? r.MATTER.textLines : "-"}`).join(" "), "sec", JSON.stringify(m.sectionHeaders.map((s) => [s.text, s.colSpan, s.headerCols, s.leftDelta, s.rightDelta, s.textAligned])));
  console.log("console", sink.length, JSON.stringify(sink.slice(0, 3)));
})();
