// 642A OBS "after" (task 642.5): replay of before/obs-642a-before.cjs against the ClientsDataTable
// build (worktree-epic-642A @ dd1fa28b4). Identical walk. Changes vs the before script (all listed):
//   (1) this header comment;
//   (2) the sort-arrow probe: the arrows are now lucide <svg> icons (ArrowUp / ArrowDown), not the
//       text glyphs ↑/↓, so `arrow` reads the icon class (same values "↑" / "↓" / null);
//   (3) PW_PATH default points at the 642A worktree's @playwright/test (same 1.59.1).
//
// Usage: node obs-642a-before.cjs <tag> [createView=0|1]
//   <tag>        output name: writes <tag>.json next to this file and screenshots into
//                <tag minus the "before-"/"after-" prefix>/ (e.g. before-rich → rich/).
//                AFTER run: copy this file, keep every probe, run with tag after-pristine / after-rich.
//   createView=1 if no saved (non-system) client view exists, create "OBS 642A view" through the
//                Save View dialog (started on "All clients", default columns, not shared).
// Env: PW_PATH (path to @playwright/test; default: main checkout frontend), BASE (default :3001).
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  process.env.PW_PATH || "/Users/rakheendama/Projects/2026/worktree-epic-642A/frontend/node_modules/@playwright/test"
);

const BASE = process.env.BASE || "http://localhost:3001";
const ORG = "e2e-test-org";
const LIST = `/org/${ORG}/clients`;
const OUT = __dirname;
const tag = process.argv[2] || "before";
const createView = process.argv[3] === "1";
const VIEWPORTS = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
// A fixed tag slug used for the clearOnSavedView probe; the rich dataset creates tag "VIP" (slug vip).
const PROBE_TAG = "vip";
const VIEW_NAME = "OBS 642A view";

const result = { tag, startedAt: new Date().toISOString(), base: BASE, steps: {}, console: [] };

async function newContext(browser, w, theme, user = "alice") {
  const context = await browser.newContext({ viewport: VIEWPORTS[w] });
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
      result.console.push({ ctx, type: m.type(), text: text.slice(0, 600), url: page.url() });
  });
  page.on("pageerror", (e) =>
    result.console.push({ ctx, type: "pageerror", text: String(e).slice(0, 600), url: page.url() })
  );
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
  return { context, page };
}

/** Wait for the list to settle after a navigation: the URL predicate, then h1 + idle. */
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
  const norm = (s) => (s || "").replace(/[↑↓]/g, "").replace(/\s+/g, " ").trim();
  const table = main.querySelector('[data-testid="clients-table"]');
  const ths = table ? [...table.querySelectorAll("thead th")] : [];
  const rows = [...main.querySelectorAll('[data-testid="client-row"]')];
  const footer = main.querySelector('[data-testid="clients-footer"]');
  const h1 = main.querySelector("h1");
  const pills = [...main.querySelectorAll('[data-testid="client-lifecycle-filter"] a')].map((a) => ({
    label: a.textContent.trim(),
    href: a.getAttribute("href"),
    active: a.getAttribute("aria-current") === "true",
  }));
  const inc = main.querySelector('[data-testid="client-show-incomplete"]');
  const tabs = [...main.querySelectorAll('[data-testid="saved-view-selector"] [role="tab"]')].map((t) => ({
    name: t.textContent.trim(),
    selected: t.getAttribute("aria-selected") === "true",
    system: t.getAttribute("data-system-view") === "true",
  }));
  const emptyBox = [...main.querySelectorAll("p")].find((p) => /No clients match this view/.test(p.textContent));
  const h2 = [...main.querySelectorAll("h2")].find((h) => vis(h));
  return {
    url: location.pathname + location.search,
    title: document.title,
    h1: h1 ? h1.textContent.trim() : null,
    h1Count: main.querySelectorAll("h1").length,
    subtitle: h1 && h1.parentElement.querySelector("p") ? h1.parentElement.querySelector("p").textContent.trim() : null,
    caption: table && table.querySelector("caption") ? table.querySelector("caption").textContent.trim() : null,
    hasTable: !!table,
    rowCount: rows.length,
    rowNames: rows.map((r) => {
      const l = r.querySelector('[data-testid="client-link"]');
      return l ? l.textContent.trim() : null;
    }),
    rowFica: rows.map((r) => {
      const b = r.querySelector('[data-testid="client-fica-status"]');
      return b ? norm(b.parentElement.textContent) : null;
    }),
    rowTypes: rows.map((r) => {
      const b = r.querySelector('[data-testid="client-type"]');
      return b ? b.textContent.trim() : null;
    }),
    // Every cell's text in header order (hidden cells included; visibility is per header below).
    rowCells: rows.map((r) => [...r.querySelectorAll("td")].map((td) => norm(td.textContent))),
    footer: footer ? footer.textContent.trim() : null,
    headers: ths.map((th) => {
      const a = th.querySelector("a");
      return {
        text: norm(th.textContent) || (th.querySelector(".sr-only") ? `[sr:${th.querySelector(".sr-only").textContent}]` : ""),
        arrow: th.querySelector("svg.lucide-arrow-up") ? "↑" : th.querySelector("svg.lucide-arrow-down") ? "↓" : null,
        visible: vis(th),
        ariaSort: th.getAttribute("aria-sort"),
        sortHref: a ? a.getAttribute("href") : null,
        className: th.className,
      };
    }),
    lifecyclePills: pills,
    lifecycleGroupPresent: !!main.querySelector('[data-testid="client-lifecycle-filter"]'),
    showIncomplete: inc
      ? { tag: inc.tagName.toLowerCase(), href: inc.getAttribute("href"), active: inc.getAttribute("aria-current") === "true", title: inc.getAttribute("title") }
      : null,
    viewTabs: tabs,
    filtersTrigger: main.querySelector('[data-testid="client-filters-trigger"]')
      ? norm(main.querySelector('[data-testid="client-filters-trigger"]').textContent)
      : null,
    searchValue: main.querySelector('[role="search"] input') ? main.querySelector('[role="search"] input').value : null,
    emptyText: emptyBox ? emptyBox.parentElement.innerText.trim() : null,
    clearFiltersHref: emptyBox && emptyBox.parentElement.querySelector("a") ? emptyBox.parentElement.querySelector("a").getAttribute("href") : null,
    emptyStateText: !table && !emptyBox && h2 ? norm(h2.parentElement.innerText) : null,
    permissionDenied: /permission|don.t have access|not authori[sz]ed/i.test(main.innerText) && !table ? norm(main.innerText).slice(0, 300) : null,
  };
}

