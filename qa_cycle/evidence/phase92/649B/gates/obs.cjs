// 649B OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice / Bob via /mock-login). psql is read-only EVIDENCE.
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-649B/frontend/node_modules/@playwright/test"
);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const EV = "/tmp/649B-evidence";
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [] };

// ── dates (SAST business dates) ─────────────────────────────────────
const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const D = {
  today: sastToday,
  deposit: addDays(sastToday, -15),
  payment: addDays(sastToday, -10),
  beforeOriginal: addDays(sastToday, -11),
  between: addDays(sastToday, -7),
  reversal: addDays(sastToday, -5),
  future: addDays(sastToday, 3),
  beforePaymentDate: addDays(sastToday, -12),
};
result.dates = D;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const REF = {
  apexDeposit: `OBS649B-DEP-${RUN}`,
  apexPayment: `OBS649B-PAY-${RUN}`,
};
result.refs = REF;
const REASON_A = `OBS649B reason A — bank returned the cheque ${RUN}`;
const REASON_B = `OBS649B reason B — payee bank rejected the EFT ${RUN}`;
const REASON_BONUS = `OBS649B reason bonus — Moroka deposit ${RUN}`;
result.reasons = { A: REASON_A, B: REASON_B, bonus: REASON_BONUS };

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q],
    { encoding: "utf8" }
  );
}
function psqlA(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q],
    { encoding: "utf8" }
  ).trim();
}
const SCHEMA = psqlA(
  "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"
);
fs.writeFileSync(SQL_FILE, `-- 649B OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 649B OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
function sql(label, q) {
  const qq = q.replace(/\bT\./g, `${SCHEMA}.`);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
function val(q) {
  return psqlA(q.replace(/\bT\./g, `${SCHEMA}.`));
}
function customerId(prefix) {
  return val(`select id from T.customers where name like '${prefix}%' order by created_at limit 1`);
}
function clientBal(cid, asAt) {
  const d = asAt ? ` and transaction_date <= '${asAt}'` : "";
  return val(
    `select coalesce(sum(ledger_effect),0) from T.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='${cid}'${d}`
  );
}
function eq2(label) {
  const out = sql(
    `${label} EQ-2`,
    `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference`
  );
  const row = val(
    `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null)||'|'||
            (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT')`
  ).split("|");
  return { cashbook: row[0], sumClient: row[1], diff: (Number(row[0]) - Number(row[1])).toFixed(2), out };
}
function txRows(label, where) {
  return sql(
    label,
    `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from T.trust_transactions where ${where} order by created_at`
  );
}

// ── browser helpers ─────────────────────────────────────────────────
let browser, context, page;
async function newSession(userId) {
  if (context) await context.close();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  page = await context.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") result.consoleErrors.push({ user: userId, url: page.url(), text: m.text() });
  });
  page.on("pageerror", (e) => result.pageErrors.push({ user: userId, url: page.url(), text: String(e) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(userId);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  await page.getByRole("heading").first().waitFor({ timeout: 30_000 });
}
let shot = 0;
async function snap(name) {
  shot += 1;
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await page.waitForTimeout(800);
  await page.screenshot({ path: file, fullPage: true });
  const text = await page.locator("body").innerText().catch(() => "");
  fs.writeFileSync(file.replace(/\.png$/, ".txt"), text);
  return { file, text };
}
async function openTransactions() {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 30_000 });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
}
function rowById(id) {
  return page.getByTestId(`transaction-row-${id}`);
}
async function rowText(id) {
  return (await rowById(id).innerText().catch(() => "<row not on page>")).replace(/\s+/g, " ").trim();
}
async function ledgerListBalance(cid) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/client-ledgers`, { waitUntil: "load" });
  await page.getByTestId("client-ledgers-page").waitFor({ timeout: 30_000 });
  const row = page.getByTestId(`ledger-row-${cid}`);
  await row.waitFor({ timeout: 15_000 });
  const cells = await row.locator("td").allInnerTexts();
  return cells.map((c) => c.trim());
}
async function ledgerDetail(cid, name) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/client-ledgers/${cid}`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 30_000 });
  const s = await snap(name);
  return s;
}
async function overviewCards(name) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 30_000 });
  const cards = await page.locator('[data-testid="summary-cards"]').innerText().catch(() => null);
  await snap(name);
  return cards;
}
async function openReverseDialog(id) {
  const action = page.getByTestId(`reversal-action-${id}`);
  await action.getByTestId("reverse-button").click();
  const dialog = page.getByRole("dialog", { name: "Reverse Transaction" });
  await dialog.waitFor({ timeout: 15_000 });
  return dialog;
}
async function approve(id) {
  const actions = page.getByTestId(`approval-actions-${id}`);
  await actions.getByRole("button", { name: "Approve" }).click();
  await page.waitForTimeout(2500);
  return (await actions.innerText().catch(() => "")).replace(/\s+/g, " ").trim();
}
async function pickOption(triggerTestId, dialog, re) {
  await dialog.getByTestId(triggerTestId).click();
  await page.getByRole("option", { name: re }).first().click();
}

