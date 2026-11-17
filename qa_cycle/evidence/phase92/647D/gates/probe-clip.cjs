// 647D OBS (h) final-state clip probe (adapted from 647B probe-clip.cjs; adds the Transactions table) (read-only): is any column of the Unidentified deposits table or the Fund ledger
// table outside its container / the viewport, or truncated? Signs in as Alice, opens Other trust creditors at
// 1280 / 1440 (and 1920 for reference), measures each table's scroll-container chain, header cells and every
// body cell. No state change. (647C lesson F-1.)
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/647D/gates/obs`;
(async () => {
  const browser = await chromium.launch({ headless: true });
  const res = { at: new Date().toISOString(), widths: [], consoleErrors: [] };
  for (const width of [1280, 1440, 1920]) {
    const ctx = await browser.newContext({ viewport: { width, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const p = await ctx.newPage();
    p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ width, text: m.text() }); });
    await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
    await p.locator("#user-select").selectOption("user_e2e_alice");
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/other-creditors", { waitUntil: "load" });
    await p.getByTestId("unidentified-deposit-row").first().waitFor({ timeout: 60_000 });
    await p.getByTestId("fund-ledger-section").waitFor({ timeout: 60_000 });
    await p.waitForTimeout(800);
    const m = await p.evaluate(() => {
      const vw = document.documentElement.clientWidth;
      function measure(testid) {
        const el = document.querySelector(`[data-testid='${testid}']`);
        if (!el) return { testid, missing: true };
        const t = el.tagName === "TABLE" ? el : el.querySelector("table");
        const chain = [];
        for (let x = t; x && chain.length < 6; x = x.parentElement) {
          const cs = getComputedStyle(x);
          const r = x.getBoundingClientRect();
          chain.push({ tag: x.tagName, testid: x.getAttribute("data-testid"), cls: (x.className || "").toString().slice(0, 120),
            overflowX: cs.overflowX, clientWidth: x.clientWidth, scrollWidth: x.scrollWidth, left: Math.round(r.left), right: Math.round(r.right) });
        }
        // the nearest clipping ancestor (overflow != visible) or the viewport
        let clipRight = vw, clipLeft = 0, clipBy = "viewport";
        for (let x = t.parentElement; x; x = x.parentElement) {
          const cs = getComputedStyle(x);
          if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 40)}`; break; }
        }
        const cellInfo = (c) => {
          const r = c.getBoundingClientRect();
          return { text: c.innerText.replace(/\s+/g, " ").trim(), left: Math.round(r.left), right: Math.round(r.right),
            scrollWidth: c.scrollWidth, clientWidth: c.clientWidth,
            inside: r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5, truncated: c.scrollWidth > c.clientWidth + 1 };
        };
        const header = [...t.querySelectorAll("thead th")].map(cellInfo);
        const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
        const tableRect = t.getBoundingClientRect();
        return { testid, vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), tableLeft: Math.round(tableRect.left), tableRight: Math.round(tableRect.right),
          tableScrollWidth: t.scrollWidth, chain, header, rows,
          allInside: header.every((h) => h.inside) && rows.every((r) => r.every((c) => c.inside)),
          noneTruncated: header.every((h) => !h.truncated) && rows.every((r) => r.every((c) => !c.truncated)),
          noHorizontalOverflow: chain.every((c) => c.scrollWidth <= c.clientWidth + 1 || c.overflowX === "visible") };
      }
      return { unidentified: measure("unidentified-deposits-table"), fund: measure("fund-ledger-table") };
    });
    // the Transactions page (647D description line under the type badge)
    await p.screenshot({ path: `${OUT}/h-probe-other-creditors-${width}.png`, fullPage: true });
    await p.getByTestId("unidentified-deposits-section").screenshot({ path: `${OUT}/h-probe-unidentified-${width}-crop.png` });
    await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/transactions", { waitUntil: "load" });
    await p.getByTestId("transactions-table").waitFor({ timeout: 60_000 });
    await p.waitForTimeout(800);
    m.transactions = await p.evaluate(() => {
      const vw = document.documentElement.clientWidth;
      const t = document.querySelector("[data-testid='transactions-table']");
      let clipRight = vw, clipLeft = 0, clipBy = "viewport";
      for (let x = t.parentElement; x; x = x.parentElement) {
        const cs = getComputedStyle(x);
        if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 40)}`; break; }
      }
      const cellInfo = (c) => { const r = c.getBoundingClientRect(); const kids = [...c.querySelectorAll("*")].map((k) => k.getBoundingClientRect()).filter((k) => k.width > 0);
        return { text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 140), left: Math.round(r.left), right: Math.round(r.right),
          inside: r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5 && kids.every((k) => k.left >= clipLeft - 0.5 && k.right <= clipRight + 0.5), truncated: c.scrollWidth > c.clientWidth + 1 }; };
      const header = [...t.querySelectorAll("thead th")].map(cellInfo);
      const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
      const descLines = [...t.querySelectorAll("[data-testid^='transaction-description-']")].map((e) => { const r = e.getBoundingClientRect(); return { text: e.innerText.slice(0, 140), left: Math.round(r.left), right: Math.round(r.right), height: Math.round(r.height) }; });
      const tr = t.getBoundingClientRect();
      return { vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), tableLeft: Math.round(tr.left), tableRight: Math.round(tr.right), tableScrollWidth: t.scrollWidth,
        wrapperScrollWidth: t.parentElement.scrollWidth, wrapperClientWidth: t.parentElement.clientWidth, header, rows, descLines,
        allInside: header.every((h) => h.inside) && rows.every((r) => r.every((c) => c.inside)), noneTruncated: header.every((h) => !h.truncated) && rows.every((r) => r.every((c) => !c.truncated)),
        noHorizontalOverflow: t.parentElement.scrollWidth <= t.parentElement.clientWidth + 1 };
    });
    await p.screenshot({ path: `${OUT}/h-probe-transactions-${width}.png`, fullPage: true });
    m.width = width;
    m.fundEmpty = !!m.fund.missing; // no Fund-ledger rows on this stack: that table is n/a
    m.ok = ["unidentified", "fund", "transactions"].filter((k) => !(k === "fund" && m.fundEmpty)).every((k) => !m[k].missing && m[k].allInside && m[k].noneTruncated && m[k].noHorizontalOverflow);
    res.widths.push(m);
    await ctx.close();
  }
  res.verdict = res.widths.filter((w) => w.width !== 1920).every((w) => w.ok) ? "PASS" : "FAIL";
  fs.writeFileSync(`${OUT}/probe-clip.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify(res, null, 2));
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); process.exit(1); });
