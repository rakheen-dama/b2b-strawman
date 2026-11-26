// 664A OBS-after step 4 (NEW): keyboard walk (OBS-390/KBD). From a fresh load, Tab from the top of the
// page into the settings family bar (390) and on through its five links, then into the family tab
// strip and ArrowRight across every tab (390 and 1024), Enter on the last tab (manual activation).
// For every focus step: the focused element, :focus-visible, its focus ring (inset box-shadow), whether
// its box intersects an edge-fade overlay whose computed opacity > 0, and a crop of the row.
// Alice, light + dark at 390, light at 1024, Firm (8 tabs). Writes kbd.json + sheet-kbd-*.png to ../.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const RAW = "/tmp/664A-after-kbd-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const K = {};
const sink = [];
const lab = { label: "" };

const FOCUS = () => {
  const e = document.activeElement;
  if (!e || e === document.body) return { tag: "body" };
  const r = (x) => { const b = x.getBoundingClientRect(); return { left: +b.left.toFixed(1), top: +b.top.toFixed(1), right: +b.right.toFixed(1), bottom: +b.bottom.toFixed(1) }; };
  const nav = e.closest('nav[aria-label="Settings navigation"]');
  const tl = e.closest('[role="tablist"][aria-label$="settings"]');
  const row = nav && nav.querySelector('[data-slot="scroll-area"]') ? nav : tl ? tl.closest('[data-slot="scroll-area"]').parentElement : null;
  const cs = getComputedStyle(e);
  const out = { tag: e.tagName.toLowerCase(), role: e.getAttribute("role"), text: (e.textContent || "").trim().slice(0, 40), where: nav && row ? "family-bar" : tl ? "tab-strip" : "other", focusVisible: e.matches(":focus-visible"), boxShadow: cs.boxShadow, outline: `${cs.outlineStyle} ${cs.outlineWidth}`, box: r(e), ariaSelected: e.getAttribute("aria-selected"), ariaCurrent: e.getAttribute("aria-current") };
  if (row) {
    const vp = row.querySelector('[data-slot="scroll-area-viewport"]');
    const vb = vp.getBoundingClientRect();
    const eb = e.getBoundingClientRect();
    out.row = { top: +row.getBoundingClientRect().top.toFixed(1), bottom: +row.getBoundingClientRect().bottom.toFixed(1), scrollLeft: vp.scrollLeft, viewport: r(vp) };
    out.fullyInViewport = eb.left >= vb.left - 0.5 && eb.right <= vb.right + 0.5;
    out.fades = ["start", "end"].map((edge) => {
      const f = row.querySelector(`[data-slot$="-fade-${edge}"]`);
      const fb = f.getBoundingClientRect();
      const op = +getComputedStyle(f).opacity;
      const ix = Math.max(0, Math.min(eb.right, fb.right) - Math.max(eb.left, fb.left));
      const iy = Math.max(0, Math.min(eb.bottom, fb.bottom) - Math.max(eb.top, fb.top));
      return { edge, dataVisible: f.getAttribute("data-visible"), opacity: op, box: r(f), intersectPx: +(ix * iy).toFixed(1), coveredByVisibleFade: op > 0 && ix * iy > 0 };
    });
    out.ringHiddenUnderFade = out.fades.some((f) => f.coveredByVisibleFade);
  }
  out.ringVisible = out.focusVisible && /inset/.test(out.boxShadow) && !/rgba\(0, 0, 0, 0\) 0px 0px 0px 2px inset/.test(out.boxShadow);
  return out;
};

