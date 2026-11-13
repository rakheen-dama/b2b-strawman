// 643C OBS interactions @ b0fe90ccf (1440, light then dark): sort buttons (click + keyboard), aria-sort,
// row order, focus ring + focus retention, hover, expand/collapse breakdown (+ survives sort), date
// range (chart + table follow), CSV export, Matter-table width counterfactual, console.
const fs = require("fs"), path = require("path");
const { chromium, PROF, OUT, session, open } = require("./common.cjs");
const res = { light: {}, dark: {} };
const UTIL = "Team Utilization", MATTER = "Matter Profitability";
const cardLoc = (page, title) => page.locator('[data-slot="card"]').filter({ has: page.locator('[data-slot="card-title"]', { hasText: new RegExp(`^${title}$`) }) }).last();

// In-page helpers installed once per document.
const HELPERS = () => {
  window.__h = {
    card: (title) => { const t = [...document.querySelectorAll('[data-slot="card-title"]')].find((e) => e.textContent.trim() === title); return t && t.closest('[data-slot="card"]'); },
    num: (s) => { const t = s.replace(/[\s R%h]/g, "").replace(",", "."); const v = parseFloat(t); return isNaN(v) ? null : v; },
    state: (title) => {
      const c = __h.card(title); const t = c && c.querySelector("table"); if (!t) return { table: false };
      const ths = [...t.querySelectorAll("thead th")];
      const rows = [...t.querySelectorAll("tbody tr")].filter((r) => r.getAttribute("data-slot") !== "data-table-expanded-row");
      return { heads: ths.map((th) => [th.textContent.trim(), th.getAttribute("aria-sort")]), rows: rows.map((r) => [...r.children].map((td) => td.textContent.trim())) };
    },
    rgba: (() => { const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true }); return (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], d[3] / 255]; }; })(),
    // composite element's background stack up to the first opaque layer
    bg: (el) => { const layers = []; for (let e = el; e; e = e.parentElement) { const v = __h.rgba(getComputedStyle(e).backgroundColor); if (v[3] > 0) { layers.push(v); if (v[3] >= 0.99) break; } } let out = [255, 255, 255]; for (const l of layers.reverse()) out = out.map((c, i) => Math.round(l[i] * l[3] + c * (1 - l[3]))); return out; },
    lum: ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); },
    ratio: (a, b) => { const [x, y] = [__h.lum(a), __h.lum(b)].sort((p, q) => q - p); return +((x + 0.05) / (y + 0.05)).toFixed(2); },
  };
};

async function sortSweep(page, title, colTypes) {
  const out = [];
  const card = cardLoc(page, title);
  const names = await card.locator("thead th button").allTextContents();
  for (const name of names) {
    for (const click of [1, 2]) {
      await card.locator("thead th button", { hasText: new RegExp(`^${name.replace(/[%]/g, "\\%")}$`) }).click();
      await page.waitForTimeout(350);
      const st = await page.evaluate((t) => __h.state(t), title);
      const idx = st.heads.findIndex((h) => h[0] === name);
      const vals = st.rows.map((r) => r[idx]);
      const active = st.heads.filter((h) => h[1] && h[1] !== "none");
      const dir = active.length === 1 ? active[0][1] : null;
      const type = colTypes[name];
      let sorted;
      if (type === "text") sorted = await page.evaluate(([v, d]) => v.every((x, i) => i === 0 || (d === "ascending" ? v[i - 1].localeCompare(x) <= 0 : v[i - 1].localeCompare(x) >= 0)), [vals, dir]);
      else sorted = await page.evaluate(([v, d]) => { const n = v.map(__h.num); return n.every((x, i) => i === 0 || (d === "ascending" ? n[i - 1] <= x : n[i - 1] >= x)); }, [vals, dir]);
      out.push({ col: name, click, activeTh: active.map((a) => a[0]), ariaSort: dir, values: vals, correctlySorted: sorted, othersNone: st.heads.filter((h) => h[0] !== name && h[1] !== null).every((h) => h[1] === "none") });
    }
  }
  return out;
}

