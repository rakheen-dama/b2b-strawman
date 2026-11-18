// 648B OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Helpers copied from 648E gates-6b4615074/obs.cjs (648A lineage). Every state change goes through the UI:
//  - Alice imports bank statements in the reconciliation wizard (Trust › Reconciliation › New) on the lifecycle's
//    section 86(4) and section 86(3) investment accounts, records the 400,00 credit as section 86(4) investment
//    interest (GROSS) from the statement line, places a fresh Moroka s86(4) investment and records a NET bank advice
//    through the Record interest dialog; Bob (admin) approves the placement on Trust › Transactions.
//  - psql is read-only EVIDENCE; API reads are GETs with the browser context's own mock-auth-token against :8081.
// Steps: (b0) the lifecycle's NET 95,00 entry on QuickCollect; (a) GROSS 400,00 from a statement line;
// (b1) a fresh NET bank advice on a fresh Moroka s86(4) investment; (c) s86(3): no interest action on the register,
// no "Section 86(4) investment interest" classification in the wizard; (d) raw-enum scans (word-boundary regexes);
// (e) clip probes; (f) console / page errors.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648B";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const { measureIn } = require("./measure.cjs");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const ORG = `${BASE}/org/e2e-test-org`;
const EV = `${W}/qa_cycle/evidence/phase92/648B/gates-c952121f6`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const P = 7500;
const result = { steps: {}, consoleErrors: [], pageErrors: [], posts: [], notes: [], scans: [], clips: {}, startedAt: new Date().toISOString() };
const save = () => fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;
const TODAY = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
result.today = TODAY;

// ── read-only SQL evidence ──────────────────────────────────────────
function psqlRaw(args) {
  for (let i = 0; i < 4; i++) {
    try { return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", ...args], { encoding: "utf8" }); }
    catch (e) { if (i === 3) throw e; execFileSync("sleep", ["1"]); }
  }
}
const psql = (q) => psqlRaw(["-v", "ON_ERROR_STOP=1", "-c", q]);
const psqlA = (q) => psqlRaw(["-At", "-F", "|", "-c", q]).trim();
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(SQL_FILE, `-- 648B OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 648B OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
const num = (x) => (x == null || x === "" ? 0 : Number(x));
const eq = (x, y) => x != null && y != null && Math.abs(Number(x) - Number(y)) < 0.005;
async function waitSql(q, pred, ms = 30_000) {
  const t0 = Date.now(); let v = val(q);
  while (!pred(v) && Date.now() - t0 < ms) { await new Promise((r) => setTimeout(r, 500)); v = val(q); }
  return v;
}
const ids = {};

// ── browser helpers ─────────────────────────────────────────────────
let browser;
const S = {};
function watch(p, tag) {
  p.on("console", (m) => { if (m.type() === "error") result.consoleErrors.push({ tab: tag, url: p.url(), text: m.text() }); });
  p.on("pageerror", (e) => result.pageErrors.push({ tab: tag, url: p.url(), text: String(e) }));
  p.on("request", (r) => { if (r.method() === "POST") result.posts.push({ tab: tag, at: new Date().toISOString(), url: r.url(), nextAction: r.headers()["next-action"] || null }); });
}
async function newSession(name, userId) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  watch(p, name);
  await p.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption(userId);
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.getByRole("heading").first().waitFor({ timeout: 30_000 });
  await p.waitForLoadState("networkidle").catch(() => {}); // settle (spurious userinfo abort otherwise)
  S[name] = { ctx, p };
  return S[name];
}
let shot = 0;
const norm = (s) => (s || "").replace(/[\s  ]+/g, " ").trim();
const money = (s) => { const m = norm(s).replace(/[^\d,\-−]/g, "").replace("−", "-").replace(",", "."); return m === "" ? null : Number(m); };
const amounts = (s) => (norm(s).match(/-?R ?\d{1,3}(?: \d{3})*,\d{2}/g) || []).map(money);
async function snap(p, name, widths = [1440, 1280]) {
  shot += 1;
  const files = [];
  for (const w of widths) {
    await p.setViewportSize({ width: w, height: 1100 });
    await p.waitForTimeout(500);
    const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}-${w}.png`;
    await p.screenshot({ path: file, fullPage: true });
    files.push(file.replace(`${OUT}/`, ""));
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(300);
  fs.writeFileSync(`${OUT}/${String(shot).padStart(2, "0")}-${name}.txt`, await p.locator("body").innerText().catch(() => ""));
  return files;
}
async function crop(locator, name) {
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await locator.screenshot({ path: file }).catch((e) => result.notes.push(`crop ${name} failed: ${String(e).slice(0, 120)}`));
  return file.replace(`${OUT}/`, "");
}
async function token(ctx) { const t = (await ctx.cookies()).find((c) => c.name === "mock-auth-token"); return t ? t.value : ""; }
async function apiGet(ctx, path, saveAs) {
  const res = await ctx.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token(ctx)}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { method: "GET", path, status: res.status(), body };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
  return r;
}

