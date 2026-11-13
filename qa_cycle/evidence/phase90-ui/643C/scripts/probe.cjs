const { chromium, PROF, session, open } = require("./common.cjs");
(async () => {
  const browser = await chromium.launch();
  const { page, get } = await session(browser, {});
  const u = await get("/api/reports/utilization?from=2026-11-01&to=2026-11-13");
  const p = await get("/api/reports/profitability?from=2026-11-01&to=2026-11-13");
  console.log("util", u.status, JSON.stringify(u.body).slice(0, 1500));
  console.log("prof", p.status, JSON.stringify(p.body).slice(0, 1500));
  await open(page, PROF);
  console.log(await page.evaluate(() => [...document.querySelectorAll('[data-slot="card-title"]')].map((e) => e.textContent.trim())));
  console.log(await page.evaluate(() => document.querySelector("#main-content").querySelectorAll("table").length));
  await browser.close();
})();
