// 652B reproduce-first (task 652.4, browser): headless Chromium against the e2e mock-auth stack
// (:3001) as Alice. Every state change goes through the UI. psql is read-only evidence.
//
// 1. Trust → Transactions → Record Transaction → Record Deposit: a deposit (unique amount and
//    reference) dated two days ago. Deposits land RECORDED (no approval step).
// 2. Reconciliation wizard, period end today: upload a one-line statement whose credit equals the
//    deposit's amount, dated today, with a different reference — auto-match scores it "amount, close
//    date" (below threshold), so the line stays Unmatched and must be matched by hand.
// 3. Review: the "Unmatched Trust Transactions" pane. On main it lists APPROVED rows only, so the
//    recorded deposit is absent and the line has no Match candidate.
//
// Run from the worktree's frontend/: NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652B/repro-browser.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(
  path.resolve(process.cwd(), "node_modules/@playwright/test"),
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const today = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Africa/Johannesburg",
}).format(new Date());
const addDays = (iso, n) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const periodEnd = today;
const depositDate = addDays(today, -2);
const AMOUNT = 652 + (parseInt(RUN, 36) % 900) / 100; // unique per run, e.g. 658.37
const DEP_REF = `DEP/652B/${RUN}`;
const result = { run: RUN, today, periodEnd, depositDate, amount: AMOUNT, depositRef: DEP_REF, steps: {}, pageErrors: [], consoleErrors: [] };

function psql(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q],
    { encoding: "utf8" },
  ).trim();
}
const SCHEMA = psql(
  "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1",
);
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  const [client, matter] = sql(
    "select c.name, p.name from T.customers c join T.projects p on p.customer_id=c.id order by c.created_at, p.created_at limit 1",
  ).split("|");
  Object.assign(result, { schema: SCHEMA, accountId, client, matter });

  const csv = path.join(OUT, `s1-${RUN}.csv`);
  fs.writeFileSync(
    csv,
    [
      `Standard Bank Trust Account - 652B ${RUN}`,
      "Date,Description,Reference,Amount,Balance",
      `${today},Deposit ${client},BANK/652B/${RUN},${AMOUNT.toFixed(2)},${AMOUNT.toFixed(2)}`,
    ].join("\n") + "\n",
  );

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push(String(e)));
  page.on("console", (m) => m.type() === "error" && result.consoleErrors.push(m.text().slice(0, 300)));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await page.waitForURL(/\/org\//, { timeout: 60_000 });

  // 1. Record the deposit through the UI.
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: "Record Deposit" }).click();
  const dialog = page.getByRole("dialog", { name: "Record Deposit" });
  await dialog.waitFor();
  await dialog.getByTestId("trust-deposit-customer-trigger").click();
  await page.getByRole("option", { name: new RegExp(`^${client}(\\s|$)`) }).click();
  await dialog.getByTestId("trust-deposit-matter-trigger").click();
  await page.getByRole("option", { name: new RegExp(matter) }).click();
  await dialog.getByLabel("Amount").fill(AMOUNT.toFixed(2));
  await dialog.getByLabel("Reference").fill(DEP_REF);
  await dialog.getByLabel(/Description/).fill(`652B repro ${RUN}`);
  await dialog.getByLabel("Transaction Date").fill(depositDate);
  await dialog.getByRole("button", { name: "Record Deposit" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15_000 });
  const row = page.locator("main tbody tr", { hasText: DEP_REF });
  await row.waitFor({ timeout: 15_000 });
  result.steps.depositRow = (await row.innerText()).replace(/\s+/g, " ");
  await page.screenshot({ path: path.join(OUT, "repro-browser-1-deposit-recorded.png"), fullPage: true });
  result.steps.depositDb = sql(
    `select id, status, amount, transaction_date, coalesce(bank_statement_line_id::text,'') from T.trust_transactions where reference='${DEP_REF}'`,
  );
  const depositId = result.steps.depositDb.split("|")[0];

  // 2. Wizard: period end today, the one-line statement, auto-match, review.
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(periodEnd);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(csv);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  result.steps.autoMatch = (await page.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(1000);

  const stmt = sql(`select id from T.bank_statements where file_name='${path.basename(csv)}'`);
  const lineId = sql(`select id from T.bank_statement_lines where bank_statement_id='${stmt}'`);
  result.steps.lineDb = sql(`select match_status, coalesce(trust_transaction_id::text,'') from T.bank_statement_lines where id='${lineId}'`);

  // 3. The pane: which transactions are listed; select the line and look for a Match on the deposit.
  const listed = await page.locator('[data-testid^="transaction-"]').evaluateAll((els) =>
    els.map((e) => e.getAttribute("data-testid").replace("transaction-", "")),
  );
  const listedRows = listed.length
    ? sql(`select id, transaction_type, status, amount, reference from T.trust_transactions where id in (${listed.map((id) => `'${id}'`).join(",")}) order by transaction_date desc`)
    : "";
  await page.getByTestId(`bank-line-${lineId}`).click();
  await page.waitForTimeout(500);
  result.steps.review = {
    paneListed: listedRows.split("\n").filter(Boolean),
    depositListed: listed.includes(depositId),
    matchButtonForDeposit: await page.getByTestId(`match-btn-${depositId}`).count(),
    lineText: (await page.getByTestId(`bank-line-${lineId}`).innerText()).replace(/\s+/g, " "),
    dbUnlinkedPosted: sql(
      `select status, transaction_type, count(*) from T.trust_transactions where trust_account_id='${accountId}' and bank_statement_line_id is null and status in ('RECORDED','APPROVED') group by 1,2 order by 1,2`,
    ).split("\n"),
  };
  await page.screenshot({ path: path.join(OUT, "repro-browser-2-review-pane.png"), fullPage: true });
  Object.assign(result, { depositId, statementId: stmt, lineId });

  fs.writeFileSync(path.join(OUT, "repro-browser-result.json"), JSON.stringify(result, null, 2));
  await browser.close();
  console.log(JSON.stringify(result, null, 2));
})().catch((e) => {
  fs.writeFileSync(path.join(OUT, "repro-browser-result.json"), JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  console.error(e);
  process.exit(1);
});
