// 641B "after" capture (task 641.12). Read-only: drives the e2e mock-auth stack (frontend :3001,
// built from the 641B worktree) as Alice (owner) and Carol (member). Screenshots the matter detail
// page (full page + hero clip) at 1024x768 / 1440x900 (+1280 measure) in light + dark; dumps hero
// DOM facts, tile strip contents + bounding boxes + overflow + colour contrast, Overview KPI
// cards, console errors/warnings; walks every tab; opens New Matter / Edit Matter dialogs
// (no submit). Output: obs-after.json + PNGs next to this file.
// Run: node obs-after.cjs   (nvm node v20; requires the worktree frontend's @playwright/test)
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-641B/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;

const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
const MATTERS = {
  rich: { id: "f04aaa84-880f-41c0-8a0d-1cd67dbdf99d", note: "Ndlovu v RAF (Alice lead + Carol member; trust, open fee note, POSTPONED 2026-12-12 + SCHEDULED 2027-01-11 motion)" },
  estate: { id: "a9245382-b2c3-4150-9c97-cb8f58f17bfd", note: "Estate Late Peter Moroka (Alice; trust x4, open fee note, unbilled time, no court dates)" },
  closed: { id: "9eb0f749-a1b7-4565-b28b-c509f0a2a56f", note: "Ndlovu v RAF Depth II (CLOSED; trust, unbilled)" },
  internal: { id: "fbfb09aa-9a06-4571-9147-fe3b8f52b5cf", note: "Time Test Project (Alice + Carol; internal, unbilled time)" },
  // Same seed matters as before/ (different ids on the fresh stack) for an apples-to-apples compare.
  shared: { id: "a3b0ec9e-903f-46a8-a6ba-df650ddc6823", note: "Budget Test Project fzfy (Alice + Carol; budget + time, internal) = before/ 'shared'" },
  website: { id: "58604884-c735-4d92-a2cd-a01599ee124e", note: "Website Redesign (Alice; client Acme, fee note) = before/ 'rich'" },
};
const VP = { 1024: { w: 1024, h: 768 }, 1280: { w: 1280, h: 800 }, 1440: { w: 1440, h: 900 } };

// [user, matterKey, viewportKey, theme]
const PLAN = [
  ...["alice", "carol"].flatMap((u) =>
    [1024, 1440].flatMap((w) => ["light", "dark"].map((t) => [u, "rich", w, t]))
  ),
  ["alice", "rich", 1280, "light"],
  ["alice", "estate", 1440, "light"],
  ["alice", "estate", 1024, "light"],
  ["alice", "closed", 1440, "light"],
  ["carol", "internal", 1440, "light"],
  ["carol", "internal", 1024, "dark"],
  ["alice", "shared", 1440, "light"],
  ["alice", "shared", 1024, "light"],
  ["carol", "shared", 1440, "light"],
  ["carol", "shared", 1024, "light"],
  ["alice", "website", 1440, "light"],
];

async function login(page, userId) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", userId);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

