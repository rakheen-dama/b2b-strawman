// 642A final: Client column width at narrow/wide frames on the default view (rich data present or not).
// Usage: node probe-client-col-narrow.cjs → final/probe-client-col-narrow.json
const path = require("path"); const fs = require("fs");
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-642A/frontend/node_modules/@playwright/test");
const BASE = "http://localhost:3001";
(async () => {
  const b = await chromium.launch(); const out = {};
  for (const [w, h] of [[390, 844], [768, 1024], [1024, 768], [1440, 900]]) {
    const c = await b.newContext({ viewport: { width: w, height: h } }); const p = await c.newPage();
    await p.goto(`${BASE}/mock-login`); await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In" }).click(); await p.waitForURL("**/dashboard", { timeout: 90000 });
    await p.goto(`${BASE}/org/e2e-test-org/clients`); await p.locator('#main-content [data-testid="clients-table"]').waitFor();
    await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400);
    out[`${w}x${h}`] = await p.evaluate(() => {
      const t = document.querySelector('#main-content [data-testid="clients-table"]');
      const r = (el) => el.getBoundingClientRect();
      const th = t.querySelector("thead th"); const th2 = t.querySelectorAll("thead th")[1];
      const links = [...t.querySelectorAll('[data-testid="client-link"]')];
      const td = t.querySelector("tbody tr td");
      return {
        clientThWidth: Math.round(r(th).width), clientThClass: th.className.slice(0, 160),
        clientTdWidth: Math.round(r(td).width), clientTdClass: td.className.slice(0, 160),
        headerOverlapsNext: r(th).right > r(th2).left + 0.5 || th.querySelector("a") && r(th.querySelector("a")).right > r(th2).left + 0.5,
        clientHeaderLinkWidth: th.querySelector("a") ? Math.round(r(th.querySelector("a")).width) : null,
        linkWidths: links.map((a) => Math.round(r(a).width)),
        namesVisibleChars: links.map((a) => ({ name: a.textContent, clientW: a.clientWidth, scrollW: a.scrollWidth })),
        tableWidth: Math.round(r(t).width), containerClientWidth: t.parentElement.clientWidth,
      };
    });
    await c.close();
  }
  fs.writeFileSync(path.join(__dirname, "probe-client-col-narrow.json"), JSON.stringify(out, null, 2)); await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