(async () => {
  const browser = await chromium.launch();
  for (const theme of ["light", "dark"]) {
    const R = res[theme]; const msgs = [];
    const { ctx, page } = await session(browser, { theme, consoleSink: msgs, acceptDownloads: true });
    msgs.length = 0;
    await open(page, PROF, '[data-slot="card"] table');
    await page.evaluate(HELPERS);
    R.htmlClass = await page.evaluate(() => document.documentElement.className);
    // default sort (fresh load)
    R.defaultSort = { util: await page.evaluate((t) => __h.state(t).heads, UTIL), matter: await page.evaluate((t) => __h.state(t).heads, MATTER) };
    // 643B charts present above tables
    R.charts = await page.evaluate(() => {
      const bt = document.querySelector('[data-testid="billing-trend"]');
      const svgIn = (el) => el ? el.querySelectorAll("svg.recharts-surface").length : 0;
      const u = __h.card("Team Utilization"), m = __h.card("Matter Profitability");
      const top = (el) => el.getBoundingClientRect().top;
      return { billingTrend: !!bt, billingTrendState: bt && bt.getAttribute("data-state"), billingSvgs: svgIn(bt), billingAboveUtil: bt && u ? bt.getBoundingClientRect().bottom <= top(u) : null,
        utilSvgs: svgIn(u), utilBars: u ? u.querySelectorAll(".recharts-bar-rectangle").length : 0, matterSvgs: svgIn(m), matterBars: m ? m.querySelectorAll(".recharts-bar-rectangle").length : 0 };
    });
    if (theme === "light") {
      // sorting sweep (mouse)
      R.sortUtil = await sortSweep(page, UTIL, { Name: "text", "Total Hours": "num", Billable: "num", "Non-Billable": "num", "Utilization %": "num" });
      R.sortMatter = await sortSweep(page, MATTER, { Matter: "text", Client: "text", "Billable Hours": "num", Revenue: "num", Cost: "num", Margin: "num", "Margin %": "num" });
      await open(page, PROF, '[data-slot="card"] table'); await page.evaluate(HELPERS);
    }
    // keyboard: Tab from #util-to to the first header button
    await page.focus("#util-to");
    let tabs = 0, hit = null;
    for (; tabs < 15; ) {
      await page.keyboard.press("Tab"); tabs++;
      hit = await page.evaluate(() => { const a = document.activeElement; const th = a && a.closest("thead th"); return { tag: a.tagName, label: (a.getAttribute("aria-label") || a.textContent || "").trim().slice(0, 40), inHeader: !!th }; });
      if (hit.inHeader) break;
    }
    const focusInfo = () => page.evaluate(() => { const a = document.activeElement; const cs = getComputedStyle(a); return { label: a.textContent.trim(), focusVisible: a.matches(":focus-visible"), boxShadow: cs.boxShadow, outline: cs.outlineStyle + " " + cs.outlineWidth, sameNode: a === window.__btn, connected: window.__btn ? window.__btn.isConnected : null, ariaSort: a.closest("th") && a.closest("th").getAttribute("aria-sort") }; });
    await page.evaluate(() => { window.__btn = document.activeElement; });
    const kb = { tabsFromUtilTo: tabs, firstHeaderStop: hit, before: await focusInfo() };
    await cardLoc(page, UTIL).screenshot({ path: path.join(OUT, `util-header-focus-1440-${theme}.png`) });
    await page.keyboard.press("Enter"); await page.waitForTimeout(400);
    kb.afterEnter = await focusInfo(); kb.rowsAfterEnter = (await page.evaluate((t) => __h.state(t), UTIL)).rows.map((r) => r[1]);
    await page.keyboard.press("Space"); await page.waitForTimeout(400);
    kb.afterSpace = await focusInfo(); kb.rowsAfterSpace = (await page.evaluate((t) => __h.state(t), UTIL)).rows.map((r) => r[1]);
    // Tab onward to the next header button and sort with Enter (matter table too)
    await page.keyboard.press("Tab"); await page.evaluate(() => { window.__btn = document.activeElement; });
    kb.nextStop = await focusInfo(); await page.keyboard.press("Enter"); await page.waitForTimeout(400); kb.nextAfterEnter = await focusInfo();
    await page.focus("#prof-to"); await page.keyboard.press("Tab");
    const afterProfTo = await page.evaluate(() => (document.activeElement.textContent || "").trim());
    // walk to the first matter header button
    let mt = 0; for (; mt < 15; mt++) { const inH = await page.evaluate(() => !!document.activeElement.closest("thead th") && !!document.activeElement.closest('[data-slot="card"]').textContent.includes("Matter Profitability")); if (inH) break; await page.keyboard.press("Tab"); }
    await page.evaluate(() => { window.__btn = document.activeElement; });
    kb.matter = { firstTabAfterProfTo: afterProfTo, extraTabs: mt, before: await focusInfo() };
    await page.keyboard.press("Enter"); await page.waitForTimeout(400);
    kb.matter.afterEnter = await focusInfo(); kb.matter.order = (await page.evaluate((t) => __h.state(t), MATTER)).rows.map((r) => r[0].slice(0, 22));
    R.keyboard = kb;
    // hover a non-active header button: colour change
    await open(page, PROF, '[data-slot="card"] table'); await page.evaluate(HELPERS);
    const hb = cardLoc(page, MATTER).locator("thead th button", { hasText: /^Client$/ });
    const col = () => hb.evaluate((b) => { const c = getComputedStyle(b).color; return { color: c, ratio: __h.ratio(__h.rgba(c), __h.bg(b)) }; });
    R.hover = { before: await col() }; await hb.hover(); await page.waitForTimeout(300); R.hover.after = await col();
    if (theme === "dark") await cardLoc(page, MATTER).locator("thead").screenshot({ path: path.join(OUT, "matter-header-hover-1440-dark.png") });
    await page.mouse.move(0, 0);
    // expand / collapse (Bob row), survives sort, focus retention
    const util = cardLoc(page, UTIL);
    const expandState = () => page.evaluate(() => {
      const t = __h.card("Team Utilization").querySelector("table"); const trs = [...t.querySelectorAll("tbody tr")];
      return trs.map((tr) => { const ex = tr.getAttribute("data-slot") === "data-table-expanded-row"; if (ex) { const td = tr.children[0]; const tb = t.getBoundingClientRect(), cb = td.getBoundingClientRect(); const txt = td.querySelector(".text-muted-foreground"); return { expanded: true, colSpan: td.colSpan, cols: t.querySelectorAll("thead th").length, cells: tr.children.length, spansTable: Math.abs(cb.left - tb.left) < 1 && Math.abs(cb.right - tb.right) < 1, text: td.textContent.trim(), rowBg: getComputedStyle(tr).backgroundColor, bgComposite: __h.bg(td), textContrast: txt ? __h.ratio(__h.rgba(getComputedStyle(txt).color), __h.bg(txt)) : null, strongContrast: __h.ratio(__h.rgba(getComputedStyle(td.querySelector(".text-foreground")).color), __h.bg(td)) }; } const b = tr.querySelector("button[aria-label]"); return { name: tr.children[1].textContent.trim(), toggle: b ? b.getAttribute("aria-label") : null }; });
    });
    R.expand = { initial: await expandState(), expandableRows: (await expandState()).filter((r) => r.toggle).map((r) => r.name) };
    const bobRow = util.locator("tbody tr", { hasText: "Bob Admin" });
    await bobRow.getByRole("button", { name: "Expand currency breakdown" }).click(); await page.waitForTimeout(300);
    R.expand.afterClick = await expandState();
    await util.screenshot({ path: path.join(OUT, `util-expanded-1440-${theme}.png`) });
    await util.locator("thead th button", { hasText: /^Name$/ }).click(); await page.waitForTimeout(400);
    R.expand.afterSortName = await expandState();
    await util.locator("thead th button", { hasText: /^Name$/ }).click(); await page.waitForTimeout(400);
    R.expand.afterSortNameAsc = await expandState();
    // keyboard on the toggle: focus it, Enter collapses, focus stays, Enter re-expands
    const tog = bobRow.getByRole("button", { name: /currency breakdown/ });
    await tog.focus(); await page.evaluate(() => { window.__btn = document.activeElement; });
    const togInfo = () => page.evaluate(() => { const a = document.activeElement; return { label: a.getAttribute("aria-label"), sameNode: a === window.__btn, focusVisible: a.matches(":focus-visible"), outline: getComputedStyle(a).outlineStyle + " " + getComputedStyle(a).outlineWidth, boxShadow: getComputedStyle(a).boxShadow }; });
    R.expand.kbBefore = await togInfo();
    await page.keyboard.press("Enter"); await page.waitForTimeout(300); R.expand.kbAfterEnter = await togInfo(); R.expand.kbRowsAfterEnter = (await expandState()).filter((r) => r.expanded).length;
    await page.keyboard.press("Space"); await page.waitForTimeout(300); R.expand.kbAfterSpace = await togInfo(); R.expand.kbRowsAfterSpace = (await expandState()).filter((r) => r.expanded).length;
    // Tab-reach the toggle: Shift+Tab/Tab focus ring through real keyboard
    await page.keyboard.press("Shift+Tab"); await page.keyboard.press("Tab"); R.expand.kbTabReturn = await togInfo();
    if (theme === "light") await util.screenshot({ path: path.join(OUT, "util-expand-toggle-focus-1440-light.png") });
    if (theme === "light") {
      // date range: util + matter
      const snap = (title) => page.evaluate((t) => { const c = __h.card(t); const tb = c.querySelector("table"); return { rows: tb ? [...tb.querySelectorAll("tbody tr")].filter((r) => r.getAttribute("data-slot") !== "data-table-expanded-row").map((r) => [...r.children].map((td) => td.textContent.trim()).filter(Boolean).slice(0, 2).join(" | ")) : [], table: !!tb, chartSvgs: c.querySelectorAll("svg.recharts-surface").length, bars: c.querySelectorAll(".recharts-bar-rectangle").length, chartCats: [...c.querySelectorAll(".recharts-yAxis-tick-labels text")].map((e) => (e.querySelector("title") || e).textContent.trim()), empty: /No (utilization|matter profitability) data for this period/.test(c.textContent), expandedRows: c.querySelectorAll('[data-slot="data-table-expanded-row"]').length, mainTables: document.querySelectorAll("#main-content table").length }; }, title);
      const setRange = async (fromSel, toSel, f, t) => { await page.fill(fromSel, f); await page.waitForTimeout(1500); await page.fill(toSel, t); await page.waitForTimeout(2500); await page.waitForLoadState("networkidle").catch(() => {}); };
      R.range = { util: [], matter: [] };
      for (const [f, t] of [["2026-10-01", "2026-10-31"], ["2026-11-01", "2026-11-12"], ["2026-11-13", "2026-11-13"], ["2026-11-01", "2026-11-13"]]) {
        await setRange("#util-from", "#util-to", f, t); R.range.util.push({ from: f, to: t, ...(await snap(UTIL)) });
        if (f === "2026-10-01") await util.screenshot({ path: path.join(OUT, "util-range-oct-empty-1440-light.png") });
        await setRange("#prof-from", "#prof-to", f, t); R.range.matter.push({ from: f, to: t, ...(await snap(MATTER)) });
      }
      // CSV export
      const mc = cardLoc(page, MATTER);
      R.csv = { exportButtonsInMatterCard: await mc.getByRole("button", { name: /export csv/i }).count(), exportButtonsOnPage: await page.getByRole("button", { name: /export csv/i }).count() };
      const [dl] = await Promise.all([page.waitForEvent("download", { timeout: 30000 }), page.click('[data-testid="profitability-export-csv"]')]);
      R.csv.filename = dl.suggestedFilename(); const p = path.join(OUT, R.csv.filename); await dl.saveAs(p);
      R.csv.firstLines = fs.readFileSync(p, "utf8").split(/\r?\n/).slice(0, 4);
      R.csv.lineCount = fs.readFileSync(p, "utf8").split(/\r?\n/).filter(Boolean).length;
      // Matter-table width counterfactual: (a) as rendered, (b) money without font-mono, (c) + 12px header icons (main's ArrowUpDown size-3)
      await open(page, PROF, '[data-slot="card"] table'); await page.evaluate(HELPERS);
      R.matterWidth = await page.evaluate(() => {
        const t = __h.card("Matter Profitability").querySelector("table"); const sc = t.closest('[data-slot="table-container"]');
        const w = () => ({ table: Math.round(t.getBoundingClientRect().width), scroll: sc.scrollWidth, client: sc.clientWidth, cols: [...t.querySelectorAll("thead th")].map((th) => [th.textContent.trim(), Math.round(th.getBoundingClientRect().width)]) });
        const a = w();
        t.querySelectorAll("tbody .font-mono").forEach((e) => e.classList.remove("font-mono")); const b = w();
        t.querySelectorAll("thead th button svg").forEach((s) => { s.style.width = "12px"; s.style.height = "12px"; }); const c = w();
        return { asRendered: a, noFontMono: b, noFontMonoIcons12: c, monoFont: getComputedStyle(document.body).getPropertyValue("--font-mono").slice(0, 60) };
      });
      await cardLoc(page, MATTER).screenshot({ path: path.join(OUT, "matter-card-1440-light.png") });
    }
    R.console = msgs.splice(0);
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, "interact-raw.json"), JSON.stringify(res));
  await browser.close(); console.log("done");
})().catch((e) => { console.error(e); fs.writeFileSync(path.join(OUT, "interact-raw.json"), JSON.stringify({ error: String(e), res })); process.exit(1); });
