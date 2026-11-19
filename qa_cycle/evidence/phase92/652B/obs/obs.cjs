// 652B OBS gate (PR #1816, head 82f5490d6): headless Chromium against the e2e mock-auth stack (:3001)
// as Alice. Adapted from ../repro-browser.cjs. Every state change goes through the UI; psql is
// read-only evidence.
//
// Setup (UI): a deposit (RECORDED) of amount A1 dated today-2; a second client + onboarding + a
// matter for it; an inter-client transfer Acme -> new client of amount A2 dated today-1
// (TRANSFER_OUT / TRANSFER_IN, RECORDED).
// Statement (one upload): +A1 today (close-date suggestion only -> Unmatched), +A2 and -A2 today
// (transfer legs are sign-incompatible for auto-match -> Unmatched), -1250.00 today-2 (APPROVED
// payment PAY/652A/GT8HE is 2 days off -> close-date suggestion only -> Unmatched).
// Checks: (a) deposit listed + Match offered on the A1 line; (b) Match -> line Matched, deposit
// leaves the pane, DB linked; (c) neither transfer leg listed / no Match on the A2 lines;
// (d) APPROVED unlinked payments still listed, Match offered on the 1250 line (not clicked).
//
// Run from the worktree's frontend/: NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652B/obs/obs.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(path.resolve(process.cwd(), "node_modules/@playwright/test"));

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const RUN = process.env.RUN || Date.now().toString(36).slice(-5).toUpperCase();
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
const addDays = (iso, n) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const n = parseInt(RUN, 36);
const A1 = 300 + (n % 900) / 100; // deposit, e.g. 304.17
const A2 = 400 + ((n >> 3) % 900) / 100; // transfer, e.g. 407.55
const DEP_REF = `DEP/652B-OBS/${RUN}`;
const TRF_REF = `TRF/652B-OBS/${RUN}`;
const CLIENT_B = `OBS652B Client ${RUN}`;
const MATTER_B = `OBS652B Matter ${RUN}`;
const result = { run: RUN, today, amounts: { deposit: A1, transfer: A2 }, depositRef: DEP_REF, transferRef: TRF_REF, clientB: CLIENT_B, matterB: MATTER_B, steps: {}, checks: {}, pageErrors: [], consoleErrors: [] };
const save = () => fs.writeFileSync(path.join(OUT, "obs-result.json"), JSON.stringify(result, null, 2));