async function walk(browser, S, w, h, theme) {
  const key = `${w}-${theme}`;
  const ctx = await C.newCtx(browser, S, { width: w, height: h, theme, extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
  const p = await ctx.newPage();
  C.watch(p, sink, lab);
  lab.label = `kbd|${key}`;
  await p.goto(`${BASE}/settings/firm`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(600);
  const R = (K[key] = { preTabs: [], steps: [] });
  const tiles = [];
  const snap = async (label, f) => {
    const y = Math.max(0, (f.row ? f.row.top : f.box.top) - 12);
    const file = path.join(RAW, `${key}-${String(tiles.length).padStart(2, "0")}.png`);
    const x0 = f.row ? Math.max(0, f.row.viewport.left - 16) : 0;
    const wid = f.row ? Math.min(w - x0, f.row.viewport.right - f.row.viewport.left + 32) : w;
    await p.screenshot({ path: file, clip: { x: x0, y, width: wid, height: 72 } });
    tiles.push([`${label}: "${f.text}" ${f.where} focus-visible=${f.focusVisible} ring=${f.ringVisible} under-fade=${!!f.ringHiddenUnderFade} sl=${f.row ? f.row.scrollLeft : "-"} fades ${f.fades ? f.fades.map((x) => `${x.edge}:${x.opacity}`).join("/") : "-"}`, file]);
  };
  const target = w < 768 ? "family-bar" : "tab-strip";
  // Tab from the top of the page until focus enters the first row (bounded).
  let f;
  for (let i = 0; i < 150; i++) {
    await p.keyboard.press("Tab");
    f = await p.evaluate(FOCUS);
    if (f.where === target) break;
    R.preTabs.push(`${f.tag}${f.role ? `[${f.role}]` : ""} "${f.text}"`);
  }
  if (w < 768) {
    // Through the family bar: 5 links, then on into the strip.
    for (let i = 0; i < 5 && f.where === "family-bar"; i++) {
      R.steps.push({ key: i === 0 ? "Tab (enter bar)" : "Tab", ...f });
      await snap(`Tab ${i + 1}`, f);
      await p.keyboard.press("Tab");
      await p.waitForTimeout(150);
      f = await p.evaluate(FOCUS);
    }
    R.betweenBarAndStrip = [];
    for (let i = 0; i < 20 && f.where !== "tab-strip"; i++) {
      R.betweenBarAndStrip.push(`${f.tag}${f.role ? `[${f.role}]` : ""} "${f.text}"`);
      await p.keyboard.press("Tab");
      f = await p.evaluate(FOCUS);
    }
  }
  // Into the strip (roving tabindex: Tab lands on the selected tab), then ArrowRight across it.
  R.steps.push({ key: "Tab (enter strip)", ...f });
  await snap("Tab → strip", f);
  const n = await p.locator('#main-content [role="tablist"][aria-label="Firm settings"] [role="tab"]').count();
  for (let i = 1; i < n; i++) {
    await p.keyboard.press("ArrowRight");
    await p.waitForTimeout(350);
    f = await p.evaluate(FOCUS);
    R.steps.push({ key: "ArrowRight", url: await p.evaluate(() => location.search), ...f });
    await snap(`ArrowRight ${i}`, f);
  }
  await p.keyboard.press("Enter");
  await p.waitForURL(/\?tab=/, { timeout: 30000 }).catch(() => {});
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  R.afterEnter = await p.evaluate(() => ({ url: location.search, selected: document.querySelector('[role="tablist"][aria-label="Firm settings"] [aria-selected="true"]').textContent.trim(), active: `${document.activeElement.tagName.toLowerCase()} "${(document.activeElement.textContent || "").trim().slice(0, 30)}"`, focusInPanel: !!document.querySelector('#main-content [role="tabpanel"]').contains(document.activeElement) }));
  R.summary = { focusSteps: R.steps.length, allFocusVisible: R.steps.every((s) => s.focusVisible), allRingVisible: R.steps.every((s) => s.ringVisible), anyRingUnderVisibleFade: R.steps.some((s) => s.ringHiddenUnderFade), allFullyInViewport: R.steps.every((s) => s.fullyInViewport), arrowUrlUnchanged: R.steps.filter((s) => s.key === "ArrowRight").every((s) => s.url === ""), orderVisited: R.steps.map((s) => s.text) };
  await ctx.close();
  return tiles;
}

(async () => {
  const browser = await C.chromium.launch();
  const S = await C.loginState(browser, "alice");
  const t390l = await walk(browser, S, 390, 844, "light");
  const t390d = await walk(browser, S, 390, 844, "dark");
  const t1024 = await walk(browser, S, 1024, 768, "light");
  K.console = { errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, messages: sink };
  fs.writeFileSync(path.join(OUT, "kbd.json"), JSON.stringify({ capturedAt: new Date().toISOString(), ...K }) + "\n");
  const interleave = t390l.flatMap((t, i) => [t, t390d[i] || ["(missing)", null]]);
  await C.sheet(browser, path.join(OUT, "sheet-kbd-390.png"), "664A after — KBD 390×844 Alice /settings/firm: Tab through the family bar (5 links) then into the tab strip and ArrowRight across 8 tabs; cols light / dark. Each crop = the focused row at that step", interleave, 2);
  await C.sheet(browser, path.join(OUT, "sheet-kbd-1024.png"), "664A after — KBD 1024×768 Alice light /settings/firm: Tab into the tab strip (selected tab) then ArrowRight across 8 tabs", t1024, 2);
  await browser.close();
  for (const k of ["390-light", "390-dark", "1024-light"]) console.log(k, JSON.stringify(K[k].summary), JSON.stringify(K[k].afterEnter), "pre", K[k].preTabs.length, "between", JSON.stringify(K[k].betweenBarAndStrip || []));
  console.log("console", JSON.stringify(K.console).slice(0, 300));
})();
