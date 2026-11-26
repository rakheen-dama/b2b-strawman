// 664A OBS-before step 1: capture the pre-664A settings shell on the running e2e mock-auth stack (:3001),
// Playwright library (chromium.launch(), headless), login through the shared 661B helper (Alice via the
// /mock-login UI, Carol via the mock-auth-token cookie). Theme via localStorage `kazi-theme`, app rail via
// cookie `sidebar_state` (expanded), reduced motion, timezone Africa/Johannesburg.
// Read-only: navigation and window scrolling only. No clicks on tabs, no form input, no submits.
// Covers: every family (default tab) × 1024/1440 × light/dark × Alice/Carol; 390 per family; Carol's
// forbidden notice; the pill tab strip; the rail sticky at `top-14` (K-03); SEL-COMPAT before; inner
// Tabs baseline; the grouped-tab-bar consumers (matter + client detail) incl. the ::after underline.
// Raw tiles + tiles.json go to /tmp/664A-before-raw/ (outside the repo); measurements.json,
// sel-compat.json and console.json to ../.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = "/tmp/664A-before-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const FAMILIES = ["firm", "matters", "documents", "fees", "connections"];
const VIEWPORTS = [[1024, 768], [1440, 900]];
// From 00-discover.cjs (Alice's GET /api/matters, /api/clients).
const MATTER = "9a7042bc-c0cc-4010-b37e-442a3eb07d5e"; // Khumalo Property Holdings — Erf 1234 Sandton Transfer
const CLIENT = "68c6b51e-a8f9-4b76-9e05-6bf992a79e53"; // Acme Corp
// Carol (member) holds AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS,
// VIEW_TRUST — not TEAM_OVERSIGHT (Firm › Audit trail's requiredCapability) and not admin (Roles).
const FORBIDDEN = [["audit-trail", "TEAM_OVERSIGHT (requiredCapability)"], ["roles", "adminOnly"]];

const M = { families: {}, at390: {}, forbidden: {}, railSticky: {}, pillStrip: {}, groupedTabBar: {} };
const SEL = {};
const consoleSink = [];
const tiles = []; // [key, file, caption]
const lab = { label: "" };
const pages = [];