// ── UI writes ───────────────────────────────────────────────────────
async function placeViaUi(p, o) {
  const s = { ...o };
  await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  await p.getByRole("button", { name: "Place Investment" }).first().click();
  const dlg = p.getByTestId("place-investment-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  await dlg.getByRole("radio", { name: "Section 86(4) — invested on a client's instruction" }).check();
  await p.waitForTimeout(300);
  await dlg.getByTestId("investment-account-select").click();
  await p.getByRole("listbox").waitFor({ timeout: 10_000 });
  s.pickerOptions = (await p.getByRole("option").allInnerTexts()).map(norm);
  await p.getByRole("option", { name: new RegExp(`^${o.accountName.replace(/[()]/g, "\\$&")} `) }).first().click();
  await p.waitForTimeout(300);
  s.pickerText = norm(await dlg.getByTestId("investment-account-select").innerText());
  await dlg.getByLabel("Client ID").fill(o.client);
  await dlg.getByLabel("Matter ID").fill(o.matter);
  await dlg.getByLabel("Institution").fill(o.institution);
  await dlg.getByLabel("Account Number").fill(o.depositNumber);
  await dlg.getByLabel("Principal").fill(String(o.principal));
  await dlg.getByLabel("Interest Rate %").fill(String(o.rate));
  await dlg.getByLabel("Notes (optional)").fill(`OBS ${RUN} 648B s86(4)`);
  s.filesFilled = await snap(p, "b1-place-dialog-filled", [1440]);
  await crop(dlg, "b1-place-dialog-filled-crop");
  const nInv = Number(val("select count(*) from T.trust_investments"));
  await dlg.getByRole("button", { name: "Place Investment" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  s.investmentsCountBefore = nInv;
  s.investmentsCountAfter = Number(await waitSql("select count(*) from T.trust_investments", (v) => Number(v) === nInv + 1));
  const [inv] = rowsA(`select id, deposit_transaction_id, investment_basis, customer_id, project_id, investment_account_id, trust_account_id, status, principal from T.trust_investments where account_number='${o.depositNumber}'`);
  s.investmentDb = { id: inv[0], depositTx: inv[1], basis: inv[2], customerId: inv[3], projectId: inv[4], investmentAccountId: inv[5], fundingAccountId: inv[6], status: inv[7], principal: inv[8] };
  return s;
}
async function approveViaUi(p, txId) {
  const s = { txId };
  await p.goto(`${ORG}/trust-accounting/transactions`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
  const row = p.getByTestId(`transaction-row-${txId}`);
  await row.waitFor({ timeout: 30_000 });
  s.rowTextBefore = norm(await row.innerText());
  s.files = await snap(p, "b3-transactions-awaiting", [1440]);
  await crop(row, "b3-transactions-row-awaiting-crop");
  await p.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve" }).click();
  await p.waitForFunction((id) => { const r = document.querySelector(`[data-testid='transaction-row-${id}']`); return r && /approved/i.test(r.innerText) && !/awaiting/i.test(r.innerText); }, txId, { timeout: 30_000 });
  s.rowTextAfter = norm(await row.innerText());
  await crop(row, "b3-transactions-row-approved-crop");
  return s;
}

const allTrue = (o) => Object.values(o || {}).every(Boolean);
// ── 648B helpers ────────────────────────────────────────────────────
// (d) raw-enum scan: case-sensitive WORD-BOUNDARY regexes on textContent (so "NET" never matches inside a word)
const RAW_RX = [/\bGROSS\b/, /\bNET\b/, /\bINVESTMENT_INTEREST\b/, /\bINV_INTEREST\b/, /\bFUND_INV_SHARE\b/, /\bCLIENT_INSTRUCTION\b/, /\bFIRM_DISCRETION\b/, /\bPOSTED\b/, /\bREVERSED\b/];
result.rawRegexes = RAW_RX.map(String);
async function scanRaw(locator, label) {
  const content = await locator.evaluate((el) => el.textContent || "");
  const r = { label, chars: content.length, hits: RAW_RX.filter((rx) => rx.test(content)).map(String) };
  r.clean = r.hits.length === 0;
  fs.writeFileSync(`${OUT}/scan-${label}.txt`, content);
  result.scans.push(r);
  return r;
}
// (e) clip probe: measureIn on the root + page horizontal scroll + the overflow-x wrapper + every money figure
async function clip(p, label, rootSel, { widths = [1280, 1440, 1920], wrapperSel = null, figSel = "span, p, dd" } = {}) {
  const res = [];
  for (const w of widths) {
    await p.setViewportSize({ width: w, height: 1100 });
    await p.waitForTimeout(700);
    const m = await p.evaluate(measureIn, { rootSel, opts: {} });
    m.width = w;
    m.page = await p.evaluate(() => ({ docScrollWidth: document.documentElement.scrollWidth, docClientWidth: document.documentElement.clientWidth,
      pageHorizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1 }));
    m.wrapper = wrapperSel ? await p.evaluate((sel) => { const e = document.querySelector(sel); return e ? { scrollWidth: e.scrollWidth, clientWidth: e.clientWidth, overflows: e.scrollWidth > e.clientWidth + 1 } : { missing: true }; }, wrapperSel) : null;
    m.figures = await p.evaluate(({ sel, figSel }) => {
      const root = document.querySelector(sel); if (!root) return [];
      const vw = document.documentElement.clientWidth;
      const rx = /R[\s  ]?-?\d{1,3}(?:[\s  ]\d{3})*,\d{2}/;
      return [...root.querySelectorAll(figSel)].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && rx.test(n.textContent))).map((e) => {
        const r = e.getBoundingClientRect();
        const lh = parseFloat(getComputedStyle(e).lineHeight) || 16;
        // the figure itself must sit on one line: measure the money substring with a Range
        const tn = [...e.childNodes].find((n) => n.nodeType === 3 && rx.test(n.textContent));
        const mm = tn.textContent.match(rx); const range = document.createRange();
        range.setStart(tn, mm.index); range.setEnd(tn, mm.index + mm[0].length);
        const rects = [...range.getClientRects()].filter((x) => x.width > 0);
        const tops = new Set(rects.map((x) => Math.round(x.top)));
        return { text: (e.innerText || "").replace(/\s+/g, " ").trim().slice(0, 90), left: Math.round(r.left), right: Math.round(r.right), lines: Math.round(r.height / lh),
          figureOnOneLine: tops.size <= 1, insideViewport: r.left >= -0.5 && r.right <= vw + 0.5,
          clipped: e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflowX !== "visible" };
      });
    }, { sel: rootSel, figSel });
    m.figuresOk = m.figures.length > 0 && m.figures.every((f) => f.insideViewport && !f.clipped && f.figureOnOneLine);
    res.push(m);
    const el = p.locator(rootSel).first();
    if (await el.count()) await el.screenshot({ path: `${OUT}/e-${label}-${w}-crop.png` }).catch(() => {});
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(300);
  result.clips[label] = res.map((m) => ({ width: m.width, ok: m.ok, figuresOk: m.figuresOk, pageHorizontalScroll: m.page.pageHorizontalScroll, wrapper: m.wrapper, root: m.root,
    offenders: m.offenders, truncated: m.truncated, figures: m.figures }));
  return res;
}
const subRx = (label) => new RegExp(`${label.replace(/[()%]/g, "\\$&")} (-?R ?\\d{1,3}(?: \\d{3})*,\\d{2})`);
// The register row of one investment (by deposit account number): its sub-lines and balance
async function registerRow(p, depositNumber, label) {
  await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  const row = p.locator("main tbody tr").filter({ hasText: depositNumber }).first();
  await row.waitFor({ timeout: 30_000 });
  const text = norm(await row.innerText());
  const cells = (await row.locator("td").allInnerTexts()).map(norm);
  const g = (l) => { const m = text.match(subRx(l)); return m ? money(m[1]) : null; };
  const u = { label, depositNumber, text, cells, gross: g("Gross interest"), fund: g("Fund 5%"), net: g("Net interest"),
    balance: money(cells[5] || ""), principal: amounts(cells[4] || "")[0] ?? null,
    hasRecordInterest: (await row.getByRole("button", { name: "Record interest", exact: true }).count()) > 0,
    hasWithdraw: (await row.getByRole("button", { name: "Withdraw", exact: true }).count()) > 0,
    fundNote: (await row.getByTestId("investment-interest-fund-note").count()) ? norm(await row.getByTestId("investment-interest-fund-note").innerText()) : null };
  return { u, row };
}
async function registerSnap(p, label) {
  const files = await snap(p, `register-${label}`);
  await crop(p.getByTestId("investments-table"), `register-${label}-table-crop`);
  const sc = await scanRaw(p.locator("main").first(), `register-${label}`);
  return { files, scan: sc };
}
async function apiInvestments(ctx, key) {
  const r = await apiGet(ctx, `/api/trust-accounts/${ids.funding}/investments?page=0&size=100`, key);
  const rows = (r.body && (r.body.content || r.body)) || [];
  return { status: r.status, rows: Array.isArray(rows) ? rows : [] };
}
const pickInv = (rows, id) => { const x = rows.find((r) => r.id === id); return x && { grossInterest: Number(x.grossInterest), fundShare: Number(x.fundShare), netInterest: Number(x.netInterest), balance: Number(x.balance), status: x.status, hasInterestEarnedKey: Object.prototype.hasOwnProperty.call(x, "interestEarned") }; };
function sqlEntries(label, invId) {
  sql(`${label}: trust_investment_interest rows`, `select e.id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line, e.net_transaction_id, e.fund_share_transaction_id from T.trust_investment_interest e where e.investment_id='${invId}' order by e.created_at`);
  sql(`${label}: the investment's interest rows in trust_transactions`, `select t.id, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.status, t.posted_at is not null posted, a.account_name, t.bank_statement_line_id is not null has_line, t.reference from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.investment_id='${invId}' and t.transaction_type in ('INV_INTEREST','FUND_INV_SHARE') order by t.created_at`);
  const entries = rowsA(`select e.id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, coalesce(e.bank_statement_line_id::text,''), e.net_transaction_id, coalesce(e.fund_share_transaction_id::text,'') from T.trust_investment_interest e where e.investment_id='${invId}' order by e.created_at`)
    .map((r) => ({ id: r[0], entryDate: r[1], gross: num(r[2]), fund: num(r[3]), net: num(r[4]), presentation: r[5], status: r[6], lineId: r[7] || null, netTx: r[8], fundTx: r[9] || null }));
  const rows = rowsA(`select t.id, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.status, t.posted_at is not null, coalesce(t.bank_statement_line_id::text,'') from T.trust_transactions t where t.investment_id='${invId}' and t.transaction_type in ('INV_INTEREST','FUND_INV_SHARE') order by t.created_at`)
    .map((r) => ({ id: r[0], type: r[1], amount: num(r[2]), ledger: r[3], ledgerEffect: num(r[4]), cashbookEffect: num(r[5]), status: r[6], posted: r[7] === "t", lineId: r[8] || null }));
  const balance = num(val(`select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t where t.investment_id='${invId}' and t.trust_account_id=(select investment_account_id from T.trust_investments where id='${invId}') and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null`));
  sql(`${label}: investment balance (CLIENT_INVESTMENT rows of the investment, posted)`, `select coalesce(sum(t.ledger_effect),0) balance from T.trust_transactions t where t.investment_id='${invId}' and t.trust_account_id=(select investment_account_id from T.trust_investments where id='${invId}') and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null`);
  return { entries, rows, balance };
}
async function clientCard(ctx, key, client) {
  const r = await apiGet(ctx, `/api/trust-accounts/${ids.funding}/client-ledgers/${client}`, key);
  return { status: r.status, balance: Number(r.body.balance), investedBalance: Number(r.body.investedBalance) };
}
const uiIsChecked = async (loc) => (await loc.getAttribute("data-state")) === "checked" || (await loc.getAttribute("aria-checked")) === "true";

// The reconciliation wizard: select the account + period end, import the CSV, auto-match, start the review
async function wizardToReview(p, accountId, csvPath, label) {
  const s = { accountId, csv: csvPath.replace(`${OUT}/`, "") };
  await p.goto(`${ORG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await p.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await p.getByTestId("account-select").locator(`option[value='${accountId}']`).waitFor({ state: "attached", timeout: 30_000 });
  await p.getByTestId("account-select").selectOption(accountId);
  await p.getByTestId("period-end-input").fill(TODAY);
  s.files1 = await snap(p, `${label}-wizard-step1`, [1440]);
  await p.getByTestId("step-select").getByTestId("step-next-btn").click();
  const up = p.getByTestId("bank-statement-upload");
  await up.waitFor({ timeout: 30_000 });
  const nSt = Number(val(`select count(*) from T.bank_statements where trust_account_id='${accountId}'`));
  await up.getByTestId("file-input").setInputFiles(csvPath);
  await p.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  s.uploadResult = norm(await p.getByTestId("upload-result").innerText());
  s.files2 = await snap(p, `${label}-wizard-uploaded`, [1440]);
  s.statementsBefore = nSt;
  s.statementsAfter = Number(await waitSql(`select count(*) from T.bank_statements where trust_account_id='${accountId}'`, (v) => Number(v) === nSt + 1));
  s.statementId = val(`select id from T.bank_statements where trust_account_id='${accountId}' order by created_at desc limit 1`);
  await p.getByRole("button", { name: "Next" }).last().click();
  await p.getByTestId("step-auto-match").waitFor({ timeout: 30_000 });
  await p.getByTestId("auto-match-btn").click();
  await p.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  s.autoMatch = norm(await p.getByTestId("auto-match-result").innerText());
  await p.getByTestId("step-auto-match").getByTestId("step-next-btn").click();
  await p.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  await p.getByTestId("interest-investments-loading").waitFor({ state: "detached", timeout: 30_000 }).catch(() => {});
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  s.investmentsError = (await p.getByTestId("interest-investments-error").count()) ? norm(await p.getByTestId("interest-investments-error").innerText()) : null;
  sql(`${label}: the imported statement's lines`, `select l.id, l.line_number, l.transaction_date, l.description, l.amount, l.match_status, l.suggested_classification, l.classification from T.bank_statement_lines l where l.bank_statement_id='${s.statementId}' order by l.line_number`);
  s.lines = rowsA(`select l.id, l.line_number, l.transaction_date, l.amount, l.match_status, coalesce(l.suggested_classification,''), coalesce(l.classification,'') from T.bank_statement_lines l where l.bank_statement_id='${s.statementId}' order by l.line_number`)
    .map((r) => ({ id: r[0], n: Number(r[1]), date: r[2], amount: num(r[3]), matchStatus: r[4], suggested: r[5] || null, classification: r[6] || null }));
  s.files4 = await snap(p, `${label}-wizard-review`);
  s.scanReview = await scanRaw(p.locator("main").first(), `${label}-wizard-review`);
  return s;
}
async function openRecordFromStatement(p, lineId) {
  // the Record action renders only for the SELECTED unmatched line (ReconciliationSplitPane: isSelected) — attempt 1 missed this
  await p.getByTestId(`bank-line-${lineId}`).click();
  await p.getByTestId(`record-btn-${lineId}`).click();
  const dlg = p.getByTestId("record-from-statement-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  await p.waitForTimeout(400);
  const labels = (await dlg.getByTestId("classification-radio").locator("label").allInnerTexts()).map(norm);
  return { dlg, labels };
}

// ── main ────────────────────────────────────────────────────────────
(async () => {
  browser = await chromium.launch({ headless: true });
  await newSession("alice", "user_e2e_alice");
  await newSession("bob", "user_e2e_bob");
  const A = S.alice;
  ids.funding = val("select id from T.trust_accounts where account_type<>'INVESTMENT' and is_primary order by created_at limit 1");
  ids.qc = val("select id from T.customers where name ilike 'QuickCollect%' order by created_at limit 1");
  ids.qcName = val(`select name from T.customers where id='${ids.qc}'`);
  ids.moroka = val("select id from T.customers where name ilike 'Moroka%' order by created_at limit 1");
  ids.morokaMatter = val(`select project_id from T.trust_transactions where customer_id='${ids.moroka}' and trust_account_id='${ids.funding}' and ledger='CLIENT' and posted_at is not null and project_id is not null group by 1 order by sum(ledger_effect) desc limit 1`);
  ids.inv4 = val("select id from T.trust_accounts where account_type='INVESTMENT' and investment_basis='CLIENT_INSTRUCTION' order by created_at limit 1");
  ids.inv4Name = val(`select account_name from T.trust_accounts where id='${ids.inv4}'`);
  ids.inv3 = val("select id from T.trust_accounts where account_type='INVESTMENT' and investment_basis='FIRM_DISCRETION' order by created_at limit 1");
  ids.inv3Name = val(`select account_name from T.trust_accounts where id='${ids.inv3}'`);
  ids.qcInv = val("select id from T.trust_investments where account_number='CALL-1122334455'");
  ids.firmInv = val("select id from T.trust_investments where account_number='FD-0987654321'");
  ids.activeS864 = rowsA("select i.id, i.account_number, i.status, coalesce(c.name,'') from T.trust_investments i left join T.customers c on c.id=i.customer_id where i.investment_basis='CLIENT_INSTRUCTION' order by i.created_at");
  result.ids = ids;
  sql("context: trust accounts", "select id, account_name, account_type, investment_basis, is_primary, status from T.trust_accounts order by created_at");
  sql("context: investments after the lifecycle", "select i.id, i.account_number, i.investment_basis, i.status, i.principal, i.deposit_date, c.name client, ia.account_name holding from T.trust_investments i left join T.customers c on c.id=i.customer_id join T.trust_accounts ia on ia.id=i.investment_account_id order by i.created_at");
  sql("context: interest entries after the lifecycle", "select e.investment_id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line from T.trust_investment_interest e order by e.created_at");
  save();

  // ── b0: the lifecycle's day-60 NET bank advice on QuickCollect (gross 100,00) ──
  const b0 = (result.steps.b0 = {});
  b0.reg = (await registerRow(A.p, "CALL-1122334455", "b0")).u;
  b0.snap = await registerSnap(A.p, "b0-after-lifecycle");
  b0.firmRow = (await registerRow(A.p, "FD-0987654321", "c-firm")).u;
  b0.apiEntries = await apiGet(A.ctx, `/api/trust-investments/${ids.qcInv}/interest`, "b0-qc-interest");
  b0.apiInv = await apiInvestments(A.ctx, "b0-investments");
  b0.apiQc = pickInv(b0.apiInv.rows, ids.qcInv);
  b0.apiCard = await clientCard(A.ctx, "b0-qc-card", ids.qc);
  b0.sql = sqlEntries("b0 quickcollect after the lifecycle", ids.qcInv);
  const e0 = Array.isArray(b0.apiEntries.body) ? b0.apiEntries.body : [];
  b0.checks = {
    uiGross100: eq(b0.reg.gross, 100), uiFund5: eq(b0.reg.fund, 5), uiNet95: eq(b0.reg.net, 95), uiBalance30095: eq(b0.reg.balance, 30095),
    apiOneEntry: b0.apiEntries.status === 200 && e0.length === 1,
    apiEntryNet: e0.length === 1 && e0[0].presentation === "NET" && e0[0].status === "POSTED" && eq(e0[0].grossAmount, 100) && eq(e0[0].fundShareAmount, 5) && eq(e0[0].netAmount, 95) && e0[0].bankStatementLineId === null && e0[0].fundShareTransactionId === null,
    apiRegisterKeys: !!b0.apiQc && eq(b0.apiQc.grossInterest, 100) && eq(b0.apiQc.fundShare, 5) && eq(b0.apiQc.netInterest, 95) && eq(b0.apiQc.balance, 30095) && !b0.apiQc.hasInterestEarnedKey,
    apiCardInvested30095: b0.apiCard.status === 200 && eq(b0.apiCard.investedBalance, 30095),
    sqlOneEntry: b0.sql.entries.length === 1 && b0.sql.entries[0].presentation === "NET" && b0.sql.entries[0].status === "POSTED" && eq(b0.sql.entries[0].gross, 100) && eq(b0.sql.entries[0].fund, 5) && eq(b0.sql.entries[0].net, 95) && !b0.sql.entries[0].lineId && !b0.sql.entries[0].fundTx,
    sqlOneInvInterest95: b0.sql.rows.filter((r) => r.type === "INV_INTEREST").length === 1 && eq(b0.sql.rows.find((r) => r.type === "INV_INTEREST").amount, 95),
    sqlNoFundInvShare: b0.sql.rows.filter((r) => r.type === "FUND_INV_SHARE").length === 0,
    sqlBalance30095: eq(b0.sql.balance, 30095),
    noRawEnum: b0.snap.scan.clean,
  };
  save();

  // ── c (register part): the s86(3) row offers no Record interest and shows the Fund note ──
  const c = (result.steps.c = {});
  c.register = { hasRecordInterest: b0.firmRow.hasRecordInterest, hasWithdraw: b0.firmRow.hasWithdraw, fundNote: b0.firmRow.fundNote, hasGrossSubLine: b0.firmRow.gross !== null };
  c.apiFirm = pickInv(b0.apiInv.rows, ids.firmInv);

  // ── a: GROSS 400,00 from a statement line on the s86(4) account (reconciliation wizard → Record from statement) ──
  const a = (result.steps.a = {});
  const csv4 = `${OUT}/obs648b-${RUN}-s864-statement.csv`;
  fs.writeFileSync(csv4, `Date,Description,Amount,Balance\n${TODAY},Credit interest CALL-1122334455 OBS ${RUN},400.00,30495.00\n${TODAY},Credit interest call account OBS ${RUN} second,60.00,30555.00\n`);
  a.wizard = await wizardToReview(A.p, ids.inv4, csv4, "a");
  const line400 = a.wizard.lines.find((l) => eq(l.amount, 400));
  const line60 = a.wizard.lines.find((l) => eq(l.amount, 60));
  a.line400 = line400; a.line60 = line60;
  const qcBefore = { card: await clientCard(A.ctx, "a-before-qc-card", ids.qc), inv: pickInv((await apiInvestments(A.ctx, "a-before-investments")).rows, ids.qcInv) };
  a.before = qcBefore;
  const rf = await openRecordFromStatement(A.p, line400.id);
  a.dialog = { classificationLabels: rf.labels };
  a.dialog.suggestedChecked = await uiIsChecked(rf.dlg.locator("#classification-INVESTMENT_INTEREST"));
  a.dialog.suggestedNote = (await rf.dlg.getByTestId("classification-suggested-note").count()) ? norm(await rf.dlg.getByTestId("classification-suggested-note").innerText()) : null;
  if (!a.dialog.suggestedChecked) await rf.dlg.getByText("Section 86(4) investment interest", { exact: true }).click();
  const invSel = rf.dlg.getByTestId("interest-investment-select");
  await invSel.waitFor({ timeout: 10_000 });
  a.dialog.investmentOptions = (await invSel.locator("option").allInnerTexts()).map(norm);
  await invSel.selectOption(ids.qcInv);
  await rf.dlg.locator("label[for='presentation-GROSS']").click();
  await A.p.waitForTimeout(400);
  a.dialog.presentationLabels = (await rf.dlg.getByTestId("interest-presentation-radio").locator("label").allInnerTexts()).map(norm);
  const grossIn = rf.dlg.getByTestId("interest-gross-input");
  a.dialog.grossValue = await grossIn.inputValue();
  a.dialog.grossReadOnly = await grossIn.evaluate((e) => e.readOnly);
  a.dialog.fundShare = norm(await rf.dlg.getByTestId("interest-fund-share").innerText());
  a.dialog.clientShare = norm(await rf.dlg.getByTestId("interest-client-share").innerText());
  await rf.dlg.getByTestId("record-notes-input").fill(`OBS ${RUN} 648B GROSS from the statement line`);
  a.dialog.files = await snap(A.p, "a-record-from-statement-gross-filled", [1440, 1280]);
  await crop(rf.dlg, "a-record-from-statement-gross-crop");
  a.dialog.scan = await scanRaw(rf.dlg, "a-record-from-statement-dialog");
  await clip(A.p, "a-record-from-statement-dialog", "[data-testid='record-from-statement-dialog']", { figSel: "span" });
  await rf.dlg.getByTestId("record-submit").click();
  await rf.dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await A.p.getByTestId(`recorded-badge-${line400.id}`).waitFor({ timeout: 30_000 });
  a.badge = norm(await A.p.getByTestId(`recorded-badge-${line400.id}`).innerText());
  a.files = await snap(A.p, "a-wizard-after-record", [1440]);
  a.scanAfter = await scanRaw(A.p.locator("main").first(), "a-wizard-after-record");
  // after: SQL, API, register
  a.sql = sqlEntries("a quickcollect after GROSS 400", ids.qcInv);
  sql("a: the 400,00 line after recording", `select l.id, l.match_status, l.classification, l.trust_transaction_id, t.transaction_type, t.amount from T.bank_statement_lines l left join T.trust_transactions t on t.id=l.trust_transaction_id where l.id='${line400.id}'`);
  a.lineAfter = rowsA(`select l.match_status, coalesce(l.classification,''), coalesce(l.trust_transaction_id::text,'') from T.bank_statement_lines l where l.id='${line400.id}'`)[0];
  a.statementAfter = rowsA(`select line_count, matched_count, status from T.bank_statements where id='${a.wizard.statementId}'`)[0];
  a.apiEntries = await apiGet(A.ctx, `/api/trust-investments/${ids.qcInv}/interest`, "a-qc-interest");
  const ea = Array.isArray(a.apiEntries.body) ? a.apiEntries.body : [];
  const grossEntryApi = ea.find((e) => e.presentation === "GROSS");
  const grossEntrySql = a.sql.entries.find((e) => e.presentation === "GROSS");
  const netRow = grossEntrySql && a.sql.rows.find((r) => r.id === grossEntrySql.netTx);
  const fundRow = grossEntrySql && a.sql.rows.find((r) => r.id === grossEntrySql.fundTx);
  a.sqlGrossEntry = grossEntrySql; a.sqlNetRow = netRow; a.sqlFundRow = fundRow;
  a.after = { card: await clientCard(A.ctx, "a-after-qc-card", ids.qc), inv: pickInv((await apiInvestments(A.ctx, "a-after-investments")).rows, ids.qcInv) };
  a.reg = (await registerRow(A.p, "CALL-1122334455", "a")).u;
  a.regSnap = await registerSnap(A.p, "a-after-gross");
  a.checks = {
    importedTwoLines: a.wizard.lines.length === 2,
    importSuggestionInvestmentInterest: line400.suggested === "INVESTMENT_INTEREST",
    s864ChoicesOffered: a.dialog.classificationLabels.includes("Section 86(4) investment interest") && !a.dialog.classificationLabels.includes("Interest received for the Legal Practitioners' Fidelity Fund") && !a.dialog.classificationLabels.includes("Unidentified deposit"),
    suggestedPreselected: a.dialog.suggestedChecked,
    dialogGross400ReadOnly: Number(a.dialog.grossValue) === 400 && a.dialog.grossReadOnly,
    dialogFund20: eq(money(a.dialog.fundShare), 20), dialogClient380: eq(money(a.dialog.clientShare), 380),
    badgeRecorded: /^Recorded: Section 86\(4\) investment interest$/.test(a.badge),
    sqlEntryGross: !!grossEntrySql && eq(grossEntrySql.gross, 400) && eq(grossEntrySql.fund, 20) && eq(grossEntrySql.net, 380) && grossEntrySql.status === "POSTED" && grossEntrySql.lineId === line400.id && grossEntrySql.entryDate === TODAY,
    sqlNetRowInvInterest380: !!netRow && netRow.type === "INV_INTEREST" && eq(netRow.amount, 380) && netRow.posted && netRow.lineId === line400.id,
    sqlFundRowFundInvShare20: !!fundRow && fundRow.type === "FUND_INV_SHARE" && eq(fundRow.amount, 20) && fundRow.posted && fundRow.lineId === line400.id,
    sqlLineRecorded: a.lineAfter[0] === "RECORDED" && a.lineAfter[1] === "INVESTMENT_INTEREST" && a.lineAfter[2] === (netRow && netRow.id),
    apiTwoEntries: a.apiEntries.status === 200 && ea.length === 2,
    apiGrossEntry: !!grossEntryApi && eq(grossEntryApi.grossAmount, 400) && eq(grossEntryApi.fundShareAmount, 20) && eq(grossEntryApi.netAmount, 380) && grossEntryApi.status === "POSTED" && grossEntryApi.bankStatementLineId === line400.id && !!grossEntryApi.fundShareTransactionId,
    apiRegisterSums: !!a.after.inv && eq(a.after.inv.grossInterest, 500) && eq(a.after.inv.fundShare, 25) && eq(a.after.inv.netInterest, 475) && eq(a.after.inv.balance, 30475),
    uiRegisterSums: eq(a.reg.gross, 500) && eq(a.reg.fund, 25) && eq(a.reg.net, 475) && eq(a.reg.balance, 30475),
    sqlBalance30475: eq(a.sql.balance, 30475),
    cardInvestedUp380: eq(a.after.card.investedBalance, qcBefore.card.investedBalance + 380) && eq(a.after.card.balance, qcBefore.card.balance),
    noRawEnum: a.dialog.scan.clean && a.scanAfter.clean && a.regSnap.scan.clean && a.wizard.scanReview.clean,
  };
  save();

  // ── b1: a fresh NET bank advice through the Record interest dialog on a fresh Moroka s86(4) investment ──
  const b1 = (result.steps.b1 = {});
  b1.whyFresh = `active s86(4) investments after the lifecycle: ${JSON.stringify(ids.activeS864)} — only QuickCollect's; a fresh one is placed through the UI (Alice) and approved (Bob)`;
  b1.place = await placeViaUi(A.p, { client: ids.moroka, matter: ids.morokaMatter, accountName: ids.inv4Name, institution: "Nedbank", depositNumber: `OBS4B-${RUN}`, principal: P, rate: 7 });
  b1.approve = await approveViaUi(S.bob.p, b1.place.investmentDb.depositTx);
  const mInv = b1.place.investmentDb.id;
  ids.morokaInv = mInv;
  b1.regBefore = (await registerRow(A.p, `OBS4B-${RUN}`, "b1-before")).u;
  const { row: mRow } = await registerRow(A.p, `OBS4B-${RUN}`, "b1-open");
  await mRow.getByRole("button", { name: "Record interest", exact: true }).click();
  const dlg = A.p.getByTestId("record-investment-interest-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  await A.p.waitForTimeout(1200);
  const sel = dlg.getByTestId("interest-line-select");
  const grossRadio = dlg.locator("#interest-presentation-GROSS");
  const netRadio = dlg.locator("#interest-presentation-NET");
  const dateIn = dlg.getByLabel("Date credited");
  const grossInput = dlg.getByLabel("Gross interest");
  const d = (b1.dialog = {});
  d.open = { lineOptions: (await sel.locator("option").allInnerTexts()).map(norm), lineValue: await sel.inputValue(), grossDisabled: await grossRadio.isDisabled(), netChecked: await uiIsChecked(netRadio),
    adviceNote: (await dlg.getByTestId("interest-bank-advice-note").count()) ? norm(await dlg.getByTestId("interest-bank-advice-note").innerText()) : null, date: await dateIn.inputValue(),
    dateMin: await dateIn.getAttribute("min"), dateMax: await dateIn.getAttribute("max"), fund: norm(await dlg.getByTestId("interest-fund-share").innerText()), client: norm(await dlg.getByTestId("interest-client-share").innerText()) };
  await crop(dlg, "b1-record-interest-dialog-open-crop");
  d.scanOpen = await scanRaw(dlg, "b1-record-interest-dialog-open");
  // the line picker: pick the unmatched 60,00 line, then GROSS (shown, not typed), then back to a bank advice — nothing submitted
  if (line60) {
    await sel.selectOption(line60.id);
    await A.p.waitForTimeout(400);
    d.withLine = { date: await dateIn.inputValue(), dateReadOnly: await dateIn.evaluate((e) => e.readOnly), grossDisabled: await grossRadio.isDisabled(), adviceNoteShown: (await dlg.getByTestId("interest-bank-advice-note").count()) > 0 };
    await dlg.locator("label[for='interest-presentation-GROSS']").click();
    await A.p.waitForTimeout(400);
    d.withLineGross = { gross: await grossInput.inputValue(), grossReadOnly: await grossInput.evaluate((e) => e.readOnly), fund: norm(await dlg.getByTestId("interest-fund-share").innerText()), client: norm(await dlg.getByTestId("interest-client-share").innerText()) };
    await crop(dlg, "b1-record-interest-dialog-line-gross-crop");
    d.scanLineGross = await scanRaw(dlg, "b1-record-interest-dialog-line-gross");
    await sel.selectOption("");
    await A.p.waitForTimeout(400);
    d.backToAdvice = { date: await dateIn.inputValue(), dateReadOnly: await dateIn.evaluate((e) => e.readOnly), gross: await grossInput.inputValue(), grossDisabled: await grossRadio.isDisabled(), netChecked: await uiIsChecked(netRadio), adviceNoteShown: (await dlg.getByTestId("interest-bank-advice-note").count()) > 0 };
  }
  if (!(await dateIn.inputValue())) await dateIn.fill(TODAY);
  await grossInput.fill("33.33");
  await A.p.waitForTimeout(400);
  d.filled = { date: await dateIn.inputValue(), gross: await grossInput.inputValue(), fund: norm(await dlg.getByTestId("interest-fund-share").innerText()), client: norm(await dlg.getByTestId("interest-client-share").innerText()), netChecked: await uiIsChecked(netRadio) };
  d.files = await snap(A.p, "b1-record-interest-dialog-filled", [1440, 1280]);
  await crop(dlg, "b1-record-interest-dialog-filled-crop");
  d.scanFilled = await scanRaw(dlg, "b1-record-interest-dialog-filled");
  await clip(A.p, "b1-record-interest-dialog", "[data-testid='record-investment-interest-dialog']", { figSel: "span" });
  const nE = Number(val(`select count(*) from T.trust_investment_interest where investment_id='${mInv}'`));
  await dlg.getByRole("button", { name: "Record interest", exact: true }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  d.entriesAfter = Number(await waitSql(`select count(*) from T.trust_investment_interest where investment_id='${mInv}'`, (v) => Number(v) === nE + 1));
  b1.sql = sqlEntries("b1 moroka fresh NET 33,33", mInv);
  b1.apiEntries = await apiGet(A.ctx, `/api/trust-investments/${mInv}/interest`, "b1-moroka-interest");
  const eb = Array.isArray(b1.apiEntries.body) ? b1.apiEntries.body : [];
  b1.apiInv = pickInv((await apiInvestments(A.ctx, "b1-after-investments")).rows, mInv);
  b1.card = await clientCard(A.ctx, "b1-moroka-card", ids.moroka);
  b1.reg = (await registerRow(A.p, `OBS4B-${RUN}`, "b1-after")).u;
  b1.regSnap = await registerSnap(A.p, "b1-after-net");
  await clip(A.p, "register-table", "[data-testid='investments-table']", { wrapperSel: "main .overflow-x-auto", figSel: "td span" });
  b1.line60After = line60 ? rowsA(`select match_status, coalesce(classification,'') from T.bank_statement_lines where id='${line60.id}'`)[0] : null;
  b1.checks = {
    openNoLineNetOnly: d.open.lineValue === "" && d.open.grossDisabled && d.open.netChecked && !!d.open.adviceNote,
    openOffersOnlyTheUnmatched60Line: d.open.lineOptions.length === 2 && /R 60,00/.test(d.open.lineOptions[1] || "") && !d.open.lineOptions.some((o) => /400,00/.test(o)),
    lineDatesAndEnablesGross: !line60 || (d.withLine.date === line60.date && d.withLine.dateReadOnly && !d.withLine.grossDisabled),
    lineGrossShown60: !line60 || (Number(d.withLineGross.gross) === 60 && d.withLineGross.grossReadOnly && eq(money(d.withLineGross.fund), 3) && eq(money(d.withLineGross.client), 57)),
    backToAdviceResets: !line60 || (d.backToAdvice.date === "" && !d.backToAdvice.dateReadOnly && d.backToAdvice.gross === "" && d.backToAdvice.grossDisabled && d.backToAdvice.netChecked && d.backToAdvice.adviceNoteShown),
    dialogShares: eq(money(d.filled.fund), 1.67) && eq(money(d.filled.client), 31.66) && d.filled.netChecked,
    sqlEntry: b1.sql.entries.length === 1 && b1.sql.entries[0].presentation === "NET" && b1.sql.entries[0].status === "POSTED" && eq(b1.sql.entries[0].gross, 33.33) && eq(b1.sql.entries[0].fund, 1.67) && eq(b1.sql.entries[0].net, 31.66) && !b1.sql.entries[0].lineId && !b1.sql.entries[0].fundTx && b1.sql.entries[0].entryDate === TODAY,
    sqlOneInvInterest: b1.sql.rows.length === 1 && b1.sql.rows[0].type === "INV_INTEREST" && eq(b1.sql.rows[0].amount, 31.66) && b1.sql.rows[0].posted,
    sqlNoFundInvShare: b1.sql.rows.filter((r) => r.type === "FUND_INV_SHARE").length === 0,
    apiEntry: eb.length === 1 && eb[0].presentation === "NET" && eb[0].status === "POSTED" && eq(eb[0].grossAmount, 33.33) && eq(eb[0].fundShareAmount, 1.67) && eq(eb[0].netAmount, 31.66),
    apiRegister: !!b1.apiInv && eq(b1.apiInv.grossInterest, 33.33) && eq(b1.apiInv.fundShare, 1.67) && eq(b1.apiInv.netInterest, 31.66) && eq(b1.apiInv.balance, P + 31.66),
    uiRegister: eq(b1.reg.gross, 33.33) && eq(b1.reg.fund, 1.67) && eq(b1.reg.net, 31.66) && eq(b1.reg.balance, P + 31.66),
    uiRegisterBeforeZeros: eq(b1.regBefore.gross, 0) && eq(b1.regBefore.fund, 0) && eq(b1.regBefore.net, 0) && b1.regBefore.hasRecordInterest,
    sqlBalance: eq(b1.sql.balance, P + 31.66),
    cardInvested: eq(b1.card.investedBalance, P + 31.66),
    line60StillUnmatched: !line60 || (b1.line60After && b1.line60After[0] === "UNMATCHED"),
    noRawEnum: d.scanOpen.clean && (!d.scanLineGross || d.scanLineGross.clean) && d.scanFilled.clean && b1.regSnap.scan.clean,
  };
  save();

  // ── c (wizard part): a credit on the s86(3) account offers no "Section 86(4) investment interest" ──
  const csv3 = `${OUT}/obs648b-${RUN}-s863-statement.csv`;
  fs.writeFileSync(csv3, `Date,Description,Amount,Balance\n${TODAY},Credit interest FD-0987654321 OBS ${RUN},150.00,200150.00\n`);
  c.wizard = await wizardToReview(A.p, ids.inv3, csv3, "c");
  const l3 = c.wizard.lines[0];
  const rf3 = await openRecordFromStatement(A.p, l3.id);
  c.dialogLabels = rf3.labels;
  c.investmentSelectShown = (await rf3.dlg.getByTestId("interest-investment-select").count()) > 0;
  c.files = await snap(A.p, "c-s863-record-from-statement", [1440]);
  await crop(rf3.dlg, "c-s863-record-from-statement-crop");
  c.scanDialog = await scanRaw(rf3.dlg, "c-s863-record-from-statement-dialog");
  await rf3.dlg.getByRole("button", { name: "Cancel" }).click();
  await rf3.dlg.waitFor({ state: "hidden", timeout: 15_000 });
  c.line3After = rowsA(`select match_status, coalesce(classification,''), coalesce(suggested_classification,'') from T.bank_statement_lines where id='${l3.id}'`)[0];
  c.checks = {
    registerNoRecordInterest: !c.register.hasRecordInterest && c.register.hasWithdraw,
    registerFundNote: c.register.fundNote === "Interest is the Fund's — record it from the investment account's statement",
    registerNoInterestFigures: !c.register.hasGrossSubLine,
    wizardNoS864Interest: !c.dialogLabels.includes("Section 86(4) investment interest") && c.dialogLabels.length > 0,
    wizardOffersFundInterest: c.dialogLabels.includes("Interest received for the Legal Practitioners' Fidelity Fund"),
    noInvestmentPicker: !c.investmentSelectShown,
    lineUntouched: c.line3After[0] === "UNMATCHED" && c.line3After[1] === "",
    suggestionFundInterest: c.line3After[2] === "FUND_INTEREST",
    noRawEnum: c.scanDialog.clean && c.wizard.scanReview.clean,
  };
  save();

  result.finishedAt = new Date().toISOString();
  result.summary = { run: RUN, today: TODAY, ids,
    b0: { pass: allTrue(b0.checks), checks: b0.checks, ui: { gross: b0.reg.gross, fund: b0.reg.fund, net: b0.reg.net, balance: b0.reg.balance }, api: b0.apiQc, card: b0.apiCard, sqlEntries: b0.sql.entries },
    a: { pass: allTrue(a.checks), checks: a.checks, dialog: { labels: a.dialog.classificationLabels, gross: a.dialog.grossValue, fund: a.dialog.fundShare, client: a.dialog.clientShare, presentationLabels: a.dialog.presentationLabels }, badge: a.badge, ui: { gross: a.reg.gross, fund: a.reg.fund, net: a.reg.net, balance: a.reg.balance }, api: a.after.inv, card: a.after.card, sqlEntry: a.sqlGrossEntry, netRow: a.sqlNetRow, fundRow: a.sqlFundRow, line: a.lineAfter, statement: a.statementAfter },
    b1: { pass: allTrue(b1.checks), checks: b1.checks, dialog: b1.dialog.filled, ui: { gross: b1.reg.gross, fund: b1.reg.fund, net: b1.reg.net, balance: b1.reg.balance }, api: b1.apiInv, card: b1.card, sqlEntries: b1.sql.entries },
    c: { pass: allTrue(c.checks), checks: c.checks, register: c.register, dialogLabels: c.dialogLabels, a864Labels: a.dialog.classificationLabels },
    d: { scans: result.scans.length, dirty: result.scans.filter((s) => !s.clean), regexes: result.rawRegexes },
    e: Object.fromEntries(Object.entries(result.clips).map(([k, v]) => [k, v.map((m) => ({ w: m.width, ok: m.ok, figuresOk: m.figuresOk, figures: m.figures.length, pageHScroll: m.pageHorizontalScroll, wrapper: m.wrapper, offenders: m.offenders.length, truncated: m.truncated.length }))])),
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length, posts: result.posts.length };
  save();
  console.log(JSON.stringify(result.summary, null, 2));
  await browser.close();
})().catch(async (e) => { result.error = String(e.stack || e); save(); console.error("OBS FAILED", e); try { await browser.close(); } catch {} process.exit(1); });
