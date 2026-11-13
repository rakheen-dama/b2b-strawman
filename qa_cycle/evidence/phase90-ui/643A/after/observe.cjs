// 643A after-OBS (read-only, after drive.cjs created the DRAFT reconciliation): trust dashboard tiles vs
// APIs, trend chart "View as table" vs tiles, tooltip, keyboard focus, transactions table, viewport x theme
// screenshots + horizontal-overflow check, reduced motion, Home trust widget, Carol (member), console.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const fs = require("fs"), path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org", OUT = __dirname;
const TRUST = `${BASE}/org/${SLUG}/trust-accounting`, HOME = `${BASE}/org/${SLUG}/dashboard`;
const lines = (t) => t.split("\n").map((s) => s.trim()).filter(Boolean);
const consoleMsgs = [];
async function session(browser, { user, theme = "light", viewport = { width: 1440, height: 900 }, reducedMotion }) {
  const ctx = await browser.newContext({ viewport, colorScheme: theme, reducedMotion: reducedMotion || "no-preference" });
  await ctx.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch {} }, theme);
  const page = await ctx.newPage();
  const tag = `${user || "alice"}/${theme}/${viewport.width}${reducedMotion ? "/reduce" : ""}`;
  page.on("console", (m) => { if (["error", "warning"].includes(m.type())) consoleMsgs.push({ session: tag, url: page.url(), type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => consoleMsgs.push({ session: tag, url: page.url(), type: "pageerror", text: String(e).slice(0, 300) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  if (user === "carol") await page.locator("#user-select").selectOption("user_e2e_carol");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 }); await page.waitForTimeout(1000); // let Home's client fetches settle before navigating away
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  return { ctx, page, get };
}
async function openTrust(page) {
  const resp = await page.goto(TRUST, { waitUntil: "load" });
  await page.locator("h1").first().waitFor({ timeout: 60000 });
  await page.locator('[data-testid="trust-balance-trend"]').waitFor({ timeout: 60000 }).catch(() => {});
  await page.locator('[data-testid="trust-balance-trend"][data-state="ready"], [data-testid="trust-balance-trend"][data-state="empty"], [data-testid="trust-balance-trend"][data-state="error"]').waitFor({ timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(3500); // let the recharts area animation finish before screenshots
  return resp ? resp.status() : null;
}
const overflow = (page) => page.evaluate(() => ({ scrollWidth: document.documentElement.scrollWidth, innerWidth: window.innerWidth, ok: document.documentElement.scrollWidth <= window.innerWidth, htmlClass: document.documentElement.className }));
// Full-page capture without Playwright's fullPage resize: a fullPage capture resizes the viewport, which
// makes recharts' ResponsiveContainer re-run its draw-in animation and the frame shows an empty plot. So
// grow the viewport to the document height, let the animation finish, capture, then restore.
const shot = async (page, n) => {
  const vp = page.viewportSize();
  const h = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: vp.width, height: Math.max(h, vp.height) });
  await page.waitForTimeout(3500);
  await page.screenshot({ path: path.join(OUT, n) });
  await page.setViewportSize(vp);
};
(async () => {
  const browser = await chromium.launch();
  const out = {};
  // ---- Alice 1440 light: values, chart, table, tooltip, focus ----
  {
    const { ctx, page, get } = await session(browser, {});
    const tp = (await get("/api/dashboard/trust-position")).body;
    const id = tp.trustAccountId;
    out.api = { trustPosition: tp, cashbook: (await get(`/api/trust-accounts/${id}/cashbook-balance`)).body, totalBalance: (await get(`/api/trust-accounts/${id}/total-balance`)).body, trendLast: (await get("/api/dashboard/trust-balance-trend")).body.months.at(-1), reconciliations: (await get(`/api/trust-accounts/${id}/reconciliations`)).body.map((r) => ({ periodEnd: r.periodEnd, status: r.status, isBalanced: r.isBalanced })), transactions: (await get(`/api/trust-accounts/${id}/transactions?size=50`)).body };
    const t = out.api.transactions; out.api.transactions = t && t.page ? { totalElements: t.page.totalElements, size: t.page.size, contentLen: (t.content || []).length } : t && t.totalElements !== undefined ? { totalElements: t.totalElements, contentLen: (t.content || []).length } : null;
    out.alice1440Light = { httpStatus: await openTrust(page) };
    const o = out.alice1440Light;
    o.h1 = await page.locator("h1").first().innerText();
    o.cards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean)));
    const chart = page.locator('[data-testid="trust-balance-trend"]');
    o.chart = { state: await chart.getAttribute("data-state"), role: await chart.getAttribute("role"), ariaLabel: await chart.getAttribute("aria-label"), header: lines(await chart.locator('[data-slot="card-header"]').innerText().catch(() => "")), legend: await chart.locator(".recharts-legend-item-text, [class*=legend] span").allInnerTexts().catch(() => []), dashedPaths: await chart.locator('path.recharts-curve[stroke-dasharray]:not([stroke-dasharray=""])').count(), srSummary: await chart.locator('.sr-only').allInnerTexts().catch(() => []) };
    o.tablesInChartBeforeToggle = await chart.locator("table").count();
    o.overflow = await overflow(page);
    await shot(page, "trust-dashboard-1440-light.png");
    // tooltip: hover the last-but-one x position
    const surf = chart.locator("svg.recharts-surface").first();
    const box = await surf.boundingBox();
    await page.mouse.move(box.x + box.width * 0.82, box.y + box.height * 0.5);
    await page.waitForTimeout(700);
    o.tooltip = lines(await chart.locator(".recharts-tooltip-wrapper").innerText().catch(() => ""));
    await chart.screenshot({ path: path.join(OUT, "chart-tooltip-1440-light.png") });
    const nov = await chart.locator(".recharts-xAxis .recharts-cartesian-axis-tick").last().boundingBox();
    await page.mouse.move(nov.x + nov.width / 2, box.y + box.height * 0.4);
    await page.waitForTimeout(700);
    await chart.screenshot({ path: path.join(OUT, "chart-tooltip-last-point-1440-light.png") });
    o.tooltipLast = lines(await chart.locator(".recharts-tooltip-wrapper").innerText().catch(() => ""));
    await page.mouse.move(5, 5);
    // keyboard focus: Tab from top until focus lands inside the chart
    await page.goto(TRUST, { waitUntil: "load" }); await openTrust(page);
    await page.locator("body").click({ position: { x: 1, y: 1 } }).catch(() => {});
    const focusPath = [];
    for (let i = 0; i < 80; i++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => { const a = document.activeElement; const inChart = !!a.closest('[data-testid="trust-balance-trend"]'); const cs = getComputedStyle(a); return { tag: a.tagName, cls: String(a.getAttribute("class") || "").slice(0, 60), text: (a.innerText || a.getAttribute("aria-label") || "").slice(0, 40), inChart, outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`, boxShadow: cs.boxShadow.slice(0, 80) }; });
      focusPath.push(`${f.tag}:${f.text}`);
      if (f.inChart) { o.chartFocus = { tabPresses: i + 1, ...f }; break; }
    }
    o.focusPathHead = focusPath.slice(-6);
    await page.locator('[data-testid="trust-balance-trend"]').scrollIntoViewIfNeeded();
    o.chartFocusAfterArrow = null;
    if (o.chartFocus) {
      await chart.screenshot({ path: path.join(OUT, "chart-keyboard-focus-1440-light.png") });
      await page.keyboard.press("ArrowRight"); await page.waitForTimeout(500);
      o.chartFocusAfterArrow = lines(await chart.locator(".recharts-tooltip-wrapper").innerText().catch(() => ""));
    }
    // View as table
    await page.goto(TRUST, { waitUntil: "load" }); await openTrust(page);
    const btn = chart.getByRole("button", { name: "View as table" });
    o.toggleAriaExpandedBefore = await btn.getAttribute("aria-expanded");
    await btn.click();
    await chart.locator("table").waitFor();
    o.tablesInChartAfterToggle = await chart.locator("table").count();
    o.toggleAriaExpandedAfter = await chart.getByRole("button", { name: "Hide table" }).getAttribute("aria-expanded");
    o.chartTable = { columns: await chart.locator("table thead th").allInnerTexts(), rows: await chart.locator("table tbody tr").evaluateAll((trs) => trs.map((tr) => [...tr.children].map((c) => c.innerText.trim()))) };
    await chart.screenshot({ path: path.join(OUT, "chart-view-as-table-1440-light.png") });
    // transactions table
    const tbl = page.locator('[data-testid="transactions-table"]');
    o.transactionsTable = { count: await tbl.count(), tagName: await tbl.evaluate((e) => e.tagName).catch(() => null), columns: await tbl.locator("thead th").allInnerTexts(), rowCount: await tbl.locator("tbody tr").count(), firstRow: await tbl.locator("tbody tr").first().evaluate((tr) => [...tr.children].map((c) => c.innerText.trim())).catch(() => null), sectionText: lines(await tbl.locator("xpath=ancestor::*[@data-slot='card'][1]").innerText().catch(() => "")).slice(0, 4) };
    // Home widget 1440 light
    await page.goto(HOME, { waitUntil: "load" });
    const w = page.locator('#main-content [data-testid="trust-position-widget"]');
    await w.waitFor({ timeout: 60000 }); await page.waitForTimeout(800);
    out.homeWidget1440Light = { text: lines(await w.innerText()), overflow: await overflow(page) };
    await w.scrollIntoViewIfNeeded();
    await w.screenshot({ path: path.join(OUT, "home-trust-widget-1440-light.png") });
    await ctx.close();
  }
  // ---- viewport x theme matrix ----
  out.matrix = {};
  for (const [w, h, theme] of [[1024, 768, "light"], [1024, 768, "dark"], [1440, 900, "dark"], [390, 844, "light"], [768, 1024, "light"]]) {
    const { ctx, page } = await session(browser, { theme, viewport: { width: w, height: h } });
    const status = await openTrust(page);
    const key = `${w}-${theme}`;
    const chart = page.locator('[data-testid="trust-balance-trend"]');
    out.matrix[key] = { status, overflow: await overflow(page), chartState: await chart.getAttribute("data-state"), cardsTop: await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((e) => Math.round(e.getBoundingClientRect().top))), cardsWidth: await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((e) => Math.round(e.getBoundingClientRect().width))),
      clipped: await page.evaluate(() => [...document.querySelectorAll('[data-testid="summary-cards"] *, [data-testid="transactions-table"] *')].filter((e) => e.children.length === 0 && e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflow !== "visible").map((e) => e.innerText.slice(0, 40)).slice(0, 10)),
      tableScroller: await page.evaluate(() => { const t = document.querySelector('[data-testid="transactions-table"]'); const p = t && t.parentElement; return p ? { tableW: Math.round(t.scrollWidth), parentW: Math.round(p.clientWidth), parentOverflowX: getComputedStyle(p).overflowX } : null; }) };
    await shot(page, `trust-dashboard-${w}-${theme}.png`);
    if (theme === "dark" && w === 1440) {
      out.matrix[key].darkColors = await page.evaluate(() => { const q = (s) => document.querySelector(s); const c = (e) => e ? { color: getComputedStyle(e).color, bg: getComputedStyle(e).backgroundColor } : null; return { body: c(document.body), card: c(q('[data-testid="summary-cards"] > *')), chartCard: c(q('[data-testid="trust-balance-trend"]')), axisTick: (() => { const t = q('[data-testid="trust-balance-trend"] .recharts-cartesian-axis-tick text'); return t ? getComputedStyle(t).fill : null; })(), table: c(q('[data-testid="transactions-table"]')) }; });
      await page.locator('[data-testid="trust-balance-trend"]').screenshot({ path: path.join(OUT, "chart-1440-dark.png") });
    }
    await ctx.close();
  }
  // ---- reduced motion ----
  {
    const { ctx, page } = await session(browser, { reducedMotion: "reduce" });
    await openTrust(page);
    const chart = page.locator('[data-testid="trust-balance-trend"]');
    out.reducedMotion = { matches: await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches), chartState: await chart.getAttribute("data-state"), areaPaths: await chart.locator("path.recharts-area-area").count(), curves: await chart.locator("path.recharts-curve").count() };
    await chart.screenshot({ path: path.join(OUT, "chart-reduced-motion-1440-light.png") });
    await ctx.close();
  }
  // ---- Carol (member) ----
  {
    const { ctx, page, get } = await session(browser, { user: "carol" });
    out.carol = { trustPositionApi: (await get("/api/dashboard/trust-position")).status, trendApi: (await get("/api/dashboard/trust-balance-trend")).status, trustAccountsApi: (await get("/api/trust-accounts")).status };
    out.carol.pageStatus = await openTrust(page);
    out.carol.h1 = await page.locator("h1").first().innerText().catch(() => null);
    out.carol.cards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean))).catch(() => null);
    out.carol.chart = await page.locator('[data-testid="trust-balance-trend"]').evaluate((e) => ({ state: e.getAttribute("data-state"), text: e.innerText.slice(0, 200) })).catch(() => null);
    out.carol.transactionsRows = await page.locator('[data-testid="transactions-table"] tbody tr').count();
    out.carol.mainText = lines(await page.locator("main").innerText().catch(() => "")).slice(0, 12);
    out.carol.overflow = await overflow(page);
    await shot(page, "trust-dashboard-carol-1440-light.png");
    await page.goto(HOME, { waitUntil: "load" }); await page.locator("h1").first().waitFor({ timeout: 60000 });
    await page.locator('#main-content [data-testid="trust-position-widget"]').waitFor({ timeout: 20000 }).catch(() => {});
    out.carol.homeWidget = await page.locator('#main-content [data-testid="trust-position-widget"]').innerText().then(lines).catch(() => "absent");
    await ctx.close();
  }
  out.console = consoleMsgs;
  fs.writeFileSync(path.join(OUT, "observe-raw.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error(e); console.error(JSON.stringify(consoleMsgs)); process.exit(1); });
