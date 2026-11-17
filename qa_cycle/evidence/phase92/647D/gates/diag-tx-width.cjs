// 647D OBS (h) diagnostic, read-only on the server: why the Transactions table is wider than its 926 px
// scroll wrapper at 1280. Signs in as Alice, opens Transactions at 1280, measures every Type cell (badge width,
// description-line width) and then — in this headless page's own DOM only, nothing is sent anywhere — re-measures
// the table with (1) the 647D description lines hidden, (2) the 647D resolution rows hidden, (3) the type badges
// allowed to wrap. Each variant is undone before the next.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const OUT = `${W}/qa_cycle/evidence/phase92/647D/gates/obs`;
(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/transactions", { waitUntil: "load" });
  await p.getByTestId("transactions-table").waitFor({ timeout: 60_000 });
  await p.waitForTimeout(800);
  const res = await p.evaluate(() => {
    const t = document.querySelector("[data-testid='transactions-table']");
    const wrap = t.parentElement;
    const width = () => ({ table: Math.round(t.getBoundingClientRect().width), wrapper: wrap.clientWidth, scroll: wrap.scrollWidth,
      cols: [...t.querySelectorAll("thead th")].map((h) => `${h.innerText.trim()}:${Math.round(h.getBoundingClientRect().width)}`) });
    const typeCells = [...t.querySelectorAll("tbody tr")].map((tr) => {
      const td = tr.querySelectorAll("td")[2];
      const badge = td.querySelector("[data-slot='badge'], span");
      const desc = td.querySelector("[data-testid^='transaction-description-']");
      return { label: badge ? badge.innerText.trim() : null, badgeW: badge ? Math.round(badge.getBoundingClientRect().width) : null,
        badgeWhiteSpace: badge ? getComputedStyle(badge).whiteSpace : null, desc: desc ? desc.innerText.slice(0, 60) : null,
        descW: desc ? Math.round(desc.getBoundingClientRect().width) : null, cellW: Math.round(td.getBoundingClientRect().width) };
    });
    const out = { asIs: width(), typeCells };
    const descs = [...t.querySelectorAll("[data-testid^='transaction-description-']")];
    descs.forEach((d) => (d.style.display = "none"));
    out.descriptionLinesHidden = width();
    descs.forEach((d) => (d.style.display = ""));
    const resRows = [...t.querySelectorAll("tbody tr")].filter((tr) => /Unidentified deposit allocated|Unidentified deposit paid to the Fund|Deposit identified and allocated/.test(tr.innerText));
    resRows.forEach((r) => (r.style.display = "none"));
    out.resolutionRowsHidden = { ...width(), hiddenRows: resRows.length };
    resRows.forEach((r) => (r.style.display = ""));
    const badges = typeCells.length ? [...t.querySelectorAll("tbody tr td:nth-child(3) > span:first-child")] : [];
    badges.forEach((b) => (b.style.whiteSpace = "normal"));
    out.badgesWrap = width();
    badges.forEach((b) => (b.style.whiteSpace = ""));
    out.restored = width();
    return out;
  });
  await p.screenshot({ path: `${OUT}/h-diag-transactions-1280.png`, fullPage: true });
  await p.getByTestId("transactions-table").locator("..").screenshot({ path: `${OUT}/h-diag-transactions-1280-crop.png` });
  fs.writeFileSync(`${OUT}/diag-tx-width.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify(res, null, 2));
  await browser.close();
})().catch((e) => { console.error("DIAG FAILED", e); process.exit(1); });
