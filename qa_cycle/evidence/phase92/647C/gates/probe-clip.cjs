// 647C OBS supplementary probe (read-only): is the Fund ledger's Running balance column clipped?
// Signs in as Alice, opens Other trust creditors at several viewport widths, measures the table's
// scroll container and the last column's cells. No state change.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647C";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/647C/gates/obs`;
(async () => {
  const browser = await chromium.launch({ headless: true });
  const res = { at: new Date().toISOString(), widths: [] };
  for (const width of [1280, 1440, 1920]) {
    const ctx = await browser.newContext({ viewport: { width, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const p = await ctx.newPage();
    await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
    await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/other-creditors", { waitUntil: "load" });
    await p.getByTestId("fund-ledger-row").first().waitFor({ timeout: 60_000 });
    await p.waitForTimeout(800);
    const m = await p.evaluate(() => {
      const table = document.querySelector("[data-testid='fund-ledger-table']");
      const t = table.tagName === "TABLE" ? table : table.querySelector("table");
      const chain = [];
      for (let el = t; el && chain.length < 6; el = el.parentElement) {
        const cs = getComputedStyle(el);
        const r = el.getBoundingClientRect();
        chain.push({ tag: el.tagName, testid: el.getAttribute("data-testid"), cls: (el.className || "").toString().slice(0, 120),
          overflowX: cs.overflowX, clientWidth: el.clientWidth, scrollWidth: el.scrollWidth, left: Math.round(r.left), right: Math.round(r.right) });
      }
      const header = [...t.querySelectorAll("thead th")].map((th) => {
        const r = th.getBoundingClientRect();
        return { text: th.innerText.trim(), right: Math.round(r.right), scrollWidth: th.scrollWidth, clientWidth: th.clientWidth };
      });
      const balances = [...document.querySelectorAll("[data-testid='fund-ledger-balance']")].map((s) => {
        const td = s.closest("td");
        const r = s.getBoundingClientRect();
        const tr = td.getBoundingClientRect();
        const cs = getComputedStyle(td);
        return { text: s.innerText.trim(), spanRight: Math.round(r.right), tdRight: Math.round(tr.right), tdScrollWidth: td.scrollWidth, tdClientWidth: td.clientWidth,
          tdOverflow: cs.overflow, tdTextOverflow: cs.textOverflow, tdPaddingRight: cs.paddingRight };
      });
      return { chain, header, balances };
    });
    m.width = width;
    m.clipped = m.balances.some((b) => b.tdScrollWidth > b.tdClientWidth) || m.chain.some((c) => c.scrollWidth > c.clientWidth && c.overflowX !== "visible");
    await p.screenshot({ path: `${OUT}/22-probe-fund-ledger-${width}.png`, fullPage: false });
    await p.getByTestId("fund-ledger-section").screenshot({ path: `${OUT}/22-probe-fund-ledger-${width}-crop.png` });
    res.widths.push(m);
    await ctx.close();
  }
  fs.writeFileSync(`${OUT}/probe-clip.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify(res, null, 2));
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); process.exit(1); });
