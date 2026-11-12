// 641A OBS "before" (task 641.1): today's Matters list behaviour on the mock-auth e2e stack (:3001),
// built from worktree-epic-641A @ 01e6782d1 (no product change). This is the parity oracle the
// after-change observation replays.
// Usage: node obs-641a-before.cjs [tag=before] [createView=0|1]
//   createView=1: if no saved (non-system) matter view exists, create one through the Save View
//   dialog ("OBS 641A view", default columns, no filters) so the clearOnSavedView drop can be seen.
// Writes PNG + <tag>.json next to this file.
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-641A/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const LIST = `/org/${ORG}/matters`;
const OUT = __dirname;
const tag = process.argv[2] || "before";
const createView = process.argv[3] === "1";
const VIEWPORTS = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };

const result = { tag, startedAt: new Date().toISOString(), base: BASE, steps: {}, console: [] };

async function newContext(browser, w, theme) {
  const context = await browser.newContext({ viewport: VIEWPORTS[w] });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  page.on("console", (m) => {
    const text = m.text();
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(text))
      result.console.push({ ctx: `${w}-${theme}`, type: m.type(), text: text.slice(0, 600), url: page.url() });
  });
  page.on("pageerror", (e) =>
    result.console.push({ ctx: `${w}-${theme}`, type: "pageerror", text: String(e).slice(0, 600), url: page.url() })
  );
  return { context, page };
}

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
}

/** Wait for the list to settle after a navigation: the URL predicate, then table/empty + idle. */
async function settle(page, urlPred) {
  if (urlPred) await page.waitForURL(urlPred, { timeout: 30_000 });
  await page.locator("#main-content h1").first().waitFor({ timeout: 30_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(400);
}

function rel(u) {
  const x = new URL(u);
  return x.pathname + x.search;
}

/** Runs in the page: the list state. */
function readList() {
  const main = document.querySelector("#main-content") || document;
  const vis = (el) => !!el && getComputedStyle(el).display !== "none" && el.getClientRects().length > 0;
  const table = main.querySelector('[data-testid="matters-table"]');
  const ths = table ? [...table.querySelectorAll("thead th")] : [];
  const rows = [...main.querySelectorAll('[data-testid="matter-row"]')];
  const footer = main.querySelector('[data-testid="matters-footer"]');
  const h1 = main.querySelector("h1");
  const statusChips = [...main.querySelectorAll('[data-testid="project-status-filter"] a')].map((a) => ({
    label: a.textContent.trim(),
    href: a.getAttribute("href"),
    active: a.className.includes("bg-slate-900"),
  }));
  const tabs = [...main.querySelectorAll('[data-testid="saved-view-selector"] [role="tab"]')].map((t) => ({
    name: t.textContent.trim(),
    selected: t.getAttribute("aria-selected") === "true",
    system: t.getAttribute("data-system-view") === "true",
  }));
  const emptyBox = [...main.querySelectorAll("p")].find((p) => /No matters match this view/.test(p.textContent));
  return {
    url: location.pathname + location.search,
    h1: h1 ? h1.textContent.trim() : null,
    subtitle: h1 && h1.parentElement.querySelector("p") ? h1.parentElement.querySelector("p").textContent.trim() : null,
    caption: table && table.querySelector("caption") ? table.querySelector("caption").textContent.trim() : null,
    rowCount: rows.length,
    rowNames: rows.map((r) => {
      const l = r.querySelector('[data-testid="matter-link"]');
      return l ? l.textContent.trim() : null;
    }),
    rowStatuses: rows.map((r) => {
      const b = r.querySelector('[data-testid="matter-status-badge"]');
      return b ? b.textContent.trim() : null;
    }),
    footer: footer ? footer.textContent.trim() : null,
    headers: ths.map((th) => {
      const a = th.querySelector("a");
      return {
        text: th.textContent.replace(/[↑↓]/g, "").trim() || (th.querySelector(".sr-only") ? `[sr:${th.querySelector(".sr-only").textContent}]` : ""),
        arrow: (th.textContent.match(/[↑↓]/) || [null])[0],
        visible: vis(th),
        ariaSort: th.getAttribute("aria-sort"),
        sortHref: a ? a.getAttribute("href") : null,
        className: th.className,
      };
    }),
    statusChips,
    viewTabs: tabs,
    filtersTrigger: main.querySelector('[data-testid="matter-filters-trigger"]')
      ? main.querySelector('[data-testid="matter-filters-trigger"]').textContent.trim()
      : null,
    searchValue: main.querySelector('[role="search"] input') ? main.querySelector('[role="search"] input').value : null,
    emptyText: emptyBox ? emptyBox.parentElement.innerText.trim() : null,
    clearFiltersHref: emptyBox && emptyBox.parentElement.querySelector("a") ? emptyBox.parentElement.querySelector("a").getAttribute("href") : null,
  };
}

async function snap(page) {
  return page.evaluate(readList);
}

async function shot(page, name) {
  // One sub-directory per run tag (pristine / rich), so the two datasets never overwrite.
  const dir = tag.replace(/^before-/, "");
  fs.mkdirSync(path.join(OUT, dir), { recursive: true });
  const file = `${dir}/${name}.png`;
  await page.screenshot({ path: path.join(OUT, file), fullPage: true });
  return file;
}

function save() {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, `${tag}.json`), JSON.stringify(result, null, 2));
}

