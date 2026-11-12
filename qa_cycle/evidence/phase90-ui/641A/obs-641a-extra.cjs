// 641A OBS "after" — task 641.6 extra checks on the DataTable build (rich dataset, after
// obs-641a-after.cjs after-rich 1). Column menu + reset on navigation, Export CSV (download
// captured), keyboard, pagination absence, row click / client link / row menu, empty variants,
// sticky-header absence, layout measurements and screenshots at 1024/1440 × light/dark, Carol CSV.
// Usage: node obs-641a-extra.cjs   → checks/*.png, extra.json, matters-export*.csv
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
const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
const result = { startedAt: new Date().toISOString(), checks: {}, layout: {}, console: [] };

async function newContext(browser, w, theme, user = "alice") {
  const context = await browser.newContext({ viewport: VIEWPORTS[w], acceptDownloads: true });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  const ctx = `${user}-${w}-${theme}`;
  page.on("console", (m) => {
    const text = m.text();
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(text))
      result.console.push({ ctx, type: m.type(), text: text.slice(0, 300), url: page.url() });
  });
  page.on("pageerror", (e) => result.console.push({ ctx, type: "pageerror", text: String(e).slice(0, 300), url: page.url() }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
  return { context, page };
}

const rel = (u) => { const x = new URL(u); return x.pathname + x.search; };
async function settle(page, urlPred) {
  if (urlPred) await page.waitForURL(urlPred, { timeout: 30_000 });
  await page.locator("#main-content h1").first().waitFor({ timeout: 30_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(400);
}
async function gotoList(page, qs = "") {
  await page.goto(`${BASE}${LIST}${qs}`, { waitUntil: "load" });
  await settle(page);
}
const shot = async (page, name, full = true) => {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: full });
  return `checks/${name}.png`;
};
function save() {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "extra.json"), JSON.stringify(result, null, 2));
}

/** In-page: headers (visible), body cell texts, controls, pagination markers. */
function state() {
  const main = document.querySelector("#main-content");
  const vis = (el) => !!el && getComputedStyle(el).display !== "none" && el.getClientRects().length > 0;
  const t = main.querySelector('[data-testid="matters-table"]');
  const ths = t ? [...t.querySelectorAll("thead th")].filter(vis) : [];
  const rows = [...main.querySelectorAll('[data-testid="matter-row"]')];
  const btn = (name) => [...main.querySelectorAll("button")].find((b) => b.textContent.trim() === name);
  return {
    url: location.pathname + location.search,
    headers: ths.map((th) => th.textContent.trim()),
    rows: rows.map((r) => [...r.querySelectorAll("td")].filter(vis).map((td) => td.innerText.trim().replace(/\s+/g, " "))),
    columnsButton: !!btn("Columns"),
    exportButton: !!btn("Export CSV"),
    pagination: {
      rowsPerPage: /Rows per page/.test(main.innerText),
      pageXofY: /Page \d+ of \d+/.test(main.innerText),
      prevNext: !!main.querySelector('[aria-label="Go to previous page"],[aria-label="Go to next page"]'),
    },
    footer: (main.querySelector('[data-testid="matters-footer"]') || {}).textContent || null,
    emptyText: (() => { const p = [...main.querySelectorAll("p")].find((x) => /No matters match this view/.test(x.textContent)); return p ? p.parentElement.innerText.trim() : null; })(),
    clearFiltersHref: (() => { const a = [...main.querySelectorAll("a")].find((x) => x.textContent.trim() === "Clear filters"); return a ? a.getAttribute("href") : null; })(),
    hasTable: !!t,
  };
}