async function domFacts(page) {
  return page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    // Resolve any CSS colour (oklch etc.) to sRGB via a canvas pixel.
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const cx = cv.getContext("2d", { willReadFrequently: true });
    const rgb = (c) => {
      cx.clearRect(0, 0, 1, 1);
      cx.fillStyle = "#000";
      cx.fillStyle = c;
      cx.fillRect(0, 0, 1, 1);
      const d = cx.getImageData(0, 0, 1, 1).data;
      return [d[0], d[1], d[2], d[3]];
    };
    const lum = ([r, g, b]) => {
      const f = (v) => {
        v /= 255;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
      };
      return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
    };
    const bgOf = (el) => {
      for (let e = el; e; e = e.parentElement) {
        const c = rgb(getComputedStyle(e).backgroundColor);
        if (c[3] > 0) return c;
      }
      return [255, 255, 255, 255];
    };
    const contrast = (el) => {
      if (!el) return null;
      const fg = rgb(getComputedStyle(el).color);
      const bg = bgOf(el);
      const [a, b] = [lum(fg), lum(bg)].sort((x, y) => y - x);
      return { fg: fg.slice(0, 3).join(","), bg: bg.slice(0, 3).join(","), ratio: +((a + 0.05) / (b + 0.05)).toFixed(2) };
    };
    const ovf = (el) => (el ? { sw: el.scrollWidth, cw: el.clientWidth, overflow: el.scrollWidth > el.clientWidth } : null);

    const card = q('[data-testid="matter-header-card"]');
    const fieldsDl = q('[data-testid="matter-header-fields"]');
    const fields = fieldsDl
      ? [...fieldsDl.querySelectorAll('[data-testid^="matter-header-field-"]')].map((f) => ({
          testid: f.getAttribute("data-testid"),
          dt: txt(f.querySelector("dt")),
          dd: txt(f.querySelector("dd")),
        }))
      : null;
    const actions = q('[data-testid="header-lifecycle-actions"]');
    const strip = card ? card.querySelector('[data-slot="detail-hero-tiles"]') : null;
    const tiles = strip
      ? [...strip.children].map((wrap) => {
          const sc = wrap.querySelector('[data-slot="stat-card"]');
          const r = wrap.getBoundingClientRect();
          const label = sc && sc.querySelector('[data-slot="stat-card-label"]');
          const value = sc && sc.querySelector('[data-slot="stat-card-value"]');
          const hint = sc && sc.querySelector('[data-slot="stat-card-hint"]');
          const err = sc && sc.querySelector('[role="status"]');
          return {
            label: txt(label),
            value: txt(value),
            hint: txt(hint),
            errorText: txt(err),
            hintHasIcon: !!(hint && hint.querySelector("svg")),
            variant: sc && sc.getAttribute("data-variant"),
            state: sc && sc.getAttribute("data-state"),
            role: sc && sc.getAttribute("role"),
            labelledBy: !!(sc && sc.getAttribute("aria-labelledby")),
            isLink: wrap.tagName === "A",
            box: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
            valueOverflow: ovf(value),
            hintOverflow: ovf(hint),
            labelOverflow: ovf(label),
            valueClass: value && value.className,
            contrast: { label: contrast(label), value: contrast(value), hint: contrast(hint) },
          };
        })
      : [];
    const tops = [...new Set(tiles.map((t) => t.box.y))].sort((a, b) => a - b);
    const lefts = [...new Set(tiles.map((t) => t.box.x))].sort((a, b) => a - b);
    const grid = tiles.map((t) => `${t.label}@r${tops.indexOf(t.box.y) + 1}c${lefts.indexOf(t.box.x) + 1}`);
    const cards = [...document.querySelectorAll('[data-testid^="metric-card-"]')]
      .filter((c) => c.getAttribute("data-testid") !== "metric-card-grid")
      .map((c) => ({ testid: c.getAttribute("data-testid"), text: txt(c) }));
    const h1s = [...document.querySelectorAll("h1")].map((h) => ({ testid: h.getAttribute("data-testid"), text: txt(h) }));
    const cardRect = card ? card.getBoundingClientRect() : null;
    const tablist = q('[role="tablist"]');
    return {
      url: location.pathname + location.search,
      htmlClass: document.documentElement.className,
      docOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      headerCardPresent: !!card,
      headerCardSlot: card && card.getAttribute("data-slot"),
      headerCardWidth: cardRect ? Math.round(cardRect.width) : null,
      headerCardText: txt(card),
      headerAboveTabs: card && tablist ? cardRect.bottom <= tablist.getBoundingClientRect().top + 1 : null,
      h1Count: h1s.length,
      h1s,
      matterNameInMain: !!q('#main-content [data-testid="matter-name"]'),
      matterName: txt(q('[data-testid="matter-name"]')),
      identifier: txt(q('[data-slot="detail-hero-identifier"]')),
      statusBlock: txt(q('[data-slot="detail-hero-status"]')),
      statusBadge: txt(q('[data-testid="project-status-badge"]')),
      closedAt: txt(q('[data-testid="project-closed-at"]')),
      subtitle: txt(q('[data-slot="detail-hero-subtitle"]')),
      headerFieldsPresent: !!fieldsDl,
      headerFieldsInsideCard: !!(card && fieldsDl && card.contains(fieldsDl)),
      headerFields: fields,
      lifecycleActionsPresent: !!actions,
      lifecycleButtons: actions
        ? [...actions.querySelectorAll("button, a")].map((b) => ({
            text: txt(b),
            testid: b.getAttribute("data-testid"),
            ariaLabel: b.getAttribute("aria-label"),
          }))
        : null,
      stripClass: strip && strip.className,
      tileCount: tiles.length,
      tiles,
      grid,
      gridShape: tiles.length ? `${lefts.length} col x ${tops.length} row` : "none",
      healthHeader: txt(q('[data-testid="project-health-header"]')),
      metricGridPresent: !!q('[data-testid="metric-card-grid"]'),
      metricCards: cards,
      upcomingDeadlines: txt(q('[data-testid="matter-upcoming-deadlines-tile"]')),
      tabs: [...document.querySelectorAll('[role="tab"]')].map((t) => ({ text: txt(t), selected: t.getAttribute("aria-selected") })),
    };
  });
}

const isHydration = (t) => /hydrat|did not match|server rendered html|text content does not match/i.test(t);

