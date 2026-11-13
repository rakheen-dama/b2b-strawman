// 642C re-observation round 2 after ad4f84ba4: (a) a viewport WIDTH change scrolls the active
// trigger clear of the 32px edge fades (same-width relayouts / user scrolls never move the row);
// (b) scrollTriggerClearOfFades measures with getBoundingClientRect (floor start / ceil end).
// Read-only: drives the e2e mock-auth stack (:3001, built from ad4f84ba4) as Alice with the
// Playwright library. Run: node obs-after-fix2.cjs (nvm node v20). Adapted from ../after-fix/obs-after-fix.cjs.
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
const EPS = 0.01;
// Fresh stack ids (lifecycle run mwe6c12fa5b4), found in tenant_7d218705360b.projects/customers.
const PAGES = {
  matter: { url: `${BASE}/org/${SLUG}/matters/504406d9-0fbd-4891-9813-5ed1ca24518a`, name: "Ndlovu v RAF mwe6c12fa5b4" },
  client: { url: `${BASE}/org/${SLUG}/clients/375c87f2-f266-490c-9492-4759cb6412c7`, name: "Sipho Ndlovu mwe6c12fa5b4" },
};
const TABS = '[data-testid="grouped-tab-bar"] [role="tab"]';

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", "user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2000);
}

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

