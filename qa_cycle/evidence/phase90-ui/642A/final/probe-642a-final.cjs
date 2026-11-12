// 642A final-code (706919c0a) probe: narrow/wide layout of the default clients view, local
// horizontal scroll below the 42rem @container frame, sticky header from it, duplicate-table
// sampling during load, console capture.
// Usage: node probe-642a-final.cjs <label> [sticky-only]
//   label: pristine | rich   → final/probe-<label>.json, final/shots-<label>/*.png
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-642A/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const LIST = `/org/${ORG}/clients`;
const label = process.argv[2] || "pristine";
const stickyOnly = process.argv[3] === "sticky-only";
const OUT = __dirname;
const SHOTS = path.join(OUT, `shots-${label}`);
fs.mkdirSync(SHOTS, { recursive: true });
const result = { label, startedAt: new Date().toISOString(), layout: {}, dup: {}, sticky: {}, console: [] };
const save = () => {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, `probe-${label}${stickyOnly ? "-sticky" : ""}.json`), JSON.stringify(result, null, 2));
};

// Runs in every document from its start: samples the clients-table count every 50 ms for 5 s and
// also tracks the max via a MutationObserver (catches transients shorter than 50 ms).
function samplerInit() {
  const start = performance.now();
  const S = (window.__dupSamples = { samples: [], maxObserved: 0, maxAt: null, href: location.href });
  const count = () => document.querySelectorAll('[data-testid="clients-table"]').length;
  const note = (n) => { if (n > S.maxObserved) { S.maxObserved = n; S.maxAt = Math.round(performance.now() - start); } };
  const id = setInterval(() => {
    const n = count();
    S.samples.push([Math.round(performance.now() - start), n]);
    note(n);
    if (performance.now() - start > 5000) clearInterval(id);
  }, 50);
  const startMO = () => {
    const mo = new MutationObserver(() => note(count()));
    mo.observe(document.documentElement, { childList: true, subtree: true });
    setTimeout(() => mo.disconnect(), 5000);
  };
  if (document.documentElement) startMO(); else document.addEventListener("DOMContentLoaded", startMO);
}