/** Runs in the page: layout probes (overflow, widths, sideways page scroll). */
function layout() {
  const r = (el) => el.getBoundingClientRect();
  const cs = (el) => getComputedStyle(el);
  const main = document.querySelector("#main-content");
  const t = document.querySelector('[data-testid="clients-table"]');
  const sc = t ? t.closest('[data-slot="table-container"]') || t.parentElement : null;
  const cq = t ? t.closest(".\\@container") : null;
  let scroller = null;
  for (let n = t ? t.parentElement : null; n && n !== document.body; n = n.parentElement) {
    const ox = cs(n).overflowX;
    if ((ox === "auto" || ox === "scroll") && n.scrollWidth > n.clientWidth) { scroller = n; break; }
  }
  return {
    htmlClass: document.documentElement.className,
    viewport: { w: innerWidth, h: innerHeight },
    docScrollWidth: document.documentElement.scrollWidth,
    docClientWidth: document.documentElement.clientWidth,
    pageScrollsSideways: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    mainWidth: main ? Math.round(r(main).width) : null,
    table: t
      ? {
          tableWidth: Math.round(r(t).width * 10) / 10,
          tableScrollWidth: t.scrollWidth,
          containerSlot: sc ? sc.getAttribute("data-slot") : null,
          containerClientWidth: sc ? sc.clientWidth : null,
          containerScrollWidth: sc ? sc.scrollWidth : null,
          containerOverflowX: sc ? cs(sc).overflowX : null,
          tableScrollsSideways: sc ? sc.scrollWidth > sc.clientWidth : null,
          overflowPx: sc ? sc.scrollWidth - sc.clientWidth : null,
          cqContainerWidth: cq ? Math.round(r(cq).width) : null,
          firstScrollingAncestor: scroller ? `${scroller.tagName.toLowerCase()}.${String(scroller.className).slice(0, 80)} (${scroller.scrollWidth}/${scroller.clientWidth})` : null,
          theadPosition: cs(t.querySelector("thead")).position,
          thPosition: t.querySelector("thead th") ? cs(t.querySelector("thead th")).position : null,
          lastHeaderRight: (() => { const ths = [...t.querySelectorAll("thead th")].filter((x) => cs(x).display !== "none"); return ths.length ? Math.round(r(ths[ths.length - 1]).right) : null; })(),
          containerRight: sc ? Math.round(r(sc).right) : null,
        }
      : null,
  };
}

