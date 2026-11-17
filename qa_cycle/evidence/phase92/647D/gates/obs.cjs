// 647D OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI: Alice (owner) uploads a statement with three unknown credits,
// records each as an Unidentified deposit, requests an allocation, tries to approve it herself, Bob
// (admin) approves; Alice undoes it; Alice tries to unmatch the statement line (refused); Alice
// requests a payment to the Fund, Bob approves; Alice requests a second one, Bob rejects. The portal
// (branch build on :3012, CORS route shim) is read as the Moroka portal contact after a UI magic-link
// sign-in. psql is read-only EVIDENCE. API reads use each browser context's own mock-auth-token
// against :8081. No API writes, no SQL writes.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const PORTAL = "http://localhost:3012";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/647D/gates`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [], clip: [], startedAt: new Date().toISOString() };
const save = () => fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));

const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
function daysBetween(fromIso, toIso) {
  return Math.round((new Date(`${toIso}T12:00:00Z`) - new Date(`${fromIso}T12:00:00Z`)) / 86_400_000);
}
const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const longDate = (iso) => { const [y, m, d] = iso.split("-").map(Number); return `${d} ${MONTHS[m - 1]} ${y}`; };
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
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(SQL_FILE, `-- 647D OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 647D OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
async function waitSql(q, pred, ms = 30_000) {
  const t0 = Date.now();
  let v = val(q);
  while (!pred(v) && Date.now() - t0 < ms) { await new Promise((r) => setTimeout(r, 500)); v = val(q); }
  return v;
}

// ── browser helpers ─────────────────────────────────────────────────
let browser;
const S = {}; // sessions: alice { ctx, wiz, pg }, bob { ctx, pg }
function watch(p, tag) {
  p.on("console", (m) => { if (m.type() === "error") result.consoleErrors.push({ tab: tag, url: p.url(), text: m.text() }); });
  p.on("pageerror", (e) => result.pageErrors.push({ tab: tag, url: p.url(), text: String(e) }));
}
async function newSession(name, userId) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  watch(p, `${name}-1`);
  await p.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption(userId);
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.getByRole("heading").first().waitFor({ timeout: 30_000 });
  const s = { ctx, wiz: p, pg: p };
  if (name === "alice") { s.pg = await ctx.newPage(); watch(s.pg, `${name}-2`); } // tab 1 keeps the wizard open
  S[name] = s;
  return s;
}
let shot = 0;
const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
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
  const text = await p.locator("body").innerText().catch(() => "");
  fs.writeFileSync(`${OUT}/${String(shot).padStart(2, "0")}-${name}.txt`, text);
  return files;
}
async function crop(locator, name) {
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await locator.screenshot({ path: file }).catch(() => {});
  return file.replace(`${OUT}/`, "");
}
async function token(ctx) {
  const tok = (await ctx.cookies()).find((c) => c.name === "mock-auth-token");
  return tok ? tok.value : "";
}
async function api(who, path, saveAs) {
  const ctx = S[who].ctx;
  const res = await ctx.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token(ctx)}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { path, status: res.status(), body };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
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

// bounding-rect clip measure of a table (647B probe-clip method) at 1280 and 1440
async function clipMeasure(p, label, testids) {
  const out = { label, widths: [] };
  for (const width of [1280, 1440]) {
    await p.setViewportSize({ width, height: 1100 });
    await p.waitForTimeout(600);
    const m = await p.evaluate((tids) => {
      const vw = document.documentElement.clientWidth;
      function measure(testid) {
        const el = document.querySelector(`[data-testid='${testid}']`);
        if (!el) return { testid, missing: true };
        const t = el.tagName === "TABLE" ? el : el.querySelector("table");
        if (!t) return { testid, missing: true };
        const chain = [];
        for (let x = t; x && chain.length < 6; x = x.parentElement) {
          const cs = getComputedStyle(x); const r = x.getBoundingClientRect();
          chain.push({ tag: x.tagName, testid: x.getAttribute("data-testid"), overflowX: cs.overflowX, clientWidth: x.clientWidth, scrollWidth: x.scrollWidth, left: Math.round(r.left), right: Math.round(r.right) });
        }
        let clipRight = vw, clipLeft = 0, clipBy = "viewport";
        for (let x = t.parentElement; x; x = x.parentElement) {
          const cs = getComputedStyle(x);
          if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 40)}`; break; }
        }
        const cellInfo = (c) => {
          const r = c.getBoundingClientRect();
          // a cell's own content: every descendant element's rect must sit inside the clip too
          const kids = [...c.querySelectorAll("*")].map((k) => k.getBoundingClientRect()).filter((k) => k.width > 0);
          const kidsInside = kids.every((k) => k.left >= clipLeft - 0.5 && k.right <= clipRight + 0.5);
          return { text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 120), left: Math.round(r.left), right: Math.round(r.right),
            inside: r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5 && kidsInside, truncated: c.scrollWidth > c.clientWidth + 1 };
        };
        const header = [...t.querySelectorAll("thead th")].map(cellInfo);
        const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
        const tr = t.getBoundingClientRect();
        return { testid, vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), tableLeft: Math.round(tr.left), tableRight: Math.round(tr.right),
          tableScrollWidth: t.scrollWidth, chain, header, rows,
          allInside: header.every((h) => h.inside) && rows.every((r) => r.every((c) => c.inside)),
          noneTruncated: header.every((h) => !h.truncated) && rows.every((r) => r.every((c) => !c.truncated)),
          noHorizontalOverflow: chain.every((c) => c.scrollWidth <= c.clientWidth + 1 || c.overflowX === "visible") };
      }
      return tids.map(measure);
    }, testids);
    const ok = m.every((x) => !x.missing && x.allInside && x.noneTruncated && x.noHorizontalOverflow);
    out.widths.push({ width, ok, tables: m });
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  out.ok = out.widths.every((w) => w.ok);
  result.clip.push(out);
  fs.writeFileSync(`${OUT}/clip-${label}.json`, JSON.stringify(out, null, 2));
  return out.ok;
}

