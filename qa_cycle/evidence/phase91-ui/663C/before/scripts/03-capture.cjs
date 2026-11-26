// 663C OBS-before step 3: capture the pre-663C state of K-05, K-07, K-08, K-24, K-26, K-62/63/64 on the
// running e2e mock-auth stack (:3001), Playwright library (chromium.launch()), login through the shared
// 661B helper (Alice via /mock-login UI, Carol via the mock-auth-token cookie). Theme via localStorage
// `kazi-theme`, rail via cookie `sidebar_state`, reduced motion, timezone Africa/Johannesburg.
// Read-only: navigation, opening/closing menus, palette and selects with Escape. No form submits.
// Raw tiles + raw.json go to .tmp/663C-before-raw/; measurements.json + console.json to ../.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = path.join(C.W, ".tmp/663C-before-raw");
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const FEE_NOTE = "fbf447d0-8e0f-4125-99ba-c38cf0b78bec"; // INV-0005 (SENT), from 00-discover.cjs
const REPORT = "invoice-aging"; // first slug of Alice's /api/report-definitions (00-discover.cjs)
const TEAM_SPEC = "361f4148-7748-48f0-b49e-be7fe62a1fe0"; // "Team spec mwxbfat81651", 9 tasks (matters list)

const M = {}; // measurements
const consoleSink = [];
const tiles = []; // [key, file, caption]
const lab = { label: "" };

async function page(browser, state, opts, label) {
  const ctx = await C.newCtx(browser, state, { ...opts, extra: { ...EXTRA, ...(opts.extra || {}) } });
  const p = await ctx.newPage();
  lab.label = label;
  C.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function settle(p) {
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(400);
}
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
  return f;
};