// Row + a trigger (focused element, or the aria-selected one) vs the row viewport and fades.
function snap(page, which = "focus") {
  return page.evaluate((which) => {
    const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
    const vp = sticky.querySelector('[data-slot="scroll-area-viewport"]');
    const a = which === "focus" ? document.activeElement : sticky.querySelector('[role="tab"][aria-selected="true"]');
    const v = vp.getBoundingClientRect();
    const fades = {};
    for (const k of ["start", "end"]) {
      const f = sticky.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`);
      const r = f.getBoundingClientRect();
      fades[k] = { visible: f.getAttribute("data-visible") === "true", left: r.left, right: r.right };
    }
    const max = vp.scrollWidth - vp.clientWidth;
    const base = {
      scrollLeft: vp.scrollLeft, maxScrollLeft: max, overflows: max > 0, vpLeft: +v.left.toFixed(2), vpRight: +v.right.toFixed(2), vpWidth: +v.width.toFixed(2),
      fadeStart: fades.start.visible, fadeEnd: fades.end.visible, scrollY: window.scrollY, scrollX: window.scrollX,
      maxScrollY: window.__maxScrollY, rowScrollEvents: window.__rowScrollEvents, winW: innerWidth,
    };
    if (!a || a.getAttribute("role") !== "tab") return { ...base, testid: a ? a.getAttribute("data-testid") || a.tagName : null };
    const b = a.getBoundingClientRect();
    const overlap = (f) => (f.visible ? Math.max(0, Math.min(b.right, f.right) - Math.max(b.left, f.left)) : 0);
    return {
      ...base,
      testid: a.getAttribute("data-testid"), text: a.textContent.replace(/\s+/g, " ").trim(),
      left: b.left, right: b.right, top: b.top, h: b.height, w: b.width,
      endGap: +(v.right - b.right).toFixed(3),
      startGap: +(b.left - v.left).toFixed(3),
      overlapStartFade: +overlap(fades.start).toFixed(3),
      overlapEndFade: +overlap(fades.end).toFixed(3),
      inViewport: b.left >= v.left - 0.5 && b.right <= v.right + 0.5,
      focusVisible: a.matches(":focus-visible"),
    };
  }, which);
}
// Strict clearance: each side ≥ 32.0px, or that fade is hidden (row at 0 / max).
const okEnd = (s) => s.endGap >= FADE - EPS || !s.fadeEnd;
const okStart = (s) => s.startGap >= FADE - EPS || !s.fadeStart;
const clear = (s) => s.inViewport && okEnd(s) && okStart(s);

async function settle(page) {
  let last = null, same = 0;
  for (let i = 0; i < 40 && same < 6; i++) {
    await page.waitForTimeout(100);
    const sl = await page.evaluate(() => document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]').scrollLeft);
    if (sl === last) same++; else { same = 0; last = sl; }
  }
  await page.waitForTimeout(300);
}

// Outermost fully-covered device pixels of the 2px inset ring (DPR 2), left and right, mid-height,
// plus one pixel further in. A visible fade over the ring would tint these towards the background.
async function edgeRing(page, s) {
  const dpr = 2;
  const clipY = Math.max(0, Math.floor(s.top) - 2);
  const clip = { x: 0, y: clipY, width: s.winW, height: Math.ceil(s.h) + 4 };
  const buf = await page.screenshot({ clip });
  return page.evaluate(async ({ b64, s, clipY, dpr }) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement("canvas");
    c.width = img.width; c.height = img.height;
    const x = c.getContext("2d", { willReadFrequently: true });
    x.drawImage(img, 0, 0);
    const d = document.createElement("div");
    d.style.color = "var(--ring)";
    document.body.appendChild(d);
    const ringCss = getComputedStyle(d).color;
    d.remove();
    const rc = document.createElement("canvas").getContext("2d");
    rc.fillStyle = ringCss; rc.fillRect(0, 0, 1, 1);
    const ring = [...rc.getImageData(0, 0, 1, 1).data.slice(0, 3)];
    const px = (X, Y) => [...x.getImageData(X, Y, 1, 1).data.slice(0, 3)];
    const Y = Math.round((s.top + s.h / 2 - clipY) * dpr);
    const L = Math.ceil(s.left * dpr), R = Math.floor(s.right * dpr) - 1;
    const dist = (p) => Math.max(...p.map((v, i) => Math.abs(v - ring[i])));
    const out = { ring, L, R };
    for (const [k, X] of Object.entries({ leftOuter: L, leftInner: L + 1, rightOuter: R, rightInner: R - 1 })) { const p = px(X, Y); out[k] = { rgb: p, d: dist(p) }; }
    out.maxD = Math.max(out.leftOuter.d, out.leftInner.d, out.rightOuter.d, out.rightInner.d);
    return out;
  }, { b64: buf.toString("base64"), s, clipY, dpr });
}

(async () => {
  const browser = await chromium.launch();
  const R = { capturedAt: new Date().toISOString(), head: "ad4f84ba4", pages: PAGES, resize: [], noFight: [], keyboard390: {}, deepLink: null, mouse768: null, wide1440: [], console: null };
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
    window.addEventListener("scroll", () => { window.__maxScrollY = Math.max(window.__maxScrollY, window.scrollY); }, { passive: true });
  };
  const rowShot = async (page, file, s) => {
    await page.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: Math.max(0, s.top - 40), width: s.winW, height: s.h + 80 } });
    shots.push(file);
  };
  const press = async (page, key) => { await page.keyboard.press(key); await page.waitForTimeout(350); };
  const VP = '[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]';
  const getSL = (page) => page.evaluate((sel) => document.querySelector(sel).scrollLeft, VP);
  const setSL0 = (page) => page.evaluate((sel) => { document.querySelector(sel).scrollLeft = 0; }, VP);
  const f2 = (n) => (typeof n === "number" ? +n.toFixed(2) : n);
  const fmt = (s) => `${s.testid}[${f2(s.left)}-${f2(s.right)} sl=${f2(s.scrollLeft)}/${s.maxScrollLeft} sg=${f2(s.startGap)} eg=${f2(s.endGap)} fades=${+s.fadeStart}${+s.fadeEnd} fv=${s.focusVisible} ${clear(s) ? "CLEAR" : "NOT-CLEAR"} y=${s.scrollY}]`;

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(track);
  const page = await ctx.newPage();
  wire(page, "alice");
  await login(page);

  // ---------- 1 + 2. resize 1440 → 390 / 768 with ?tab=audit; then no-fighting at 390
  for (const [key, pg] of Object.entries(PAGES)) {
    for (const [w, h] of [[390, 844], [768, 1024]]) {
      await page.setViewportSize({ width: 1440, height: 900 });
      await load(page, `${pg.url}?tab=audit`);
      const before = await snap(page, "active");
      await page.evaluate(() => { window.__maxScrollY = 0; window.__rowScrollEvents = 0; });
      await page.setViewportSize({ width: w, height: h });
      await settle(page);
      const after = await snap(page, "active");
      const e = {
        page: key, from: "1440x900", to: `${w}x${h}`, before: { overflows: before.overflows, scrollLeft: before.scrollLeft, scrollY: before.scrollY, active: before.testid, text: before.text },
        after,
        pass: !before.overflows && before.scrollLeft === 0 && after.overflows && after.scrollLeft > 0 && after.right <= after.vpRight - FADE + EPS && after.fadeStart && after.fadeEnd === (after.scrollLeft < after.maxScrollLeft - 1) && after.scrollY === before.scrollY && after.maxScrollY === 0 && clear(after),
      };
      if (w === 390 || key === "matter") { const file = `resize-${key}-1440to${w}-audit.png`; await rowShot(page, file, after); e.file = file; }
      R.resize.push(e);
      console.log(`resize ${key} 1440→${w}: before overflow=${before.overflows} sl=${before.scrollLeft} y=${before.scrollY} active=${before.testid}(${before.text}) | after ${fmt(after)} maxY=${after.maxScrollY} rowScrollEvents=${after.rowScrollEvents} PASS=${e.pass}`);

      if (w !== 390) continue;
      // 2. no fighting the user at 390: scroll the row back to 0 by hand, then same-width relayouts.
      const nf = { page: key, steps: [] };
      const box = await page.locator('[data-slot="grouped-tab-bar-sticky"]').boundingBox();
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 - 4);
      await page.mouse.wheel(-2000, 0);
      await page.waitForTimeout(800);
      let sl = await getSL(page);
      nf.afterWheel = sl;
      if (sl !== 0) { await setSL0(page); await page.waitForTimeout(400); sl = await getSL(page); nf.setDirect = true; }
      const rec = async (label) => { await page.waitForTimeout(700); const s = await snap(page, "active"); nf.steps.push({ label, scrollLeft: s.scrollLeft, fadeStart: s.fadeStart, fadeEnd: s.fadeEnd, scrollY: s.scrollY, winW: s.winW }); return s; };
      await rec("baseline after user scroll to 0");
      // (a) header user menu → theme Dark → Light → close
      const um = page.getByRole("button", { name: "User menu" });
      nf.userMenuVisible = await um.isVisible().catch(() => false);
      if (nf.userMenuVisible) {
        await um.click();
        await rec("user menu open");
        const dark = page.getByRole("radio", { name: "Dark" }).or(page.getByRole("button", { name: "Dark" })).first();
        if (await dark.isVisible().catch(() => false)) {
          await dark.click();
          await rec("theme → dark");
          nf.htmlClassDark = await page.evaluate(() => document.documentElement.classList.contains("dark"));
          await page.getByRole("radio", { name: "Light" }).or(page.getByRole("button", { name: "Light" })).first().click();
          await rec("theme → light");
        } else nf.themeToggleMissing = true;
        await page.keyboard.press("Escape");
        await rec("user menu closed (Escape)");
        if (await page.locator('[role="menu"]').isVisible().catch(() => false)) { await page.mouse.click(5, 800); await rec("user menu closed (outside click)"); }
      }
      // (b) tab-bar dropdown (Work, fully visible at scrollLeft 0): mouse open, Escape close, open, outside-click close
      const work = page.locator('[data-testid="tab-group-work"]');
      await work.click();
      const s1 = await rec("tab dropdown Work open (mouse)");
      nf.workMenuOpen = await page.locator('[role="menu"]').isVisible().catch(() => false);
      await page.keyboard.press("Escape");
      await rec("tab dropdown Work closed (Escape → focus-visible on Work)");
      await work.click();
      await rec("tab dropdown Work re-open (mouse)");
      await page.mouse.click(200, 800);
      await rec("tab dropdown Work closed (outside click)");
      // (c) height-only viewport change (same width) and a forced reflow of the row
      await page.setViewportSize({ width: 390, height: 700 });
      await rec("viewport height 844 → 700 (same width)");
      await page.setViewportSize({ width: 390, height: 844 });
      await rec("viewport height 700 → 844 (same width)");
      await page.evaluate(() => { const r = document.querySelector('[data-testid="grouped-tab-bar"]'); r.style.paddingRight = "1px"; r.offsetWidth; });
      await rec("row width +1px (same viewport width)");
      await page.evaluate(() => { document.querySelector('[data-testid="grouped-tab-bar"]').style.paddingRight = ""; });
      await rec("row width restored");
      nf.pass = nf.steps.every((s) => s.scrollLeft === 0);
      const file = `nofight-${key}-390-after-relayouts.png`;
      await rowShot(page, file, { ...s1, top: s1.top, h: s1.h, winW: 390 });
      nf.file = file;
      R.noFight.push(nf);
      console.log(`nofight ${key}: wheel→${nf.afterWheel}${nf.setDirect ? " (then set 0 directly)" : ""} userMenu=${nf.userMenuVisible} dark=${nf.htmlClassDark} workMenu=${nf.workMenuOpen}\n   ${nf.steps.map((s) => `${s.label}: sl=${s.scrollLeft} fades=${+s.fadeStart}${+s.fadeEnd} y=${s.scrollY}`).join("\n   ")}\n   PASS=${nf.pass}`);
    }
  }

  // ---------- 3. keyboard probe at 390 (sub-pixel clearance + edge ring pixels)
  await page.setViewportSize({ width: 390, height: 844 });
  for (const [key, pg] of Object.entries(PAGES)) {
    await load(page, pg.url);
    const ids = await page.locator(TABS).evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")));
    const n = ids.length;
    const K = { ids, initial: await snap(page, "active"), steps: [] };
    await page.locator(TABS).first().focus();
    await press(page, "Shift+Tab");
    await press(page, "Tab");
    K.entry = await snap(page);
    const step = async (dir, i) => {
      await press(page, dir);
      const s = await snap(page);
      s.dir = dir; s.clear = clear(s); s.okEnd = okEnd(s); s.okStart = okStart(s);
      s.ring = await edgeRing(page, s);
      K.steps.push(s);
      return s;
    };
    for (let i = 1; i < n; i++) {
      const s = await step("ArrowRight", i);
      if ((key === "matter" && s.testid === "tab-group-finance") || (key === "client" && s.testid === "tab-group-compliance")) await rowShot(page, `kbd-${key}-390-${s.testid}.png`, s);
    }
    for (let i = 1; i < n; i++) await step("ArrowLeft", i);
    const endSide = K.steps.filter((s) => s.fadeEnd);
    const startSide = K.steps.filter((s) => s.fadeStart);
    K.minEndGap = endSide.length ? Math.min(...endSide.map((s) => s.endGap)) : null;
    K.minEndGapAt = endSide.length ? endSide.reduce((a, b) => (b.endGap < a.endGap ? b : a)).testid : null;
    K.minStartGap = startSide.length ? Math.min(...startSide.map((s) => s.startGap)) : null;
    K.minStartGapAt = startSide.length ? startSide.reduce((a, b) => (b.startGap < a.startGap ? b : a)).testid : null;
    K.maxRingD = Math.max(...K.steps.map((s) => s.ring.maxD));
    K.allClear = K.steps.every((s) => s.clear);
    K.maxScrollY = Math.max(...K.steps.map((s) => s.scrollY));
    K.pass = K.allClear && K.maxRingD <= 3 && K.maxScrollY === 0;
    R.keyboard390[key] = K;
    console.log(`\n== kbd ${key} 390 n=${n} entry=${K.entry.testid} fv=${K.entry.focusVisible}`);
    console.log("  " + K.steps.map((s) => `${s.dir} ${fmt(s)} ring(L/R outer)=${s.ring.leftOuter.d}/${s.ring.rightOuter.d} max=${s.ring.maxD}`).join("\n  "));
    console.log(`  minEndGap=${K.minEndGap} @${K.minEndGapAt} minStartGap=${K.minStartGap} @${K.minStartGapAt} maxRingD=${K.maxRingD} allClear=${K.allClear} maxScrollY=${K.maxScrollY} PASS=${K.pass}`);
  }

  // ---------- 4a. deep link ?tab=disbursements at 390 (fresh navigation; scrollY tracked from document start)
  {
    await load(page, `${PAGES.matter.url}?tab=disbursements`);
    const s = await snap(page, "active");
    s.clear = clear(s);
    s.tab = new URL(page.url()).searchParams.get("tab");
    s.pass = s.clear && s.scrollY === 0 && s.maxScrollY === 0 && s.inViewport;
    await page.screenshot({ path: path.join(OUT, "deeplink-matter-390-disbursements.png"), clip: { x: 0, y: 0, width: 390, height: 844 } });
    shots.push("deeplink-matter-390-disbursements.png");
    R.deepLink = s;
    console.log(`deeplink matter 390 ?tab=${s.tab}: ${fmt(s)} text="${s.text}" maxScrollY=${s.maxScrollY} PASS=${s.pass}`);
  }
  // ---------- 5 (part). 1440 wide: one tablist, underline, focus never changes scrollLeft
  await page.setViewportSize({ width: 1440, height: 900 });
  for (const [key, pg] of Object.entries(PAGES)) {
    await load(page, pg.url);
    const facts = await page.evaluate(() => {
      const lists = [...document.querySelectorAll('[role="tablist"]')];
      const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
      const act = document.querySelector('[data-testid="grouped-tab-bar"] [role="tab"][aria-selected="true"]');
      const af = act && getComputedStyle(act, "::after");
      return { tablists: lists.length, visibleTablists: lists.filter((l) => l.getClientRects().length).length, row: `${vp.scrollWidth}/${vp.clientWidth}`, overflows: vp.scrollWidth > vp.clientWidth, active: act && act.getAttribute("data-testid"), underline: af && { opacity: af.opacity, height: af.height } };
    });
    const n = await page.locator(TABS).count();
    await page.locator(TABS).first().focus();
    await press(page, "Shift+Tab");
    await press(page, "Tab");
    const seq = [await snap(page)];
    for (let i = 1; i <= n; i++) { await press(page, "ArrowRight"); seq.push(await snap(page)); }
    for (let i = 1; i < n; i++) { await press(page, "Tab"); seq.push(await snap(page)); }
    for (let i = 1; i < n; i++) { await press(page, "Shift+Tab"); seq.push(await snap(page)); }
    const e = { page: key, ...facts, n, scrollLefts: [...new Set(seq.map((s) => s.scrollLeft))], rowScrollEvents: seq[seq.length - 1].rowScrollEvents, allInBar: seq.every((s) => /^tab-group-/.test(s.testid || "")), allFocusVisible: seq.every((s) => s.focusVisible), maxScrollY: Math.max(...seq.map((s) => s.scrollY)) };
    e.pass = e.visibleTablists === 1 && !e.overflows && e.underline && e.underline.opacity === "1" && e.scrollLefts.length === 1 && e.scrollLefts[0] === 0 && e.rowScrollEvents === 0 && e.allInBar && e.maxScrollY === 0;
    if (key === "matter") { const file = "wide-matter-1440-focus.png"; await rowShot(page, file, seq[3]); e.file = file; }
    R.wide1440.push(e);
    console.log(`wide1440 ${key}: ${JSON.stringify(e)}`);
  }
  await ctx.close();

  // ---------- 4b. mouse click 10px inside the end fade at 768 (DPR 1)
  {
    const mctx = await browser.newContext({ viewport: { width: 768, height: 1024 } });
    await mctx.addInitScript(track);
    const mpage = await mctx.newPage();
    wire(mpage, "alice-mouse");
    await login(mpage);
    await load(mpage, PAGES.matter.url);
    const tabBefore = new URL(mpage.url()).searchParams.get("tab");
    const pick = await mpage.evaluate(() => {
      const sticky = document.querySelector('[data-slot="grouped-tab-bar-sticky"]');
      const vp = sticky.querySelector('[data-slot="scroll-area-viewport"]');
      const v = vp.getBoundingClientRect();
      const tabs = [...sticky.querySelectorAll('[role="tab"]')];
      const cands = tabs.filter((t) => { const b = t.getBoundingClientRect(); return b.left < v.right - 3 && b.right > v.right - 32 && !t.getAttribute("aria-haspopup"); });
      if (!cands.length) return { none: true, tabs: tabs.map((t) => [t.getAttribute("data-testid"), Math.round(t.getBoundingClientRect().left), Math.round(t.getBoundingClientRect().right)]), vpRight: v.right };
      const t = cands[cands.length - 1];
      const b = t.getBoundingClientRect();
      const x = Math.round(v.right - 10), y = Math.round(b.top + b.height / 2);
      return { testid: t.getAttribute("data-testid"), left: b.left, right: b.right, vpRight: v.right, x, y, fadeEnd: sticky.querySelector('[data-slot="grouped-tab-bar-fade-end"]').getAttribute("data-visible"), scrollLeft: vp.scrollLeft, hitIsTrigger: document.elementFromPoint(x, y)?.closest('[role="tab"]') === t };
    });
    await mpage.evaluate(() => {
      const vp = document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]');
      window.__clk = [];
      const rec = (e) => { const t = e.target.closest && e.target.closest('[role="tab"]'); window.__clk.push({ type: e.type, target: t ? t.getAttribute("data-testid") : e.target.tagName, scrollLeft: vp.scrollLeft }); };
      for (const t of ["pointerdown", "mousedown", "focusin", "mouseup", "click"]) document.addEventListener(t, rec, true);
    });
    if (!pick.none) { await mpage.mouse.click(pick.x, pick.y); await mpage.waitForTimeout(1500); }
    const after = await mpage.evaluate((id) => ({ clk: window.__clk, tab: new URL(location.href).searchParams.get("tab"), selected: id && document.querySelector(`[data-testid="${id}"]`).getAttribute("aria-selected"), scrollLeft: document.querySelector('[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]').scrollLeft, scrollY }), pick.testid || null);
    const m = { pick, tabBefore, ...after, navigated: after.tab !== tabBefore && after.selected === "true", rowJumpedDuringClick: new Set(after.clk.map((c) => c.scrollLeft)).size > 1, clickTarget: (after.clk.find((c) => c.type === "click") || {}).target };
    m.pass = !pick.none && pick.hitIsTrigger && m.navigated && !m.rowJumpedDuringClick && m.clickTarget === pick.testid;
    await mpage.screenshot({ path: path.join(OUT, "mouse-matter-768-after-click.png"), clip: { x: 0, y: Math.max(0, (pick.y || 100) - 50), width: 768, height: 100 } });
    shots.push("mouse-matter-768-after-click.png");
    R.mouse768 = m;
    console.log(`mouse768 ${JSON.stringify(m)}`);
    await mctx.close();
  }

  const onTabPages = consoleAll.filter((c) => /\/(matters|clients)\//.test(c.url));
  R.console = {
    all: { errors: consoleAll.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleAll.filter((c) => c.type === "warning").length, hydration: consoleAll.filter((c) => /hydrat/i.test(c.text)).length },
    tabPages: { errors: onTabPages.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: onTabPages.filter((c) => c.type === "warning").length, hydration: onTabPages.filter((c) => /hydrat/i.test(c.text)).length },
    items: consoleAll.slice(0, 20),
  };
  R.screenshots = shots;
  // compact: drop ring rgb detail except maxD
  for (const K of Object.values(R.keyboard390)) for (const s of K.steps) s.ring = { maxD: s.ring.maxD, lo: s.ring.leftOuter.rgb, ro: s.ring.rightOuter.rgb, ring: s.ring.ring };
  fs.writeFileSync(path.join(OUT, "obs-after-fix2.json"), JSON.stringify(R));
  console.log(`console: ${JSON.stringify(R.console)}`);
  console.log(`screenshots: ${shots.length} ${shots.join(", ")}`);
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
