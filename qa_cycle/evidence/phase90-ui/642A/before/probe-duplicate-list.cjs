const { chromium } = require("/Users/rakheendama/Projects/2026/b2b-strawman/frontend/node_modules/@playwright/test");
(async () => {
  const b = await chromium.launch({ headless: true });
  const p = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  await p.goto("http://localhost:3001/mock-login"); await p.getByRole("button", { name: "Sign In" }).click();
  await p.waitForURL("**/dashboard", { timeout: 90000 });
  await p.goto("http://localhost:3001/org/e2e-test-org/clients"); await p.locator("#main-content h1").waitFor(); await p.waitForLoadState("networkidle");
  console.log(JSON.stringify(await p.evaluate(() => {
    const all = [...document.querySelectorAll('[data-testid="clients-table"]')];
    return { clientsTablesInDocument: all.length, inMain: all.filter((t) => t.closest("#main-content")).length,
      outside: all.filter((t) => !t.closest("#main-content")).map((t) => { let n = t, path = []; while (n && path.length < 8) { path.push(n.tagName.toLowerCase() + (n.id ? "#" + n.id : "") + (n.hidden ? "[hidden]" : "")); n = n.parentElement; } return { visible: t.getClientRects().length > 0, path }; }),
      clientRowsInDocument: document.querySelectorAll('[data-testid="client-row"]').length,
      clientRowsInMain: document.querySelectorAll('#main-content [data-testid="client-row"]').length,
      h1InDocument: document.querySelectorAll("h1").length };
  })));
  await b.close();
})();
