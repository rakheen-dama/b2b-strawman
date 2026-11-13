// 642C after-observation (task 642.14). Read-only: drives the e2e mock-auth stack (frontend :3001,
// built from worktree @32c89d2c1) as Alice (owner) and Carol (member). Measures the restyled
// GroupedTabBar on the matter + client detail pages: tabs, underline (::after), opacity of the
// sticky bar, tab switching mechanics, sticky behaviour, overflow/scrollbar/fades (F7), deep links,
// keyboard focus ring, console. Writes obs-after.json (compact) + screenshots.
// Run: node obs-after.cjs (nvm node v20). Started from ../before/obs-before.cjs.
const path = require("path");
const fs = require("fs");
const PW_PATH =
  process.env.PW_PATH ||
  require.resolve("@playwright/test", { paths: [path.resolve(__dirname, "../../../../../frontend")] });
const { chromium } = require(PW_PATH);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
// Ids from the lifecycle run on the fresh stack (tmpdir/kazi-legal-lifecycle-run.json, runId mwe43hoif41f).
const PAGES = {
  matter: {
    url: `${BASE}/org/${SLUG}/matters/c77bf2fa-a12d-4d2d-bb4c-1e6476c300b8`,
    note: "Sipho litigation matter (lifecycle run mwe43hoif41f)",
    visits: [
      ["single-tab group Diary", { group: "schedule" }],
      ["inactive multi group Work → Tasks", { group: "work", item: "tasks", shoot: "matter-dropdown-work-open-1440-light.png" }],
      ["inactive multi group Fees → Disbursements", { group: "finance", item: "disbursements" }],
      ["dropdown Fees → Statements (group already active)", { group: "finance", item: "statements" }],
    ],
    longTab: "activity",
    deepLink: "disbursements",
  },
  client: {
    url: `${BASE}/org/${SLUG}/clients/690187db-6dd1-40a3-93f9-0121b0d0f783`,
    note: "Sipho Ndlovu client (lifecycle run mwe43hoif41f) — matters + fee notes + trust",
    visits: [
      ["single-tab group Activity", { group: "activity" }],
      ["inactive multi group Matters → Documents", { group: "work", item: "documents" }],
      ["inactive multi group Fees → Trust", { group: "finance", item: "trust", shoot: "client-dropdown-fees-open-1440-light.png" }],
      ["dropdown Fees → Fee notes (group already active)", { group: "finance", item: "invoices" }],
    ],
    longTab: "audit",
    deepLink: "audit",
  },
};

async function login(page, userId) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", userId);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

const bar = (page) => page.locator('[data-testid="grouped-tab-bar"]').filter({ visible: true }).first();

async function load(page, url, theme) {
  await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
  const resp = await page.goto(url, { waitUntil: "load", timeout: 90000 });
  await bar(page).waitFor({ state: "visible", timeout: 60000 });
  await page.waitForTimeout(2500); // hydration, effects, fade/underline transitions
  return resp ? resp.status() : null;
}