// ---------- K-05 sidebar density ----------
const NAV_MEASURE = (rootSel) => {
  const root = document.querySelector(rootSel);
  if (!root) return { error: "no sidebar root " + rootSel };
  const sc = root.querySelector('[data-sidebar="content"]');
  const scr = sc.getBoundingClientRect();
  const fold = Math.min(scr.bottom, window.innerHeight);
  const nav = root.querySelector('nav[aria-label="Main navigation"]');
  const r = (e) => e.getBoundingClientRect();
  const groups = [...nav.querySelectorAll('[data-sidebar="group-label"]')].map((e) => ({
    label: e.textContent.trim(),
    top: Math.round(r(e).top),
    bottom: Math.round(r(e).bottom),
    h: Math.round(r(e).height),
    expanded: e.getAttribute("aria-expanded"),
    fullyAboveFold: r(e).bottom <= fold,
    topAboveFold: r(e).top < fold,
  }));
  const items = [...nav.querySelectorAll('a[data-sidebar="menu-button"]')].filter((a) => r(a).height > 0);
  const it = (a) => (a ? { label: a.textContent.trim(), top: Math.round(r(a).top), bottom: Math.round(r(a).bottom), h: Math.round(r(a).height) } : null);
  const gaps = items.slice(1).map((a, i) => Math.round(r(a).top - r(items[i]).bottom)).filter((g) => g >= 0 && g < 20);
  const seps = [...nav.querySelectorAll('[data-sidebar="separator"]')].map((s) => { const cs = getComputedStyle(s); return Math.round(r(s).height + parseFloat(cs.marginTop) + parseFloat(cs.marginBottom)); });
  const grp = [...nav.querySelectorAll('[data-sidebar="group"]')].map((g) => getComputedStyle(g).paddingTop + "/" + getComputedStyle(g).paddingBottom);
  return {
    viewport: [window.innerWidth, window.innerHeight],
    scroll: { top: Math.round(scr.top), bottom: Math.round(scr.bottom), fold: Math.round(fold), clientHeight: sc.clientHeight, scrollHeight: sc.scrollHeight, overflow: sc.scrollHeight - sc.clientHeight },
    groups,
    itemCount: items.length,
    firstItem: it(items[0]),
    lastItem: it(items[items.length - 1]),
    itemHeights: [...new Set(items.map((a) => Math.round(r(a).height)))],
    itemGaps: [...new Set(gaps)],
    separatorBlock: [...new Set(seps)],
    groupPadding: [...new Set(grp)],
    groupsBelowFold: groups.filter((g) => !g.fullyAboveFold).map((g) => g.label),
  };
};
const DESKTOP_ROOT = '[data-slot="sidebar-container"] [data-sidebar="sidebar"]';
const MOBILE_ROOT = '[role="dialog"][data-mobile="true"]';
async function expandAll(p, rootSel) {
  for (let i = 0; i < 8; i++) {
    const closed = p.locator(`${rootSel} nav[aria-label="Main navigation"] [data-sidebar="group-label"][aria-expanded="false"]`);
    if ((await closed.count()) === 0) break;
    await closed.first().click();
    await p.waitForTimeout(150);
  }
}
async function k05(browser, S, who) {
  M.K05 = M.K05 || {};
  const o = (M.K05[who] = {});
  for (const [w, h] of [[1440, 900], [1024, 768]]) {
    const { ctx, p } = await page(browser, S[who], { width: w, height: h }, `K05|${who}|${w}|light`);
    await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
    await settle(p);
    o[`${w}-default`] = await p.evaluate(NAV_MEASURE, DESKTOP_ROOT);
    if (w === 1440) await shot(p, `k05__${who}-1440-default`, `${who} 1440×900 default groups`);
    await expandAll(p, DESKTOP_ROOT);
    // expanding scrolls the rail (the clicked trigger is kept in view): measure from scrollTop 0
    await p.evaluate((s) => { document.querySelector(s + ' [data-sidebar="content"]').scrollTop = 0; }, DESKTOP_ROOT);
    await p.mouse.move(1300, 10);
    await p.waitForTimeout(200);
    o[`${w}-allOpen`] = await p.evaluate(NAV_MEASURE, DESKTOP_ROOT);
    if (w === 1440) await shot(p, `k05__${who}-1440-allopen`, `${who} 1440×900 all groups open`);
    await ctx.close();
  }
  const { ctx, p } = await page(browser, S[who], { width: 390, height: 844 }, `K05|${who}|390|light`);
  await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
  await settle(p);
  await p.getByRole("button", { name: "Toggle menu", exact: true }).first().click();
  await p.locator(MOBILE_ROOT).waitFor();
  await p.waitForTimeout(500);
  o["390-sheet-default"] = await p.evaluate(NAV_MEASURE, MOBILE_ROOT);
  await shot(p, `k05__${who}-390-sheet`, `${who} 390×844 Sheet, default groups, scrollTop 0`);
  await p.evaluate((s) => { const sc = document.querySelector(s + ' [data-sidebar="content"]'); sc.scrollTop = sc.scrollHeight; }, MOBILE_ROOT);
  await p.waitForTimeout(200);
  await shot(p, `k05__${who}-390-sheet-bottom`, `${who} 390×844 Sheet, scrolled to bottom`);
  await p.evaluate((s) => { document.querySelector(s + ' [data-sidebar="content"]').scrollTop = 0; }, MOBILE_ROOT);
  await expandAll(p, MOBILE_ROOT);
  await p.evaluate((s) => { document.querySelector(s + ' [data-sidebar="content"]').scrollTop = 0; }, MOBILE_ROOT);
  await p.waitForTimeout(200);
  o["390-sheet-allOpen"] = await p.evaluate(NAV_MEASURE, MOBILE_ROOT);
  await ctx.close();
}

