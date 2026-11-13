// 642C re-observation after fix 664a58068 (D-642C-OBS-1: keyboard focus hidden under the end fade
// at 390). Read-only: drives the e2e mock-auth stack (:3001, built from 664a58068) as Alice with the
// Playwright library. Measures focused-trigger clearance from the edge fades (arrow keys, Tab,
// Shift+Tab, wrap), mouse clicks inside the end fade, deep links, wide layouts and the console.
// Run: node obs-after-fix.cjs (nvm node v20). Adapted from ../after/obs-after.cjs.
const path = require("path");
const fs = require("fs");
const PW_PATH =
  process.env.PW_PATH ||
  require.resolve("@playwright/test", { paths: [path.resolve(__dirname, "../../../../../frontend")] });
const { chromium } = require(PW_PATH);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const FADE = 32;
// Fresh stack ids (lifecycle run mwe5644z5770), found in tenant_7d218705360b.projects/customers.
const PAGES = {
  matter: { url: `${BASE}/org/${SLUG}/matters/e1f5964b-b227-46f2-84a0-463b35aaa834`, name: "Ndlovu v RAF mwe5644z5770", target4: "tab-group-client", deepLink: "disbursements" },
  client: { url: `${BASE}/org/${SLUG}/clients/94221767-a984-4192-ba8e-7a104b10d1fc`, name: "Sipho Ndlovu mwe5644z5770", target4: "tab-group-compliance", deepLink: "audit" },
};

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", "user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

const TABS = '[data-testid="grouped-tab-bar"] [role="tab"]';

async function load(page, url) {
  await page.evaluate(() => localStorage.setItem("kazi-theme", "light"));
  const resp = await page.goto(url, { waitUntil: "load", timeout: 90000 });
  await page.locator('[data-testid="grouped-tab-bar"]').first().waitFor({ state: "visible", timeout: 60000 });
  await page.waitForTimeout(2500);
  await page.evaluate(() => {
    const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
    window.__rowScrollEvents = 0;
    vp.addEventListener("scroll", () => window.__rowScrollEvents++, { passive: true });
  });
  return resp ? resp.status() : null;
}

