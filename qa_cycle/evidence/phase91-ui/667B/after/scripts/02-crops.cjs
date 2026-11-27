// 667B OBS-after defect crops (NEW, read-only page loads). The Statements Not Yet Reconciled card at
// 1024 (Alice, light + dark): the open-statements table overflows its scroller and clips Discard.
// Usage (from this dir): PHASE=after node 02-crops.cjs → ../crop-open-statements-1024-{light,dark}.png
const path = require("path");
const L = require("../../before/scripts/lib.cjs");

(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  for (const theme of ["light", "dark"]) {
    const ctx = await L.newCtx(browser, st, { width: 1024, height: 768, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    await page.goto(`${L.TA}/reconciliation`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(600);
    const f = path.join(L.OUT, `crop-open-statements-1024-${theme}.png`);
    await page.getByTestId("open-statements").screenshot({ path: f });
    console.log("crop", f);
    await ctx.close();
  }
  await browser.close();
})().catch((e) => { console.error("CROPS FAIL", e); process.exit(1); });