async function page(browser, state, opts, label) {
  const ctx = await C.newCtx(browser, state, { ...opts, extra: { ...EXTRA, ...(opts.extra || {}) } });
  const p = await ctx.newPage();
  lab.label = label;
  C.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function go(p, url, label) {
  lab.label = label;
  const resp = await p.goto(url, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(500);
  pages.push(label);
  return resp ? resp.status() : null;
}
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
  return f;
};
const elShot = async (loc, key, caption) => {
  const f = path.join(RAW, `${key}.png`);
  await loc.screenshot({ path: f });
  tiles.push([key, f, caption]);
  return f;
};

// ---------- in-page probe of the settings shell ----------
const SHELL = () => {
  const r = (e) => { const b = e.getBoundingClientRect(); return { top: Math.round(b.top), left: Math.round(b.left), width: Math.round(b.width), height: Math.round(b.height), bottom: Math.round(b.bottom) }; };
  const col = (e) => { const cs = getComputedStyle(e); return { bg: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), color: cs.color, colorHex: window.__hex(cs.color), fontWeight: cs.fontWeight }; };
  const visible = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(e).visibility !== "hidden"; };
  const navs = [...document.querySelectorAll('nav[aria-label="Settings navigation"]')];
  const rail = navs[0] || null;
  const aside = rail && rail.closest("aside");
  const railCurrent = rail ? [...rail.querySelectorAll('a[aria-current="true"]')] : [];
  const famCurrent = rail ? [...rail.querySelectorAll('a[aria-current="page"]')] : [];
  const main = document.querySelector("main#main-content");
  const tablists = main ? [...main.querySelectorAll('[role="tablist"]')] : [];
  const strip = tablists.find((t) => /settings$/.test(t.getAttribute("aria-label") || "")) || null;
  const scroller = strip && strip.parentElement;
  const active = strip && strip.querySelector('[role="tab"][aria-selected="true"]');
  const inactive = strip && strip.querySelector('[role="tab"][aria-selected="false"]');
  const header = document.querySelector("header");
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const rootCs = getComputedStyle(document.documentElement);
  const out = {
    viewport: [window.innerWidth, window.innerHeight],
    title: document.title,
    navCount: navs.length,
    navVisibleCount: navs.filter(visible).length,
    rail: rail ? { visible: visible(rail), box: r(rail), asideDisplay: aside ? getComputedStyle(aside).display : null, position: getComputedStyle(rail).position, cssTop: getComputedStyle(rail).top, height: getComputedStyle(rail).height } : null,
    railFamilyCurrent: famCurrent.map((a) => ({ text: a.textContent.trim(), ...col(a) })),
    railTabCurrent: railCurrent.map((a) => { const cs = getComputedStyle(a); return { text: a.textContent.trim(), ...col(a), borderLeft: `${cs.borderLeftWidth} ${window.__hex(cs.borderLeftColor)}`, box: r(a) }; }),
    railTabs: rail ? [...rail.querySelectorAll("ul ul a")].map((a) => a.textContent.trim()) : [],
    strip: strip
      ? {
          label: strip.getAttribute("aria-label"),
          box: r(strip),
          tabs: [...strip.querySelectorAll('[role="tab"]')].map((t) => t.textContent.trim()),
          stripBg: col(strip).bgHex,
          stripRadius: getComputedStyle(strip).borderRadius,
          scrollerScrollWidth: scroller.scrollWidth,
          scrollerClientWidth: scroller.clientWidth,
          overflow: scroller.scrollWidth > scroller.clientWidth,
          overflowX: getComputedStyle(scroller).overflowX,
          activeTab: active ? { text: active.textContent.trim(), ...col(active), boxShadow: getComputedStyle(active).boxShadow, borderRadius: getComputedStyle(active).borderRadius, box: r(active) } : null,
          inactiveTab: inactive ? { text: inactive.textContent.trim(), ...col(inactive) } : null,
        }
      : null,
    otherTablists: tablists.filter((t) => t !== strip).map((t) => ({ label: t.getAttribute("aria-label"), tabs: t.querySelectorAll('[role="tab"]').length })),
    h1: main ? [...main.querySelectorAll("h1")].map((h) => h.textContent.trim()) : [],
    mainCount: document.querySelectorAll("main#main-content").length,
    header: header ? { box: r(header), position: getComputedStyle(header).position, cssTop: getComputedStyle(header).top } : null,
    appHeaderHeightVar: rootCs.getPropertyValue("--app-header-height").trim(),
    notice: notice ? (() => { const cs = getComputedStyle(notice); return { text: notice.textContent.trim(), ...col(notice), border: `${cs.borderTopWidth} ${cs.borderTopStyle} ${window.__hex(cs.borderTopColor)}`, borderColor: cs.borderTopColor, radius: cs.borderRadius, box: r(notice), contrast: [window.__rgb(cs.color, cs.backgroundColor), window.__rgb(cs.backgroundColor)] }; })() : null,
    docScroll: { scrollHeight: document.scrollingElement.scrollHeight, clientHeight: document.scrollingElement.clientHeight },
  };
  return out;
};
const withContrast = (m) => {
  if (m.notice) {
    const [fg, bg] = m.notice.contrast;
    m.notice.contrast = { fg: C.hex3(fg), bg: C.hex3(bg), ratio: C.ratio(fg, bg) };
  }
  return m;
};

// ---------- families × viewport × theme ----------
async function families(browser, S, who) {
  M.families[who] = {};
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S[who], { width: w, height: h, theme }, "");
      for (const fam of FAMILIES) {
        const label = `family|${who}|${fam}|${w}|${theme}`;
        const status = await go(p, `${BASE}/settings/${fam}`, label);
        const m = withContrast(await p.evaluate(SHELL));
        m.status = status;
        m.url = p.url().replace(C.APP, "");
        M.families[who][`${fam}-${w}-${theme}`] = m;
        await shot(p, `fam__${fam}-${who}-${w}-${theme}`, `${who} ${fam} ${w}×${h} ${theme} — tab "${m.strip && m.strip.activeTab ? m.strip.activeTab.text : "?"}", ${m.strip ? m.strip.tabs.length : 0} tabs${m.strip && m.strip.overflow ? " (strip overflows)" : ""}`);
        if (who === "alice" && w === 1440) {
          const strip = p.locator('#main-content [role="tablist"][aria-label$="settings"]').first();
          if (await strip.count()) await elShot(strip, `pill__${fam}-${theme}`, `${fam} ${theme} — pill strip "${m.strip.label}"`);
        }
        if (w === 1024 && theme === "light" && fam === "firm") SEL[who] = await selCompat(p, who);
      }
      await ctx.close();
    }
}

