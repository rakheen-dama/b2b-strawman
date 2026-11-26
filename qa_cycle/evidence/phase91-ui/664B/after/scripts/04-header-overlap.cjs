// 664B after (new, additive): F1 sort-icon placement probe. Run from this dir after the before
// scripts: `PHASE=after node 04-header-overlap.cjs`. Read-only (navigation, scroll, no clicks that write).
// For every sortable th (one holding an svg) it measures the label's text-line rects (Range over the
// th's text nodes) against the sort icon's rect: overlap yes/no and the horizontal gap on the lines
// the icon shares. It also checks the icon sits inside the th and the label is not clipped. Then it
// scrolls the page and compares the top of a sortable th with a non-sortable one (Actions) to catch
// a `relative` (F1) vs `@2xl:sticky` position conflict. Crops: thead at deviceScaleFactor 2.
// Output: ../header-overlap.json, ../sheet-header-crops.png (raw crops in /tmp/664B-after-raw/hdr/).
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const RAW = path.join(L.RAW, "hdr");
fs.mkdirSync(RAW, { recursive: true });
const VIEW = "664B K-11 view";
const OUT = { phase: "after", capturedAt: null, pages: {} };
const sink = [];
const lab = { label: "" };
const tiles = [];

const PROBE = () => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0; };
  return [...document.querySelectorAll("#main-content table")].filter(vis).map((t, ti) => {
    const ths = [...t.querySelectorAll("thead th")].filter(vis);
    return ths.map((h) => {
      const svg = h.querySelector("svg");
      const hb = h.getBoundingClientRect();
      const lines = [];
      const w = document.createTreeWalker(h, NodeFilter.SHOW_TEXT);
      for (let n = w.nextNode(); n; n = w.nextNode()) {
        if (!n.textContent.trim() || (svg && svg.contains(n))) continue;
        const r = document.createRange();
        r.selectNodeContents(n);
        for (const q of r.getClientRects()) if (q.width > 0) lines.push({ l: q.left, r: q.right, t: q.top, b: q.bottom });
      }
      const ctl = h.querySelector("a,button");
      const out = { table: ti, label: h.innerText.trim().replace(/\n/g, " / ").slice(0, 28), align: getComputedStyle(h).textAlign, thPosition: getComputedStyle(h).position, th: [Math.round(hb.left), Math.round(hb.right)], lines: lines.map((q) => [Math.round(q.l), Math.round(q.r), Math.round(q.t), Math.round(q.b)]), clipped: ctl ? ctl.scrollWidth > ctl.clientWidth + 1 : false };
      if (!svg) return out;
      const s = svg.getBoundingClientRect();
      const shared = lines.filter((q) => q.b > s.top && q.t < s.bottom);
      const overlap = shared.some((q) => q.r > s.left + 0.5 && q.l < s.right - 0.5);
      const gaps = shared.map((q) => (q.l >= s.right ? q.l - s.right : s.left - q.r));
      out.icon = { cls: (svg.getAttribute("class") || "").match(/lucide-[a-z-]+/)?.[0], rect: [Math.round(s.left), Math.round(s.right), Math.round(s.top), Math.round(s.bottom)], insideTh: s.left >= hb.left - 0.5 && s.right <= hb.right + 0.5, overlap, minGapPx: gaps.length ? +Math.min(...gaps).toFixed(1) : null, sharesLineWithText: shared.length > 0 };
      return out;
    });
  });
};
// Page scroll parity: top of the first sortable th vs the Actions (non-sortable) th before/after scrolling.
const STICK = () => {
  const t = document.querySelector("#main-content table");
  const ths = [...t.querySelectorAll("thead th")].filter((h) => h.getBoundingClientRect().width > 0);
  const sortable = ths.find((h) => h.querySelector("svg"));
  const plain = ths.find((h) => !h.querySelector("svg"));
  const top = (e) => (e ? Math.round(e.getBoundingClientRect().top) : null);
  return { sortable: sortable && sortable.innerText.trim().slice(0, 16), sortablePos: sortable && getComputedStyle(sortable).position, sortableTop: top(sortable), plain: plain && plain.innerText.trim().slice(0, 16), plainPos: plain && getComputedStyle(plain).position, plainTop: top(plain), tbodyTop: top(t.querySelector("tbody")), docScrollTop: document.scrollingElement.scrollTop, mainScrollTop: (document.querySelector("#main-content") || {}).scrollTop };
};

