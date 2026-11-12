// 642A OBS "after" — task 642.5 checks on the ClientsDataTable build (rich dataset, run after
// `obs-642a-after.cjs after-rich 1`, so "OBS 642A view" exists). Adapted from 641A obs-641a-extra.cjs.
// Columns menu (+ reset on navigation), Export CSV (download captured), sticky header (OBS-CARRY 639C),
// two-line headers + accessible names, Client column width / truncation, keyboard, row interactions,
// empty variants, SA-calendar dates in 3 browser time zones, Carol, console.
// Usage: node obs-642a-extra.cjs   → checks/*.png, extra.json, clients-export*.csv
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-642A/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const LIST = `/org/${ORG}/clients`;
const OUT = __dirname;
const SHOTS = path.join(OUT, "checks");
fs.mkdirSync(SHOTS, { recursive: true });
const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
const VIEW_NAME = "OBS 642A view";
const result = { startedAt: new Date().toISOString(), checks: {}, sticky: {}, layout: {}, dates: {}, console: [] };

async function newContext(browser, vp, theme, user = "alice", timezoneId) {
  const context = await browser.newContext({ viewport: vp, acceptDownloads: true, ...(timezoneId ? { timezoneId } : {}) });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  const ctx = `${user}-${vp.width}x${vp.height}-${theme}${timezoneId ? "-" + timezoneId : ""}`;
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
async function gotoSavedView(page) {
  await gotoList(page);
  await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: VIEW_NAME, exact: true }).click();
  await settle(page, (u) => new URL(u.toString()).searchParams.has("view"));
}
const shot = async (page, name, full = true) => {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: full });
  return `checks/${name}.png`;
};
function save() {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "extra.json"), JSON.stringify(result, null, 2));
}

/** In-page: visible headers, visible body cells, controls, empty/footer. */
function state() {
  const main = document.querySelector("#main-content");
  const vis = (el) => !!el && getComputedStyle(el).display !== "none" && el.getClientRects().length > 0;
  const t = main.querySelector('[data-testid="clients-table"]');
  const ths = t ? [...t.querySelectorAll("thead th")].filter(vis) : [];
  const rows = [...main.querySelectorAll('[data-testid="client-row"]')];
  const btn = (name) => [...main.querySelectorAll("button")].find((b) => b.textContent.trim() === name);
  return {
    url: location.pathname + location.search,
    headers: ths.map((th) => th.textContent.replace(/\s+/g, " ").trim()),
    rows: rows.map((r) => [...r.querySelectorAll("td")].filter(vis).map((td) => td.innerText.trim().replace(/\s+/g, " "))),
    columnsButton: !!btn("Columns"),
    exportButton: !!btn("Export CSV"),
    pagination: {
      rowsPerPage: /Rows per page/.test(main.innerText),
      pageXofY: /Page \d+ of \d+/.test(main.innerText),
      prevNext: !!main.querySelector('[aria-label="Go to previous page"],[aria-label="Go to next page"]'),
    },
    footer: (main.querySelector('[data-testid="clients-footer"]') || {}).textContent || null,
    emptyText: (() => { const p = [...main.querySelectorAll("p")].find((x) => /No clients match this view/.test(x.textContent)); return p ? p.parentElement.innerText.trim() : null; })(),
    emptyStateTitle: (() => { const h = [...main.querySelectorAll("h2,h3")].find((x) => /complete|No clients yet/.test(x.textContent)); return h ? h.textContent.trim() : null; })(),
    clearFiltersHref: (() => { const a = [...main.querySelectorAll("a")].find((x) => x.textContent.trim() === "Clear filters"); return a ? a.getAttribute("href") : null; })(),
    toolbar: { search: !!main.querySelector('[role="search"]'), lifecycle: !!main.querySelector('[data-testid="client-lifecycle-filter"]'), showIncomplete: !!main.querySelector('[data-testid="client-show-incomplete"]'), views: !!main.querySelector('[data-testid="saved-view-selector"]') },
    hasTable: !!t,
  };
}

