// 646A OBS — real Chromium against the e2e mock-auth stack (:3001). Read-only: navigation + screenshots.
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-646A/frontend/node_modules/@playwright/test"
);
const fs = require("fs");

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = "/tmp/646A-evidence/obs";
const result = { pages: {}, consoleErrors: [], pageErrors: [] };

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    timezoneId: "Africa/Johannesburg",
  });
  const page = await context.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") result.consoleErrors.push({ url: page.url(), text: m.text() });
  });
  page.on("pageerror", (e) => result.pageErrors.push({ url: page.url(), text: String(e) }));

  // Login (Alice, owner)
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  await page.waitForLoadState("load");
  await page.getByRole("heading").first().waitFor({ timeout: 30_000 });
  result.afterLoginUrl = page.url();

  async function visit(key, path, file) {
    await page.goto(`${BASE}${path}`, { waitUntil: "load" });
    await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 30_000 });
    await page.waitForTimeout(1500);
    const text = await page.locator("main").first().innerText().catch(() => page.innerText("body"));
    await page.screenshot({ path: `${OUT}/${file}`, fullPage: true });
    fs.writeFileSync(`${OUT}/${file.replace(/\.png$/, ".txt")}`, text);
    result.pages[key] = { url: page.url(), screenshot: `${OUT}/${file}`, text };
    return text;
  }

  // 1. Trust overview
  await visit("overview", `/org/${SLUG}/trust-accounting`, "01-trust-overview.png");
  result.pages.overview.summaryCards = await page
    .locator('[data-testid="summary-cards"]')
    .innerText()
    .catch(() => null);

  // 2. Client-ledgers list
  await visit("clientLedgers", `/org/${SLUG}/trust-accounting/client-ledgers`, "02-client-ledgers.png");
  const headers = await page.locator("table thead th").allInnerTexts();
  const rows = await page.locator('tr[data-testid^="ledger-row-"]').evaluateAll((trs) =>
    trs.map((tr) => ({
      clientId: tr.getAttribute("data-testid").replace("ledger-row-", ""),
      cells: Array.from(tr.querySelectorAll("td")).map((td) => td.innerText.trim()),
    }))
  );
  result.pages.clientLedgers.headers = headers;
  result.pages.clientLedgers.rows = rows;
  result.pages.clientLedgers.hasTotalInterestColumn = headers.some((h) => /interest/i.test(h));

  // 3. Every client's ledger page (text + count), screenshot each; flag the one with most transactions
  result.clientDetails = [];
  let i = 0;
  for (const r of rows) {
    i += 1;
    const file = `03-client-ledger-${String(i).padStart(2, "0")}-${r.clientId}.png`;
    const text = await visit(
      `client_${r.clientId}`,
      `/org/${SLUG}/trust-accounting/client-ledgers/${r.clientId}`,
      file
    );
    const m = text.match(/(\d+) transactions? found/);
    const totals = await page
      .getByText("Ledger totals", { exact: true })
      .locator("xpath=..")
      .innerText()
      .catch(() => null);
    result.clientDetails.push({
      clientId: r.clientId,
      name: r.cells[0],
      listBalance: r.cells[1],
      transactionsFound: m ? Number(m[1]) : null,
      ledgerTotalsText: totals,
      hasInterestCreditedRow: /interest credited/i.test(text),
      screenshot: `${OUT}/${file}`,
    });
  }
  const most = [...result.clientDetails].sort(
    (a, b) => (b.transactionsFound ?? -1) - (a.transactionsFound ?? -1)
  )[0];
  result.mostTransactionsClient = most ? most.clientId : null;

  // Interest text anywhere on the pages visited
  result.interestMentions = Object.entries(result.pages)
    .filter(([, p]) => /total interest|interest credited/i.test(p.text))
    .map(([k]) => k);

  for (const p of Object.values(result.pages)) delete p.text; // keep JSON focused; screenshots hold the pixels
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (e) => {
  console.error("OBS FAILED:", e);
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  process.exit(1);
});
