// 665E OBS-AFTER step 11 (NEW): found.md F-665E-6 asks OBS-after to confirm whether the Clauses
// category <h2> inside the <button data-testid="clause-category"> is exposed as a heading. Read-only:
// Alice 1440 light, Clauses tab; DOM h2 count inside the buttons vs Playwright role-engine counts and
// the first button's ARIA snapshot. Writes $RAW/clause-heading.json.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  await p.goto(`${L.BASE}/settings/documents?tab=clauses`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  const btn = p.getByTestId("clause-category");
  const out = {
    domH2InCategoryButtons: await p.locator('[data-testid="clause-category"] h2').count(),
    categoryButtons: await btn.count(),
    roleHeadingL2InMain: await p.locator("#main-content").getByRole("heading", { level: 2 }).count(),
    roleHeadingL2InsideCategoryButtons: await btn.getByRole("heading", { level: 2 }).count(),
    firstButtonAriaSnapshot: await btn.first().ariaSnapshot(),
    mainAriaHeadings: (await p.locator("#main-content").ariaSnapshot()).split("\n").filter((l) => /heading/.test(l)).slice(0, 20),
  };
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "clause-heading.json"), JSON.stringify(out));
  console.log(JSON.stringify(out));
})();
