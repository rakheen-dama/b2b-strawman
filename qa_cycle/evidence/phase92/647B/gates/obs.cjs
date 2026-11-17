// 647B OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice, owner, via /mock-login): statement upload, auto-match,
// record from statement as "Unidentified deposit" x1, unmatch x1. The debit line's dialog is opened only
// to read what it offers, then cancelled.
// psql is read-only EVIDENCE. API reads use the browser context's own mock-auth-token against :8081
// (the frontend calls the backend from server actions, so a page network capture cannot see them).
// No API writes.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647B";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/647B/gates`;
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
function daysBetween(fromIso, toIso) {
  return Math.round((new Date(`${toIso}T12:00:00Z`) - new Date(`${fromIso}T12:00:00Z`)) / 86_400_000);
}
const MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const dayMonYear = (iso) => { const [y, m, d] = iso.split("-").map(Number); return { d, mon: MON[m - 1], y }; };
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
fs.writeFileSync(SQL_FILE, `-- 647B OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 647B OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
  const m = String(s).match(/[-−]?\s*R[\s  ]?[-−]?[\d\s  ]*\d,\d{2}/);
  if (!m) return null;
  const neg = /[-−]/.test(m[0]);
  const n = Number(m[0].replace(/[^\d,]/g, "").replace(",", "."));
  return neg ? -n : n;
};

const lineSql = (stmt) => `select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from T.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from T.bank_statement_lines l where l.bank_statement_id='${stmt}' order by l.line_number`;
const rowSql = (where) => `select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.unidentified_deposit_id, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from T.trust_transactions t where ${where} order by t.created_at`;
const itemSql = (where) => `select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from T.unidentified_deposits d where ${where} order by d.received_date, d.created_at, d.id`;
const ledgerBalanceSql = (credId, asAt) => `select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${credId}' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '${asAt}'`;
const eqSql = (acct, asAt) => `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where trust_account_id='${acct}' and posted_at is not null and transaction_date <= date '${asAt}') cashbook, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${acct}' and posted_at is not null and transaction_date <= date '${asAt}') all_creditors, (select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t join T.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='${acct}' and c.account_type='UNIDENTIFIED_DEPOSITS' and t.posted_at is not null and t.transaction_date <= date '${asAt}') unidentified_ledger, (select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t join T.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='${acct}' and c.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date <= date '${asAt}') fund_ledger`;

// Other trust creditors page: the Unidentified deposits StatCard + table, cross-checked with API + SQL
async function itemsPage(label, ids) {
  await page2.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
  await page2.getByTestId("other-creditors-page").waitFor({ timeout: 60_000 });
  const card = page2.getByTestId("creditor-UNIDENTIFIED_DEPOSITS");
  await card.waitFor({ timeout: 30_000 });
  const section = page2.getByTestId("unidentified-deposits-section");
  await section.waitFor({ timeout: 30_000 });
  const cardText = (await card.innerText()).replace(/\s+/g, " ").trim();
  const cardValue = money(cardText);
  const rowEls = page2.getByTestId("unidentified-deposit-row");
  const n = await rowEls.count();
  const uiRows = [];
  for (let i = 0; i < n; i++) {
    const r = rowEls.nth(i);
    const cells = (await r.locator("td").allInnerTexts()).map((c) => c.replace(/\s+/g, " ").trim());
    uiRows.push({
      cells,
      status: (await r.getByTestId("unidentified-deposit-status").innerText()).trim(),
      ageText: await r.getByTestId("unidentified-deposit-age").innerText().catch(() => null),
      overOneYear: (await r.getByTestId("unidentified-over-one-year").count()) > 0,
      amount: money(cells[2]),
    });
  }
  const emptyShown = await page2.getByTestId("unidentified-deposits-empty").isVisible().catch(() => false);
  const errorShown = await page2.getByTestId("unidentified-deposits-error").isVisible().catch(() => false);
  const headers = (await section.locator("thead th").allInnerTexts()).map((h) => h.trim());
  const file = await snap(page2, `${label}-other-creditors`);
  await crop(section, `${label}-unidentified-crop`);
  await crop(card, `${label}-unidentified-card-crop`);
  const apiCards = await api(`/api/trust-accounts/${ids.account}/creditor-accounts`, `${label}-creditor-accounts`);
  const udApi = Array.isArray(apiCards.body) ? apiCards.body.find((a) => a.accountType === "UNIDENTIFIED_DEPOSITS") : null;
  const apiItems = await api(`/api/trust-accounts/${ids.account}/unidentified-deposits?page=0&size=25`, `${label}-unidentified-deposits`);
  const apiHeld = await api(`/api/trust-accounts/${ids.account}/unidentified-deposits?status=HELD`, `${label}-unidentified-deposits-held`);
  sql(`${label}: unidentified_deposits rows`, itemSql(`d.trust_account_id='${ids.account}'`));
  sql(`${label}: unidentified ledger balance as at today`, ledgerBalanceSql(ids.udAccount, sastToday));
  const sqlBalance = Number(val(ledgerBalanceSql(ids.udAccount, sastToday)));
  return { label, cardText, cardValue, cardApi: udApi && udApi.balance, sqlBalance, headers, uiRowCount: n, uiRows, emptyShown, errorShown,
    api: apiItems.body, apiStatus: apiItems.status, apiHeld: apiHeld.body, shot: file };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = (result.ids = {});
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  ids.udAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='UNIDENTIFIED_DEPOSITS'`);
  ids.fundAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='FUND_INTEREST_AND_CHARGES'`);
  sql("baseline: creditor accounts", `select id, trust_account_id, account_type, name from T.trust_creditor_accounts`);
  sql("baseline: trust transactions", `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id, unidentified_deposit_id from T.trust_transactions order by transaction_date, created_at`);
  sql("baseline: statements / recons / items", `select (select count(*) from T.bank_statements) statements, (select count(*) from T.bank_statement_lines) lines, (select count(*) from T.trust_reconciliations) recons, (select count(*) from T.unidentified_deposits) items`);
  sql("baseline: EQ-2 as at today", eqSql(ids.account, sastToday));
  const txCount = () => Number(val(`select count(*) from T.trust_transactions`));
  const itemCount = () => Number(val(`select count(*) from T.unidentified_deposits`));
  const eqNow = () => { const [r] = rowsA(eqSql(ids.account, sastToday)); return { cashbook: Number(r[0]), allCreditors: Number(r[1]), unidentified: Number(r[2]), fund: Number(r[3]) }; };

  // The statement: one unknown credit (to be recorded as an unidentified deposit), one debit (only to read
  // the dialog's choices). Neither matches anything in the seed.
  const LINES = [
    { n: 1, date: addDays(sastToday, -5), desc: "DEPOSIT UNKNOWN REF 12345", ref: `UNK/OBS647B/${RUN}`, amount: 4321.09 },
    { n: 2, date: sastToday, desc: "MONTHLY SERVICE FEE", ref: `SVC/OBS647B/${RUN}`, amount: -57.5 },
  ];
  const D = { today: sastToday, periodEnd: LINES.map((l) => l.date).sort().slice(-1)[0], expectedAgeDays: daysBetween(LINES[0].date, sastToday) };
  result.dates = D;
  result.lines = LINES;

  // the CSV (Standard Bank layout: 2 header lines; Date,Description,Reference,Amount,Balance; yyyy-MM-dd)
  let bal = 0;
  const csv = [`Standard Bank Trust Account - OBS 647B ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of [...LINES].sort((x, y) => x.date.localeCompare(y.date) || x.n - y.n)) {
    bal = +(bal + l.amount).toFixed(2);
    csv.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`);
  }
  const csvName = `statement-OBS647B-${RUN}.csv`;
  const csvFile = `${OUT}/${csvName}`;
  fs.writeFileSync(csvFile, csv.join("\n") + "\n");
  result.csv = csv;

  await newSession("user_e2e_alice");

  // ── baseline: Other trust creditors (Unidentified deposits before OBS) ──
  const base = (result.steps.baseline = {});
  base.txCount = txCount();
  base.itemCount = itemCount();
  base.eq = eqNow();
  base.page = await itemsPage("00-baseline", ids);

  // ════ (a) wizard: upload, auto-match, review — the unknown credit stays unmatched ════
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
  sql("a: lines at import", lineSql(ids.statement));
  for (const l of LINES) {
    const [r] = rowsA(`select id, coalesce(suggested_classification,'<null>') from T.bank_statement_lines where bank_statement_id='${ids.statement}' and description='${l.desc}'`);
    l.id = r ? r[0] : null;
    l.suggestedAtImport = r ? r[1] : "<missing>";
  }
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  a.autoMatchResult = (await page.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  a.shotAutoMatch = await snap(page, "a-auto-match-result");
  sql("a: lines after auto-match", lineSql(ids.statement));
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  a.refusalBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  ids.recon = val(`select id from T.trust_reconciliations where bank_statement_id='${ids.statement}'`);
  a.progress = (await page.getByTestId("match-progress").innerText().catch(() => "")).replace(/\s+/g, " ");
  const l1 = LINES[0];
  const l2 = LINES[1];
  a.line1Text = (await page.getByTestId(`bank-line-${l1.id}`).innerText()).replace(/\s+/g, " ");
  a.shotReview = await snap(page, "a-review-step-unmatched-line");
  await crop(page.getByTestId(`bank-line-${l1.id}`), "a-line1-crop");
  const [s1] = rowsA(`select match_status, coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where id='${l1.id}'`);
  a.line1 = { matchStatus: s1[0], trustTransactionId: s1[1], suggestedAtImport: l1.suggestedAtImport };
  a.txCountAfter = txCount();
  a.checks = {
    twoLinesImported: !!l1.id && !!l2.id, noRefusal: !a.refusalBanner, reconCreated: !!ids.recon,
    line1UnmatchedAfterAutoMatch: a.line1.matchStatus === "UNMATCHED" && a.line1.trustTransactionId === "<null>",
    line1NoSuggestion: l1.suggestedAtImport === "<null>",
    line1ShownUnmatched: /Unmatched/.test(a.line1Text) && /DEPOSIT UNKNOWN REF 12345/.test(a.line1Text),
    nothingPosted: a.txCountAfter === base.txCount,
  };

  // ════ (b) Record from statement — Unidentified deposit offered on the credit, NOT on the debit ════
  const b = (result.steps.b = {});
  async function openDialog(l) {
    await page.getByTestId(`bank-line-${l.id}`).click();
    await page.getByTestId(`record-btn-${l.id}`).click();
    const dlg = page.getByTestId("record-from-statement-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await page.waitForTimeout(300);
    const radios = await dlg.getByTestId("classification-radio").locator("button[role='radio']").evaluateAll((bs) =>
      bs.map((x) => ({ value: x.getAttribute("value"), checked: x.getAttribute("data-state") === "checked" || x.getAttribute("aria-checked") === "true",
        label: (document.querySelector(`label[for='${x.id}']`) || {}).innerText || null })));
    return { dlg, radios, text: (await dlg.innerText()).replace(/\s+/g, " ") };
  }
  // debit line first: look, screenshot, Cancel
  {
    const cnt = txCount();
    const { dlg, radios, text } = await openDialog(l2);
    b.debit = { offered: radios.map((r) => r.value), labels: radios.map((r) => r.label), text };
    b.debit.shot = await snap(page, "b-debit-line-dialog");
    await crop(dlg, "b-debit-line-dialog-crop");
    await dlg.getByRole("button", { name: "Cancel" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 15_000 });
    b.debit.txCountUnchanged = txCount() === cnt;
  }
  // credit line: choose Unidentified deposit, confirm
  b.txCountBefore = txCount();
  b.itemCountBefore = itemCount();
  {
    const { dlg, radios, text } = await openDialog(l1);
    b.credit = { offered: radios.map((r) => r.value), labels: radios.map((r) => r.label), preChecked: radios.filter((r) => r.checked).map((r) => r.value), textBefore: text };
    await dlg.locator("button[role='radio'][value='UNIDENTIFIED_DEPOSIT']").click();
    await page.waitForTimeout(200);
    b.credit.noteVisible = await dlg.getByTestId("classification-unidentified-note").isVisible().catch(() => false);
    b.credit.noteText = await dlg.getByTestId("classification-unidentified-note").innerText().catch(() => null);
    b.credit.chargeFieldsVisible = await dlg.getByTestId("charge-vat-input").isVisible().catch(() => false);
    await dlg.getByTestId("record-notes-input").fill(`OBS 647B ${RUN} unknown credit`);
    b.credit.shot = await snap(page, "b-credit-line-unidentified-chosen");
    await crop(dlg, "b-credit-line-dialog-crop");
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  }
  b.checks = {
    debitDoesNotOfferUnidentified: !b.debit.offered.includes("UNIDENTIFIED_DEPOSIT") && b.debit.offered.length > 0,
    debitCancelPostedNothing: b.debit.txCountUnchanged,
    creditOffersUnidentified: b.credit.offered.includes("UNIDENTIFIED_DEPOSIT") && b.credit.labels[b.credit.offered.indexOf("UNIDENTIFIED_DEPOSIT")] === "Unidentified deposit",
    creditNothingPreselected: b.credit.preChecked.length === 0,
    unidentifiedNoteShown: b.credit.noteVisible,
  };

  // ════ (c) the line shows "Recorded: Unidentified deposit" ════
  const c = (result.steps.c = {});
  const badge = page.getByTestId(`recorded-badge-${l1.id}`);
  await badge.waitFor({ timeout: 30_000 });
  c.badge = (await badge.innerText()).trim();
  c.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  c.lineText = (await page.getByTestId(`bank-line-${l1.id}`).innerText()).replace(/\s+/g, " ");
  c.shot = await snap(page, "c-line-recorded-unidentified");
  await crop(page.getByTestId(`bank-line-${l1.id}`), "c-line1-recorded-crop");
  c.lineSql = sql("c: line 1 after record", lineSql(ids.statement));
  const [ls] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where id='${l1.id}'`);
  c.line = { matchStatus: ls[0], classification: ls[1], trustTransactionId: ls[2] };
  c.txCountAfter = txCount();
  c.itemCountAfter = itemCount();
  c.checks = {
    badgeLabel: c.badge === "Recorded: Unidentified deposit", noRawEnum: !/UNIDENTIFIED_DEPOSIT/.test(c.lineText), noError: !c.errorBanner,
    lineRecorded: c.line.matchStatus === "RECORDED" && c.line.classification === "UNIDENTIFIED_DEPOSIT",
    oneRowCreated: c.txCountAfter === b.txCountBefore + 1, oneItemCreated: c.itemCountAfter === b.itemCountBefore + 1,
  };

  // ════ (d) Other trust creditors: the item is Held, UI = API = SQL; ledger + equation ════
  const d = (result.steps.d = {});
  d.itemSql = sql("d: the item", itemSql(`d.bank_statement_line_id='${l1.id}'`));
  const [it] = rowsA(`select id, deposit_transaction_id, received_date, bank_description, coalesce(bank_reference,''), amount, status, coalesce(resolved_date::text,'<null>'), creditor_account_id from T.unidentified_deposits where bank_statement_line_id='${l1.id}'`);
  d.item = it ? { id: it[0], depositTransactionId: it[1], receivedDate: it[2], description: it[3], reference: it[4], amount: Number(it[5]), status: it[6], resolvedDate: it[7], creditorAccountId: it[8] } : null;
  d.rowSql = sql("d: the deposit row", rowSql(`t.bank_statement_line_id='${l1.id}'`));
  const [dr] = rowsA(`select id, transaction_type, status, amount, transaction_date, ledger, ledger_effect, cashbook_effect, posted_at is not null, coalesce(unidentified_deposit_id::text,'<null>'), creditor_account_id from T.trust_transactions where bank_statement_line_id='${l1.id}'`);
  d.row = dr ? { id: dr[0], type: dr[1], status: dr[2], amount: Number(dr[3]), date: dr[4], ledger: dr[5], ledgerEffect: Number(dr[6]), cashbookEffect: Number(dr[7]), posted: dr[8] === "t", unidentifiedDepositId: dr[9], creditorAccountId: dr[10] } : null;
  d.page = await itemsPage("d-after-record", ids);
  d.eq = eqNow();
  sql("d: EQ-2 as at today", eqSql(ids.account, sastToday));
  const apiItem = ((d.page.api && d.page.api.content) || []).find((x) => d.item && x.id === d.item.id);
  const uiItem = d.page.uiRows[d.page.uiRows.length - 1]; // oldest first; the OBS item is the only one on a fresh stack
  const dmy = dayMonYear(l1.date);
  d.figures = {
    expectedAgeDays: D.expectedAgeDays, receivedDate: { sql: d.item && d.item.receivedDate, api: apiItem && apiItem.receivedDate, ui: uiItem && uiItem.cells[0] },
    description: { sql: d.item && d.item.description, api: apiItem && apiItem.bankDescription, ui: uiItem && uiItem.cells[1] },
    amount: { sql: d.item && d.item.amount, api: apiItem && apiItem.amount, ui: uiItem && uiItem.amount },
    ageDays: { api: apiItem && apiItem.ageDays, ui: uiItem && uiItem.ageText },
    status: { sql: d.item && d.item.status, api: apiItem && apiItem.status, ui: uiItem && uiItem.status },
    ledgerBalance: { ui: d.page.cardValue, api: d.page.cardApi, sql: d.page.sqlBalance },
  };
  d.checks = {
    oneUiRow: d.page.uiRowCount === 1 && d.page.uiRowCount === (d.page.api.content || []).length,
    itemHeldSql: !!d.item && d.item.status === "HELD" && d.item.resolvedDate === "<null>",
    itemLinkedBothWays: !!d.item && !!d.row && d.row.unidentifiedDepositId === d.item.id && d.item.depositTransactionId === d.row.id,
    rowShape: !!d.row && d.row.type === "UNIDENTIFIED_DEPOSIT" && d.row.status === "RECORDED" && d.row.ledger === "NON_CLIENT" && d.row.posted && d.row.date === l1.date && eq(d.row.amount, l1.amount) && eq(d.row.ledgerEffect, l1.amount) && eq(d.row.cashbookEffect, l1.amount) && d.row.creditorAccountId === ids.udAccount,
    receivedDateUiApiSql: !!apiItem && d.item.receivedDate === l1.date && apiItem.receivedDate === l1.date && !!uiItem && uiItem.cells[0].includes(String(dmy.d)) && uiItem.cells[0].includes(dmy.mon) && uiItem.cells[0].includes(String(dmy.y)),
    descriptionUiApiSql: !!apiItem && d.item.description === l1.desc && apiItem.bankDescription === l1.desc && uiItem.cells[1].includes(l1.desc) && uiItem.cells[1].includes(l1.ref) && apiItem.bankReference === l1.ref,
    amountUiApiSql: !!apiItem && eq(d.item.amount, l1.amount) && eq(apiItem.amount, l1.amount) && eq(uiItem.amount, l1.amount),
    ageUiApiExpected: !!apiItem && apiItem.ageDays === D.expectedAgeDays && (uiItem.ageText || "").trim() === (D.expectedAgeDays === 1 ? "1 day" : `${D.expectedAgeDays} days`) && apiItem.olderThanOneYear === false && !uiItem.overOneYear,
    statusHeldUiApiSql: !!apiItem && apiItem.status === "HELD" && uiItem.status === "Held",
    heldFilterApi: !!d.page.apiHeld && d.page.apiHeld.page && d.page.apiHeld.page.totalElements === 1,
    ledgerCardUiApiSql: eq(d.page.cardValue, d.page.cardApi) && eq(d.page.cardApi, d.page.sqlBalance) && eq(d.page.sqlBalance, base.page.sqlBalance + l1.amount),
    equationHolds: eq(d.eq.cashbook, d.eq.allCreditors) && eq(d.eq.cashbook, base.eq.cashbook + l1.amount) && eq(d.eq.unidentified, base.eq.unidentified + l1.amount),
  };

  // ════ (e) unmatch the recorded line through the confirmation ════
  const e = (result.steps.e = {});
  e.txCountBefore = txCount();
  await page.bringToFront();
  await page.getByTestId(`unmatch-btn-${l1.id}`).click();
  const ud = page.getByTestId("unmatch-recorded-dialog");
  await ud.waitFor({ timeout: 15_000 });
  e.dialogText = (await ud.innerText()).replace(/\s+/g, " ");
  e.shotDialog = await snap(page, "e-unmatch-recorded-confirmation");
  await crop(ud, "e-unmatch-dialog-crop");
  await ud.getByTestId("unmatch-recorded-confirm").click();
  await ud.waitFor({ state: "hidden", timeout: 30_000 });
  for (let i = 0; i < 60 && (await page.getByTestId(`recorded-badge-${l1.id}`).count()) > 0; i++) await page.waitForTimeout(250);
  await page.waitForTimeout(1500);
  e.lineText = (await page.getByTestId(`bank-line-${l1.id}`).innerText()).replace(/\s+/g, " ");
  e.errorBanner = await page.getByTestId("reconciliation-error").innerText().catch(() => null);
  e.shotAfter = await snap(page, "e-after-unmatch");
  await crop(page.getByTestId(`bank-line-${l1.id}`), "e-line1-unmatched-crop");
  e.itemSql = sql("e: the item after unmatch", itemSql(`d.id='${d.item.id}'`));
  e.originalSql = sql("e: original after unmatch", rowSql(`t.id='${d.row.id}'`));
  e.contraSql = sql("e: contra", rowSql(`t.reversal_of='${d.row.id}'`));
  e.itemRowsSql = sql("e: every row naming the item", rowSql(`t.unidentified_deposit_id='${d.item.id}'`));
  e.lineSql = sql("e: lines after unmatch", lineSql(ids.statement));
  const [ei] = rowsA(`select status, coalesce(resolved_date::text,'<null>') from T.unidentified_deposits where id='${d.item.id}'`);
  e.item = { status: ei[0], resolvedDate: ei[1] };
  const [orig] = rowsA(`select status, coalesce(reversed_by_id::text,'') from T.trust_transactions where id='${d.row.id}'`);
  const [con] = rowsA(`select id, transaction_type, transaction_date, ledger_effect, cashbook_effect, coalesce(unidentified_deposit_id::text,'<null>'), creditor_account_id from T.trust_transactions where reversal_of='${d.row.id}'`);
  e.original = { id: d.row.id, status: orig[0], reversedBy: orig[1] };
  e.contra = con ? { id: con[0], type: con[1], date: con[2], ledgerEffect: Number(con[3]), cashbookEffect: Number(con[4]), unidentifiedDepositId: con[5], creditorAccountId: con[6] } : null;
  e.itemLedgerNet = Number(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where unidentified_deposit_id='${d.item.id}' and posted_at is not null`));
  const [l1s] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where id='${l1.id}'`);
  e.line = { matchStatus: l1s[0], classification: l1s[1], trustTransactionId: l1s[2] };
  e.txCountAfter = txCount();
  e.page = await itemsPage("e-after-unmatch", ids);
  e.eq = eqNow();
  sql("e: EQ-2 as at today", eqSql(ids.account, sastToday));
  const apiItemE = ((e.page.api && e.page.api.content) || []).find((x) => x.id === d.item.id);
  const uiItemE = e.page.uiRows[e.page.uiRows.length - 1];
  e.checks = {
    dialogSaysReversed: /Unmatch recorded line\?/.test(e.dialogText) && /recorded as Unidentified deposit/.test(e.dialogText) && /reverses the entry/.test(e.dialogText) && /the line's date/.test(e.dialogText),
    lineUnmatchedUi: /Unmatched/.test(e.lineText) && !/Recorded:/.test(e.lineText), noError: !e.errorBanner,
    lineUnmatchedSql: e.line.matchStatus === "UNMATCHED" && e.line.classification === "<null>" && e.line.trustTransactionId === "<null>",
    itemReversedSql: e.item.status === "REVERSED" && e.item.resolvedDate === l1.date,
    originalReversed: e.original.status === "REVERSED" && !!e.contra && e.original.reversedBy === e.contra.id,
    contraShape: !!e.contra && e.contra.type === "REVERSAL" && e.contra.date === l1.date && e.contra.unidentifiedDepositId === d.item.id && eq(e.contra.ledgerEffect, -l1.amount) && e.contra.creditorAccountId === ids.udAccount,
    oneRowCreated: e.txCountAfter === e.txCountBefore + 1,
    itemLedgerNetsToZero: eq(e.itemLedgerNet, 0),
    tableShowsReversed: !!uiItemE && uiItemE.status === "Reversed" && !uiItemE.ageText && !!apiItemE && apiItemE.status === "REVERSED" && apiItemE.resolvedDate === l1.date,
    heldFilterEmpty: !!e.page.apiHeld && e.page.apiHeld.page && e.page.apiHeld.page.totalElements === 0,
    ledgerBackToBaseline: eq(e.page.cardValue, base.page.cardValue) && eq(e.page.cardApi, e.page.sqlBalance) && eq(e.page.sqlBalance, base.page.sqlBalance),
    equationHolds: eq(e.eq.cashbook, e.eq.allCreditors) && eq(e.eq.cashbook, base.eq.cashbook) && eq(e.eq.unidentified, base.eq.unidentified),
  };

  // ════ (f-prep, supplementary) data for the clip probe: both tables need real rows ════
  // Through the UI: the debit line recorded as a Bank charge (a Fund ledger row), and the unknown credit
  // recorded again as an Unidentified deposit (a new HELD item next to the REVERSED one).
  const fp = (result.steps.fPrep = {});
  {
    const { dlg } = await openDialog(l2);
    await dlg.locator("button[role='radio'][value='BANK_CHARGE']").click();
    await dlg.locator("#charge-recoverable-no").click();
    await dlg.getByTestId("charge-vat-input").fill("7.50");
    await dlg.getByTestId("record-notes-input").fill(`OBS 647B ${RUN} probe prep`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await page.getByTestId(`recorded-badge-${l2.id}`).waitFor({ timeout: 30_000 });
    fp.line2Badge = (await page.getByTestId(`recorded-badge-${l2.id}`).innerText()).trim();
  }
  {
    const { dlg, radios } = await openDialog(l1);
    fp.line1ReopenOffered = radios.map((r) => r.value);
    await dlg.locator("button[role='radio'][value='UNIDENTIFIED_DEPOSIT']").click();
    await dlg.getByTestId("record-notes-input").fill(`OBS 647B ${RUN} re-record`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await page.getByTestId(`recorded-badge-${l1.id}`).waitFor({ timeout: 30_000 });
    fp.line1Badge = (await page.getByTestId(`recorded-badge-${l1.id}`).innerText()).trim();
  }
  fp.shot = await snap(page, "fprep-both-lines-recorded");
  sql("f-prep: items after re-record", itemSql(`d.trust_account_id='${ids.account}'`));
  fp.page = await itemsPage("fprep-after-rerecord", ids);
  fp.eq = eqNow();
  sql("f-prep: EQ-2 as at today", eqSql(ids.account, sastToday));
  fp.checks = {
    line2Recorded: fp.line2Badge === "Recorded: Bank charge", line1RecordedAgain: fp.line1Badge === "Recorded: Unidentified deposit",
    twoItemsReversedThenHeld: fp.page.uiRows.length === 2 && fp.page.uiRows.map((u) => u.status).sort().join(",") === "Held,Reversed",
    equationHolds: eq(fp.eq.cashbook, fp.eq.allCreditors),
  };

  sql("final: OBS statement lines", lineSql(ids.statement));
  sql("final: OBS rows", rowSql(`t.bank_statement_line_id in (select id from T.bank_statement_lines where bank_statement_id='${ids.statement}') or t.reversal_of in (select id from T.trust_transactions where bank_statement_line_id in (select id from T.bank_statement_lines where bank_statement_id='${ids.statement}'))`));
  sql("final: EQ-2 as at today", eqSql(ids.account, sastToday));

  const allTrue = (o) => !!o && Object.values(o).every(Boolean);
  result.verdict = {
    a: allTrue(a.checks) ? "PASS" : "FAIL", b: allTrue(b.checks) ? "PASS" : "FAIL", c: allTrue(c.checks) ? "PASS" : "FAIL",
    d: allTrue(d.checks) ? "PASS" : "FAIL", e: allTrue(e.checks) ? "PASS" : "FAIL", fPrep: allTrue(fp.checks) ? "PASS" : "FAIL",
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