// Everything about the bar in one frame. Runs in the page.
function barFacts(page) {
  return page.evaluate(() => {
    const R = (el) => {
      if (!el) return null;
      const b = el.getBoundingClientRect();
      return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right), bottom: Math.round(b.bottom) };
    };
    const resolve = (v) => {
      const d = document.createElement("div");
      d.style.color = `var(${v})`;
      document.body.appendChild(d);
      const c = getComputedStyle(d).color;
      d.remove();
      return c;
    };
    const alpha = (c) => {
      if (!c || c === "transparent") return 0;
      let m = c.match(/\/\s*([\d.]+%?)\s*\)$/);
      if (m) return m[1].endsWith("%") ? parseFloat(m[1]) / 100 : parseFloat(m[1]);
      m = c.match(/^rgba\([^)]*,\s*([\d.]+)\)$/);
      return m ? parseFloat(m[1]) : 1;
    };
    const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
    const area = sticky && sticky.querySelector('[data-slot="scroll-area"]');
    const vp = area && area.querySelector('[data-slot="scroll-area-viewport"]');
    const list = document.querySelector('[data-testid="grouped-tab-bar"]');
    const scs = sticky && getComputedStyle(sticky);
    const bars = area ? [...area.querySelectorAll('[data-slot="scroll-area-scrollbar"]')] : [];
    const hbar = bars.find((b) => b.getAttribute("data-orientation") === "horizontal") || null;
    const thumb = hbar && hbar.querySelector('[data-slot="scroll-area-thumb"]');
    const fadeEl = (k) => sticky && sticky.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`);
    const fade = (k) => {
      const f = fadeEl(k);
      return f ? { visible: f.getAttribute("data-visible"), opacity: getComputedStyle(f).opacity, pe: getComputedStyle(f).pointerEvents, rect: R(f) } : null;
    };
    const vpR = vp && vp.getBoundingClientRect();
    const tabs = list
      ? [...list.querySelectorAll('[role="tab"]')].map((t) => {
          const cs = getComputedStyle(t);
          const af = getComputedStyle(t, "::after");
          const b = t.getBoundingClientRect();
          return {
            testid: t.getAttribute("data-testid"),
            text: t.textContent.replace(/\s+/g, " ").trim(),
            selected: t.getAttribute("aria-selected"),
            haspopup: t.getAttribute("aria-haspopup"),
            state: t.getAttribute("data-state"),
            color: cs.color,
            after: { content: af.content, opacity: af.opacity, height: af.height, bg: af.backgroundColor, bottom: af.bottom },
            rect: R(t),
            fullyInViewport: vpR ? b.left >= vpR.left - 0.5 && b.right <= vpR.right + 0.5 : null,
          };
        })
      : [];
    // the app header (sticky top-2 z-30)
    const header = document.querySelector("header.sticky") || document.querySelector("header");
    const allLists = [...document.querySelectorAll('[role="tablist"]')];
    const panels = [...document.querySelectorAll('[role="tabpanel"]')];
    return {
      url: location.pathname + location.search,
      htmlClass: document.documentElement.className,
      tokens: { fg: resolve("--foreground"), muted: resolve("--muted-foreground"), bg: resolve("--background"), ring: resolve("--ring") },
      tablistCount: allLists.length,
      tablistVisibleCount: allLists.filter((l) => l.getClientRects().length > 0).length,
      tabpanelCount: panels.length,
      tabpanelVisibleCount: panels.filter((p) => p.getClientRects().length > 0 && !p.hidden).length,
      sticky: sticky ? { rect: R(sticky), position: scs.position, top: scs.top, zIndex: scs.zIndex, bg: scs.backgroundColor, bgAlpha: alpha(scs.backgroundColor), borderBottom: `${scs.borderBottomWidth} ${scs.borderBottomColor}` } : null,
      area: area ? { rect: R(area), paddingBottom: getComputedStyle(area).paddingBottom } : null,
      viewport: vp ? { rect: R(vp), scrollWidth: vp.scrollWidth, clientWidth: vp.clientWidth, scrollLeft: Math.round(vp.scrollLeft), overflowX: getComputedStyle(vp).overflowX } : null,
      scrollbars: bars.map((b) => ({ orientation: b.getAttribute("data-orientation"), state: b.getAttribute("data-state"), rect: R(b), opacity: getComputedStyle(b).opacity, display: getComputedStyle(b).display })),
      hScrollbar: hbar ? { rect: R(hbar), thumb: R(thumb), thumbBg: thumb && getComputedStyle(thumb).backgroundColor } : null,
      fades: { start: fade("start"), end: fade("end") },
      tabs,
      header: header ? { rect: R(header), zIndex: getComputedStyle(header).zIndex, position: getComputedStyle(header).position } : null,
      docScrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      scrollY: Math.round(window.scrollY),
    };
  });
}

// Settle helper (inlined into each evaluate): wait until a scroll position is unchanged for 5 frames
// (html is scroll-behavior: smooth, so programmatic scrolls animate).

function scrollWindow(page, dy) {
  return page.evaluate(
    async (dy) => {
      const settle = async (getPos) => { const f = () => new Promise((r) => requestAnimationFrame(() => r())); let last = getPos(), s = 0; for (let i = 0; i < 180 && s < 5; i++) { await f(); const n = getPos(); s = n === last ? s + 1 : 0; last = n; } return last; };
      const se = document.scrollingElement;
      const initial = se.scrollTop;
      const st = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
      const before = Math.round(st.getBoundingClientRect().top);
      se.scrollTop = initial + dy;
      const fin = await settle(() => se.scrollTop);
      return { maxScroll: se.scrollHeight - se.clientHeight, initial: Math.round(initial), scrolledBy: Math.round(fin - initial), barTopBefore: before, barTopAfter: Math.round(st.getBoundingClientRect().top) };
    },
    dy
  );
}

function scrollRow(page, where) {
  return page.evaluate(
    async (where) => {
      const settle = async (getPos) => { const f = () => new Promise((r) => requestAnimationFrame(() => r())); let last = getPos(), s = 0; for (let i = 0; i < 180 && s < 5; i++) { await f(); const n = getPos(); s = n === last ? s + 1 : 0; last = n; } return last; };
      const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
      const max = vp.scrollWidth - vp.clientWidth;
      vp.scrollLeft = where === "mid" ? Math.round(max / 2) : where === "end" ? max : 0;
      await settle(() => vp.scrollLeft);
      await new Promise((r) => setTimeout(r, 400)); // fade opacity transition
      const f = (k) => {
        const el = document.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`);
        return { visible: el.getAttribute("data-visible"), opacity: getComputedStyle(el).opacity };
      };
      return { where, max, scrollLeft: Math.round(vp.scrollLeft), start: f("start"), end: f("end") };
    },
    where
  );
}