// ---------- SEL-COMPAT before (1024 light, Firm) ----------
async function selCompat(p, who) {
  const d = await p.evaluate(() => {
    const main = document.querySelector("main#main-content");
    const navs = [...document.querySelectorAll('nav[aria-label="Settings navigation"]')];
    return {
      url: location.pathname + location.search,
      navCount: navs.length,
      familyAriaCurrentPage: navs.flatMap((n) => [...n.querySelectorAll('a[aria-current="page"]')].map((a) => a.textContent.trim())),
      railTabAriaCurrentTrue: navs.flatMap((n) => [...n.querySelectorAll('a[aria-current="true"]')].map((a) => a.textContent.trim())),
      anyAriaCurrent: [...document.querySelectorAll("[aria-current]")].map((e) => `${e.tagName.toLowerCase()}[aria-current=${e.getAttribute("aria-current")}] "${e.textContent.trim().slice(0, 30)}"`),
      tablists: [...document.querySelectorAll('[role="tablist"]')].map((t) => ({ label: t.getAttribute("aria-label"), inMain: !!(main && main.contains(t)) })),
      roleTab: [...document.querySelectorAll('[role="tab"]')].map((t) => `${t.textContent.trim()}:${t.getAttribute("aria-selected")}`),
      h1InMain: main ? [...main.querySelectorAll("h1")].map((h) => h.textContent.trim()) : [],
      mainCount: document.querySelectorAll("main#main-content").length,
      allMainCount: document.querySelectorAll("main").length,
    };
  });
  d.roleTabCount = d.roleTab.length;
  d.h1InMainCount = d.h1InMain.length;
  d.searchButtonExact = await p.getByRole("button", { name: "Search, Command K", exact: true }).count();
  d.searchButtonInHeader = await p.locator("header").getByRole("button", { name: "Search, Command K", exact: true }).count();
  d.tablistByName = await p.getByRole("tablist", { name: "Firm settings", exact: true }).count();
  d.settingsNavByRole = await p.getByRole("navigation", { name: "Settings navigation", exact: true }).count();
  return d;
}