/** Runs in the page: DOM shape of the first matter row (tags + testids + links, depth-limited). */
function rowShape() {
  const row = document.querySelector('[data-testid="matter-row"]');
  if (!row) return null;
  const desc = (el, depth) => {
    const o = { tag: el.tagName.toLowerCase() };
    for (const a of ["data-testid", "data-slot", "role", "href", "title", "aria-label", "aria-haspopup", "tabindex"]) {
      if (el.hasAttribute(a)) o[a] = el.getAttribute(a);
    }
    if (el.classList.contains("sr-only")) o.srOnly = true;
    if (el.children.length === 0 && el.textContent.trim()) o.text = el.textContent.trim().slice(0, 60);
    if (depth < 5 && el.children.length) o.children = [...el.children].map((c) => desc(c, depth + 1));
    return o;
  };
  const table = row.closest("table");
  return {
    ancestry: (() => {
      const a = [];
      let n = row;
      for (let i = 0; i < 6 && n; i++, n = n.parentElement)
        a.push(`${n.tagName.toLowerCase()}${n.getAttribute("data-testid") ? `[data-testid=${n.getAttribute("data-testid")}]` : ""}${n.getAttribute("data-slot") ? `[data-slot=${n.getAttribute("data-slot")}]` : ""}`);
      return a;
    })(),
    usesTable: !!table,
    rowTag: row.tagName.toLowerCase(),
    rowAttrs: Object.fromEntries([...row.attributes].map((a) => [a.name, a.value])),
    rowCursor: getComputedStyle(row).cursor,
    rowTabIndex: row.tabIndex,
    rowRole: row.getAttribute("role"),
    cellCount: row.children.length,
    linksInRow: [...row.querySelectorAll("a")].map((a) => ({ text: a.textContent.trim(), href: a.getAttribute("href"), testid: a.getAttribute("data-testid") })),
    buttonsInRow: [...row.querySelectorAll("button")].map((b) => ({ text: b.textContent.trim(), ariaLabel: b.getAttribute("aria-label"), haspopup: b.getAttribute("aria-haspopup"), testid: b.getAttribute("data-testid") })),
    tree: desc(row, 0),
  };
}

async function gotoList(page, qs = "") {
  await page.goto(`${BASE}${LIST}${qs}`, { waitUntil: "load" });
  await settle(page);
}

