// 663C OBS-after step 5 (NEW, after-only): probes the before pass did not take, run after 03-capture.cjs
// on the same stack with the same helpers / context options (661B common.cjs, reduced motion, timezone
// Africa/Johannesburg, theme via localStorage `kazi-theme`, rail via cookie `sidebar_state`).
//   K-05: full ordered nav list (zone label + aria-expanded, item label + href) — 1440 rail and 390 Sheet,
//         Alice and Carol — and the 1440 default-state geometry in dark.
//   K-07: keyboard-only (Tab from the top of the page, no mouse) open/close from the header button and the
//         pill; DA-13: open from the header button / pill, type "Clients", Enter → where focus lands.
//   K-26: a long crumb chain at 1024 (/settings/documents/templates/[id]/edit), rail expanded/collapsed × theme.
//   K-62/63/64: Carol dark on /clients and /invoices; "Back to Home" on the in-shell 404 (keyboard + click);
//         Alice's real matter 404. SEL-COMPAT over every touched page → ../sel-compat.json.
// Read-only: navigation, opening/closing the palette and following links. No form submits, no typing into
// any form (the only typing is the palette's search input). Merges into ../measurements.json (key
// "extra") and ../console.json (key "extra"); raw tiles to .tmp/663C-after-raw/ (tiles-extra.json).
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = path.join(C.W, ".tmp/663C-after-raw");
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const FEE_NOTE = "83ca9f03-9d78-4832-9185-90454dd36939"; // INV-0005 (SENT), 01-discover-ids.cjs
const TEAM_SPEC = "5e9c0144-b197-4592-859a-bcc6fb28b1d6"; // "Team spec mwxeos7x3f94" (01-discover-ids.cjs)
const TEMPLATE = "a65f3949-02a5-463e-a50a-eb4509bdb222"; // "Power of Attorney to Pass Transfer" (01-discover-ids.cjs)
const REPORT = "invoice-aging";
const NIL = "00000000-0000-0000-0000-000000000000";

const X = {};
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;
async function page(browser, state, opts, label) {
  const ctx = await C.newCtx(browser, state, { ...opts, extra: { ...EXTRA, ...(opts.extra || {}) } });
  const p = await ctx.newPage();
  pagesOpened++;
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
};