// ── SQL shapes ──────────────────────────────────────────────────────
const lineSql = (stmt) => `select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.classification, l.trust_transaction_id from T.bank_statement_lines l where l.bank_statement_id='${stmt}' order by l.line_number`;
const rowSql = (where) => `select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.customer_id, t.project_id, t.creditor_account_id, t.unidentified_deposit_id, t.reference, t.description, t.reversal_of, t.reversed_by_id, t.recorded_by, t.approved_by, t.rejection_reason, t.created_at from T.trust_transactions t where ${where} order by t.created_at`;
const itemSql = (where) => `select d.id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.allocation_credit_transaction_id, d.allocated_customer_id, d.allocated_project_id, d.deposit_transaction_id, d.bank_statement_line_id from T.unidentified_deposits d where ${where} order by d.received_date, d.created_at, d.id`;
let ids;
const ledgerBal = (credId) => `select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${credId}' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '${sastToday}'`;
const clientBal = (cust) => `select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and customer_id='${cust}' and ledger='CLIENT' and posted_at is not null and transaction_date <= date '${sastToday}'`;
const eqSql = (asAt) => `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${asAt}') cashbook, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${asAt}') all_creditors, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and ledger='CLIENT' and posted_at is not null and transaction_date <= date '${asAt}') client_ledgers, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${ids.udAccount}' and posted_at is not null and transaction_date <= date '${asAt}') unidentified_ledger, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${ids.fundAccount}' and posted_at is not null and transaction_date <= date '${asAt}') fund_ledger`;
function books(label, asAt = sastToday) {
  sql(`${label}: books as at ${asAt}`, eqSql(asAt));
  const [r] = rowsA(eqSql(asAt));
  const b = { asAt, cashbook: Number(r[0]), allCreditors: Number(r[1]), clientLedgers: Number(r[2]), unidentified: Number(r[3]), fund: Number(r[4]),
    moroka: Number(val(clientBal(ids.moroka))) };
  b.balanced = eq(b.cashbook, b.allCreditors) && eq(b.allCreditors, b.clientLedgers + b.unidentified + b.fund);
  return b;
}
const txCount = () => Number(val(`select count(*) from T.trust_transactions`));
const delta = (a, b) => ({ cashbook: +(b.cashbook - a.cashbook).toFixed(2), unidentified: +(b.unidentified - a.unidentified).toFixed(2), fund: +(b.fund - a.fund).toFixed(2), moroka: +(b.moroka - a.moroka).toFixed(2), clientLedgers: +(b.clientLedgers - a.clientLedgers).toFixed(2) });
const same = (d, exp) => Object.keys(d).every((k) => eq(d[k], exp[k] || 0));

// Other trust creditors page as `who`: the Unidentified deposits card + table, cross-checked with API + SQL
async function itemsPage(who, label) {
  const p = S[who].pg;
  await p.bringToFront();
  await p.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
  await p.getByTestId("other-creditors-page").waitFor({ timeout: 60_000 });
  const section = p.getByTestId("unidentified-deposits-section");
  await section.waitFor({ timeout: 30_000 });
  await p.getByTestId("unidentified-deposit-row").first().waitFor({ timeout: 30_000 }).catch(() => {});
  const card = p.getByTestId("creditor-UNIDENTIFIED_DEPOSITS");
  const cardValue = money(norm(await card.innerText()));
  const rowEls = p.getByTestId("unidentified-deposit-row");
  const n = await rowEls.count();
  const rows = {};
  for (let i = 0; i < n; i++) {
    const r = rowEls.nth(i);
    const cells = (await r.locator("td").allInnerTexts()).map(norm);
    const allocBtn = await r.locator("[data-testid^='allocate-btn-']").getAttribute("data-testid").catch(() => null);
    const id = allocBtn ? allocBtn.replace("allocate-btn-", "") : null;
    rows[cells[1]] = { id, cells, status: norm(await r.getByTestId("unidentified-deposit-status").innerText()),
      pending: await r.getByTestId("unidentified-deposit-pending").innerText().catch(() => null),
      allocatedTo: await r.getByTestId("unidentified-deposit-allocated-to").innerText().catch(() => null),
      hasAllocate: (await r.locator("[data-testid^='allocate-btn-']").count()) > 0,
      hasPayToFund: (await r.locator("[data-testid^='pay-to-fund-btn-']").count()) > 0, amount: money(cells[2]) };
  }
  const headers = (await section.locator("thead th").allInnerTexts()).map(norm);
  const files = await snap(p, `${label}-other-creditors-${who}`);
  await crop(section, `${label}-unidentified-crop`);
  const apiCards = await api(who, `/api/trust-accounts/${ids.account}/creditor-accounts`, `${label}-creditor-accounts`);
  const udApi = Array.isArray(apiCards.body) ? apiCards.body.find((a) => a.accountType === "UNIDENTIFIED_DEPOSITS") : null;
  const fundApi = Array.isArray(apiCards.body) ? apiCards.body.find((a) => a.accountType === "FUND_INTEREST_AND_CHARGES") : null;
  const apiItems = await api(who, `/api/trust-accounts/${ids.account}/unidentified-deposits?page=0&size=25`, `${label}-unidentified-deposits`);
  sql(`${label}: OBS items`, itemSql(`d.trust_account_id='${ids.account}'`));
  return { cardValue, cardApi: udApi && udApi.balance, fundApi: fundApi && fundApi.balance, sqlBalance: Number(val(ledgerBal(ids.udAccount))),
    headers, n, rows, api: (apiItems.body && apiItems.body.content) || [], files };
}
// row of the items page whose description cell carries `desc`
const uiRow = (pg, desc) => Object.entries(pg.rows).find(([k]) => k.includes(desc))?.[1] || null;
const apiItem = (pg, id) => pg.api.find((x) => x.id === id) || null;
const itemDb = (id) => { const [r] = rowsA(`select status, coalesce(resolved_date::text,'<null>'), coalesce(resolution_transaction_id::text,'<null>'), coalesce(allocated_customer_id::text,'<null>'), coalesce(allocated_project_id::text,'<null>'), coalesce(allocation_credit_transaction_id::text,'<null>') from T.unidentified_deposits where id='${id}'`); return { status: r[0], resolvedDate: r[1], resolutionTx: r[2], customer: r[3], project: r[4], creditTx: r[5] }; };
const txDb = (id) => { const [r] = rowsA(`select transaction_type, status, transaction_date, ledger, ledger_effect, cashbook_effect, posted_at is not null, coalesce(customer_id::text,'<null>'), coalesce(project_id::text,'<null>'), coalesce(creditor_account_id::text,'<null>'), coalesce(unidentified_deposit_id::text,'<null>'), reference, coalesce(description,''), coalesce(reversed_by_id::text,'<null>'), recorded_by, coalesce(approved_by::text,'<null>') from T.trust_transactions where id='${id}'`); if (!r) return null; return { type: r[0], status: r[1], date: r[2], ledger: r[3], ledgerEffect: Number(r[4]), cashbookEffect: Number(r[5]), posted: r[6] === "t", customer: r[7], project: r[8], creditor: r[9], item: r[10], reference: r[11], description: r[12], reversedBy: r[13], recordedBy: r[14], approvedBy: r[15] }; };

// Transactions page as `who`; returns the row for `txId` (type badge, description line, status, actions)
async function txPage(who, label, txId, opts = {}) {
  const p = S[who].pg;
  await p.bringToFront();
  await p.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
  await p.getByTestId("transactions-table").waitFor({ timeout: 60_000 });
  const row = p.getByTestId(`transaction-row-${txId}`);
  await row.waitFor({ timeout: 30_000 });
  const cells = (await row.locator("td").allInnerTexts()).map(norm);
  const r = { cells, description: await p.getByTestId(`transaction-description-${txId}`).innerText().catch(() => null),
    hasApprove: (await row.getByTestId("approve-button").count()) > 0, hasReverse: (await row.getByTestId("reverse-button").count()) > 0 };
  if (!opts.noShot) { r.files = await snap(p, `${label}-transactions-${who}`); await crop(row, `${label}-tx-row-crop`); }
  return r;
}