async function crop(p, loc, key, caption) {
  const f = path.join(RAW, `${key}.png`);
  if (!(await loc.count())) return tiles.push([caption + " (absent)", null]);
  await loc.first().screenshot({ path: f });
  tiles.push([caption, f]);
}
async function page(browser, S, key, url, { w = 1024, h = 768, theme = "light", who = "alice", view = null, stick = false } = {}) {
  const ctx = await L.newCtx(browser, S[who], { width: w, height: h, theme, extra: { ...L.EXTRA, deviceScaleFactor: 2 } });
  const p = await ctx.newPage();
  L.watch(p, sink, lab);
  lab.label = key;
  await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  if (view) {
    await p.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: view, exact: true }).first().click();
    await p.waitForURL(/view=/, { timeout: 15000 }).catch(() => {});
    await p.waitForLoadState("networkidle").catch(() => {});
  }
  await p.mouse.move(1, 1);
  await p.waitForTimeout(900);
  const r = { url: p.url().replace(L.BASE, ""), viewport: `${w}x${h}`, theme, tables: await p.evaluate(PROBE) };
  const n = r.tables.length;
  for (let i = 0; i < n; i++) await crop(p, p.locator("#main-content table").filter({ visible: true }).nth(i).locator("thead"), `${key}-t${i}`, `${key} table ${i} thead (${w}×${h} ${theme}, 2x)`);
  if (stick) {
    r.stick = { atRest: await p.evaluate(STICK) };
    const tb = await p.locator("#main-content table tbody").first().boundingBox();
    await p.mouse.move(Math.round(w / 2), Math.round(Math.min(h - 40, tb.y + 40)));
    await p.mouse.wheel(0, 450);
    await p.waitForTimeout(700);
    r.stick.afterWheel450 = await p.evaluate(STICK);
    await p.mouse.move(1, 1);
    await p.waitForTimeout(300);
    const f = path.join(RAW, `${key}-scrolled.png`);
    await p.screenshot({ path: f, clip: { x: 0, y: 0, width: w, height: Math.min(h, 360) } });
    tiles.push([`${key} after a 450px wheel over the table: top ${Math.min(h, 360)}px of the viewport (sortable th top ${r.stick.afterWheel450.sortableTop}, ${r.stick.afterWheel450.plain} th top ${r.stick.afterWheel450.plainTop})`, f]);
  }
  OUT.pages[key] = r;
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = { alice: await L.loginState(browser, "alice"), carol: await L.loginState(browser, "carol") };
  await page(browser, S, "matters-alice-1024-light", "/matters", { stick: true });
  await page(browser, S, "matters-alice-1440-dark", "/matters", { w: 1440, h: 900, theme: "dark" });
  await page(browser, S, "matters-carol-1024-light", "/matters", { who: "carol" });
  await page(browser, S, "clients-default-1024-light", "/clients", { stick: true });
  await page(browser, S, "clients-default-1440-light", "/clients", { w: 1440, h: 900, stick: true });
  await page(browser, S, "clients-k11view-1024-light", "/clients", { view: VIEW });
  await page(browser, S, "profitability-1024-light", "/reports/profitability", {});
  await browser.close();
  OUT.capturedAt = new Date().toISOString();
  let ths = 0, icons = 0, overlaps = [], outside = [], clipped = [];
  for (const [k, r] of Object.entries(OUT.pages))
    for (const t of r.tables)
      for (const h of t) {
        ths++;
        if (h.clipped) clipped.push(`${k}: ${h.label}`);
        if (!h.icon) continue;
        icons++;
        if (h.icon.overlap) overlaps.push(`${k}: ${h.label}`);
        if (!h.icon.insideTh) outside.push(`${k}: ${h.label}`);
      }
  OUT.summary = { ths, sortIcons: icons, overlaps, iconsOutsideTh: outside, clippedLabels: clipped, console: { errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, messages: sink } };
  fs.writeFileSync(path.join(L.OUT, "header-overlap.json"), JSON.stringify(OUT) + "\n");
  const b2 = await L.chromium.launch();
  await L.sheet(b2, path.join(L.OUT, "sheet-header-crops.png"), "664B after — F1 sort-icon slot: thead crops at 2x (Alice unless noted), plus the top of the viewport after scrolling (sticky header parity)", tiles, 1, 1400);
  await b2.close();
  console.log(JSON.stringify(OUT.summary));
})();