/** In-page: Client column + header measurements (width, truncation, two-line headers, tag clipping). */
function clientColumn() {
  const t = document.querySelector('[data-testid="clients-table"]');
  if (!t) return null;
  const cs = (el) => getComputedStyle(el);
  const r = (el) => el.getBoundingClientRect();
  const ths = [...t.querySelectorAll("thead th")].filter((x) => cs(x).display !== "none");
  const links = [...t.querySelectorAll('[data-testid="client-link"]')];
  return {
    headers: ths.map((th) => {
      const two = th.querySelector("span.inline-flex.flex-col");
      const lines = two ? [...two.children].map((s) => ({ text: s.textContent, top: Math.round(r(s).top) })) : null;
      return { text: th.textContent.replace(/\s+/g, " ").trim(), width: Math.round(r(th).width), left: Math.round(r(th).left), twoLine: lines ? lines.length === 2 && lines[0].top !== lines[1].top : false, lines, height: Math.round(r(th).height) };
    }),
    clientTh: (() => { const th = ths[0]; return { width: Math.round(r(th).width), className: th.className }; })(),
    links: links.map((a) => ({ text: a.textContent, title: a.getAttribute("title"), width: Math.round(r(a).width), clientWidth: a.clientWidth, scrollWidth: a.scrollWidth, truncated: a.scrollWidth > a.clientWidth, visibleChars: a.scrollWidth > 0 ? Math.round((a.clientWidth / a.scrollWidth) * a.textContent.length) : 0 })),
    tagRows: [...t.querySelectorAll('[data-testid="client-row"]')].map((row) => {
      const type = row.querySelector('[data-testid="client-type"]');
      const box = type ? type.parentElement : null;
      const td = row.querySelector("td");
      const badges = box ? [...box.querySelectorAll('[data-slot="badge"]')] : [];
      const tdR = r(td);
      return { name: row.querySelector('[data-testid="client-link"]').textContent, chips: badges.map((b) => ({ text: b.textContent.trim(), fullyVisible: r(b).right <= tdR.right - 1 && r(b).width > 0, right: Math.round(r(b).right) })), tdRight: Math.round(tdR.right) };
    }),
  };
}

function stickyProbe() {
  const t = document.querySelector('[data-testid="clients-table"]');
  const th = t.querySelector("thead th");
  const hdr = document.querySelector("header");
  const sc = t.closest('[data-slot="table-container"]');
  const r = (el) => el.getBoundingClientRect();
  const hr = r(th);
  // What is painted at the header row's centre: the th itself (not covered by the app header)?
  const probeY = Math.round(hr.top + hr.height / 2);
  const hit = document.elementFromPoint(Math.round(hr.left + 20), probeY);
  let scroller = "window";
  for (let n = t.parentElement; n && n !== document.body; n = n.parentElement) {
    const oy = getComputedStyle(n).overflowY;
    if ((oy === "auto" || oy === "scroll") && n.scrollHeight > n.clientHeight) { scroller = `${n.tagName.toLowerCase()}#${n.id}.${String(n.className).slice(0, 60)}`; break; }
  }
  return {
    thPosition: getComputedStyle(th).position,
    thTopCss: getComputedStyle(th).top,
    thTop: Math.round(hr.top * 10) / 10,
    thBottom: Math.round(hr.bottom * 10) / 10,
    appHeaderBottom: hdr ? Math.round(r(hdr).bottom * 10) / 10 : null,
    appHeaderPosition: hdr ? getComputedStyle(hdr).position : null,
    headerCoveredByAppHeader: hdr ? r(hdr).bottom > hr.top + 0.5 : null,
    hitAtHeaderCentre: hit ? `${hit.tagName.toLowerCase()}${hit.closest("thead") ? " (in thead)" : hit.closest("header") ? " (in app header)" : ""}` : null,
    hitInThead: !!(hit && hit.closest("thead")),
    firstRowTop: Math.round(r(t.querySelector("tbody tr")).top),
    scrollY: Math.round(scrollY),
    scroller,
    docScrollWidth: document.documentElement.scrollWidth,
    docClientWidth: document.documentElement.clientWidth,
    pageScrollsSideways: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    container: sc ? { overflowX: getComputedStyle(sc).overflowX, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollsSideways: sc.scrollWidth > sc.clientWidth, scrollLeft: sc.scrollLeft } : null,
    docScrollHeight: document.documentElement.scrollHeight,
    viewportH: innerHeight,
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
  return { file, suggestedFilename: dl.suggestedFilename(), bom: raw.charCodeAt(0) === 0xfeff, crlf: raw.includes("\r\n"), lines: raw.replace(/^﻿/, "").split(/\r?\n/).filter(Boolean) };
}

async function apiClients() {
  const tok = await (await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: USERS.alice, orgId: ORG, orgSlug: ORG, orgRole: "owner" }) })).json();
  const res = await fetch(`${BACKEND}/api/clients`, { headers: { Authorization: `Bearer ${tok.access_token}` } });
  return (await res.json()).map((c) => ({ name: c.name, createdAt: c.createdAt, ficaLastVerifiedAt: c.ficaLastVerifiedAt, ficaStatus: c.ficaStatus }));
}
const saDate = (iso, tz = "Africa/Johannesburg") => (iso ? new Intl.DateTimeFormat("en-ZA", { timeZone: tz, day: "numeric", month: "short", year: "numeric" }).format(new Date(iso)) : null);

