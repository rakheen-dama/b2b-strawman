// 643A final-OBS (read-only, after drive.cjs created the DRAFT reconciliation) on head 859d257d2:
// tiles vs APIs, chart "View as table", tooltip + keyboard, D1 transactions-table width, D2 StatCard
// value/icon overlap + column count, D3 chart end labels, width x theme screenshots, page overflow,
// Home trust widget, Carol (member), console.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const fs = require("fs"), path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org", OUT = __dirname;
const TRUST = `${BASE}/org/${SLUG}/trust-accounting`, HOME = `${BASE}/org/${SLUG}/dashboard`;
const lines = (t) => t.split("\n").map((s) => s.trim()).filter(Boolean);
const consoleMsgs = [], loads = [];
async function session(browser, { user, theme = "light", viewport = { width: 1440, height: 900 }, reducedMotion }) {
  const ctx = await browser.newContext({ viewport, colorScheme: theme, reducedMotion: reducedMotion || "no-preference" });
  await ctx.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch {} }, theme);
  const page = await ctx.newPage();
  const tag = `${user || "alice"}/${theme}/${viewport.width}${reducedMotion ? "/reduce" : ""}`;
  page.on("console", (m) => { if (["error", "warning"].includes(m.type()) || /hydrat/i.test(m.text())) consoleMsgs.push({ session: tag, url: page.url(), type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => consoleMsgs.push({ session: tag, url: page.url(), type: "pageerror", text: String(e).slice(0, 300) }));
  page.on("load", () => loads.push(`${tag} ${page.url().replace(BASE, "")}`));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  if (user === "carol") await page.locator("#user-select").selectOption("user_e2e_carol");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 }); await page.waitForTimeout(1000);
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  return { ctx, page, get };
}
async function openTrust(page) {
  const resp = await page.goto(TRUST, { waitUntil: "load" });
  await page.locator("h1").first().waitFor({ timeout: 60000 });
  await page.locator('[data-testid="trust-balance-trend"][data-state]').waitFor({ timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(3500); // recharts draw-in animation
  return resp ? resp.status() : null;
}
const overflow = (page) => page.evaluate(() => ({ scrollWidth: document.documentElement.scrollWidth, innerWidth: window.innerWidth, ok: document.documentElement.scrollWidth <= window.innerWidth, htmlClass: document.documentElement.className }));
// Grow the viewport to the document height instead of fullPage (fullPage restarts the chart animation).
const shot = async (page, n) => {
  const vp = page.viewportSize();
  const h = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: vp.width, height: Math.max(h, vp.height) });
  await page.waitForTimeout(3500);
  await page.screenshot({ path: path.join(OUT, n) });
  await page.setViewportSize(vp);
  await page.waitForTimeout(1500);
};
// D1 + D2 measurements (DOM boxes; Range boxes catch text painting beyond its element).
const layout = (page) => page.evaluate(() => {
  const R = (r) => ({ l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) });
  const B = (e) => R(e.getBoundingClientRect());
  const T = (e) => { const rg = document.createRange(); rg.selectNodeContents(e); return R(rg.getBoundingClientRect()); };
  const hit = (a, b) => a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b;
  const cardEls = [...document.querySelectorAll('[data-testid="summary-cards"] > *')];
  const cards = cardEls.map((card) => {
    const q = (s) => card.querySelector(`[data-slot="${s}"]`);
    const val = q("stat-card-value"), icon = q("stat-card-icon"), label = q("stat-card-label"), hint = q("stat-card-hint");
    const cb = B(card), vb = val && T(val), ib = icon && B(icon), lb = label && T(label), hb = hint && T(hint);
    return { label: label && label.innerText.trim(), value: val && val.innerText.trim(), valueScroll: val ? [val.scrollWidth, val.clientWidth] : null, valueFits: val ? val.scrollWidth <= val.clientWidth : null, valueTextInsideCard: vb ? vb.l >= cb.l && vb.r <= cb.r : null, valueHitsIcon: vb && ib ? hit(vb, ib) : null, labelHitsIcon: lb && ib ? hit(lb, ib) : null, hintHitsIcon: hb && ib ? hit(hb, ib) : null, iconInsideCard: ib ? ib.r <= cb.r : null, card: cb, valueBox: vb, iconBox: ib };
  });
  const tops = cards.map((c) => c.card.t);
  const columns = tops.filter((t) => t === tops[0]).length;
  const t = document.querySelector('[data-testid="transactions-table"]');
  let tx = null;
  if (t) {
    const sc = t.closest('[data-slot="table-container"]') || t.parentElement;
    const card = t.closest('[data-slot="card"]') || sc; const sb = B(sc), kb = B(card);
    const heads = [...t.querySelectorAll("thead th")].map((th) => { const b = B(th); return { col: th.innerText.trim(), inScroller: b.l >= sb.l - 1 && b.r <= sb.r + 1, inCard: b.l >= kb.l - 1 && b.r <= kb.r + 1 }; });
    const refIdx = heads.findIndex((h) => /reference/i.test(h.col));
    const refs = [...t.querySelectorAll("tbody tr")].map((tr) => { const td = tr.children[refIdx]; const span = td.firstElementChild || td; const lh = parseFloat(getComputedStyle(span).lineHeight) || 20; const rects = (() => { const rg = document.createRange(); rg.selectNodeContents(span); return new Set([...rg.getClientRects()].map((r) => Math.round(r.top))).size; })(); return { len: td.innerText.trim().length, lines: rects, h: Math.round(span.getBoundingClientRect().height), lh, overflowsCell: span.scrollWidth > td.clientWidth + 1 }; });
    const long = refs.filter((r) => r.len > 30);
    tx = { tableScrollWidth: t.scrollWidth, scrollerClientWidth: sc.clientWidth, scrollerScrollWidth: sc.scrollWidth, fits: t.scrollWidth <= sc.clientWidth, heads, allHeadsInCard: heads.every((h) => h.inCard && h.inScroller), rows: refs.length, longRefs: long.length, longRefsWrapped: long.filter((r) => r.lines > 1).length, anyRefOverflowsCell: refs.some((r) => r.overflowsCell), maxRefLen: Math.max(...refs.map((r) => r.len)) };
  }
  return { cards, columns, tx };
});
const chartInfo = (page) => page.evaluate(() => {
  const c = document.querySelector('[data-testid="trust-balance-trend"]');
  if (!c) return null;
  const B = (e) => { const r = e.getBoundingClientRect(); return { l: Math.round(r.left), r: Math.round(r.right), t: Math.round(r.top), b: Math.round(r.bottom) }; };
  const hit = (a, b) => a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b;
  const svg = c.querySelector("svg.recharts-surface");
  const endLabels = svg ? [...svg.querySelectorAll("text")].filter((e) => /^(Cashbook|Client ledgers)$/.test(e.textContent.trim())).map((e) => ({ text: e.textContent.trim(), box: B(e) })) : [];
  const overlap = endLabels.length >= 2 && hit(endLabels[0].box, endLabels[1].box);
  const legend = [...c.querySelectorAll(".recharts-legend-wrapper *")].filter((e) => e.children.length === 0 && e.textContent.trim()).map((e) => e.textContent.trim());
  const ticks = svg ? [...svg.querySelectorAll("text")].map((e) => e.textContent.trim()).filter((t) => /^[A-Z][a-z]{2} \d{4}/.test(t)) : [];
  const svgB = svg && B(svg);
  const labelsInsideSvg = endLabels.every((l) => svgB && l.box.r <= svgB.r && l.box.l >= svgB.l);
  return { state: c.getAttribute("data-state"), endLabels, endLabelsOverlap: overlap, labelsInsideSvg, legend, xTicks: ticks, tables: c.querySelectorAll("table").length };
});
async function lastPointTooltip(page) {
  const chart = page.locator('[data-testid="trust-balance-trend"]');
  await chart.scrollIntoViewIfNeeded();
  const surf = chart.locator("svg.recharts-surface").first(); const box = await surf.boundingBox();
  const ticks = await chart.locator("svg.recharts-surface text").evaluateAll((els) => els.filter((e) => /^[A-Z][a-z]{2} \d{4}/.test(e.textContent.trim())).map((e) => { const r = e.getBoundingClientRect(); return { t: e.textContent.trim(), cx: Math.round(r.x + r.width / 2) }; }));
  await page.mouse.move(ticks.at(-1).cx, box.y + box.height * 0.4); await page.waitForTimeout(700);
  return { lastTick: ticks.at(-1), tooltip: lines(await chart.locator(".recharts-tooltip-wrapper").innerText().catch(() => "")) };
}
(async () => {
  const browser = await chromium.launch();
  const out = {};
  // ---- Alice 1440 light: values, chart, tooltip, keyboard, table, Home ----
  {
    const { ctx, page, get } = await session(browser, {});
    const tp = (await get("/api/dashboard/trust-position")).body; const id = tp.trustAccountId;
    const trend = (await get("/api/dashboard/trust-balance-trend")).body;
    out.api = { trustPosition: tp, cashbook: (await get(`/api/trust-accounts/${id}/cashbook-balance`)).body, totalBalance: (await get(`/api/trust-accounts/${id}/total-balance`)).body, trendMonths: trend.months.length, trendLast: trend.months.at(-1), reconciliations: (await get(`/api/trust-accounts/${id}/reconciliations`)).body.map((r) => ({ id: r.id, periodEnd: r.periodEnd, status: r.status, isBalanced: r.isBalanced })) };
    const o = (out.alice1440Light = { httpStatus: await openTrust(page) });
    o.h1 = await page.locator("h1").first().innerText();
    o.cards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean)));
    o.layout = await layout(page);
    o.chart = await chartInfo(page);
    o.overflow = await overflow(page);
    await shot(page, "trust-dashboard-1440-light.png");
    o.tooltipLast = await lastPointTooltip(page);
    await page.locator('[data-testid="trust-balance-trend"]').screenshot({ path: path.join(OUT, "chart-tooltip-last-1440-light.png") });
    await page.mouse.move(5, 5);
    // keyboard: Tab from the top until focus lands inside the chart
    await openTrust(page);
    await page.locator("body").click({ position: { x: 1, y: 1 } }).catch(() => {});
    for (let i = 0; i < 80; i++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => { const a = document.activeElement; const cs = getComputedStyle(a); return { tag: a.tagName, inChart: !!a.closest('[data-testid="trust-balance-trend"]'), outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`, boxShadow: cs.boxShadow.slice(0, 80) }; });
      if (f.inChart) { o.chartFocus = { tabPresses: i + 1, ...f }; break; }
    }
    if (o.chartFocus) {
      await page.locator('[data-testid="trust-balance-trend"]').screenshot({ path: path.join(OUT, "chart-keyboard-focus-1440-light.png") });
      const tt = async () => lines(await page.locator('[data-testid="trust-balance-trend"] .recharts-tooltip-wrapper').innerText().catch(() => "")).join(" | ");
      o.arrows = [];
      for (const k of ["ArrowRight", "ArrowRight", "ArrowLeft"]) { await page.keyboard.press(k); await page.waitForTimeout(400); o.arrows.push(`${k}: ${await tt()}`); }
      for (let i = 0; i < 12; i++) { await page.keyboard.press("ArrowRight"); await page.waitForTimeout(200); }
      o.arrows.push(`ArrowRight x12: ${await tt()}`);
    }
    // View as table
    await openTrust(page);
    const chart = page.locator('[data-testid="trust-balance-trend"]');
    const btn = chart.getByRole("button", { name: "View as table" });
    o.toggle = { tablesBefore: await chart.locator("table").count(), ariaExpandedBefore: await btn.getAttribute("aria-expanded") };
    await btn.click(); await chart.locator("table").waitFor();
    o.toggle.tablesAfter = await chart.locator("table").count();
    o.toggle.ariaExpandedAfter = await chart.getByRole("button", { name: "Hide table" }).getAttribute("aria-expanded");
    o.chartTable = { columns: await chart.locator("table thead th").allInnerTexts(), rows: await chart.locator("table tbody tr").evaluateAll((trs) => trs.map((tr) => [...tr.children].map((c) => c.innerText.trim()))) };
    await chart.screenshot({ path: path.join(OUT, "chart-view-as-table-1440-light.png") });
    // Home widget
    await page.goto(HOME, { waitUntil: "load" }); await page.locator("h1").first().waitFor({ timeout: 60000 });
    const w = page.locator('#main-content [data-testid="trust-position-widget"]');
    await w.waitFor({ timeout: 60000 }); await page.waitForTimeout(800);
    out.homeWidget1440Light = { text: lines(await w.innerText()), overflow: await overflow(page) };
    await w.scrollIntoViewIfNeeded(); await w.screenshot({ path: path.join(OUT, "home-trust-widget-1440-light.png") });
    await ctx.close();
  }
  // ---- width x theme matrix ----
  out.matrix = {};
  for (const [w, h, theme, full] of [[390, 844, "light", true], [768, 1024, "light", true], [1024, 768, "light", true], [1024, 768, "dark", true], [1280, 800, "light", false], [1440, 900, "dark", true]]) {
    const { ctx, page } = await session(browser, { theme, viewport: { width: w, height: h } });
    const key = `${w}-${theme}`;
    const m = (out.matrix[key] = { status: await openTrust(page) });
    m.overflow = await overflow(page); m.layout = await layout(page); m.chart = await chartInfo(page);
    if (full) await shot(page, `trust-dashboard-${w}-${theme}.png`);
    else await page.locator('[data-testid="summary-cards"]').screenshot({ path: path.join(OUT, `summary-cards-${w}-${theme}.png`) });
    if (w >= 1024) { m.tooltipLast = await lastPointTooltip(page); await page.mouse.move(5, 5); }
    if (theme === "dark") {
      m.darkColors = await page.evaluate(() => { const q = (s) => document.querySelector(s); const c = (e) => e ? `${getComputedStyle(e).color} on ${getComputedStyle(e).backgroundColor}` : null; return { body: c(document.body), card: c(q('[data-testid="summary-cards"] > *')), statValue: c(q('[data-slot="stat-card-value"]')), statHint: c(q('[data-slot="stat-card-hint"]')), axisTickFill: (() => { const t = q('[data-testid="trust-balance-trend"] .recharts-cartesian-axis-tick text'); return t ? getComputedStyle(t).fill : null; })(), txCell: c(q('[data-testid="transactions-table"] tbody td')) }; });
      await page.waitForTimeout(500);
      await page.locator('[data-testid="trust-balance-trend"]').screenshot({ path: path.join(OUT, `chart-${w}-dark.png`) });
    }
    await ctx.close();
  }
  // ---- Carol (member) 1440 light ----
  {
    const { ctx, page, get } = await session(browser, { user: "carol" });
    out.carol = { trustPositionApi: (await get("/api/dashboard/trust-position")).status, trendApi: (await get("/api/dashboard/trust-balance-trend")).status };
    out.carol.pageStatus = await openTrust(page);
    out.carol.h1 = await page.locator("h1").first().innerText().catch(() => null);
    out.carol.cards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean))).catch(() => null);
    out.carol.chart = await chartInfo(page);
    out.carol.layout = await layout(page);
    out.carol.overflow = await overflow(page);
    await shot(page, "trust-dashboard-carol-1440-light.png");
    await ctx.close();
  }
  out.loads = loads.length;
  out.console = consoleMsgs;
  fs.writeFileSync(path.join(OUT, "observe-raw.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error(e); console.error(JSON.stringify(consoleMsgs)); process.exit(1); });