(async () => {
  const browser = await chromium.launch();
  const results = { loads: [], tabs: [], dialogs: [] };

  for (const userKey of ["alice", "carol"]) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    let consoleBuf = [];
    page.on("console", (m) => {
      if (m.type() === "error" || m.type() === "warning") consoleBuf.push({ type: m.type(), text: m.text().slice(0, 600) });
    });
    page.on("pageerror", (e) => consoleBuf.push({ type: "pageerror", text: String(e).slice(0, 600) }));
    await login(page, USERS[userKey]);

    const open = async (matterKey, vpKey, theme) => {
      const vp = VP[vpKey];
      await page.setViewportSize({ width: vp.w, height: vp.h });
      await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
      consoleBuf = [];
      const resp = await page.goto(`${BASE}/org/${SLUG}/matters/${MATTERS[matterKey].id}`, { waitUntil: "load", timeout: 90000 });
      let h1ok = true;
      try {
        await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
        await page.locator('[data-testid="metric-card-grid"]').waitFor({ state: "visible", timeout: 20000 }).catch(() => {});
      } catch {
        h1ok = false;
      }
      await page.waitForTimeout(2500);
      return { resp, h1ok };
    };

    for (const [, matterKey, vpKey, theme] of PLAN.filter((p) => p[0] === userKey)) {
      const { resp, h1ok } = await open(matterKey, vpKey, theme);
      const base = `${userKey}-${matterKey}-${vpKey}-${theme}`;
      await page.screenshot({ path: path.join(OUT, `${base}.png`), fullPage: true });
      const hero = page.locator('[data-testid="matter-header-card"]');
      if (await hero.count()) await hero.screenshot({ path: path.join(OUT, `${base}-hero.png`) });
      const facts = await domFacts(page);
      results.loads.push({
        user: userKey, matter: matterKey, matterId: MATTERS[matterKey].id, viewport: `${VP[vpKey].w}x${VP[vpKey].h}`, theme,
        httpStatus: resp ? resp.status() : null, h1ok, file: `${base}.png`, facts,
        console: consoleBuf, hydrationWarnings: consoleBuf.filter((c) => isHydration(c.text)).length,
      });
      console.log(`captured ${base} status=${resp && resp.status()} h1=${h1ok} html=${facts.htmlClass} tiles=${facts.tileCount} ${facts.gridShape} console=${consoleBuf.length}`);
    }

    if (userKey === "alice") {
      // Tab walk: header must stay rendered above the tabs on every tab.
      await open("rich", 1440, "light");
      // The tab bar is grouped: some tabs (Details/Work/Fees/Client/Activity) are menu triggers
      // (aria-haspopup=menu) whose menuitems are the real tabs. Enumerate first, then visit each.
      const groups = (await page.getByRole("tab").allTextContents()).map((t) => t.replace(/\s+/g, " ").trim());
      const targets = [];
      for (const g of groups) {
        const tab = page.getByRole("tab", { name: g, exact: true });
        if ((await tab.getAttribute("aria-haspopup")) === "menu") {
          await tab.click();
          await page.getByRole("menu").waitFor({ state: "visible", timeout: 10000 });
          const items = (await page.getByRole("menuitem").allTextContents()).map((t) => t.replace(/\s+/g, " ").trim());
          await page.keyboard.press("Escape");
          await page.waitForTimeout(300);
          items.forEach((it) => targets.push([g, it]));
        } else targets.push([g, null]);
      }
      for (const [g, item] of targets) {
        consoleBuf = [];
        const name = item ? `${g} > ${item}` : g;
        // A group tab's accessible name becomes "<group> · <item>" once one of its items is active.
        await page.getByRole("tab", { name: new RegExp(`^${g}( ·|$)`) }).click();
        if (item) {
          await page.getByRole("menu").waitFor({ state: "visible", timeout: 10000 });
          await page.getByRole("menuitem", { name: item, exact: true }).click();
        }
        await page.waitForTimeout(2000);
        const f = await page.evaluate(() => {
          const card = document.querySelector('[data-testid="matter-header-card"]');
          const tl = document.querySelector('[role="tablist"]');
          const sel = document.querySelector('[role="tab"][aria-selected="true"]');
          return {
            url: location.pathname + location.search,
            headerPresent: !!card,
            headerVisible: !!(card && card.getBoundingClientRect().height > 0),
            tileCount: card ? card.querySelectorAll('[data-slot="detail-hero-tiles"] [data-slot="stat-card"]').length : 0,
            headerAboveTabs: card && tl ? card.getBoundingClientRect().bottom <= tl.getBoundingClientRect().top + 1 : null,
            selectedTab: sel ? sel.textContent.replace(/\s+/g, " ").trim() : null,
            h1Count: document.querySelectorAll("h1").length,
          };
        });
        results.tabs.push({ tab: name, ...f, console: consoleBuf });
        console.log(`tab ${name}: header=${f.headerPresent} tiles=${f.tileCount} above=${f.headerAboveTabs} sel=${f.selectedTab}`);
        if (/^Fees >/.test(name) && !results.tabShot) {
          results.tabShot = `alice-rich-1440-light-tab-${(item || g).replace(/\W+/g, "-").toLowerCase()}.png`;
          await page.screenshot({ path: path.join(OUT, results.tabShot), fullPage: false });
        }
      }

      // Dialogs at 1024, light + dark (open only, no submit).
      for (const theme of ["light", "dark"]) {
        await page.setViewportSize({ width: 1024, height: 768 });
        await page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
        consoleBuf = [];
        await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: "load", timeout: 90000 });
        await page.locator("h1").first().waitFor({ state: "visible", timeout: 60000 });
        await page.waitForTimeout(2000);
        await page.getByRole("button", { name: "New Matter" }).first().click();
        const dlg = page.getByRole("dialog");
        await dlg.waitFor({ state: "visible", timeout: 15000 });
        await page.waitForTimeout(800);
        results.dialogs.push({ dialog: "create", theme, ...(await dialogFacts(page)), console: consoleBuf });
        await page.screenshot({ path: path.join(OUT, `alice-dialog-create-1024-${theme}.png`) });
        // The scroll container is DialogContent's inner max-h-[90vh] overflow-y-auto div, not
        // [role=dialog] itself: wheel over the dialog to reach the footer, then record it.
        await page.mouse.move(512, 400);
        await page.mouse.wheel(0, 2000);
        await page.waitForTimeout(300);
        await page.screenshot({ path: path.join(OUT, `alice-dialog-create-1024-${theme}-scrolled.png`) });
        results.dialogs.push({ dialog: "create", theme, afterWheel: true, ...(await dialogFacts(page)) });
        await page.keyboard.press("Escape");

        await open("rich", 1024, theme);
        await page.getByTestId("overflow-actions-trigger").click();
        await page.getByRole("menuitem", { name: "Edit Matter" }).click();
        await page.getByRole("dialog").waitFor({ state: "visible", timeout: 15000 });
        await page.waitForTimeout(800);
        results.dialogs.push({ dialog: "edit", theme, ...(await dialogFacts(page)), console: consoleBuf });
        await page.screenshot({ path: path.join(OUT, `alice-dialog-edit-1024-${theme}.png`) });
        // The scroll container is DialogContent's inner max-h-[90vh] overflow-y-auto div, not
        // [role=dialog] itself: wheel over the dialog to reach the footer, then record it.
        await page.mouse.move(512, 400);
        await page.mouse.wheel(0, 2000);
        await page.waitForTimeout(300);
        await page.screenshot({ path: path.join(OUT, `alice-dialog-edit-1024-${theme}-scrolled.png`) });
        results.dialogs.push({ dialog: "edit", theme, afterWheel: true, ...(await dialogFacts(page)) });
        await page.keyboard.press("Escape");
        console.log(`dialogs ${theme} captured`);
      }
    }
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "obs-after.json"), JSON.stringify(results, null, 2));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});