/** Runs in the page: DOM shape of the first client row (tags + testids + links, depth-limited). */
function rowShape() {
  const row = document.querySelector('[data-testid="client-row"]');
  if (!row) return null;
  const desc = (el, depth) => {
    const o = { tag: el.tagName.toLowerCase() };
    for (const a of ["data-testid", "data-slot", "role", "href", "title", "aria-label", "aria-haspopup", "tabindex"]) {
      if (el.hasAttribute(a)) o[a] = el.getAttribute(a);
    }
    if (el.classList && el.classList.contains("sr-only")) o.srOnly = true;
    if (el.children.length === 0 && el.textContent.trim()) o.text = el.textContent.trim().slice(0, 60);
    if (depth < 5 && el.children.length) o.children = [...el.children].map((c) => desc(c, depth + 1));
    return o;
  };
  return {
    ancestry: (() => {
      const a = [];
      let n = row;
      for (let i = 0; i < 6 && n; i++, n = n.parentElement)
        a.push(`${n.tagName.toLowerCase()}${n.getAttribute("data-testid") ? `[data-testid=${n.getAttribute("data-testid")}]` : ""}${n.getAttribute("data-slot") ? `[data-slot=${n.getAttribute("data-slot")}]` : ""}`);
      return a;
    })(),
    usesTable: !!row.closest("table"),
    rowTag: row.tagName.toLowerCase(),
    rowAttrs: Object.fromEntries([...row.attributes].filter((a) => a.name !== "class").map((a) => [a.name, a.value])),
    rowCursor: getComputedStyle(row).cursor,
    rowTabIndex: row.tabIndex,
    rowRole: row.getAttribute("role"),
    cellCount: row.children.length,
    linksInRow: [...row.querySelectorAll("a")].map((a) => ({ text: a.textContent.trim(), href: a.getAttribute("href"), testid: a.getAttribute("data-testid") })),
    buttonsInRow: [...row.querySelectorAll("button")].map((b) => ({ text: b.textContent.trim(), ariaLabel: b.getAttribute("aria-label"), haspopup: b.getAttribute("aria-haspopup"), testid: b.getAttribute("data-testid") })),
    tree: desc(row, 0),
  };
}

async function snap(page) {
  return page.evaluate(readList);
}

async function shot(page, name) {
  const dir = tag.replace(/^(before|after)-/, "");
  fs.mkdirSync(path.join(OUT, dir), { recursive: true });
  const file = `${dir}/${name}.png`;
  await page.screenshot({ path: path.join(OUT, file), fullPage: true });
  return file;
}

function save() {
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, `${tag}.json`), JSON.stringify(result, null, 2));
}

async function gotoList(page, qs = "") {
  await page.goto(`${BASE}${LIST}${qs}`, { waitUntil: "load" });
  await settle(page);
}

/** Click a sort header link by its visible label; wait for the URL to carry its href. */
async function clickSort(page, label) {
  const a = page.locator('[data-testid="clients-table"] thead').getByRole("link", { name: label, exact: true });
  const href = await a.getAttribute("href");
  await a.click();
  await settle(page, (u) => rel(u.toString()) === href);
  return href;
}

const pick = (s, keys) => Object.fromEntries(keys.map((k) => [k, s[k]]));
const LISTED = ["url", "rowNames", "rowFica", "footer", "subtitle", "emptyText", "emptyStateText"];
/** The cells of one column (by header text) for every row. */
const colCells = (s, label) => { const i = s.headers.findIndex((h) => h.text === label); return i < 0 ? null : s.rowCells.map((c) => c[i]); };