// ---------- K-07 palette focus ----------
const AE = () => {
  const a = document.activeElement;
  if (!a) return null;
  return {
    tag: a.tagName.toLowerCase(),
    ariaLabel: a.getAttribute("aria-label"),
    text: (a.textContent || "").trim().slice(0, 40),
    testid: a.getAttribute("data-testid"),
    dataSidebar: a.getAttribute("data-sidebar"),
    inDialog: !!a.closest('[role="dialog"]'),
    isBody: a === document.body,
  };
};
const HEADER_BTN = 'button[aria-label="Search, Command K"]:not([data-sidebar])';
const PILL_BTN = 'button[aria-label="Search, Command K"][data-sidebar="menu-button"]';
const PALETTE = '[role="dialog"] [cmdk-input]';
async function k07(browser, S, who, theme) {
  M.K07 = M.K07 || {};
  const o = (M.K07[`${who}-${theme}`] = {});
  const trials = [
    ["header-click", async (p) => p.locator(HEADER_BTN).click()],
    ["header-keyboard-enter", async (p) => { await p.locator(HEADER_BTN).focus(); o.__before = await p.evaluate(AE); await p.keyboard.press("Enter"); }],
    ["pill-click", async (p) => p.locator(PILL_BTN).click()],
    ["pill-keyboard-enter", async (p) => { await p.locator(PILL_BTN).focus(); o.__before = await p.evaluate(AE); await p.keyboard.press("Enter"); }],
    ["cmdk-from-notifications", async (p) => { await p.getByRole("button", { name: "Notifications" }).first().focus(); o.__before = await p.evaluate(AE); await p.keyboard.press("Meta+k"); }],
    ["cmdk-from-body", async (p) => { o.__before = await p.evaluate(AE); await p.keyboard.press("Meta+k"); }],
  ];
  for (const [name, open] of trials) {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme }, `K07|${who}|1440|${theme}|${name}`);
    await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
    await settle(p);
    delete o.__before;
    const pre = await p.evaluate(AE);
    await open(p);
    let opened = true;
    await p.locator(PALETTE).waitFor({ timeout: 5000 }).catch(() => { opened = false; });
    await p.waitForTimeout(250);
    const atOpen = await p.evaluate(AE);
    const shootOpen = theme === "light" || name === "header-click";
    if (shootOpen && ["header-click", "pill-click", "cmdk-from-notifications"].includes(name)) await shot(p, `k07__${who}-${theme}-${name}-open`, `${who} ${theme} ${name}: open`);
    await p.keyboard.press("Escape");
    await p.waitForTimeout(500);
    const afterClose = await p.evaluate(AE);
    const stillOpen = (await p.locator(PALETTE).count()) > 0;
    o[name] = { beforeOpen: o.__before || pre, opened, atOpen, afterClose, stillOpen };
    if (["header-click", "pill-click", "cmdk-from-notifications", "header-keyboard-enter"].includes(name))
      await shot(p, `k07__${who}-${theme}-${name}-closed`, `${who} ${theme} ${name}: after Escape, activeElement=${afterClose.isBody ? "body" : afterClose.tag + " " + (afterClose.ariaLabel || afterClose.text)}`);
    await ctx.close();
  }
  delete o.__before;
}

// ---------- K-08 sensitive events ----------
async function k08(browser, S, who, w, theme) {
  M.K08 = M.K08 || {};
  const { ctx, p } = await page(browser, S[who], { width: w, height: w === 1440 ? 900 : 768, theme }, `K08|${who}|${w}|${theme}`);
  await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
  await settle(p);
  const wid = p.locator('[data-testid="sensitive-events-widget"]');
  const present = (await wid.count()) > 0;
  const m = { present, audience: await p.locator('[data-testid="home-page"]').getAttribute("data-audience").catch(() => null) };
  if (present) {
    await wid.scrollIntoViewIfNeeded();
    Object.assign(m, await wid.evaluate((e) => ({
      h: Math.round(e.getBoundingClientRect().height),
      w: Math.round(e.getBoundingClientRect().width),
      counts: [...e.querySelectorAll('[data-testid^="sensitive-count-"]')].map((c) => c.dataset.testid.replace("sensitive-count-", "") + "=" + c.textContent.trim()),
      rows: e.querySelectorAll('[data-testid^="sensitive-row-"]').length,
      empty: !!e.querySelector('[data-testid="sensitive-events-empty"]'),
      errorLine: /could not be loaded/i.test(e.textContent),
      text: e.textContent.replace(/\s+/g, " ").trim().slice(0, 300),
    })));
    const f = path.join(RAW, `k08__${who}-${w}-${theme}.png`);
    await wid.screenshot({ path: f });
    tiles.push([`k08__${who}-${w}-${theme}`, f, `${who} ${w} ${theme}: rows=${m.rows} empty=${m.empty}`]);
  }
  M.K08[`${who}-${w}-${theme}`] = m;
  await ctx.close();
}