/** In-page: layout probes (overflow, toolbar wrap, header alignment, borders, dark surfaces, alignment). */
function layout() {
  const main = document.querySelector("#main-content");
  const t = main.querySelector('[data-testid="matters-table"]');
  const sc = t && t.closest('[data-slot="table-container"]');
  const frame = t && t.closest(".\\@container");
  const dt = main.querySelector('[data-slot="data-table"]');
  const h1 = main.querySelector("h1");
  const cs = (el) => (el ? getComputedStyle(el) : null);
  const r = (el) => (el ? el.getBoundingClientRect() : null);
  // toolbar: the flex-wrap row holding the search box
  const search = main.querySelector('[role="search"]');
  const row = search && search.closest(".flex-wrap");
  const rowKids = row ? [...row.children].map((c) => ({ tag: c.tagName, text: c.innerText.trim().slice(0, 40), top: Math.round(r(c).top), left: Math.round(r(c).left), right: Math.round(r(c).right) })) : [];
  const ths = t ? [...t.querySelectorAll("thead th")].filter((x) => cs(x).display !== "none") : [];
  const firstRow = t && t.querySelector('tbody tr');
  const tds = firstRow ? [...firstRow.querySelectorAll("td")].filter((x) => cs(x).display !== "none") : [];
  const align = ths.map((th, i) => ({ header: th.textContent.trim(), thAlign: cs(th).textAlign, tdAlign: tds[i] ? cs(tds[i]).textAlign : null, thLeft: Math.round(r(th).left), tdLeft: tds[i] ? Math.round(r(tds[i]).left) : null }));
  const bg = (el) => (el ? cs(el).backgroundColor : null);
  const whiteSurfaces = [...main.querySelectorAll("*")].filter((el) => {
    const b = cs(el).backgroundColor;
    return (b === "rgb(255, 255, 255)" || b === "oklch(1 0 0)") && el.getClientRects().length > 0 && r(el).width > 20;
  }).slice(0, 10).map((el) => `${el.tagName.toLowerCase()}.${String(el.className).slice(0, 80)}`);
  return {
    htmlClass: document.documentElement.className,
    viewport: innerWidth,
    docScrollWidth: document.documentElement.scrollWidth,
    table: t ? { tableWidth: Math.round(r(t).width * 10) / 10, containerClientWidth: sc.clientWidth, containerScrollWidth: sc.scrollWidth, overflowX: cs(sc).overflowX, horizontallyScrollable: sc.scrollWidth > sc.clientWidth, frameWidth: Math.round(r(frame).width) } : null,
    borders: frame ? { frameBorder: cs(frame).borderTopWidth + " " + cs(frame).borderTopStyle, frameRadius: cs(frame).borderTopLeftRadius, containerBorder: cs(sc).borderTopWidth + " " + cs(sc).borderTopStyle, tableBorder: cs(t).borderTopWidth + " " + cs(t).borderTopStyle } : null,
    alignment: { h1Left: h1 ? Math.round(r(h1).left) : null, dataTableLeft: dt ? Math.round(r(dt).left) : null, frameLeft: frame ? Math.round(r(frame).left) : null, frameRight: frame ? Math.round(r(frame).right) : null, searchLeft: search ? Math.round(r(search).left) : null, newMatterRight: (() => { const b = [...main.querySelectorAll("button")].find((x) => x.textContent.trim() === "New Matter"); return b ? Math.round(r(b).right) : null; })() },
    toolbarRow: { children: rowKids, distinctTops: [...new Set(rowKids.map((k) => k.top))].length },
    headerCells: align,
    colors: { body: bg(document.body), frame: bg(frame), thead: t ? bg(t.querySelector("thead")) : null, th: ths[0] ? bg(ths[0]) : null, thColor: ths[0] ? cs(ths[0]).color : null, row: firstRow ? bg(firstRow) : null, td: tds[1] ? cs(tds[1]).color : null, columnsBtn: (() => { const b = [...main.querySelectorAll("button")].find((x) => x.textContent.trim() === "Columns"); return b ? [bg(b), cs(b).color, cs(b).borderTopColor] : null; })() },
    whiteSurfacesInMain: whiteSurfaces,
  };
}

async function clickColumnsMenu(page) {
  await page.locator("#main-content").getByRole("button", { name: "Columns", exact: true }).click();
  const menu = page.getByRole("menu");
  await menu.waitFor({ timeout: 10_000 });
  await page.waitForTimeout(200);
  return menu;
}

