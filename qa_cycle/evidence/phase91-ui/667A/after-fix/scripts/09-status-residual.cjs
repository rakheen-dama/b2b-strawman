// 667A OBS-after-fix: the Status half of F-667A-A4. At scrollLeft 0, how much of each row's Status badge and
// "1 of 2 approvals" line (approval-progress) sits under the sticky Actions cell, per persona, 1024 light/dark.
// Read-only (no clicks). Writes ../status-residual.json. Usage (Node 20): node 09-status-residual.cjs
const path = require("path"), fs = require("fs");
process.env.PHASE = "after-fix";
const L = require("../../before/scripts/lib.cjs");
const OUT = path.resolve(__dirname, "..");
const TX = `${L.TA}/transactions`;
function probe() {
  const t = document.querySelector('[data-testid="transactions-table"]'); let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  sc.scrollLeft = 0; const s = sc.getBoundingClientRect();
  const heads = [...t.querySelectorAll("thead th")].map((h) => h.textContent.trim());
  const si = heads.indexOf("Status"), xi = heads.indexOf("Actions");
  return [...t.querySelectorAll("tbody tr")].map((tr) => {
    const x = tr.children[xi].getBoundingClientRect(); const cell = tr.children[si];
    const one = (el) => { if (!el) return null; const b = el.getBoundingClientRect(); return { text: el.textContent.trim().slice(0, 30), left: Math.round(b.left - s.left), right: Math.round(b.right - s.left), coveredPx: Math.max(0, Math.round(Math.min(b.right, x.right) - Math.max(b.left, x.left))), w: Math.round(b.width) }; };
    return { actionsLeft: Math.round(x.left - s.left), badge: one(cell.querySelector('[data-slot="badge"]')), progress: one(cell.querySelector('[data-testid^="approval-progress-"]')) };
  });
}
(async () => {
  const browser = await L.chromium.launch(); const res = {};
  for (const who of ["alice", "bob", "carol"]) { const st = await L.loginState(browser, who);
    for (const theme of ["light", "dark"]) { const ctx = await L.newCtx(browser, st, { width: 1024, height: 768, theme, extra: L.EXTRA }); const page = await ctx.newPage();
      for (const [k, url] of [["await", `${TX}?status=AWAITING_APPROVAL`], ["p1", TX]]) { await page.goto(url, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(500);
        const rows = await page.evaluate(probe);
        const b = rows.map((r) => r.badge).filter(Boolean), p = rows.map((r) => r.progress).filter(Boolean);
        res[`${who}/${k}/1024-${theme}`] = { rows: rows.length, badgesFullyCovered: b.filter((x) => x.coveredPx >= x.w - 1).length, badgesPartlyCovered: b.filter((x) => x.coveredPx > 0 && x.coveredPx < x.w - 1).length, badgesClear: b.filter((x) => x.coveredPx === 0).length, maxBadgeCoveredPx: Math.max(0, ...b.map((x) => x.coveredPx)), progress: p, sample: rows[0] }; }
      await ctx.close(); } }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "status-residual.json"), JSON.stringify(res, null, 1));
  for (const [k, v] of Object.entries(res)) console.log(k, `rows ${v.rows} badge full ${v.badgesFullyCovered} part ${v.badgesPartlyCovered} clear ${v.badgesClear} maxPx ${v.maxBadgeCoveredPx} progress ${JSON.stringify(v.progress)} sample ${JSON.stringify(v.sample)}`);
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