// ---------- K-24 focused menu/select item icons ----------
const FOCUS_MEASURE = (sel) => {
  const a = document.activeElement;
  const item = a && a.matches(sel) ? a : document.querySelector(sel + "[data-highlighted]");
  if (!item) return { error: "no focused item", ae: a && a.outerHTML.slice(0, 120) };
  const content = item.closest('[data-slot$="-content"]') || item.parentElement;
  const cBg = getComputedStyle(content).backgroundColor;
  const iBg = getComputedStyle(item).backgroundColor;
  const bg = window.__rgb(iBg, cBg); // item fill composited over the popover fill (over white)
  const bgCss = `rgb(${bg.join(",")})`;
  const svgs = [...item.querySelectorAll("svg")].map((s) => ({ cls: s.getAttribute("class"), color: window.__rgb(getComputedStyle(s).color, bgCss), rawColor: getComputedStyle(s).color, inIndicator: !!s.closest('[data-slot$="-item-indicator"], [data-radix-select-item-indicator], span.absolute') }));
  const r = item.getBoundingClientRect();
  const cr = content.getBoundingClientRect();
  return {
    text: item.textContent.trim(),
    role: item.getAttribute("role"),
    itemBgRaw: iBg,
    contentBgRaw: cBg,
    bg,
    label: window.__rgb(getComputedStyle(item).color, bgCss),
    svgs,
    itemRect: [r.x, r.y, r.width, r.height].map(Math.round),
    contentRect: [cr.x, cr.y, cr.width, cr.height].map(Math.round),
  };
};
function addRatios(m) {
  if (m.error) return m;
  m.bgHex = C.hex3(m.bg);
  m.labelHex = C.hex3(m.label);
  m.labelRatio = C.ratio(m.label, m.bg);
  m.svgs = m.svgs.map((s) => ({ ...s, hex: C.hex3(s.color), ratio: C.ratio(s.color, m.bg) }));
  return m;
}
async function k24(browser, S, who, theme) {
  M.K24 = M.K24 || {};
  const o = (M.K24[`${who}-${theme}`] = {});
  // (a) DropdownMenu: matters list row menu (ExternalLink / UserRound icons, no text- class).
  {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme, extra: { deviceScaleFactor: 2 } }, `K24|${who}|1440|${theme}|menu`);
    await p.goto(`${BASE}/matters`, { waitUntil: "load" });
    await settle(p);
    await p.mouse.move(0, 0);
    // a row whose menu has 2 items (has a client)
    const triggers = p.locator('[data-testid="matter-row-actions-trigger"]');
    await triggers.first().focus();
    await p.keyboard.press("Enter");
    await p.locator('[role="menu"]').waitFor();
    await p.waitForTimeout(300);
    o.menuFirst = addRatios(await p.evaluate(FOCUS_MEASURE, '[role="menuitem"]'));
    const cr = o.menuFirst.contentRect || [1100, 200, 200, 100];
    const clip = { x: Math.max(0, cr[0] - 24), y: Math.max(0, cr[1] - 48), width: cr[2] + 48, height: cr[3] + 72 };
    await shot(p, `k24__${who}-${theme}-menu-first`, `${who} ${theme} row menu, keyboard focus "${o.menuFirst.text}"`, { clip });
    if ((await p.locator('[role="menuitem"]').count()) > 1) {
      await p.keyboard.press("ArrowDown");
      await p.waitForTimeout(200);
      o.menuSecond = addRatios(await p.evaluate(FOCUS_MEASURE, '[role="menuitem"]'));
      await shot(p, `k24__${who}-${theme}-menu-second`, `${who} ${theme} row menu, ArrowDown → "${o.menuSecond.text}"`, { clip });
    }
    await p.keyboard.press("Escape");
    await ctx.close();
  }
  // (b) Select: task status select in the task detail Sheet (item icons size-3, no text- class).
  {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme, extra: { deviceScaleFactor: 2 } }, `K24|${who}|1440|${theme}|select`);
    await p.goto(`${BASE}/matters/${TEAM_SPEC}?tab=tasks`, { waitUntil: "load" });
    await settle(p);
    await p.locator("#main-content tbody tr").first().getByRole("button").first().click();
    await p.getByRole("dialog").waitFor();
    await settle(p);
    await p.mouse.move(0, 0);
    const cb = p.locator('[role="dialog"] [role="combobox"]').filter({ hasText: /^(Open|In Progress|Done|Cancelled)$/ }).first();
    o.selectTrigger = (await cb.textContent()).trim();
    await cb.focus();
    await p.keyboard.press("Enter");
    await p.locator('[role="listbox"]').waitFor();
    await p.waitForTimeout(300);
    o.selectFirst = addRatios(await p.evaluate(FOCUS_MEASURE, '[role="option"]'));
    const cr = o.selectFirst.contentRect || [900, 100, 200, 100];
    const clip = { x: Math.max(0, cr[0] - 24), y: Math.max(0, cr[1] - 56), width: cr[2] + 48, height: cr[3] + 80 };
    await shot(p, `k24__${who}-${theme}-select-first`, `${who} ${theme} task-status select, focus "${o.selectFirst.text}"`, { clip });
    await p.keyboard.press("ArrowDown");
    await p.waitForTimeout(200);
    o.selectSecond = addRatios(await p.evaluate(FOCUS_MEASURE, '[role="option"]'));
    await shot(p, `k24__${who}-${theme}-select-second`, `${who} ${theme} task-status select, ArrowDown → "${o.selectSecond.text}"`, { clip });
    await p.keyboard.press("Escape"); // closes the listbox without choosing — status unchanged
    await p.waitForTimeout(200);
    o.selectTriggerAfter = (await cb.textContent().catch(() => "")).trim();
    await ctx.close();
  }
}

