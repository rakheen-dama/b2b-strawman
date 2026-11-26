// 665C OBS after-fix step 9b (new, read-only): 3× crops of the Naming "Available placeholders" box
// (Alice, 1440×900, light and dark) for the F-665C-11 eyeball, plus a GET of /api/settings to record
// the stored naming pattern (explains the empty-looking Pattern input: F-665C-3 left it at " ").
// Writes ../crop-fixed-naming-chips-{light,dark}-3x.png; prints the pattern.
const path = require("path");
const L = require("./lib.cjs");
(async () => {
  const a = await L.idpToken("alice");
  const s = (await L.apiGet(a, "/api/settings")).json || {};
  console.log("settings naming keys", JSON.stringify(Object.fromEntries(Object.entries(s).filter(([k]) => /nam/i.test(k)))));
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  for (const theme of ["light", "dark"]) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 3, storageState: S, ...L.EXTRA });
    await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
    await ctx.addInitScript(L.INIT, theme);
    const p = await ctx.newPage();
    await p.goto(`${L.BASE}/settings/matters?tab=naming`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(400);
    const box = p.locator("#main-content code").first().locator("xpath=ancestor::div[contains(@class,'bg-muted')][1]");
    await box.screenshot({ path: path.join(L.OUT, `crop-fixed-naming-chips-${theme}-3x.png`) });
    await ctx.close();
  }
  await browser.close();
  console.log("ok");
})().catch((e) => { console.error(e); process.exit(1); });
