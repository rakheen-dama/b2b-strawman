// 647C OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice, owner, via /mock-login): statement upload, auto-match,
// record from statement x4 (each confirming the parser's pre-selected suggestion), unmatch x1.
// psql is read-only EVIDENCE. API reads use the browser context's own mock-auth-token against :8081
// (the frontend calls the backend from server actions, so a page network capture cannot see them).
// No API writes.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647C";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/647C/gates`;
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

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.writeFileSync(SQL_FILE, `-- 647C OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 647C OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
const eq = (x, y) => x != null && y != null && !Number.isNaN(Number(x)) && !Number.isNaN(Number(y)) && Math.abs(Number(x) - Number(y)) < 0.005;

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
const money = (s) => {
  if (s == null) return null;
  const m = String(s).match(/[-−]?\s*R[\s  ]?[-−]?[\d\s  ]*\d,\d{2}/);
  if (!m) return null;
  const neg = /[-−]/.test(m[0]);
  const n = Number(m[0].replace(/[^\d,]/g, "").replace(",", "."));
  return neg ? -n : n;
};

const lineSql = (stmt) => `select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from T.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from T.bank_statement_lines l where l.bank_statement_id='${stmt}' order by l.line_number`;
const rowSql = (where) => `select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from T.trust_transactions t where ${where} order by t.created_at`;
// The Fund ledger as 647C's query computes it, recomputed independently: every NON_CLIENT row of the
// Fund creditor account, newest first; running balance = window SUM(ledger_effect) over POSTED rows in
// posting order (transaction_date, created_at, id).
const fundLedgerSql = (fundId) => `with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from T.trust_transactions t where t.creditor_account_id='${fundId}' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from T.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='${fundId}' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc`;
const fundBalanceSql = (fundId, asAt) => `select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${fundId}' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '${asAt}'`;

// Other trust creditors page: Fund StatCard, the Fund ledger table, cross-checked with API + SQL
async function fundLedgerPage(label, ids, query = "") {
  await page2.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors${query}`, { waitUntil: "load" });
  await page2.getByTestId("other-creditors-page").waitFor({ timeout: 60_000 });
  const card = page2.getByTestId("creditor-FUND_INTEREST_AND_CHARGES");
  await card.waitFor({ timeout: 30_000 });
  await page2.getByTestId("fund-ledger-section").waitFor({ timeout: 30_000 });
  const cardText = (await card.innerText()).replace(/\s+/g, " ").trim();
  const cardValue = money(cardText);
  const rowEls = page2.getByTestId("fund-ledger-row");
  const n = await rowEls.count();
  const uiRows = [];
  for (let i = 0; i < n; i++) {
    const r = rowEls.nth(i);
    const cells = await r.locator("td").allInnerTexts();
    uiRows.push({
      cells: cells.map((c) => c.replace(/\s+/g, " ").trim()),
      balanceText: (await r.getByTestId("fund-ledger-balance").innerText()).replace(/\s+/g, " ").trim(),
      chargeText: (await r.getByTestId("fund-ledger-charge").innerText()).replace(/\s+/g, " ").trim(),
      reversedBadge: (await r.getByText("Reversed", { exact: true }).count()) > 0,
    });
  }
  for (const u of uiRows) u.balance = money(u.balanceText);
  const file = await snap(page2, `${label}-other-creditors`);
  await crop(page2.getByTestId("fund-ledger-section"), `${label}-fund-ledger-crop`);
  await crop(card, `${label}-fund-card-crop`);
  const apiCards = await api(`/api/trust-accounts/${ids.account}/creditor-accounts`, `${label}-creditor-accounts`);
  const fundApi = Array.isArray(apiCards.body) ? apiCards.body.find((a) => a.accountType === "FUND_INTEREST_AND_CHARGES") : null;
  const apiRows = await api(`/api/trust-creditor-accounts/${ids.fundAccount}/transactions?page=0&size=25`, `${label}-fund-ledger-transactions`);
  const sqlText = sql(`${label}: Fund ledger rows + window running balance`, fundLedgerSql(ids.fundAccount));
  const sqlRows = rowsA(fundLedgerSql(ids.fundAccount)).map((r) => ({ id: r[0], date: r[1], type: r[2], status: r[3], amount: Number(r[4]),
    ledgerEffect: Number(r[5]), runningBalance: r[6] === "" ? null : Number(r[6]), reference: r[7], chargeRecoverable: r[8], chargeVat: r[9], reversalOf: r[10] }));
  sql(`${label}: Fund creditor balance as at today`, fundBalanceSql(ids.fundAccount, sastToday));
  const sqlBalance = Number(val(fundBalanceSql(ids.fundAccount, sastToday)));
  const apiContent = (apiRows.body && apiRows.body.content) || [];
  const perRow = sqlRows.map((s, i) => {
    const u = uiRows[i] || {};
    const a = apiContent[i] || {};
    return { i, id: s.id, type: s.type, status: s.status, date: s.date, reference: s.reference, sqlRunning: s.runningBalance, apiRunning: a.runningBalance,
      uiRunning: u.balance, uiReference: u.cells ? u.cells[1] : null, uiCharge: u.chargeText, uiReversedBadge: u.reversedBadge,
      idMatchesApi: a.id === s.id, refMatchesUi: u.cells ? u.cells[1] === s.reference : false,
      runningMatches: eq(s.runningBalance, a.runningBalance) && eq(a.runningBalance, u.balance) };
  });
  const top = sqlRows[0];
  return { label, query, cardText, cardValue, cardApi: fundApi && fundApi.balance, sqlBalance, uiRowCount: n, sqlRowCount: sqlRows.length,
    apiTotalElements: apiRows.body && apiRows.body.page ? apiRows.body.page.totalElements : null, apiStatus: apiRows.status,
    uiRows, sqlRows, perRow, shot: file,
    checks: {
      oneUiRowPerEntry: n === sqlRows.length && sqlRows.length > 0,
      apiRowCount: apiContent.length === sqlRows.length,
      newestFirstSameOrderUiApiSql: perRow.every((p) => p.idMatchesApi && p.refMatchesUi),
      everyRowRunningBalanceUiApiSql: perRow.every((p) => p.runningMatches),
      topRunningEqualsStatCard: top ? eq(uiRows[0] && uiRows[0].balance, cardValue) : false,
      statCardEqualsApiEqualsSql: eq(cardValue, fundApi && fundApi.balance) && eq(fundApi && fundApi.balance, sqlBalance),
      topRunningEqualsSqlBalance: top ? eq(top.runningBalance, sqlBalance) : false,
    } };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = (result.ids = {});
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  ids.fundAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='FUND_INTEREST_AND_CHARGES'`);
  sql("baseline: creditor accounts", `select id, trust_account_id, account_type, name from T.trust_creditor_accounts`);
  sql("baseline: trust transactions", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id from T.trust_transactions order by transaction_date, created_at`);
  sql("baseline: statements / recons", `select (select count(*) from T.bank_statements) statements, (select count(*) from T.bank_statement_lines) lines, (select count(*) from T.trust_reconciliations) recons`);
  const txCount = () => Number(val(`select count(*) from T.trust_transactions`));

  // The auto-match target: an existing approved, unlinked, trust-to-business debit from the lifecycle seed.
  const [target] = rowsA(`select t.id, t.reference, t.amount, t.transaction_date, t.transaction_type from T.trust_transactions t where t.trust_account_id='${ids.account}' and t.status in ('APPROVED','RECORDED') and t.bank_statement_line_id is null and t.cashbook_effect < 0 and t.reference is not null and t.reference not like '%,%' and (select count(*) from T.trust_transactions x where lower(x.reference)=lower(t.reference) and x.amount=t.amount) = 1 order by (t.transaction_type='FEE_TRANSFER') desc, t.created_at limit 1`);
  result.autoMatchTarget = target ? { id: target[0], reference: target[1], amount: Number(target[2]), date: target[3], type: target[4] } : null;
  if (!target) result.notes.push("No approved unlinked debit with a unique reference in the seed: (b) cannot be arranged.");

  // The statement: four keyword lines, one ordinary line, one line that auto-matches the seed row.
  const LINES = [
    { n: 1, date: addDays(sastToday, -7), desc: "CREDIT INTEREST", ref: `INT/OBS647C/${RUN}`, amount: 1234.56, expect: "FUND_INTEREST", record: { cls: "FUND_INTEREST" } },
    { n: 2, date: addDays(sastToday, -6), desc: "LPFF FIDELITY FUND INTEREST SWEEP", ref: `LPFF/OBS647C/${RUN}`, amount: -1119.56, expect: "FUND_SWEEP", record: { cls: "FUND_SWEEP" } },
    { n: 3, date: addDays(sastToday, -5), desc: "MONTHLY SERVICE FEE", ref: `SVC/OBS647C/${RUN}`, amount: -57.5, expect: "BANK_CHARGE", record: { cls: "BANK_CHARGE", recoverable: true, vat: "7.50" } },
    { n: 4, date: addDays(sastToday, -4), desc: "SERVICE FEE REFUND", ref: `RFD/OBS647C/${RUN}`, amount: 57.5, expect: "CHARGE_REFUND", record: { cls: "CHARGE_REFUND" } },
    { n: 5, date: addDays(sastToday, -3), desc: "DEPOSIT J NKOSI", ref: `DEP/OBS647C/${RUN}`, amount: 3210.0, expect: null },
  ];
  if (target) {
    // "FEE" in the description: the parser suggests BANK_CHARGE at import; auto-match must clear it.
    LINES.push({ n: 6, date: result.autoMatchTarget.date, desc: "TRUST TO BUSINESS FEE TRANSFER", ref: result.autoMatchTarget.reference,
      amount: -result.autoMatchTarget.amount, expect: "BANK_CHARGE", autoMatch: true });
  }
  const D = { today: sastToday, periodEnd: LINES.map((l) => l.date).sort().slice(-1)[0] };
  result.dates = D;
  result.lines = LINES;

  // the CSV (Standard Bank layout: 2 header lines; Date,Description,Reference,Amount,Balance; yyyy-MM-dd)
  let bal = 0;
  const csv = [`Standard Bank Trust Account - OBS 647C ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of [...LINES].sort((x, y) => x.date.localeCompare(y.date) || x.n - y.n)) {
    bal = +(bal + l.amount).toFixed(2);
    csv.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`);
  }
  const csvName = `statement-OBS647C-${RUN}.csv`;
  const csvFile = `${OUT}/${csvName}`;
  fs.writeFileSync(csvFile, csv.join("\n") + "\n");
  result.csv = csv;

  await newSession("user_e2e_alice");

  // ── baseline: Other trust creditors (Fund ledger before OBS) ──
  const base = (result.steps.baseline = {});
  base.txCount = txCount();
  base.fund = await fundLedgerPage("00-baseline", ids);

  // ════ (a) wizard steps 1-2: upload; suggestions stored at import ════
  const a = (result.steps.a = {});
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(ids.account);
  await page.getByTestId("period-end-input").fill(D.periodEnd);
  a.periodEnd = await page.getByTestId("period-end-input").inputValue();
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("bank-statement-upload").waitFor({ timeout: 30_000 });
  await page.getByTestId("file-input").setInputFiles(csvFile);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  a.uploadResult = (await page.getByTestId("upload-result").innerText()).replace(/\s+/g, " ");
  a.shotUpload = await snap(page, "a-statement-uploaded");
  ids.statement = val(`select id from T.bank_statements where file_name='${csvName}'`);
  a.linesSql = sql("a: lines at import (before auto-match)", lineSql(ids.statement));
  const imported = rowsA(`select line_number, id, description, amount, coalesce(suggested_classification,'<null>'), match_status from T.bank_statement_lines where bank_statement_id='${ids.statement}' order by line_number`);
  a.imported = imported.map((r) => ({ lineNumber: Number(r[0]), id: r[1], description: r[2], amount: Number(r[3]), suggested: r[4], matchStatus: r[5] }));
  for (const l of LINES) {
    const im = a.imported.find((r) => r.description === l.desc && eq(r.amount, l.amount));
    l.id = im ? im.id : null;
    l.suggestedAtImport = im ? im.suggested : "<missing>";
  }
  a.txCountAfterImport = txCount();
  a.perLine = LINES.map((l) => ({ n: l.n, desc: l.desc, amount: l.amount, expected: l.expect || "<null>", stored: l.suggestedAtImport, ok: (l.expect || "<null>") === l.suggestedAtImport }));
  a.checks = { everyLineAsExpected: a.perLine.every((p) => p.ok), lineCount: a.imported.length === LINES.length, nothingPostedAtImport: a.txCountAfterImport === base.txCount };

  // ════ (b) step 3: auto-match; a matched line keeps no suggestion ════
  const b = (result.steps.b = {});
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  b.autoMatchResult = (await page.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  b.shotAutoMatch = await snap(page, "b-auto-match-result");
  b.linesSql = sql("b: lines after auto-match", lineSql(ids.statement));
  const after = rowsA(`select id, match_status, coalesce(suggested_classification,'<null>'), coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where bank_statement_id='${ids.statement}'`);
  b.lines = LINES.map((l) => { const r = after.find((x) => x[0] === l.id) || []; return { n: l.n, matchStatus: r[1], suggested: r[2], trustTransactionId: r[3] }; });
  b.txCountAfterAutoMatch = txCount();
  const l6 = LINES.find((l) => l.autoMatch);
  if (l6) {
    const s6 = b.lines.find((x) => x.n === 6);
    b.target = result.autoMatchTarget;
    b.targetRowSql = sql("b: auto-match target row after auto-match", rowSql(`t.id='${result.autoMatchTarget.id}'`));
    b.checks = {
      suggestedAtImportWasBankCharge: l6.suggestedAtImport === "BANK_CHARGE",
      autoMatched: s6.matchStatus === "AUTO_MATCHED", linkedToTarget: s6.trustTransactionId === result.autoMatchTarget.id,
      suggestionClearedByAutoMatch: s6.suggested === "<null>",
      otherLinesKeptSuggestion: b.lines.filter((x) => x.n !== 6).every((x) => x.matchStatus === "UNMATCHED" && x.suggested === ((LINES.find((l) => l.n === x.n).expect) || "<null>")),
      autoMatchCreatedNoRow: b.txCountAfterAutoMatch === base.txCount,
    };
  }
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  b.refusalBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  ids.recon = val(`select id from T.trust_reconciliations where bank_statement_id='${ids.statement}'`);
  b.statementApi = (await api(`/api/bank-statements/${ids.statement}`, "b-bank-statement-after-auto-match")).body;
  b.progress = (await page.getByTestId("match-progress").innerText()).replace(/\s+/g, " ");
  b.shotReview = await snap(page, "b-review-step");
  if (b.statementApi && Array.isArray(b.statementApi.lines)) {
    b.apiSuggestions = b.statementApi.lines.map((x) => ({ id: x.id, description: x.description, matchStatus: x.matchStatus, suggestedClassification: x.suggestedClassification }));
  }

  // ════ (c) pre-selection in the dialog — pass 1: look, screenshot, Cancel (nothing posted) ════
  const c = (result.steps.c = { look: [], record: [] });
  async function openDialog(l) {
    await page.getByTestId(`bank-line-${l.id}`).click();
    await page.getByTestId(`record-btn-${l.id}`).click();
    const dlg = page.getByTestId("record-from-statement-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await page.waitForTimeout(300);
    const radios = await dlg.getByTestId("classification-radio").locator("button[role='radio']").evaluateAll((bs) =>
      bs.map((x) => ({ value: x.getAttribute("value"), checked: x.getAttribute("data-state") === "checked" || x.getAttribute("aria-checked") === "true" })));
    return {
      dlg,
      state: {
        offered: radios.map((r) => r.value), checked: radios.filter((r) => r.checked).map((r) => r.value),
        noteVisible: await dlg.getByTestId("classification-suggested-note").isVisible().catch(() => false),
        noteText: await dlg.getByTestId("classification-suggested-note").innerText().catch(() => null),
        chargeRecoverableVisible: await dlg.getByTestId("charge-recoverable-radio").isVisible().catch(() => false),
        chargeVatVisible: await dlg.getByTestId("charge-vat-input").isVisible().catch(() => false),
        text: (await dlg.innerText()).replace(/\s+/g, " "),
      },
    };
  }
  for (const l of LINES.filter((x) => !x.autoMatch)) {
    const cnt = txCount();
    const { dlg, state } = await openDialog(l);
    state.n = l.n; state.expected = l.expect || null;
    state.shot = await snap(page, `c-look-line${l.n}-${l.expect || "ORDINARY"}`);
    await crop(dlg, `c-look-line${l.n}-dialog-crop`);
    await dlg.getByRole("button", { name: "Cancel" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15_000 });
    state.txCountUnchanged = txCount() === cnt;
    state.lineStillUnmatched = val(`select match_status from T.bank_statement_lines where id='${l.id}'`) === "UNMATCHED";
    state.ok = l.expect
      ? state.checked.length === 1 && state.checked[0] === l.expect && state.noteVisible && (l.expect === "BANK_CHARGE" ? state.chargeVatVisible && state.chargeRecoverableVisible : !state.chargeVatVisible)
      : state.checked.length === 0 && !state.noteVisible && !state.chargeVatVisible;
    c.look.push(state);
  }
  c.txCountAfterLook = txCount();
  sql("c: rows linked to OBS lines after the look pass (expect none)", rowSql(`t.bank_statement_line_id in (select id from T.bank_statement_lines where bank_statement_id='${ids.statement}') and t.id <> '${result.autoMatchTarget ? result.autoMatchTarget.id : "00000000-0000-0000-0000-000000000000"}'`));

  // pass 2: record the four keyword lines, confirming the pre-selected choice
  for (const l of LINES.filter((x) => x.record)) {
    const st = { n: l.n, cls: l.record.cls };
    st.txCountBeforeClick = txCount();
    st.rowsForLineBeforeClick = Number(val(`select count(*) from T.trust_transactions where bank_statement_line_id='${l.id}'`));
    sql(`c: line ${l.n} rows before Record (expect 0)`, `select count(*) as rows_for_line from T.trust_transactions where bank_statement_line_id='${l.id}'`);
    const { dlg, state } = await openDialog(l);
    st.dialog = state;
    st.preselected = state.checked.length === 1 && state.checked[0] === l.record.cls;
    // no click on the classification: confirm the pre-selected choice as-is
    if (l.record.cls === "BANK_CHARGE") {
      await dlg.locator(l.record.recoverable ? "#charge-recoverable-yes" : "#charge-recoverable-no").click();
      await dlg.getByTestId("charge-vat-input").fill(l.record.vat);
    }
    await dlg.getByTestId("record-notes-input").fill(`OBS 647C ${RUN} line ${l.n}`);
    st.shot = await snap(page, `c-record-line${l.n}-${l.record.cls}`);
    await crop(dlg, `c-record-line${l.n}-dialog-crop`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    const badge = page.getByTestId(`recorded-badge-${l.id}`);
    await badge.waitFor({ timeout: 30_000 });
    st.badge = (await badge.innerText()).trim();
    st.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
    st.rowSql = sql(`c: row posted for line ${l.n} (${l.record.cls})`, rowSql(`t.bank_statement_line_id='${l.id}'`));
    const [row] = rowsA(`select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, coalesce(t.charge_recoverable::text,''), coalesce(t.charge_vat_amount::text,''), t.ledger_effect from T.trust_transactions t where t.bank_statement_line_id='${l.id}'`);
    st.row = row ? { id: row[0], type: row[1], status: row[2], amount: Number(row[3]), date: row[4], chargeRecoverable: row[5], chargeVat: row[6], ledgerEffect: Number(row[7]) } : null;
    l.txId = row ? row[0] : null;
    st.txCountAfter = txCount();
    const [ls] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(suggested_classification,'<null>') from T.bank_statement_lines where id='${l.id}'`);
    st.line = { matchStatus: ls[0], classification: ls[1], suggested: ls[2] };
    st.checks = {
      preselected: st.preselected, noteVisible: state.noteVisible, nothingBeforeClick: st.rowsForLineBeforeClick === 0,
      oneRowCreated: st.txCountAfter === st.txCountBeforeClick + 1, type: st.row && st.row.type === l.record.cls, recorded: st.row && st.row.status === "RECORDED",
      date: st.row && st.row.date === l.date, amount: st.row && eq(st.row.amount, Math.abs(l.amount)),
      charge: l.record.cls === "BANK_CHARGE" ? st.row && st.row.chargeRecoverable === "true" && eq(st.row.chargeVat, l.record.vat) : st.row && st.row.chargeRecoverable === "",
      lineRecorded: st.line.matchStatus === "RECORDED" && st.line.classification === l.record.cls, badge: /^Recorded: /.test(st.badge), noError: !st.errorBanner,
    };
    c.record.push(st);
  }
  c.shotAfterRecord = await snap(page, "c-all-recorded");
  c.lookChecks = { everyDialogAsExpected: c.look.every((s) => s.ok && s.txCountUnchanged && s.lineStillUnmatched), nothingPostedByLooking: c.txCountAfterLook === base.txCount };
  c.recordChecks = c.record.map((s) => ({ n: s.n, allTrue: Object.values(s.checks).every(Boolean) }));

  // ════ (d) + (e) Fund ledger rows, running balance, out-of-range page ════
  const d = (result.steps.d = {});
  d.fund = await fundLedgerPage("d-after-record", ids);
  d.chargeRows = d.fund.uiRows.filter((u) => /Recoverable|Not recoverable/.test(u.chargeText)).map((u) => u.chargeText);
  d.checks = { ...d.fund.checks, chargeDetailShown: d.chargeRows.some((t) => /^Recoverable · VAT R/.test(t.replace(/\s+/g, " "))) };

  const e = (result.steps.e = {});
  await page2.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors?page=50`, { waitUntil: "load" });
  await page2.getByTestId("fund-ledger-section").waitFor({ timeout: 60_000 });
  const oor = page2.getByTestId("fund-ledger-page-out-of-range");
  e.outOfRangeVisible = await oor.isVisible().catch(() => false);
  e.outOfRangeText = await oor.innerText().catch(() => null);
  e.rowsOnPage50 = await page2.getByTestId("fund-ledger-row").count();
  e.emptyLedgerMessageShown = await page2.getByTestId("fund-ledger-empty").isVisible().catch(() => false);
  e.apiPage50 = (await api(`/api/trust-creditor-accounts/${ids.fundAccount}/transactions?page=50&size=25`, "e-fund-ledger-transactions-page50")).body;
  e.shot = await snap(page2, "e-page50-out-of-range");
  await crop(page2.getByTestId("fund-ledger-section"), "e-page50-fund-ledger-crop");
  await page2.getByRole("link", { name: "Go to the first page" }).click();
  await page2.waitForURL(/other-creditors\?page=0$/, { timeout: 30_000 });
  await page2.getByTestId("fund-ledger-row").first().waitFor({ timeout: 30_000 });
  e.urlAfterLink = page2.url();
  e.rowsAfterLink = await page2.getByTestId("fund-ledger-row").count();
  e.shotAfter = await snap(page2, "e-after-go-to-first-page");
  e.checks = { outOfRangeShown: e.outOfRangeVisible && /No entries on this page\./.test(e.outOfRangeText || "") && /Go to the first page/.test(e.outOfRangeText || ""),
    noRowsOnPage50: e.rowsOnPage50 === 0, notTheEmptyLedgerMessage: !e.emptyLedgerMessageShown,
    linkGoesToPage0: /\?page=0$/.test(e.urlAfterLink), rowsShownAfterLink: e.rowsAfterLink === d.fund.sqlRowCount };

  // ════ (f) unmatch the recorded bank charge (line 3) through the confirmation ════
  const f = (result.steps.f = {});
  const l3 = LINES[2];
  f.txCountBefore = txCount();
  await page.getByTestId(`unmatch-btn-${l3.id}`).click();
  const ud = page.getByTestId("unmatch-recorded-dialog");
  await ud.waitFor({ timeout: 15_000 });
  f.dialogText = (await ud.innerText()).replace(/\s+/g, " ");
  f.shotDialog = await snap(page, "f-unmatch-recorded-confirmation");
  await crop(ud, "f-unmatch-dialog-crop");
  await ud.getByTestId("unmatch-recorded-confirm").click();
  await ud.waitFor({ state: "hidden", timeout: 30_000 });
  for (let i = 0; i < 60 && (await page.getByTestId(`recorded-badge-${l3.id}`).count()) > 0; i++) await page.waitForTimeout(250);
  await page.waitForTimeout(1500);
  f.lineText = (await page.getByTestId(`bank-line-${l3.id}`).innerText()).replace(/\s+/g, " ");
  f.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  f.shotAfter = await snap(page, "f-after-unmatch");
  f.originalSql = sql("f: original after unmatch", rowSql(`t.id='${l3.txId}'`));
  f.contraSql = sql("f: contra", rowSql(`t.reversal_of='${l3.txId}'`));
  const [orig] = rowsA(`select status, coalesce(reversed_by_id::text,'') from T.trust_transactions where id='${l3.txId}'`);
  const [con] = rowsA(`select id, transaction_type, transaction_date, ledger_effect from T.trust_transactions where reversal_of='${l3.txId}'`);
  f.original = { id: l3.txId, status: orig[0], reversedBy: orig[1] };
  f.contra = con ? { id: con[0], type: con[1], date: con[2], ledgerEffect: Number(con[3]) } : null;
  const [ls3] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(suggested_classification,'<null>') from T.bank_statement_lines where id='${l3.id}'`);
  f.line = { matchStatus: ls3[0], classification: ls3[1], suggested: ls3[2] };
  f.txCountAfter = txCount();
  f.fund = await fundLedgerPage("f-after-unmatch", ids);
  const origUi = f.fund.perRow.find((p) => p.id === l3.txId);
  const conUi = f.contra ? f.fund.perRow.find((p) => p.id === f.contra.id) : null;
  f.checks = {
    originalReversed: f.original.status === "REVERSED", contraIsReversal: !!f.contra && f.contra.type === "REVERSAL" && f.contra.date === l3.date && eq(f.contra.ledgerEffect, 57.5),
    oneRowCreated: f.txCountAfter === f.txCountBefore + 1, lineUnmatched: f.line.matchStatus === "UNMATCHED" && f.line.classification === "<null>",
    originalRowShownWithReversedBadge: !!origUi && origUi.uiReversedBadge === true,
    contraRowShown: !!conUi, ...Object.fromEntries(Object.entries(f.fund.checks).map(([k, v]) => [`ledger_${k}`, v])),
  };
  // supplementary: a recorded line's suggestion survives its unmatch, and re-opening pre-selects again
  f.suggestionKeptAfterUnmatch = f.line.suggested === "BANK_CHARGE";
  const re = await openDialog(l3);
  f.reopened = re.state;
  f.shotReopen = await snap(page, "f-line3-reopened-after-unmatch");
  await re.dlg.getByRole("button", { name: "Cancel" }).click();
  await re.dlg.waitFor({ state: "hidden", timeout: 15_000 });
  f.txCountAfterReopen = txCount();

  sql("final: OBS statement lines", lineSql(ids.statement));
  sql("final: OBS rows", rowSql(`t.reference like '%/OBS647C/${RUN}' or t.reversal_of in (select id from T.trust_transactions where reference like '%/OBS647C/${RUN}')`));
  sql("final: EQ-2 cashbook vs creditors as at today", `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${sastToday}') cashbook, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${sastToday}') all_creditors, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${ids.fundAccount}' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '${sastToday}') fund_ledger`);

  const allTrue = (o) => !!o && Object.values(o).every(Boolean);
  result.verdict = {
    a: allTrue(a.checks) ? "PASS" : "FAIL",
    b: !l6 ? "NOT-OBSERVED" : allTrue(b.checks) ? "PASS" : "FAIL",
    c: allTrue(c.lookChecks) && c.recordChecks.every((x) => x.allTrue) && c.record.length >= 2 ? "PASS" : "FAIL",
    d: allTrue(d.checks) ? "PASS" : "FAIL",
    e: allTrue(e.checks) ? "PASS" : "FAIL",
    f: allTrue(f.checks) ? "PASS" : "FAIL",
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length,
  };
  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap(page, "zz-fatal"); } catch {}
  try { await snap(page2, "zz-fatal-tab2"); } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String((err && err.stack) || err), finishedAt: new Date().toISOString() }, null, 2));
  process.exit(1);
});