/** Click a sort header link by its visible label; wait for the URL to carry its href. */
async function clickSort(page, label) {
  const a = page.locator('[data-testid="matters-table"] thead').getByRole("link", { name: label, exact: true });
  const href = await a.getAttribute("href");
  await a.click();
  await settle(page, (u) => rel(u.toString()) === href);
  return href;
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  try {
    // ------------------------------------------------------------ 1440 light: the full walk
    const { context, page } = await newContext(browser, 1440, "light");
    await login(page);

    // Step 1 — default list
    await gotoList(page);
    const s1 = await snap(page);
    s1.rowShape = await page.evaluate(rowShape);
    s1.screenshot = await shot(page, "01-default-1440-light");
    // Row click outside a link: click the status cell of the first row.
    const firstRow = page.locator('[data-testid="matter-row"]').first();
    const statusCell = firstRow.locator('[data-testid="matter-status-badge"]').first();
    const firstHref = await firstRow.locator('[data-testid="matter-link"]').getAttribute("href");
    await statusCell.click();
    let rowClick = { clickedOn: "status badge cell", expectedHref: firstHref };
    try {
      await page.waitForURL((u) => rel(u.toString()).startsWith(firstHref), { timeout: 15_000 });
      rowClick.navigatedTo = rel(page.url());
      rowClick.navigates = true;
    } catch (e) {
      rowClick.navigates = false;
      rowClick.urlAfter = rel(page.url());
    }
    s1.rowClickOutsideLink = rowClick;
    result.steps["1-default"] = s1;
    save();

    // Step 2 — sort by every sortable column, twice (toggle)
    await gotoList(page);
    const base = await snap(page);
    const sortable = base.headers.filter((h) => h.sortHref);
    const sorts = [];
    for (const h of sortable) {
      await gotoList(page);
      const entry = { column: h.text, visibleAtDefault: h.visible, defaultHref: h.sortHref };
      if (h.visible) {
        entry.firstClickHref = await clickSort(page, h.text);
      } else {
        // Hidden by container query at this width: follow the header's own href.
        await page.goto(`${BASE}${h.sortHref}`, { waitUntil: "load" });
        await settle(page);
        entry.firstClickHref = h.sortHref;
        entry.note = "header hidden at 1440 default; navigated to its href";
      }
      const a1 = await snap(page);
      const hdr1 = a1.headers.find((x) => x.text === h.text);
      entry.first = { url: a1.url, ariaSort: hdr1 && hdr1.ariaSort, arrow: hdr1 && hdr1.arrow, visibleWhenActive: hdr1 && hdr1.visible, rowNames: a1.rowNames, footer: a1.footer };
      entry.secondClickHref = await clickSort(page, h.text);
      const a2 = await snap(page);
      const hdr2 = a2.headers.find((x) => x.text === h.text);
      entry.second = { url: a2.url, ariaSort: hdr2 && hdr2.ariaSort, arrow: hdr2 && hdr2.arrow, rowNames: a2.rowNames, footer: a2.footer };
      entry.thirdHref = hdr2 && hdr2.sortHref;
      sorts.push(entry);
    }
    await shot(page, "02-sort-last-1440-light");
    result.steps["2-sort"] = sorts;
    save();

    // Step 3 — saved views (view selector tabs) + clearOnSavedView on `tags`
    const v = { };
    await gotoList(page);
    let st = await snap(page);
    v.tabsAtDefault = st.viewTabs;
    if (createView && !st.viewTabs.some((t) => !t.system)) {
      await page.getByRole("button", { name: "Save View" }).click();
      const dlg = page.getByRole("dialog");
      await dlg.waitFor();
      v.createDialogTitles = [await dlg.getByRole("heading").first().textContent()];
      await page.waitForTimeout(500);
      // Pre-existing: with tags + custom fields, step 1 is taller than the viewport and the
      // dialog does not scroll, so "Next" sits off-screen. Record that, then click it via the DOM.
      v.createDialogStep1 = await dlg.evaluate((d) => {
        const r = d.getBoundingClientRect();
        const next = [...d.querySelectorAll("button")].find((b) => b.textContent.trim() === "Next");
        const nr = next.getBoundingClientRect();
        return { dialogTop: r.top, dialogBottom: r.bottom, viewportH: innerHeight, nextButtonBottom: nr.bottom, nextInViewport: nr.bottom <= innerHeight && nr.top >= 0, overflowY: getComputedStyle(d).overflowY };
      });
      v.createDialogStep1Screenshot = await shot(page, "03-save-view-dialog-step1-1440-light");
      const clickNext = () => dlg.getByRole("button", { name: "Next" }).dispatchEvent("click");
      await clickNext();
      v.createDialogTitles.push(await dlg.getByRole("heading").first().textContent());
      await clickNext();
      v.createDialogTitles.push(await dlg.getByRole("heading").first().textContent());
      await dlg.locator("#view-name").fill("OBS 641A view");
      await dlg.getByRole("button", { name: "Save View" }).dispatchEvent("click");
      await dlg.waitFor({ state: "hidden", timeout: 30_000 });
      await page.waitForTimeout(1000);
      v.createdView = "OBS 641A view (default columns, no filters, not shared) — created via Save View dialog";
      v.urlAfterCreate = rel(page.url());
      await gotoList(page);
      st = await snap(page);
      v.tabsAfterCreate = st.viewTabs;
    }
    v.switches = [];
    for (const t of st.viewTabs) {
      await gotoList(page);
      await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: t.name, exact: false }).first().click();
      await page.waitForTimeout(300);
      await settle(page);
      const a = await snap(page);
      v.switches.push({ tab: t.name, system: t.system, url: a.url, caption: a.caption, selectedTab: (a.viewTabs.find((x) => x.selected) || {}).name, rowNames: a.rowNames, footer: a.footer, emptyText: a.emptyText, headers: a.headers.filter((h) => h.visible).map((h) => h.text) });
    }
    // tags param then switch to a saved view (and to a system view, for contrast)
    v.tagsDrop = [];
    const savedTab = st.viewTabs.find((t) => !t.system);
    const systemTab = st.viewTabs.find((t) => t.system && !t.selected);
    for (const target of [systemTab, savedTab].filter(Boolean)) {
      await gotoList(page, "?tags=obs-nonexistent-tag&status=ALL");
      const before = rel(page.url());
      const beforeSnap = await snap(page);
      await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: target.name, exact: false }).first().click();
      await page.waitForURL((u) => rel(u.toString()) !== before, { timeout: 20_000 }).catch(() => {});
      await settle(page);
      const after = await snap(page);
      v.tagsDrop.push({ switchTo: target.name, system: target.system, urlBefore: before, filtersTriggerBefore: beforeSnap.filtersTrigger, emptyBefore: beforeSnap.emptyText, urlAfter: after.url, rowNamesAfter: after.rowNames, footerAfter: after.footer, filtersTriggerAfter: after.filtersTrigger });
    }
    v.screenshot = await shot(page, "03-view-switch-1440-light");
    result.steps["3-views"] = v;
    save();

    // Step 4 — status filter chips
    const s4 = [];
    await gotoList(page);
    const chips = (await snap(page)).statusChips;
    for (const c of chips) {
      await gotoList(page);
      await page.locator('[data-testid="project-status-filter"]').getByRole("link", { name: c.label, exact: true }).click();
      await settle(page, (u) => rel(u.toString()) === c.href);
      const a = await snap(page);
      s4.push({ chip: c.label, href: c.href, url: a.url, rowNames: a.rowNames, rowStatuses: a.rowStatuses, footer: a.footer, subtitle: a.subtitle, emptyText: a.emptyText, activeChip: (a.statusChips.find((x) => x.active) || {}).label });
      if (c.label === "Closed" || c.label === "All") await shot(page, `04-status-${c.label.toLowerCase()}-1440-light`);
    }
    result.steps["4-status"] = { chipsAtDefault: chips, results: s4 };
    save();

    // Step 5 — search
    await gotoList(page);
    const names = (await snap(page)).rowNames.filter(Boolean);
    // A term matching a subset: the longest word of the first name that is not in every row.
    let term = null;
    for (const n of names) {
      for (const w of n.split(/\s+/).filter((x) => x.length >= 4)) {
        const hits = names.filter((x) => x.toLowerCase().includes(w.toLowerCase())).length;
        if (hits > 0 && hits < names.length) { term = w; break; }
      }
      if (term) break;
    }
    // Only one distinct row set (e.g. a 1-matter seed): fall back to part of the first name.
    const termMatchesAll = !term;
    if (!term) term = (names[0] || "Website").split(/\s+/)[0].slice(0, 5);
    const s5 = { term, termMatchesAll };
    const searchBox = page.locator('[role="search"] input');
    s5.placeholder = await searchBox.getAttribute("placeholder");
    await searchBox.fill(term);
    await settle(page, (u) => new URL(u.toString()).searchParams.get("search") === term);
    s5.match = await snap(page);
    s5.match.screenshot = await shot(page, "05-search-match-1440-light");
    await searchBox.fill("zzqx-no-such-matter");
    await settle(page, (u) => new URL(u.toString()).searchParams.get("search") === "zzqx-no-such-matter");
    s5.noMatch = await snap(page);
    s5.noMatch.screenshot = await shot(page, "05-search-empty-1440-light");
    // Clear filters link
    const clear = page.getByRole("link", { name: "Clear filters" });
    if (await clear.count()) {
      await clear.click();
      await settle(page, (u) => rel(u.toString()) === LIST);
      const c = await snap(page);
      s5.afterClearFilters = { url: c.url, rowCount: c.rowCount, footer: c.footer, searchValue: c.searchValue };
    }
    result.steps["5-search"] = s5;
    save();

    // Step 6 — filter sheet
    const s6 = {};
    await gotoList(page);
    const trig = page.locator('[data-testid="matter-filters-trigger"]');
    s6.triggerPresent = (await trig.count()) > 0;
    if (s6.triggerPresent) {
      s6.triggerText = (await trig.textContent()).trim();
      await trig.click();
      const sheet = page.getByRole("dialog");
      await sheet.waitFor();
      await page.waitForTimeout(400);
      s6.sheet = await sheet.evaluate((d) => ({
        title: (d.querySelector('[data-slot="sheet-title"]') || d.querySelector("h2") || {}).textContent,
        description: (d.querySelector('[data-slot="sheet-description"]') || d.querySelector("p") || {}).textContent,
        labels: [...d.querySelectorAll("label")].map((l) => l.textContent.trim()),
        buttons: [...d.querySelectorAll("button")].map((b) => (b.getAttribute("aria-label") || b.textContent).trim()),
        inputs: [...d.querySelectorAll("input,select,textarea")].map((i) => ({ type: i.type, placeholder: i.placeholder, name: i.name })),
      }));
      s6.sheetScreenshot = await shot(page, "06-filter-sheet-1440-light");
      await sheet.getByRole("button", { name: "Add Tag" }).click();
      const opts = page.locator('[role="option"]');
      await opts.first().waitFor({ timeout: 10_000 }).catch(() => {});
      s6.tagOptions = await opts.allTextContents();
      if (s6.tagOptions.length) {
        s6.pickedTag = s6.tagOptions[0];
        await opts.first().click();
        await page.waitForTimeout(300);
        await sheet.getByRole("button", { name: "Apply", exact: true }).click();
        await settle(page, (u) => new URL(u.toString()).searchParams.has("tags"));
        const a = await snap(page);
        s6.applied = { url: a.url, rowNames: a.rowNames, footer: a.footer, filtersTrigger: a.filtersTrigger, emptyText: a.emptyText };
        s6.appliedScreenshot = await shot(page, "06-filter-applied-1440-light");
      } else {
        await page.keyboard.press("Escape");
      }
    }
    result.steps["6-filter-sheet"] = s6;
    save();

    // Step 7 — New Matter / New from Template
    const s7 = {};
    await gotoList(page);
    for (const name of ["New Matter", "New from Template"]) {
      const b = page.locator("#main-content").getByRole("button", { name, exact: true });
      const e = { count: await b.count() };
      if (e.count) {
        e.disabled = await b.first().isDisabled();
        e.role = "button";
        if (!e.disabled) {
          await b.first().click();
          const d = page.getByRole("dialog");
          await d.waitFor({ timeout: 10_000 });
          e.dialogTitle = (await d.getByRole("heading").first().textContent()).trim();
          e.screenshot = await shot(page, `07-${name.toLowerCase().replace(/\s+/g, "-")}-1440-light`);
          await page.keyboard.press("Escape");
          await d.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
        }
      }
      s7[name] = e;
    }
    result.steps["7-create-buttons"] = s7;
    save();

    // Step 8 — keyboard: Tab to a sort header link, Enter
    const s8 = {};
    await gotoList(page);
    await page.locator('[role="search"] input').focus();
    let tabs = 0;
    for (; tabs < 60; tabs++) {
      await page.keyboard.press("Tab");
      const inThead = await page.evaluate(() => !!document.activeElement && !!document.activeElement.closest('[data-testid="matters-table"] thead'));
      if (inThead) break;
    }
    s8.tabsFromSearch = tabs + 1;
    s8.focused = await page.evaluate(() => ({ text: document.activeElement.textContent.trim(), href: document.activeElement.getAttribute("href"), tag: document.activeElement.tagName, outline: getComputedStyle(document.activeElement).outlineStyle, boxShadow: getComputedStyle(document.activeElement).boxShadow }));
    s8.focusScreenshot = await shot(page, "08-keyboard-focus-1440-light");
    await page.keyboard.press("Enter");
    await settle(page, (u) => rel(u.toString()) === s8.focused.href);
    const k = await snap(page);
    s8.after = { url: k.url, rowNames: k.rowNames };
    result.steps["8-keyboard"] = s8;
    save();

    // Step 9 — combined search + status + sort: query preservation in sort hrefs
    const s9 = {};
    await gotoList(page, `?status=ALL&search=${encodeURIComponent(term)}&sort=name&dir=asc`);
    let c = await snap(page);
    s9.start = { url: c.url, rowNames: c.rowNames, footer: c.footer, sortHrefs: c.headers.filter((h) => h.sortHref).map((h) => ({ column: h.text, href: h.sortHref, visible: h.visible, ariaSort: h.ariaSort })), statusChipHrefs: c.statusChips };
    const clicked = await clickSort(page, "Client");
    c = await snap(page);
    s9.afterClientSort = { clicked, url: c.url, rowNames: c.rowNames, footer: c.footer, searchValue: c.searchValue, activeChip: (c.statusChips.find((x) => x.active) || {}).label };
    // Status chip click on that state keeps sort+search?
    await page.locator('[data-testid="project-status-filter"]').getByRole("link", { name: "Active", exact: true }).click();
    await settle(page, (u) => !new URL(u.toString()).searchParams.has("status"));
    c = await snap(page);
    s9.afterActiveChip = { url: c.url, rowNames: c.rowNames, footer: c.footer };
    // Search edit on that state keeps sort?
    await page.locator('[role="search"] input').fill("");
    await settle(page, (u) => !new URL(u.toString()).searchParams.has("search"));
    c = await snap(page);
    s9.afterSearchCleared = { url: c.url, rowNames: c.rowNames, footer: c.footer };
    s9.screenshot = await shot(page, "09-combined-1440-light");
    result.steps["9-combined"] = s9;
    save();
    await context.close();

    // ------------------------------------------------------------ other viewports/themes: default list
    const extra = {};
    for (const [w, theme] of [[1440, "dark"], [1024, "light"], [1024, "dark"]]) {
      const x = await newContext(browser, w, theme);
      await login(x.page);
      await gotoList(x.page);
      const a = await snap(x.page);
      extra[`${w}-${theme}`] = {
        htmlClass: await x.page.evaluate(() => document.documentElement.className),
        url: a.url,
        rowNames: a.rowNames,
        footer: a.footer,
        visibleHeaders: a.headers.filter((h) => h.visible).map((h) => h.text),
        hiddenHeaders: a.headers.filter((h) => !h.visible).map((h) => h.text),
        tableContainerWidth: await x.page.evaluate(() => { const t = document.querySelector('[data-testid="matters-table"]'); const c = t && t.closest(".\\@container"); return c ? c.getBoundingClientRect().width : null; }),
        screenshot: await shot(x.page, `01-default-${w}-${theme}`),
      };
      await x.context.close();
    }
    const d1440 = result.steps["1-default"];
    extra["1440-light"] = { visibleHeaders: d1440.headers.filter((h) => h.visible).map((h) => h.text), hiddenHeaders: d1440.headers.filter((h) => !h.visible).map((h) => h.text) };
    result.steps["1b-viewports"] = extra;
    save();
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
