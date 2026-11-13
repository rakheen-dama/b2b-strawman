// 643C OBS static matrix @ 8dbb9a408 (re-OBS): profitability + reports index at 390/768/1024/1440 x light/dark.
// Measures: h1/page-header/cost note, tables + column headers + aria-sort + default order, charts above
// tables, page overflow + offenders, table container scroll, long-name overlap, dark contrast, console.
const fs = require("fs"), path = require("path");
const { chromium, PROF, REPORTS, OUT, SLUG, session, open } = require("./common.cjs");
const WIDTHS = [390, 768, 1024, 1440], THEMES = ["light", "dark"];
const res = { prof: {}, reports: {}, console: {} };

const measureProf = (page) => page.evaluate(() => {
  const R = (r) => ({ l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) });
  const B = (e) => R(e.getBoundingClientRect());
  const T = (e) => { const rg = document.createRange(); rg.selectNodeContents(e); return R(rg.getBoundingClientRect()); };
  // colour → sRGB via canvas (handles oklch/lab), contrast per WCAG.
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgb = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "#000"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], d[3]]; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return +((x + 0.05) / (y + 0.05)).toFixed(2); };
  const bgOf = (el) => { for (let e = el; e; e = e.parentElement) { const c = getComputedStyle(e).backgroundColor; const v = rgb(c); if (v[3] > 200) return v; } return [255, 255, 255, 255]; };
  const main = document.querySelector("#main-content");
  const card = (title) => { const t = [...document.querySelectorAll('[data-slot="card-title"]')].find((e) => e.textContent.trim() === title); return t && t.closest('[data-slot="card"]'); };
  const h1s = [...main.querySelectorAll("h1")].map((e) => e.textContent.trim());
  const note = document.querySelector('[data-testid="profitability-cost-note"]');
  const ph = document.querySelector('[data-testid="page-header"]');
  const tableInfo = (title) => {
    const c = card(title); if (!c) return null; const t = c.querySelector("table"); if (!t) return { card: !!c, table: false };
    const sc = t.closest('[data-slot="table-container"]') || t.parentElement; const sb = B(sc);
    const heads = [...t.querySelectorAll("thead th")].map((th) => { const b = B(th); const btn = th.querySelector("button,a"); return { name: th.textContent.trim(), ariaSort: th.getAttribute("aria-sort"), ctl: btn ? btn.tagName : null, l: b.l, r: b.r, w: b.r - b.l, hiddenBehindScroll: b.r > sb.r + 1 }; });
    const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.children].map((td) => td.textContent.trim()));
    // long-name overlap: a cell's text box must stay inside its cell, and not hit the next cell's text.
    const overlaps = [];
    for (const tr of t.querySelectorAll("tbody tr")) {
      const tds = [...tr.children];
      tds.forEach((td, i) => {
        if (!td.textContent.trim()) return;
        const tb = T(td), cb = B(td);
        if (tb.r > cb.r + 1 || tb.l < cb.l - 1) overlaps.push({ cell: td.textContent.trim().slice(0, 40), text: tb, cell_: cb });
        const nx = tds[i + 1]; if (nx && nx.textContent.trim()) { const nb = T(nx); if (tb.r > nb.l && tb.t < nb.b && nb.t < tb.b) overlaps.push({ a: td.textContent.trim().slice(0, 30), b: nx.textContent.trim().slice(0, 30), px: tb.r - nb.l }); }
      });
    }
    const lines = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.children].slice(0, 2).map((td) => { const rg = document.createRange(); rg.selectNodeContents(td); return new Set([...rg.getClientRects()].map((r) => Math.round(r.top))).size; }));
    const chart = c.querySelector("svg.recharts-surface");
    return { tableBox: B(t), container: { scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth, overflowPx: sc.scrollWidth - sc.clientWidth, overflowX: getComputedStyle(sc).overflowX }, cardBox: B(c), heads, rowCount: rows.length, rows, overlaps, maxLinesFirst2Cols: Math.max(0, ...lines.flat()), chartTop: chart ? B(chart).t : null, chartAboveTable: chart ? B(chart).b <= B(t).t : null };
  };
  const util = tableInfo("Team Utilization"), matter = tableInfo("Matter Profitability"), client = tableInfo("Client Profitability");
  const bc = card("Fees Billed vs Collected"); const bsvg = bc && bc.querySelector("svg.recharts-surface");
  // contrast: margin values, utilization bars, header buttons
  const contrast = {};
  const mc = card("Matter Profitability");
  if (mc) {
    const signed = [...mc.querySelectorAll("tbody .text-success-foreground, tbody .text-danger-foreground")];
    contrast.marginValues = signed.slice(0, 4).map((e) => { const fg = rgb(getComputedStyle(e).color), bg = bgOf(e); return { text: e.textContent.trim(), cls: e.className.includes("success") ? "success" : "danger", fg: getComputedStyle(e).color, ratio: ratio(fg, bg) }; });
    contrast.signedCount = signed.length;
    const hb = mc.querySelector("thead th button"); if (hb) { const fg = rgb(getComputedStyle(hb).color); contrast.headerButton = { color: getComputedStyle(hb).color, ratio: ratio(fg, bgOf(hb)) }; }
    const cell = mc.querySelector("tbody td"); if (cell) contrast.matterCell = ratio(rgb(getComputedStyle(cell).color), bgOf(cell));
  }
  const uc = card("Team Utilization");
  if (uc) {
    const bar = uc.querySelector("tbody .bg-success, tbody .bg-warning, tbody .bg-muted-foreground");
    if (bar) { const track = bar.parentElement; const fill = rgb(getComputedStyle(bar).backgroundColor), tr = rgb(getComputedStyle(track).backgroundColor), cardBg = bgOf(track.parentElement); contrast.utilBar = { fill: getComputedStyle(bar).backgroundColor, track: getComputedStyle(track).backgroundColor, fillVsTrack: ratio(fill, tr), fillVsCard: ratio(fill, cardBg), widthPct: bar.style.width, box: B(bar) }; }
    const pct = uc.querySelector("tbody td:last-child span.text-muted-foreground"); if (pct) contrast.utilPctText = ratio(rgb(getComputedStyle(pct).color), bgOf(pct));
  }
  const cards = [...document.querySelectorAll('[data-slot="card-title"]')].map((e) => e.textContent.trim());
  // page overflow offenders
  const vw = document.documentElement.clientWidth;
  const offenders = [...main.querySelectorAll("*")].filter((e) => e.getBoundingClientRect().right > vw + 0.5 && !e.closest('[data-slot="table-container"]')).slice(0, 12).map((e) => [e.tagName, e.id || "", (e.closest('[data-slot="card"]')?.querySelector('[data-slot="card-title"]')?.textContent || "").trim(), Math.round(e.getBoundingClientRect().right - vw)]);
  // header row: date controls row wraps?
  const hdrRows = ["util-to", "prof-to", "cust-prof-to"].map((id) => { const e = document.getElementById(id); if (!e) return null; const c = e.closest('[data-slot="card"]'); return { id, inputRight: B(e).r, cardRight: B(c).r, insideCard: B(e).r <= B(c).r }; });
  return { htmlClass: document.documentElement.className, h1s, pageHeader: !!ph, pageHeaderContainsH1: ph ? !!ph.querySelector("h1") : null, pageHeaderDesc: ph ? ph.textContent.trim().replace(h1s[0] || "", "").trim() : null, costNote: note ? note.textContent.trim() : null, mainTables: main.querySelectorAll("table").length, cards, billingChart: !!bsvg, billingChartAboveTable: null, util, matter, client, contrast, overflow: { scrollWidth: document.documentElement.scrollWidth, clientWidth: vw, px: document.documentElement.scrollWidth - vw }, offenders, hdrRows };
});

