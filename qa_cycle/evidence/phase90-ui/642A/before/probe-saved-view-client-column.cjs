const { chromium } = require("/Users/rakheendama/Projects/2026/b2b-strawman/frontend/node_modules/@playwright/test");
(async () => {
  const b = await chromium.launch({ headless: true });
  const out = {};
  for (const w of [1024, 1440]) {
    const c = await b.newContext({ viewport: { width: w, height: w === 1024 ? 768 : 900 } });
    const p = await c.newPage();
    await p.goto("http://localhost:3001/mock-login"); await p.getByRole("button", { name: "Sign In" }).click();
    await p.waitForURL("**/dashboard", { timeout: 90000 });
    await p.goto("http://localhost:3001/org/e2e-test-org/clients"); await p.locator("#main-content h1").waitFor();
    await p.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: "OBS 642A view", exact: true }).click();
    await p.waitForURL((u) => u.toString().includes("view=")); await p.waitForLoadState("networkidle"); await p.waitForTimeout(500);
    out[w] = await p.evaluate(() => {
      const t = document.querySelector('[data-testid="clients-table"]');
      const ths = [...t.querySelectorAll("thead th")].map((th) => ({ t: th.textContent.replace(/\s+/g, " ").trim(), w: Math.round(th.getBoundingClientRect().width), l: Math.round(th.getBoundingClientRect().left) }));
      const link = t.querySelector('[data-testid="client-link"]');
      const lr = link.getBoundingClientRect();
      return { ths, firstLink: { w: Math.round(lr.width), h: Math.round(lr.height), text: link.textContent, visibleWidth: Math.round(lr.width) > 0 } };
    });
  }
  console.log(JSON.stringify(out, null, 1));
  await b.close();
})();