// ---------- K-26 breadcrumbs ----------
const CRUMB_MEASURE = () => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  if (!nav) return { error: "no breadcrumb nav" };
  const r = (e) => e.getBoundingClientRect();
  const org = nav.querySelector(":scope > a");
  const lh = parseFloat(getComputedStyle(org).lineHeight) || 20;
  const header = nav.closest("header");
  const kids = [...nav.children].filter((e) => r(e).width > 0);
  return {
    viewport: [window.innerWidth, window.innerHeight],
    navH: Math.round(r(nav).height),
    navW: Math.round(r(nav).width),
    headerH: header ? Math.round(r(header).height) : null,
    org: { text: org.textContent.trim(), w: Math.round(r(org).width), h: Math.round(r(org).height), lines: Math.round(r(org).height / lh), title: org.getAttribute("title") },
    crumbs: kids.map((e) => ({ text: e.textContent.trim().slice(0, 40), w: Math.round(r(e).width), h: Math.round(r(e).height), lines: Math.round(r(e).height / lh) })),
    wraps: kids.some((e) => r(e).height > lh * 1.5),
    lineHeight: lh,
  };
};
async function k26(browser, S, who, pages) {
  M.K26 = M.K26 || {};
  for (const [pk, url] of pages) {
    for (const collapsed of [false, true]) {
      for (const theme of ["light", "dark"]) {
        const rail = collapsed ? "collapsed" : "expanded";
        const { ctx, p } = await page(browser, S[who], { width: 1024, height: 768, theme, collapsed }, `K26|${who}|1024|${theme}|${rail}|${pk}`);
        const resp = await p.goto(url, { waitUntil: "load" });
        await settle(p);
        const m = await p.evaluate(CRUMB_MEASURE);
        m.status = resp && resp.status();
        m.url = p.url().replace(C.APP, "");
        M.K26[`${who}-${pk}-${rail}-${theme}`] = m;
        await shot(p, `k26__${who}-${pk}-${rail}-${theme}`, `${who} ${pk} 1024 rail ${rail} ${theme}: nav ${m.navH}px, org ${m.org ? m.org.w + "×" + m.org.h : "?"}`, { clip: { x: 0, y: 0, width: 1024, height: 96 } });
        await ctx.close();
      }
    }
  }
}