// Focused trigger vs the row viewport and the *visible* fades. Runs in the page.
function snap(page, el) {
  return page.evaluate((useActive) => {
    const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
    const vp = sticky.querySelector('[data-slot="scroll-area-viewport"]');
    const a = useActive ? document.activeElement : null;
    const v = vp.getBoundingClientRect();
    const fades = {};
    for (const k of ["start", "end"]) {
      const f = sticky.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`);
      const r = f.getBoundingClientRect();
      fades[k] = { visible: f.getAttribute("data-visible") === "true", opacity: +getComputedStyle(f).opacity, left: r.left, right: r.right };
    }
    const base = {
      scrollLeft: Math.round(vp.scrollLeft), maxScrollLeft: vp.scrollWidth - vp.clientWidth, vpLeft: Math.round(v.left), vpRight: Math.round(v.right),
      fadeStart: fades.start.visible, fadeEnd: fades.end.visible, scrollY: Math.round(scrollY), scrollX: Math.round(scrollX),
      maxScrollY: window.__maxScrollY, maxScrollX: window.__maxScrollX, rowScrollEvents: window.__rowScrollEvents,
    };
    if (!a || a.getAttribute("role") !== "tab") return { ...base, testid: a ? a.getAttribute("data-testid") || a.tagName : null };
    const b = a.getBoundingClientRect();
    const overlap = (f) => (f.visible || f.opacity > 0 ? Math.max(0, Math.min(b.right, f.right) - Math.max(b.left, f.left)) : 0);
    const cs = getComputedStyle(a);
    return {
      ...base,
      testid: a.getAttribute("data-testid"),
      left: Math.round(b.left * 10) / 10, right: Math.round(b.right * 10) / 10, top: Math.round(b.top), h: Math.round(b.height), w: Math.round(b.width),
      endGap: Math.round((v.right - b.right) * 10) / 10, // ≥ 32 → clear of end fade
      startGap: Math.round((b.left - v.left) * 10) / 10, // ≥ 32 → clear of start fade
      overlapStartFade: Math.round(overlap(fades.start) * 10) / 10,
      overlapEndFade: Math.round(overlap(fades.end) * 10) / 10,
      inViewport: b.left >= v.left - 0.5 && b.right <= v.right + 0.5 && b.top >= v.top - 0.5 && b.bottom <= v.bottom + 0.5,
      focusVisible: a.matches(":focus-visible"),
      boxShadow: cs.boxShadow,
    };
  }, el !== false);
}
const clear = (s) => s.inViewport && s.overlapStartFade === 0 && s.overlapEndFade === 0;

// Pixel check of the 2px inset ring: sample the middle of the left / right / top / bottom ring
// strips from a DPR-2 screenshot and compare to the --ring colour. Under a visible fade the strip
// blends towards the background and fails the match.
async function ringPixels(page, s) {
  const pad = 0;
  const clip = { x: s.left + pad, y: s.top, width: s.w, height: s.h };
  const buf = await page.screenshot({ clip });
  return page.evaluate(async (b64) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement("canvas");
    c.width = img.width;
    c.height = img.height;
    const x = c.getContext("2d", { willReadFrequently: true });
    x.drawImage(img, 0, 0);
    const ringC = document.createElement("canvas").getContext("2d");
    const d = document.createElement("div");
    d.style.color = "var(--ring)";
    document.body.appendChild(d);
    const ringCss = getComputedStyle(d).color;
    d.remove();
    ringC.fillStyle = "#ff00ff";
    ringC.fillStyle = ringCss;
    ringC.fillRect(0, 0, 1, 1);
    const ring = [...ringC.getImageData(0, 0, 1, 1).data.slice(0, 3)];
    const px = (X, Y) => [...x.getImageData(X, Y, 1, 1).data.slice(0, 3)];
    const W = img.width, H = img.height;
    const pts = { left: px(1, Math.floor(H / 2)), right: px(W - 2, Math.floor(H / 2)), top: px(Math.floor(W / 4), 1), topRight: px(W - 6, 1), bottom: px(Math.floor(W / 4), H - 2) };
    const dist = (p) => Math.max(...p.map((v, i) => Math.abs(v - ring[i])));
    const out = { ring, imgW: W };
    for (const [k, p] of Object.entries(pts)) out[k] = { rgb: p, d: dist(p) };
    out.allMatch = Object.keys(pts).every((k) => out[k].d <= 24);
    return out;
  }, buf.toString("base64"));
}

(async () => {
  const browser = await chromium.launch();
  const R = { capturedAt: new Date().toISOString(), head: "664a58068", pages: PAGES, keyboard390: {}, wide: [], mouse: [], deepLinks: [], console: null };
  const shots = [];
  const consoleAll = [];
  const wire = (page, who) => {
    page.on("console", (m) => {
      if (m.type() === "error" || m.type() === "warning") consoleAll.push({ who, type: m.type(), text: m.text().slice(0, 300), url: page.url().replace(BASE, "") });
    });
    page.on("pageerror", (e) => consoleAll.push({ who, type: "pageerror", text: String(e).slice(0, 300), url: page.url().replace(BASE, "") }));
  };
  const track = () => {
    window.__maxScrollY = 0;
    window.__maxScrollX = 0;
    window.addEventListener("scroll", () => { window.__maxScrollY = Math.max(window.__maxScrollY, window.scrollY); window.__maxScrollX = Math.max(window.__maxScrollX, window.scrollX); }, { passive: true });
  };
  const shoot = async (page, file, s, w) => {
    await page.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: Math.max(0, s.top - 30), width: w, height: s.h + 60 } });
    shots.push(file);
  };
  const press = async (page, key) => { await page.keyboard.press(key); await page.waitForTimeout(350); };

  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(track);
  const page = await ctx.newPage();
  wire(page, "alice");
  await login(page);

  // ---------- 1–5. keyboard at 390×844
  for (const [key, pg] of Object.entries(PAGES)) {
    await page.setViewportSize({ width: 390, height: 844 });
    const status = await load(page, pg.url);
    const ids = await page.locator(TABS).evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")));
    const n = ids.length;
    const K = { status, ids, initial: await snap(page, false), arrowRight: [], wrap: null, tabFwd: [], arrowLeft: [], shiftTab: [], arrowLeftWrap: null };
    // keyboard modality: focus() Details, Shift+Tab out, Tab back in
    await page.locator(TABS).first().focus();
    await press(page, "Shift+Tab");
    await press(page, "Tab");
    K.entry = await snap(page);
    const ev0 = K.entry.rowScrollEvents;
    for (let i = 1; i < n; i++) {
      await press(page, "ArrowRight");
      const s = await snap(page);
      s.clear = clear(s);
      if (i === 4) {
        s.ring = await ringPixels(page, s);
        await shoot(page, `kbd-${key}-390-arrow4-${s.testid}.png`, s, 390);
      }
      if (i === n - 1) {
        s.ring = await ringPixels(page, s);
        await shoot(page, `kbd-${key}-390-last-${s.testid}.png`, s, 390);
      }
      K.arrowRight.push(s);
    }
    K.detailsToOverviewScrollEvents = K.arrowRight[0].rowScrollEvents - ev0;
    // 5. wrap
    await press(page, "ArrowRight");
    K.wrap = await snap(page);
    K.wrap.clear = clear(K.wrap);
    // 2. Tab forward from Details (now at scrollLeft 0 after the wrap)
    for (let i = 1; i < n; i++) {
      await press(page, "Tab");
      const s = await snap(page);
      s.clear = clear(s);
      K.tabFwd.push(s);
    }
    // 3. start side: ArrowLeft from the last trigger back to Details
    for (let i = 1; i < n; i++) {
      await press(page, "ArrowLeft");
      const s = await snap(page);
      s.clear = clear(s);
      if (i === n - 3) { s.ring = await ringPixels(page, s); await shoot(page, `kbd-${key}-390-arrowleft-${s.testid}.png`, s, 390); }
      K.arrowLeft.push(s);
    }
    // ArrowLeft from Details wraps to the last trigger
    await press(page, "ArrowLeft");
    K.arrowLeftWrap = await snap(page);
    K.arrowLeftWrap.clear = clear(K.arrowLeftWrap);
    // 3. Shift+Tab from the last trigger back to Details
    for (let i = 1; i < n; i++) {
      await press(page, "Shift+Tab");
      const s = await snap(page);
      s.clear = clear(s);
      K.shiftTab.push(s);
    }
    R.keyboard390[key] = K;
    const fmt = (s) => `${s.testid}[${s.left}-${s.right} sl=${s.scrollLeft}/${s.maxScrollLeft} sg=${s.startGap} eg=${s.endGap} ov=${s.overlapStartFade}/${s.overlapEndFade} fv=${s.focusVisible} ${s.clear ? "CLEAR" : "NOT-CLEAR"} y=${s.scrollY} x=${s.scrollX}]`;
    console.log(`\n== ${key} 390 status=${status} n=${n} ids=${ids.join(",")} initial sl=${K.initial.scrollLeft} entry=${K.entry.testid} fv=${K.entry.focusVisible}`);
    console.log(` ArrowRight: ${K.arrowRight.map(fmt).join("\n   ")}`);
    console.log(` D→O scroll events: ${K.detailsToOverviewScrollEvents}; wrap: ${fmt(K.wrap)}`);
    console.log(` Tab: ${K.tabFwd.map(fmt).join("\n   ")}`);
    console.log(` ArrowLeft: ${K.arrowLeft.map(fmt).join("\n   ")}`);
    console.log(` ArrowLeft wrap: ${fmt(K.arrowLeftWrap)}`);
    console.log(` Shift+Tab: ${K.shiftTab.map(fmt).join("\n   ")}`);
    console.log(` rings: ${K.arrowRight.concat(K.arrowLeft).filter((s) => s.ring).map((s) => `${s.testid}:${s.ring.allMatch}(${["left", "right", "top", "topRight", "bottom"].map((k) => s.ring[k].d).join("/")})`).join(" ")}`);
  }

  // ---------- 7. deep links at 390
  for (const [key, pg] of Object.entries(PAGES)) {
    await load(page, `${pg.url}?tab=${pg.deepLink}`);
    const s = await page.evaluate(() => {
      const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
      const vp = sticky.querySelector('[data-slot="scroll-area-viewport"]');
      const a = sticky.querySelector('[role="tab"][aria-selected="true"]');
      const v = vp.getBoundingClientRect(), b = a.getBoundingClientRect();
      const f = (k) => sticky.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`).getAttribute("data-visible") === "true";
      const max = vp.scrollWidth - vp.clientWidth;
      return { testid: a.getAttribute("data-testid"), text: a.textContent.replace(/\s+/g, " ").trim(), left: Math.round(b.left), right: Math.round(b.right), vpLeft: Math.round(v.left), vpRight: Math.round(v.right), scrollLeft: Math.round(vp.scrollLeft), max,
        startGap: Math.round(b.left - v.left), endGap: Math.round(v.right - b.right), fadeStart: f("start"), fadeEnd: f("end"),
        clearOfFades: (b.left - v.left >= 32 - 0.5 || !f("start")) && (v.right - b.right >= 32 - 0.5 || !f("end")) && b.left >= v.left - 0.5 && b.right <= v.right + 0.5,
        scrollY: Math.round(scrollY), maxScrollY: window.__maxScrollY, tab: new URL(location.href).searchParams.get("tab") };
    });
    s.page = key;
    const file = `deeplink-${key}-390-${pg.deepLink}.png`;
    await page.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: 0, width: 390, height: 844 } });
    shots.push(file);
    s.file = file;
    R.deepLinks.push(s);
    console.log(`deeplink ${key} ${JSON.stringify(s)}`);
  }
  await ctx.close();

  // ---------- 6. mouse inside the end fade (DPR 1)
  const mctx = await browser.newContext({ viewport: { width: 768, height: 1024 } });
  await mctx.addInitScript(track);
  const mpage = await mctx.newPage();
  wire(mpage, "alice-mouse");
  await login(mpage);
  const installClickProbe = (p) =>
    p.evaluate(() => {
      const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
      window.__clk = [];
      const rec = (e) => {
        const t = e.target.closest && e.target.closest('[role="tab"]');
        window.__clk.push({ type: e.type, target: t ? t.getAttribute("data-testid") : e.target.tagName, scrollLeft: Math.round(vp.scrollLeft), activeFv: document.activeElement && document.activeElement.matches(":focus-visible") });
      };
      for (const t of ["pointerdown", "mousedown", "focusin", "mouseup", "click"]) document.addEventListener(t, rec, true);
    });
  const pickInFade = (p, wantPopup) =>
    p.evaluate((wantPopup) => {
      const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
      const vp = sticky.querySelector('[data-slot="scroll-area-viewport"]');
      const v = vp.getBoundingClientRect();
      const tabs = [...sticky.querySelectorAll('[role="tab"]')];
      const cands = tabs.filter((t) => { const b = t.getBoundingClientRect(); return b.left < v.right - 3 && b.right > v.right - 32 && (!!t.getAttribute("aria-haspopup")) === wantPopup; });
      if (!cands.length) return { none: true, tabs: tabs.map((t) => [t.getAttribute("data-testid"), Math.round(t.getBoundingClientRect().left), Math.round(t.getBoundingClientRect().right), t.getAttribute("aria-haspopup")]), vpRight: Math.round(v.right) };
      const t = cands[cands.length - 1]; // the most hidden one
      const b = t.getBoundingClientRect();
      const x = Math.min(v.right - 3, b.right - 3, Math.max(b.left + 4, v.right - 10));
      return { testid: t.getAttribute("data-testid"), left: Math.round(b.left), right: Math.round(b.right), vpRight: Math.round(v.right), x: Math.round(x), y: Math.round(b.top + b.height / 2), visiblePx: Math.round(Math.min(b.right, v.right) - b.left), fadeEnd: sticky.querySelector('[data-slot="grouped-tab-bar-fade-end"]').getAttribute("data-visible"), scrollLeft: Math.round(vp.scrollLeft), selected: t.getAttribute("aria-selected"), hitIsTrigger: document.elementFromPoint(Math.round(x), Math.round(b.top + b.height / 2))?.closest('[role="tab"]') === t };
    }, wantPopup);
  {
    const pg = PAGES.matter;
    await mpage.setViewportSize({ width: 768, height: 1024 });
    await load(mpage, pg.url);
    const tabBefore = new URL(mpage.url()).searchParams.get("tab");
    const pick = await pickInFade(mpage, false);
    await installClickProbe(mpage);
    if (!pick.none) {
      await mpage.mouse.click(pick.x, pick.y);
      await mpage.waitForTimeout(1500);
    }
    const after = await mpage.evaluate((id) => ({ clk: window.__clk, tab: new URL(location.href).searchParams.get("tab"), selected: id && document.querySelector(`[data-testid="${id}"]`).getAttribute("aria-selected"), scrollLeft: Math.round(document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]').scrollLeft) }), pick.testid || null);
    const m = { case: "768 matter single-tab mouse click inside end fade", pick, tabBefore, ...after, navigated: after.tab !== tabBefore && after.selected === "true", rowJumpedBeforeClick: after.clk.length ? new Set(after.clk.map((c) => c.scrollLeft)).size > 1 : null, clickTarget: (after.clk.find((c) => c.type === "click") || {}).target };
    const file = "mouse-matter-768-after-click.png";
    await mpage.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: Math.max(0, pick.y - 50), width: 768, height: 100 } });
    shots.push(file);
    m.file = file;
    R.mouse.push(m);
    console.log(`mouse ${JSON.stringify(m)}`);
    // Playwright locator.click() on the same trigger after a reload (supplementary)
    await load(mpage, pg.url);
    if (pick.testid) {
      await mpage.locator(`[data-testid="${pick.testid}"]`).click();
      await mpage.waitForTimeout(1500);
      const l = await mpage.evaluate((id) => ({ tab: new URL(location.href).searchParams.get("tab"), selected: document.querySelector(`[data-testid="${id}"]`).getAttribute("aria-selected") }), pick.testid);
      R.mouse.push({ case: "768 matter locator.click()", testid: pick.testid, ...l });
      console.log(`locator.click ${pick.testid} ${JSON.stringify(l)}`);
    }
  }
  for (const [key, pg] of Object.entries(PAGES)) {
    await mpage.setViewportSize({ width: 390, height: 844 });
    await load(mpage, pg.url);
    const pick = await pickInFade(mpage, true);
    await installClickProbe(mpage);
    let menu = null;
    if (!pick.none) {
      await mpage.mouse.click(pick.x, pick.y);
      await mpage.waitForTimeout(800);
      menu = await mpage.evaluate(() => { const m = document.querySelector('[role="menu"]'); if (!m) return { open: false }; const b = m.getBoundingClientRect(); return { open: true, items: [...m.querySelectorAll('[role="menuitem"]')].map((i) => i.getAttribute("data-testid")), rect: [Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom)] }; });
    }
    const clk = await mpage.evaluate(() => window.__clk);
    const m = { case: `390 ${key} dropdown mouse click inside end fade`, pick, menu, clk, rowJumpedBeforeOpen: clk.length ? new Set(clk.filter((c) => c.type === "pointerdown" || c.type === "mousedown").map((c) => c.scrollLeft)).size > 1 || clk[0].scrollLeft !== pick.scrollLeft : null };
    if (key === "matter") {
      const file = "mouse-matter-390-dropdown-open.png";
      const top = menu && menu.open ? Math.min(menu.rect[1], pick.y - 30) - 10 : pick.y - 40;
      await mpage.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: Math.max(0, top), width: 390, height: Math.max(menu && menu.open ? menu.rect[3] : 0, pick.y + 30) - Math.max(0, top) + 10 } });
      shots.push(file);
      m.file = file;
    }
    await mpage.keyboard.press("Escape");
    await mpage.waitForTimeout(500);
    m.afterEscape = await snap(mpage);
    R.mouse.push(m);
    console.log(`mouse ${JSON.stringify({ ...m, afterEscape: { testid: m.afterEscape.testid, fv: m.afterEscape.focusVisible, sl: m.afterEscape.scrollLeft, ov: [m.afterEscape.overlapStartFade, m.afterEscape.overlapEndFade] } })}`);
  }
  await mctx.close();

  // ---------- 8. wide layouts 1440 / 1024 light
  const wctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await wctx.addInitScript(track);
  const wpage = await wctx.newPage();
  wire(wpage, "alice-wide");
  await login(wpage);
  for (const [w, h] of [[1440, 900], [1024, 768]]) {
    for (const [key, pg] of Object.entries(PAGES)) {
      await wpage.setViewportSize({ width: w, height: h });
      await load(wpage, pg.url);
      const facts = await wpage.evaluate(() => {
        const lists = [...document.querySelectorAll('[role="tablist"]')];
        const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
        const act = document.querySelector('[data-testid="grouped-tab-bar"] [role="tab"][aria-selected="true"]');
        const af = act && getComputedStyle(act, "::after");
        return { tablists: lists.length, visibleTablists: lists.filter((l) => l.getClientRects().length).length, row: `${vp.scrollWidth}/${vp.clientWidth}`, overflows: vp.scrollWidth > vp.clientWidth, active: act && act.getAttribute("data-testid"), underline: af && { opacity: af.opacity, height: af.height } };
      });
      const n = await wpage.locator(TABS).count();
      await wpage.locator(TABS).first().focus();
      await press(wpage, "Shift+Tab");
      await press(wpage, "Tab");
      const seq = [await snap(wpage)];
      for (let i = 1; i <= n; i++) { await press(wpage, "ArrowRight"); seq.push(await snap(wpage)); }
      for (let i = 1; i < n; i++) { await press(wpage, "Tab"); seq.push(await snap(wpage)); }
      for (let i = 1; i < n; i++) { await press(wpage, "Shift+Tab"); seq.push(await snap(wpage)); }
      // ring visible on the third trigger
      await wpage.locator(TABS).first().focus();
      await press(wpage, "Shift+Tab");
      await press(wpage, "Tab");
      await press(wpage, "ArrowRight");
      await press(wpage, "ArrowRight");
      const cur = await snap(wpage);
      const ring = await ringPixels(wpage, cur);
      let file = null;
      if ((w === 1024 && key === "matter") || (w === 1440 && key === "client")) {
        file = `kbd-${key}-${w}-ring-${cur.testid}.png`;
        await wpage.screenshot({ path: path.join(OUT, file), clip: { x: Math.max(0, cur.left - 200), y: Math.max(0, cur.top - 30), width: 600, height: cur.h + 60 } });
        shots.push(file);
      }
      const e = { page: key, viewport: `${w}x${h}`, ...facts, n, scrollLefts: [...new Set(seq.map((s) => s.scrollLeft))], rowScrollEvents: seq[seq.length - 1].rowScrollEvents, allInBar: seq.every((s) => /^tab-group-/.test(s.testid || "")), allFocusVisible: seq.every((s) => s.focusVisible), maxScrollY: seq[seq.length - 1].maxScrollY, maxScrollX: seq[seq.length - 1].maxScrollX, ringTrigger: cur.testid, boxShadow: cur.boxShadow, ring, file };
      R.wide.push(e);
      console.log(`wide ${key} ${w}: ${JSON.stringify({ ...e, ring: { allMatch: ring.allMatch, d: ["left", "right", "top", "topRight", "bottom"].map((k) => ring[k].d) } })}`);
    }
  }
  await wctx.close();

  R.console = { errors: consoleAll.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleAll.filter((c) => c.type === "warning").length, hydration: consoleAll.filter((c) => /hydrat/i.test(c.text)).length, items: consoleAll.slice(0, 20) };
  R.screenshots = shots;
  fs.writeFileSync(path.join(OUT, "obs-after-fix.json"), JSON.stringify(R));
  console.log(`console total: ${JSON.stringify(R.console)}`);
  console.log(`screenshots: ${shots.length} ${shots.join(", ")}`);
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
