// 640B OBS-CARRY "Primitive observation (639B)": the Unbilled WIP BarChartPreset on Alice's Home
// (≥ 2 fee earners). Usage: node obs-639b.cjs [theme=light] [width=1440]
// (a) hover tooltip, (b) keyboard focus + arrows tooltip, (c) "View as table" mounts one table,
// (d) a11y snapshot (sr summary + figure name), (e) throttled load: skeleton vs loaded height + CLS.
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-640B/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const OUT = __dirname;
const theme = process.argv[2] || "light";
const width = Number(process.argv[3] || 1440);
const VIEW = width === 1024 ? { width: 1024, height: 768 } : { width: 1440, height: 900 };
const tag = `${width}-${theme}`;
const report = {};

async function newPage(browser) {
  const context = await browser.newContext({ viewport: VIEW });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
    // Layout-shift log (CLS sources) for (e).
    window.__shifts = [];
    try {
      new PerformanceObserver((list) => {
        for (const e of list.getEntries()) {
          window.__shifts.push({
            value: e.value,
            hadRecentInput: e.hadRecentInput,
            sources: (e.sources || []).map((s) => ({
              node: s.node ? `${s.node.nodeName}.${String(s.node.className).slice(0, 60)}` : null,
              prev: s.previousRect && { y: s.previousRect.y, h: s.previousRect.height },
              cur: s.currentRect && { y: s.currentRect.y, h: s.currentRect.height },
            })),
          });
        }
      }).observe({ type: "layout-shift", buffered: true });
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  const messages = [];
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning") messages.push({ type: m.type(), text: m.text().slice(0, 400) });
  });
  page.on("pageerror", (e) => messages.push({ type: "pageerror", text: String(e).slice(0, 400) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption("user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 60_000 });
  try {
    await page.locator("#main-content").getByRole("heading", { level: 1, name: "Home" }).waitFor({ timeout: 60_000 });
  } catch (e) {
    await page.screenshot({ path: path.join(OUT, `debug-heading-timeout-${tag}.png`) });
    console.error("heading timeout", page.url(), await page.evaluate(() => Array.from(document.querySelectorAll("h1")).map((h) => h.outerHTML.slice(0, 200))), JSON.stringify(messages));
    throw e;
  }
  return { context, page, messages };
}

const tooltipState = (w) =>
  w.evaluate((el) => {
    const t = el.querySelector(".recharts-tooltip-wrapper");
    if (!t) return { present: false };
    const cs = getComputedStyle(t);
    return { present: true, visibility: cs.visibility, text: t.innerText.replace(/\s+/g, " ").trim() };
  });

async function interactive(browser) {
  const { context, page, messages } = await newPage(browser);
  const widget = page.locator("#main-content").getByTestId("unbilled-wip-widget");
  const svg = widget.locator("svg.recharts-surface");
  await svg.waitFor({ timeout: 30_000 });
  await widget.scrollIntoViewIfNeeded();
  await page.mouse.move(0, 0);
  await page.waitForTimeout(3000);

  // Full-page-capture artifact check: does a fullPage screenshot restart the bar animation?
  const barWidths = () =>
    widget.evaluate((el) => Array.from(el.querySelectorAll(".recharts-bar-rectangle path")).map((p) => Math.round(p.getBoundingClientRect().width)));
  const settled = await barWidths();
  await page.screenshot({ path: path.join(OUT, `fullpage-artifact-${tag}.png`), fullPage: true });
  const rightAfterFullPage = await barWidths();
  await page.waitForTimeout(3000);
  report.fullPageArtifact = { settled, rightAfterFullPage, after3s: await barWidths() };

  // (a) hover the first (largest) bar
  const bar = widget.locator(".recharts-bar-rectangle path").first();
  await bar.hover();
  await page.waitForTimeout(600);
  report.a_hover = await tooltipState(widget);
  await widget.screenshot({ path: path.join(OUT, `a-hover-tooltip-${tag}.png`) });
  await page.mouse.move(0, 0);
  await page.waitForTimeout(600);
  report.a_afterMouseOut = await tooltipState(widget);

  // (b) keyboard: Tab from the top of the page until focus is inside the chart
  await page.evaluate(() => {
    document.activeElement && document.activeElement.blur();
    window.scrollTo(0, 0);
  });
  await page.locator("body").click({ position: { x: 1, y: 1 } }).catch(() => {});
  let tabs = 0;
  let focusInfo = null;
  for (; tabs < 120; tabs++) {
    await page.keyboard.press("Tab");
    focusInfo = await page.evaluate(() => {
      const a = document.activeElement;
      const w = document.querySelector('[data-testid="unbilled-wip-widget"]');
      return {
        inChart: !!(a && w && w.contains(a) && a.closest(".recharts-wrapper, svg.recharts-surface")),
        inWidget: !!(a && w && w.contains(a)),
        tag: a ? a.tagName : null,
        cls: a ? String(a.getAttribute("class")).slice(0, 80) : null,
        role: a ? a.getAttribute("role") : null,
        label: a ? (a.getAttribute("aria-label") || a.textContent || "").slice(0, 60) : null,
      };
    });
    if (focusInfo.inChart) break;
  }
  report.b_tabsToChart = tabs + 1;
  report.b_focused = focusInfo;
  await page.waitForTimeout(400);
  report.b_onFocus = await tooltipState(widget);
  await widget.screenshot({ path: path.join(OUT, `b-keyboard-focus-${tag}.png`) });
  await page.keyboard.press("ArrowRight");
  await page.waitForTimeout(400);
  report.b_arrowRight = await tooltipState(widget);
  await widget.screenshot({ path: path.join(OUT, `b-keyboard-arrowright-${tag}.png`) });
  await page.keyboard.press("ArrowRight");
  await page.waitForTimeout(400);
  report.b_arrowRight2 = await tooltipState(widget);
  await page.keyboard.press("ArrowLeft");
  await page.waitForTimeout(400);
  report.b_arrowLeft = await tooltipState(widget);
  await widget.screenshot({ path: path.join(OUT, `b-keyboard-arrowleft-${tag}.png`) });
  report.b_focusRing = await page.evaluate(() => {
    const a = document.activeElement;
    const cs = getComputedStyle(a);
    return { outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`, boxShadow: cs.boxShadow };
  });
  await page.keyboard.press("Escape");

  // (d) a11y tree before the table toggle
  const fig = page.getByRole("figure", { name: "Unbilled WIP" });
  report.d_figureNamedCount = await fig.count();
  const aria = await widget.ariaSnapshot();
  fs.writeFileSync(path.join(OUT, `d-aria-snapshot-${tag}.txt`), aria);
  report.d_srSummary = await widget.evaluate((el) => {
    const s = Array.from(el.querySelectorAll(".sr-only")).map((n) => n.textContent.trim()).filter(Boolean);
    return s;
  });

  // (c) View as table
  report.c_tablesBefore = await widget.locator("table").count();
  await widget.getByRole("button", { name: "View as table" }).click();
  await page.waitForTimeout(500);
  report.c_tablesAfter = await widget.locator("table").count();
  report.c_tablesInPage = await page.locator("main table").count();
  report.c_toggleLabelAfter = await widget.locator('[data-slot="wip-chart"] button').last().innerText();
  report.c_tableText = await widget.locator("table").first().innerText().catch(() => null);
  await widget.screenshot({ path: path.join(OUT, `c-view-as-table-${tag}.png`) });
  const aria2 = await widget.ariaSnapshot();
  fs.writeFileSync(path.join(OUT, `d-aria-snapshot-table-open-${tag}.txt`), aria2);
  report.interactiveConsole = messages;
  await context.close();
}

async function throttled(browser) {
  const { context, page, messages } = await newPage(browser);
  const cdp = await context.newCDPSession(page);
  await cdp.send("Network.enable");
  await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false,
    latency: 562.5,
    downloadThroughput: (1.6 * 1024 * 1024 * 0.9) / 8,
    uploadThroughput: (750 * 1024 * 0.9) / 8,
  });
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  const nav = page.goto(`${BASE}/org/${ORG}/dashboard`, { waitUntil: "commit", timeout: 180_000 });
  await nav;
  const widget = page.locator("#main-content").getByTestId("unbilled-wip-widget");
  const measure = () =>
    page.evaluate(() => {
      const w = document.querySelector('[data-testid="unbilled-wip-widget"]');
      if (!w) return null;
      const c = w.querySelector('[data-slot="wip-chart"]');
      const sk = w.querySelector('[data-slot="wip-chart-skeleton"]');
      const svg = w.querySelector("svg.recharts-surface");
      const cell = w.closest('[data-testid^="home-cell-"]');
      const r = (e) => (e ? Math.round(e.getBoundingClientRect().height * 10) / 10 : null);
      return {
        t: Math.round(performance.now()),
        skeleton: !!sk,
        chart: !!svg,
        widgetH: r(w),
        wipChartH: r(c),
        skeletonH: r(sk),
        cellH: r(cell),
        widgetTop: w ? Math.round(w.getBoundingClientRect().top + window.scrollY) : null,
        nextCellTop: (() => {
          const cells = Array.from(document.querySelectorAll('[data-testid^="home-cell-"]'));
          const i = cells.indexOf(cell);
          return cells[i + 1] ? Math.round(cells[i + 1].getBoundingClientRect().top + window.scrollY) : null;
        })(),
      };
    }).catch(() => null);
  let skeletonSample = null;
  const samples = [];
  for (let i = 0; i < 1200; i++) {
    const m = await measure();
    if (m) {
      samples.push(m);
      if (m.skeleton && !skeletonSample) {
        skeletonSample = m;
        await widget.screenshot({ path: path.join(OUT, `e-skeleton-throttled-1440-${theme}.png`) }).catch(() => {});
        await page.screenshot({ path: path.join(OUT, `e-skeleton-throttled-page-1440-${theme}.png`) }).catch(() => {});
      }
      if (m.chart) break;
    }
    await page.waitForTimeout(50);
  }
  await widget.locator("svg.recharts-surface").waitFor({ timeout: 180_000 });
  await page.waitForTimeout(4000);
  const loaded = await measure();
  await widget.screenshot({ path: path.join(OUT, `e-loaded-throttled-1440-${theme}.png`) });
  const shifts = await page.evaluate(() => window.__shifts);
  report.e = {
    skeletonSample,
    loaded,
    heightDelta: skeletonSample && loaded ? Math.round((loaded.widgetH - skeletonSample.widgetH) * 10) / 10 : null,
    wipChartDelta: skeletonSample && loaded ? Math.round((loaded.wipChartH - skeletonSample.wipChartH) * 10) / 10 : null,
    samplesWithSkeleton: samples.filter((s) => s.skeleton).length,
    firstAndLastSamples: [samples[0], samples[samples.length - 1]],
    layoutShifts: shifts,
    cls: shifts.filter((s) => !s.hadRecentInput).reduce((a, s) => a + s.value, 0),
  };
  report.throttledConsole = messages;
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  await context.close();
}

(async () => {
  const browser = await chromium.launch();
  try {
    await interactive(browser);
    if (width === 1440) await throttled(browser);
  } finally {
    await browser.close();
  }
  fs.writeFileSync(path.join(OUT, `report-${tag}.json`), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 1));
})().catch((e) => {
  console.error(e);
  fs.writeFileSync(path.join(OUT, `report-${tag}.json`), JSON.stringify({ ...report, error: String(e) }, null, 2));
  process.exit(1);
});
