// 642C observe-first "before" capture (task 642.11). Read-only: drives the e2e mock-auth stack
// (frontend :3001) as Alice (owner) and Carol (member). Records the GroupedTabBar on the matter and
// client detail pages: DOM (tabs, groups, rect, ancestor position/overflow, scroll widths), tab
// switching (URL + which mechanic: replaceState vs router.replace / RSC fetch), dropdown open,
// scroll-away (sticky?), keyboard focus ring, overflow at 390/768 (F7) and console errors.
// Writes obs-before.json (compact) + screenshots. Run: node obs-before.cjs (nvm node v20).
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
// Ids from the lifecycle run state (tmpdir/kazi-legal-lifecycle-run.json, runId mwe0pu4a52ee).
const PAGES = {
  matter: {
    url: `${BASE}/org/${SLUG}/matters/80f7d731-0a0c-4f94-bcd8-f1d2bfa10535`,
    note: "Sipho litigation matter (lifecycle run mwe0pu4a52ee)",
    // [label, action] — action: {group} = click group trigger; {group, item} = open dropdown, click item
    visits: [
      ["single-tab group Diary", { group: "schedule" }],
      ["inactive multi group Work → Tasks", { group: "work", item: "tasks", shoot: "matter-dropdown-work-open-1440-light.png" }],
      ["inactive multi group Fees → Trust", { group: "finance", item: "trust" }],
      ["dropdown Fees → Statements (group already active)", { group: "finance", item: "statements" }],
    ],
  },
  client: {
    url: `${BASE}/org/${SLUG}/clients/cfb69742-e9fa-4c61-8438-fa42319c30b1`,
    note: "Sipho Ndlovu client (lifecycle run mwe0pu4a52ee) — matters + fee notes + trust",
    visits: [
      ["single-tab group Activity", { group: "activity" }],
      ["inactive multi group Matters → Documents", { group: "work", item: "documents" }],
      ["inactive multi group Fees → Trust", { group: "finance", item: "trust", shoot: "client-dropdown-fees-open-1440-light.png" }],
      ["dropdown Fees → Fee notes (group already active)", { group: "finance", item: "invoices" }],
    ],
  },
};

async function login(page, userId) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", userId);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

const visibleBar = (page) => page.locator('[data-testid="grouped-tab-bar"]').filter({ visible: true }).first();

async function load(page, url, theme) {
  await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
  const resp = await page.goto(url, { waitUntil: "load", timeout: 90000 });
  await visibleBar(page).waitFor({ state: "visible", timeout: 60000 });
  await page.waitForTimeout(2500); // hydration + late console warnings
  return resp ? resp.status() : null;
}

function barFacts(page) {
  return page.evaluate(() => {
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const r = (el) => {
      if (!el) return null;
      const b = el.getBoundingClientRect();
      return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right) };
    };
    const all = [...document.querySelectorAll('[data-testid="grouped-tab-bar"]')];
    const bar = all.find((b) => b.offsetParent !== null || getComputedStyle(b).position === "fixed") || null;
    const ancestors = [];
    if (bar) {
      let el = bar;
      while (el && el !== document.documentElement) {
        const cs = getComputedStyle(el);
        ancestors.push({
          tag: el.tagName.toLowerCase(),
          testid: el.getAttribute("data-testid"),
          id: el.id || null,
          cls: (el.getAttribute("class") || "").slice(0, 140),
          position: cs.position,
          top: cs.top,
          zIndex: cs.zIndex,
          overflowX: cs.overflowX,
          overflowY: cs.overflowY,
          contain: cs.contain,
          scrollH: el.scrollHeight,
          clientH: el.clientHeight,
          scrollW: el.scrollWidth,
          clientW: el.clientWidth,
        });
        el = el.parentElement;
      }
      const hcs = getComputedStyle(document.documentElement);
      ancestors.push({ tag: "html", position: hcs.position, overflowX: hcs.overflowX, overflowY: hcs.overflowY });
    }
    const tabs = bar
      ? [...bar.querySelectorAll('[role="tab"]')].map((t) => ({
          testid: t.getAttribute("data-testid"),
          text: txt(t),
          selected: t.getAttribute("aria-selected"),
          haspopup: t.getAttribute("aria-haspopup"),
          expanded: t.getAttribute("aria-expanded"),
          controls: t.getAttribute("aria-controls"),
          dataState: t.getAttribute("data-state"),
          hasChevron: !!t.querySelector("svg"),
          rect: r(t),
        }))
      : [];
    const barCs = bar ? getComputedStyle(bar) : null;
    return {
      barCount: all.length,
      barVisibleCount: all.filter((b) => b.offsetParent !== null).length,
      barRect: r(bar),
      barClass: bar ? bar.getAttribute("class") : null,
      barPosition: barCs && barCs.position,
      barBackground: barCs && barCs.backgroundColor,
      barScrollWidth: bar && bar.scrollWidth,
      barClientWidth: bar && bar.clientWidth,
      barOverflowX: barCs && barCs.overflowX,
      tabs,
      dropdownGroups: tabs.filter((t) => t.haspopup).map((t) => t.testid),
      ancestorsNonVisibleOverflow: ancestors.filter((a) => a.overflowX !== "visible" || a.overflowY !== "visible").map((a) => `${a.tag}${a.testid ? `[${a.testid}]` : ""}${a.id ? `#${a.id}` : ""} ${a.overflowX}/${a.overflowY}`),
      ancestorsPositioned: ancestors.filter((a) => a.position && a.position !== "static").map((a) => `${a.tag} ${a.position}`),
      ancestors,
      tabpanels: [...document.querySelectorAll('[role="tabpanel"]')].map((p) => ({ id: p.id, state: p.getAttribute("data-state"), labelledby: p.getAttribute("aria-labelledby"), hidden: p.hidden })),
      docScrollWidth: document.documentElement.scrollWidth,
      docClientWidth: document.documentElement.clientWidth,
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      docScrollHeight: document.documentElement.scrollHeight,
      htmlClass: document.documentElement.className,
      url: location.pathname + location.search,
    };
  });
}

