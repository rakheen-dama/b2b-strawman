// 667A OBS-after F-667A-A4 prototype: at 1024 the sticky Actions hides Amount + Status at scrollLeft 0.
// Live-DOM style edits only (nothing saved). P0 = as built; P1 = Actions stacked (flex-col);
// P2 = P1 + Reference floor 6rem + Type floor 7rem; P3 = P2 + Status badge allowed to wrap. Alice + Bob, 1024 light.
const path = require("path"), fs = require("fs");
process.env.PHASE = "after";
const L = require("../../before/scripts/lib.cjs");
const OUT = path.resolve(__dirname, "..");
const TX = `${L.TA}/transactions`;
const res = {};
function probe() {
  const t = document.querySelector('[data-testid="transactions-table"]'); let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const heads = [...t.querySelectorAll("thead th")].map((h) => h.textContent.trim());
  sc.scrollLeft = 0;
  const rows = [...t.querySelectorAll("tbody tr")].filter((r) => { const b = r.getBoundingClientRect(); return b.top >= 0 && b.bottom <= innerHeight; }).slice(0, 4);
  const s = sc.getBoundingClientRect();
  const shown = Object.fromEntries(heads.map((h, i) => [h, rows.filter((r) => { const c = r.children[i], b = c.getBoundingClientRect(); const cx = Math.min(Math.max(b.left + b.width / 2, s.left + 2), s.right - 2); const hit = document.elementFromPoint(cx, b.top + b.height / 2); return hit && c.contains(hit) && b.right <= s.right + 0.5; }).length + "/" + rows.length]));
  return { scroller: `${sc.clientWidth}/${sc.scrollWidth}`, widths: Object.fromEntries(heads.map((h, i) => [h, Math.round(t.querySelector("thead tr").children[i].getBoundingClientRect().width)])), shown };
}
function apply(level) {
  const t = document.querySelector('[data-testid="transactions-table"]');
  const heads = [...t.querySelectorAll("thead th")].map((h) => h.textContent.trim().toLowerCase());
  const col = (n) => [...t.querySelectorAll("tr")].map((r) => r.children[heads.indexOf(n)]).filter(Boolean);
  if (level >= 1) for (const c of col("actions")) { for (const d of c.querySelectorAll("div.flex-wrap")) { d.style.flexDirection = "column"; d.style.alignItems = "stretch"; } }
  if (level >= 2) { for (const c of col("reference")) c.style.minWidth = "6rem"; for (const c of col("type")) c.style.minWidth = "7rem"; }
  if (level >= 3) for (const c of col("status")) { c.style.whiteSpace = "normal"; for (const b of c.querySelectorAll('[data-slot="badge"]')) { b.style.whiteSpace = "normal"; } }
}
(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "bob"]) {
    const st = await L.loginState(browser, who);
    const ctx = await L.newCtx(browser, st, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
    const page = await ctx.newPage();
    for (const [k, url] of [["tx-await", `${TX}?status=AWAITING_APPROVAL`], ["tx-p2", `${TX}?page=1`]]) {
      if (who === "bob" && k === "tx-p2") continue;
      res[`${who}/${k}`] = {};
      for (const lvl of [0, 1, 2, 3]) {
        await page.goto(url, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(500);
        await page.getByTestId("transactions-table").scrollIntoViewIfNeeded();
        await page.evaluate(apply, lvl); await page.waitForTimeout(100);
        res[`${who}/${k}`][`P${lvl}`] = await page.evaluate(probe);
        if (who === "alice" && k === "tx-await" && (lvl === 0 || lvl === 3)) await page.getByTestId("transactions-table").screenshot({ path: path.join(OUT, `crop-F-667A-A4-1024-await-${lvl === 0 ? "as-built" : "prototype-P3"}.png`) });
      }
    }
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "prototype-sticky.json"), JSON.stringify(res, null, 1));
  for (const [k, v] of Object.entries(res)) for (const [p, m] of Object.entries(v)) console.log(k, p, m.scroller, JSON.stringify(m.widths), JSON.stringify(m.shown));
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