async function main() {
  const browser = await chromium.launch({ headless: true });
  try {
    // ------------------------------------------------------------ 1440 light: the full walk (Alice)
    if (process.env.ONLY_CAROL) {
      const prev = JSON.parse(fs.readFileSync(path.join(OUT, `${tag}.json`), "utf8"));
      Object.assign(result, prev, { console: prev.console || [] });
    }
    const { context, page } = process.env.ONLY_CAROL ? {} : await newContext(browser, 1440, "light");
    if (!process.env.ONLY_CAROL) {

    // Step 1 — default list, row click, client link, row actions menu
    await gotoList(page);
    const s1 = await snap(page);
    s1.rowShape = await page.evaluate(rowShape);
    s1.layout = await page.evaluate(layout);
    s1.screenshot = await shot(page, "01-default-1440-light");
    if (s1.rowCount > 0) {
      const lifeIdx = s1.headers.findIndex((h) => h.text === "Lifecycle");
      const firstRow = page.locator('[data-testid="client-row"]').first();
      const firstHref = await firstRow.locator('[data-testid="client-link"]').getAttribute("href");
      // Row click on a plain (non-link) cell: the Lifecycle cell.
      const rowClick = { clickedOn: `td[${lifeIdx}] (Lifecycle cell)`, expectedHref: firstHref };
      await firstRow.locator("td").nth(lifeIdx).click();
      try {
        await page.waitForURL((u) => rel(u.toString()).startsWith(firstHref), { timeout: 15_000 });
        rowClick.navigates = true;
        rowClick.navigatedTo = rel(page.url());
      } catch (e) {
        rowClick.navigates = false;
        rowClick.urlAfter = rel(page.url());
      }
      s1.rowClickPlainCell = rowClick;
      // Client link
      await gotoList(page);
      await page.locator('[data-testid="client-link"]').first().click();
      await page.waitForURL((u) => rel(u.toString()).startsWith(firstHref), { timeout: 15_000 }).catch(() => {});
      await page.locator("#main-content h1").first().waitFor({ timeout: 30_000 }).catch(() => {});
      s1.clientLinkClick = { url: rel(page.url()), h1: (await page.locator("#main-content h1").first().textContent().catch(() => null)) };
      // Row actions menu
      await gotoList(page);
      const trig = page.locator('[data-testid="client-row-actions-trigger"]').first();
      const before = rel(page.url());
      await trig.click();
      const menu = page.getByRole("menu");
      await menu.waitFor({ timeout: 10_000 });
      await page.waitForTimeout(300);
      s1.rowActions = {
        urlAfterOpen: rel(page.url()),
        navigatedOnOpen: rel(page.url()) !== before,
        triggerAttrs: await trig.evaluate((b) => ({ haspopup: b.getAttribute("aria-haspopup"), expanded: b.getAttribute("aria-expanded"), srText: (b.querySelector(".sr-only") || {}).textContent || null })),
        items: await menu.getByRole("menuitem").evaluateAll((els) => els.map((e) => ({ text: e.textContent.trim(), tag: e.tagName.toLowerCase(), href: e.getAttribute("href") }))),
      };
      s1.rowActions.screenshot = await shot(page, "01-row-actions-menu-1440-light");
      await menu.getByRole("menuitem", { name: "Open client" }).click();
      await page.waitForURL((u) => rel(u.toString()).startsWith(firstHref), { timeout: 15_000 }).catch(() => {});
      s1.rowActions.openClientNavigatedTo = rel(page.url());
    }
    result.steps["1-default"] = s1;
    save();

    // Step 2 — sort by every sortable column, twice (toggle)
    await gotoList(page);
    const base = await snap(page);
    const sorts = [];
    for (const h of base.headers.filter((x) => x.sortHref)) {
      await gotoList(page);
      const entry = { column: h.text, visibleAtDefault: h.visible, defaultHref: h.sortHref, ariaSortAtDefault: h.ariaSort };
      if (h.visible) {
        entry.firstClickHref = await clickSort(page, h.text);
      } else {
        await page.goto(`${BASE}${h.sortHref}`, { waitUntil: "load" });
        await settle(page);
        entry.firstClickHref = h.sortHref;
        entry.note = "header hidden at 1440 default; navigated to its href";
      }
      const a1 = await snap(page);
      const hdr1 = a1.headers.find((x) => x.text === h.text);
      entry.first = { url: a1.url, ariaSort: hdr1 && hdr1.ariaSort, arrow: hdr1 && hdr1.arrow, visibleWhenActive: hdr1 && hdr1.visible, rowNames: a1.rowNames, footer: a1.footer, visibleHeaders: a1.headers.filter((x) => x.visible).map((x) => x.text) };
      entry.secondClickHref = await clickSort(page, h.text);
      const a2 = await snap(page);
      const hdr2 = a2.headers.find((x) => x.text === h.text);
      entry.second = { url: a2.url, ariaSort: hdr2 && hdr2.ariaSort, arrow: hdr2 && hdr2.arrow, rowNames: a2.rowNames, footer: a2.footer };
      entry.thirdHref = hdr2 && hdr2.sortHref;
      // The third header's href in the sorted state: shows the sort params carried by other headers.
      const sortable2 = a2.headers.filter((x) => x.sortHref);
      entry.thirdColumnHref = sortable2[2] ? { column: sortable2[2].text, href: sortable2[2].sortHref } : null;
      entry.otherAriaSort = a2.headers.filter((x) => x.text !== h.text).map((x) => [x.text, x.ariaSort]);
      sorts.push(entry);
    }
    await shot(page, "02-sort-last-1440-light");
    result.steps["2-sort"] = sorts;
    save();

    // Step 3 — saved views + clearOnSavedView (tags, lifecycleStatus)
    const v = {};
    await gotoList(page);
    let st = await snap(page);
    v.tabsAtDefault = st.viewTabs;
    if (createView && !st.viewTabs.some((t) => !t.system)) {
      await page.locator("#main-content").getByRole("button", { name: "Save View" }).click();
      const dlg = page.getByRole("dialog");
      await dlg.waitFor();
      await page.waitForTimeout(500);
      v.createDialogTitles = [(await dlg.getByRole("heading").first().textContent()).trim()];
      v.createDialogStep1 = await dlg.evaluate((d) => {
        const r = d.getBoundingClientRect();
        const next = [...d.querySelectorAll("button")].find((b) => b.textContent.trim() === "Next");
        const nr = next.getBoundingClientRect();
        return { dialogTop: Math.round(r.top), dialogBottom: Math.round(r.bottom), viewportH: innerHeight, nextInViewport: nr.bottom <= innerHeight && nr.top >= 0, overflowY: getComputedStyle(d).overflowY, carriedFilters: (d.querySelector('[data-testid="carried-filters"]') || {}).textContent || null, labels: [...d.querySelectorAll("label")].map((l) => l.textContent.trim()).slice(0, 40) };
      });
      v.createDialogStep1Screenshot = await shot(page, "03-save-view-dialog-step1-1440-light");
      const clickNext = () => dlg.getByRole("button", { name: "Next" }).dispatchEvent("click");
      await clickNext();
      await page.waitForTimeout(300);
      v.createDialogTitles.push((await dlg.getByRole("heading").first().textContent()).trim());
      v.createDialogStep2Columns = await dlg.evaluate((d) =>
        [...d.querySelectorAll('button[role="checkbox"],input[type="checkbox"]')].map((c) => ({
          label: (c.id && d.querySelector(`label[for="${c.id}"]`) ? d.querySelector(`label[for="${c.id}"]`).textContent : (c.closest("label") || c.parentElement).textContent).trim(),
          checked: c.getAttribute("aria-checked") === "true" || c.checked === true,
        }))
      );
      v.createDialogStep2Screenshot = await shot(page, "03-save-view-dialog-step2-1440-light");
      await clickNext();
      await page.waitForTimeout(300);
      v.createDialogTitles.push((await dlg.getByRole("heading").first().textContent()).trim());
      await dlg.locator("#view-name").fill(VIEW_NAME);
      await dlg.getByRole("button", { name: "Save View" }).dispatchEvent("click");
      await dlg.waitFor({ state: "hidden", timeout: 30_000 });
      await page.waitForTimeout(1000);
      v.createdView = `${VIEW_NAME} (started on All clients, default columns, not shared) — created via Save View dialog`;
      v.urlAfterCreate = rel(page.url());
      await gotoList(page);
      st = await snap(page);
      v.tabsAfterCreate = st.viewTabs;
    }
    v.switches = [];
    for (const t of st.viewTabs) {
      await gotoList(page);
      await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: t.name, exact: true }).first().click();
      await page.waitForTimeout(300);
      await settle(page);
      const a = await snap(page);
      v.switches.push({ tab: t.name, system: t.system, url: a.url, caption: a.caption, selectedTab: (a.viewTabs.find((x) => x.selected) || {}).name, rowNames: a.rowNames, footer: a.footer, subtitle: a.subtitle, emptyText: a.emptyText, emptyStateText: a.emptyStateText, clearFiltersHref: a.clearFiltersHref, headers: a.headers.filter((h) => h.visible).map((h) => h.text), lifecycleGroupPresent: a.lifecycleGroupPresent, lifecyclePills: a.lifecyclePills, showIncomplete: a.showIncomplete, filtersTrigger: a.filtersTrigger });
      if (!t.system) v.savedViewLayout = await page.evaluate(layout);
    }
    v.tagsDrop = [];
    const startQs = `?tags=${PROBE_TAG}&lifecycleStatus=ACTIVE&sortBy=createdAt&sortDir=desc`;
    const savedTab = st.viewTabs.find((t) => !t.system);
    const targets = [st.viewTabs.find((t) => t.name === "FICA outstanding"), st.viewTabs.find((t) => t.name === "All clients"), savedTab].filter(Boolean);
    for (const target of targets) {
      await gotoList(page, startQs);
      const before = rel(page.url());
      const b = await snap(page);
      await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: target.name, exact: true }).first().click();
      await page.waitForURL((u) => rel(u.toString()) !== before, { timeout: 20_000 }).catch(() => {});
      await settle(page);
      const a = await snap(page);
      v.tagsDrop.push({ switchTo: target.name, system: target.system, urlBefore: before, rowNamesBefore: b.rowNames, filtersTriggerBefore: b.filtersTrigger, emptyBefore: b.emptyText, activePillBefore: (b.lifecyclePills.find((x) => x.active) || {}).label || null, urlAfter: a.url, rowNamesAfter: a.rowNames, footerAfter: a.footer, emptyAfter: a.emptyText, filtersTriggerAfter: a.filtersTrigger, lifecycleGroupAfter: a.lifecycleGroupPresent, selectedTabAfter: (a.viewTabs.find((x) => x.selected) || {}).name });
    }
    v.screenshot = await shot(page, "03-view-switch-1440-light");
    result.steps["3-views"] = v;
    save();

    // Step 4 — lifecycle pills (every option) + Show incomplete + the Active system view's "All"
    const s4 = { results: [] };
    await gotoList(page);
    const d4 = await snap(page);
    s4.pillsAtDefault = d4.lifecyclePills;
    s4.showIncompleteAtDefault = d4.showIncomplete;
    for (const p of d4.lifecyclePills) {
      await gotoList(page);
      await page.locator('[data-testid="client-lifecycle-filter"]').getByRole("link", { name: p.label, exact: true }).click();
      await settle(page, (u) => rel(u.toString()) === p.href);
      const a = await snap(page);
      s4.results.push({ pill: p.label, href: p.href, ...pick(a, LISTED), clearFiltersHref: a.clearFiltersHref, lifecycleCells: colCells(a, "Lifecycle"), activePill: (a.lifecyclePills.find((x) => x.active) || {}).label || null });
      if (["All", "Active", "Prospect"].includes(p.label)) await shot(page, `04-lifecycle-${p.label.toLowerCase()}-1440-light`);
    }
    await gotoList(page);
    if (d4.showIncomplete && d4.showIncomplete.href) {
      await page.locator('[data-testid="client-show-incomplete"]').click();
      await settle(page, (u) => new URL(u.toString()).searchParams.get("showIncomplete") === "true");
      const a = await snap(page);
      s4.showIncompleteOn = { ...pick(a, LISTED), toggle: a.showIncomplete, completenessCells: colCells(a, "Completeness") };
      s4.showIncompleteOn.screenshot = await shot(page, "04-show-incomplete-1440-light");
      await page.locator('[data-testid="client-show-incomplete"]').click();
      await settle(page, (u) => !new URL(u.toString()).searchParams.has("showIncomplete"));
      s4.showIncompleteOff = { url: rel(page.url()) };
    }
    await gotoList(page, "?view=system%3Aactive");
    const av = await snap(page);
    s4.onActiveView = { url: av.url, pills: av.lifecyclePills, rowNames: av.rowNames };
    await page.locator('[data-testid="client-lifecycle-filter"]').getByRole("link", { name: "All", exact: true }).click();
    await settle(page, (u) => new URL(u.toString()).searchParams.has("lifecycleStatus"));
    const av2 = await snap(page);
    s4.onActiveViewClickAll = { url: av2.url, rowNames: av2.rowNames, footer: av2.footer, activePill: (av2.lifecyclePills.find((x) => x.active) || {}).label || null, selectedTab: (av2.viewTabs.find((x) => x.selected) || {}).name };
    result.steps["4-lifecycle"] = s4;
    save();

    // Step 5 — search
    await gotoList(page);
    const d5 = await snap(page);
    const names = d5.rowNames.filter(Boolean);
    let term = null;
    for (const n of names) {
      for (const w of n.split(/\s+/).filter((x) => x.length >= 4)) {
        const hits = names.filter((x) => x.toLowerCase().includes(w.toLowerCase())).length;
        if (hits > 0 && hits < names.length) { term = w; break; }
      }
      if (term) break;
    }
    const termMatchesAll = !term;
    if (!term) term = (names[0] || "Acme").split(/\s+/)[0].slice(0, 4);
    const s5 = { term, termMatchesAll };
    const searchBox = page.locator('[role="search"] input');
    s5.placeholder = await searchBox.getAttribute("placeholder");
    s5.searchRegionLabel = await page.locator('[role="search"]').getAttribute("aria-label");
    await searchBox.fill(term);
    await settle(page, (u) => new URL(u.toString()).searchParams.get("search") === term);
    s5.match = await snap(page);
    s5.match.screenshot = await shot(page, "05-search-match-1440-light");
    // Search also matches e-mail: the local part of the first row's e-mail, if any.
    const emailCell = (d5.rowCells.map((c) => c.find((x) => /@/.test(x))).find(Boolean) || "").match(/([A-Za-z0-9._%+-]+)@/);
    if (emailCell) {
      s5.emailTerm = emailCell[1];
      await searchBox.fill(s5.emailTerm);
      await settle(page, (u) => new URL(u.toString()).searchParams.get("search") === s5.emailTerm);
      const e = await snap(page);
      s5.emailMatch = pick(e, ["url", "rowNames", "footer"]);
    }
    await searchBox.fill("zzqx-no-such-client");
    await settle(page, (u) => new URL(u.toString()).searchParams.get("search") === "zzqx-no-such-client");
    s5.noMatch = await snap(page);
    s5.noMatch.screenshot = await shot(page, "05-search-empty-1440-light");
    const clear = page.getByRole("link", { name: "Clear filters" });
    if (await clear.count()) {
      await clear.click();
      await settle(page, (u) => rel(u.toString()) === LIST);
      const c = await snap(page);
      s5.afterClearFilters = { url: c.url, rowCount: c.rowCount, footer: c.footer, searchValue: c.searchValue };
    }
    result.steps["5-search"] = s5;
    save();

    // Step 6 — filter sheet (tags) + completeness ("Show incomplete") combined with tags
    const s6 = {};
    await gotoList(page);
    const trig6 = page.locator('[data-testid="client-filters-trigger"]');
    s6.triggerPresent = (await trig6.count()) > 0;
    if (s6.triggerPresent) {
      s6.triggerText = ((await trig6.textContent()) || "").replace(/\s+/g, " ").trim();
      await trig6.click();
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
        s6.applied = { url: a.url, rowNames: a.rowNames, footer: a.footer, filtersTrigger: a.filtersTrigger, emptyText: a.emptyText, pills: a.lifecyclePills.map((p) => p.href), showIncompleteHref: a.showIncomplete && a.showIncomplete.href, sortHrefs: a.headers.filter((h) => h.sortHref).map((h) => h.sortHref) };
        s6.appliedScreenshot = await shot(page, "06-filter-applied-1440-light");
        // + Show incomplete on top of the tag filter
        await page.locator('[data-testid="client-show-incomplete"]').click();
        await settle(page, (u) => new URL(u.toString()).searchParams.get("showIncomplete") === "true");
        const b = await snap(page);
        s6.appliedPlusIncomplete = { url: b.url, rowNames: b.rowNames, footer: b.footer, emptyText: b.emptyText, emptyStateText: b.emptyStateText, filtersTrigger: b.filtersTrigger };
        // Clear from the sheet
        await page.locator('[data-testid="client-filters-trigger"]').click();
        await page.getByRole("dialog").waitFor();
        await page.getByRole("dialog").getByRole("button", { name: "Clear", exact: true }).click();
        await settle(page, (u) => !new URL(u.toString()).searchParams.has("tags"));
        s6.afterSheetClear = { url: rel(page.url()) };
      } else {
        await page.keyboard.press("Escape");
      }
    }
    result.steps["6-filter-sheet"] = s6;
    save();

    // Step 7 — New Client button
    const s7 = {};
    await gotoList(page);
    for (const name of ["New Client"]) {
      const b = page.locator("#main-content").getByRole("button", { name, exact: true });
      const e = { count: await b.count() };
      if (e.count) {
        e.disabled = await b.first().isDisabled();
        if (!e.disabled) {
          await b.first().click();
          const d = page.getByRole("dialog");
          await d.waitFor({ timeout: 10_000 });
          e.dialogTitle = ((await d.getByRole("heading").first().textContent()) || "").trim();
          e.screenshot = await shot(page, "07-new-client-1440-light");
          await page.keyboard.press("Escape");
          await d.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
        }
      }
      s7[name] = e;
    }
    result.steps["7-create-buttons"] = s7;
    save();

    // Step 8 — keyboard: Tab from the search box to the first sort header link, Enter
    const s8 = { path: [] };
    await gotoList(page);
    await page.locator('[role="search"] input').focus();
    let tabs = 0;
    for (; tabs < 60; tabs++) {
      await page.keyboard.press("Tab");
      const f = await page.evaluate(() => {
        const el = document.activeElement;
        return { tag: el.tagName.toLowerCase(), text: (el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40), testid: el.getAttribute("data-testid"), href: el.getAttribute("href"), inThead: !!el.closest('[data-testid="clients-table"] thead') };
      });
      s8.path.push(f);
      if (f.inThead) break;
    }
    s8.tabsFromSearch = tabs + 1;
    s8.focused = await page.evaluate(() => ({ text: document.activeElement.textContent.replace(/[↑↓]/g, "").replace(/\s+/g, " ").trim(), href: document.activeElement.getAttribute("href"), tag: document.activeElement.tagName, outline: getComputedStyle(document.activeElement).outlineStyle, boxShadow: getComputedStyle(document.activeElement).boxShadow }));
    s8.focusScreenshot = await shot(page, "08-keyboard-focus-1440-light");
    await page.keyboard.press("Enter");
    await settle(page, (u) => rel(u.toString()) === s8.focused.href);
    const k = await snap(page);
    s8.after = { url: k.url, rowNames: k.rowNames };
    result.steps["8-keyboard"] = s8;
    save();

    // Step 9 — combined lifecycle + search + sort: query preservation
    const s9 = {};
    await gotoList(page, `?lifecycleStatus=ACTIVE&search=${encodeURIComponent(term)}&sortBy=name&sortDir=desc`);
    let c = await snap(page);
    s9.start = { url: c.url, rowNames: c.rowNames, footer: c.footer, sortHrefs: c.headers.filter((h) => h.sortHref).map((h) => ({ column: h.text, href: h.sortHref, visible: h.visible, ariaSort: h.ariaSort })), lifecyclePills: c.lifecyclePills, showIncompleteHref: c.showIncomplete && c.showIncomplete.href, searchValue: c.searchValue };
    const clicked = await clickSort(page, "FICA");
    c = await snap(page);
    s9.afterFicaSort = { clicked, url: c.url, rowNames: c.rowNames, footer: c.footer, searchValue: c.searchValue, activePill: (c.lifecyclePills.find((x) => x.active) || {}).label || null };
    await page.locator('[data-testid="client-lifecycle-filter"]').getByRole("link", { name: "All", exact: true }).click();
    await settle(page, (u) => !new URL(u.toString()).searchParams.has("lifecycleStatus"));
    c = await snap(page);
    s9.afterAllPill = { url: c.url, rowNames: c.rowNames, footer: c.footer };
    await page.locator('[role="search"] input').fill("");
    await settle(page, (u) => !new URL(u.toString()).searchParams.has("search"));
    c = await snap(page);
    s9.afterSearchCleared = { url: c.url, rowNames: c.rowNames, footer: c.footer };
    s9.screenshot = await shot(page, "09-combined-1440-light");
    result.steps["9-combined"] = s9;
    save();

    // Step 10 — empty variants (direct URLs)
    const s10 = {};
    for (const [key, qs] of [
      ["searchNoMatch", "?search=zzqx-no-such-client"],
      ["offboardedNone", "?lifecycleStatus=OFFBOARDED"],
      ["viewWithTrustBalance", "?view=system%3Awith-trust-balance"],
      ["viewFicaOutstanding", "?view=system%3Afica-outstanding"],
      ["showIncomplete", "?showIncomplete=true"],
      ["unknownView", "?view=00000000-0000-0000-0000-000000000000"],
      ["badSort", "?sortBy=contact&sortDir=desc"],
    ]) {
      await gotoList(page, qs);
      const a = await snap(page);
      s10[key] = { qs, ...pick(a, [...LISTED, "hasTable", "clearFiltersHref", "caption"]), selectedTab: (a.viewTabs.find((x) => x.selected) || {}).name, ariaSorts: a.headers.filter((h) => h.ariaSort && h.ariaSort !== "none").map((h) => [h.text, h.ariaSort]) };
      if (!a.hasTable) s10[key].screenshot = await shot(page, `10-empty-${key}-1440-light`);
    }
    result.steps["10-empty"] = s10;
    save();
    await context.close();

    // ------------------------------------------------------------ viewports × themes: default list + layout
    const extra = {};
    for (const [w, theme] of [[1440, "light"], [1440, "dark"], [1024, "light"], [1024, "dark"]]) {
      const x = await newContext(browser, w, theme);
      await gotoList(x.page);
      const a = await snap(x.page);
      const e = {
        url: a.url,
        rowNames: a.rowNames,
        footer: a.footer,
        visibleHeaders: a.headers.filter((h) => h.visible).map((h) => h.text),
        hiddenHeaders: a.headers.filter((h) => !h.visible).map((h) => h.text),
        layout: await x.page.evaluate(layout),
        screenshot: await shot(x.page, `01-default-${w}-${theme}`),
      };
      if (e.layout.table && e.layout.table.tableScrollsSideways) {
        await x.page.evaluate(() => { const sc = document.querySelector('[data-testid="clients-table"]').closest('[data-slot="table-container"]'); sc.scrollLeft = sc.scrollWidth; });
        e.scrolledRightScreenshot = await shot(x.page, `01-default-${w}-${theme}-scrolled-right`);
      }
      if (theme === "light") {
        // Forced-visible column (the active sort is never hidden) and the saved view (all columns).
        await gotoList(x.page, "?sortBy=createdAt&sortDir=asc");
        const f = await snap(x.page);
        e.sortedByCreated = { visibleHeaders: f.headers.filter((h) => h.visible).map((h) => h.text), layout: await x.page.evaluate(layout), screenshot: await shot(x.page, `01-sorted-created-${w}-light`) };
        const saved = a.viewTabs.find((t) => !t.system);
        if (saved) {
          await gotoList(x.page);
          await x.page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: saved.name, exact: true }).first().click();
          await settle(x.page, (u) => new URL(u.toString()).searchParams.has("view"));
          const sv = await snap(x.page);
          e.savedView = { url: sv.url, visibleHeaders: sv.headers.filter((h) => h.visible).map((h) => h.text), layout: await x.page.evaluate(layout), screenshot: await shot(x.page, `01-saved-view-${w}-light`) };
        }
      }
      extra[`${w}-${theme}`] = e;
      await x.context.close();
    }
    result.steps["1b-viewports"] = extra;
    save();
    } // !ONLY_CAROL

    // ------------------------------------------------------------ Carol (member): default list
    const carol = {};
    for (const w of [1440, 1024]) {
      const x = await newContext(browser, w, "light", "carol");
      // A member may get a permission page with no #main-content h1: do not require one.
      await x.page.goto(`${BASE}${LIST}`, { waitUntil: "load" });
      await x.page.locator("#main-content h1").first().waitFor({ timeout: 15_000 }).catch(() => {});
      await x.page.waitForLoadState("networkidle").catch(() => {});
      await x.page.waitForTimeout(800);
      const a = await snap(x.page);
      carol[`${w}-light`] = {
        ...pick(a, ["url", "h1", "subtitle", "hasTable", "rowNames", "rowFica", "rowCells", "footer", "permissionDenied", "viewTabs", "lifecyclePills", "filtersTrigger"]),
        headers: a.headers.map((h) => ({ text: h.text, visible: h.visible })),
        newClientButton: await x.page.locator("#main-content").getByRole("button", { name: "New Client", exact: true }).count(),
        saveViewButton: await x.page.locator("#main-content").getByRole("button", { name: "Save View" }).count(),
        layout: await x.page.evaluate(layout),
        screenshot: await shot(x.page, `11-carol-default-${w}-light`),
      };
      await x.context.close();
    }
    result.steps["11-carol"] = carol;
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