// Which element actually scrolls the page (window vs an app-shell container)?
function scrollProbe(page, dy) {
  return page.evaluate((dy) => {
    const bar = [...document.querySelectorAll('[data-testid="grouped-tab-bar"]')].find((b) => b.offsetParent !== null);
    const before = Math.round(bar.getBoundingClientRect().top);
    let scroller = null;
    let el = bar.parentElement;
    while (el && el !== document.body && el !== document.documentElement) {
      const cs = getComputedStyle(el);
      if (/(auto|scroll)/.test(cs.overflowY) && el.scrollHeight > el.clientHeight) {
        scroller = el;
        break;
      }
      el = el.parentElement;
    }
    const target = scroller || document.scrollingElement;
    const maxScroll = target.scrollHeight - target.clientHeight;
    target.scrollTop += dy;
    return {
      scroller: scroller ? `${scroller.tagName.toLowerCase()}.${(scroller.getAttribute("class") || "").slice(0, 80)}` : "document.scrollingElement (window)",
      maxScroll,
      scrolledBy: Math.round(target.scrollTop),
      barTopBefore: before,
    };
  }, dy);
}

(async () => {
  const browser = await chromium.launch();
  const results = { capturedAt: new Date().toISOString(), pages: Object.fromEntries(Object.entries(PAGES).map(([k, v]) => [k, { url: v.url, note: v.note }])), loads: [], visits: [], scroll: [], keyboard: [], f7: [], carol: null };
  const shots = [];

  // ---------------- Alice ----------------
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  let consoleBuf = [];
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning") consoleBuf.push({ type: m.type(), text: m.text().slice(0, 400) });
  });
  page.on("pageerror", (e) => consoleBuf.push({ type: "pageerror", text: String(e).slice(0, 400) }));
  let reqBuf = [];
  page.on("request", (rq) => {
    const h = rq.headers();
    const isRsc = h["rsc"] === "1" || /[?&]_rsc=/.test(rq.url());
    if (isRsc || rq.isNavigationRequest()) reqBuf.push({ rsc: isRsc, nav: rq.isNavigationRequest(), method: rq.method(), url: rq.url().replace(BASE, "").slice(0, 200) });
  });
  await login(page, USERS.alice);

  const summarizeConsole = (buf) => ({
    errors: buf.filter((c) => c.type === "error" || c.type === "pageerror").length,
    warnings: buf.filter((c) => c.type === "warning").length,
    hydration: buf.filter((c) => /hydrat/i.test(c.text)).length,
    items: buf.slice(0, 8),
  });

  // 1. per-page, per-viewport/theme loads
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h, theme] of [[1440, 900, "light"], [1440, 900, "dark"], [1024, 768, "light"], [1024, 768, "dark"]]) {
      await page.setViewportSize({ width: w, height: h });
      consoleBuf = [];
      const status = await load(page, pg.url, theme);
      const facts = await barFacts(page);
      if (facts.barRect && facts.barRect.y > h - 60) {
        await visibleBar(page).evaluate((el) => el.scrollIntoView({ block: "center" }));
        await page.waitForTimeout(300);
      }
      const file = `alice-${key}-${w}-${theme}.png`;
      await page.screenshot({ path: path.join(OUT, file) });
      shots.push(file);
      results.loads.push({ user: "alice", page: key, viewport: `${w}x${h}`, theme, status, file, facts, console: summarizeConsole(consoleBuf) });
      console.log(`load ${key} ${w} ${theme} status=${status} bars=${facts.barCount}/${facts.barVisibleCount} barY=${facts.barRect && facts.barRect.y} pos=${facts.barPosition} sw=${facts.barScrollWidth}/${facts.barClientWidth} doc=${facts.docScrollWidth}/${facts.innerWidth} console=${consoleBuf.length}`);
    }
  }

  // 2. tab visits (1440 light)
  await page.setViewportSize({ width: 1440, height: 900 });
  for (const [key, pg] of Object.entries(PAGES)) {
    consoleBuf = [];
    await load(page, pg.url, "light");
    // Instrument history API to see which mechanic writes ?tab=
    await page.evaluate(() => {
      window.__hist = [];
      for (const fn of ["pushState", "replaceState"]) {
        const orig = history[fn].bind(history);
        history[fn] = (s, t, u) => {
          window.__hist.push({ fn, url: String(u), nextRouter: !!(s && (s.__NA || s.__PRIVATE_NEXTJS_INTERNALS_TREE)) });
          return orig(s, t, u);
        };
      }
    });
    for (const [label, act] of pg.visits) {
      reqBuf = [];
      await page.evaluate(() => (window.__hist = []));
      const urlBefore = page.url();
      const group = page.getByTestId(`tab-group-${act.group}`).filter({ visible: true });
      let menuOpenedOnClick = null;
      await group.click();
      await page.waitForTimeout(1200);
      const afterGroupClick = await page.evaluate(() => {
        const bar = [...document.querySelectorAll('[data-testid="grouped-tab-bar"]')].find((b) => b.offsetParent !== null);
        return {
          search: location.search,
          menuOpen: !!document.querySelector('[role="menu"]'),
          bodyPointerEvents: getComputedStyle(document.body).pointerEvents,
          selected: bar ? [...bar.querySelectorAll('[role="tab"][aria-selected="true"]')].map((t) => t.getAttribute("data-testid")) : [],
          hist: window.__hist.map((h) => h.fn),
        };
      });
      if (act.item) {
        const item = page.getByTestId(`tab-item-${act.item}`).filter({ visible: true });
        let open = await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false);
        menuOpenedOnClick = open;
        if (!open) {
          await page.waitForTimeout(800);
          await group.click();
          open = await item.waitFor({ state: "visible", timeout: 3000 }).then(() => true, () => false);
        }
        const menuFacts = open
          ? await page.evaluate(() => {
              const menu = document.querySelector('[role="menu"]');
              if (!menu) return null;
              const b = menu.getBoundingClientRect();
              return {
                items: [...menu.querySelectorAll('[role="menuitem"]')].map((m) => ({ testid: m.getAttribute("data-testid"), text: m.textContent.trim(), cls: (m.getAttribute("class") || "").includes("bg-slate-100") ? "active-bg" : "" })),
                rect: { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) },
              };
            })
          : null;
        const trig = await group.evaluate((t) => ({ expanded: t.getAttribute("aria-expanded"), haspopup: t.getAttribute("aria-haspopup"), state: t.getAttribute("data-state") }));
        if (act.shoot && open) {
          await page.screenshot({ path: path.join(OUT, act.shoot) });
          shots.push(act.shoot);
        }
        if (open) await page.getByTestId(`tab-item-${act.item}`).filter({ visible: true }).click();
        act._menu = { menuOpenedOnFirstClick: menuOpenedOnClick, opened: open, menuFacts, triggerWhileOpen: trig };
      }
      await page.waitForTimeout(2000);
      const after = await page.evaluate(() => {
        const bar = [...document.querySelectorAll('[data-testid="grouped-tab-bar"]')].find((b) => b.offsetParent !== null);
        const sel = bar ? [...bar.querySelectorAll('[role="tab"]')].filter((t) => t.getAttribute("aria-selected") === "true").map((t) => ({ testid: t.getAttribute("data-testid"), text: t.textContent.replace(/\s+/g, " ").trim() })) : [];
        const panel = document.querySelector('[role="tabpanel"][data-state="active"]');
        return { hist: window.__hist, selected: sel, activePanelId: panel ? panel.id : null, activePanelHead: panel ? panel.textContent.replace(/\s+/g, " ").trim().slice(0, 120) : null };
      });
      const urlAfter = page.url();
      results.visits.push({
        page: key,
        label,
        action: { group: act.group, item: act.item || null },
        urlBefore: urlBefore.replace(BASE, ""),
        urlAfter: urlAfter.replace(BASE, ""),
        tabParam: new URL(urlAfter).searchParams.get("tab"),
        historyCalls: after.hist,
        rscRequests: reqBuf.filter((r) => r.rsc).length,
        navRequests: reqBuf.filter((r) => r.nav).length,
        requests: reqBuf.slice(0, 6),
        selected: after.selected,
        activePanelId: after.activePanelId,
        activePanelHead: after.activePanelHead,
        menu: act._menu || null,
        afterGroupClick,
      });
      await page.keyboard.press("Escape").catch(() => {});
      await page.waitForTimeout(300);
      console.log(`visit ${key} "${label}" tab=${new URL(urlAfter).searchParams.get("tab")} hist=${JSON.stringify(after.hist.map((h) => h.fn))} rsc=${reqBuf.filter((r) => r.rsc).length} sel=${JSON.stringify(after.selected.map((s) => s.text))}`);
    }
    results.visitConsole = results.visitConsole || {};
    results.visitConsole[key] = summarizeConsole(consoleBuf);
  }

  // 3. scroll-away probe (1440 light, default tab, then a long tab)
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const tab of [null, key === "matter" ? "activity" : "audit"]) {
      await load(page, tab ? `${pg.url}?tab=${tab}` : pg.url, "light");
      const p1 = await scrollProbe(page, 600);
      await page.waitForTimeout(400);
      const topAfter = await visibleBar(page).evaluate((b) => Math.round(b.getBoundingClientRect().top));
      const inView = topAfter >= 0 && topAfter < 900;
      let file = null;
      if (!tab) {
        file = `alice-${key}-scrolled600-1440-light.png`;
        await page.screenshot({ path: path.join(OUT, file) });
        shots.push(file);
      }
      results.scroll.push({ page: key, tab: tab || "(default)", ...p1, barTopAfter: topAfter, barInView: inView, file });
      console.log(`scroll ${key} ${tab || "default"} scroller=${p1.scroller} max=${p1.maxScroll} scrolled=${p1.scrolledBy} top ${p1.barTopBefore}→${topAfter} inView=${inView}`);
    }
  }

  // 4. keyboard: focus first tab, ArrowRight x2
  for (const [key, pg] of Object.entries(PAGES)) {
    await load(page, pg.url, "light");
    const first = page.locator('[data-testid="grouped-tab-bar"] [role="tab"]').filter({ visible: true }).first();
    await first.focus();
    const seq = [];
    const snap = () =>
      page.evaluate(() => {
        const a = document.activeElement;
        const cs = getComputedStyle(a);
        return {
          testid: a.getAttribute("data-testid"),
          role: a.getAttribute("role"),
          text: a.textContent.replace(/\s+/g, " ").trim(),
          focusVisible: a.matches(":focus-visible"),
          outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor} offset ${cs.outlineOffset}`,
          boxShadow: cs.boxShadow,
          tabIndex: a.tabIndex,
          url: location.search,
        };
      });
    seq.push({ step: "focus()", ...(await snap()) });
    await page.keyboard.press("ArrowRight");
    seq.push({ step: "ArrowRight#1", ...(await snap()) });
    await page.keyboard.press("ArrowRight");
    seq.push({ step: "ArrowRight#2", ...(await snap()) });
    const file = `alice-${key}-keyboard-focus-1440-light.png`;
    const bb = await visibleBar(page).boundingBox();
    await page.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: Math.max(0, bb.y - 120), width: 1440, height: Math.min(260, 900 - Math.max(0, bb.y - 120)) } });
    shots.push(file);
    // Tab key: does Tab move between triggers (roving tabindex?) or leave the bar?
    await page.keyboard.press("Tab");
    seq.push({ step: "Tab", ...(await snap()) });
    // Enter on a focused, inactive multi-tab group: opens the menu or navigates?
    await page.keyboard.press("Escape");
    await page.getByTestId("tab-group-work").filter({ visible: true }).focus();
    await page.keyboard.press("Enter");
    await page.waitForTimeout(1200);
    const enterOnInactiveGroup = await page.evaluate(() => ({
      search: location.search,
      menuOpen: !!document.querySelector('[role="menu"]'),
      focused: document.activeElement.getAttribute("data-testid") || document.activeElement.getAttribute("role"),
      workSelected: document.querySelector('[data-testid="tab-group-work"]').getAttribute("aria-selected"),
    }));
    await page.keyboard.press("Escape");
    results.keyboard.push({ page: key, seq, file, enterOnInactiveGroup });
    console.log(`keyboard ${key}: ${seq.map((s) => `${s.step}=${s.testid}(fv=${s.focusVisible},${s.outline})`).join(" | ")}`);
  }

  // 5. F7 overflow probe (light) at 390 and 768
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h] of [[390, 844], [768, 1024]]) {
      await page.setViewportSize({ width: w, height: h });
      consoleBuf = [];
      await load(page, pg.url, "light");
      const facts = await barFacts(page);
      const file = `alice-${key}-${w}-light.png`;
      await page.screenshot({ path: path.join(OUT, file) });
      shots.push(file);
      const widest = await page.evaluate(() => {
        const vw = window.innerWidth;
        const offenders = [];
        for (const el of document.querySelectorAll("body *")) {
          const b = el.getBoundingClientRect();
          if (b.right > vw + 1 && b.width > 0 && el.offsetParent !== null) offenders.push({ tag: el.tagName.toLowerCase(), testid: el.getAttribute("data-testid"), cls: (el.getAttribute("class") || "").slice(0, 80), right: Math.round(b.right) });
        }
        // keep the outermost offenders only
        return offenders.slice(0, 12);
      });
      results.f7.push({
        page: key,
        viewport: `${w}x${h}`,
        docScrollWidth: facts.docScrollWidth,
        innerWidth: facts.innerWidth,
        pageOverflowsX: facts.docScrollWidth > facts.innerWidth,
        barScrollWidth: facts.barScrollWidth,
        barClientWidth: facts.barClientWidth,
        barRight: facts.barRect && facts.barRect.right,
        barOverflowX: facts.barOverflowX,
        lastTabRight: facts.tabs.length ? facts.tabs[facts.tabs.length - 1].rect.right : null,
        tabs: facts.tabs.map((t) => `${t.testid}@${t.rect.x}-${t.rect.right}`),
        firstOffenders: widest,
        file,
        console: summarizeConsole(consoleBuf),
      });
      console.log(`f7 ${key} ${w}: doc ${facts.docScrollWidth}/${facts.innerWidth} bar ${facts.barScrollWidth}/${facts.barClientWidth} right=${facts.barRect && facts.barRect.right}`);
    }
  }
  await ctx.close();

  // ---------------- Carol (member) matter 1440 light ----------------
  const cctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const cpage = await cctx.newPage();
  const cbuf = [];
  cpage.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning") cbuf.push({ type: m.type(), text: m.text().slice(0, 400) });
  });
  cpage.on("pageerror", (e) => cbuf.push({ type: "pageerror", text: String(e).slice(0, 400) }));
  await login(cpage, USERS.carol);
  cbuf.length = 0;
  const cstatus = await load(cpage, PAGES.matter.url, "light");
  const cfacts = await barFacts(cpage);
  await cpage.screenshot({ path: path.join(OUT, "carol-matter-1440-light.png") });
  shots.push("carol-matter-1440-light.png");
  results.carol = { page: "matter", viewport: "1440x900", theme: "light", status: cstatus, facts: cfacts, console: summarizeConsole(cbuf), file: "carol-matter-1440-light.png" };
  console.log(`carol matter status=${cstatus} tabs=${cfacts.tabs.map((t) => t.testid).join(",")}`);
  await cctx.close();

  await browser.close();
  results.screenshots = shots;
  fs.writeFileSync(path.join(OUT, "obs-before.json"), JSON.stringify(results));
  console.log(`screenshots: ${shots.length}`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
