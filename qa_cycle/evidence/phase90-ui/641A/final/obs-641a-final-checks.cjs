// 641A OBS final head (2a0610740): targeted checks for the review-fix commit.
//  1. Opened column on the saved view "OBS 641A view" at 1440, in three browser time zones
//     (Africa/Johannesburg, America/New_York, Pacific/Kiritimati = UTC+14, which puts a 15:08Z
//     instant on the NEXT calendar day, so browser-local formatting would show), vs the API
//     createdAt formatted in Africa/Johannesburg. Hydration warnings captured per context.
//  2. Export CSV on the saved view (SAST + Kiritimati) -> matters-export*.csv.
//  3. Filtered-empty state: screenshots 1024/1440 x light/dark, Clear filters colour vs background
//     contrast, hover underline, click restores the list.
// Usage: node obs-641a-final-checks.cjs <matters-api.json>   -> checks/*.png, final-checks.json
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-641A/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const LIST = `/org/${ORG}/matters`;
const OUT = __dirname;
const SHOTS = path.join(OUT, "checks");
fs.mkdirSync(SHOTS, { recursive: true });
const VIEWPORTS = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
const api = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const result = { startedAt: new Date().toISOString(), opened: {}, csv: {}, emptyState: {}, console: [], loads: [] };

const saDate = (iso) =>
  new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" });
const localDate = (iso, tz) =>
  new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: tz });