const measureReports = (page) => page.evaluate(() => {
  const B = (e) => { const r = e.getBoundingClientRect(); return { l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) }; };
  const main = document.querySelector("#main-content");
  const ph = document.querySelector('[data-testid="page-header"]');
  const sections = [...main.querySelectorAll("section")].map((s) => {
    const h2 = s.querySelector("h2"); const links = [...s.querySelectorAll("a[href]")];
    const grid = s.querySelector(".grid");
    const tops = links.map((a) => B(a).t); const firstRow = tops.filter((t) => t === tops[0]).length;
    return { h2: h2 && h2.textContent.trim(), gridCols: grid ? getComputedStyle(grid).gridTemplateColumns.split(" ").length : null, firstRowCards: firstRow, cards: links.map((a) => ({ title: a.querySelector('[data-slot="card-title"]')?.textContent.trim(), href: a.getAttribute("href"), box: B(a) })) };
  });
  const vw = document.documentElement.clientWidth;
  const offenders = [...main.querySelectorAll("*")].filter((e) => e.getBoundingClientRect().right > vw + 0.5).slice(0, 10).map((e) => [e.tagName, e.className.toString().slice(0, 60), Math.round(e.getBoundingClientRect().right - vw)]);
  const t = ph && ph.querySelector("h1");
  return { htmlClass: document.documentElement.className, h1s: [...main.querySelectorAll("h1")].map((e) => e.textContent.trim()), pageHeader: !!ph, h1InPageHeader: !!t, h1Font: t ? getComputedStyle(t).fontFamily.slice(0, 40) + " " + getComputedStyle(t).fontSize : null, sections, overflow: { scrollWidth: document.documentElement.scrollWidth, clientWidth: vw, px: document.documentElement.scrollWidth - vw }, offenders };
});

