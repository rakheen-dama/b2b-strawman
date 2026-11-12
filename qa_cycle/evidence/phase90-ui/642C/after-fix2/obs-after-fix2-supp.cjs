// 642C round 2 supplement: (A) resize 1440 → 390 / 768 with a NON-last group active, so the end
// fade stays visible and the strict ≥ 32px end gap is measurable; (B) instrumented repro of the
// client no-fight step that moved the row to 16 after an outside click closed the Work dropdown;
// (C) a real theme toggle (mock header avatar menu → Dark → Light) at 390 after a user scroll to 0.
const path = require("path");
const fs = require("fs");
const PW_PATH = process.env.PW_PATH || require.resolve("@playwright/test", { paths: [path.resolve(__dirname, "../../../../../frontend")] });
const { chromium } = require(PW_PATH);
const BASE = "http://localhost:3001";
const OUT = __dirname;
const M = `${BASE}/org/e2e-test-org/matters/504406d9-0fbd-4891-9813-5ed1ca24518a`;
const C = `${BASE}/org/e2e-test-org/clients/375c87f2-f266-490c-9492-4759cb6412c7`;
const VP = '[data-slot="grouped-tab-bar-sticky"] [data-slot="scroll-area-viewport"]';

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", "user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/dashboard/, { timeout: 60000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2000);
}
async function load(page, url) {
  await page.evaluate(() => localStorage.setItem("kazi-theme", "light"));
  await page.goto(url, { waitUntil: "load", timeout: 90000 });
  await page.locator('[data-testid="grouped-tab-bar"]').first().waitFor({ state: "visible", timeout: 60000 });
  await page.waitForTimeout(2500);
}
// Log every scrollLeft write (with stack) and every scroll event (with focus state) on the row.
const instrument = (page) =>
  page.evaluate((sel) => {
    const vp = document.querySelector(sel);
    window.__log = [];
    const d = Object.getOwnPropertyDescriptor(Element.prototype, "scrollLeft");
    Object.defineProperty(vp, "scrollLeft", {
      configurable: true,
      get() { return d.get.call(this); },
      set(v) { window.__log.push({ kind: "write", v, stack: new Error().stack.split("\n").slice(2, 6).join(" | ").slice(0, 400) }); d.set.call(this, v); },
    });
    vp.addEventListener("scroll", () => { const a = document.activeElement; window.__log.push({ kind: "scroll", sl: d.get.call(vp), active: a && (a.getAttribute("data-testid") || a.tagName), fv: a && a.matches(":focus-visible") }); }, { passive: true });
    document.addEventListener("focusin", (e) => window.__log.push({ kind: "focusin", target: e.target.getAttribute("data-testid") || e.target.tagName + "." + (e.target.className || "").toString().slice(0, 40), fv: e.target.matches(":focus-visible"), sl: d.get.call(vp) }), true);
    document.addEventListener("pointerdown", (e) => window.__log.push({ kind: "pointerdown", target: e.target.tagName + "." + (e.target.className || "").toString().slice(0, 60), x: e.clientX, y: e.clientY }), true);
  }, VP);
const snap = (page) =>
  page.evaluate((sel) => {
    const vp = document.querySelector(sel);
    const sticky = vp.closest('[data-slot="grouped-tab-bar-sticky"]');
    const a = sticky.querySelector('[role="tab"][aria-selected="true"]');
    const v = vp.getBoundingClientRect(), b = a.getBoundingClientRect();
    const f = (k) => sticky.querySelector(`[data-slot="grouped-tab-bar-fade-${k}"]`).getAttribute("data-visible") === "true";
    return { active: a.getAttribute("data-testid"), text: a.textContent.replace(/\s+/g, " ").trim(), left: +b.left.toFixed(2), right: +b.right.toFixed(2), vpLeft: v.left, vpRight: v.right, scrollLeft: vp.scrollLeft, max: vp.scrollWidth - vp.clientWidth, endGap: +(v.right - b.right).toFixed(3), startGap: +(b.left - v.left).toFixed(3), fadeStart: f("start"), fadeEnd: f("end"), scrollY: window.scrollY, active_el: document.activeElement && (document.activeElement.getAttribute("data-testid") || document.activeElement.tagName) };
  }, VP);
async function settle(page) {
  let last = null, same = 0;
  for (let i = 0; i < 40 && same < 6; i++) { await page.waitForTimeout(100); const sl = await page.evaluate((s) => document.querySelector(s).scrollLeft, VP); if (sl === last) same++; else { same = 0; last = sl; } }
  await page.waitForTimeout(300);
}

