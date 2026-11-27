// 670A task 670.6 dark walk: every in-scope /org/ surface of the exit matrix (M1 groups from
// m1-targets.cjs + M2 groups from m2-targets.cjs, every target that has an Alice 1440-dark tile, with
// the same interaction / rail state) + re-verification pages for carried items (K-57 matter overview,
// K-58 matter Team tab, Phase 90 D-3 /matters/new). Alice (owner), 1440×900, stored kazi-theme=dark.
// Per page: load + settle (lib) + the target's act, then m2-dark-audit.cjs darkAudit() (composited
// canvas contrast) and a raw viewport tile under /tmp/670A-exit-raw/darkwalk/ (not committed; the
// committed screenshots are the matrix contact sheets' Alice 1440-D tiles, and defect crops).
// Probes (read-only, nothing saved): K-21 focused menu item (open the first row-actions menu on a list
// page, ArrowDown, measure the highlighted item), then Escape.
// Output: ../darkwalk.json (single line). Usage: node m2-darkwalk.cjs [keyFilterRegex]
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");
const { darkAudit } = require("./m2-dark-audit.cjs");
const M1 = require("./m1-targets.cjs").GROUPS;
const M2 = require("./m2-targets.cjs").GROUPS;
const ids = L.readIds();
const RAWD = path.join(L.RAW, "darkwalk");
fs.mkdirSync(RAWD, { recursive: true });
const filt = process.argv[2] ? new RegExp(process.argv[2]) : null;

const pages = [];
const seen = new Set();
for (const [gname, g] of [...Object.entries(M1), ...Object.entries(M2)]) {
  for (const t of g.targets) {
    const url = typeof t.url === "function" ? t.url("alice") : t.url;
    if (!url.includes("/org/") || !((t.personas.alice || []).includes("1440-dark")) || seen.has(t.key)) continue;
    seen.add(t.key);
    pages.push({ group: gname, key: t.key, surface: t.surface, url, act: t.act, collapsed: !!t.collapsed, sheet: `${gname.startsWith("settings") ? "settings" : gname}/${t.sheet || t.key}.png` });
  }
}
const B = L.BASE;
const mid = ids.matterId;
pages.push({ group: "reverify", key: "matter-overview", surface: "Re-verify K-57 › matter Overview", url: `${B}/matters/${mid}?tab=overview`, sheet: null, collapsed: false });
pages.push({ group: "reverify", key: "matter-team", surface: "Re-verify K-58 › matter Team tab", url: `${B}/matters/${mid}?tab=team`, sheet: null, collapsed: false });
pages.push({ group: "reverify", key: "matters-new", surface: "Re-verify Phase 90 D-3 › /matters/new", url: `${B}/matters/new`, sheet: null, collapsed: false });

// K-21 focused menu item probe pages (first one with a row-actions menu wins).
const MENU_PAGES = [`${B}/team`, `${B}/legal/adverse-parties`, `${B}/clients`, `${B}/matters`, `${B}/settings/matters?tab=tags`];
const MENU_PROBE = () => {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const rgba = (c) => { cx.clearRect(0, 0, 1, 1); cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2], d[3] / 255]; };
  const blend = (t, u) => [t[0] * t[3] + u[0] * (1 - t[3]), t[1] * t[3] + u[1] * (1 - t[3]), t[2] * t[3] + u[2] * (1 - t[3]), 1];
  const lum = (c) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
  const hex = (c) => "#" + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const bgOf = (el) => { if (!el || el.nodeType !== 1) return [9, 9, 11, 1]; const o = rgba(getComputedStyle(el).backgroundColor); return o[3] >= 0.999 ? o : o[3] > 0 ? blend(o, bgOf(el.parentElement)) : bgOf(el.parentElement); };
  const items = [...document.querySelectorAll('[role="menuitem"], [role="option"]')].filter((e) => e.getBoundingClientRect().width > 2);
  return items.slice(0, 6).map((e) => { const bg = bgOf(e); const fg = blend(rgba(getComputedStyle(e).color), bg); return { text: e.textContent.trim().slice(0, 30), highlighted: e.hasAttribute("data-highlighted") || e === document.activeElement, fg: hex(fg), bg: hex(bg), ratio: +ratio(fg, bg).toFixed(2), cls: String(e.className).slice(0, 160) }; });
};