function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psql("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const sql = (q) => {
  const text = q.replace(/\bT\./g, `${SCHEMA}.`);
  const out = psql(text);
  (result.queries ||= []).push({ q: text, out });
  return out;
};
const shot = (page, name) => page.screenshot({ path: path.join(OUT, name), fullPage: true });

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  const [clientAId, clientA, matterAId, matterA] = sql(
    "select c.id, c.name, p.id, p.name from T.customers c join T.projects p on p.customer_id=c.id where c.name='Acme Corp' order by p.created_at limit 1",
  ).split("|");
  Object.assign(result, { schema: SCHEMA, accountId, clientA, matterA });

  const csv = path.join(OUT, `obs-${RUN}.csv`);
  fs.writeFileSync(
    csv,
    [
      `Standard Bank Trust Account - 652B OBS ${RUN}`,
      "Date,Description,Reference,Amount,Balance",
      `${addDays(today, -2)},Payment out,BANK/652B-OBS/${RUN}/P,-1250.00,-1250.00`,
      `${today},Deposit ${clientA},BANK/652B-OBS/${RUN}/D,${A1.toFixed(2)},${(A1 - 1250).toFixed(2)}`,
      `${today},Transfer credit,BANK/652B-OBS/${RUN}/TI,${A2.toFixed(2)},${(A1 - 1250 + A2).toFixed(2)}`,
      `${today},Transfer debit,BANK/652B-OBS/${RUN}/TO,-${A2.toFixed(2)},${(A1 - 1250).toFixed(2)}`,
    ].join("\n") + "\n",
  );

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push(String(e)));
  page.on("console", (m) => m.type() === "error" && result.consoleErrors.push(m.text().slice(0, 300)));
  try {
    await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
    await page.waitForURL(/\/org\//, { timeout: 60_000 });

    // --- Setup 1: deposit A1 (UI) ---
    if (!sql(`select id from T.trust_transactions where reference='${DEP_REF}'`)) {
      await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
      await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
      await page.getByTestId("record-transaction-button").click();
      await page.getByRole("menuitem", { name: "Record Deposit" }).click();
      const dialog = page.getByRole("dialog", { name: "Record Deposit" });
      await dialog.waitFor();
      await dialog.getByTestId("trust-deposit-customer-trigger").click();
      await page.getByRole("option", { name: new RegExp(`^${clientA}(\\s|$)`) }).click();
      await dialog.getByTestId("trust-deposit-matter-trigger").click();
      await page.getByRole("option", { name: new RegExp(matterA) }).click();
      await dialog.getByLabel("Amount").fill(A1.toFixed(2));
      await dialog.getByLabel("Reference").fill(DEP_REF);
      await dialog.getByLabel(/Description/).fill(`652B OBS ${RUN}`);
      await dialog.getByLabel("Transaction Date").fill(addDays(today, -2));
      await dialog.getByRole("button", { name: "Record Deposit" }).click();
      await dialog.waitFor({ state: "hidden", timeout: 15_000 });
      const row = page.locator("main tbody tr", { hasText: DEP_REF });
      await row.waitFor({ timeout: 15_000 });
      result.steps.depositRow = (await row.innerText()).replace(/\s+/g, " ");
      await shot(page, "obs-1-deposit-recorded.png");
    }
    result.steps.depositDb = sql(`select id, transaction_type, status, amount, transaction_date, coalesce(bank_statement_line_id::text,'NULL') from T.trust_transactions where reference='${DEP_REF}'`);
    const depositId = result.steps.depositDb.split("|")[0];
    save();

    // --- Setup 2: client B, onboarding, matter B (UI) ---
    if (!sql(`select id from T.customers where name='${CLIENT_B}'`)) {
      await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: "load" });
      await page.getByRole("button", { name: "New Client" }).click();
      const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
      await dialog.waitFor();
      await dialog.getByLabel("Name", { exact: true }).fill(CLIENT_B);
      const typeSel = dialog.getByLabel("Type", { exact: true });
      const opt = typeSel.locator("option", { hasText: "Individual" }).first();
      await opt.waitFor({ state: "attached", timeout: 15_000 });
      await typeSel.selectOption(await opt.getAttribute("value"));
      await dialog.getByLabel("Email", { exact: true }).fill(`obs652b-${RUN.toLowerCase()}@example.com`);
      await dialog.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
      await dialog.locator('input[name="city"]').fill("Sandton");
      await dialog.locator('input[name="postalCode"]').fill("2196");
      await dialog.getByLabel(/^Country/).selectOption("ZA");
      await dialog.getByRole("button", { name: "Next" }).click();
      await dialog.getByText("Step 2 of 2").waitFor({ timeout: 15_000 });
      await dialog.getByRole("button", { name: "Create Client" }).click();
      await page.getByRole("heading", { level: 1, name: CLIENT_B }).waitFor({ timeout: 30_000 });
    }
    const clientBId = sql(`select id from T.customers where name='${CLIENT_B}'`);
    if (sql(`select lifecycle_status from T.customers where id='${clientBId}'`) === "PROSPECT") {
      await page.goto(`${BASE}/org/${SLUG}/clients/${clientBId}`, { waitUntil: "load" });
      await page.getByRole("heading", { level: 1, name: CLIENT_B }).waitFor({ timeout: 30_000 });
      await page.getByRole("button", { name: "Start Onboarding" }).first().click();
      const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
      await confirm.getByRole("button", { name: "Start Onboarding" }).click();
      await confirm.waitFor({ state: "hidden", timeout: 15_000 });
      await page.getByTestId("lifecycle-badge").getByText("Onboarding").waitFor({ timeout: 15_000 });
    }
    if (!sql(`select id from T.projects where name='${MATTER_B}'`)) {
      await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: "load" });
      await page.getByRole("button", { name: "New Matter" }).click();
      const md = page.getByRole("dialog", { name: "Create Matter" });
      await md.waitFor();
      await md.getByLabel("Name", { exact: true }).fill(MATTER_B);
      const csel = md.getByLabel(/^Client/);
      const copt = csel.locator("option", { hasText: CLIENT_B }).first();
      await copt.waitFor({ state: "attached", timeout: 15_000 });
      await csel.selectOption(await copt.getAttribute("value"));
      await md.getByLabel(/^Work Type/).fill("LITIGATION");
      await md.getByRole("button", { name: "Create Matter" }).click();
      await md.waitFor({ state: "hidden", timeout: 15_000 });
    }
    const matterBId = sql(`select id from T.projects where name='${MATTER_B}'`);
    result.steps.clientB = sql(`select id, name, lifecycle_status from T.customers where id='${clientBId}'`);
    result.steps.matterB = sql(`select id, name, customer_id from T.projects where id='${matterBId}'`);
    save();

    // --- Setup 3: transfer A2 Acme -> client B (UI, Record Transfer dialog) ---
    if (!sql(`select id from T.trust_transactions where reference='${TRF_REF}' limit 1`)) {
      await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
      await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
      await page.getByTestId("record-transaction-button").click();
      await page.getByRole("menuitem", { name: "Record Transfer" }).click();
      const td = page.getByRole("dialog", { name: "Record Transfer" });
      await td.waitFor();
      await td.getByLabel("Source Client ID").fill(clientAId);
      await td.getByLabel("Target Client ID").fill(clientBId);
      await td.getByLabel("Source Matter").fill(matterAId);
      await td.getByLabel("Target Matter").fill(matterBId);
      await td.getByLabel("Amount").fill(A2.toFixed(2));
      await td.getByLabel("Reference").fill(TRF_REF);
      await td.getByLabel(/Description/).fill(`652B OBS transfer ${RUN}`);
      await td.getByLabel("Transaction Date").fill(addDays(today, -1));
      await shot(page, "obs-2a-transfer-dialog.png");
      await td.getByRole("button", { name: "Record Transfer" }).click();
      await td.waitFor({ state: "hidden", timeout: 15_000 });
      await page.locator("main tbody tr", { hasText: TRF_REF }).first().waitFor({ timeout: 15_000 });
      result.steps.transferRows = await page.locator("main tbody tr", { hasText: TRF_REF }).evaluateAll((els) => els.map((e) => e.innerText.replace(/\s+/g, " ")));
      await shot(page, "obs-2b-transfer-recorded.png");
    }
    result.steps.transferDb = sql(`select id, transaction_type, status, amount, transaction_date, coalesce(bank_statement_line_id::text,'NULL') from T.trust_transactions where reference='${TRF_REF}' order by transaction_type`).split("\n");
    const transferIds = result.steps.transferDb.map((r) => r.split("|")[0]);
    save();

    // --- Wizard: period end today, upload, auto-match, review ---
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
    await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
    await page.getByTestId("account-select").selectOption(accountId);
    await page.getByTestId("period-end-input").fill(today);
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
    const lines = sql(`select id, amount, reference, match_status, coalesce(trust_transaction_id::text,'NULL') from T.bank_statement_lines where bank_statement_id='${stmt}' order by reference`).split("\n");
    result.steps.linesAfterAutoMatch = lines;
    const lineId = (suffix) => lines.find((l) => l.split("|")[2].endsWith(`/${suffix}`)).split("|")[0];
    const [lineD, lineP, lineTI, lineTO] = ["D", "P", "TI", "TO"].map(lineId);

    const listedIds = async () =>
      page.locator('[data-testid^="transaction-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid").replace("transaction-", "")));
    const listed = await listedIds();
    result.steps.paneListed = listed.length
      ? sql(`select id, transaction_type, status, amount, reference from T.trust_transactions where id in (${listed.map((id) => `'${id}'`).join(",")}) order by transaction_date desc`).split("\n")
      : [];
    result.steps.dbUnlinkedPosted = sql(
      `select transaction_type, status, count(*) from T.trust_transactions where trust_account_id='${accountId}' and bank_statement_line_id is null and status in ('RECORDED','APPROVED') group by 1,2 order by 1,2`,
    ).split("\n");

    // (c) transfer legs: not listed; no Match on either A2 line
    const matchBtnCount = async (ids) => {
      let c = 0;
      for (const id of ids) c += await page.getByTestId(`match-btn-${id}`).count();
      return c;
    };
    await page.getByTestId(`bank-line-${lineTI}`).click();
    await page.waitForTimeout(400);
    const tiMatch = await matchBtnCount(transferIds);
    const tiAnyMatch = await page.locator('[data-testid^="match-btn-"]').count();
    await shot(page, "obs-5-transfer-line-selected.png");
    await page.getByTestId(`bank-line-${lineTO}`).click();
    await page.waitForTimeout(400);
    const toMatch = await matchBtnCount(transferIds);
    const toAnyMatch = await page.locator('[data-testid^="match-btn-"]').count();
    result.checks.c = {
      transferLegsListed: transferIds.filter((id) => listed.includes(id)),
      matchBtnsForTransferLegs_onCreditLine: tiMatch,
      matchBtnsAny_onCreditLine: tiAnyMatch,
      matchBtnsForTransferLegs_onDebitLine: toMatch,
      matchBtnsAny_onDebitLine: toAnyMatch,
    };
    result.checks.c.pass = transferIds.length === 2 && result.checks.c.transferLegsListed.length === 0 && tiMatch + toMatch === 0;

    // (d) APPROVED unlinked payments listed; Match offered on the 1250 line (not clicked)
    await page.getByTestId(`bank-line-${lineP}`).click();
    await page.waitForTimeout(400);
    const approvedListed = result.steps.paneListed.filter((r) => r.split("|")[2] === "APPROVED");
    const paymentMatchBtns = await page.locator('[data-testid^="match-btn-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid").replace("match-btn-", "")));
    result.checks.d = {
      approvedListed,
      matchBtnsOn1250Line: paymentMatchBtns.length ? sql(`select id, transaction_type, status, amount, reference from T.trust_transactions where id in (${paymentMatchBtns.map((id) => `'${id}'`).join(",")})`).split("\n") : [],
    };
    result.checks.d.pass = approvedListed.length > 0 && result.checks.d.matchBtnsOn1250Line.some((r) => r.includes("|APPROVED|"));
    await shot(page, "obs-6-approved-payment-offered.png");

    // (a) deposit listed; Match on the A1 line
    await page.getByTestId(`bank-line-${lineD}`).click();
    await page.waitForTimeout(400);
    result.checks.a = {
      depositListed: listed.includes(depositId),
      depositRowText: listed.includes(depositId) ? (await page.getByTestId(`transaction-${depositId}`).innerText()).replace(/\s+/g, " ") : null,
      matchBtnForDeposit: await page.getByTestId(`match-btn-${depositId}`).count(),
      lineText: (await page.getByTestId(`bank-line-${lineD}`).innerText()).replace(/\s+/g, " "),
    };
    result.checks.a.pass = result.checks.a.depositListed && result.checks.a.matchBtnForDeposit === 1 && result.steps.depositDb.includes("|RECORDED|");
    await shot(page, "obs-3-recorded-deposit-offered.png");
    save();

    // (b) click Match
    await page.getByTestId(`match-btn-${depositId}`).click();
    await page.getByTestId(`transaction-${depositId}`).waitFor({ state: "detached", timeout: 30_000 });
    await page.waitForTimeout(1000);
    result.checks.b = {
      depositStillListed: (await listedIds()).includes(depositId),
      lineText: (await page.getByTestId(`bank-line-${lineD}`).innerText()).replace(/\s+/g, " "),
      matchProgress: (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " "),
      errorBanner: await page.getByTestId("reconciliation-error").count(),
      staleBanner: await page.getByTestId("reconciliation-figures-stale").count(),
      dbTransaction: sql(`select id, status, coalesce(bank_statement_line_id::text,'NULL') from T.trust_transactions where id='${depositId}'`),
      dbLine: sql(`select id, match_status, coalesce(trust_transaction_id::text,'NULL'), match_confidence from T.bank_statement_lines where id='${lineD}'`),
    };
    result.checks.b.pass =
      !result.checks.b.depositStillListed &&
      /matched/i.test(result.checks.b.lineText) &&
      result.checks.b.dbTransaction.endsWith(`|${lineD}`) &&
      result.checks.b.dbLine.includes("|MANUALLY_MATCHED|") &&
      result.checks.b.dbLine.includes(depositId) &&
      result.checks.b.errorBanner === 0;
    await shot(page, "obs-4-deposit-matched.png");
    Object.assign(result, { depositId, transferIds, statementId: stmt, lineIds: { lineD, lineP, lineTI, lineTO } });
  } catch (e) {
    result.fatal = String(e);
    await shot(page, "obs-fail.png").catch(() => {});
  }
  save();
  await browser.close();
  console.log(JSON.stringify(result, (k, v) => (k === "queries" ? undefined : v), 2));
  if (result.fatal) process.exit(1);
})();