(async () => {
  const browser = await chromium.launch();
  const R = { capturedAt: new Date().toISOString(), resizeNonLast: [], outsideClick: [], theme: [] };
  const consoleAll = [];
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning") consoleAll.push({ type: m.type(), text: m.text().slice(0, 200), url: page.url().replace(BASE, "") }); });
  page.on("pageerror", (e) => consoleAll.push({ type: "pageerror", text: String(e).slice(0, 200) }));
  await login(page);

  // A. non-last active group
  for (const [key, url, tab] of [["matter", M, "court-dates"], ["matter", M, "disbursements"], ["client", C, "onboarding"]]) {
    for (const w of [390, 768]) {
      await page.setViewportSize({ width: 1440, height: 900 });
      await load(page, `${url}?tab=${tab}`);
      const before = await snap(page);
      await page.setViewportSize({ width: w, height: w === 390 ? 844 : 1024 });
      await settle(page);
      const after = await snap(page);
      const overflow = after.max > 0;
      const strictEnd = !after.fadeEnd || after.endGap >= 32 - 0.01;
      const strictStart = !after.fadeStart || after.startGap >= 32 - 0.01;
      const e = { page: key, tab, to: w, before: { max: before.max, sl: before.scrollLeft, y: before.scrollY }, after, pass: before.max === 0 && before.scrollLeft === 0 && (!overflow || after.scrollLeft >= 0) && strictEnd && strictStart && after.left >= after.vpLeft - 0.5 && after.right <= after.vpRight + 0.5 && after.scrollY === before.scrollY };
      if (w === 390 && tab !== "disbursements") { const file = `resize-${key}-1440to390-${tab}.png`; await page.screenshot({ path: path.join(OUT, file), clip: { x: 0, y: 0, width: 390, height: 360 } }); e.file = file; }
      R.resizeNonLast.push(e);
      console.log(`resizeNonLast ${key} ?tab=${tab} 1440→${w}: before max=${before.max} sl=${before.scrollLeft} | after ${after.active}("${after.text}") [${after.left}-${after.right}] vp ${after.vpLeft}-${after.vpRight} sl=${after.scrollLeft}/${after.max} sg=${after.startGap} eg=${after.endGap} fades=${+after.fadeStart}${+after.fadeEnd} y=${after.scrollY} PASS=${e.pass}`);
    }
  }

  // B. instrumented repro of the no-fight outside-click step (both pages, two click points)
  for (const [key, url] of [["client", C], ["matter", M]]) {
    for (const pt of [[200, 800], [370, 600]]) {
      await page.setViewportSize({ width: 1440, height: 900 });
      await load(page, `${url}?tab=audit`);
      await page.setViewportSize({ width: 390, height: 844 });
      await settle(page);
      await page.evaluate((s) => { document.querySelector(s).scrollLeft = 0; }, VP);
      await page.waitForTimeout(600);
      await instrument(page);
      const s0 = await snap(page);
      await page.locator('[data-testid="tab-group-work"]').click();
      await page.waitForTimeout(700);
      const s1 = await snap(page);
      const hit = await page.evaluate(([x, y]) => { const e = document.elementFromPoint(x, y); return e ? e.tagName + "." + (e.className || "").toString().slice(0, 80) + " / " + (e.textContent || "").trim().slice(0, 40) : null; }, pt);
      await page.mouse.click(pt[0], pt[1]);
      await page.waitForTimeout(900);
      const s2 = await snap(page);
      const log = await page.evaluate(() => window.__log);
      const o = { page: key, point: pt, hitUnderOverlay: hit, sl: [s0.scrollLeft, s1.scrollLeft, s2.scrollLeft], activeAfter: s2.active_el, log };
      R.outsideClick.push(o);
      console.log(`outsideClick ${key} @${pt}: sl ${o.sl.join(" → ")} activeAfter=${o.activeAfter} hit="${hit}"\n   ${log.map((l) => JSON.stringify(l)).join("\n   ")}`);
    }
  }

  // C. real theme toggle at 390 after a user scroll to 0 (mock header avatar menu)
  for (const [key, url] of [["matter", M], ["client", C]]) {
    await page.setViewportSize({ width: 1440, height: 900 });
    await load(page, `${url}?tab=audit`);
    await page.setViewportSize({ width: 390, height: 844 });
    await settle(page);
    const box = await page.locator('[data-slot="grouped-tab-bar-sticky"]').boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 - 4);
    await page.mouse.wheel(-2000, 0);
    await page.waitForTimeout(800);
    let sl = await page.evaluate((s) => document.querySelector(s).scrollLeft, VP);
    const t = { page: key, afterWheel: sl, steps: [] };
    await instrument(page);
    const rec = async (label) => { await page.waitForTimeout(800); const s = await snap(page); t.steps.push({ label, sl: s.scrollLeft, fades: `${+s.fadeStart}${+s.fadeEnd}`, dark: await page.evaluate(() => document.documentElement.classList.contains("dark")), y: s.scrollY }); };
    await rec("after wheel to 0");
    const avatar = page.locator("header button.rounded-full").last();
    t.avatarVisible = await avatar.isVisible();
    await avatar.click();
    await rec("avatar menu open");
    await page.getByRole("radio", { name: "Dark" }).click();
    await rec("theme Dark");
    if (key === "matter") await page.screenshot({ path: path.join(OUT, "nofight-matter-390-dark.png"), clip: { x: 0, y: 0, width: 390, height: 400 } });
    await page.getByRole("radio", { name: "Light" }).click();
    await rec("theme Light");
    await avatar.click();
    await rec("avatar menu closed (toggle click)");
    t.log = await page.evaluate(() => window.__log.filter((l) => l.kind !== "pointerdown"));
    t.pass = t.afterWheel === 0 && t.steps.every((s) => s.sl === 0) && t.steps.some((s) => s.dark);
    R.theme.push(t);
    console.log(`theme ${key}: wheel→${t.afterWheel} avatar=${t.avatarVisible}\n   ${t.steps.map((s) => `${s.label}: sl=${s.sl} fades=${s.fades} dark=${s.dark} y=${s.y}`).join("\n   ")}\n   rowLog=${JSON.stringify(t.log)}\n   PASS=${t.pass}`);
  }
  R.console = consoleAll;
  fs.writeFileSync(path.join(OUT, "obs-after-fix2-supp.json"), JSON.stringify(R));
  console.log(`console: ${JSON.stringify(consoleAll)}`);
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