(async () => {
  const browser = await L.chromium.launch();
  const state = await L.loginState(browser, "alice");
  const sink = [];
  const walk = [];
  const t0 = Date.now();
  for (const col of [false, true]) {
    const todo = pages.filter((p) => p.collapsed === col && (!filt || filt.test(p.key)));
    if (!todo.length) continue;
    const ctx = await L.ctxFor(browser, state, "1440-dark", { collapsed: col });
    const page = await ctx.newPage();
    const lbl = { label: "" };
    L.watch(page, sink, lbl, "darkwalk");
    for (const p of todo) {
      lbl.label = `darkwalk|${p.key}|alice|1440-dark`;
      const rec = { group: p.group, key: p.key, surface: p.surface, url: p.url.replace(L.APP, ""), sheet: p.sheet, collapsed: p.collapsed };
      try {
        const resp = await page.goto(p.url, { waitUntil: "load", timeout: 45000 });
        rec.status = resp ? resp.status() : null;
        await L.settle(page);
        if (p.act) { try { rec.act = (await p.act(page, { who: "alice", v: "1440-dark" })) || "ok"; } catch (e) { rec.act = { error: e.message.split("\n")[0] }; } await page.waitForTimeout(300); }
        await page.mouse.move(1, 1);
        await page.waitForTimeout(500);
        Object.assign(rec, await page.evaluate(() => ({ finalUrl: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search, h1: [...document.querySelectorAll("main#main-content h1")].map((h) => h.textContent.trim().slice(0, 60)), dark: document.documentElement.classList.contains("dark") })));
        rec.audit = await page.evaluate(darkAudit);
        rec.raw = path.join(RAWD, `${p.key}.png`);
        await page.screenshot({ path: rec.raw, fullPage: false });
      } catch (e) { rec.error = String(e).slice(0, 300); }
      const c = L.counts(sink, lbl.label); Object.assign(rec, c);
      walk.push(rec);
      const a = rec.audit || {};
      console.log(`${p.key} st=${rec.status} dark=${rec.dark} fails=${a.failCount} ill=${a.illegibleCount} light=${a.lightSurfaceCount} discs=${a.lightDiscCount} faint=${a.faintFieldCount} destr=${(a.destructive || []).filter((d) => !d.reddish).length}/${(a.destructive || []).length} accent=${(a.accent || []).length} e/w=${c.cErr}/${c.cWarn} ${rec.error ? "ERR " + rec.error.slice(0, 80) : ""} ${Math.round((Date.now() - t0) / 1000)}s`);
    }
    await ctx.close();
  }
  // K-21 menu probe.
  const probes = {};
  if (!filt) {
    const ctx = await L.ctxFor(browser, state, "1440-dark");
    const page = await ctx.newPage();
    for (const u of MENU_PAGES) {
      await page.goto(u, { waitUntil: "load" }); await L.settle(page);
      const trig = page.locator('#main-content button[aria-haspopup="menu"]').first();
      if (!(await trig.count())) continue;
      await trig.click(); await page.waitForTimeout(600);
      await page.keyboard.press("ArrowDown"); await page.waitForTimeout(400);
      probes.k21Menu = { page: u.replace(L.APP, ""), trigger: (await trig.getAttribute("aria-label")) || (await trig.innerText()).slice(0, 30), items: await page.evaluate(MENU_PROBE) };
      await page.screenshot({ path: path.join(RAWD, "k21-menu.png") });
      await page.keyboard.press("Escape");
      break;
    }
    await ctx.close();
  }
  await browser.close();
  const out = path.join(L.EXIT, "darkwalk.json");
  const prev = fs.existsSync(out) ? JSON.parse(fs.readFileSync(out, "utf8")) : { walk: [] };
  const keys = new Set(walk.map((w) => w.key));
  const merged = filt ? prev.walk.filter((w) => !keys.has(w.key)).concat(walk) : walk;
  fs.writeFileSync(out, JSON.stringify({ capturedAt: new Date().toISOString(), persona: "alice (owner)", theme: "dark (stored kazi-theme)", viewport: "1440x900", pages: merged.length, probes: filt ? prev.probes : probes, console: filt ? prev.console : sink, walk: merged.map((w) => ({ ...w, raw: w.raw ? L.rel(w.raw) : null })) }) + "\n");
  console.log("DONE", walk.length, "pages", JSON.stringify(probes).slice(0, 600));
})().catch((e) => { console.error("DARKWALK FAIL", e); process.exit(1); });