(async () => {
  browser = await chromium.launch({ headless: true });
  ids = result.ids = {};
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  ids.udAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='UNIDENTIFIED_DEPOSITS'`);
  ids.fundAccount = val(`select id from T.trust_creditor_accounts where trust_account_id='${ids.account}' and account_type='FUND_INTEREST_AND_CHARGES'`);
  ids.moroka = val(`select id from T.customers where name like 'Moroka Family Trust%' order by created_at limit 1`);
  ids.morokaName = val(`select name from T.customers where id='${ids.moroka}'`);
  ids.morokaMatter = val(`select p.id from T.projects p join T.customer_projects cp on cp.project_id=p.id where cp.customer_id='${ids.moroka}' and p.name like 'Estate Late Peter Moroka%' limit 1`);
  ids.morokaMatterName = val(`select name from T.projects where id='${ids.morokaMatter}'`);
  ids.alice = val(`select id from T.members where email like 'alice%' limit 1`);
  ids.bob = val(`select id from T.members where email like 'bob%' limit 1`);
  sql("baseline: creditor accounts", `select id, trust_account_id, account_type, name from T.trust_creditor_accounts`);
  sql("baseline: trust transactions", rowSql("true"));
  sql("baseline: statements / recons / items", `select (select count(*) from T.bank_statements) statements, (select count(*) from T.trust_reconciliations) recons, (select count(*) from T.unidentified_deposits) items`);
  sql("baseline: portal trust rows (Moroka)", `select id, transaction_type, amount, running_balance, transaction_date, description, reference from portal.portal_trust_transaction where customer_id='${ids.moroka}' order by transaction_date, recorded_at`);

  // three unknown credits: A → allocate/undo/unmatch-refused, B → pay to the Fund (approved), C → pay to the Fund (rejected)
  const LINES = [
    { k: "A", date: addDays(sastToday, -7), desc: `DEPOSIT UNKNOWN REF A${RUN}`, ref: `UNK-A/OBS647D/${RUN}`, amount: 4321.09 },
    { k: "B", date: addDays(sastToday, -5), desc: `DEPOSIT UNKNOWN REF B${RUN}`, ref: `UNK-B/OBS647D/${RUN}`, amount: 1234.56 },
    { k: "C", date: addDays(sastToday, -4), desc: `DEPOSIT UNKNOWN REF C${RUN}`, ref: `UNK-C/OBS647D/${RUN}`, amount: 777.0 },
  ];
  const L = Object.fromEntries(LINES.map((l) => [l.k, l]));
  const D = (result.dates = { today: sastToday, periodEnd: LINES.map((l) => l.date).sort().slice(-1)[0], allocationDate: addDays(L.A.date, 4) });
  result.lines = LINES;
  let bal = 0;
  const csv = [`Standard Bank Trust Account - OBS 647D ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of LINES) { bal = +(bal + l.amount).toFixed(2); csv.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
  const csvName = `statement-OBS647D-${RUN}.csv`;
  const csvFile = `${OUT}/${csvName}`;
  fs.writeFileSync(csvFile, csv.join("\n") + "\n");

  await newSession("alice", "user_e2e_alice");
  await newSession("bob", "user_e2e_bob");
  const base = (result.steps.baseline = { books: books("baseline"), txCount: txCount() });
  base.page = await itemsPage("alice", "00-baseline");
  save();

  // ════ prep: wizard (Alice) — upload, auto-match, review; record A, B, C as Unidentified deposit ════
  const prep = (result.steps.prep = {});
  const wiz = S.alice.wiz;
  await wiz.bringToFront();
  await wiz.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await wiz.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await wiz.getByTestId("account-select").selectOption(ids.account);
  await wiz.getByTestId("period-end-input").fill(D.periodEnd);
  await wiz.getByTestId("step-next-btn").click();
  await wiz.getByTestId("bank-statement-upload").waitFor({ timeout: 30_000 });
  await wiz.getByTestId("file-input").setInputFiles(csvFile);
  await wiz.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  prep.uploadResult = norm(await wiz.getByTestId("upload-result").innerText());
  ids.statement = val(`select id from T.bank_statements where file_name='${csvName}'`);
  for (const l of LINES) l.lineId = val(`select id from T.bank_statement_lines where bank_statement_id='${ids.statement}' and description='${l.desc}'`);
  await wiz.getByTestId("step-next-btn").click();
  await wiz.getByTestId("auto-match-btn").click();
  await wiz.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  prep.autoMatch = norm(await wiz.getByTestId("auto-match-result").innerText());
  await wiz.getByTestId("step-next-btn").click();
  await wiz.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  prep.refusal = await wiz.getByTestId("reconciliation-error").innerText().catch(() => null);
  for (const l of LINES) {
    await wiz.getByTestId(`bank-line-${l.lineId}`).click();
    await wiz.getByTestId(`record-btn-${l.lineId}`).click();
    const dlg = wiz.getByTestId("record-from-statement-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await wiz.waitForTimeout(300);
    await dlg.locator("button[role='radio'][value='UNIDENTIFIED_DEPOSIT']").click();
    await dlg.getByTestId("record-notes-input").fill(`OBS 647D ${RUN} unknown credit ${l.k}`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await wiz.getByTestId(`recorded-badge-${l.lineId}`).waitFor({ timeout: 30_000 });
    l.badge = norm(await wiz.getByTestId(`recorded-badge-${l.lineId}`).innerText());
    await wiz.waitForTimeout(800);
  }
  prep.files = await snap(wiz, "prep-wizard-three-lines-recorded", [1440]);
  sql("prep: lines", lineSql(ids.statement));
  for (const l of LINES) {
    const [it] = rowsA(`select id, deposit_transaction_id from T.unidentified_deposits where bank_statement_line_id='${l.lineId}'`);
    l.itemId = it ? it[0] : null; l.depositTx = it ? it[1] : null;
  }
  prep.checks = { threeRecorded: LINES.every((l) => l.badge === "Recorded: Unidentified deposit"), threeItems: LINES.every((l) => !!l.itemId), noRefusal: !prep.refusal };
  const prepBooks = (prep.books = books("prep"));
  save();

  // ════ (a) Alice allocates item A to Moroka / Estate matter, dated received+4 ════
  const a = (result.steps.a = {});
  a.before = await itemsPage("alice", "a1-held");
  const rA0 = uiRow(a.before, L.A.desc);
  a.heldRow = rA0;
  a.allHeldOfferBoth = LINES.every((l) => { const r = uiRow(a.before, l.desc); return r && r.status === "Held" && r.hasAllocate && r.hasPayToFund && r.id === l.itemId; });
  a.headers = a.before.headers;
  await clipMeasure(S.alice.pg, "a1-items-held", ["unidentified-deposits-table"]);
  const pA = S.alice.pg;
  await pA.getByTestId(`allocate-btn-${L.A.itemId}`).click();
  const adlg = pA.getByTestId("allocate-unidentified-dialog");
  await adlg.waitFor({ timeout: 15_000 });
  a.dialogText = norm(await adlg.innerText());
  a.defaultDate = await adlg.getByTestId("allocate-date").inputValue();
  a.dateMin = await adlg.getByTestId("allocate-date").getAttribute("min");
  a.dateMax = await adlg.getByTestId("allocate-date").getAttribute("max");
  await adlg.getByTestId("allocate-customer-trigger").click();
  await pA.getByPlaceholder("Search clients...").fill("Moroka");
  await pA.getByRole("option", { name: /Moroka Family Trust/ }).first().click();
  const mt = adlg.getByTestId("allocate-matter-trigger");
  for (let i = 0; i < 40 && ((await mt.isDisabled()) || /Loading/.test(await mt.innerText())); i++) await pA.waitForTimeout(250);
  await mt.click();
  await pA.getByRole("option", { name: /Estate Late Peter Moroka/ }).first().click();
  await adlg.getByTestId("allocate-date").fill(D.allocationDate);
  await adlg.getByTestId("allocate-notes").fill(`OBS 647D ${RUN}: executor's letter identifies the deposit`);
  a.dialogFilled = norm(await adlg.innerText());
  a.dialogFiles = await snap(pA, "a2-allocate-dialog-filled");
  await crop(adlg, "a2-allocate-dialog-crop");
  const cntA = txCount();
  await adlg.getByTestId("allocate-submit").click();
  await adlg.waitFor({ state: "hidden", timeout: 30_000 });
  ids.allocOut = await waitSql(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${L.A.itemId}'`, (v) => !!v);
  a.page = await itemsPage("alice", "a3-pending");
  const rA = uiRow(a.page, L.A.desc);
  a.pendingRow = rA;
  a.apiItem = apiItem(a.page, L.A.itemId);
  a.item = itemDb(L.A.itemId);
  a.allocOut = txDb(ids.allocOut);
  sql("a: item A after request", itemSql(`d.id='${L.A.itemId}'`));
  sql("a: rows naming item A", rowSql(`t.unidentified_deposit_id='${L.A.itemId}'`));
  a.books = books("a: after request");
  a.delta = delta(prepBooks, a.books);
  a.api = {
    clientLedger: (await api("alice", `/api/trust-accounts/${ids.account}/client-ledgers/${ids.moroka}`, "a-moroka-ledger")).body,
    cashbook: (await api("alice", `/api/trust-accounts/${ids.account}/cashbook-balance`, "a-cashbook")).body,
  };
  await clipMeasure(pA, "a3-items-pending", ["unidentified-deposits-table"]);
  const expectPending = `Allocation to ${ids.morokaName} awaiting approval`;
  a.checks = {
    allHeldOfferAllocateAndPay: a.allHeldOfferBoth,
    actionsColumnHeader: a.headers.includes("Actions"),
    dialogDateWindow: a.defaultDate === sastToday && a.dateMin === L.A.date && a.dateMax === sastToday,
    oneRowCreated: txCount() === cntA + 1,
    pendingTextUi: !!rA && rA.pending === expectPending && !rA.hasAllocate && !rA.hasPayToFund && rA.status === "Held",
    itemHeldWithResolution: a.item.status === "HELD" && a.item.resolutionTx === ids.allocOut && a.item.customer === ids.moroka && a.item.project === ids.morokaMatter && a.item.resolvedDate === "<null>",
    apiItemPending: !!a.apiItem && a.apiItem.status === "HELD" && a.apiItem.resolutionTransactionId === ids.allocOut && a.apiItem.allocatedClientId === ids.moroka && a.apiItem.resolutionStatus === "AWAITING_APPROVAL",
    allocOutPendingShape: !!a.allocOut && a.allocOut.type === "UNIDENT_ALLOC_OUT" && a.allocOut.status === "AWAITING_APPROVAL" && !a.allocOut.posted && a.allocOut.date === D.allocationDate && a.allocOut.ledger === "NON_CLIENT" && a.allocOut.creditor === ids.udAccount && a.allocOut.item === L.A.itemId && /^UID-[0-9A-F]{8}$/.test(a.allocOut.reference),
    nothingMovedSql: same(a.delta, {}),
    unidentifiedUiApiSql: eq(a.page.cardValue, a.page.cardApi) && eq(a.page.cardApi, a.page.sqlBalance) && eq(a.page.sqlBalance, prepBooks.unidentified),
    morokaApiUnchanged: !!a.api.clientLedger && eq(a.api.clientLedger.balance, prepBooks.moroka),
  };
  save();

  // ════ (b) Transactions page: pending row label + description; Alice's own approval refused ════
  const b = (result.steps.b = {});
  b.row = await txPage("alice", "b1-pending", ids.allocOut);
  await clipMeasure(S.alice.pg, "b1-transactions-pending", ["transactions-table"]);
  const rowB = S.alice.pg.getByTestId(`transaction-row-${ids.allocOut}`);
  await rowB.getByTestId("approve-button").click();
  const errSpan = S.alice.pg.getByTestId(`approval-actions-${ids.allocOut}`).locator(".text-destructive");
  await errSpan.waitFor({ timeout: 30_000 });
  b.refusal = norm(await errSpan.innerText());
  b.files = await snap(S.alice.pg, "b2-alice-approve-refused");
  await crop(rowB, "b2-alice-refused-row-crop");
  b.allocOut = txDb(ids.allocOut);
  b.books = books("b: after Alice's refused approval");
  b.checks = {
    typeLabel: b.row.cells[2].startsWith("Unidentified deposit allocated"),
    descriptionLine: !!b.row.description && b.row.description.length > 0,
    approveOffered: b.row.hasApprove,
    refusedSelfApproval: /self-approval|recorder cannot be the sole approver|different member/i.test(b.refusal),
    stillPending: b.allocOut.status === "AWAITING_APPROVAL" && !b.allocOut.posted && b.allocOut.approvedBy === "<null>",
    nothingMoved: same(delta(prepBooks, b.books), {}),
  };
  save();

  // ════ (c) Bob approves: item ALLOCATED; UNIDENT_ALLOC_IN on Moroka's ledger, dated the allocation date ════
  const c = (result.steps.c = {});
  c.bobRow = await txPage("bob", "c1-bob-pending", ids.allocOut);
  await S.bob.pg.getByTestId(`transaction-row-${ids.allocOut}`).getByTestId("approve-button").click();
  await waitSql(`select status from T.trust_transactions where id='${ids.allocOut}'`, (v) => v === "APPROVED");
  ids.allocIn = await waitSql(`select coalesce(allocation_credit_transaction_id::text,'') from T.unidentified_deposits where id='${L.A.itemId}'`, (v) => !!v);
  await S.bob.pg.waitForTimeout(1500);
  c.bobToast = await S.bob.pg.locator("[data-sonner-toast]").first().innerText().catch(() => null);
  c.bobAfterFiles = await snap(S.bob.pg, "c2-bob-approved");
  c.item = itemDb(L.A.itemId);
  c.allocOut = txDb(ids.allocOut);
  c.allocIn = txDb(ids.allocIn);
  sql("c: item A after approval", itemSql(`d.id='${L.A.itemId}'`));
  sql("c: rows naming item A", rowSql(`t.unidentified_deposit_id='${L.A.itemId}'`));
  c.books = books("c: after approval");
  c.delta = delta(prepBooks, c.books);
  c.page = await itemsPage("alice", "c3-allocated");
  const rC = uiRow(c.page, L.A.desc);
  c.uiRow = rC;
  c.apiItem = apiItem(c.page, L.A.itemId);
  await clipMeasure(S.alice.pg, "c3-items-allocated", ["unidentified-deposits-table"]);
  // the client ledger (Moroka): UI + API history
  const pL = S.alice.pg;
  await pL.goto(`${BASE}/org/${SLUG}/trust-accounting/client-ledgers/${ids.moroka}`, { waitUntil: "load" });
  await pL.getByText(/Deposit received/).first().waitFor({ timeout: 30_000 }).catch(() => {});
  c.ledgerFiles = await snap(pL, "c4-moroka-client-ledger");
  c.ledgerText = norm(await pL.locator("body").innerText());
  const expDesc = `Deposit received ${longDate(L.A.date)} — ${L.A.desc}`;
  c.expectedDescription = expDesc;
  const ledgerRowLoc = pL.locator("tr", { hasText: "Deposit received" }).first();
  c.ledgerRowText = norm(await ledgerRowLoc.innerText().catch(() => ""));
  await crop(ledgerRowLoc, "c4-ledger-row-crop");
  c.apiLedger = (await api("alice", `/api/trust-accounts/${ids.account}/client-ledgers/${ids.moroka}`, "c-moroka-ledger")).body;
  c.apiHistory = (await api("alice", `/api/trust-accounts/${ids.account}/client-ledgers/${ids.moroka}/history?page=0&size=50`, "c-moroka-history")).body;
  const hist = (c.apiHistory && (c.apiHistory.content || c.apiHistory)) || [];
  c.apiAllocIn = Array.isArray(hist) ? hist.find((h) => h.id === ids.allocIn) : null;
  // dashboard: the firm-wide position
  await pL.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await pL.getByTestId("summary-cards").waitFor({ timeout: 30_000 }).catch(() => {});
  await pL.waitForTimeout(1000);
  c.dashFiles = await snap(pL, "c5-trust-dashboard");
  c.dashText = norm(await pL.getByTestId("summary-cards").innerText().catch(() => ""));
  c.position = (await api("alice", `/api/dashboard/trust-position?accountId=${ids.account}`, "c-trust-position")).body;
  // transactions page: both legs; Reverse offered on the allocation, not on its credit leg (VIA_PAIR)
  c.outRow = await txPage("alice", "c6-legs", ids.allocOut);
  c.inRow = await txPage("alice", "c6-legs-in", ids.allocIn, { noShot: true });
  const L1 = L.A.amount;
  c.checks = {
    allocOutApprovedPosted: c.allocOut.status === "APPROVED" && c.allocOut.posted && c.allocOut.approvedBy === ids.bob && eq(c.allocOut.ledgerEffect, -L1) && eq(c.allocOut.cashbookEffect, 0),
    allocInShape: !!c.allocIn && c.allocIn.type === "UNIDENT_ALLOC_IN" && c.allocIn.posted && c.allocIn.ledger === "CLIENT" && c.allocIn.customer === ids.moroka && c.allocIn.project === ids.morokaMatter && c.allocIn.date === D.allocationDate && eq(c.allocIn.ledgerEffect, L1) && eq(c.allocIn.cashbookEffect, 0) && c.allocIn.item === L.A.itemId && c.allocIn.reference === c.allocOut.reference,
    allocInDescriptionCarriesReceiptDate: !!c.allocIn && c.allocIn.description === expDesc,
    itemAllocated: c.item.status === "ALLOCATED" && c.item.creditTx === ids.allocIn && c.item.customer === ids.moroka && c.item.project === ids.morokaMatter && c.item.resolvedDate !== "<null>",
    movedClientFromUnidentified: same(c.delta, { unidentified: -L1, moroka: L1, clientLedgers: L1 }),
    booksBalance: c.books.balanced,
    uiAllocated: !!rC && rC.status === "Allocated" && rC.allocatedTo === `${ids.morokaName} — ${ids.morokaMatterName}` && !rC.hasAllocate && !rC.pending,
    apiAllocated: !!c.apiItem && c.apiItem.status === "ALLOCATED" && c.apiItem.allocatedClientName === ids.morokaName && c.apiItem.allocatedMatterName === ids.morokaMatterName,
    unidentifiedUiApiSql: eq(c.page.cardValue, c.page.cardApi) && eq(c.page.cardApi, c.page.sqlBalance) && eq(c.page.sqlBalance, prepBooks.unidentified - L1),
    ledgerUiShowsRow: c.ledgerText.includes(expDesc),
    ledgerApiBalance: !!c.apiLedger && eq(c.apiLedger.balance, c.books.moroka),
    ledgerApiHistoryRow: !!c.apiAllocIn,
    positionBalanced: !!c.position && eq(c.position.difference, 0) && eq(c.position.cashbookBalance, c.books.cashbook),
    reverseOnAllocOutOnly: c.outRow.hasReverse && !c.inRow.hasReverse,
  };
  save();

  // ════ (d) Portal — D12: what the client sees for the allocated row ════
  const d = (result.steps.d = {});
  const portalRowSql = `select id, transaction_type, amount, running_balance, transaction_date, description, reference from portal.portal_trust_transaction where customer_id='${ids.moroka}' order by transaction_date, recorded_at`;
  await waitSql(`select count(*) from portal.portal_trust_transaction where id='${ids.allocIn}'`, (v) => v === "1");
  sql("d: portal trust rows (Moroka) after approval", portalRowSql);
  const [pr] = rowsA(`select transaction_type, amount, transaction_date, coalesce(description,''), coalesce(reference,''), matter_id from portal.portal_trust_transaction where id='${ids.allocIn}'`);
  d.portalRow = pr ? { type: pr[0], amount: Number(pr[1]), date: pr[2], description: pr[3], reference: pr[4], matter: pr[5] } : null;
  sql("d: firm-only rows in the portal table (any client)", `select transaction_type, count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT') group by 1`);
  d.firmOnlyInPortal = Number(val(`select count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT')`));
  try {
    const pctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const cors = (req) => ({ "access-control-allow-origin": PORTAL, "access-control-allow-credentials": "true",
      "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "access-control-allow-headers": req.headers()["access-control-request-headers"] || "authorization,content-type" });
    await pctx.route(`${BACKEND}/**`, async (route) => {
      const req = route.request();
      if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors(req) });
      const resp = await route.fetch({ headers: { ...req.headers(), origin: "http://localhost:3002" } });
      await route.fulfill({ response: resp, headers: { ...resp.headers(), ...cors(req) } });
    });
    const pp = await pctx.newPage();
    watch(pp, "portal");
    await pp.goto(`${PORTAL}/login?orgId=${SLUG}`, { waitUntil: "load" });
    const email = val(`select email from T.portal_contacts where customer_id='${ids.moroka}' limit 1`);
    await pp.locator("input[type='email']").fill(email);
    await pp.locator("button[type='submit']").click();
    const link = pp.locator("a[href*='/auth/exchange']");
    await link.waitFor({ timeout: 60_000 });
    await link.click();
    await pp.waitForURL((u) => !/\/(login|auth\/exchange)/.test(u.toString()), { timeout: 60_000 });
    await pp.goto(`${PORTAL}/trust/${ids.morokaMatter}`, { waitUntil: "load" });
    await pp.getByText("Deposit identified and allocated").first().waitFor({ timeout: 60_000 });
    d.portalFiles = await snap(pp, "d1-portal-moroka-trust");
    d.portalText = norm(await pp.locator("body").innerText());
    const prow = pp.locator("tr, li, [role='row']", { hasText: "Deposit identified and allocated" }).first();
    d.portalRowText = norm(await prow.innerText().catch(() => ""));
    await crop(prow, "d1-portal-row-crop");
    d.portalUi = "OBSERVED";
    await pctx.close();
  } catch (err) {
    d.portalUi = "DEFERRED";
    d.portalError = String((err && err.message) || err).slice(0, 600);
  }
  d.checks = {
    portalRowSynced: !!d.portalRow && d.portalRow.type === "UNIDENT_ALLOC_IN" && eq(d.portalRow.amount, L.A.amount) && d.portalRow.date === D.allocationDate && d.portalRow.matter === ids.morokaMatter,
    portalDescriptionVerbatimD12: !!d.portalRow && d.portalRow.description === c.expectedDescription,
    noFirmOnlyRowsInPortal: d.firmOnlyInPortal === 0,
    portalUiLabel: d.portalUi === "OBSERVED" ? d.portalText.includes("Deposit identified and allocated") && !/Unident alloc in|UNIDENT_ALLOC_IN/i.test(d.portalText) : null,
    portalUiDescription: d.portalUi === "OBSERVED" ? d.portalText.includes(c.expectedDescription) : null,
  };
  save();

  // ════ (e) undo the allocation (Alice, Transactions page): item HELD again, both legs contra'd ════
  const e = (result.steps.e = {});
  const pE = S.alice.pg;
  await txPage("alice", "e0-before-undo", ids.allocOut, { noShot: true });
  await pE.getByTestId(`transaction-row-${ids.allocOut}`).getByTestId("reverse-button").click();
  const rdlg = pE.getByRole("dialog");
  await rdlg.getByTestId("reversal-reason-input").waitFor({ timeout: 15_000 });
  e.dialogText = norm(await rdlg.innerText());
  await rdlg.getByTestId("reversal-reason-input").fill(`OBS 647D ${RUN}: allocated to the wrong client`);
  e.dialogFiles = await snap(pE, "e1-undo-dialog");
  const cntE = txCount();
  await rdlg.getByTestId("confirm-reverse-button").click();
  await waitSql(`select coalesce(reversed_by_id::text,'') from T.trust_transactions where id='${ids.allocOut}'`, (v) => !!v);
  await pE.waitForTimeout(1500);
  e.item = itemDb(L.A.itemId);
  e.allocOut = txDb(ids.allocOut);
  e.allocIn = txDb(ids.allocIn);
  e.contraOut = e.allocOut.reversedBy !== "<null>" ? txDb(e.allocOut.reversedBy) : null;
  e.contraIn = e.allocIn.reversedBy !== "<null>" ? txDb(e.allocIn.reversedBy) : null;
  e.rowsAdded = txCount() - cntE;
  sql("e: rows naming item A after undo", rowSql(`t.unidentified_deposit_id='${L.A.itemId}'`));
  sql("e: item A after undo", itemSql(`d.id='${L.A.itemId}'`));
  e.books = books("e: after undo");
  e.delta = delta(prepBooks, e.books);
  e.txAfter = await txPage("alice", "e2-after-undo", ids.allocOut);
  e.page = await itemsPage("alice", "e3-held-again");
  const rE = uiRow(e.page, L.A.desc);
  e.uiRow = rE;
  e.apiItem = apiItem(e.page, L.A.itemId);
  sql("e: portal trust rows (Moroka) after undo", portalRowSql);
  const L2 = L.A.amount;
  e.checks = {
    twoContras: e.rowsAdded === 2 && !!e.contraOut && !!e.contraIn,
    contraOutShape: !!e.contraOut && e.contraOut.type === "REVERSAL" && e.contraOut.posted && e.contraOut.item === L.A.itemId && e.contraOut.creditor === ids.udAccount && eq(e.contraOut.ledgerEffect, L2) && eq(e.contraOut.cashbookEffect, 0) && e.contraOut.date === sastToday,
    contraInShape: !!e.contraIn && e.contraIn.type === "REVERSAL" && e.contraIn.posted && e.contraIn.item === L.A.itemId && e.contraIn.customer === ids.moroka && eq(e.contraIn.ledgerEffect, -L2) && eq(e.contraIn.cashbookEffect, 0) && e.contraIn.date === sastToday,
    legsReversed: e.allocOut.status === "REVERSED" && e.allocIn.status === "REVERSED",
    itemHeldAgain: e.item.status === "HELD" && e.item.resolvedDate === "<null>" && e.item.resolutionTx === "<null>",
    balancesRestored: same(e.delta, {}),
    booksBalance: e.books.balanced,
    uiHeldWithActions: !!rE && rE.status === "Held" && rE.hasAllocate && rE.hasPayToFund && !rE.allocatedTo,
    apiHeld: !!e.apiItem && e.apiItem.status === "HELD" && e.apiItem.resolutionTransactionId === null,
    unidentifiedUiApiSql: eq(e.page.cardValue, e.page.cardApi) && eq(e.page.cardApi, e.page.sqlBalance) && eq(e.page.sqlBalance, prepBooks.unidentified),
  };
  save();

  // ════ (f) F-647D-1: unmatching line A in the wizard is refused; nothing moves ════
  const f = (result.steps.f = {});
  const wz = S.alice.wiz;
  await wz.bringToFront();
  const cntF = txCount();
  await wz.getByTestId(`unmatch-btn-${L.A.lineId}`).click();
  const udlg = wz.getByTestId("unmatch-recorded-dialog");
  await udlg.waitFor({ timeout: 15_000 });
  f.dialogText = norm(await udlg.innerText());
  await udlg.getByTestId("unmatch-recorded-confirm").click();
  const banner = wz.getByTestId("reconciliation-error");
  await banner.waitFor({ timeout: 30_000 }).catch(() => {});
  await wz.waitForTimeout(1000);
  f.banner = norm(await banner.innerText().catch(() => ""));
  f.dialogStillOpen = await udlg.isVisible().catch(() => false);
  f.dialogErr = f.dialogStillOpen ? norm(await udlg.innerText()) : null;
  f.files = await snap(wz, "f1-unmatch-refused");
  await crop(banner, "f1-refusal-banner-crop");
  f.lineText = norm(await wz.getByTestId(`bank-line-${L.A.lineId}`).innerText().catch(() => ""));
  const [lf] = rowsA(`select match_status, coalesce(classification,'<null>'), coalesce(trust_transaction_id::text,'<null>') from T.bank_statement_lines where id='${L.A.lineId}'`);
  f.line = { matchStatus: lf[0], classification: lf[1], trustTransactionId: lf[2] };
  f.item = itemDb(L.A.itemId);
  f.rowsAdded = txCount() - cntF;
  sql("f: lines after refused unmatch", lineSql(ids.statement));
  f.books = books("f: after refused unmatch");
  if (f.dialogStillOpen) await udlg.getByRole("button", { name: /Keep|Cancel/ }).first().click().catch(() => {});
  f.checks = {
    refusedWithMessage: /Cannot unmatch statement line/i.test(f.banner + " " + (f.dialogErr || "")) || /can no longer be unmatched/i.test(f.banner + " " + (f.dialogErr || "")),
    lineStillRecorded: f.line.matchStatus === "RECORDED" && f.line.classification === "UNIDENTIFIED_DEPOSIT",
    itemStillHeld: f.item.status === "HELD",
    nothingMoved: f.rowsAdded === 0 && same(delta(e.books, f.books), {}),
  };
  save();

  // ════ (g) Pay to the Fund: B requested by Alice, approved by Bob; C requested, rejected by Bob ════
  const g = (result.steps.g = {});
  async function requestFund(l, tag) {
    const p = S.alice.pg;
    await itemsPage("alice", `${tag}0-before-fund`);
    await p.getByTestId(`pay-to-fund-btn-${l.itemId}`).click();
    const dlg = p.getByTestId("pay-to-fund-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    const r = { dialogText: norm(await dlg.innerText()), age: norm(await dlg.getByTestId("pay-to-fund-age").innerText()),
      warningVisible: await dlg.getByTestId("pay-to-fund-under-one-year-warning").isVisible().catch(() => false),
      warningRole: await dlg.getByTestId("pay-to-fund-under-one-year-warning").getAttribute("role").catch(() => null),
      warning: norm(await dlg.getByTestId("pay-to-fund-under-one-year-warning").innerText().catch(() => "")),
      defaultDate: await dlg.getByTestId("pay-to-fund-date").inputValue(), dateMin: await dlg.getByTestId("pay-to-fund-date").getAttribute("min"),
      dateMax: await dlg.getByTestId("pay-to-fund-date").getAttribute("max"), reference: `LPFF-${l.k}/OBS647D/${RUN}` };
    await dlg.getByTestId("pay-to-fund-reference").fill(r.reference);
    await dlg.getByTestId("pay-to-fund-notes").fill(`OBS 647D ${RUN}: unclaimed, pay over (${l.k})`);
    r.files = await snap(p, `${tag}1-pay-to-fund-dialog`);
    await crop(dlg, `${tag}1-pay-to-fund-dialog-crop`);
    const cnt = txCount();
    await dlg.getByTestId("pay-to-fund-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    r.txId = await waitSql(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${l.itemId}'`, (v) => !!v);
    r.rowsAdded = txCount() - cnt;
    r.tx = txDb(r.txId);
    r.item = itemDb(l.itemId);
    r.page = await itemsPage("alice", `${tag}2-fund-pending`);
    r.uiRow = uiRow(r.page, l.desc);
    r.apiItem = apiItem(r.page, l.itemId);
    return r;
  }
  const gBefore = (g.booksBefore = books("g: before"));
  const LB = L.B.amount;
  g.B = await requestFund(L.B, "g");
  await clipMeasure(S.alice.pg, "g2-items-fund-pending", ["unidentified-deposits-table"]);
  g.B.booksAfterRequest = books("g: after B requested");
  g.B.bobRow = await txPage("bob", "g3-bob-fund-pending", g.B.txId);
  await clipMeasure(S.bob.pg, "g3-transactions-fund-pending", ["transactions-table"]);
  await S.bob.pg.getByTestId(`transaction-row-${g.B.txId}`).getByTestId("approve-button").click();
  await waitSql(`select status from T.trust_transactions where id='${g.B.txId}'`, (v) => v === "APPROVED");
  await waitSql(`select status from T.unidentified_deposits where id='${L.B.itemId}'`, (v) => v === "PAID_TO_FUND");
  await S.bob.pg.waitForTimeout(1500);
  g.B.bobAfter = await snap(S.bob.pg, "g4-bob-fund-approved");
  g.B.txAfter = txDb(g.B.txId);
  g.B.itemAfter = itemDb(L.B.itemId);
  sql("g: rows naming item B", rowSql(`t.unidentified_deposit_id='${L.B.itemId}'`));
  sql("g: item B after approval", itemSql(`d.id='${L.B.itemId}'`));
  g.B.booksAfter = books("g: after B approved");
  g.B.delta = delta(gBefore, g.B.booksAfter);
  g.B.pageAfter = await itemsPage("alice", "g5-paid-to-fund");
  g.B.uiAfter = uiRow(g.B.pageAfter, L.B.desc);
  g.B.apiAfter = apiItem(g.B.pageAfter, L.B.itemId);
  g.B.fundLedgerApi = (await api("alice", `/api/trust-creditor-accounts/${ids.fundAccount}/transactions?page=0&size=25`, "g-fund-ledger")).body;
  g.B.udLedgerApi = (await api("alice", `/api/trust-creditor-accounts/${ids.udAccount}/transactions?page=0&size=25`, "g-unidentified-ledger")).body;
  g.B.checks = {
    ageText: g.B.age === `Held for ${daysBetween(L.B.date, sastToday)} days`,
    underOneYearWarning: g.B.warningVisible && g.B.warningRole === "status" && /less than a year/i.test(g.B.warning),
    dateWindow: g.B.defaultDate === sastToday && g.B.dateMin === L.B.date && g.B.dateMax === sastToday,
    oneRowPending: g.B.rowsAdded === 1 && g.B.tx.type === "UNIDENT_FUND_PAY" && g.B.tx.status === "AWAITING_APPROVAL" && !g.B.tx.posted && g.B.tx.reference === g.B.reference && g.B.tx.date === sastToday && g.B.tx.item === L.B.itemId,
    pendingTextUi: !!g.B.uiRow && g.B.uiRow.pending === "Payment to the Fund awaiting approval" && !g.B.uiRow.hasAllocate && !g.B.uiRow.hasPayToFund,
    nothingMovedOnRequest: same(delta(gBefore, g.B.booksAfterRequest), {}),
    bobRowDescription: !!g.B.bobRow.description && g.B.bobRow.cells[2].startsWith("Unidentified deposit paid to the Fund"),
    approvedPosted: g.B.txAfter.status === "APPROVED" && g.B.txAfter.posted && g.B.txAfter.approvedBy === ids.bob && eq(g.B.txAfter.ledgerEffect, -LB) && eq(g.B.txAfter.cashbookEffect, -LB) && g.B.txAfter.creditor === ids.udAccount,
    itemPaidToFund: g.B.itemAfter.status === "PAID_TO_FUND" && g.B.itemAfter.resolvedDate === sastToday,
    movesPerPostingTable: same(g.B.delta, { cashbook: -LB, unidentified: -LB }),
    booksBalance: g.B.booksAfter.balanced,
    uiApiPaid: !!g.B.uiAfter && !g.B.uiAfter.hasAllocate && !g.B.uiAfter.pending && !!g.B.apiAfter && g.B.apiAfter.status === "PAID_TO_FUND",
    unidentifiedUiApiSql: eq(g.B.pageAfter.cardValue, g.B.pageAfter.cardApi) && eq(g.B.pageAfter.cardApi, g.B.pageAfter.sqlBalance) && eq(g.B.pageAfter.sqlBalance, gBefore.unidentified - LB),
    fundLedgerUnchangedApi: eq(g.B.pageAfter.fundApi, gBefore.fund),
  };
  save();

  // reject path: C
  const gcBefore = (g.booksBeforeC = books("g: before C"));
  g.C = await requestFund(L.C, "gc");
  await txPage("bob", "gc3-bob-fund-pending", g.C.txId);
  await S.bob.pg.getByTestId(`transaction-row-${g.C.txId}`).getByTestId("reject-button").click();
  const rj = S.bob.pg.getByRole("dialog");
  await rj.getByTestId("rejection-reason-input").waitFor({ timeout: 15_000 });
  await rj.getByTestId("rejection-reason-input").fill(`OBS 647D ${RUN}: the owner has been traced — do not pay over`);
  await snap(S.bob.pg, "gc4-bob-reject-dialog");
  await rj.getByTestId("confirm-reject-button").click();
  await waitSql(`select status from T.trust_transactions where id='${g.C.txId}'`, (v) => v === "REJECTED");
  await S.bob.pg.waitForTimeout(1000);
  g.C.txAfter = txDb(g.C.txId);
  g.C.itemAfter = itemDb(L.C.itemId);
  sql("g: rows naming item C", rowSql(`t.unidentified_deposit_id='${L.C.itemId}'`));
  g.C.booksAfter = books("g: after C rejected");
  g.C.pageAfter = await itemsPage("alice", "gc5-rejected-held");
  g.C.uiAfter = uiRow(g.C.pageAfter, L.C.desc);
  g.C.checks = {
    ageText: g.C.age === `Held for ${daysBetween(L.C.date, sastToday)} days`,
    warning: g.C.warningVisible,
    rejectedNotPosted: g.C.txAfter.status === "REJECTED" && !g.C.txAfter.posted,
    itemHeldResolutionCleared: g.C.itemAfter.status === "HELD" && g.C.itemAfter.resolutionTx === "<null>" && g.C.itemAfter.resolvedDate === "<null>",
    nothingMoved: same(delta(gcBefore, g.C.booksAfter), {}),
    uiHeldActionsBack: !!g.C.uiAfter && g.C.uiAfter.status === "Held" && g.C.uiAfter.hasAllocate && g.C.uiAfter.hasPayToFund && !g.C.uiAfter.pending,
  };
  save();

  // ════ final: books, portal table, clip of the final Transactions page ════
  const fin = (result.steps.final = {});
  fin.books = books("final");
  fin.booksAtAllocationDate = books("final", D.allocationDate);
  fin.booksAtReceiptA = books("final", L.A.date);
  sql("final: OBS rows", rowSql(`t.unidentified_deposit_id in (select id from T.unidentified_deposits where bank_statement_line_id in (select id from T.bank_statement_lines where bank_statement_id='${ids.statement}'))`));
  sql("final: OBS items", itemSql(`d.trust_account_id='${ids.account}'`));
  sql("final: portal trust rows (Moroka)", portalRowSql);
  sql("final: firm-only rows in the portal table (any client)", `select transaction_type, count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT') group by 1`);
  fin.firmOnlyInPortal = Number(val(`select count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT')`));
  await txPage("alice", "z1-final", ids.allocOut);
  await clipMeasure(S.alice.pg, "z1-transactions-final", ["transactions-table"]);
  fin.position = (await api("alice", `/api/dashboard/trust-position?accountId=${ids.account}`, "z-trust-position")).body;
  fin.checks = { booksBalanceToday: fin.books.balanced, booksBalanceAtAllocationDate: fin.booksAtAllocationDate.balanced, booksBalanceAtReceiptA: fin.booksAtReceiptA.balanced,
    noFirmOnlyRowsInPortal: fin.firmOnlyInPortal === 0, positionBalanced: !!fin.position && eq(fin.position.difference, 0) };

  const allTrue = (o) => !!o && Object.values(o).every((v) => v === true || v === null);
  result.verdict = {
    prep: allTrue(prep.checks), a: allTrue(a.checks), b: allTrue(b.checks), c: allTrue(c.checks), d: allTrue(d.checks), dPortalUi: d.portalUi,
    e: allTrue(e.checks), f: allTrue(f.checks), gB: allTrue(g.B.checks), gC: allTrue(g.C.checks), final: allTrue(fin.checks),
    clip: result.clip.map((x) => `${x.label}:${x.ok ? "OK" : "CLIPPED"}`),
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length,
  };
  result.finishedAt = new Date().toISOString();
  save();
  console.log(JSON.stringify(result.verdict, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap(S.alice.pg, "zz-fatal-alice", [1440]); } catch {}
  try { await snap(S.alice.wiz, "zz-fatal-wizard", [1440]); } catch {}
  try { await snap(S.bob.pg, "zz-fatal-bob", [1440]); } catch {}
  result.fatal = String((err && err.stack) || err);
  result.finishedAt = new Date().toISOString();
  save();
  process.exit(1);
});