async function dialogFacts(page) {
  return page.evaluate(() => {
    const d = document.querySelector('[role="dialog"]');
    const txt = (el) => (el ? el.textContent.replace(/\s+/g, " ").trim() : null);
    const r = d.getBoundingClientRect();
    return {
      title: txt(d.querySelector('[data-slot="dialog-title"], h2')),
      description: txt(d.querySelector('[data-slot="dialog-description"]')),
      headings: [...d.querySelectorAll("h2, h3, h4, legend")].map(txt),
      labels: [...d.querySelectorAll("label")].map(txt),
      buttons: [...d.querySelectorAll("button")].map((b) => txt(b) || b.getAttribute("aria-label")),
      footer: txt(d.querySelector('[data-slot="dialog-footer"]')),
      box: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
      scroller: (() => { const sc = [...d.querySelectorAll("*")].find((e) => /(auto|scroll)/.test(getComputedStyle(e).overflowY)); return sc ? { sh: sc.scrollHeight, ch: sc.clientHeight, scrollTop: Math.round(sc.scrollTop), scrollable: sc.scrollHeight > sc.clientHeight } : null; })(),
      sectionHeadings: [...d.querySelectorAll("form p")].map(txt).filter((t) => t && t === t.toUpperCase() || /details|classification/i.test(t || "")),
      footerTop: (() => { const f = d.querySelector('[data-slot="dialog-footer"]'); return f ? Math.round(f.getBoundingClientRect().top) : null; })(),
      footerVisibleWithoutScroll: (() => { const f = d.querySelector('[data-slot="dialog-footer"]'); if (!f) return null; const fr = f.getBoundingClientRect(); return fr.bottom <= Math.min(r.bottom, window.innerHeight); })(),
      footerBorderTop: (() => { const f = d.querySelector('[data-slot="dialog-footer"]'); return f ? getComputedStyle(f).borderTopWidth : null; })(),
      vh: window.innerHeight,
    };
  });
}