async function newContext(browser, vp, theme) {
  const context = await browser.newContext({ viewport: vp });
  await context.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch (e) {} }, theme);
  await context.addInitScript(samplerInit);
  const page = await context.newPage();
  const ctx = `${vp.width}x${vp.height}-${theme}`;
  page.on("console", (m) => {
    const text = m.text();
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(text))
      result.console.push({ ctx, type: m.type(), text: text.slice(0, 300), url: page.url() });
  });
  page.on("pageerror", (e) => result.console.push({ ctx, type: "pageerror", text: String(e).slice(0, 300), url: page.url() }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption("user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
  return { context, page };
}

async function settle(page) {
  await page.locator('[data-testid="clients-table"]').first().waitFor({ timeout: 30_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(400);
}


const NAV = `a[href="/org/${ORG}/clients"]`;
async function openNavIfNeeded(page) {
  if (await page.locator(NAV).filter({ visible: true }).count()) return false;
  const trigger = page.getByRole("button", { name: /menu|navigation/i }).filter({ visible: true }).first();
  await trigger.click();
  await page.locator(NAV).filter({ visible: true }).first().waitFor({ timeout: 10_000 });
  await page.waitForTimeout(300);
  return true;
}
async function clickClientsNav(page) {
  await page.locator(NAV).filter({ visible: true }).first().click();
}

function measure() {
  const t = document.querySelector('[data-testid="clients-table"]');
  const sc = t.closest('[data-slot="table-container"]');
  const frame = sc.parentElement;
  const th = t.querySelector("thead th");
  const r = (el) => el.getBoundingClientRect();
  const thsVisible = [...t.querySelectorAll("thead th")].filter((x) => getComputedStyle(x).display !== "none" && x.getClientRects().length > 0);
  return {
    viewport: `${innerWidth}x${innerHeight}`,
    docScrollWidth: document.documentElement.scrollWidth,
    docClientWidth: document.documentElement.clientWidth,
    pageScrollsSideways: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    frame: { isContainer: String(frame.className).includes("@container"), width: Math.round(r(frame).width * 10) / 10, containerType: getComputedStyle(frame).containerType },
    container: { overflowX: getComputedStyle(sc).overflowX, width: Math.round(r(sc).width * 10) / 10, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollsInFrame: sc.scrollWidth > sc.clientWidth, scrollLeft: sc.scrollLeft },
    table: { width: Math.round(r(t).width * 10) / 10 },
    th: { position: getComputedStyle(th).position, top: getComputedStyle(th).top, zIndex: getComputedStyle(th).zIndex },
    visibleHeaders: thsVisible.map((x) => x.textContent.trim() || "(actions)"),
    rows: t.querySelectorAll('[data-testid="client-row"]').length,
    tablesInDocument: document.querySelectorAll('[data-testid="clients-table"]').length,
  };
}

/** After scrolling the container right: is the row-actions column on screen and hittable? */
function actionsReach() {
  const t = document.querySelector('[data-testid="clients-table"]');
  const sc = t.closest('[data-slot="table-container"]');
  const row = t.querySelector('[data-testid="client-row"]');
  const lastTd = row.querySelectorAll("td")[row.querySelectorAll("td").length - 1];
  const btn = lastTd.querySelector("button");
  const r = (el) => el.getBoundingClientRect();
  const br = btn ? r(btn) : null;
  const cr = r(sc);
  const hit = br ? document.elementFromPoint(br.left + br.width / 2, br.top + br.height / 2) : null;
  return {
    scrollLeft: sc.scrollLeft,
    maxScrollLeft: sc.scrollWidth - sc.clientWidth,
    button: br ? { left: Math.round(br.left), right: Math.round(br.right), top: Math.round(br.top), ariaLabel: btn.getAttribute("aria-label"), text: btn.textContent.trim() } : null,
    containerRect: { left: Math.round(cr.left), right: Math.round(cr.right) },
    insideContainer: br ? br.left >= cr.left - 0.5 && br.right <= cr.right + 0.5 : false,
    insideViewport: br ? br.left >= 0 && br.right <= innerWidth : false,
    hitIsButton: !!(hit && btn && (hit === btn || btn.contains(hit))),
    docScrollWidth: document.documentElement.scrollWidth,
  };
}

function stickyProbe() {
  const t = document.querySelector('[data-testid="clients-table"]');
  const th = t.querySelector("thead th");
  const hdr = document.querySelector("header");
  const sc = t.closest('[data-slot="table-container"]');
  const r = (el) => el.getBoundingClientRect();
  const hr = r(th);
  const hit = document.elementFromPoint(Math.round(hr.left + 20), Math.round(hr.top + hr.height / 2));
  return {
    thPosition: getComputedStyle(th).position,
    thTopCss: getComputedStyle(th).top,
    thTop: Math.round(hr.top * 10) / 10,
    thBottom: Math.round(hr.bottom * 10) / 10,
    appHeaderBottom: hdr ? Math.round(r(hdr).bottom * 10) / 10 : null,
    headerCoveredByAppHeader: hdr ? r(hdr).bottom > hr.top + 0.5 : null,
    hitInThead: !!(hit && hit.closest("thead")),
    firstRowTop: Math.round(r(t.querySelector("tbody tr")).top),
    scrollY: Math.round(scrollY),
    maxScrollY: document.documentElement.scrollHeight - innerHeight,
    docScrollWidth: document.documentElement.scrollWidth,
    docClientWidth: document.documentElement.clientWidth,
    container: { overflowX: getComputedStyle(sc).overflowX, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth },
  };
}

(async () => {
  const browser = await chromium.launch();
  try {
    if (!stickyOnly) {
      for (const [w, h] of [[390, 844], [768, 1024], [1024, 768], [1440, 900]]) {
        for (const theme of ["light", "dark"]) {
          const key = `${w}x${h}-${theme}`;
          const x = await newContext(browser, { width: w, height: h }, theme);
          const L = {};
          // Full document load of the list (sampler starts at document start).
          await x.page.goto(`${BASE}${LIST}`, { waitUntil: "commit" });
          await settle(x.page);
          L.measure = await x.page.evaluate(measure);
          await x.page.screenshot({ path: path.join(SHOTS, `default-${key}.png`), fullPage: true });
          if (L.measure.container.scrollsInFrame) {
            await x.page.evaluate(() => { const sc = document.querySelector('[data-testid="clients-table"]').closest('[data-slot="table-container"]'); sc.scrollLeft = sc.scrollWidth; });
            await x.page.waitForTimeout(300);
            L.scrolledRight = await x.page.evaluate(actionsReach);
            await x.page.screenshot({ path: path.join(SHOTS, `scrolled-right-${key}.png`), fullPage: false });
            // Open the row menu from the scrolled position (real click).
            const btn = x.page.locator('[data-testid="client-row"]').first().locator("td").last().locator("button");
            await btn.click();
            const menu = x.page.getByRole("menu");
            await menu.waitFor({ timeout: 10_000 });
            L.rowMenuItems = await menu.getByRole("menuitem").allTextContents();
            await x.page.screenshot({ path: path.join(SHOTS, `row-menu-scrolled-${key}.png`), fullPage: false });
            await x.page.keyboard.press("Escape");
            L.urlAfterMenu = new URL(x.page.url()).pathname;
          }
          await x.page.waitForTimeout(Math.max(0, 5300 - 1000));
          L.dupFullLoad = await x.page.evaluate(() => ({ maxObserved: window.__dupSamples.maxObserved, maxAt: window.__dupSamples.maxAt, nSamples: window.__dupSamples.samples.length, countsSeen: [...new Set(window.__dupSamples.samples.map((s) => s[1]))] }));
          // Client-side navigation dashboard → clients (sampler started in-page right before the click).
          await x.page.goto(`${BASE}/org/${ORG}/dashboard`, { waitUntil: "load" });
          await x.page.waitForLoadState("networkidle").catch(() => {});
          L.navOpenedMobileMenu = await openNavIfNeeded(x.page);
          await x.page.evaluate(() => {
            const start = performance.now();
            const S = (window.__spaDup = { samples: [], maxObserved: 0, maxAt: null });
            const count = () => document.querySelectorAll('[data-testid="clients-table"]').length;
            const note = (n) => { if (n > S.maxObserved) { S.maxObserved = n; S.maxAt = Math.round(performance.now() - start); } };
            const id = setInterval(() => { const n = count(); S.samples.push([Math.round(performance.now() - start), n]); note(n); if (performance.now() - start > 5000) clearInterval(id); }, 50);
            const mo = new MutationObserver(() => note(count()));
            mo.observe(document.documentElement, { childList: true, subtree: true });
            setTimeout(() => mo.disconnect(), 5000);
          });
          await clickClientsNav(x.page);
          await x.page.waitForURL(`**${LIST}`, { timeout: 30_000 });
          await settle(x.page);
          await x.page.waitForTimeout(5000);
          L.dupClientNav = await x.page.evaluate(() => ({ maxObserved: window.__spaDup.maxObserved, maxAt: window.__spaDup.maxAt, nSamples: window.__spaDup.samples.length, countsSeen: [...new Set(window.__spaDup.samples.map((s) => s[1]))] }));
          L.measureAfterClientNav = await x.page.evaluate(measure);
          result.layout[key] = L;
          await x.context.close();
          save();
        }
      }
    }

    // Sticky scroll (window scroller) at 1440x500 and 1024x500.
    for (const [w, h] of [[1440, 500], [1024, 500]]) {
      for (const theme of ["light", "dark"]) {
        const key = `${w}x${h}-${theme}`;
        const x = await newContext(browser, { width: w, height: h }, theme);
        await x.page.goto(`${BASE}${LIST}`, { waitUntil: "load" });
        await settle(x.page);
        const S = { top: await x.page.evaluate(stickyProbe) };
        await x.page.screenshot({ path: path.join(SHOTS, `sticky-top-${key}.png`), fullPage: false });
        for (const dy of [150, 300, 600]) {
          await x.page.evaluate((d) => window.scrollTo(0, d), dy);
          await x.page.waitForTimeout(300);
          S[`scrolled${dy}`] = await x.page.evaluate(stickyProbe);
        }
        await x.page.screenshot({ path: path.join(SHOTS, `sticky-scrolled-${key}.png`), fullPage: false });
        result.sticky[key] = S;
        await x.context.close();
        save();
      }
    }
  } finally {
    save();
    await browser.close();
  }
})().catch((e) => { console.error(e); save(); process.exit(1); });