// ---------- K-05 ----------
const DESKTOP_ROOT = '[data-slot="sidebar-container"] [data-sidebar="sidebar"]';
const MOBILE_ROOT = '[role="dialog"][data-mobile="true"]';
const NAV_LIST = (rootSel) => {
  const nav = document.querySelector(rootSel + ' nav[aria-label="Main navigation"]');
  if (!nav) return { error: "no nav" };
  return {
    zones: [...nav.querySelectorAll('[data-sidebar="group-label"]')].map((e) => `${e.textContent.trim()}:${e.getAttribute("aria-expanded")}`),
    links: [...nav.querySelectorAll('a[data-sidebar="menu-button"]')].map((a) => `${a.textContent.trim()} ${new URL(a.href).pathname.replace(/^\/org\/[^/]+/, "")}`),
  };
};
// Same geometry fields as 03-capture.cjs NAV_MEASURE (copied), for the dark default state.
const GEO = (rootSel) => {
  const root = document.querySelector(rootSel);
  const sc = root.querySelector('[data-sidebar="content"]');
  const scr = sc.getBoundingClientRect();
  const fold = Math.min(scr.bottom, window.innerHeight);
  const nav = root.querySelector('nav[aria-label="Main navigation"]');
  const r = (e) => e.getBoundingClientRect();
  const items = [...nav.querySelectorAll('a[data-sidebar="menu-button"]')].filter((a) => r(a).height > 0);
  return {
    fold: Math.round(fold),
    overflow: sc.scrollHeight - sc.clientHeight,
    groups: [...nav.querySelectorAll('[data-sidebar="group-label"]')].map((e) => `${e.textContent.trim()} ${Math.round(r(e).top)}-${Math.round(r(e).bottom)}${r(e).bottom <= fold ? "" : " *"}`),
    itemHeights: [...new Set(items.map((a) => Math.round(r(a).height)))],
    itemGaps: [...new Set(items.slice(1).map((a, i) => Math.round(r(a).top - r(items[i]).bottom)).filter((g) => g >= 0 && g < 20))],
  };
};
async function expandAll(p, rootSel) {
  for (let i = 0; i < 8; i++) {
    const closed = p.locator(`${rootSel} nav[aria-label="Main navigation"] [data-sidebar="group-label"][aria-expanded="false"]`);
    if ((await closed.count()) === 0) break;
    await closed.first().click();
    await p.waitForTimeout(150);
  }
}
async function k05(browser, S, who) {
  const o = ((X.K05 = X.K05 || {})[who] = {});
  {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900 }, `K05x|${who}|1440|light`);
    await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
    await settle(p);
    o.rail_default = await p.evaluate(NAV_LIST, DESKTOP_ROOT);
    await expandAll(p, DESKTOP_ROOT);
    o.rail_allOpen = await p.evaluate(NAV_LIST, DESKTOP_ROOT);
    await ctx.close();
  }
  {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme: "dark" }, `K05x|${who}|1440|dark`);
    await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
    await settle(p);
    o.dark1440_default = await p.evaluate(GEO, DESKTOP_ROOT);
    await shot(p, `k05x__${who}-1440-dark-default`, `${who} 1440×900 dark default groups: ${o.dark1440_default.groups.join(", ")}`, { clip: { x: 0, y: 0, width: 420, height: 900 } });
    await ctx.close();
  }
  {
    const { ctx, p } = await page(browser, S[who], { width: 390, height: 844 }, `K05x|${who}|390|light`);
    await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
    await settle(p);
    await p.getByRole("button", { name: "Toggle menu", exact: true }).first().click();
    await p.locator(MOBILE_ROOT).waitFor();
    await p.waitForTimeout(500);
    o.sheet_default = await p.evaluate(NAV_LIST, MOBILE_ROOT);
    await expandAll(p, MOBILE_ROOT);
    o.sheet_allOpen = await p.evaluate(NAV_LIST, MOBILE_ROOT);
    await ctx.close();
  }
  o.sheetEqualsRail = JSON.stringify(o.sheet_allOpen.links) === JSON.stringify(o.rail_allOpen.links);
}

