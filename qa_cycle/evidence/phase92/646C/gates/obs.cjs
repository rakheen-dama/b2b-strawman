// 646C OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice via /mock-login). psql is read-only EVIDENCE.
// API reads use the browser context's own mock-auth-token against the backend (:8081): the frontend
// fetches server-side (server actions), so a page network capture cannot see the backend calls.
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-646C/frontend/node_modules/@playwright/test"
);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = "/tmp/646C-gates";
const OUT = `${EV}/obs`;
const CSV = `${EV}/csv`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [] };

const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const dmy = (iso) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
const D = { today: sastToday, tomorrow: addDays(sastToday, 1), refusalEnd: addDays(sastToday, -3) };
result.dates = D;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.writeFileSync(SQL_FILE, `-- 646C OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 646C OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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

// The six-term position (TrustLedgerQueryRepository.position) + outstanding (…outstanding), reproduced read-only
function positionSql(acct, asAt) {
  return `select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account
from T.trust_transactions t left join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='${acct}' and t.posted_at is not null and t.transaction_date <= date '${asAt}'`;
}
function outstandingSql(acct, asAt) {
  return `select
  coalesce( sum(t.cashbook_effect) filter (where t.cashbook_effect > 0),0) as outstanding_deposits,
  coalesce(-sum(t.cashbook_effect) filter (where t.cashbook_effect < 0),0) as outstanding_payments
from T.trust_transactions t left join T.bank_statement_lines l on l.id=t.bank_statement_line_id
where t.trust_account_id='${acct}' and t.posted_at is not null and t.cashbook_effect <> 0
  and t.transaction_date <= date '${asAt}'
  and (t.bank_statement_line_id is null or l.transaction_date > date '${asAt}')
  and not (t.bank_statement_line_id is null and t.reversed_by_id is not null and exists (
       select 1 from T.trust_transactions r where r.id=t.reversed_by_id and r.posted_at is not null
          and r.bank_statement_line_id is null and r.transaction_date <= date '${asAt}'))
  and not (t.bank_statement_line_id is null and t.reversal_of is not null and exists (
       select 1 from T.trust_transactions o where o.id=t.reversal_of and o.bank_statement_line_id is null))`;
}
function sqlFigures(label, acct, stmtId, asAt) {
  sql(`${label}: position as at ${asAt}`, positionSql(acct, asAt));
  sql(`${label}: outstanding as at ${asAt}`, outstandingSql(acct, asAt));
  sql(`${label}: statement + lines`, `select s.closing_balance, s.opening_balance, s.line_count, s.matched_count from T.bank_statements s where s.id='${stmtId}'`);
  sql(`${label}: lines`, `select line_number, transaction_date, reference, amount, running_balance, match_status, trust_transaction_id, match_confidence from T.bank_statement_lines where bank_statement_id='${stmtId}' order by line_number`);
  const p = rowsA(positionSql(acct, asAt))[0].map(Number);
  const o = rowsA(outstandingSql(acct, asAt))[0].map(Number);
  const bank = Number(val(`select closing_balance from T.bank_statements where id='${stmtId}'`));
  const excluded = Number(val(`select coalesce(sum(amount),0) from T.bank_statement_lines where bank_statement_id='${stmtId}' and match_status='EXCLUDED'`));
  const unresolved = Number(val(`select count(*) from T.bank_statement_lines where bank_statement_id='${stmtId}' and match_status='UNMATCHED'`));
  const [cashbook, client, inv, fund, unid, other, inter] = p;
  const creditors = +(client + inv + fund + unid + other + inter).toFixed(2);
  const adjusted = +(bank + o[0] - o[1]).toFixed(2);
  return {
    bankBalance: bank, outstandingDeposits: o[0], outstandingPayments: o[1], adjustedBankBalance: adjusted,
    cashbookBalance: cashbook, bankToCashbookDifference: +(adjusted - cashbook).toFixed(2),
    clientLedgers: client, investmentCreditors: inv, fundInterestAndCharges: fund, unidentifiedDeposits: unid,
    otherNonClient: other, interAccount: inter, creditorTotal: creditors,
    cashbookToCreditorsDifference: +(cashbook - creditors).toFixed(2), difference: +(adjusted - creditors).toFixed(2),
    excludedLinesTotal: excluded, unresolvedLineCount: unresolved,
    isBalanced: adjusted === cashbook && cashbook === creditors,
  };
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
  return { file: file.replace(`${OUT}/`, ""), text };
}
async function api(path) {
  const tok = (await context.cookies()).find((c) => c.name === "mock-auth-token");
  const res = await context.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${tok ? tok.value : ""}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  return { status: res.status(), body };
}
async function apiPost(path, data) {
  const tok = (await context.cookies()).find((c) => c.name === "mock-auth-token");
  const res = await context.request.post(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${tok ? tok.value : ""}`, "Content-Type": "application/json" }, data });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 800));
  return { status: res.status(), body };
}
const money = (s) => {
  if (s == null) return null;
  const neg = /[-−]/.test(s);
  const n = Number(s.replace(/[^\d,]/g, "").replace(",", "."));
  return neg ? -n : n;
};
// read the review-step summary card into figures
async function uiFigures() {
  const sec = async (tid) =>
    page.getByTestId(tid).locator("dl > div").evaluateAll((divs) =>
      divs.map((d) => [d.querySelector("dt").innerText.trim(), d.querySelector("dd").innerText.trim()])
    );
  const bank = await sec("comparison-bank-cashbook");
  const books = await sec("comparison-cashbook-creditors");
  const cred = await sec("creditor-breakdown");
  const get = (arr, re) => { const r = arr.find(([l]) => re.test(l)); return r ? money(r[1]) : null; };
  const unresolvedText = (await page.getByTestId("unresolved-line-count").innerText()).trim();
  const excludedText = (await page.getByTestId("excluded-lines-total").innerText()).trim();
  const fig = {
    raw: { bank, books, cred, unresolvedText, excludedText },
    bankBalance: get(bank, /^Bank balance/),
    outstandingDeposits: get(bank, /Outstanding deposits/),
    outstandingPayments: get(bank, /Outstanding payments/),
    adjustedBankBalance: get(bank, /Adjusted bank balance/),
    cashbookBalance: get(bank, /^Cashbook/),
    bankToCashbookDifference: money(await page.getByTestId("bank-to-cashbook-difference").innerText()),
    cashbookOnBooksSide: get(books, /^Cashbook/),
    creditorTotal: get(books, /^Trust creditors/),
    cashbookToCreditorsDifference: money(await page.getByTestId("cashbook-to-creditors-difference").innerText()),
    clientLedgers: get(cred, /^Client ledgers/),
    investmentCreditors: get(cred, /Investment creditors/) ?? "(row hidden: 0)",
    fundInterestAndCharges: get(cred, /Fidelity Fund/),
    unidentifiedDeposits: get(cred, /Unidentified deposits/),
    interAccount: get(cred, /section 86\(3\)|Held for the firm/) ?? "(row hidden: 0)",
    otherNonClient: get(cred, /Other non-client/) ?? "(row hidden: 0)",
    unresolvedLineCount: Number((unresolvedText.match(/^(\d+)/) || [])[1]),
    excludedLinesTotal: money((excludedText.match(/total\s+([^.]*\d)/) || [])[1]),
    balancedText: (await page.getByTestId("balance-summary").innerText()).match(/Reconciliation is (not )?balanced/)?.[0],
    completeDisabled: await page.getByTestId("complete-reconciliation-btn").isDisabled(),
    progress: (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ").trim(),
  };
  fig.title = (await page.getByTestId("balance-summary").innerText()).split("\n")[0];
  return fig;
}
async function waitSummaryChange(prevText, timeout = 30_000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const t = await page.getByTestId("balance-summary").innerText().catch(() => "");
    if (t && t !== prevText) return true;
    await page.waitForTimeout(500);
  }
  return false;
}
function compare(ui, apiB, s) {
  const keys = ["bankBalance", "outstandingDeposits", "outstandingPayments", "adjustedBankBalance", "cashbookBalance",
    "bankToCashbookDifference", "clientLedgers", "investmentCreditors", "fundInterestAndCharges", "unidentifiedDeposits",
    "otherNonClient", "interAccount", "creditorTotal", "cashbookToCreditorsDifference", "difference",
    "excludedLinesTotal", "unresolvedLineCount", "isBalanced"];
  const cb = apiB.creditorBreakdown || {};
  const apiVal = (k) => {
    if (k === "creditorTotal") return cb.total;
    if (["clientLedgers", "investmentCreditors", "fundInterestAndCharges", "unidentifiedDeposits", "otherNonClient", "interAccount"].includes(k)) return cb[k];
    return apiB[k];
  };
  const uiVal = (k) => {
    if (k === "difference") return "(not shown)";
    if (k === "isBalanced") return ui.balancedText === "Reconciliation is balanced";
    const v = ui[k];
    return typeof v === "string" && v.startsWith("(row hidden") ? 0 : v;
  };
  return keys.map((k) => {
    const u = uiVal(k), a = apiVal(k), q = s[k];
    const eq = (x, y) => (typeof x === "boolean" || typeof y === "boolean" ? x === y : Math.abs(Number(x) - Number(y)) < 0.005);
    return { term: k, ui: u, api: a, sql: q, match: (u === "(not shown)" || eq(u, a)) && eq(a, q) };
  });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = {};
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  result.account = rowsA(`select id, account_name, bank_name, account_number from T.trust_accounts`);
  sql("baseline: trust transactions", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id from T.trust_transactions order by transaction_date, created_at`);
  sql("baseline: creditor accounts", `select id, account_type, name from T.trust_creditor_accounts`);
  const baseline = sqlFigures("baseline", ids.account, "00000000-0000-0000-0000-000000000000", D.today);
  result.baselineBooks = baseline;

  // pick the statement's matching transactions from the lifecycle's books
  const t1 = rowsA(`select id, transaction_date, amount, reference from T.trust_transactions t where trust_account_id='${ids.account}' and posted_at is not null and cashbook_effect > 0 and status in ('RECORDED','APPROVED') and bank_statement_line_id is null and reference is not null and reference <> '' and transaction_date <= date '${D.today}' and (select count(*) from T.trust_transactions x where x.reference=t.reference) = 1 order by transaction_date, created_at limit 1`)[0];
  const t2 = rowsA(`select id, transaction_date, amount, reference, transaction_type from T.trust_transactions t where trust_account_id='${ids.account}' and posted_at is not null and cashbook_effect < 0 and status='APPROVED' and bank_statement_line_id is null and transaction_date <= date '${D.today}' and (select count(*) from T.trust_transactions x where x.trust_account_id=t.trust_account_id and x.amount=t.amount and x.cashbook_effect < 0) = 1 order by transaction_date, created_at limit 1`)[0];
  if (!t1 || !t2) throw new Error(`no suitable transactions: t1=${t1} t2=${t2}`);
  ids.t1 = t1[0]; ids.t2 = t2[0];
  const l2date = t2[1] < D.today ? addDays(t2[1], 1) : addDays(t2[1], -1);
  const lines = [
    // bank value date two days before the cashbook date: matched by the exact reference (no date rule)
    { date: addDays(t1[1], -2), desc: "EFT credit (client deposit)", amount: Number(t1[2]), ref: t1[3] },
    { date: l2date, desc: "EFT debit (trust payment)", amount: -Number(t2[2]), ref: `EFT-OBS646C-${RUN}` },
    { date: D.today, desc: "Bank charges - monthly service fee", amount: -85.5, ref: `CHG-OBS646C-${RUN}` },
  ].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  // make the bank charge the file's last line (dated today)
  const chg = lines.findIndex((l) => l.ref.startsWith("CHG")); lines.push(lines.splice(chg, 1)[0]);
  let bal = 0;
  const csvRows = lines.map((l) => { bal = +(bal + l.amount).toFixed(2); return `${dmy(l.date)},${l.desc},${l.amount.toFixed(2)},${bal.toFixed(2)},${l.ref}`; });
  const csvB = [`FNB Trust Account Statement - OBS 646C ${RUN}`, "Date,Description,Amount,Balance,Reference", ...csvRows].join("\n") + "\n";
  const csvBPath = `${CSV}/obs646c-${RUN}-statement.csv`;
  fs.writeFileSync(csvBPath, csvB);
  result.csvB = { path: csvBPath, content: csvB, t1, t2, closing: bal };

  // ════ a. Future period end refused in step 1 ════
  const a = (result.steps.a = {});
  await newSession("user_e2e_alice");
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.getByTestId("new-reconciliation-btn").click();
  await page.waitForURL(/reconciliation\/new/, { timeout: 30_000 });
  await page.getByTestId("step-select").waitFor({ timeout: 30_000 });
  const acctSel = page.getByTestId("account-select");
  await acctSel.locator("option").first().waitFor({ state: "attached", timeout: 15_000 });
  a.accountOptions = await acctSel.locator("option").allInnerTexts();
  await acctSel.selectOption(ids.account);
  a.maxAttr = await page.getByTestId("period-end-input").getAttribute("max");
  await page.getByTestId("period-end-input").fill(D.tomorrow);
  a.typedValue = await page.getByTestId("period-end-input").inputValue();
  a.nextEnabled = await page.getByTestId("step-next-btn").isEnabled();
  await page.getByTestId("step-next-btn").click();
  await page.waitForTimeout(800);
  a.errorText = await page.getByTestId("period-end-error").innerText().catch(() => null);
  a.errorRole = await page.getByTestId("period-end-error").getAttribute("role").catch(() => null);
  a.ariaInvalid = await page.getByTestId("period-end-input").getAttribute("aria-invalid");
  a.stillOnStep1 = await page.getByTestId("step-select").isVisible();
  a.uploadVisible = await page.getByTestId("bank-statement-upload").isVisible().catch(() => false);
  a.shotRefused = (await snap("a1-future-period-end-refused")).file;
  a.sqlReconsAfterRefusal = val("select count(*) from T.trust_reconciliations");
  await page.getByTestId("period-end-input").fill(D.today);
  a.errorClearedOnEdit = (await page.getByTestId("period-end-error").count()) === 0;
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("bank-statement-upload").waitFor({ timeout: 15_000 });
  a.todayAdvancesToStep2 = await page.getByTestId("bank-statement-upload").isVisible();
  a.shotToday = (await snap("a2-today-advances-to-upload")).file;

  // ════ b. Draft reconciliation shows both comparisons ════
  const b = (result.steps.b = {});
  await page.getByTestId("file-input").setInputFiles(csvBPath);
  await page.getByTestId("upload-result").waitFor({ timeout: 30_000 });
  b.uploadResult = (await page.getByTestId("upload-result").innerText()).replace(/\s+/g, " ");
  b.shotUpload = (await snap("b1-statement-uploaded")).file;
  ids.stmtB = val(`select id from T.bank_statements where file_name like 'obs646c-${RUN}-statement%' order by created_at desc limit 1`);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 30_000 });
  b.autoMatchResult = (await page.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  b.shotAutoMatch = (await snap("b2-auto-match")).file;
  await page.getByRole("button", { name: "Review Matches" }).click();
  await page.getByTestId("balance-summary").waitFor({ timeout: 60_000 });
  ids.reconB = val(`select id from T.trust_reconciliations where bank_statement_id='${ids.stmtB}'`);
  ids.lines = rowsA(`select id, line_number, reference, match_status from T.bank_statement_lines where bank_statement_id='${ids.stmtB}' order by line_number`);
  const lineByRef = (re) => (ids.lines.find((l) => re.test(l[2])) || [])[0];
  ids.lineMatch = lineByRef(/^EFT-OBS646C/);
  ids.lineCharge = lineByRef(/^CHG-OBS646C/);
  b.unmatchedTxPane = await page.locator('[data-testid^="transaction-"]').evaluateAll((els) => els.map((e) => e.getAttribute("data-testid") + " :: " + e.innerText.replace(/\s+/g, " ").trim()));
  const states = (result.states = []);
  async function record(label) {
    const s = await snap(`b-${label}`);
    const ui = await uiFigures();
    const ap = await api(`/api/trust-reconciliations/${ids.reconB}`);
    fs.writeFileSync(`${OUT}/api-reconciliation-${label}.json`, JSON.stringify(ap, null, 2));
    const q = sqlFigures(`state ${label}`, ids.account, ids.stmtB, D.today);
    sql(`state ${label}: stored reconciliation row`, `select period_end, bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference, is_balanced, status, integrity_flags from T.trust_reconciliations where id='${ids.reconB}'`);
    const st = { label, shot: s.file, ui, apiStatus: ap.status, table: compare(ui, ap.body, q) };
    st.allMatch = st.table.every((r) => r.match);
    states.push(st);
    return st;
  }
  const s0 = await record("01-review-opened");
  // match the EFT debit line through the UI
  const prev0 = await page.getByTestId("balance-summary").innerText();
  await page.getByTestId(`bank-line-${ids.lineMatch}`).click();
  await page.getByTestId(`match-btn-${ids.t2}`).waitFor({ timeout: 15_000 });
  b.candidateHighlighted = await page.getByTestId(`transaction-${ids.t2}`).getAttribute("class").then((c) => /teal/.test(c));
  await page.getByTestId(`match-btn-${ids.t2}`).click();
  b.changedAfterMatch = await waitSummaryChange(prev0);
  await page.waitForTimeout(1000);
  const s1 = await record("02-after-manual-match");
  // exclude the bank-charge line through the UI
  const prev1 = await page.getByTestId("balance-summary").innerText();
  await page.getByTestId(`bank-line-${ids.lineCharge}`).click();
  await page.getByTestId(`exclude-btn-${ids.lineCharge}`).click();
  b.changedAfterExclude = await waitSummaryChange(prev1);
  await page.waitForTimeout(1000);
  const s2 = await record("03-after-exclude");
  sql("b: line ↔ transaction links", `select l.line_number, l.reference, l.amount, l.match_status, l.excluded_reason, t.id as txn, t.transaction_type, t.status, t.reference as txn_ref, t.bank_statement_line_id = l.id as linked from T.bank_statement_lines l left join T.trust_transactions t on t.id = l.trust_transaction_id where l.bank_statement_id='${ids.stmtB}' order by l.line_number`);

  // ════ d. Refusal: period end before the statement's last line ════
  const d = (result.steps.d = {});
  const csvD = [`FNB Trust Account Statement - OBS 646C late ${RUN}`, "Date,Description,Amount,Balance,Reference",
    `${dmy(addDays(D.today, -1))},Interest credit,12.34,12.34,INT-OBS646C-${RUN}`,
    `${dmy(D.today)},Sundry credit,7.77,20.11,SUN-OBS646C-${RUN}`].join("\n") + "\n";
  const csvDPath = `${CSV}/obs646c-${RUN}-late.csv`;
  fs.writeFileSync(csvDPath, csvD);
  d.csv = { path: csvDPath, content: csvD, periodEnd: D.refusalEnd };
  d.reconCountBefore = val("select count(*) from T.trust_reconciliations");
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 30_000 });
  await page.getByTestId("account-select").locator("option").first().waitFor({ state: "attached", timeout: 15_000 });
  await page.getByTestId("account-select").selectOption(ids.account);
  await page.getByTestId("period-end-input").fill(D.refusalEnd);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("bank-statement-upload").waitFor({ timeout: 15_000 });
  await page.getByTestId("file-input").setInputFiles(csvDPath);
  await page.getByTestId("upload-result").waitFor({ timeout: 30_000 });
  d.uploadResult = (await page.getByTestId("upload-result").innerText()).replace(/\s+/g, " ");
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 30_000 });
  await page.getByRole("button", { name: "Review Matches" }).click();
  const banner = page.locator("div.bg-red-50").first();
  await banner.waitFor({ timeout: 30_000 }).catch(() => {});
  d.bannerText = await banner.innerText().catch(() => null);
  d.stayedOnStep3 = await page.getByTestId("step-auto-match").isVisible().catch(() => false);
  d.reviewReached = await page.getByTestId("balance-summary").isVisible().catch(() => false);
  d.shot = (await snap("d1-refusal-statement-after-period-end")).file;
  d.reconCountAfter = val("select count(*) from T.trust_reconciliations");
  ids.stmtD = val(`select id from T.bank_statements where file_name like 'obs646c-${RUN}-late%' order by created_at desc limit 1`);
  // the same refusal read straight from the backend with the session token (evidence of the 400 body)
  d.apiProbe = await apiPost(`/api/trust-accounts/${ids.account}/reconciliations`, { periodEnd: D.refusalEnd, bankStatementId: ids.stmtD });
  d.reconCountAfterProbe = val("select count(*) from T.trust_reconciliations");

  // b's draft re-read after d's import (it must still calculate: d's lines are not after b's last line)
  result.steps.bAfterD = await api(`/api/trust-reconciliations/${ids.reconB}`).then((r) => ({ status: r.status, difference: r.body.difference, isBalanced: r.body.isBalanced, status2: r.body.status }));
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.getByTestId("reconciliations-table").waitFor({ timeout: 30_000 }).catch(() => {});
  result.steps.listShot = (await snap("e1-reconciliation-list")).file;
  sql("final: reconciliations", "select id, period_end, status, is_balanced, bank_balance, adjusted_bank_balance, cashbook_balance, creditor_total, difference from T.trust_reconciliations order by created_at");
  sql("final: bank statements", "select id, file_name, period_start, period_end, closing_balance, line_count, matched_count from T.bank_statements order by created_at");

  result.ids = ids;
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap("zz-fatal"); } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String(err && err.stack || err) }, null, 2));
  process.exit(1);
});