async function main() {
  const browser = await chromium.launch({ headless: true });
  const V1440 = { width: 1440, height: 900 };
  const V1024 = { width: 1024, height: 768 };
  try {
    // ================================================================ 1440 light: behaviour checks
    const { context, page } = await newContext(browser, V1440, "light");
    const c = result.checks;
    await gotoList(page);
    c.default = await page.evaluate(state);

    // ---- Columns menu: items + checked state; hide one; reset on navigation
    let menu = await clickColumnsMenu(page);
    c.columnMenu = {
      items: await menu.evaluate((m) => [...m.querySelectorAll('[role="menuitemcheckbox"]')].map((i) => ({ label: i.textContent.trim(), checked: i.getAttribute("aria-checked") }))),
      label: await menu.evaluate((m) => (m.querySelector('[data-slot="dropdown-menu-label"]') || {}).textContent || null),
    };
    c.columnMenu.screenshot = await shot(page, "colmenu-open-1440-light", false);
    await menu.getByRole("menuitemcheckbox", { name: "Trust balance" }).click();
    await page.waitForTimeout(300);
    c.columnMenu.stillOpenAfterToggle = await page.getByRole("menu").isVisible();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
    c.columnMenu.afterHideTrust = await page.evaluate(state);
    c.columnMenu.afterHideTrustScreenshot = await shot(page, "colmenu-trust-hidden-1440-light");
    c.csvTrustHidden = await exportCsv(page, "clients-export-trust-hidden.csv");
    // navigate via a sort link → visibility resets
    const lifeLink = page.locator('[data-testid="clients-table"] thead').getByRole("link", { name: "Lifecycle", exact: true });
    const lifeHref = await lifeLink.getAttribute("href");
    await lifeLink.click();
    await settle(page, (u) => rel(u.toString()) === lifeHref);
    c.columnMenu.afterSortNav = (await page.evaluate(state)).headers;
    c.columnMenu.afterSortNavUrl = rel(page.url());
    c.columnMenu.afterSortNavScreenshot = await shot(page, "colmenu-reset-after-sort-1440-light");
    // hide again (Lifecycle — the active sort column), then navigate via a lifecycle pill → resets
    menu = await clickColumnsMenu(page);
    await menu.getByRole("menuitemcheckbox", { name: "Completeness" }).click();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
    c.columnMenu.afterHideCompleteness = (await page.evaluate(state)).headers;
    await page.locator('[data-testid="client-lifecycle-filter"]').getByRole("link", { name: "Active", exact: true }).click();
    await settle(page, (u) => new URL(u.toString()).searchParams.get("lifecycleStatus") === "ACTIVE");
    c.columnMenu.afterPillNav = (await page.evaluate(state)).headers;
    c.columnMenu.afterPillNavUrl = rel(page.url());
    // saved view: the menu lists the same columns (+ Contact visible)
    await gotoSavedView(page);
    menu = await clickColumnsMenu(page);
    c.columnMenu.savedViewItems = await menu.evaluate((m) => [...m.querySelectorAll('[role="menuitemcheckbox"]')].map((i) => ({ label: i.textContent.trim(), checked: i.getAttribute("aria-checked") })));
    await page.keyboard.press("Escape");

    // ---- Export CSV at the default list (1440: Contact priority-hidden on screen)
    await gotoList(page);
    c.csv1440 = await exportCsv(page, "clients.csv");
    c.csv1440.screenHeaders = (await page.evaluate(state)).headers;
    await gotoSavedView(page);
    c.csvSavedView = await exportCsv(page, "clients-export-saved-view.csv");

    // ---- Two-line headers + accessible names
    await gotoList(page);
    c.headers1440 = (await page.evaluate(clientColumn)).headers;
    c.theadAria = await page.locator('[data-testid="clients-table"] thead').ariaSnapshot();
    const expectNames = ["Client", "Lifecycle", "FICA", "Open matters", "Outstanding fee notes", "Trust balance", "Completeness", "Created", "Contact", "Actions"];
    c.accessibleNames = {};
    for (const n of expectNames) {
      c.accessibleNames[n] = {
        columnheader: await page.getByRole("columnheader", { name: n, exact: true }).count(),
        link: await page.locator('[data-testid="clients-table"] thead').getByRole("link", { name: n, exact: true }).count(),
      };
    }
    c.headersShot = await shot(page, "headers-two-line-1440-light", false);

    // ---- Keyboard: Tab from search to the first header link + Enter; Columns opens with Enter
    await page.locator('[role="search"] input').focus();
    const seq = [];
    for (let i = 0; i < 20; i++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => ({ tag: document.activeElement.tagName, text: document.activeElement.textContent.trim().slice(0, 30), href: document.activeElement.getAttribute("href"), inThead: !!document.activeElement.closest("thead"), boxShadow: getComputedStyle(document.activeElement).boxShadow, outline: getComputedStyle(document.activeElement).outlineStyle }));
      seq.push(f);
      if (f.inThead) break;
    }
    c.keyboard = { tabSequenceFromSearch: seq, tabs: seq.length };
    const focused = seq[seq.length - 1];
    c.keyboard.focusShot = await shot(page, "keyboard-focus-sort-link-1440-light", false);
    await page.keyboard.press("Enter");
    await settle(page, (u) => rel(u.toString()) === focused.href);
    c.keyboard.afterEnter = rel(page.url());
    c.keyboard.rowsAfterEnter = (await page.evaluate(state)).rows.map((r) => r[0]);
    await gotoList(page);
    await page.locator("#main-content").getByRole("button", { name: "Columns", exact: true }).focus();
    await page.keyboard.press("Enter");
    c.keyboard.columnsMenuOpensWithEnter = await page.getByRole("menu").isVisible({ timeout: 3000 }).catch(() => false);
    await page.keyboard.press("Escape");

    c.pagination = c.default.pagination;

    // ---- Row interactions: plain cell (Trust balance), client link, row menu
    await gotoList(page);
    const row = page.locator('[data-testid="client-row"]').nth(1);
    const clientHref = await row.locator('[data-testid="client-link"]').getAttribute("href");
    const hdrs = (await page.evaluate(state)).headers;
    const trustIdx = hdrs.indexOf("Trust balance");
    const cell = row.locator("td:visible").nth(trustIdx);
    c.rowClick = { cell: "Trust balance", cellText: (await cell.innerText()).trim(), expected: clientHref };
    await cell.click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(clientHref), { timeout: 15_000 }).then(() => (c.rowClick.navigates = true)).catch(() => (c.rowClick.navigates = false));
    c.rowClick.urlAfter = rel(page.url());
    c.rowClick.h1 = await page.locator("#main-content h1").first().textContent({ timeout: 15_000 }).catch(() => null);
    // client link: count navigations (must be exactly one)
    await gotoList(page);
    let navs = 0;
    const onNav = (f) => { if (f === page.mainFrame()) navs++; };
    page.on("framenavigated", onNav);
    await page.locator('[data-testid="client-row"]').nth(1).locator('[data-testid="client-link"]').click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(clientHref), { timeout: 15_000 }).catch(() => {});
    await page.locator("#main-content h1").first().waitFor({ timeout: 15_000 }).catch(() => {});
    await page.waitForTimeout(1500);
    page.off("framenavigated", onNav);
    c.clientLink = { expected: clientHref, urlAfter: rel(page.url()), ok: rel(page.url()).startsWith(clientHref), mainFrameNavigations: navs, h1: await page.locator("#main-content h1").first().textContent().catch(() => null) };
    await gotoList(page);
    const before = rel(page.url());
    await page.locator('[data-testid="client-row"]').nth(1).locator('[data-testid="client-row-actions-trigger"]').click();
    const rm = page.getByRole("menu");
    await rm.waitFor({ timeout: 10_000 });
    await page.waitForTimeout(1000);
    c.rowMenu = { items: await rm.getByRole("menuitem").allInnerTexts(), urlAfterOpen: rel(page.url()), rowDidNotNavigate: rel(page.url()) === before };
    c.rowMenu.screenshot = await shot(page, "row-menu-open-1440-light", false);
    await rm.getByRole("menuitem", { name: "Open client" }).click();
    await page.waitForURL((u) => rel(u.toString()).startsWith(clientHref), { timeout: 15_000 }).catch(() => {});
    c.rowMenu.openClientUrl = rel(page.url());

    // ---- Empty variants
    c.empty = {};
    for (const [key, qs] of [
      ["searchNoMatch", "?search=zzqx-no-such-client"],
      ["offboarded", "?lifecycleStatus=OFFBOARDED"],
      ["litigationIncomplete", "?tags=litigation&showIncomplete=true"],
      ["unknownView", "?view=00000000-0000-0000-0000-000000000000"],
    ]) {
      await gotoList(page, qs);
      c.empty[key] = { qs, ...(await page.evaluate(state)) };
      delete c.empty[key].rows;
      c.empty[key].rowCount = (await page.evaluate(state)).rows.length;
      c.empty[key].screenshot = await shot(page, `empty-${key}-1440-light`);
    }
    await gotoList(page, "?search=zzqx-no-such-client");
    await page.getByRole("link", { name: "Clear filters" }).click();
    await settle(page, (u) => rel(u.toString()) === LIST);
    c.empty.searchNoMatch.afterClear = { url: rel(page.url()), rows: (await page.evaluate(state)).rows.length };
    await context.close();
    save();

    // ================================================================ Sticky header (OBS-CARRY 639C)
    for (const [w, h] of [[1440, 500], [1024, 500]]) {
      for (const theme of ["light", "dark"]) {
        const x = await newContext(browser, { width: w, height: h }, theme);
        const key = `${w}x${h}-${theme}`;
        const S = {};
        await gotoList(x.page);
        S.defaultTop = await x.page.evaluate(stickyProbe);
        S.defaultTopShot = await shot(x.page, `sticky-default-top-${key}`, false);
        for (const dy of [150, 300, 600]) {
          await x.page.evaluate((d) => window.scrollTo(0, d), dy);
          await x.page.waitForTimeout(300);
          S[`defaultScrolled${dy}`] = await x.page.evaluate(stickyProbe);
        }
        S.defaultScrolledShot = await shot(x.page, `sticky-default-scrolled-${key}`, false);
        if (theme === "light") {
          await gotoSavedView(x.page);
          S.savedTop = await x.page.evaluate(stickyProbe);
          await x.page.evaluate(() => window.scrollTo(0, 300));
          await x.page.waitForTimeout(300);
          S.savedScrolled300 = await x.page.evaluate(stickyProbe);
          S.savedScrolledShot = await shot(x.page, `sticky-saved-view-scrolled-${key}`, false);
          await x.page.evaluate(() => { window.scrollTo(0, 0); const sc = document.querySelector('[data-testid="clients-table"]').closest('[data-slot="table-container"]'); sc.scrollLeft = sc.scrollWidth; });
          await x.page.waitForTimeout(300);
          S.savedScrolledRight = await x.page.evaluate(stickyProbe);
          S.savedScrolledRightShot = await shot(x.page, `saved-view-scrolled-right-${key}`, false);
        }
        result.sticky[key] = S;
        await x.context.close();
        save();
      }
    }

    // ================================================================ layout × width × theme
    for (const [vp, w] of [[V1024, 1024], [V1440, 1440]]) {
      for (const theme of ["light", "dark"]) {
        const x = await newContext(browser, vp, theme);
        const key = `${w}-${theme}`;
        const L = {};
        await gotoList(x.page);
        L.state = await x.page.evaluate(state);
        L.clientColumn = await x.page.evaluate(clientColumn);
        L.defaultShot = await shot(x.page, `default-${key}`);
        await gotoList(x.page, "?sortBy=trustBalance&sortDir=desc");
        L.sorted = { url: rel(x.page.url()), headers: (await x.page.evaluate(state)).headers };
        L.sortedShot = await shot(x.page, `sorted-trust-desc-${key}`);
        await gotoList(x.page);
        await clickColumnsMenu(x.page);
        L.colMenuShot = await shot(x.page, `colmenu-open-${key}`, false);
        L.colMenuBox = await x.page.getByRole("menu").evaluate((m) => { const r = m.getBoundingClientRect(); return { top: Math.round(r.top), bottom: Math.round(r.bottom), left: Math.round(r.left), right: Math.round(r.right), innerW: innerWidth, innerH: innerHeight }; });
        await x.page.keyboard.press("Escape");
        await gotoList(x.page, "?search=zzqx-no-such-client");
        L.filteredEmptyShot = await shot(x.page, `filtered-empty-${key}`);
        if (theme === "light") {
          await gotoSavedView(x.page);
          L.savedView = { clientColumn: await x.page.evaluate(clientColumn), shot: await shot(x.page, `saved-view-${key}`) };
        }
        if (w === 1024 && theme === "light") {
          await gotoList(x.page);
          result.checks.csv1024 = await exportCsv(x.page, "clients-export-1024.csv");
          result.checks.csv1024.screenHeaders = (await x.page.evaluate(state)).headers;
        }
        result.layout[key] = L;
        await x.context.close();
        save();
      }
    }

    // ================================================================ Dates: SA calendar in 3 time zones
    const api = await apiClients();
    result.dates.api = api.map((a) => ({ ...a, createdSA: saDate(a.createdAt), createdKiritimati: saDate(a.createdAt, "Pacific/Kiritimati"), ficaSA: saDate(a.ficaLastVerifiedAt) }));
    for (const tz of ["Africa/Johannesburg", "America/New_York", "Pacific/Kiritimati"]) {
      const x = await newContext(browser, V1440, "light", "alice", tz);
      await gotoSavedView(x.page); // saved view shows every column incl. Created at 1440
      const read = () => x.page.evaluate(() => {
        const t = document.querySelector('[data-testid="clients-table"]');
        const ths = [...t.querySelectorAll("thead th")].map((th) => th.textContent.replace(/\s+/g, " ").trim());
        const ci = ths.indexOf("Created");
        return [...t.querySelectorAll('[data-testid="client-row"]')].map((r) => {
          const tds = r.querySelectorAll("td");
          const fica = r.querySelector('[data-testid="client-fica-status"]');
          return { name: r.querySelector('[data-testid="client-link"]').textContent, created: tds[ci].innerText.trim(), fica: fica ? fica.parentElement.innerText.replace(/\s+/g, " ").trim() : null };
        });
      });
      const first = await read();
      await x.page.reload({ waitUntil: "load" });
      await settle(x.page);
      const reloaded = await read();
      const csv = await exportCsv(x.page, `clients-export-${tz.split("/")[1].toLowerCase()}.csv`);
      const rows = first.map((r) => {
        const a = result.dates.api.find((z) => z.name === r.name) || {};
        const expFica = a.ficaStatus === "DONE" ? `Done ${a.ficaSA}` : null;
        return { ...r, expectedCreated: a.createdSA, createdOk: r.created === a.createdSA, ficaOk: expFica ? r.fica === expFica : true };
      });
      result.dates[tz] = { browserOffsetMin: await x.page.evaluate(() => -new Date().getTimezoneOffset()), rows, allCreatedOk: rows.every((r) => r.createdOk), allFicaOk: rows.every((r) => r.ficaOk), reloadSame: JSON.stringify(first) === JSON.stringify(reloaded), csvHeader: csv.lines[0], csvFirst: csv.lines[1] };
      if (tz === "Pacific/Kiritimati") result.dates[tz].shot = await shot(x.page, "dates-saved-view-kiritimati-1440-light");
      await x.context.close();
      save();
    }

    // ================================================================ Carol (member)
    result.checks.carol = {};
    for (const [vp, w] of [[V1440, 1440], [V1024, 1024]]) {
      const cc = await newContext(browser, vp, "light", "carol");
      await cc.page.goto(`${BASE}${LIST}`, { waitUntil: "load" });
      await cc.page.waitForLoadState("networkidle").catch(() => {});
      await cc.page.waitForTimeout(800);
      result.checks.carol[w] = {
        url: rel(cc.page.url()),
        text: (await cc.page.locator("#main-content").innerText()).replace(/\s+/g, " ").trim().slice(0, 300),
        hasTable: (await cc.page.locator('[data-testid="clients-table"]').count()) > 0,
        columnsButton: await cc.page.locator("#main-content").getByRole("button", { name: "Columns" }).count(),
        exportButton: await cc.page.locator("#main-content").getByRole("button", { name: "Export CSV" }).count(),
        user: await cc.page.locator("aside, nav").getByText("Carol").first().textContent().catch(() => null),
        shot: await shot(cc.page, `carol-${w}-light`),
      };
      await cc.context.close();
    }
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