// ---------- K-07 keyboard-only + DA-13 ----------
const AE = () => {
  const a = document.activeElement;
  if (!a) return null;
  return { tag: a.tagName.toLowerCase(), ariaLabel: a.getAttribute("aria-label"), text: (a.textContent || "").trim().slice(0, 40), dataSidebar: a.getAttribute("data-sidebar"), inDialog: !!a.closest('[role="dialog"]'), isBody: a === document.body, focusVisible: a.matches(":focus-visible") };
};
const IS_TARGET = (kind) => {
  const a = document.activeElement;
  if (!a || a.getAttribute("aria-label") !== "Search, Command K") return false;
  return kind === "pill" ? a.getAttribute("data-sidebar") === "menu-button" : !a.hasAttribute("data-sidebar");
};
const PALETTE = '[role="dialog"] [cmdk-input]';
async function tabTo(p, kind) {
  await p.evaluate(() => { document.activeElement && document.activeElement.blur(); window.scrollTo(0, 0); });
  for (let i = 1; i <= 120; i++) {
    await p.keyboard.press("Tab");
    if (await p.evaluate(IS_TARGET, kind)) return i;
  }
  return null;
}
async function k07(browser, S, who, theme) {
  const o = ((X.K07 = X.K07 || {})[`${who}-${theme}`] = {});
  for (const kind of ["header", "pill"]) {
    // (1) keyboard-only open/close: Tab to the trigger, Enter, Escape.
    {
      const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme }, `K07x|${who}|${theme}|${kind}-tab`);
      await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
      await settle(p);
      const tabs = await tabTo(p, kind);
      const before = await p.evaluate(AE);
      await p.keyboard.press("Enter");
      let opened = true;
      await p.locator(PALETTE).waitFor({ timeout: 5000 }).catch(() => { opened = false; });
      const atOpen = await p.evaluate(AE);
      await p.keyboard.press("Escape");
      await p.waitForTimeout(500);
      const afterClose = await p.evaluate(AE);
      o[`${kind}-tab-enter-escape`] = { tabs, before, opened, atOpen, afterClose, stillOpen: (await p.locator(PALETTE).count()) > 0 };
      if (theme === "light") await shot(p, `k07x__${who}-${theme}-${kind}-tab-closed`, `${who} ${theme} ${kind}: Tab×${tabs}, Enter, Escape → ${afterClose.isBody ? "body" : afterClose.tag + " " + (afterClose.ariaLabel || afterClose.text) + (afterClose.dataSidebar ? " [pill]" : "")}${afterClose.focusVisible ? " (:focus-visible)" : ""}`, { clip: kind === "pill" ? { x: 0, y: 0, width: 420, height: 200 } : { x: 0, y: 0, width: 1440, height: 120 } });
      await ctx.close();
    }
    // (2) DA-13: open by keyboard, type "Clients", Enter → navigation; where does focus land?
    {
      const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme }, `K07x|${who}|${theme}|${kind}-navigate`);
      await p.goto(`${BASE}/dashboard`, { waitUntil: "load" });
      await settle(p);
      const tabs = await tabTo(p, kind);
      await p.keyboard.press("Enter");
      await p.locator(PALETTE).waitFor({ timeout: 5000 });
      await p.keyboard.type(who === "carol" ? "Matters" : "Clients");
      await p.waitForTimeout(300);
      const selected = await p.locator('[role="dialog"] [cmdk-item][data-selected="true"]').first().textContent().catch(() => null);
      await p.keyboard.press("Enter");
      await p.waitForURL(who === "carol" ? /\/matters/ : /\/clients/, { timeout: 15000 }).catch(() => {});
      await settle(p);
      const afterNav = await p.evaluate(AE);
      o[`${kind}-navigate`] = { tabs, typed: who === "carol" ? "Matters" : "Clients", selected: selected && selected.trim(), url: p.url().replace(C.APP, ""), paletteOpen: (await p.locator(PALETTE).count()) > 0, afterNav };
      if (theme === "light") await shot(p, `k07x__${who}-${theme}-${kind}-navigate`, `${who} ${theme} ${kind}: palette → "${selected && selected.trim()}" Enter → ${o[`${kind}-navigate`].url.replace(/^\/org\/[^/]+/, "")}; focus ${afterNav.isBody ? "body" : afterNav.tag + " " + (afterNav.ariaLabel || afterNav.text) + (afterNav.dataSidebar ? " [pill]" : "")}`, { clip: { x: 0, y: 0, width: 1440, height: 260 } });
      await ctx.close();
    }
  }
}

// ---------- K-26 long chain ----------
const CRUMB_MEASURE = () => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  if (!nav) return { error: "no breadcrumb nav" };
  const r = (e) => e.getBoundingClientRect();
  const org = nav.querySelector(":scope > a");
  const lh = parseFloat(getComputedStyle(org).lineHeight) || 20;
  const kids = [...nav.children].filter((e) => r(e).width > 0);
  const inner = (e) => e.matches("a,span") && !e.children.length ? e : [...e.querySelectorAll("a,span")].pop();
  return {
    navH: Math.round(r(nav).height),
    navW: Math.round(r(nav).width),
    org: { text: org.textContent.trim(), w: Math.round(r(org).width), h: Math.round(r(org).height), title: org.getAttribute("title"), truncated: org.scrollWidth > org.clientWidth },
    crumbs: kids.slice(1).map((e) => { const t = inner(e) || e; return { text: e.textContent.trim().slice(0, 40), w: Math.round(r(e).width), h: Math.round(r(e).height), title: t.getAttribute("title"), truncated: t.scrollWidth > t.clientWidth }; }),
    lastVisible: (() => { const l = kids[kids.length - 1]; const b = r(l); return b.width > 0 && b.right <= window.innerWidth; })(),
    wraps: kids.some((e) => r(e).height > lh * 1.5),
  };
};
async function k26(browser, S, who, pk, url) {
  X.K26 = X.K26 || {};
  for (const collapsed of [false, true])
    for (const theme of ["light", "dark"]) {
      const rail = collapsed ? "collapsed" : "expanded";
      const { ctx, p } = await page(browser, S[who], { width: 1024, height: 768, theme, collapsed }, `K26x|${who}|${theme}|${rail}|${pk}`);
      const resp = await p.goto(url, { waitUntil: "load" });
      await settle(p);
      const m = await p.evaluate(CRUMB_MEASURE);
      m.status = resp && resp.status();
      X.K26[`${who}-${pk}-${rail}-${theme}`] = m;
      await shot(p, `k26x__${who}-${pk}-${rail}-${theme}`, `${who} ${pk} 1024 rail ${rail} ${theme}: nav ${m.navH}px, org ${m.org ? m.org.w + "×" + m.org.h : "?"}, crumbs ${m.crumbs ? m.crumbs.map((c) => c.w + (c.truncated ? "…" : "")).join("/") : "?"}`, { clip: { x: 0, y: 0, width: 1024, height: 96 } });
      await ctx.close();
    }
}