// What is painted on top at a point (for bleed-through / covering checks).
function hit(page, x, y) {
  return page.evaluate(
    ([x, y]) => {
      const el = document.elementFromPoint(x, y);
      if (!el) return null;
      const inSticky = !!el.closest('[data-slot="grouped-tab-bar-sticky"]');
      const inHeader = !!el.closest("header");
      const inMenu = !!el.closest('[role="menu"]');
      return { tag: el.tagName.toLowerCase(), testid: el.getAttribute("data-testid") || (el.closest("[data-testid]") || {}).getAttribute?.("data-testid") || null, slot: el.getAttribute("data-slot"), inSticky, inHeader, inMenu, text: (el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40) };
    },
    [x, y]
  );
}

function widestOffenders(page) {
  return page.evaluate(() => {
    const vw = window.innerWidth;
    const out = [];
    for (const el of document.querySelectorAll("body *")) {
      const b = el.getBoundingClientRect();
      if (b.right <= vw + 1 || b.width === 0) continue;
      // skip anything clipped inside the tab scroll viewport (it is scrollable, not page overflow)
      if (el.closest('[data-slot="scroll-area-viewport"]')) continue;
      if (el.closest('[role="menu"]')) continue;
      out.push({ tag: el.tagName.toLowerCase(), testid: el.getAttribute("data-testid"), slot: el.getAttribute("data-slot"), cls: (el.getAttribute("class") || "").slice(0, 70), text: (el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40), right: Math.round(b.right) });
    }
    out.sort((a, b) => b.right - a.right);
    return out.slice(0, 8);
  });
}


// ------------------------------------------------------------------ checks (node side)
function loadChecks(f, theme) {
  const active = f.tabs.filter((t) => t.selected === "true");
  const inactive = f.tabs.filter((t) => t.selected !== "true");
  const heights = [...new Set(f.tabs.map((t) => t.rect.h))];
  return {
    oneTablist: f.tablistCount === 1 && f.tablistVisibleCount === 1,
    oneVisiblePanel: f.tabpanelVisibleCount === 1,
    oneActive: active.length === 1,
    underlineOnActive: active.length === 1 && active[0].after.opacity === "1" && active[0].after.height === "2px" && active[0].after.bg !== "rgba(0, 0, 0, 0)",
    underlineHiddenOnInactive: inactive.every((t) => t.after.opacity === "0"),
    activeFg: active.every((t) => t.color === f.tokens.fg),
    inactiveMuted: inactive.every((t) => t.color === f.tokens.muted),
    triggerHeights: heights,
    oneLine: heights.length === 1 && heights[0] < 48,
    stickyOpaque: f.sticky && f.sticky.bgAlpha === 1,
    stickyBgIsBackgroundToken: f.sticky && f.sticky.bg === f.tokens.bg,
    themeApplied: theme === "dark" ? /\bdark\b/.test(f.htmlClass) : !/\bdark\b/.test(f.htmlClass),
  };
}

