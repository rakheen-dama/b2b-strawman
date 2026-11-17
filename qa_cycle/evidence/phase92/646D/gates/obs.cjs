// 646D OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice records, Bob approves, via /mock-login). psql is
// read-only EVIDENCE. API reads use the browser context's own mock-auth-token against :8081 (the
// frontend fetches server-side, so a page network capture cannot see the backend calls). The one
// API write is the (d) probe that reproduces the future-date refusal the UI already showed.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-646D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/646D/gates`;
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
const D = {
  today: sastToday, future: addDays(sastToday, 1), back: addDays(sastToday, -3), dayBeforeBack: addDays(sastToday, -4),
  dep: addDays(sastToday, -5), dayBeforeDep: addDays(sastToday, -6),
};
result.dates = D;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;
const REF = { dep: `DEP/OBS646D/${RUN}`, feeC: `FEE/OBS646D/${RUN}-C`, feeE: `FEE/OBS646D/${RUN}-E` };
result.refs = REF;
const AMT = { dep: 1000, feeC: 100, feeE: 50 };
result.amounts = AMT;

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.writeFileSync(SQL_FILE, `-- 646D OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 646D OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
let browser, context, page, who;
async function newSession(userId) {
  if (context) await context.close();
  who = userId;
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
const RAND = "R\\s?-?[\\d\\s\\u00a0\\u202f]+,\\d{2}";

// ════ (a) Home widget + (b) overview hint + trend: UI = API = SQL ════
async function widgets(label, acct) {
  const st = { label };
  const q = sqlPosition(`${label}`, acct, D.today);
  st.sql = q;
  // (a) Home
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: "load" });
  // a navigation can briefly hold two copies while the RSC stream swaps in; wait for one
  await page.getByTestId("trust-position-widget").first().waitFor({ timeout: 60_000 });
  for (let i = 0; i < 40 && (await page.getByTestId("trust-position-widget").count()) !== 1; i++) await page.waitForTimeout(250);
  st.widgetCount = await page.getByTestId("trust-position-widget").count();
  const w = page.getByTestId("trust-position-widget").first();
  await w.getByTestId("trust-position-creditors").waitFor({ timeout: 30_000 });
  const rows = await w.locator("dl").evaluate((dl) => {
    const out = []; const dts = dl.querySelectorAll("dt");
    dts.forEach((dt) => out.push([dt.innerText.trim(), dt.nextElementSibling ? dt.nextElementSibling.innerText.trim() : null]));
    return out;
  });
  const get = (re) => { const r = rows.find(([l]) => re.test(l)); return r ? r[1] : null; };
  st.a = {
    rows, headline: (await w.getByTestId("trust-position-headline").innerText()).trim(),
    widgetText: (await w.innerText()).replace(/\s+/g, " ").trim(),
    hasClientLedgersRow: rows.some(([l]) => /Client ledgers/.test(l)),
  };
  st.a.ui = { cashbook: money(get(/Bank cashbook/)), creditorTotal: money(get(/^Trust creditors/)), difference: money(st.a.headline) };
  st.a.balancedBadge = /Balanced/.test(st.a.widgetText) && !/Difference R/.test(st.a.widgetText);
  st.a.shot = (await snap(`a-${label}-home-widget`)).file;
  await w.screenshot({ path: `${OUT}/${String(shot).padStart(2, "0")}-a-${label}-home-widget-crop.png` }).catch(() => {});
  const ap = await api(`/api/dashboard/trust-position`, `${label}-trust-position`);
  st.a.api = { status: ap.status, cashbookBalance: ap.body.cashbookBalance, clientLedgerTotal: ap.body.clientLedgerTotal,
    creditorBreakdown: ap.body.creditorBreakdown, difference: ap.body.difference };
  st.a.match = {
    cashbook: eq(st.a.ui.cashbook, ap.body.cashbookBalance) && eq(ap.body.cashbookBalance, q.cashbook),
    creditorTotal: eq(st.a.ui.creditorTotal, ap.body.creditorBreakdown?.total) && eq(ap.body.creditorBreakdown?.total, q.creditorTotal),
    clientLedgers: eq(ap.body.clientLedgerTotal, q.clientLedgers) && eq(ap.body.creditorBreakdown?.clientLedgers, q.clientLedgers),
    difference: eq(st.a.ui.difference, ap.body.difference) && eq(ap.body.difference, q.difference),
    nonClientTermsZero: ["investmentCreditors", "fundInterestAndCharges", "unidentifiedDeposits", "otherNonClient", "interAccount"]
      .every((k) => eq(ap.body.creditorBreakdown?.[k], q[k])),
  };
  // (b) overview hint + trend
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.getByTestId("trust-balance-trend").first().waitFor({ timeout: 60_000 });
  for (let i = 0; i < 40 && (await page.getByTestId("trust-balance-trend").count()) !== 1; i++) await page.waitForTimeout(250);
  const trend = page.getByTestId("trust-balance-trend").first();
  await page.getByText(/^Trust creditors R/).first().waitFor({ timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(1500);
  const body = await page.locator("main").innerText();
  const hint = await page.getByText(/^Trust creditors R/).first().innerText().catch(() => null);
  const hintCard = await page.getByText(/^Trust creditors R/).first().locator("xpath=ancestor::*[contains(@class,'rounded')][1]").innerText().catch(() => null);
  st.b = {
    hint, hintCard: hintCard && hintCard.replace(/\s+/g, " "),
    hintCreditors: money(hint), hintHasClientLedgers: /Client ledgers R/.test(body),
    trendText: (await trend.textContent()).replace(/\s+/g, " ").trim(),
    trendLegend: await trend.locator(".recharts-legend-item-text, [class*='legend'] span").allInnerTexts().catch(() => []),
  };
  st.b.trendSeriesLabelTrustCreditors = /Trust creditors/.test(st.b.trendText);
  st.b.trendSeriesLabelClientLedgers = /Client ledgers/.test(st.b.trendText);
  st.b.shot = (await snap(`b-${label}-trust-overview`)).file;
  await trend.screenshot({ path: `${OUT}/${String(shot).padStart(2, "0")}-b-${label}-trend-crop.png` }).catch(() => {});
  const tb = await api(`/api/trust-accounts/${acct}/total-balance`, `${label}-total-balance`);
  const cb = await api(`/api/trust-accounts/${acct}/cashbook-balance`, `${label}-cashbook-balance`);
  const tr = await api(`/api/dashboard/trust-balance-trend?accountId=${acct}&months=12`, `${label}-trust-balance-trend`);
  const last = tr.body.months ? tr.body.months.at(-1) : {};
  const prev = tr.body.months ? tr.body.months.at(-2) : {};
  const qPrev = prev && prev.periodEnd ? sqlPosition(`${label} trend prev month`, acct, prev.periodEnd) : null;
  st.b.api = { totalBalance: tb.body, cashbookBalance: cb.body, trendLast: last, trendPrev: prev, trendStatus: tr.status };
  st.b.match = {
    hintCreditors: eq(st.b.hintCreditors, tb.body.creditorBreakdown?.total) && eq(tb.body.creditorBreakdown?.total, q.creditorTotal),
    totalBalanceClientLedgers: eq(tb.body.balance, q.clientLedgers),
    cashbookBalance: eq(cb.body.balance, q.cashbook),
    trendLastLedger: eq(last.clientLedgerTotal, q.allLedgerEffects) && eq(last.clientLedgerTotal, q.creditorTotal),
    trendLastCashbook: eq(last.cashbookBalance, q.cashbook),
    trendPrevMonth: qPrev ? eq(prev.clientLedgerTotal, qPrev.creditorTotal) && eq(prev.cashbookBalance, qPrev.cashbook) : null,
  };
  return st;
}

async function openTransactions() {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 30_000 });
}
const txRow = (ref) => page.locator("main tbody tr", { hasText: ref });
async function openFeeDialog() {
  await openTransactions();
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: "Record Fee Transfer" }).click();
  const dlg = page.getByRole("dialog", { name: "Record Fee Transfer" });
  await dlg.waitFor({ timeout: 15_000 });
  return dlg;
}
async function approveAsBob(txId, ref) {
  await newSession("user_e2e_bob");
  await openTransactions();
  const row = page.getByTestId(`transaction-row-${txId}`);
  await row.waitFor({ timeout: 30_000 });
  const before = (await row.innerText()).replace(/\s+/g, " ");
  await page.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve" }).click();
  for (let i = 0; i < 60; i++) {
    const t = await row.innerText().catch(() => "");
    if (/APPROVED/.test(t) && !/AWAITING/.test(t)) break;
    await page.waitForTimeout(500);
  }
  await page.reload({ waitUntil: "load" });
  await row.waitFor({ timeout: 30_000 });
  return { rowBefore: before, rowAfter: (await row.innerText()).replace(/\s+/g, " ") };
}
const txSql = (id) => `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id, project_id, invoice_id, recorded_by, approved_by from T.trust_transactions where id='${id}'`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = (result.ids = {});
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  result.account = rowsA(`select id, account_name, bank_name, require_dual_approval, payment_approval_threshold from T.trust_accounts`);
  sql("baseline: trust transactions", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id from T.trust_transactions order by transaction_date, created_at`);
  sql("baseline: creditor accounts", `select id, account_type, name from T.trust_creditor_accounts`);
  sql("baseline: open fee notes", `select i.id, i.invoice_number, i.status, i.total, i.amount_paid, c.name as client, i.customer_id from T.invoices i join T.customers c on c.id=i.customer_id where i.status in ('APPROVED','SENT','PARTIALLY_PAID') order by i.created_at`);

  // a fee note: SENT / PARTIALLY_PAID, single matter linked to (or owned by) its client, >= 200 outstanding
  const cands = rowsA(`select i.id, i.customer_id, c.name, i.invoice_number, i.status, i.total, coalesce(i.amount_paid,0),
      (select min(l.project_id::text) from T.invoice_lines l where l.invoice_id=i.id), p.name, p.status,
      (exists (select 1 from T.customer_projects cp where cp.customer_id=i.customer_id and cp.project_id=p.id) or p.customer_id=i.customer_id)
    from T.invoices i join T.customers c on c.id=i.customer_id
    left join T.projects p on p.id=(select min(l.project_id::text) from T.invoice_lines l where l.invoice_id=i.id)::uuid
    where i.status in ('SENT','PARTIALLY_PAID') and i.total - coalesce(i.amount_paid,0) >= 200
      and (select count(distinct l.project_id) from T.invoice_lines l where l.invoice_id=i.id and l.project_id is not null) = 1
    order by (p.status='ACTIVE') desc, (i.status='SENT') desc, i.total - coalesce(i.amount_paid,0) desc`);
  result.feeNoteCandidates = cands;
  const pick = cands.find((c) => c[10] === "t") || cands[0];
  if (!pick) throw new Error("no suitable fee note (SENT/PARTIALLY_PAID, single matter, >= R200 outstanding)");
  Object.assign(ids, { feeNote: pick[0], client: pick[1], clientName: pick[2], feeNoteNumber: pick[3], feeNoteStatus: pick[4],
    feeNoteTotal: pick[5], feeNotePaid: pick[6], matter: pick[7], matterName: pick[8], matterStatus: pick[9] });
  ids.alice = val(`select id from T.members where clerk_user_id='user_e2e_alice'`);
  ids.bob = val(`select id from T.members where clerk_user_id='user_e2e_bob'`);
  sql("baseline: chosen client's posted rows", `select transaction_type, amount, status, transaction_date, ledger_effect, cashbook_effect, reference from T.trust_transactions where customer_id='${ids.client}' order by transaction_date, created_at`);
  const feeCount = () => Number(val(`select count(*) from T.trust_transactions where transaction_type='FEE_TRANSFER'`));
  const txCount = () => Number(val(`select count(*) from T.trust_transactions`));

  // ── state 1: (a)+(b) on the lifecycle's books ──
  await newSession("user_e2e_alice");
  result.steps.state1 = await widgets("01-lifecycle-books", ids.account);

  // ── setup through the UI: a back-dated client deposit (so the back-dated fee transfer is not
  //    before the client's money arrived) ──
  const dep = (result.steps.deposit = {});
  await openTransactions();
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: "Record Deposit" }).click();
  const ddlg = page.getByRole("dialog", { name: "Record Deposit" });
  await ddlg.waitFor({ timeout: 15_000 });
  await ddlg.getByTestId("trust-deposit-customer-trigger").click();
  const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  await page.getByRole("option", { name: new RegExp(`^${esc(ids.clientName)}(\\s|$)`) }).click();
  await ddlg.getByTestId("trust-deposit-matter-trigger").click();
  await page.getByRole("option", { name: new RegExp(esc(ids.matterName)) }).first().click();
  await ddlg.getByLabel("Amount").fill(String(AMT.dep));
  await ddlg.getByLabel("Reference").fill(REF.dep);
  await ddlg.getByLabel(/Description/).fill(`OBS 646D back-dated deposit ${RUN}`);
  await ddlg.getByLabel("Transaction Date").fill(D.dep);
  await ddlg.getByRole("button", { name: "Record Deposit" }).click();
  await ddlg.waitFor({ state: "hidden", timeout: 30_000 });
  await txRow(REF.dep).first().waitFor({ timeout: 30_000 });
  dep.row = (await txRow(REF.dep).first().innerText()).replace(/\s+/g, " ");
  ids.deposit = val(`select id from T.trust_transactions where reference='${REF.dep}'`);
  dep.sql = sql("deposit row", txSql(ids.deposit));
  dep.shot = (await snap("dep-backdated-deposit-recorded")).file;

  // ════ (d) future date refused in the dialog ════
  const d = (result.steps.d = {});
  d.feeCountBefore = feeCount(); d.txCountBefore = txCount();
  let dlg = await openFeeDialog();
  const dateInput = dlg.getByTestId("fee-transfer-date-input");
  d.label = await dlg.getByText("Fee transfer date (optional)").innerText();
  d.description = await dlg.getByText(/Leave blank for today/).innerText().catch(() => null);
  d.maxAttr = await dateInput.getAttribute("max");
  d.initialValue = await dateInput.inputValue();
  await dlg.getByLabel("Client ID").fill(ids.client);
  await dlg.getByLabel("Fee note ID").fill(ids.feeNote);
  await dlg.getByLabel("Amount").fill(String(AMT.feeC));
  await dlg.getByLabel("Reference").fill(REF.feeC);
  await dateInput.fill(D.future);
  d.typedValue = await dateInput.inputValue();
  d.validity = await dateInput.evaluate((el) => ({ rangeOverflow: el.validity.rangeOverflow, valid: el.validity.valid, validationMessage: el.validationMessage }));
  d.formNoValidate = await dlg.locator("form").evaluate((f) => f.noValidate);
  await dlg.getByRole("button", { name: "Record Fee Transfer" }).click();
  await page.waitForTimeout(2500);
  d.dialogStillOpen = await dlg.isVisible();
  d.dialogText = (await dlg.innerText()).replace(/\s+/g, " ");
  d.visibleMessage = await dlg.getByText("The fee transfer date cannot be in the future").innerText().catch(() => null);
  d.ariaInvalid = await dateInput.getAttribute("aria-invalid");
  d.shot = (await snap("d1-future-date-refused")).file;
  await dlg.screenshot({ path: `${OUT}/${String(shot).padStart(2, "0")}-d1-dialog-crop.png` }).catch(() => {});
  d.feeCountAfterUi = feeCount(); d.txCountAfterUi = txCount();
  // supplementary: the same request straight to the backend (reproduces the refusal the UI showed)
  d.apiProbe = await apiPost(`/api/trust-accounts/${ids.account}/transactions/fee-transfer`,
    { clientId: ids.client, invoiceId: ids.feeNote, amount: AMT.feeC, reference: `${REF.feeC}-PROBE`, transactionDate: D.future }, "d-future-date-probe");
  d.feeCountAfterProbe = feeCount(); d.txCountAfterProbe = txCount();

  // ════ (c) back-dated fee transfer through the same dialog ════
  const c = (result.steps.c = {});
  c.cashbookAsAtBackBefore = (await api(`/api/trust-accounts/${ids.account}/cashbook-balance?asAt=${D.back}`)).body;
  await dateInput.fill(D.back);
  c.typedValue = await dateInput.inputValue();
  c.messageClearedOrStale = await dlg.getByText("The fee transfer date cannot be in the future").count();
  await dlg.getByRole("button", { name: "Record Fee Transfer" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await page.reload({ waitUntil: "load" });
  await txRow(REF.feeC).first().waitFor({ timeout: 30_000 });
  c.rowRecorded = (await txRow(REF.feeC).first().innerText()).replace(/\s+/g, " ");
  ids.feeC = val(`select id from T.trust_transactions where reference='${REF.feeC}'`);
  c.shotRecorded = (await snap("c1-backdated-fee-transfer-awaiting-approval")).file;
  c.apiRecorded = (await api(`/api/trust-accounts/${ids.account}/transactions/${ids.feeC}`, "c-fee-transfer-recorded")).body;
  c.sqlRecorded = sql("c: fee transfer recorded", txSql(ids.feeC));
  c.cashbookAsAtBackWhileAwaiting = (await api(`/api/trust-accounts/${ids.account}/cashbook-balance?asAt=${D.back}`)).body;
  const apr = await approveAsBob(ids.feeC, REF.feeC);
  c.rowApprovedBefore = apr.rowBefore; c.rowApproved = apr.rowAfter;
  c.shotApproved = (await snap("c2-backdated-fee-transfer-approved")).file;
  c.apiApproved = (await api(`/api/trust-accounts/${ids.account}/transactions/${ids.feeC}`, "c-fee-transfer-approved")).body;
  c.sqlApproved = sql("c: fee transfer approved", txSql(ids.feeC));
  c.sqlApprovedDate = val(`select transaction_date from T.trust_transactions where id='${ids.feeC}'`);
  c.sqlApprovedStatus = val(`select status||'|'||(posted_at is not null)||'|'||coalesce(approved_by::text,'') from T.trust_transactions where id='${ids.feeC}'`);
  // as-at reads (API) around the back date and the deposit date, with the SQL oracle beside them
  const asAt = (c.asAt = {});
  for (const [k, dt] of Object.entries({ dayBeforeDep: D.dayBeforeDep, dep: D.dep, dayBeforeBack: D.dayBeforeBack, back: D.back, today: D.today })) {
    const cbA = await api(`/api/trust-accounts/${ids.account}/cashbook-balance?asAt=${dt}`, `c-cashbook-asAt-${dt}`);
    const tbA = await api(`/api/trust-accounts/${ids.account}/total-balance?asAt=${dt}`, `c-total-balance-asAt-${dt}`);
    const q = sqlPosition(`c: as at ${k}`, ids.account, dt);
    const cl = Number(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where customer_id='${ids.client}' and trust_account_id='${ids.account}' and ledger='CLIENT' and posted_at is not null and transaction_date <= date '${dt}'`));
    asAt[k] = { date: dt, apiCashbook: cbA.body.balance, apiStatus: cbA.status, apiClientLedgers: tbA.body.balance, apiCreditorTotal: tbA.body.creditorBreakdown?.total,
      sqlCashbook: q.cashbook, sqlClientLedgers: q.clientLedgers, sqlCreditorTotal: q.creditorTotal, chosenClientLedgerSql: cl,
      match: eq(cbA.body.balance, q.cashbook) && eq(tbA.body.balance, q.clientLedgers) && eq(tbA.body.creditorBreakdown?.total, q.creditorTotal) };
  }
  c.cashbookStepAtBack = +(asAt.back.apiCashbook - asAt.dayBeforeBack.apiCashbook).toFixed(2);
  c.creditorStepAtBack = +(asAt.back.apiCreditorTotal - asAt.dayBeforeBack.apiCreditorTotal).toFixed(2);
  c.cashbookStepAtDep = +(asAt.dep.apiCashbook - asAt.dayBeforeDep.apiCashbook).toFixed(2);
  c.feeNoteAfter = sql("c: fee note after approval", `select id, invoice_number, status, total, amount_paid from T.invoices where id='${ids.feeNote}'`);

  // ════ (e) default date: blank date posts dated today ════
  const e = (result.steps.e = {});
  await newSession("user_e2e_alice");
  dlg = await openFeeDialog();
  e.initialValue = await dlg.getByTestId("fee-transfer-date-input").inputValue();
  await dlg.getByLabel("Client ID").fill(ids.client);
  await dlg.getByLabel("Fee note ID").fill(ids.feeNote);
  await dlg.getByLabel("Amount").fill(String(AMT.feeE));
  await dlg.getByLabel("Reference").fill(REF.feeE);
  e.valueAtSubmit = await dlg.getByTestId("fee-transfer-date-input").inputValue();
  await dlg.getByRole("button", { name: "Record Fee Transfer" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await page.reload({ waitUntil: "load" });
  await txRow(REF.feeE).first().waitFor({ timeout: 30_000 });
  e.rowRecorded = (await txRow(REF.feeE).first().innerText()).replace(/\s+/g, " ");
  ids.feeE = val(`select id from T.trust_transactions where reference='${REF.feeE}'`);
  e.apiRecorded = (await api(`/api/trust-accounts/${ids.account}/transactions/${ids.feeE}`, "e-fee-transfer-recorded")).body;
  const aprE = await approveAsBob(ids.feeE, REF.feeE);
  e.rowApproved = aprE.rowAfter;
  e.shot = (await snap("e1-default-date-fee-transfer-approved")).file;
  e.apiApproved = (await api(`/api/trust-accounts/${ids.account}/transactions/${ids.feeE}`, "e-fee-transfer-approved")).body;
  e.sql = sql("e: default-date fee transfer approved", txSql(ids.feeE));
  e.sqlDate = val(`select transaction_date from T.trust_transactions where id='${ids.feeE}'`);
  e.sqlStatus = val(`select status||'|'||(posted_at is not null) from T.trust_transactions where id='${ids.feeE}'`);
  e.feeNoteAfter = sql("e: fee note after", `select id, invoice_number, status, total, amount_paid from T.invoices where id='${ids.feeNote}'`);

  // ── state 2: (a)+(b) after the deposit and both fee transfers posted ──
  await newSession("user_e2e_alice");
  result.steps.state2 = await widgets("02-after-fee-transfers", ids.account);
  sql("final: OBS rows", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger_effect, cashbook_effect, reference, recorded_by, approved_by from T.trust_transactions where reference like '%OBS646D/${RUN}%' order by created_at`);
  sql("final: chosen client's ledger as at each OBS date", `select d::date as as_at, coalesce((select sum(ledger_effect) from T.trust_transactions where customer_id='${ids.client}' and trust_account_id='${ids.account}' and ledger='CLIENT' and posted_at is not null and transaction_date <= d::date),0) as client_balance from generate_series(date '${D.dayBeforeDep}', date '${D.today}', interval '1 day') d`);

  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap("zz-fatal"); } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String((err && err.stack) || err) }, null, 2));
  process.exit(1);
});