async function exportCsv(page, file) {
  const [dl] = await Promise.all([
    page.waitForEvent("download", { timeout: 15_000 }),
    page.locator("#main-content").getByRole("button", { name: "Export CSV", exact: true }).click(),
  ]);
  const target = path.join(OUT, file);
  await dl.saveAs(target);
  const raw = fs.readFileSync(target, "utf8");
  return { file, suggestedFilename: dl.suggestedFilename(), bom: raw.charCodeAt(0) === 0xfeff, lines: raw.replace(/^﻿/, "").split("\r\n").filter(Boolean) };
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  try {
    // ---------------------------------------------------------------- 1440 light: behaviour checks
    const { context, page } = await newContext(browser, 1440, "light");
    const c = result.checks;
    await gotoList(page);
    c.default = await page.evaluate(state);

    // Column menu: items + checked state (priority-hidden columns included)
    let menu = await clickColumnsMenu(page);
    c.columnMenu = { items: await menu.evaluate((m) => [...m.querySelectorAll('[role="menuitemcheckbox"]')].map((i) => ({ label: i.textContent.trim(), checked: i.getAttribute("aria-checked") }))), label: await menu.evaluate((m) => (m.querySelector('[data-slot="dropdown-menu-label"]') || {}).textContent || null) };
    c.columnMenu.screenshot = await shot(page, "colmenu-open-1440-light", false);
    await menu.getByRole("menuitemcheckbox", { name: "Client" }).click();
    await page.waitForTimeout(300);
    c.columnMenu.stillOpenAfterToggle = await page.getByRole("menu").isVisible();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
    c.columnMenu.afterHideClient = await page.evaluate(state);
    c.columnMenu.afterHideClientScreenshot = await shot(page, "colmenu-client-hidden-1440-light");
    c.csvClientHidden = await exportCsv(page, "matters-export-client-hidden.csv");
    // navigate via a sort link → visibility resets
    const href = await page.locator('[data-testid="matters-table"] thead').getByRole("link", { name: "Matter", exact: true }).getAttribute("href");
    await page.locator('[data-testid="matters-table"] thead').getByRole("link", { name: "Matter", exact: true }).click();
    await settle(page, (u) => rel(u.toString()) === href);
    c.columnMenu.afterSortNav = await page.evaluate(state);
    c.columnMenu.afterSortNavScreenshot = await shot(page, "colmenu-reset-after-sort-1440-light");
    // hide again, then navigate via a status chip → resets
    menu = await clickColumnsMenu(page);
    await menu.getByRole("menuitemcheckbox", { name: "Trust" }).click();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
    c.columnMenu.afterHideTrust = (await page.evaluate(state)).headers;
    await page.locator('[data-testid="project-status-filter"]').getByRole("link", { name: "All", exact: true }).click();
    await settle(page, (u) => new URL(u.toString()).searchParams.get("status") === "ALL");
    c.columnMenu.afterChipNav = (await page.evaluate(state)).headers;
    c.columnMenu.afterChipNavUrl = rel(page.url());

    // Export CSV at the default list (1440: Work type + Opened priority-hidden on screen)
    await gotoList(page);
    c.csv1440 = await exportCsv(page, "matters-export.csv");
    c.csv1440.screenHeaders = (await page.evaluate(state)).headers;

    // Keyboard: Tab from search to the first header link + Enter (parity step 8 already), and the
    // controls: focus Columns via keyboard and open with Enter
    await page.locator('[role="search"] input').focus();
    const seq = [];
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => ({ tag: document.activeElement.tagName, text: document.activeElement.textContent.trim().slice(0, 30), href: document.activeElement.getAttribute("href"), inThead: !!document.activeElement.closest("thead") }));
      seq.push(f);
      if (f.inThead) break;
    }
    c.keyboard = { tabSequenceFromSearch: seq };
    const focused = seq[seq.length - 1];
    c.keyboard.focusShot = await shot(page, "keyboard-focus-sort-link-1440-light", false);
    await page.keyboard.press("Enter");
    await settle(page, (u) => rel(u.toString()) === focused.href);
    c.keyboard.afterEnter = rel(page.url());
    await gotoList(page);
    await page.locator("#main-content").getByRole("button", { name: "Columns", exact: true }).focus();
    await page.keyboard.press("Enter");
    c.keyboard.columnsMenuOpensWithEnter = await page.getByRole("menu").isVisible({ timeout: 3000 }).catch(() => false);
    await page.keyboard.press("Escape");

    // Pagination: no control
    c.pagination = c.default.pagination;

    // Row click on non-link cell (Trust cell), client link, row actions menu
    await gotoList(page);
    const row = page.locator('[data-testid="matter-row"]').nth(1);
    const matterHref = await row.locator('[data-testid="matter-link"]').getAttribute("href");
    const clientHref = await row.locator('a[href*="/clients/"]').first().getAttribute("href");
    const trustCell = row.locator("td").nth(-2); // second-to-last visible? use explicit text probe below
    // pick the Unbilled cell by header index
    const hdrs = (await page.evaluate(state)).headers;
    const unbilledIdx = hdrs.indexOf("Unbilled");
    const cell = row.locator("td:visible").nth(unbilledIdx);
    c.rowClick = { cellText: (await cell.innerText()).trim(), expected: matterHref };
    await cell.click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(matterHref), { timeout: 15_000 }).then(() => (c.rowClick.navigates = true)).catch(() => (c.rowClick.navigates = false));
    c.rowClick.urlAfter = rel(page.url());
    await gotoList(page);
    await page.locator('[data-testid="matter-row"]').nth(1).locator('a[href*="/clients/"]').first().click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(clientHref), { timeout: 15_000 }).catch(() => {});
    c.clientLink = { expected: clientHref, urlAfter: rel(page.url()), ok: rel(page.url()).startsWith(clientHref) };
    await gotoList(page);
    const before = rel(page.url());
    await page.locator('[data-testid="matter-row"]').nth(1).locator('[data-testid="matter-row-actions-trigger"]').click();
    const rm = page.getByRole("menu");
    await rm.waitFor({ timeout: 10_000 });
    await page.waitForTimeout(1000);
    c.rowMenu = { items: await rm.getByRole("menuitem").allInnerTexts(), urlAfterOpen: rel(page.url()), rowDidNotNavigate: rel(page.url()) === before };
    c.rowMenu.screenshot = await shot(page, "row-menu-open-1440-light", false);
    await rm.getByRole("menuitem", { name: "Open client" }).click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(clientHref), { timeout: 15_000 }).catch(() => {});
    c.rowMenu.openClientUrl = rel(page.url());
    await gotoList(page);
    await page.locator('[data-testid="matter-row"]').nth(1).locator('[data-testid="matter-row-actions-trigger"]').click();
    await page.getByRole("menu").getByRole("menuitem", { name: "Open matter" }).click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(matterHref), { timeout: 15_000 }).catch(() => {});
    c.rowMenu.openMatterUrl = rel(page.url());

    // Empty variants
    await gotoList(page, "?search=zzqx-no-such-matter");
    c.emptyFiltered = await page.evaluate(state);
    await page.getByRole("link", { name: "Clear filters" }).click();
    await settle(page, (u) => rel(u.toString()) === LIST);
    c.emptyFiltered.afterClear = { url: rel(page.url()), rows: (await page.evaluate(state)).rows.length };
    await gotoList(page, "?view=system%3Adormant");
    c.emptyDormant = await page.evaluate(state);
    c.emptyDormant.screenshot = await shot(page, "empty-dormant-1440-light");

    // Sticky header: deliberately off — scroll the page and see whether the header moves with it
    await page.setViewportSize({ width: 1440, height: 500 });
    await gotoList(page);
    const th0 = () => page.evaluate(() => { const th = document.querySelector('[data-testid="matters-table"] thead th'); return { top: Math.round(th.getBoundingClientRect().top), position: getComputedStyle(th).position }; });
    const s0 = await th0();
    await page.evaluate(() => window.scrollBy(0, 300));
    await page.waitForTimeout(300);
    const s1 = await th0();
    c.sticky = { before: s0, afterScroll300: s1, scrollY: await page.evaluate(() => scrollY), sticks: s1.top >= 0 && s1.top === s0.top };
    c.sticky.screenshot = await shot(page, "sticky-scrolled-1440x500-light", false);
    await context.close();
    save();

    // ---------------------------------------------------------------- layout × width × theme
    for (const w of [1024, 1440]) {
      for (const theme of ["light", "dark"]) {
        const x = await newContext(browser, w, theme);
        const key = `${w}-${theme}`;
        const L = {};
        await gotoList(x.page);
        L.default = await x.page.evaluate(layout);
        L.defaultShot = await shot(x.page, `default-${key}`);
        await gotoList(x.page, "?sort=unbilled&dir=desc");
        L.sorted = { url: rel(x.page.url()), headers: (await x.page.evaluate(state)).headers, layout: await x.page.evaluate(layout) };
        L.sortedShot = await shot(x.page, `sorted-unbilled-desc-${key}`);
        await gotoList(x.page);
        await clickColumnsMenu(x.page);
        L.colMenuShot = await shot(x.page, `colmenu-open-${key}`, false);
        L.colMenuBox = await x.page.getByRole("menu").evaluate((m) => { const r = m.getBoundingClientRect(); return { top: Math.round(r.top), bottom: Math.round(r.bottom), left: Math.round(r.left), right: Math.round(r.right), bg: getComputedStyle(m).backgroundColor }; });
        await x.page.keyboard.press("Escape");
        await gotoList(x.page, "?search=zzqx-no-such-matter");
        L.filteredEmpty = { state: await x.page.evaluate(state), layout: await x.page.evaluate(layout) };
        L.filteredEmptyShot = await shot(x.page, `filtered-empty-${key}`);
        if (w === 1024 && theme === "light") {
          await gotoList(x.page);
          result.checks.csv1024 = await exportCsv(x.page, "matters-export-1024.csv");
          result.checks.csv1024.screenHeaders = (await x.page.evaluate(state)).headers;
          // scrolled sideways: what is clipped at 1024
          await x.page.evaluate(() => { const sc = document.querySelector('[data-slot="table-container"]'); sc.scrollLeft = sc.scrollWidth; });
          L.scrolledRightShot = await shot(x.page, `default-scrolled-right-${key}`);
        }
        result.layout[key] = L;
        await x.context.close();
        save();
      }
    }

    // ---------------------------------------------------------------- Carol (member): redaction
    const cc = await newContext(browser, 1440, "light", "carol");
    await gotoList(cc.page);
    result.checks.carol = await cc.page.evaluate(state);
    result.checks.carol.screenshot = await shot(cc.page, "carol-default-1440-light");
    if (result.checks.carol.exportButton) result.checks.carolCsv = await exportCsv(cc.page, "matters-export-carol.csv");
    await cc.context.close();
  } finally {
    save();
    await browser.close();
  }
}

main().catch((e) => {
  result.fatal = String(e && e.stack ? e.stack : e);
  save();
  console.error(e);
  process.exit(1);
});