(async () => {
  const browser = await chromium.launch();
  const R = { capturedAt: new Date().toISOString(), head: "32c89d2c1", pages: Object.fromEntries(Object.entries(PAGES).map(([k, v]) => [k, { url: v.url, note: v.note }])), loads: [], visits: [], sticky: [], overflow: [], deepLinks: [], keyboard: [], carol: null };
  const shots = [];
  const consoleAll = [];
  let consoleBuf = [];
  let reqBuf = [];
  const wire = (page, who) => {
    page.on("console", (m) => {
      if (m.type() === "error" || m.type() === "warning") {
        const c = { who, type: m.type(), text: m.text().slice(0, 400), url: page.url().replace(BASE, "") };
        consoleBuf.push(c);
        consoleAll.push(c);
      }
    });
    page.on("pageerror", (e) => {
      const c = { who, type: "pageerror", text: String(e).slice(0, 400), url: page.url().replace(BASE, "") };
      consoleBuf.push(c);
      consoleAll.push(c);
    });
    page.on("request", (rq) => {
      const h = rq.headers();
      const isRsc = h["rsc"] === "1" || /[?&]_rsc=/.test(rq.url());
      if (isRsc || rq.isNavigationRequest()) reqBuf.push({ rsc: isRsc, nav: rq.isNavigationRequest(), method: rq.method(), url: rq.url().replace(BASE, "").slice(0, 160) });
    });
  };
  const sumConsole = (buf) => ({ errors: buf.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: buf.filter((c) => c.type === "warning").length, hydration: buf.filter((c) => /hydrat/i.test(c.text)).length });
  const shoot = async (page, file, opts = {}) => {
    await page.screenshot({ path: path.join(OUT, file), ...opts });
    shots.push(file);
  };
  const trackScrollY = () => {
    window.__maxScrollY = 0;
    window.addEventListener("scroll", () => (window.__maxScrollY = Math.max(window.__maxScrollY, window.scrollY)), { passive: true });
  };

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(trackScrollY);
  const page = await ctx.newPage();
  wire(page, "alice");
  await login(page, USERS.alice);

  // ---------- 1. loads: both pages × 1440/1024 × light/dark
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h, theme] of [[1440, 900, "light"], [1440, 900, "dark"], [1024, 768, "light"], [1024, 768, "dark"]]) {
      await page.setViewportSize({ width: w, height: h });
      consoleBuf = [];
      const status = await load(page, pg.url, theme);
      const f = await barFacts(page);
      const file = `alice-${key}-${w}-${theme}.png`;
      await shoot(page, file);
      const checks = loadChecks(f, theme);
      R.loads.push({ page: key, viewport: `${w}x${h}`, theme, status, file, checks, facts: f, console: sumConsole(consoleBuf) });
      console.log(`load ${key} ${w} ${theme} ${status} lists=${f.tablistVisibleCount} panels=${f.tabpanelVisibleCount} sticky=${JSON.stringify(f.sticky && { y: f.sticky.rect.y, bg: f.sticky.bg })} heights=${checks.triggerHeights} vp=${f.viewport.scrollWidth}/${f.viewport.clientWidth} checks=${Object.entries(checks).filter(([k, v]) => v === false).map(([k]) => k).join(",") || "all-ok"}`);
    }
  }

  // ---------- 2. tab visits (1440 light)
  await page.setViewportSize({ width: 1440, height: 900 });
  for (const [key, pg] of Object.entries(PAGES)) {
    consoleBuf = [];
    await load(page, pg.url, "light");
    await page.evaluate(() => {
      window.__hist = [];
      for (const fn of ["pushState", "replaceState"]) {
        const orig = history[fn].bind(history);
        history[fn] = (s, t, u) => {
          window.__hist.push({ fn, url: String(u) });
          return orig(s, t, u);
        };
      }
    });
    for (const [label, act] of pg.visits) {
      reqBuf = [];
      await page.evaluate(() => (window.__hist = []));
      const group = page.getByTestId(`tab-group-${act.group}`).filter({ visible: true });
      await group.click();
      await page.waitForTimeout(1000);
      let menu = null;
      if (act.item) {
        const item = page.getByTestId(`tab-item-${act.item}`).filter({ visible: true });
        let open = await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false);
        const openedOnFirstClick = open;
        if (!open) {
          await group.click();
          open = await item.waitFor({ state: "visible", timeout: 3000 }).then(() => true, () => false);
        }
        menu = await page.evaluate(() => {
          const m = document.querySelector('[role="menu"]');
          if (!m) return null;
          const b = m.getBoundingClientRect();
          const cs = getComputedStyle(m);
          return {
            rect: { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right), bottom: Math.round(b.bottom) },
            inViewport: b.left >= 0 && b.top >= 0 && b.right <= innerWidth && b.bottom <= innerHeight,
            zIndex: getComputedStyle(m.parentElement).zIndex + "/" + cs.zIndex,
            items: [...m.querySelectorAll('[role="menuitem"]')].map((i) => `${i.getAttribute("data-testid")}${i.getAttribute("data-active") === "true" ? "*" : ""}`),
          };
        });
        menu.openedOnFirstClick = openedOnFirstClick;
        menu.opened = open;
        if (act.shoot && open) await shoot(page, act.shoot);
        if (open) await item.click();
      }
      await page.waitForTimeout(2000);
      const f = await barFacts(page);
      const hist = await page.evaluate(() => window.__hist);
      const sel = f.tabs.filter((t) => t.selected === "true");
      R.visits.push({
        page: key, label, action: act.group + (act.item ? `→${act.item}` : ""), tabParam: new URL(page.url()).searchParams.get("tab"),
        history: hist.map((h) => h.fn), rsc: reqBuf.filter((r) => r.rsc).length, nav: reqBuf.filter((r) => r.nav && !r.rsc).length, requests: reqBuf.slice(0, 4),
        selected: sel.map((t) => ({ testid: t.testid, text: t.text, afterOpacity: t.after.opacity, afterH: t.after.height })), visiblePanels: f.tabpanelVisibleCount, menu,
      });
      await page.keyboard.press("Escape").catch(() => {});
      await page.waitForTimeout(300);
      console.log(`visit ${key} "${label}" tab=${new URL(page.url()).searchParams.get("tab")} hist=${hist.map((h) => h.fn)} rsc=${reqBuf.filter((r) => r.rsc).length} sel=${sel.map((t) => t.text)} underline=${sel.map((t) => t.after.opacity)} menu=${menu && JSON.stringify({ first: menu.openedOnFirstClick, inVp: menu.inViewport, rect: menu.rect })}`);
    }
    R.visitConsole = R.visitConsole || {};
    R.visitConsole[key] = sumConsole(consoleBuf);
  }

  // ---------- 3. sticky (1440, light + dark), long tab, scroll 600. The client page's longest tab only
  // scrolls ~359px at 1440x900 (bar starts at y=467, so it would need >= 403 to reach top 64); the
  // client page is measured at 1440x600 so the bar can actually reach its sticky offset.
  for (const [key, pg] of Object.entries(PAGES)) {
    const vh = key === "client" ? 600 : 900;
    await page.setViewportSize({ width: 1440, height: vh });
    // pick the longest candidate tab on this page
    let tab = pg.longTab;
    const candidates = key === "matter" ? ["activity", "audit", "time"] : ["audit", "invoices", "matters", "trust"];
    const lens = {};
    for (const c of candidates) {
      await load(page, `${pg.url}?tab=${c}`, "light");
      lens[c] = await page.evaluate(() => document.scrollingElement.scrollHeight - document.scrollingElement.clientHeight);
    }
    tab = Object.entries(lens).sort((a, b) => b[1] - a[1])[0][0];
    for (const theme of ["light", "dark"]) {
      await load(page, `${pg.url}?tab=${tab}`, theme);
      const s = await scrollWindow(page, 600);
      const f = await barFacts(page);
      const st = f.sticky.rect;
      const lastTab = f.tabs[f.tabs.length - 1].rect;
      const hits = {
        headerCenter: await hit(page, f.header.rect.x + f.header.rect.w / 2, f.header.rect.y + f.header.rect.h / 2),
        headerBottomEdge: await hit(page, f.header.rect.x + 40, f.header.rect.bottom - 2),
        barEmptyRight: await hit(page, Math.min(st.right - 12, lastTab.right + 60), st.y + st.h / 2),
        barTabCenter: await hit(page, f.tabs[0].rect.x + f.tabs[0].rect.w / 2, f.tabs[0].rect.y + f.tabs[0].rect.h / 2),
        gapHeaderToBar: await hit(page, st.x + st.w / 2, f.header.rect.bottom + 4),
      };
      const file = `alice-${key}-sticky-scrolled600-1440x${vh}-${theme}.png`;
      await shoot(page, file);
      const entry = { page: key, viewport: `1440x${vh}`, tab, theme, lens, scroll: s, stickyTop: st.y, stickyBottom: st.bottom, headerRect: f.header.rect, headerZ: f.header.zIndex, stickyZ: f.sticky.zIndex, stickyBg: f.sticky.bg, bgAlpha: f.sticky.bgAlpha, hits, file };
      if (theme === "light") {
        // open a dropdown while stuck
        const g = f.tabs.find((t) => t.haspopup === "menu" && t.selected !== "true");
        const gid = g.testid;
        await page.getByTestId(gid).click();
        await page.waitForSelector('[role="menu"]', { timeout: 4000 }).catch(() => {});
        await page.waitForTimeout(500);
        const m = await page.evaluate(() => {
          const m = document.querySelector('[role="menu"]');
          if (!m) return null;
          const b = m.getBoundingClientRect();
          const items = [...m.querySelectorAll('[role="menuitem"]')];
          const last = items[items.length - 1].getBoundingClientRect();
          return { rect: { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), bottom: Math.round(b.bottom) }, inViewport: b.top >= 0 && b.bottom <= innerHeight && b.left >= 0 && b.right <= innerWidth, lastItem: { x: last.x + last.width / 2, y: last.y + last.height / 2 } };
        });
        entry.menuWhileStuck = m ? { group: gid, ...m, topmostAtLastItem: await hit(page, m.lastItem.x, m.lastItem.y), scrollYAfterOpen: await page.evaluate(() => Math.round(scrollY)) } : { group: gid, opened: false };
        if (key === "matter" && m) await shoot(page, `matter-sticky-menu-open-1440-light.png`);
        await page.keyboard.press("Escape");
        await page.waitForTimeout(500);
        entry.afterEscape = await page.evaluate(() => ({ stickyTop: Math.round(document.querySelector('[data-slot="grouped-tab-bar-sticky"]').getBoundingClientRect().top), scrollY: Math.round(scrollY) }));
      }
      R.sticky.push(entry);
      console.log(`sticky ${key} ${tab} ${theme} lens=${JSON.stringify(lens)} scrolled=${s.scrolledBy}/${s.maxScroll} top ${s.barTopBefore}→${s.barTopAfter} header=${JSON.stringify(f.header.rect)} z=${f.header.zIndex}/${f.sticky.zIndex} hits=${JSON.stringify(Object.fromEntries(Object.entries(hits).map(([k, v]) => [k, v && (v.inHeader ? "header" : v.inSticky ? "sticky" : `${v.tag}:${v.text}`)])))} menu=${JSON.stringify(entry.menuWhileStuck && { inVp: entry.menuWhileStuck.inViewport, top: entry.menuWhileStuck.topmostAtLastItem && entry.menuWhileStuck.topmostAtLastItem.inMenu })} esc=${JSON.stringify(entry.afterEscape)}`);
    }
  }

  await page.setViewportSize({ width: 1440, height: 900 });
  // ---------- 4. overflow / F7 (light) at 390, 768, 1024 — measured before ANY scroll of the row
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h] of [[390, 844], [768, 1024], [1024, 768]]) {
      await page.setViewportSize({ width: w, height: h });
      consoleBuf = [];
      await load(page, pg.url, "light");
      const f = await barFacts(page);
      const offenders = await widestOffenders(page);
      let file = null;
      if (w !== 1024) {
        file = `alice-${key}-${w}-light.png`;
        await shoot(page, file);
      }
      const rowOverflows = f.viewport.scrollWidth > f.viewport.clientWidth;
      const active = f.tabs.find((t) => t.selected === "true");
      const maxTabBottom = Math.max(...f.tabs.map((t) => t.rect.bottom));
      const e = {
        page: key, viewport: `${w}x${h}`, file, docScrollWidth: f.docScrollWidth, innerWidth: f.innerWidth, pageOverflowsX: f.docScrollWidth > f.innerWidth,
        stickyRect: f.sticky.rect, viewportBox: f.viewport, rowOverflows, scrollbars: f.scrollbars, hScrollbar: f.hScrollbar, areaPaddingBottom: f.area.paddingBottom,
        activeTrigger: active && { testid: active.testid, bottom: active.rect.bottom }, maxTabBottom,
        scrollbarBelowTriggers: f.hScrollbar ? f.hScrollbar.rect.y >= maxTabBottom : null,
        scrollbarVisible: f.hScrollbar ? f.hScrollbar.rect.h > 0 && f.hScrollbar.thumb && f.hScrollbar.thumb.w > 0 && f.scrollbars.find((b) => b.orientation === "horizontal").opacity !== "0" : null,
        fadesAt0: { start: f.fades.start.visible, startOpacity: f.fades.start.opacity, end: f.fades.end.visible, endOpacity: f.fades.end.opacity, pe: f.fades.end.pe },
        triggerHeights: [...new Set(f.tabs.map((t) => t.rect.h))], offenders, console: sumConsole(consoleBuf),
      };
      if (rowOverflows) {
        e.rowMid = await scrollRow(page, "mid");
        e.rowEnd = await scrollRow(page, "end");
        e.rowStart = await scrollRow(page, "start");
        // pointer through the end fade onto a partially hidden trigger
        const probe = await page.evaluate(() => {
          const fe = document.querySelector('[data-slot="grouped-tab-bar-fade-end"]').getBoundingClientRect();
          const t = [...document.querySelectorAll('[data-testid="grouped-tab-bar"] [role="tab"]')].find((t) => {
            const b = t.getBoundingClientRect();
            return b.left < fe.left + 8 && b.right > fe.left + 12;
          });
          if (!t) return null;
          const b = t.getBoundingClientRect();
          const x = fe.left + 10, y = b.top + b.height / 2;
          const top = document.elementFromPoint(x, y);
          return { testid: t.getAttribute("data-testid"), x, y, topmost: top && (top.closest('[role="tab"]') || {}).getAttribute?.("data-testid"), topmostIsFade: !!(top && top.getAttribute("data-slot") || "").includes("fade") };
        });
        if (probe) {
          reqBuf = [];
          const before = page.url();
          await page.mouse.click(probe.x, probe.y);
          await page.waitForTimeout(1200);
          probe.effect = await page.evaluate(() => ({ menuOpen: !!document.querySelector('[role="menu"]'), tab: new URL(location.href).searchParams.get("tab") }));
          probe.urlChanged = page.url() !== before;
          await page.keyboard.press("Escape").catch(() => {});
          await page.waitForTimeout(400);
        }
        e.fadeClickThrough = probe;
        // normal Playwright click on the LAST trigger (right edge, under the end fade at scrollLeft 0)
        await scrollRow(page, "start");
        const last = page.locator('[data-testid="grouped-tab-bar"] [role="tab"]').last();
        const lastId = await last.getAttribute("data-testid");
        let ok = true, err = null;
        try {
          await last.click({ timeout: 5000 });
        } catch (x) {
          ok = false;
          err = String(x).slice(0, 300);
        }
        await page.waitForTimeout(1500);
        e.lastTriggerClick = { testid: lastId, ok, err, ...(await page.evaluate(() => ({ menuOpen: !!document.querySelector('[role="menu"]'), tab: new URL(location.href).searchParams.get("tab"), selected: [...document.querySelectorAll('[data-testid="grouped-tab-bar"] [role="tab"][aria-selected="true"]')].map((t) => t.getAttribute("data-testid")) }))) };
        await page.keyboard.press("Escape").catch(() => {});
      }
      R.overflow.push(e);
      console.log(`overflow ${key} ${w}: doc ${f.docScrollWidth}/${f.innerWidth} row ${f.viewport.scrollWidth}/${f.viewport.clientWidth} hbar=${JSON.stringify(f.hScrollbar && f.hScrollbar.rect)} thumb=${JSON.stringify(f.hScrollbar && f.hScrollbar.thumb)} maxTabBottom=${maxTabBottom} fades0=${JSON.stringify(e.fadesAt0)} mid=${JSON.stringify(e.rowMid)} end=${JSON.stringify(e.rowEnd)} fadeClick=${JSON.stringify(e.fadeClickThrough)} last=${JSON.stringify(e.lastTriggerClick)} offenders=${offenders.slice(0, 3).map((o) => `${o.testid || o.tag}:${o.right}`)}`);
    }
  }

  // ---------- 5. deep links at 390 (fresh load)
  await page.setViewportSize({ width: 390, height: 844 });
  for (const [key, pg] of Object.entries(PAGES)) {
    consoleBuf = [];
    await load(page, `${pg.url}?tab=${pg.deepLink}`, "light");
    const f = await barFacts(page);
    const maxY = await page.evaluate(() => ({ maxScrollY: window.__maxScrollY, scrollX: Math.round(window.scrollX) }));
    const active = f.tabs.find((t) => t.selected === "true");
    const file = `alice-${key}-390-deeplink-${pg.deepLink}.png`;
    await shoot(page, file);
    R.deepLinks.push({ page: key, tab: pg.deepLink, url: f.url, scrollLeft: f.viewport.scrollLeft, row: `${f.viewport.scrollWidth}/${f.viewport.clientWidth}`, active: active && { testid: active.testid, text: active.text, rect: active.rect, fullyInViewport: active.fullyInViewport, underline: active.after.opacity }, viewportRect: f.viewport.rect, fades: f.fades, scrollY: f.scrollY, ...maxY, file, console: sumConsole(consoleBuf) });
    console.log(`deeplink ${key} ?tab=${pg.deepLink} scrollLeft=${f.viewport.scrollLeft} active=${active && active.text} inVp=${active && active.fullyInViewport} rect=${JSON.stringify(active && active.rect)} vp=${JSON.stringify(f.viewport.rect)} fadeStart=${f.fades.start.visible} fadeEnd=${f.fades.end.visible} scrollY=${f.scrollY} max=${maxY.maxScrollY}`);
  }
  await ctx.close();

  // ---------- 6. keyboard (deviceScaleFactor 2 for crisp crops)
  const kctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  const kpage = await kctx.newPage();
  wire(kpage, "alice-kbd");
  await login(kpage, USERS.alice);
  const snap = (p) =>
    p.evaluate(() => {
      const a = document.activeElement;
      const cs = getComputedStyle(a);
      const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
      const b = a.getBoundingClientRect(), v = vp.getBoundingClientRect();
      const visiblePx = Math.round(Math.min(b.right, v.right) - Math.max(b.left, v.left));
      return { testid: a.getAttribute("data-testid"), visiblePx, widthPx: Math.round(b.width), visibleOutsideEndFadePx: Math.max(0, Math.round(Math.min(b.right, v.right - 32) - Math.max(b.left, v.left))), focusVisible: a.matches(":focus-visible"), boxShadow: cs.boxShadow, outline: `${cs.outlineStyle} ${cs.outlineWidth}`, rect: { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) }, fullyInViewport: b.left >= v.left - 0.5 && b.right <= v.right + 0.5 && b.top >= v.top - 0.5 && b.bottom <= v.bottom + 0.5, scrollLeft: Math.round(vp.scrollLeft), scrollY: Math.round(scrollY), scrollX: Math.round(scrollX) };
    });
  const ringColor = (p) => p.evaluate(() => { const d = document.createElement("div"); d.style.color = "var(--ring)"; document.body.appendChild(d); const c = getComputedStyle(d).color; d.remove(); return c; });
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h] of [[1440, 900], [390, 844]]) {
      await kpage.setViewportSize({ width: w, height: h });
      await load(kpage, pg.url, "light");
      const ring = await ringColor(kpage);
      const first = kpage.locator('[data-testid="grouped-tab-bar"] [role="tab"]').first();
      await first.focus();
      const seq = [{ step: "focus()", ...(await snap(kpage)) }];
      const n = w === 390 ? (await kpage.locator('[data-testid="grouped-tab-bar"] [role="tab"]').count()) - 1 : 2;
      for (let i = 1; i <= n; i++) {
        await kpage.keyboard.press("ArrowRight");
        await kpage.waitForTimeout(350);
        const sn = await snap(kpage);
        seq.push({ step: `ArrowRight#${i}`, ...sn });
        if (w === 390 && !sn.fullyInViewport) {
          await kpage.waitForTimeout(1000); // give any deferred scroll a chance
          const later = await snap(kpage);
          seq.push({ step: `ArrowRight#${i}+1s`, ...later });
          const pf = `alice-${key}-keyboard-partial-390-light.png`;
          await kpage.screenshot({ path: path.join(OUT, pf), clip: { x: 0, y: Math.max(0, sn.rect.y - 40), width: w, height: sn.rect.h + 80 } });
          shots.push(pf);
        }
      }
      if (w === 390) {
        // Tab-key path to the same partially hidden trigger: focus the 4th trigger, press Tab
        await kpage.locator('[data-testid="grouped-tab-bar"] [role="tab"]').nth(3).focus();
        await kpage.evaluate(() => { const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]'); vp.scrollLeft = 0; });
        await kpage.waitForTimeout(300);
        await kpage.keyboard.press("Tab");
        await kpage.waitForTimeout(350);
        seq.push({ step: "Tab-from-4th", ...(await snap(kpage)) });
        await kpage.locator('[data-testid="grouped-tab-bar"] [role="tab"]').last().focus();
        await kpage.waitForTimeout(300);
        await kpage.keyboard.press("ArrowRight"); // wraps to first
        await kpage.waitForTimeout(350);
        seq.push({ step: "ArrowRight#wrap", ...(await snap(kpage)) });
        await kpage.keyboard.press("ArrowLeft"); // back to last
        await kpage.waitForTimeout(350);
        seq.push({ step: "ArrowLeft#toLast", ...(await snap(kpage)) });
      }
      const cur = seq[seq.length - 1];
      let file = null;
      if (w === 1440 || key === "matter") {
        file = `alice-${key}-keyboard-focus-${w}-light.png`;
        const clip = w === 1440 ? { x: Math.max(0, cur.rect.x - 160), y: Math.max(0, cur.rect.y - 40), width: Math.min(520, w), height: cur.rect.h + 80 } : { x: 0, y: Math.max(0, cur.rect.y - 40), width: w, height: cur.rect.h + 80 };
        await kpage.screenshot({ path: path.join(OUT, file), clip });
        shots.push(file);
      }
      R.keyboard.push({ page: key, viewport: `${w}x${h}`, ring, ringInBoxShadow: seq.slice(1).every((s) => s.boxShadow.includes(ring) && /inset/.test(s.boxShadow)), seq, file });
      console.log(`keyboard ${key} ${w}: ring=${ring} ${seq.map((s) => `${s.step}=${s.testid}(fv=${s.focusVisible},inVp=${s.fullyInViewport},sl=${s.scrollLeft},sy=${s.scrollY},sx=${s.scrollX})`).join(" | ")} shadow=${cur.boxShadow}`);
    }
  }
  await kctx.close();

  // ---------- 7. Carol (member) matter 1440 light
  const cctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const cpage = await cctx.newPage();
  wire(cpage, "carol");
  await login(cpage, USERS.carol);
  consoleBuf = [];
  const cstatus = await load(cpage, PAGES.matter.url, "light");
  const cf = await barFacts(cpage);
  await shoot(cpage, "carol-matter-1440-light.png");
  R.carol = { status: cstatus, checks: loadChecks(cf, "light"), tabs: cf.tabs.map((t) => ({ testid: t.testid, text: t.text, selected: t.selected, haspopup: t.haspopup })), console: sumConsole(consoleBuf), file: "carol-matter-1440-light.png" };
  console.log(`carol ${cstatus} tabs=${cf.tabs.map((t) => `${t.testid}${t.haspopup ? "▾" : ""}`).join(",")}`);
  await cctx.close();
  await browser.close();

  R.console = { ...sumConsole(consoleAll), items: consoleAll.slice(0, 20) };
  R.screenshots = shots;
  fs.writeFileSync(path.join(OUT, "obs-after.json"), JSON.stringify(R));
  console.log(`console total: ${JSON.stringify(R.console)}`);
  console.log(`screenshots: ${shots.length}`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