// Grow the viewport to the document height (643A pattern) for a full-page capture.
const shot = async (page, n) => {
  const vp = page.viewportSize(); const h = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: vp.width, height: Math.max(h, vp.height) }); await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, n) }); await page.setViewportSize(vp); await page.waitForTimeout(500);
};

(async () => {
  const browser = await chromium.launch();
  let defs = null;
  for (const theme of THEMES) for (const w of WIDTHS) {
    const msgs = [];
    const { ctx, page, get } = await session(browser, { theme, viewport: { width: w, height: w <= 768 ? 900 : (w === 1024 ? 768 : 900) }, consoleSink: msgs });
    if (!defs) { const d = await get("/api/report-definitions"); defs = d.body; res.reportDefsApi = { status: d.status, categories: (d.body?.categories || []).map((c) => ({ label: c.label, reports: c.reports.map((r) => [r.name, r.slug]) })) }; }
    // fresh load: clear messages from login/dashboard, keep only the page load's
    msgs.length = 0;
    const st = await open(page, PROF, '[data-slot="card"] table');
    const m = await measureProf(page); m.status = st; res.prof[`${w}-${theme}`] = m;
    if (w >= 1024) await shot(page, `profitability-${w}-${theme}.png`);
    const profMsgs = msgs.splice(0);
    const st2 = await open(page, REPORTS, '[data-testid="page-header"]');
    const r = await measureReports(page); r.status = st2; res.reports[`${w}-${theme}`] = r;
    if (w >= 1024) await shot(page, `reports-${w}-${theme}.png`);
    if (w === 390 && theme === "light") { await shot(page, "reports-390-light.png"); }
    res.console[`${w}-${theme}`] = { profitability: profMsgs, reports: msgs.splice(0) };
    await ctx.close();
  }
  // expected report cards from main's code: Profitability + one card per API definition
  res.expectedReportCards = [["Firm performance", "Profitability", `/org/${SLUG}/reports/profitability`], ...((defs?.categories) || []).flatMap((c) => c.reports.map((r) => [c.label, r.name, `/org/${SLUG}/reports/${r.slug}`]))];
  fs.writeFileSync(path.join(OUT, "observe-raw.json"), JSON.stringify(res));
  await browser.close();
  console.log("done");
})();
