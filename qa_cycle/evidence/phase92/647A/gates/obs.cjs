// 647A OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice, owner, via /mock-login): statement upload, auto-match,
// record from statement x5, unmatch x1. psql is read-only EVIDENCE. API reads use the browser context's
// own mock-auth-token against :8081 (the frontend calls the backend from server actions, so a page
// network capture cannot see them). The one API write is the (d) probe that sends a sign-invalid
// classification the dialog never offers; it must be refused (400) and create nothing.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647A";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/647A/gates`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [], startedAt: new Date().toISOString() };

const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;
// The statement: five lines, today-7 .. today-3; the period end is the last line's date.
const LINES = [
  { n: 1, date: addDays(sastToday, -7), desc: "Interest credit", ref: `INT/OBS647A/${RUN}`, amount: 1234.56, cls: "FUND_INTEREST" },
  { n: 2, date: addDays(sastToday, -6), desc: "Fidelity Fund interest transfer", ref: `LPFF/OBS647A/${RUN}`, amount: -1119.56, cls: "FUND_SWEEP" },
  { n: 3, date: addDays(sastToday, -5), desc: "Monthly service fee", ref: `SVC/OBS647A/${RUN}`, amount: -57.5, cls: "BANK_CHARGE", recoverable: true, vat: "7.50" },
  { n: 4, date: addDays(sastToday, -4), desc: "Fee refund from business account", ref: `RFD/OBS647A/${RUN}`, amount: 115.0, cls: "CHARGE_REFUND" },
  { n: 5, date: addDays(sastToday, -3), desc: "Cash deposit fee", ref: `CDF/OBS647A/${RUN}`, amount: -115.0, cls: "BANK_CHARGE", recoverable: false, vat: "0" },
];
// The posting table: cashbook and Fund-ledger effect of each classification (sign of |L|).
const EFFECT = { FUND_INTEREST: 1, FUND_SWEEP: -1, BANK_CHARGE: -1, CHARGE_REFUND: 1 };
const D = { today: sastToday, before: addDays(sastToday, -8), periodEnd: LINES[4].date };
for (const l of LINES) D[`line${l.n}`] = l.date;
result.dates = D;
result.lines = LINES;

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.writeFileSync(SQL_FILE, `-- 647A OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 647A OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
const T = (q) => q.replace(/\bT\./g, `${SCHEMA}.`);
function sql(label, q) {
  const qq = T(q);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(T(q));
const rowsA = (q) => (val(q) || "").split("\n").filter(Boolean).map((l) => l.split("|"));

// TrustLedgerQueryRepository.position (TrustBooksService.position): posted rows dated <= asAt
function positionSql(acct, asAt) {
  return `select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from T.trust_transactions t left join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='${acct}' and t.posted_at is not null and t.transaction_date <= date '${asAt}'`;
}
function sqlPosition(label, acct, asAt) {
  sql(`${label}: position as at ${asAt}`, positionSql(acct, asAt));
  const [cashbook, client, inv, fund, unid, other, inter, all, n] = rowsA(positionSql(acct, asAt))[0].map(Number);
  const creditors = +(client + inv + fund + unid + other + inter).toFixed(2);
  return { asAt, cashbook, clientLedgers: client, investmentCreditors: inv, fundInterestAndCharges: fund,
    unidentifiedDeposits: unid, otherNonClient: other, interAccount: inter, creditorTotal: creditors,
    allLedgerEffects: all, postedRows: n, difference: +(cashbook - creditors).toFixed(2) };
}
const eq = (x, y) => x != null && y != null && Math.abs(Number(x) - Number(y)) < 0.005;

// ── browser helpers ─────────────────────────────────────────────────
let browser, context, page, page2;
function watch(p, tag) {
  p.on("console", (m) => {
    if (m.type() === "error") result.consoleErrors.push({ tab: tag, url: p.url(), text: m.text() });
  });
  p.on("pageerror", (e) => result.pageErrors.push({ tab: tag, url: p.url(), text: String(e) }));
}
async function newSession(userId) {
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  page = await context.newPage();
  watch(page, "wizard");
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(userId);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  await page.getByRole("heading").first().waitFor({ timeout: 30_000 });
  page2 = await context.newPage(); // second tab: Other trust creditors (the wizard state lives in tab 1)
  watch(page2, "creditors");
}
let shot = 0;
async function snap(p, name) {
  shot += 1;
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await p.waitForTimeout(600);
  await p.screenshot({ path: file, fullPage: true });
  const text = await p.locator("body").innerText().catch(() => "");
  fs.writeFileSync(file.replace(/\.png$/, ".txt"), text);
  return file.replace(`${OUT}/`, "");
}
async function crop(locator, name) {
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await locator.screenshot({ path: file }).catch(() => {});
  return file.replace(`${OUT}/`, "");
}
async function token() {
  const tok = (await context.cookies()).find((c) => c.name === "mock-auth-token");
  return tok ? tok.value : "";
}
async function api(path, save) {
  const res = await context.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token()}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { path, status: res.status(), body };
  if (save) fs.writeFileSync(`${OUT}/api-${save}.json`, JSON.stringify(r, null, 2));
  return r;
}
async function apiPost(path, data, save) {
  const res = await context.request.post(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" }, data });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 800));
  const r = { path, request: data, status: res.status(), body };
  if (save) fs.writeFileSync(`${OUT}/api-${save}.json`, JSON.stringify(r, null, 2));
  return r;
}
const money = (s) => {
  if (s == null) return null;
  const neg = /[-−]/.test(s);
  const n = Number(s.replace(/[^\d,]/g, "").replace(",", "."));
  return neg ? -n : n;
};

// Other trust creditors page (tab 2): Fund ledger figure, UI = API = SQL
async function creditorsPage(label, acct) {
  await page2.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
  await page2.getByTestId("other-creditors-page").waitFor({ timeout: 60_000 });
  const card = page2.getByTestId("creditor-FUND_INTEREST_AND_CHARGES");
  await card.waitFor({ timeout: 30_000 });
  const text = (await card.innerText()).replace(/\s+/g, " ").trim();
  const m = text.match(/R\s?-?[\d\s  ]+,\d{2}/);
  const ui = m ? money(m[0]) * (/in debit/.test(text) && !/-/.test(m[0]) ? 1 : 1) : null;
  const file = await snap(page2, `${label}-other-creditors`);
  await crop(card, `${label}-other-creditors-fund-card`);
  const apiR = await api(`/api/trust-accounts/${acct}/creditor-accounts`, `${label}-creditor-accounts`);
  const fundApi = Array.isArray(apiR.body) ? apiR.body.find((a) => a.accountType === "FUND_INTEREST_AND_CHARGES") : null;
  const q = sqlPosition(`${label} (today)`, acct, D.today);
  return { cardText: text, ui, api: fundApi, sqlFund: q.fundInterestAndCharges, shot: file,
    match: eq(ui, fundApi?.balance) && eq(fundApi?.balance, q.fundInterestAndCharges) };
}
// Equation as at a date: API cashbook-balance?asAt (balance) + total-balance?asAt (creditorBreakdown) = SQL
// (CashbookBalanceResponse carries only `balance`; the creditor breakdown is on total-balance — O-1)
async function equationAt(label, acct, asAt) {
  const r = await api(`/api/trust-accounts/${acct}/cashbook-balance?asAt=${asAt}`, `${label}-cashbook-balance-asAt-${asAt}`);
  const t = await api(`/api/trust-accounts/${acct}/total-balance?asAt=${asAt}`, `${label}-total-balance-asAt-${asAt}`);
  const q = sqlPosition(`${label}`, acct, asAt);
  const cb = (r.body || {}).balance;
  const br = (t.body || {}).creditorBreakdown || {};
  return { asAt, status: [r.status, t.status], apiCashbook: cb, apiCreditorTotal: br.total, apiFund: br.fundInterestAndCharges,
    apiClientLedgers: br.clientLedgers, sqlCashbook: q.cashbook, sqlCreditorTotal: q.creditorTotal, sqlFund: q.fundInterestAndCharges,
    sqlClientLedgers: q.clientLedgers, sqlDifference: q.difference,
    match: eq(cb, q.cashbook) && eq(br.total, q.creditorTotal) && eq(br.fundInterestAndCharges, q.fundInterestAndCharges),
    equationHolds: eq(q.cashbook, q.creditorTotal) && eq(cb, br.total) };
}
async function summaryPairs() {
  return page.getByTestId("balance-summary").locator("dl dt").evaluateAll((dts) =>
    dts.map((dt) => [dt.innerText.trim(), dt.nextElementSibling ? dt.nextElementSibling.innerText.trim() : null]));
}
const lineSql = (stmt) => `select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from T.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from T.bank_statement_lines l where l.bank_statement_id='${stmt}' order by l.line_number`;
const rowSql = (where) => `select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from T.trust_transactions t left join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id where ${where} order by t.created_at`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = (result.ids = {});
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  result.account = rowsA(`select id, account_name, bank_name, account_number, is_primary, status from T.trust_accounts`);
  ids.fundAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='FUND_INTEREST_AND_CHARGES'`);
  sql("baseline: creditor accounts", `select id, trust_account_id, account_type, name from T.trust_creditor_accounts`);
  sql("baseline: trust transactions", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference from T.trust_transactions order by transaction_date, created_at`);
  sql("baseline: statements / recons", `select (select count(*) from T.bank_statements) statements, (select count(*) from T.bank_statement_lines) lines, (select count(*) from T.trust_reconciliations) recons`);
  const txCount = () => Number(val(`select count(*) from T.trust_transactions`));

  // the CSV (Standard Bank layout: 2 header lines; Date,Description,Reference,Amount,Balance; yyyy-MM-dd)
  let bal = 0;
  const csv = [`Standard Bank Trust Account - OBS 647A ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of LINES) { bal = +(bal + l.amount).toFixed(2); csv.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
  const csvFile = `${OUT}/statement-OBS647A-${RUN}.csv`;
  fs.writeFileSync(csvFile, csv.join("\n") + "\n");
  result.csv = csv;

  await newSession("user_e2e_alice");

  // ── baseline: Other trust creditors + equation ──
  const base = (result.steps.baseline = {});
  base.creditors = await creditorsPage("00-baseline", ids.account);
  base.eq = {};
  for (const [k, dt] of Object.entries({ before: D.before, ...Object.fromEntries(LINES.map((l) => [`line${l.n}`, l.date])), today: D.today })) {
    base.eq[k] = await equationAt(`00-baseline-${k}`, ids.account, dt);
  }
  base.txCount = txCount();

  // ── wizard steps 1-3 through the UI ──
  const wz = (result.steps.wizard = {});
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(ids.account);
  await page.getByTestId("period-end-input").fill(D.periodEnd);
  wz.periodEnd = await page.getByTestId("period-end-input").inputValue();
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("bank-statement-upload").waitFor({ timeout: 30_000 });
  await page.getByTestId("file-input").setInputFiles(csvFile);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  wz.uploadResult = (await page.getByTestId("upload-result").innerText()).replace(/\s+/g, " ");
  wz.shotUpload = await snap(page, "w1-statement-uploaded");
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  wz.autoMatch = (await page.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  wz.refusalBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  ids.statement = val(`select id from T.bank_statements where file_name='statement-OBS647A-${RUN}.csv'`);
  ids.recon = val(`select id from T.trust_reconciliations where bank_statement_id='${ids.statement}'`);
  sql("wizard: statement", `select id, period_start, period_end, opening_balance, closing_balance, format, line_count, matched_count, status from T.bank_statements where id='${ids.statement}'`);
  sql("wizard: lines after auto-match", lineSql(ids.statement));
  const lineIds = Object.fromEntries(rowsA(`select line_number, id from T.bank_statement_lines where bank_statement_id='${ids.statement}'`).map(([n, id]) => [n, id]));
  for (const l of LINES) l.id = lineIds[String(l.n)];
  wz.progress = (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ");
  wz.summary = await summaryPairs();
  wz.shotReview = await snap(page, "w2-review-step-before-record");
  wz.reconApi = (await api(`/api/trust-reconciliations/${ids.recon}`, "w2-reconciliation-before-record")).body;

  // ════ (a) record each line from the statement through the dialog ════
  const a = (result.steps.a = { lines: [] });
  for (const l of LINES) {
    const st = { n: l.n, lineId: l.id, amount: l.amount, date: l.date, classification: l.cls };
    const before = sqlPosition(`a${l.n}: before`, ids.account, D.today);
    st.matchedBefore = Number(val(`select matched_count from T.bank_statements where id='${ids.statement}'`));
    st.txCountBefore = txCount();
    const lineEl = page.getByTestId(`bank-line-${l.id}`);
    await lineEl.click();
    await page.getByTestId(`record-btn-${l.id}`).click();
    const dlg = page.getByTestId("record-from-statement-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    st.dialogDescription = (await dlg.locator("[data-slot='dialog-description'], p").first().innerText()).replace(/\s+/g, " ");
    st.optionsOffered = await dlg.getByTestId("classification-radio").locator("label").allInnerTexts();
    st.optionValues = await dlg.getByTestId("classification-radio").locator("button[role='radio']").evaluateAll((bs) => bs.map((b) => b.getAttribute("value")));
    st.chargeFieldsBeforeChoice = await dlg.getByTestId("charge-recoverable-radio").count();
    await dlg.locator(`#classification-${l.cls}`).click();
    if (l.cls === "BANK_CHARGE") {
      await dlg.getByTestId("charge-recoverable-radio").waitFor({ timeout: 10_000 });
      st.chargeQuestion = (await dlg.getByText("Recoverable from the Fidelity Fund?").innerText()).trim();
      st.chargeDescription = (await dlg.getByText(/list of recoverable bank charges/).innerText()).trim();
      st.chargeOptions = await dlg.getByTestId("charge-recoverable-radio").locator("label").allInnerTexts();
      await dlg.locator(l.recoverable ? "#charge-recoverable-yes" : "#charge-recoverable-no").click();
      await dlg.getByTestId("charge-vat-input").fill(l.vat);
    } else {
      st.chargeFieldsAfterChoice = await dlg.getByTestId("charge-recoverable-radio").count();
    }
    await dlg.getByTestId("record-notes-input").fill(`OBS 647A ${RUN} line ${l.n}`);
    st.dialogText = (await dlg.innerText()).replace(/\s+/g, " ");
    st.shotDialog = await snap(page, `a${l.n}-record-dialog-${l.cls}`);
    await crop(dlg, `a${l.n}-record-dialog-crop`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    const badge = page.getByTestId(`recorded-badge-${l.id}`);
    await badge.waitFor({ timeout: 30_000 });
    st.badge = (await badge.innerText()).trim();
    st.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
    st.progress = (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ");
    // read-only evidence of what was posted
    st.rowSql = sql(`a${l.n}: row posted for line ${l.n} (${l.cls})`, rowSql(`t.bank_statement_line_id='${l.id}'`));
    const [row] = rowsA(`select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null, t.creditor_account_id, coalesce(t.charge_recoverable::text,''), coalesce(t.charge_vat_amount::text,''), t.bank_statement_line_id, coalesce(t.approved_by::text,'') from T.trust_transactions t where t.bank_statement_line_id='${l.id}'`);
    st.row = { id: row[0], type: row[1], status: row[2], amount: Number(row[3]), date: row[4], ledger: row[5], ledgerEffect: Number(row[6]),
      cashbookEffect: Number(row[7]), posted: row[8], creditorAccountId: row[9], chargeRecoverable: row[10], chargeVat: row[11], lineLink: row[12], approvedBy: row[13] };
    l.txId = row[0];
    st.lineSql = sql(`a${l.n}: line after record`, lineSql(ids.statement));
    const [ls] = rowsA(`select match_status, coalesce(classification,''), coalesce(trust_transaction_id::text,'') from T.bank_statement_lines where id='${l.id}'`);
    st.line = { matchStatus: ls[0], classification: ls[1], trustTransactionId: ls[2] };
    st.matchedAfter = Number(val(`select matched_count from T.bank_statements where id='${ids.statement}'`));
    st.txCountAfter = txCount();
    st.apiTx = (await api(`/api/trust-accounts/${ids.account}/transactions/${row[0]}`, `a${l.n}-transaction`)).body;
    const after = sqlPosition(`a${l.n}: after`, ids.account, D.today);
    const L = Math.abs(l.amount);
    st.delta = { cashbook: +(after.cashbook - before.cashbook).toFixed(2), fund: +(after.fundInterestAndCharges - before.fundInterestAndCharges).toFixed(2),
      expected: EFFECT[l.cls] * L };
    st.checks = {
      type: st.row.type === l.cls, status: st.row.status === "RECORDED", amount: eq(st.row.amount, L), date: st.row.date === l.date,
      ledger: st.row.ledger === "NON_CLIENT", creditor: st.row.creditorAccountId === ids.fundAccount, link: st.row.lineLink === l.id,
      posted: st.row.posted === "t", noApproval: st.row.approvedBy === "",
      charge: l.cls === "BANK_CHARGE" ? st.row.chargeRecoverable === String(l.recoverable) && eq(st.row.chargeVat, l.vat) : st.row.chargeRecoverable === "" && st.row.chargeVat === "",
      lineRecorded: st.line.matchStatus === "RECORDED" && st.line.classification === l.cls && st.line.trustTransactionId === row[0],
      matchedPlusOne: st.matchedAfter === st.matchedBefore + 1, oneRowCreated: st.txCountAfter === st.txCountBefore + 1,
      cashbookDelta: eq(st.delta.cashbook, st.delta.expected), fundDelta: eq(st.delta.fund, st.delta.expected),
      badge: /^Recorded: /.test(st.badge),
      apiTx: st.apiTx && st.apiTx.transactionType === l.cls && eq(st.apiTx.amount, L) && st.apiTx.transactionDate === l.date && st.apiTx.status === "RECORDED",
    };
    a.lines.push(st);
  }
  a.shotBadges = await snap(page, "a6-all-lines-recorded-badges");
  await crop(page.getByTestId("reconciliation-split-pane"), "a6-split-pane-crop");
  a.badges = await page.locator("[data-testid^='recorded-badge-']").allInnerTexts();
  a.progress = (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ");
  a.summary = await summaryPairs();
  a.reconApi = (await api(`/api/trust-reconciliations/${ids.recon}`, "a6-reconciliation-after-record")).body;
  a.statementApi = (await api(`/api/bank-statements/${ids.statement}`, "a6-bank-statement-after-record")).body;

  // ════ (b) books after all five: Fund ledger, cashbook, equation, Other trust creditors ════
  const b = (result.steps.b = {});
  b.rowsSql = sql("b: all OBS rows", rowSql(`t.reference like '%/OBS647A/${RUN}'`));
  b.linesSql = sql("b: lines", lineSql(ids.statement));
  b.matchedCount = Number(val(`select matched_count from T.bank_statements where id='${ids.statement}'`));
  b.creditors = await creditorsPage("b-after-record", ids.account);
  b.eq = {};
  for (const [k, dt] of Object.entries({ before: D.before, ...Object.fromEntries(LINES.map((l) => [`line${l.n}`, l.date])), today: D.today })) {
    b.eq[k] = await equationAt(`b-after-record-${k}`, ids.account, dt);
  }
  b.fundMovementToday = +(b.eq.today.sqlFund - base.eq.today.sqlFund).toFixed(2);
  b.cashbookMovementToday = +(b.eq.today.sqlCashbook - base.eq.today.sqlCashbook).toFixed(2);

  // ════ (d) sign rule: what each dialog offered (from a), plus one API probe the UI cannot send ════
  const d = (result.steps.d = {});
  d.offered = a.lines.map((s) => ({ line: s.n, amount: s.amount, offered: s.optionsOffered, values: s.optionValues }));
  // probe on the UNMATCHED-after-(c) line is not possible yet; probe now on a fresh copy? No — use line 3
  // after (c), when it is UNMATCHED again (a RECORDED line is refused for its state, not its sign).

  // ════ (c) unmatch the recoverable charge (line 3) through the confirmation ════
  const c = (result.steps.c = {});
  const l3 = LINES[2];
  c.beforeEq = { line3: b.eq.line3, today: b.eq.today };
  c.matchedBefore = b.matchedCount;
  c.txCountBefore = txCount();
  await page.getByTestId(`unmatch-btn-${l3.id}`).click();
  const ud = page.getByTestId("unmatch-recorded-dialog");
  await ud.waitFor({ timeout: 15_000 });
  c.dialogText = (await ud.innerText()).replace(/\s+/g, " ");
  c.shotDialog = await snap(page, "c1-unmatch-recorded-confirmation");
  await crop(ud, "c1-unmatch-dialog-crop");
  await ud.getByTestId("unmatch-recorded-confirm").click();
  await ud.waitFor({ state: "hidden", timeout: 30_000 });
  for (let i = 0; i < 60 && (await page.getByTestId(`recorded-badge-${l3.id}`).count()) > 0; i++) await page.waitForTimeout(250);
  await page.waitForTimeout(1500);
  c.lineText = (await page.getByTestId(`bank-line-${l3.id}`).innerText()).replace(/\s+/g, " ");
  c.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  c.progress = (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ");
  c.summary = await summaryPairs();
  c.shotAfter = await snap(page, "c2-after-unmatch");
  c.originalSql = sql("c: original after unmatch", rowSql(`t.id='${l3.txId}'`));
  c.contraSql = sql("c: contra", rowSql(`t.reversal_of='${l3.txId}'`));
  const [orig] = rowsA(`select status, coalesce(bank_statement_line_id::text,''), coalesce(reversed_by_id::text,'') from T.trust_transactions where id='${l3.txId}'`);
  const [con] = rowsA(`select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null, coalesce(t.creditor_account_id::text,''), coalesce(t.bank_statement_line_id::text,'') from T.trust_transactions t where t.reversal_of='${l3.txId}'`);
  c.original = { status: orig[0], lineLink: orig[1], reversedBy: orig[2] };
  c.contra = { id: con[0], type: con[1], status: con[2], amount: Number(con[3]), date: con[4], ledger: con[5], ledgerEffect: Number(con[6]),
    cashbookEffect: Number(con[7]), posted: con[8], creditorAccountId: con[9], lineLink: con[10] };
  const [ls3] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where id='${l3.id}'`);
  c.line = { matchStatus: ls3[0], classification: ls3[1], trustTransactionId: ls3[2] };
  c.matchedAfter = Number(val(`select matched_count from T.bank_statements where id='${ids.statement}'`));
  c.txCountAfter = txCount();
  c.apiContra = (await api(`/api/trust-accounts/${ids.account}/transactions/${con[0]}`, "c-contra")).body;
  c.apiOriginal = (await api(`/api/trust-accounts/${ids.account}/transactions/${l3.txId}`, "c-original-after-unmatch")).body;
  c.linesSql = sql("c: lines after unmatch", lineSql(ids.statement));
  c.creditors = await creditorsPage("c-after-unmatch", ids.account);
  c.eq = {};
  for (const [k, dt] of Object.entries({ line2: D.line2, line3: D.line3, today: D.today })) c.eq[k] = await equationAt(`c-after-unmatch-${k}`, ids.account, dt);
  c.reconApi = (await api(`/api/trust-reconciliations/${ids.recon}`, "c-reconciliation-after-unmatch")).body;
  c.checks = {
    contraType: c.contra.type === "REVERSAL", contraDate: c.contra.date === l3.date, contraAmount: eq(c.contra.amount, 57.5),
    contraFundLedger: c.contra.ledger === "NON_CLIENT" && c.contra.creditorAccountId === ids.fundAccount,
    contraEffects: eq(c.contra.ledgerEffect, 57.5) && eq(c.contra.cashbookEffect, 57.5), contraUnlinked: c.contra.lineLink === "",
    originalReversed: c.original.status === "REVERSED", originalUnlinked: c.original.lineLink === "", originalReversedBy: c.original.reversedBy === c.contra.id,
    lineUnmatched: c.line.matchStatus === "UNMATCHED" && c.line.classification === "<null>" && c.line.trustTransactionId === "<null>",
    matchedMinusOne: c.matchedAfter === c.matchedBefore - 1, oneRowCreated: c.txCountAfter === c.txCountBefore + 1,
    fundRestored: eq(c.eq.today.sqlFund - b.eq.today.sqlFund, 57.5), cashbookRestored: eq(c.eq.today.sqlCashbook - b.eq.today.sqlCashbook, 57.5),
    fundAtLine3Restored: eq(c.eq.line3.sqlFund - b.eq.line3.sqlFund, 57.5), fundAtLine2Unchanged: eq(c.eq.line2.sqlFund, b.eq.line2.sqlFund),
    equationToday: c.eq.today.equationHolds, equationLine3: c.eq.line3.equationHolds, badgeGone: !/Recorded:/.test(c.lineText) && /Unmatched/.test(c.lineText),
  };

  // ════ (d) continued: line 3 is UNMATCHED again — the dialog offers debit-only; API probe with a credit-only type ════
  await page.getByTestId(`bank-line-${l3.id}`).click();
  await page.getByTestId(`record-btn-${l3.id}`).click();
  const dlg3 = page.getByTestId("record-from-statement-dialog");
  await dlg3.waitFor({ timeout: 15_000 });
  d.line3ReopenedOffered = await dlg3.getByTestId("classification-radio").locator("label").allInnerTexts();
  d.line3ReopenedValues = await dlg3.getByTestId("classification-radio").locator("button[role='radio']").evaluateAll((bs) => bs.map((x) => x.getAttribute("value")));
  d.shotReopened = await snap(page, "d1-line3-reopened-dialog-debit-options");
  await crop(dlg3, "d1-line3-reopened-dialog-crop");
  await dlg3.getByRole("button", { name: "Cancel" }).click();
  await dlg3.waitFor({ state: "hidden", timeout: 15_000 });
  d.txCountBeforeProbe = txCount();
  d.lineBeforeProbe = val(`select match_status||'|'||coalesce(classification,'<null>') from T.bank_statement_lines where id='${l3.id}'`);
  d.probe = await apiPost(`/api/bank-statement-lines/${l3.id}/record`, { classification: "FUND_INTEREST" }, "d-sign-probe-credit-type-on-debit-line");
  d.txCountAfterProbe = txCount();
  d.lineAfterProbe = val(`select match_status||'|'||coalesce(classification,'<null>') from T.bank_statement_lines where id='${l3.id}'`);

  sql("final: OBS rows", rowSql(`t.reference like '%/OBS647A/${RUN}' or t.reversal_of in (select id from T.trust_transactions where reference like '%/OBS647A/${RUN}')`));
  sql("final: lines + matched_count", lineSql(ids.statement));
  sql("final: Fund ledger as at each OBS date", `select d::date as as_at, coalesce((select sum(t.ledger_effect) from T.trust_transactions t where t.creditor_account_id='${ids.fundAccount}' and t.posted_at is not null and t.transaction_date <= d::date),0) as fund_ledger, coalesce((select sum(t.cashbook_effect) from T.trust_transactions t where t.trust_account_id='${ids.account}' and t.posted_at is not null and t.transaction_date <= d::date),0) as cashbook from generate_series(date '${D.before}', date '${D.today}', interval '1 day') d`);

  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap(page, "zz-fatal"); } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String((err && err.stack) || err) }, null, 2));
  process.exit(1);
});
