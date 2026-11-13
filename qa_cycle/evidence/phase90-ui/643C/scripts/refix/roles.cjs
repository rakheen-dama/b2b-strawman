// 643C OBS: accessible columnheader names (Playwright aria snapshot) for the utilization + matter tables.
const fs = require("fs"), path = require("path");
const { chromium, PROF, OUT, session, open } = require("./common.cjs");
(async () => {
  const browser = await chromium.launch(); const { page } = await session(browser, {});
  await open(page, PROF, '[data-slot="card"] table'); const out = {};
  for (const t of ["Team Utilization", "Matter Profitability", "Client Profitability"]) {
    const card = page.locator('[data-slot="card"]').filter({ has: page.locator('[data-slot="card-title"]', { hasText: new RegExp(`^${t}$`) }) }).last();
    const hs = card.getByRole("columnheader"); const n = await hs.count(); out[t] = { count: n, snapshot: await card.locator("thead").ariaSnapshot() };
  }
  out.h1 = await page.getByRole("heading", { level: 1 }).allTextContents();
  fs.writeFileSync(path.join(OUT, "roles-raw.json"), JSON.stringify(out)); console.log(JSON.stringify(out, null, 1)); await browser.close();
})();