async function newContext(browser, w, theme, timezoneId) {
  const context = await browser.newContext({ viewport: VIEWPORTS[w], acceptDownloads: true, timezoneId });
  await context.addInitScript((t) => {
    try { localStorage.setItem("kazi-theme", t); } catch (e) {}
  }, theme);
  const page = await context.newPage();
  const ctx = `${w}-${theme}-${timezoneId}`;
  page.on("console", (m) => {
    const text = m.text();
    if (m.type() === "error" || m.type() === "warning" || /hydrat|did not match|mismatch/i.test(text))
      result.console.push({ ctx, type: m.type(), text: text.slice(0, 600), url: page.url() });
  });
  page.on("pageerror", (e) => result.console.push({ ctx, type: "pageerror", text: String(e).slice(0, 600), url: page.url() }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
  return { context, page, ctx };
}
async function settle(page) {
  await page.locator("#main-content h1").first().waitFor({ timeout: 30_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(600);
}
async function gotoList(page, ctx, qs = "") {
  await page.goto(`${BASE}${LIST}${qs}`, { waitUntil: "load" });
  await settle(page);
  result.loads.push({ ctx, url: `${LIST}${qs}` });
}
function save() {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "final-checks.json"), JSON.stringify(result, null, 2));
}

/** In-page: visible headers + per-row {name, opened} on the matters table. */
function readOpened() {
  const main = document.querySelector("#main-content");
  const vis = (el) => !!el && getComputedStyle(el).display !== "none" && el.getClientRects().length > 0;
  const t = main.querySelector('[data-testid="matters-table"]');
  const ths = [...t.querySelectorAll("thead th")];
  const idx = ths.findIndex((th) => th.textContent.trim() === "Opened");
  const rows = [...main.querySelectorAll('[data-testid="matter-row"]')];
  return {
    url: location.pathname + location.search,
    visibleHeaders: ths.filter(vis).map((th) => th.textContent.trim()),
    openedVisible: idx >= 0 && vis(ths[idx]),
    rows: rows.map((r) => ({
      id: (r.querySelector('[data-testid="matter-link"]')?.getAttribute("href") || "").split("/").pop(),
      name: r.querySelector('[data-testid="matter-link"]')?.textContent.trim(),
      opened: idx >= 0 ? r.querySelectorAll("td")[idx]?.innerText.trim() : null,
    })),
  };
}

async function openSavedView(page, ctx) {
  await gotoList(page, ctx);
  await page.locator('[data-testid="saved-view-selector"] [role="tab"]', { hasText: "OBS 641A view" }).click();
  await page.waitForURL(/view=[0-9a-f-]{36}/, { timeout: 30_000 });
  await settle(page);
  result.loads.push({ ctx, url: new URL(page.url()).pathname + new URL(page.url()).search });
}

async function exportCsv(page, file) {
  const [dl] = await Promise.all([
    page.waitForEvent("download", { timeout: 15_000 }),
    page.locator("#main-content").getByRole("button", { name: "Export CSV", exact: true }).click(),
  ]);
  const target = path.join(OUT, file);
  await dl.saveAs(target);
  const raw = fs.readFileSync(target, "utf8");
  return { file, suggestedFilename: dl.suggestedFilename(), bom: raw.charCodeAt(0) === 0xfeff, crlf: raw.includes("\r\n"), lines: raw.replace(/^﻿/, "").split("\r\n").filter(Boolean) };
}

/** In-page: Clear filters link colour + effective background (both via canvas → sRGB), contrast. */
function linkContrast() {
  const a = [...document.querySelectorAll("#main-content a")].find((x) => x.textContent.trim() === "Clear filters");
  if (!a) return null;
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgba = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "#000"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return [...cx.getImageData(0, 0, 1, 1).data]; };
  // Composite backgrounds from the root down to the link.
  const chain = []; for (let el = a; el; el = el.parentElement) chain.unshift(el);
  let bg = [255, 255, 255]; const layers = [];
  for (const el of chain) {
    const c = getComputedStyle(el).backgroundColor; const [r, g, b, al] = rgba(c);
    if (al > 0) { const f = al / 255; bg = [r * f + bg[0] * (1 - f), g * f + bg[1] * (1 - f), b * f + bg[2] * (1 - f)]; layers.push({ tag: el.tagName, cls: (el.className || "").toString().slice(0, 80), css: c }); }
  }
  const cs = getComputedStyle(a); const fg = rgba(cs.color).slice(0, 3);
  const L = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const l1 = L(fg), l2 = L(bg); const ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  const hex = (v) => "#" + v.map((x) => Math.round(x).toString(16).padStart(2, "0")).join("");
  return {
    className: a.className, href: a.getAttribute("href"), colorCss: cs.color, colorHex: hex(fg), bgHex: hex(bg), bgLayers: layers,
    contrast: Math.round(ratio * 100) / 100, fontSize: cs.fontSize, fontWeight: cs.fontWeight,
    textDecorationLine: cs.textDecorationLine, textUnderlineOffset: cs.textUnderlineOffset,
    html: document.documentElement.className,
  };
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const expected = Object.fromEntries(api.map((m) => [m.id, { name: m.name, createdAt: m.createdAt, sa: saDate(m.createdAt), ny: localDate(m.createdAt, "America/New_York"), kiri: localDate(m.createdAt, "Pacific/Kiritimati") }]));
  result.expected = expected;
  try {
    // ---------------- 1+2: Opened on the saved view, three zones
    for (const tz of ["Africa/Johannesburg", "America/New_York", "Pacific/Kiritimati"]) {
      const { context, page, ctx } = await newContext(browser, 1440, "light", tz);
      result.opened[tz] = { browserNow: await page.evaluate(() => ({ tz: Intl.DateTimeFormat().resolvedOptions().timeZone, offsetMin: -new Date().getTimezoneOffset() })) };
      await openSavedView(page, ctx);
      const st = await page.evaluate(readOpened);
      // Also a hard reload of the saved-view URL (pure SSR → hydrate path).
      await page.reload({ waitUntil: "load" }); await settle(page);
      result.loads.push({ ctx, url: "reload " + new URL(page.url()).pathname + new URL(page.url()).search });
      const st2 = await page.evaluate(readOpened);
      st.rows.forEach((r) => { const e = expected[r.id]; r.expectedSA = e?.sa; r.browserLocal = e ? localDate(e.createdAt, tz) : null; r.createdAt = e?.createdAt; r.matchesSA = !!e && r.opened === e.sa; });
      result.opened[tz].view = st; result.opened[tz].afterReloadSame = JSON.stringify(st2.rows.map((r) => r.opened)) === JSON.stringify(st.rows.map((r) => r.opened));
      await page.screenshot({ path: path.join(SHOTS, `saved-view-opened-1440-light-${tz.replace("/", "_")}.png`), fullPage: true });
      if (tz !== "America/New_York") result.csv[tz] = await exportCsv(page, tz === "Africa/Johannesburg" ? "matters-export.csv" : "matters-export-kiritimati.csv");
      // Default list load too (hydration check on the plain /matters path).
      await gotoList(page, ctx);
      await context.close();
    }
    save();

    // ---------------- 3: filtered-empty state, 1024/1440 x light/dark
    for (const w of [1024, 1440]) for (const theme of ["light", "dark"]) {
      const { context, page, ctx } = await newContext(browser, w, theme, "Africa/Johannesburg");
      await gotoList(page, ctx, "?search=zzqx-no-such-matter");
      const key = `${w}-${theme}`; const e = {};
      e.emptyText = await page.evaluate(() => { const p = [...document.querySelectorAll("#main-content p")].find((x) => /No matters match this view/.test(x.textContent)); return p ? p.parentElement.innerText.trim() : null; });
      e.tableRendered = await page.locator('[data-testid="matters-table"]').count();
      e.link = await page.evaluate(linkContrast);
      await page.screenshot({ path: path.join(SHOTS, `filtered-empty-${key}.png`), fullPage: true });
      const link = page.getByRole("link", { name: "Clear filters" });
      await link.hover(); await page.waitForTimeout(300);
      e.hover = await page.evaluate(() => { const a = [...document.querySelectorAll("#main-content a")].find((x) => x.textContent.trim() === "Clear filters"); const cs = getComputedStyle(a); return { textDecorationLine: cs.textDecorationLine, textUnderlineOffset: cs.textUnderlineOffset, color: cs.color }; });
      await page.screenshot({ path: path.join(SHOTS, `filtered-empty-hover-${key}.png`), fullPage: false });
      // Keyboard focus visibility
      await link.focus(); await page.waitForTimeout(200);
      e.focus = await page.evaluate(() => { const a = document.activeElement; const cs = getComputedStyle(a); return { text: a.textContent.trim(), outlineStyle: cs.outlineStyle, outlineWidth: cs.outlineWidth, boxShadow: cs.boxShadow.slice(0, 120) }; });
      await link.click();
      await page.waitForURL((u) => new URL(u).search === "", { timeout: 30_000 }); await settle(page);
      result.loads.push({ ctx, url: "after Clear filters click" });
      e.afterClick = await page.evaluate(() => ({ url: location.pathname + location.search, rows: document.querySelectorAll('[data-testid="matter-row"]').length, footer: document.querySelector('[data-testid="matters-footer"]')?.textContent.trim(), search: document.querySelector('[role="search"] input')?.value }));
      result.emptyState[key] = e;
      await context.close();
    }
  } finally {
    save();
    await browser.close();
  }
}
main().catch((e) => { result.fatal = String(e.stack || e); save(); console.error(e); process.exit(1); });
