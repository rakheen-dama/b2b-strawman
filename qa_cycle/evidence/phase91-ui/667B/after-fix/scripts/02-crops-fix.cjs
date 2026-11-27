// 667B OBS after-fix measured-fix crops (NEW, read-only page loads). Alice, light:
//  - the reconciliation card (Reconciliation History) on page 2 (?page=1) at 1280×800, where
//    F-667B-A3 must now show the Completed column and keep List locked in view;
//  - the same card at 1024×768 page 1 (page 2 at 1024 comes from the unedited 02-assemble crop).
// The open-statements 1024 crops come from after/scripts/02-crops.cjs (run unedited, PHASE=after-fix)
// and the investments 1024 crop from the unedited 02-assemble.
// Usage (from this dir): PHASE=after-fix node 02-crops-fix.cjs → ../crop-recon-{p2-1280,p1-1024}-light.png
const path = require("path");
const L = require("../../before/scripts/lib.cjs");

(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  for (const [w, h, q, name] of [[1280, 800, "?page=1", "crop-recon-p2-1280-light.png"], [1024, 768, "", "crop-recon-p1-1024-light.png"]]) {
    const ctx = await L.newCtx(browser, st, { width: w, height: h, theme: "light", extra: L.EXTRA });
    const page = await ctx.newPage();
    await page.goto(`${L.TA}/reconciliation${q}`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(600);
    const f = path.join(L.OUT, name);
    await page.locator('[data-slot="card"]:has([data-testid="reconciliations-table"])').first().screenshot({ path: f });
    console.log("crop", f);
    await ctx.close();
  }
  await browser.close();
})().catch((e) => { console.error("CROPS FAIL", e); process.exit(1); });