// ---------- 390 ----------
async function at390(browser, S, who) {
  M.at390[who] = {};
  for (const theme of ["light", "dark"]) {
    const { ctx, p } = await page(browser, S[who], { width: 390, height: 844, theme }, "");
    for (const fam of FAMILIES) {
      if (theme === "dark" && fam !== "firm") continue;
      const label = `390|${who}|${fam}|${theme}`;
      const status = await go(p, `${BASE}/settings/${fam}`, label);
      const m = withContrast(await p.evaluate(SHELL));
      m.status = status;
      const snap = await p.locator("body").ariaSnapshot();
      // Any link to another settings family reachable in the a11y tree (closed app Sheet is not in it).
      m.familyLinksInA11y = ["Firm", "Matters", "Documents", "Fees & Trust", "Connections"].filter((l) => new RegExp(`link "${l.replace(/[&]/g, "\\&")}"`).test(snap));
      m.navigationLandmarks = (snap.match(/navigation "[^"]*"/g) || []);
      m.settingsHrefsVisible = await p.evaluate((fam) => [...document.querySelectorAll('a[href*="/settings/"]')].filter((a) => { const b = a.getBoundingClientRect(); return b.width > 0 && b.height > 0; }).map((a) => a.getAttribute("href")).filter((h) => !h.includes(`/settings/${fam}`)), fam);
      M.at390[who][`${fam}-${theme}`] = m;
      await shot(p, `m390__${who}-${fam}-${theme}`, `${who} 390×844 ${fam} ${theme} — nav×${m.navVisibleCount} visible, family links in a11y: ${m.familyLinksInA11y.length ? m.familyLinksInA11y.join(",") : "none"}${m.strip && m.strip.overflow ? ", strip scrolls" : ""}`);
    }
    await ctx.close();
  }
}

// ---------- forbidden notice (Carol) ----------
async function forbidden(browser, S) {
  for (const theme of ["light", "dark"]) {
    const { ctx, p } = await page(browser, S.carol, { width: 1440, height: 900, theme }, "");
    for (const [tab, why] of FORBIDDEN) {
      const label = `forbidden|carol|firm?tab=${tab}|${theme}`;
      const status = await go(p, `${BASE}/settings/firm?tab=${tab}`, label);
      const m = withContrast(await p.evaluate(SHELL));
      m.status = status;
      m.url = p.url().replace(C.APP, "");
      m.gate = why;
      M.forbidden[`${tab}-${theme}`] = m;
      await shot(p, `forb__${tab}-${theme}`, `carol 1440 ${theme} /settings/firm?tab=${tab} (${why}) — ${m.notice ? `notice ${m.notice.colorHex} on ${m.notice.bgHex}, border ${m.notice.border}` : "NO notice"}`);
    }
    await ctx.close();
  }
}

// ---------- K-03 rail sticky ----------
async function railSticky(browser, S) {
  for (const [w, h] of VIEWPORTS) {
    const { ctx, p } = await page(browser, S.alice, { width: w, height: h, theme: "light" }, "");
    const pick = {};
    for (const tab of ["general", "audit-trail"]) {
      await go(p, `${BASE}/settings/firm?tab=${tab}`, `sticky|alice|firm?tab=${tab}|${w}|light`);
      pick[tab] = await p.evaluate(() => document.scrollingElement.scrollHeight - innerHeight);
    }
    const tab = pick.general >= pick["audit-trail"] ? "general" : "audit-trail";
    await go(p, `${BASE}/settings/firm?tab=${tab}`, `sticky|alice|firm?tab=${tab}|${w}|light`);
    const probe = () => {
      const r = (e) => { const b = e.getBoundingClientRect(); return { top: +b.top.toFixed(1), bottom: +b.bottom.toFixed(1), height: +b.height.toFixed(1) }; };
      const rail = document.querySelector('nav[aria-label="Settings navigation"]');
      const header = document.querySelector("header");
      const first = rail.querySelector("a");
      // Scroll containers between the rail and the root (sticky resolves against the nearest one).
      const scrollers = [];
      for (let e = rail.parentElement; e; e = e.parentElement) {
        const cs = getComputedStyle(e);
        if (/(auto|scroll|hidden)/.test(cs.overflowY) && e !== document.body && e !== document.documentElement) scrollers.push(`${e.tagName.toLowerCase()}.${(e.className || "").toString().slice(0, 40)} overflowY=${cs.overflowY}`);
      }
      return {
        scrollY: Math.round(scrollY),
        rail: r(rail),
        railFirstLink: { text: first.textContent.trim(), ...r(first) },
        header: r(header),
        headerCssTop: getComputedStyle(header).top,
        railCssTop: getComputedStyle(rail).top,
        appHeaderHeightVar: getComputedStyle(document.documentElement).getPropertyValue("--app-header-height").trim(),
        scrollers,
        maxScroll: document.scrollingElement.scrollHeight - innerHeight,
      };
    };
    const before = await p.evaluate(probe);
    await shot(p, `sticky__${w}-before`, `alice ${w}×${h} light firm?tab=${tab} scrollY 0 — rail top ${before.rail.top}, header bottom ${before.header.bottom}`);
    const target = Math.min(600, before.maxScroll);
    await p.evaluate((y) => window.scrollTo(0, y), target);
    await p.waitForTimeout(400);
    const after = await p.evaluate(probe);
    await shot(p, `sticky__${w}-after`, `alice ${w}×${h} light firm?tab=${tab} scrollY ${after.scrollY} — rail top ${after.rail.top} (sticky top-14 = 56), header ${after.header.top}–${after.header.bottom}; overlap ${+(after.header.bottom - after.rail.top).toFixed(1)} px`);
    M.railSticky[`${w}`] = { tab, scrollablePx: pick, before, after, railUnderHeaderPx: +(after.header.bottom - after.rail.top).toFixed(1), railFirstLinkClearOfHeader: after.railFirstLink.top >= after.header.bottom };
    await ctx.close();
  }
}

// ---------- inner Tabs baseline (Alice 1024 light) ----------
async function innerTabs(browser, S) {
  const { ctx, p } = await page(browser, S.alice, { width: 1024, height: 768, theme: "light" }, "");
  SEL.innerTabs = {};
  for (const [fam, tab] of [["matters", "custom-fields"], ["fees", "tariffs"], ["documents", "email"], ["connections", "packs"]]) {
    await go(p, `${BASE}/settings/${fam}?tab=${tab}`, `inner|alice|${fam}?tab=${tab}|1024|light`);
    SEL.innerTabs[`${fam}?tab=${tab}`] = await p.evaluate(() => [...document.querySelectorAll('#main-content [role="tablist"]')].map((t) => ({ label: t.getAttribute("aria-label"), tabs: [...t.querySelectorAll('[role="tab"]')].map((x) => `${x.textContent.trim()}:${x.getAttribute("aria-selected")}`), dataSlot: t.getAttribute("data-slot") })));
  }
  await ctx.close();
}

// ---------- grouped-tab-bar consumers (1440 light) ----------
const UNDERLINE = () => {
  const bar = document.querySelector('[data-testid="grouped-tab-bar"]');
  if (!bar) return { error: "no grouped-tab-bar" };
  const sel = bar.querySelector('[role="tab"][aria-selected="true"]');
  const un = bar.querySelector('[role="tab"][aria-selected="false"]');
  const a = (e) => {
    const cs = getComputedStyle(e, "::after");
    return { content: cs.content, position: cs.position, height: cs.height, backgroundColor: cs.backgroundColor, bgHex: window.__hex(cs.backgroundColor), bottom: cs.bottom, left: cs.left, right: cs.right, opacity: cs.opacity, borderRadius: cs.borderRadius, transitionProperty: cs.transitionProperty };
  };
  const t = (e) => { const cs = getComputedStyle(e); const b = e.getBoundingClientRect(); return { text: e.textContent.trim(), color: cs.color, colorHex: window.__hex(cs.color), fontSize: cs.fontSize, fontWeight: cs.fontWeight, padding: cs.padding, height: Math.round(b.height), testid: e.getAttribute("data-testid") }; };
  const sticky = bar.closest('[data-slot="grouped-tab-bar-sticky"]');
  const scs = getComputedStyle(sticky);
  return {
    selected: sel ? { ...t(sel), after: a(sel) } : null,
    unselected: un ? { ...t(un), after: a(un) } : null,
    tabs: [...bar.querySelectorAll('[role="tab"]')].map((x) => `${x.textContent.trim()}:${x.getAttribute("aria-selected")}`),
    sticky: { top: scs.top, position: scs.position, zIndex: scs.zIndex, bg: window.__hex(scs.backgroundColor), borderBottom: `${scs.borderBottomWidth} ${window.__hex(scs.borderBottomColor)}`, box: (() => { const b = sticky.getBoundingClientRect(); return { top: Math.round(b.top), left: Math.round(b.left), width: Math.round(b.width), height: Math.round(b.height) }; })() },
  };
};
async function grouped(browser, S) {
  const { ctx, p } = await page(browser, S.alice, { width: 1440, height: 900, theme: "light" }, "");
  for (const [k, url] of [["matter", `${BASE}/matters/${MATTER}`], ["client", `${BASE}/clients/${CLIENT}`]]) {
    const status = await go(p, url, `grouped|alice|${k}|1440|light`);
    await p.locator('[data-testid="grouped-tab-bar"]').first().waitFor({ timeout: 20000 });
    await p.waitForTimeout(300);
    const m = await p.evaluate(UNDERLINE);
    m.status = status;
    m.url = p.url().replace(C.APP, "");
    M.groupedTabBar[k] = m;
    await shot(p, `gtb__${k}-viewport`, `alice 1440 light ${k} detail — viewport`);
    await elShot(p.locator('[data-slot="grouped-tab-bar-sticky"]').first(), `gtb__${k}-bar`, `${k} grouped tab bar (element) — selected "${m.selected && m.selected.text}" ::after h ${m.selected && m.selected.after.height} bg ${m.selected && m.selected.after.bgHex} bottom ${m.selected && m.selected.after.bottom}`);
  }
  await ctx.close();
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
  for (const who of ["alice", "carol"]) await step(`families ${who}`, () => families(browser, S, who));
  for (const who of ["alice", "carol"]) await step(`390 ${who}`, () => at390(browser, S, who));
  await step("forbidden carol", () => forbidden(browser, S));
  await step("rail sticky alice", () => railSticky(browser, S));
  await step("inner tabs alice", () => innerTabs(browser, S));
  await step("grouped tab bar alice", () => grouped(browser, S));
  await browser.close();

  const stack = "e2e :3001 (frontend image 22eed7334 built 2026-11-26T11:28Z from worktree-epic-663C, presumably 663C head 7149f9c11; frontend tree == main 5daacb930/cee91f031)";
  fs.writeFileSync(path.join(RAW, "tiles.json"), JSON.stringify(tiles.map(([k, f, c]) => [k, f, c])));
  fs.writeFileSync(path.join(OUT, "measurements.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack, ...M }) + "\n");
  fs.writeFileSync(path.join(OUT, "sel-compat.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack, viewport: "1024x768 light, /settings/firm (default tab)", ...SEL }) + "\n");
  const labels = [...new Set(consoleSink.map((c) => c.label))];
  fs.writeFileSync(
    path.join(OUT, "console.json"),
    JSON.stringify({ pagesOpened: pages.length, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, labelsWithMessages: labels, messages: consoleSink }) + "\n",
  );
  console.log("tiles", tiles.length, "pages", pages.length, "console", consoleSink.length, "failures", (M.failures || []).length);
})();