// ---------- K-62/63/64 extras + SEL-COMPAT ----------
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
    orgNotFound: !!main.querySelector('[data-testid="org-not-found"]'),
    text: t.slice(0, 200),
  };
};
const SEL = () => ({
  mainMainContent: document.querySelectorAll("main#main-content").length,
  mainNav: document.querySelectorAll('nav[aria-label="Main navigation"]').length,
  h1InMain: document.querySelectorAll("#main-content h1").length,
  h1Total: document.querySelectorAll("h1").length,
  breadcrumbNav: document.querySelectorAll('nav[aria-label="Breadcrumb"]').length,
});
async function selCompat(browser, S, who, list) {
  X.SEL = X.SEL || {};
  for (const [pk, rel] of list) {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900 }, `SEL|${who}|${pk}`);
    const resp = await p.goto(`${BASE}${rel}`, { waitUntil: "load" });
    await settle(p);
    const m = await p.evaluate(SEL);
    m.status = resp && resp.status();
    m.url = rel;
    if (pk === "dashboard") {
      await p.keyboard.press("Meta+k");
      await p.locator(PALETTE).waitFor({ timeout: 5000 }).catch(() => {});
      m.paletteDialogByRoleName = await p.getByRole("dialog", { name: "Command Palette", exact: true }).count();
      m.paletteInputRole = await p.locator(PALETTE).getAttribute("role").catch(() => null);
      await p.keyboard.press("Escape");
    }
    X.SEL[`${who}-${pk}`] = m;
    await ctx.close();
  }
}
async function memberExtras(browser, S) {
  X.K62_64 = X.K62_64 || {};
  for (const [who, pk, rel, theme] of [
    ["carol", "clients", "/clients", "dark"],
    ["carol", "invoices", "/invoices", "dark"],
    ["alice", "matter-404", `/matters/${NIL}`, "light"],
    ["alice", "invoice-404", `/invoices/${NIL}`, "light"],
  ]) {
    const { ctx, p } = await page(browser, S[who], { width: 1440, height: 900, theme }, `K62-64x|${who}|${theme}|${pk}`);
    const resp = await p.goto(`${BASE}${rel}`, { waitUntil: "load" });
    await settle(p);
    const m = await p.evaluate(MAIN_MEASURE);
    m.status = resp && resp.status();
    X.K62_64[`${who}-${theme}-${pk}`] = m;
    await shot(p, `k6xx__${who}-${theme}-${pk}`, `${who} ${theme} ${rel.replace(NIL, "0000…")} — HTTP ${m.status}, h1×${m.h1Count}${m.h1[0] ? ' "' + m.h1[0] + '"' : ""}${m.crashPanel ? ", crash panel" : ""}${m.noAccess ? ", no access" : ""}`);
    await ctx.close();
  }
  // "Back to Home" on the in-shell 404, keyboard (focus + Enter) and mouse; each from a fresh /invoices load.
  for (const how of ["keyboard", "click"]) {
    const { ctx, p } = await page(browser, S.carol, { width: 1440, height: 900 }, `K63x|carol|back-to-home-${how}`);
    await p.goto(`${BASE}/invoices`, { waitUntil: "load" });
    await settle(p);
    const link = p.locator("#main-content").getByRole("link", { name: "Back to Home", exact: true });
    const href = await link.getAttribute("href");
    if (how === "keyboard") { await link.focus(); await p.keyboard.press("Enter"); } else await link.click();
    await p.waitForURL(/\/dashboard/, { timeout: 15000 }).catch(() => {});
    await settle(p);
    const m = await p.evaluate(MAIN_MEASURE);
    X.K62_64[`carol-back-to-home-${how}`] = { href, landed: p.url().replace(C.APP, ""), h1: m.h1, crashPanel: m.crashPanel, homePage: (await p.locator('[data-testid="home-page"]').count()) > 0 };
    if (how === "click") await shot(p, `k6xx__carol-back-to-home`, `carol /invoices → "Back to Home" (${how}) → ${X.K62_64[`carol-back-to-home-${how}`].landed.replace(/^\/org\/[^/]+/, "")}`);
    await ctx.close();
  }
}

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const step = async (name, f) => {
    try { await f(); console.log("OK", name); } catch (e) { console.log("FAIL", name, e.message.split("\n")[0]); (X.failures = X.failures || []).push({ step: name, error: e.message.split("\n")[0].slice(0, 200) }); }
  };
  for (const who of ["alice", "carol"]) await step(`K05x ${who}`, () => k05(browser, S, who));
  for (const [who, theme] of [["alice", "light"], ["alice", "dark"], ["carol", "light"]]) await step(`K07x ${who} ${theme}`, () => k07(browser, S, who, theme));
  await step("K26x alice template edit", () => k26(browser, S, "alice", "template-edit", `${BASE}/settings/documents/templates/${TEMPLATE}/edit`));
  await step("K62-64x", () => memberExtras(browser, S));
  const carolPages = [["dashboard", "/dashboard"], ["matters", "/matters"], ["clients", "/clients"], ["clients-fica-review-due", "/clients?view=system:fica-review-due"], ["invoices", "/invoices"], ["invoices-collections", "/invoices/collections"], ["invoices-unbilled", "/invoices/unbilled"], ["compliance", "/compliance"], ["invoice-detail", `/invoices/${FEE_NOTE}`], ["report-runner", `/reports/${REPORT}`], ["reports-index", "/reports"], ["trust-transactions", "/trust-accounting/transactions"]];
  const alicePages = [["dashboard", "/dashboard"], ["matters", "/matters"], ["matter-tasks", `/matters/${TEAM_SPEC}?tab=tasks`], ["clients", "/clients"], ["clients-fica-review-due", "/clients?view=system:fica-review-due"], ["fee-note", `/invoices/${FEE_NOTE}`], ["trust-transactions", "/trust-accounting/transactions"], ["report-runner", `/reports/${REPORT}`], ["matter-404", `/matters/${NIL}`], ["invoice-404", `/invoices/${NIL}`]];
  await step("SEL carol", () => selCompat(browser, S, "carol", carolPages));
  await step("SEL alice", () => selCompat(browser, S, "alice", alicePages));
  await browser.close();

  fs.writeFileSync(path.join(RAW, "tiles-extra.json"), JSON.stringify(tiles.map(([k, f, c]) => [k, C.rel(f), c])));
  const mPath = path.join(OUT, "measurements.json");
  const M = JSON.parse(fs.readFileSync(mPath, "utf8"));
  const { SEL: sel, ...rest } = X;
  M.extra = { capturedAt: new Date().toISOString(), by: "05-capture-extra.cjs", ...rest };
  fs.writeFileSync(mPath, JSON.stringify(M) + "\n");
  fs.writeFileSync(path.join(OUT, "sel-compat.json"), JSON.stringify({ capturedAt: new Date().toISOString(), viewport: "1440x900 light", pages: sel }) + "\n");
  const cPath = path.join(OUT, "console.json");
  const CJ = JSON.parse(fs.readFileSync(cPath, "utf8"));
  CJ.extra = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, labelsWithMessages: [...new Set(consoleSink.map((c) => c.label))], messages: consoleSink };
  fs.writeFileSync(cPath, JSON.stringify(CJ) + "\n");
  console.log("tiles", tiles.length, "console", consoleSink.length, "failures", (X.failures || []).length);
})();