async function submitReverse(dialog, { reason, date }) {
  await dialog.getByTestId("reversal-reason-input").fill(reason);
  if (date !== undefined) await dialog.getByTestId("reversal-date-input").fill(date);
  await dialog.getByTestId("confirm-reverse-button").click();
  const hidden = await dialog
    .waitFor({ state: "hidden", timeout: 15_000 })
    .then(() => true)
    .catch(() => false);
  const err = hidden ? null : await dialog.locator("p.text-destructive").innerText().catch(() => null);
  return { closed: hidden, error: err };
}

async function recordViaDialog(menu, dialogName, fields) {
  await openTransactions();
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: menu }).click();
  const dialog = page.getByRole("dialog", { name: dialogName });
  await dialog.waitFor({ timeout: 15_000 });
  await pickOption(fields.customerTrigger, dialog, fields.customerRe);
  await pickOption(fields.matterTrigger, dialog, fields.matterRe);
  await dialog.getByLabel("Amount").fill(fields.amount);
  await dialog.getByLabel("Reference").fill(fields.reference);
  await dialog.getByLabel(/Description/).fill(fields.description);
  await dialog.getByLabel("Transaction Date").fill(fields.date);
  await snap(`${fields.shot}-dialog`);
  await dialog.getByRole("button", { name: dialogName }).click();
  const hidden = await dialog
    .waitFor({ state: "hidden", timeout: 20_000 })
    .then(() => true)
    .catch(() => false);
  const err = hidden ? null : await dialog.locator("p.text-destructive").innerText().catch(() => "<no error text>");
  return { closed: hidden, error: err };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch();
  const ids = {};
  ids.qc = customerId("QuickCollect Services");
  ids.moroka = customerId("Moroka Family Trust");
  ids.apex = customerId("Apex Holdings");
  ids.qcDeposit = val(
    `select id from T.trust_transactions where customer_id='${ids.qc}' and transaction_type='DEPOSIT' and status='RECORDED' order by created_at limit 1`
  );
  ids.morokaDeposit = val(
    `select id from T.trust_transactions where customer_id='${ids.moroka}' and transaction_type='DEPOSIT' and status='RECORDED' order by created_at limit 1`
  );
  result.ids = ids;
  sql("baseline: customers", `select id, name from T.customers order by created_at`);
  txRows("baseline: all trust transactions", "true");
  result.baselineEq2 = eq2("baseline");

  // ════ a. Reverse a RECORDED deposit (QuickCollect R45 000) — approval needed ════
  const a = (result.steps.a = {});
  await newSession("user_e2e_alice");
  a.balanceBefore = { sql: clientBal(ids.qc), uiListRow: await ledgerListBalance(ids.qc) };
  await openTransactions();
  a.originalRowBefore = await rowText(ids.qcDeposit);
  a.reverseOfferedOnRecordedDeposit = await page.getByTestId(`reversal-action-${ids.qcDeposit}`).isVisible();
  await snap("a1-transactions-before");
  let dlg = await openReverseDialog(ids.qcDeposit);
  a.dialogText = (await dlg.innerText()).replace(/\s+/g, " ").trim();
  a.dialogHasRawEnum = /NEEDS_APPROVAL|IMMEDIATE|VIA_PAIR|AWAITING_APPROVAL|\bNONE\b/.test(a.dialogText);
  a.dialogHasApprovalNote = /approved by another member/i.test(a.dialogText);
  a.dateInputMin = await dlg.getByTestId("reversal-date-input").getAttribute("min");
  await snap("a2-reverse-dialog-deposit");
  a.submit = await submitReverse(dlg, { reason: REASON_A });
  ids.qcContra = val(`select id from T.trust_transactions where reversal_of='${ids.qcDeposit}'`);
  await openTransactions();
  a.contraRowPending = await rowText(ids.qcContra);
  a.originalRowWhilePending = await rowText(ids.qcDeposit);
  a.reverseStillOfferedWhileContraPending = await page
    .getByTestId(`reversal-action-${ids.qcDeposit}`)
    .isVisible()
    .catch(() => false);
  await snap("a3-contra-awaiting-approval");
  txRows("a: after Alice submits (pending contra)", `id in ('${ids.qcDeposit}','${ids.qcContra}')`);
  a.balanceWhilePending = { sql: clientBal(ids.qc), uiListRow: await ledgerListBalance(ids.qc) };
  a.eq2WhilePending = eq2("a: pending");
  // Alice (the contra's recorder) tries to approve her own reversal
  await openTransactions();
  a.aliceSelfApproval = await approve(ids.qcContra);
  await snap("a4-alice-self-approval-refused");

  await newSession("user_e2e_bob");
  await openTransactions();
  a.bobSeesApprove = await page.getByTestId(`approval-actions-${ids.qcContra}`).isVisible();
  a.bobApproveResult = await approve(ids.qcContra);
  await page.reload({ waitUntil: "load" });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
  a.contraRowApproved = await rowText(ids.qcContra);
  a.originalRowAfterApproval = await rowText(ids.qcDeposit);
  a.reverseOfferedAfterApproval = await page
    .getByTestId(`reversal-action-${ids.qcDeposit}`)
    .isVisible()
    .catch(() => false);
  await snap("a5-bob-approved-contra");
  txRows("a: after Bob approves", `id in ('${ids.qcDeposit}','${ids.qcContra}')`);
  a.balanceAfterApproval = { sql: clientBal(ids.qc), uiListRow: await ledgerListBalance(ids.qc) };
  await snap("a6-client-ledgers-after-approval");
  const qcDetail = await ledgerDetail(ids.qc, "a7-quickcollect-ledger");
  a.ledgerDetailHasRawReversal = /\bREVERSAL\b/.test(qcDetail.text);
  a.ledgerDetailHasReversalLabel = /Reversal/.test(qcDetail.text);
  a.eq2AfterApproval = eq2("a: approved");

  // ════ b. Back-dated payment, reversed with a past reversal date ════
  const b = (result.steps.b = {});
  await newSession("user_e2e_alice");
  b.deposit = await recordViaDialog("Record Deposit", "Record Deposit", {
    customerTrigger: "trust-deposit-customer-trigger",
    customerRe: /^Apex Holdings/,
    matterTrigger: "trust-deposit-matter-trigger",
    matterRe: /Apex/,
    amount: "20000",
    reference: REF.apexDeposit,
    description: "OBS 649B funding deposit",
    date: D.deposit,
    shot: "b1-apex-deposit",
  });
  ids.apexDeposit = val(`select id from T.trust_transactions where reference='${REF.apexDeposit}'`);
  b.payment = await recordViaDialog("Record Payment", "Record Payment", {
    customerTrigger: "trust-payment-customer-trigger",
    customerRe: /^Apex Holdings/,
    matterTrigger: "trust-payment-matter-trigger",
    matterRe: /Apex/,
    amount: "4000",
    reference: REF.apexPayment,
    description: "OBS 649B payee: Deeds Office",
    date: D.payment,
    shot: "b2-apex-payment",
  });
  ids.apexPayment = val(`select id from T.trust_transactions where reference='${REF.apexPayment}'`);
  await openTransactions();
  b.paymentRowRecorded = await rowText(ids.apexPayment);
  await snap("b3-payment-awaiting-approval");
  await newSession("user_e2e_bob");
  await openTransactions();
  b.bobApprovePayment = await approve(ids.apexPayment);
  await page.reload({ waitUntil: "load" });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
  b.paymentRowApproved = await rowText(ids.apexPayment);
  await snap("b4-payment-approved");
  txRows("b: Apex deposit + payment (approved)", `id in ('${ids.apexDeposit}','${ids.apexPayment}')`);
  b.balanceAfterPayment = { sql: clientBal(ids.apex) };

  await newSession("user_e2e_alice");
  await openTransactions();
  b.reverseOfferedOnApprovedPayment = await page.getByTestId(`reversal-action-${ids.apexPayment}`).isVisible();
  dlg = await openReverseDialog(ids.apexPayment);
  b.dialogText = (await dlg.innerText()).replace(/\s+/g, " ").trim();
  b.dialogHasRawEnum = /NEEDS_APPROVAL|IMMEDIATE|VIA_PAIR|AWAITING_APPROVAL|\bNONE\b/.test(b.dialogText);
  b.dateInputMin = await dlg.getByTestId("reversal-date-input").getAttribute("min");
  // (i) a date before the payment's own date — the picker's min
  await dlg.getByTestId("reversal-reason-input").fill(REASON_B);
  await dlg.getByTestId("reversal-date-input").fill(D.beforePaymentDate);
  await dlg.getByTestId("confirm-reverse-button").click();
  await page.waitForTimeout(2000);
  b.beforeOriginalAttempt = {
    date: D.beforePaymentDate,
    dialogStillOpen: await dlg.isVisible(),
    rangeUnderflow: await dlg.getByTestId("reversal-date-input").evaluate((el) => el.validity.rangeUnderflow),
    validationMessage: await dlg.getByTestId("reversal-date-input").evaluate((el) => el.validationMessage),
    errorText: await dlg.locator("p.text-destructive").innerText().catch(() => null),
    contraRows: val(`select count(*) from T.trust_transactions where reversal_of='${ids.apexPayment}'`),
  };
  await snap("b5-reverse-before-original-date-blocked");
  // (ii) a future date — refused by the server with a readable message
  b.futureAttempt = { date: D.future, ...(await submitReverse(dlg, { reason: REASON_B, date: D.future })) };
  b.futureAttempt.contraRows = val(`select count(*) from T.trust_transactions where reversal_of='${ids.apexPayment}'`);
  await snap("b6-reverse-future-date-refused");
  // (iii) the real reversal, dated 5 days ago
  b.pastAttempt = { date: D.reversal, ...(await submitReverse(dlg, { reason: REASON_B, date: D.reversal })) };
  ids.apexContra = val(`select id from T.trust_transactions where reversal_of='${ids.apexPayment}'`);
  await openTransactions();
  b.contraRow = await rowText(ids.apexContra);
  b.paymentRowAfterReversal = await rowText(ids.apexPayment);
  await snap("b7-payment-reversed");
  txRows("b: Apex payment + contra", `id in ('${ids.apexPayment}','${ids.apexContra}')`);
  b.contraTransactionDate = val(`select transaction_date from T.trust_transactions where id='${ids.apexContra}'`);
  b.contraDateMatches = b.contraTransactionDate === D.reversal;
  b.eq2 = eq2("b: after payment reversal");

  // ════ c. As-at balances + statement for the period starting between the dates ════
  const c = (result.steps.c = {});
  c.asAt = {
    [`beforeOriginal ${D.beforeOriginal}`]: clientBal(ids.apex, D.beforeOriginal),
    [`between ${D.between}`]: clientBal(ids.apex, D.between),
    [`today ${D.today}`]: clientBal(ids.apex, D.today),
  };
  sql(
    "c: Apex as-at client balances",
    ["beforeOriginal", "between", "today"]
      .map(
        (k) =>
          `select '${k}' as point, date '${D[k === "beforeOriginal" ? "beforeOriginal" : k]}' as as_at, coalesce(sum(ledger_effect),0) as client_balance from T.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='${ids.apex}' and transaction_date <= '${D[k]}'`
      )
      .join(" union all ")
  );
  const apexDetail = await ledgerDetail(ids.apex, "c1-apex-ledger");
  c.ledgerDetailHasRawReversal = /\bREVERSAL\b/.test(apexDetail.text);
  c.ledgerDetailShowsReasonFirmSide = apexDetail.text.includes(REASON_B);
  // Client ledger statement report (Reports → client-ledger-statement), period from "between" to today
  try {
    await page.goto(`${BASE}/org/${SLUG}/reports/client-ledger-statement`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Run Report" }).waitFor({ timeout: 30_000 });
    await snap("c2-statement-form");
    for (const [param, re, search] of [
      ["trust_account_id", /Trust Account/i, null],
      ["customer_id", /^Apex Holdings/, "Apex"],
    ]) {
      const trigger = page.locator(`#param-${param}`);
      if ((await trigger.evaluate((el) => el.tagName)) === "INPUT") {
        const v =
          param === "customer_id"
            ? ids.apex
            : val(`select id from T.trust_accounts order by created_at limit 1`);
        await trigger.fill(v);
      } else {
        await trigger.click();
        if (search) await page.getByPlaceholder(/search/i).last().fill(search).catch(() => {});
        await page.getByRole("option", { name: re }).first().click();
      }
    }
    await page.locator("#param-dateFrom").fill(D.between);
    await page.locator("#param-dateTo").fill(D.today);
    await page.getByRole("button", { name: "Run Report" }).click();
    await page.waitForTimeout(4000);
    const st = await snap("c3-statement-result");
    c.statementText = st.text.slice(st.text.indexOf("Run Report"));
    c.statementHasRawREVERSAL = /\bREVERSAL\b/.test(c.statementText);
    c.statementShowsReason = c.statementText.includes(REASON_B);
  } catch (e) {
    c.statementError = String(e);
    await snap("c3-statement-error").catch(() => {});
  }
  sql(
    "c: statement-equivalent rows (posted, CLIENT, Apex, between..today)",
    `select transaction_date, transaction_type, status, ledger_effect, reference, description from T.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='${ids.apex}' and transaction_date between '${D.between}' and '${D.today}' order by transaction_date, created_at`
  );

  // ════ d. Reason never on client surfaces (portal read model) ════
  const d = (result.steps.d = {});
  sql(
    "d: portal read model for QuickCollect + Apex",
    `select customer_id, transaction_type, amount, running_balance, transaction_date, reference, description from portal.portal_trust_transaction where customer_id in ('${ids.qc}','${ids.apex}') order by customer_id, transaction_date, recorded_at`
  );
  d.portalRowsWithReasonText = val(
    `select count(*) from portal.portal_trust_transaction where description like '%OBS649B reason%'`
  );
  d.portalReversalDescriptions = val(
    `select string_agg(coalesce(description,'<null>'), ' || ') from portal.portal_trust_transaction where transaction_type='REVERSAL' and customer_id in ('${ids.qc}','${ids.apex}')`
  );
  d.firmSideContraDescriptions = val(
    `select string_agg(description, ' || ') from T.trust_transactions where id in ('${ids.qcContra}','${ids.apexContra}')`
  );
  sql(
    "d: portal balances",
    `select customer_id, matter_id, current_balance, last_transaction_at from portal.portal_trust_balance where customer_id in ('${ids.qc}','${ids.apex}')`
  );

  // ════ Bonus: Moroka's R250 000 deposit — reversal refused on approval (balance 241 500) ════
  const x = (result.steps.bonusMoroka = {});
  await newSession("user_e2e_alice");
  x.balanceBefore = clientBal(ids.moroka);
  await openTransactions();
  x.reverseOffered = await page.getByTestId(`reversal-action-${ids.morokaDeposit}`).isVisible();
  dlg = await openReverseDialog(ids.morokaDeposit);
  x.submit = await submitReverse(dlg, { reason: REASON_BONUS });
  ids.morokaContra = val(`select id from T.trust_transactions where reversal_of='${ids.morokaDeposit}'`);
  await newSession("user_e2e_bob");
  await openTransactions();
  x.bobApproveResult = await approve(ids.morokaContra);
  await snap("x1-moroka-reversal-approval-refused");
  await page.reload({ waitUntil: "load" });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
  x.contraRowAfter = await rowText(ids.morokaContra);
  x.originalRowAfter = await rowText(ids.morokaDeposit);
  txRows("bonus: Moroka deposit + refused contra", `id in ('${ids.morokaDeposit}','${ids.morokaContra}')`);
  x.balanceAfter = clientBal(ids.moroka);

  // ════ Final: overview + client ledgers UI vs SQL, EQ-2, invariants ════
  const f = (result.steps.final = {});
  f.overviewCards = await overviewCards("f1-trust-overview");
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/client-ledgers`, { waitUntil: "load" });
  await page.getByTestId("client-ledgers-page").waitFor({ timeout: 30_000 });
  f.clientLedgerRows = await page.locator('tr[data-testid^="ledger-row-"]').evaluateAll((trs) =>
    trs.map((tr) => ({
      clientId: tr.getAttribute("data-testid").replace("ledger-row-", ""),
      cells: Array.from(tr.querySelectorAll("td")).map((td) => td.innerText.trim()),
    }))
  );
  await snap("f2-client-ledgers");
  f.sqlClientBalances = sql(
    "final: per-client derived balances",
    `select c.name, coalesce(sum(t.ledger_effect),0) as balance from T.trust_transactions t join T.customers c on c.id=t.customer_id where t.ledger='CLIENT' and t.posted_at is not null group by c.name order by c.name`
  );
  f.eq2 = eq2("final");
  sql(
    "final: invariants (all expected 0)",
    `select
      (select count(*) from T.trust_transactions where status in ('RECORDED','APPROVED','REVERSED') and posted_at is null) as posted_status_unposted,
      (select count(*) from T.trust_transactions where status in ('AWAITING_APPROVAL','REJECTED') and (posted_at is not null or ledger_effect<>0 or cashbook_effect<>0)) as pending_with_effect,
      (select count(*) from T.trust_transactions where posted_at is not null and (abs(ledger_effect)<>amount or (cashbook_effect<>ledger_effect and cashbook_effect<>0))) as bad_shape,
      (select count(*) from T.trust_transactions o where o.status='REVERSED' and (o.reversed_by_id is null or not exists (select 1 from T.trust_transactions r where r.id=o.reversed_by_id and r.reversal_of=o.id and r.posted_at is not null))) as reversed_without_posted_contra,
      (select count(*) from T.trust_transactions r join T.trust_transactions o on o.id=r.reversal_of where r.posted_at is not null and (r.ledger_effect <> -o.ledger_effect or r.cashbook_effect <> -o.cashbook_effect)) as contra_not_negation,
      (select count(*) from T.trust_transactions r join T.trust_transactions o on o.id=r.reversal_of where r.posted_at is not null and (o.ledger_effect + r.ledger_effect) <> 0) as pair_not_net_zero`
  );
  txRows("final: all trust transactions", "true");

  result.ids = ids;
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (e) => {
  console.error("OBS FAILED:", e);
  try {
    await snap("zz-fatal");
  } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  process.exit(1);
});