// ---------- K-62/63/64 member pages ----------
const MAIN_MEASURE = () => {
  const main = document.querySelector("#main-content");
  if (!main) return { error: "no #main-content" };
  const t = main.textContent.replace(/\s+/g, " ").trim();
  return {
    h1Count: main.querySelectorAll("h1").length,
    h1: [...main.querySelectorAll("h1")].map((h) => h.textContent.trim()),
    h2: [...main.querySelectorAll("h2")].slice(0, 3).map((h) => h.textContent.trim()),
    crashPanel: /Something went wrong/i.test(t),
    noAccess: /don.t have access/i.test(t),
    notFound: /not found|could not be found|doesn.t exist/i.test(t),
    text: t.slice(0, 240),
  };
};
async function memberPages(browser, S, who, list, theme = "light") {
  M.K62_64 = M.K62_64 || {};
  for (const [pk, rel] of list) {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme }, `K62-64|${who}|1440|${theme}|${pk}`);
    const resp = await p.goto(`${BASE}${rel}`, { waitUntil: "load" });
    await settle(p);
    const m = await p.evaluate(MAIN_MEASURE);
    m.status = resp && resp.status();
    m.url = p.url().replace(C.APP, "");
    M.K62_64[`${who}-${pk}`] = m;
    await shot(p, `k6x__${who}-${pk}`, `${who} ${rel} — HTTP ${m.status}, h1×${m.h1Count}${m.h1[0] ? ' "' + m.h1[0] + '"' : ""}${m.crashPanel ? ", crash panel" : ""}${m.noAccess ? ", no access" : ""}`);
    await ctx.close();
  }
}

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const step = async (name, f) => {
    try {
      await f();
      console.log("OK", name);
    } catch (e) {
      console.log("FAIL", name, e.message.split("\n")[0]);
      (M.failures = M.failures || []).push({ step: name, error: e.message.split("\n")[0].slice(0, 200) });
    }
  };
  for (const who of ["alice", "carol"]) await step(`K05 ${who}`, () => k05(browser, S, who));
  for (const [who, theme] of [["alice", "light"], ["alice", "dark"], ["carol", "light"]]) await step(`K07 ${who} ${theme}`, () => k07(browser, S, who, theme));
  for (const w of [1440, 1024]) for (const theme of ["light", "dark"]) await step(`K08 alice ${w} ${theme}`, () => k08(browser, S, "alice", w, theme));
  await step("K08 carol 1440 light", () => k08(browser, S, "carol", 1440, "light"));
  for (const theme of ["light", "dark"]) await step(`K24 alice ${theme}`, () => k24(browser, S, "alice", theme));
  await step("K26 alice", () => k26(browser, S, "alice", [["fee-note", `${BASE}/invoices/${FEE_NOTE}`], ["trust-transactions", `${BASE}/trust-accounting/transactions`]]));
  await step("K26 carol", async () => {
    const { ctx, p } = await page(browser, S.carol, { width: 1440, height: 900 }, "K26|carol|discover-matter");
    await p.goto(`${BASE}/matters`, { waitUntil: "load" });
    await settle(p);
    const href = await p.locator('#main-content a[href*="/matters/"]').first().getAttribute("href");
    await ctx.close();
    M.carolMatter = href;
    await k26(browser, S, "carol", [["trust-transactions", `${BASE}/trust-accounting/transactions`], ["matter", `${C.APP}${href}`]]);
  });
  await step("K62-64 carol", () =>
    memberPages(browser, S, "carol", [
      ["clients", "/clients"],
      ["clients-fica-review-due", "/clients?view=system:fica-review-due"],
      ["invoices", "/invoices"],
      ["invoices-collections", "/invoices/collections"],
      ["invoices-unbilled", "/invoices/unbilled"],
      ["compliance", "/compliance"],
      ["invoice-detail", `/invoices/${FEE_NOTE}`],
      ["report-runner", `/reports/${REPORT}`],
      ["reports-index", "/reports"],
    ]),
  );
  await step("K62-64 alice control", () => memberPages(browser, S, "alice", [["clients-fica-review-due", "/clients?view=system:fica-review-due"], ["report-runner", `/reports/${REPORT}`]]));
  await browser.close();

  fs.writeFileSync(path.join(RAW, "tiles.json"), JSON.stringify(tiles.map(([k, f, c]) => [k, C.rel(f), c])));
  fs.writeFileSync(path.join(OUT, "measurements.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack: "e2e :3001 (frontend = main @ 3bc2110dc)", ...M }) + "\n");
  const labels = [...new Set(consoleSink.map((c) => c.label))];
  fs.writeFileSync(
    path.join(OUT, "console.json"),
    JSON.stringify({ pagesOpened: tiles.length, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, labelsWithMessages: labels, messages: consoleSink }) + "\n",
  );
  console.log("tiles", tiles.length, "console", consoleSink.length, "failures", (M.failures || []).length);
})();
