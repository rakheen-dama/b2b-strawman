// 647D OBS continuation (obs-g.cjs): step g onward, after obs.cjs died in g on a harness TypeError (a transient
// empty docker-exec read in itemDb, right after item B's Fund-payment request was submitted through the UI).
// Same helpers as obs.cjs, plus: retry on an empty single-row read, count-guards instead of 30 s waits
// for absent per-row elements, and the SQL evidence is APPENDED to obs-sums.sql/.out.
//
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
const save = () => fs.writeFileSync(`${OUT}/obs-g-result.json`, JSON.stringify(result, null, 2));

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
fs.appendFileSync(SQL_FILE, `\n-- ===== obs-g.cjs continuation =====\n-- 647D OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.appendFileSync(SQL_OUT, `\n== ===== obs-g.cjs continuation =====\n-- 647D OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
const T = (q) => q.replace(/\bT\./g, `${SCHEMA}.`);
function sql(label, q) {
  const qq = T(q);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(T(q));
function rowsA(q) { for (let i = 0; i < 4; i++) { const v = val(q); if (v) return v.split("\n").filter(Boolean).map((l) => l.split("|")); result.notes.push(`empty read retried: ${q.slice(0, 80)}`); } return []; }
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
async function optText(loc) { return (await loc.count()) ? norm(await loc.first().innerText()) : null; }
let shot = 30; // continue numbering after obs.cjs (its last shot was 19)
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
    const allocBtn = (await r.locator("[data-testid^='allocate-btn-']").count()) ? await r.locator("[data-testid^='allocate-btn-']").getAttribute("data-testid") : null;
    const id = allocBtn ? allocBtn.replace("allocate-btn-", "") : null;
    rows[cells[1]] = { id, cells, status: norm(await r.getByTestId("unidentified-deposit-status").innerText()),
      pending: await optText(r.getByTestId("unidentified-deposit-pending")),
      allocatedTo: await optText(r.getByTestId("unidentified-deposit-allocated-to")),
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
  const r = { cells, description: await optText(p.getByTestId(`transaction-description-${txId}`)),
    hasApprove: (await row.getByTestId("approve-button").count()) > 0, hasReverse: (await row.getByTestId("reverse-button").count()) > 0 };
  if (!opts.noShot) { r.files = await snap(p, `${label}-transactions-${who}`); await crop(row, `${label}-tx-row-crop`); }
  return r;
}


(async () => {
  browser = await chromium.launch({ headless: true });
  const prev = JSON.parse(fs.readFileSync(`${OUT}/obs-result.json`, "utf8"));
  ids = result.ids = prev.ids;
  const LINES = prev.lines;
  const L = Object.fromEntries(LINES.map((l) => [l.k, l]));
  const D = (result.dates = prev.dates);
  if (D.today !== sastToday) throw new Error(`SAST date moved: ${D.today} -> ${sastToday}`);
  await newSession("alice", "user_e2e_alice");
  await newSession("bob", "user_e2e_bob");
  const g = (result.steps.g = {});

  // ── B: the request was submitted through the UI by obs.cjs (its dialog: obs/19-g1-*). Read it back. ──
  const gBefore = (g.booksBefore = prev.steps.g.booksBefore);
  const dialogTxt = norm(fs.readFileSync(`${OUT}/19-g1-pay-to-fund-dialog.txt`, "utf8"));
  const LB = L.B.amount;
  g.B = { dialogFromObs: dialogTxt.slice(dialogTxt.indexOf("Pay to the Fidelity Fund"), dialogTxt.indexOf("Pay to the Fidelity Fund") + 700) };
  g.B.age = (g.B.dialogFromObs.match(/Held for \d+ days?/) || [null])[0];
  g.B.warningShown = /held for less than a year/i.test(g.B.dialogFromObs);
  g.B.txId = val(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${L.B.itemId}'`);
  g.B.reference = `LPFF-B/OBS647D/${prev.run}`;
  g.B.tx = txDb(g.B.txId);
  g.B.item = itemDb(L.B.itemId);
  sql("g: rows naming item B (requested)", rowSql(`t.unidentified_deposit_id='${L.B.itemId}'`));
  g.B.page = await itemsPage("alice", "g2-fund-pending");
  g.B.uiRow = uiRow(g.B.page, L.B.desc);
  g.B.apiItem = apiItem(g.B.page, L.B.itemId);
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
  sql("g: rows naming item B (approved)", rowSql(`t.unidentified_deposit_id='${L.B.itemId}'`));
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
    underOneYearWarning: g.B.warningShown,
    oneRowPending: !!g.B.tx && g.B.tx.type === "UNIDENT_FUND_PAY" && g.B.tx.status === "AWAITING_APPROVAL" && !g.B.tx.posted && g.B.tx.reference === g.B.reference && g.B.tx.date === sastToday && g.B.tx.item === L.B.itemId,
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

  // ── C: Alice requests a payment to the Fund through the dialog; Bob rejects ──
  const gcBefore = (g.booksBeforeC = books("g: before C"));
  const pA = S.alice.pg;
  g.C = {};
  await itemsPage("alice", "gc0-before-fund");
  await pA.getByTestId(`pay-to-fund-btn-${L.C.itemId}`).click();
  const dlg = pA.getByTestId("pay-to-fund-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  Object.assign(g.C, { dialogText: norm(await dlg.innerText()), age: norm(await dlg.getByTestId("pay-to-fund-age").innerText()),
    warningVisible: await dlg.getByTestId("pay-to-fund-under-one-year-warning").isVisible(),
    warningRole: await dlg.getByTestId("pay-to-fund-under-one-year-warning").getAttribute("role"),
    warning: norm(await dlg.getByTestId("pay-to-fund-under-one-year-warning").innerText()),
    defaultDate: await dlg.getByTestId("pay-to-fund-date").inputValue(), dateMin: await dlg.getByTestId("pay-to-fund-date").getAttribute("min"),
    dateMax: await dlg.getByTestId("pay-to-fund-date").getAttribute("max"), reference: `LPFF-C/OBS647D/${prev.run}` });
  await dlg.getByTestId("pay-to-fund-reference").fill(g.C.reference);
  await dlg.getByTestId("pay-to-fund-notes").fill(`OBS 647D ${prev.run}: unclaimed, pay over (C)`);
  g.C.files = await snap(pA, "gc1-pay-to-fund-dialog");
  await crop(dlg, "gc1-pay-to-fund-dialog-crop");
  const cnt = txCount();
  await dlg.getByTestId("pay-to-fund-submit").click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  g.C.txId = await waitSql(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${L.C.itemId}'`, (v) => !!v);
  g.C.rowsAdded = txCount() - cnt;
  g.C.tx = txDb(g.C.txId);
  g.C.page = await itemsPage("alice", "gc2-fund-pending");
  g.C.uiRow = uiRow(g.C.page, L.C.desc);
  await txPage("bob", "gc3-bob-fund-pending", g.C.txId);
  await S.bob.pg.getByTestId(`transaction-row-${g.C.txId}`).getByTestId("reject-button").click();
  const rj = S.bob.pg.getByRole("dialog");
  await rj.getByTestId("rejection-reason-input").waitFor({ timeout: 15_000 });
  await rj.getByTestId("rejection-reason-input").fill(`OBS 647D ${prev.run}: the owner has been traced — do not pay over`);
  await snap(S.bob.pg, "gc4-bob-reject-dialog");
  await rj.getByTestId("confirm-reject-button").click();
  await waitSql(`select status from T.trust_transactions where id='${g.C.txId}'`, (v) => v === "REJECTED");
  await S.bob.pg.waitForTimeout(1000);
  g.C.txAfter = txDb(g.C.txId);
  g.C.itemAfter = itemDb(L.C.itemId);
  sql("g: rows naming item C", rowSql(`t.unidentified_deposit_id='${L.C.itemId}'`));
  sql("g: item C after rejection", itemSql(`d.id='${L.C.itemId}'`));
  g.C.booksAfter = books("g: after C rejected");
  g.C.pageAfter = await itemsPage("alice", "gc5-rejected-held");
  g.C.uiAfter = uiRow(g.C.pageAfter, L.C.desc);
  g.C.checks = {
    ageText: g.C.age === `Held for ${daysBetween(L.C.date, sastToday)} days`,
    underOneYearWarning: g.C.warningVisible && g.C.warningRole === "status" && /less than a year/i.test(g.C.warning),
    dateWindow: g.C.defaultDate === sastToday && g.C.dateMin === L.C.date && g.C.dateMax === sastToday,
    oneRowPending: g.C.rowsAdded === 1 && g.C.tx.type === "UNIDENT_FUND_PAY" && g.C.tx.status === "AWAITING_APPROVAL" && g.C.tx.reference === g.C.reference,
    pendingTextUi: !!g.C.uiRow && g.C.uiRow.pending === "Payment to the Fund awaiting approval",
    rejectedNotPosted: g.C.txAfter.status === "REJECTED" && !g.C.txAfter.posted,
    itemHeldResolutionCleared: g.C.itemAfter.status === "HELD" && g.C.itemAfter.resolutionTx === "<null>" && g.C.itemAfter.resolvedDate === "<null>",
    nothingMoved: same(delta(gcBefore, g.C.booksAfter), {}),
    uiHeldActionsBack: !!g.C.uiAfter && g.C.uiAfter.status === "Held" && g.C.uiAfter.hasAllocate && g.C.uiAfter.hasPayToFund && !g.C.uiAfter.pending,
  };
  save();

  // ── (d, portal UI) the Moroka contact signs in through the portal's own login (dev magic link) ──
  // obs.cjs's portal attempt reached the trust page but its locator matched a hidden mobile-only line first
  // (harness). Re-read here; by now the allocation has also been undone (e), so its reversal shows too.
  const dp = (result.steps.dPortal = {});
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
    await pp.waitForFunction(() => document.body.innerText.includes("Deposit identified and allocated"), null, { timeout: 60_000 });
    await pp.waitForTimeout(800);
    dp.files = await snap(pp, "d1-portal-moroka-trust");
    dp.text = norm(await pp.locator("body").innerText());
    const vis = pp.locator("tr:visible", { hasText: "Deposit identified and allocated" });
    dp.rowText = (await vis.count()) ? norm(await vis.first().innerText()) : null;
    if (await vis.count()) await crop(vis.first(), "d1-portal-allocated-row-crop");
    const visRev = pp.locator("tr:visible", { hasText: "UID-" }).filter({ hasNotText: "Deposit identified and allocated" });
    dp.reversalRowText = (await visRev.count()) ? norm(await visRev.first().innerText()) : null;
    await pp.setViewportSize({ width: 390, height: 900 });
    await pp.waitForTimeout(600);
    await pp.screenshot({ path: `${OUT}/${String(shot).padStart(2, "0")}-d1-portal-moroka-trust-390.png`, fullPage: true });
    dp.mobileText = norm(await pp.locator("body").innerText());
    dp.ui = "OBSERVED";
    await pctx.close();
  } catch (err) {
    dp.ui = "DEFERRED";
    dp.error = String((err && err.message) || err).slice(0, 800);
  }
  const expDesc = prev.steps.c.expectedDescription;
  dp.checks = {
    labelShown: dp.ui === "OBSERVED" && dp.text.includes("Deposit identified and allocated") && !/Unident alloc in|UNIDENT_ALLOC_IN/i.test(dp.text),
    descriptionVerbatimD12: dp.ui === "OBSERVED" && dp.text.includes(expDesc),
    noFirmOnlyLabels: dp.ui === "OBSERVED" && !/Unidentified deposit allocated|paid to the Fund|UNIDENT_/i.test(dp.text),
  };
  save();

  // ── final: books, portal table, final Transactions page + clip ──
  const fin = (result.steps.final = {});
  fin.books = books("final");
  fin.booksAtAllocationDate = books("final", D.allocationDate);
  fin.booksAtReceiptA = books("final", L.A.date);
  sql("final: OBS rows", rowSql(`t.unidentified_deposit_id in (select id from T.unidentified_deposits where bank_statement_line_id in (select id from T.bank_statement_lines where bank_statement_id='${ids.statement}'))`));
  sql("final: OBS items", itemSql(`d.trust_account_id='${ids.account}'`));
  sql("final: portal trust rows (Moroka)", `select id, transaction_type, amount, running_balance, transaction_date, description, reference from portal.portal_trust_transaction where customer_id='${ids.moroka}' order by transaction_date, recorded_at`);
  sql("final: firm-only rows in the portal table (any client)", `select transaction_type, count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT') group by 1`);
  fin.firmOnlyInPortal = Number(val(`select count(*) from portal.portal_trust_transaction where transaction_type in ('UNIDENT_ALLOC_OUT','UNIDENT_FUND_PAY','UNIDENTIFIED_DEPOSIT')`));
  fin.tx = await txPage("alice", "z1-final", ids.allocOut);
  await clipMeasure(S.alice.pg, "z1-transactions-final", ["transactions-table"]);
  await itemsPage("alice", "z2-final");
  await clipMeasure(S.alice.pg, "z2-items-final", ["unidentified-deposits-table"]);
  fin.position = (await api("alice", `/api/dashboard/trust-position?accountId=${ids.account}`, "z-trust-position")).body;
  fin.checks = { booksBalanceToday: fin.books.balanced, booksBalanceAtAllocationDate: fin.booksAtAllocationDate.balanced, booksBalanceAtReceiptA: fin.booksAtReceiptA.balanced,
    noFirmOnlyRowsInPortal: fin.firmOnlyInPortal === 0, positionBalanced: !!fin.position && eq(fin.position.difference, 0) && eq(fin.position.cashbookBalance, fin.books.cashbook) };

  const allTrue = (o) => !!o && Object.values(o).every((v) => v === true);
  result.verdict = { gB: allTrue(g.B.checks), gC: allTrue(g.C.checks), dPortalUi: dp.ui, dPortal: allTrue(dp.checks), final: allTrue(fin.checks),
    clip: result.clip.map((x) => `${x.label}:${x.ok ? "OK" : "CLIPPED"}`), consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length };
  result.finishedAt = new Date().toISOString();
  save();
  console.log(JSON.stringify(result.verdict, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS-G FAILED:", err);
  try { await snap(S.alice.pg, "zz-fatal-alice", [1440]); } catch {}
  try { await snap(S.bob.pg, "zz-fatal-bob", [1440]); } catch {}
  result.fatal = String((err && err.stack) || err);
  result.finishedAt = new Date().toISOString();
  save();
  process.exit(1);
});
