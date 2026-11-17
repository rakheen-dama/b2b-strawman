// 647D RE-GATE OBS h @ 45dd3022f — real headless Chromium against the e2e mock-auth stack (:3001), after the
// lifecycle run. Adapted from ../obs.cjs + ../obs-g.cjs + ../probe-clip.cjs + ../diag-tx-width.cjs.
// Every state change goes through the UI: Alice (owner) uploads a statement with four unknown credits and records
// each as an Unidentified deposit (wizard), then:
//   A → Alice requests an allocation to Moroka / Estate, Bob approves   (smoke: unidentified −L, client +L, cashbook 0)
//   B → Alice requests a payment to the Fund, Bob approves
//   C → Alice requests an allocation, left PENDING (Approve / Reject on the row)
//   D → Alice requests a payment to the Fund, left PENDING (Approve / Reject on the row)
// so the Transactions page carries "Unidentified deposit allocated" (approved + pending), "Deposit identified and
// allocated", and "Unidentified deposit paid to the Fund" (approved + pending). Then the bounding-rect clip probe on
// the Transactions page (Bob and Alice) at 1280 / 1440 / 1920 and the Other trust creditors tables at 1280 / 1440.
// psql is read-only EVIDENCE. API reads use each browser context's own mock-auth-token against :8081.
// No API writes, no SQL writes.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/647D/gates/regate-45dd302`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [], clip: [], startedAt: new Date().toISOString() };
const save = () => fs.writeFileSync(`${OUT}/regate-obs-result.json`, JSON.stringify(result, null, 2));

const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) { const d = new Date(`${iso}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); }
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ── read-only SQL evidence (with retry on a transient empty read: first run O-1) ──
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(SQL_FILE, `-- 647D re-gate OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 647D re-gate OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
const T = (q) => q.replace(/\bT\./g, `${SCHEMA}.`);
function sql(label, q) {
  const qq = T(q);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(T(q));
function rowsA(q, mustHaveRow = false) {
  for (let i = 0; i < 6; i++) {
    const rows = (val(q) || "").split("\n").filter(Boolean).map((l) => l.split("|"));
    if (rows.length || !mustHaveRow) return rows;
    execFileSync("sleep", ["0.5"]);
  }
  throw new Error(`empty read after retries: ${q}`);
}
const eq = (x, y) => x != null && y != null && !Number.isNaN(Number(x)) && !Number.isNaN(Number(y)) && Math.abs(Number(x) - Number(y)) < 0.005;
async function waitSql(q, pred, ms = 30_000) {
  const t0 = Date.now();
  let v = val(q);
  while (!pred(v) && Date.now() - t0 < ms) { await sleep(500); v = val(q); }
  return v;
}

// ── browser helpers ──
let browser;
const S = {};
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
  if (name === "alice") { s.pg = await ctx.newPage(); watch(s.pg, `${name}-2`); }
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
    await p.waitForTimeout(600);
    const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}-${w}.png`;
    await p.screenshot({ path: file, fullPage: true });
    files.push(file.replace(`${OUT}/`, ""));
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  fs.writeFileSync(`${OUT}/${String(shot).padStart(2, "0")}-${name}.txt`, await p.locator("body").innerText().catch(() => ""));
  return files;
}
async function crop(locator, name) {
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await locator.screenshot({ path: file }).catch((e) => result.notes.push(`crop ${name} failed: ${e.message.split("\n")[0]}`));
  return file.replace(`${OUT}/`, "");
}
async function token(ctx) { const tok = (await ctx.cookies()).find((c) => c.name === "mock-auth-token"); return tok ? tok.value : ""; }
async function api(who, path, saveAs) {
  const ctx = S[who].ctx;
  const res = await ctx.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token(ctx)}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { path, status: res.status(), body };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
  return r;
}

// ── SQL shapes ──
let ids;
const rowSql = (where) => `select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.customer_id, t.project_id, t.creditor_account_id, t.unidentified_deposit_id, t.reference, t.description, t.reversal_of, t.recorded_by, t.approved_by from T.trust_transactions t where ${where} order by t.created_at`;
const itemSql = (where) => `select d.id, d.received_date, d.bank_description, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.allocation_credit_transaction_id, d.allocated_customer_id, d.allocated_project_id from T.unidentified_deposits d where ${where} order by d.received_date, d.created_at, d.id`;
const clientBal = (cust) => `select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and customer_id='${cust}' and ledger='CLIENT' and posted_at is not null and transaction_date <= date '${sastToday}'`;
const eqSql = (asAt) => `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${asAt}') cashbook, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and posted_at is not null and transaction_date <= date '${asAt}') all_creditors, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where trust_account_id='${ids.account}' and ledger='CLIENT' and posted_at is not null and transaction_date <= date '${asAt}') client_ledgers, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${ids.udAccount}' and posted_at is not null and transaction_date <= date '${asAt}') unidentified_ledger, (select coalesce(sum(ledger_effect),0) from T.trust_transactions where creditor_account_id='${ids.fundAccount}' and posted_at is not null and transaction_date <= date '${asAt}') fund_ledger`;
function books(label, asAt = sastToday) {
  sql(`${label}: books as at ${asAt}`, eqSql(asAt));
  const [r] = rowsA(eqSql(asAt), true);
  const b = { asAt, cashbook: Number(r[0]), allCreditors: Number(r[1]), clientLedgers: Number(r[2]), unidentified: Number(r[3]), fund: Number(r[4]), moroka: Number(val(clientBal(ids.moroka))) };
  b.balanced = eq(b.cashbook, b.allCreditors) && eq(b.allCreditors, b.clientLedgers + b.unidentified + b.fund);
  return b;
}
const txCount = () => Number(val(`select count(*) from T.trust_transactions`));
const delta = (a, b) => ({ cashbook: +(b.cashbook - a.cashbook).toFixed(2), unidentified: +(b.unidentified - a.unidentified).toFixed(2), fund: +(b.fund - a.fund).toFixed(2), moroka: +(b.moroka - a.moroka).toFixed(2), clientLedgers: +(b.clientLedgers - a.clientLedgers).toFixed(2) });
const same = (d, exp) => Object.keys(d).every((k) => eq(d[k], exp[k] || 0));
const itemDb = (id) => { const [r] = rowsA(`select status, coalesce(resolved_date::text,'<null>'), coalesce(resolution_transaction_id::text,'<null>'), coalesce(allocation_credit_transaction_id::text,'<null>') from T.unidentified_deposits where id='${id}'`, true); return { status: r[0], resolvedDate: r[1], resolutionTx: r[2], creditTx: r[3] }; };
const txDb = (id) => { const [r] = rowsA(`select transaction_type, status, transaction_date, ledger, ledger_effect, cashbook_effect, posted_at is not null, coalesce(customer_id::text,'<null>'), coalesce(project_id::text,'<null>'), coalesce(creditor_account_id::text,'<null>'), coalesce(approved_by::text,'<null>') from T.trust_transactions where id='${id}'`, true); return { type: r[0], status: r[1], date: r[2], ledger: r[3], ledgerEffect: Number(r[4]), cashbookEffect: Number(r[5]), posted: r[6] === "t", customer: r[7], project: r[8], creditor: r[9], approvedBy: r[10] }; };

async function itemsPage(who, label) {
  const p = S[who].pg;
  await p.bringToFront();
  await p.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
  await p.getByTestId("other-creditors-page").waitFor({ timeout: 60_000 });
  await p.getByTestId("unidentified-deposits-section").waitFor({ timeout: 30_000 });
  await p.waitForTimeout(500);
  const files = await snap(p, `${label}-other-creditors-${who}`);
  await crop(p.getByTestId("unidentified-deposits-section"), `${label}-unidentified-crop`);
  sql(`${label}: OBS items`, itemSql(`d.trust_account_id='${ids.account}'`));
  return { files };
}
async function txRow(who, txId) {
  const p = S[who].pg;
  await p.bringToFront();
  await p.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
  await p.getByTestId("transactions-table").waitFor({ timeout: 60_000 });
  const row = p.getByTestId(`transaction-row-${txId}`);
  await row.waitFor({ timeout: 30_000 });
  return row;
}

// ── h: Transactions table measure (probe-clip method + Approve/Reject + badge line count + overlap) ──
async function measureTx(p) {
  return p.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const t = document.querySelector("[data-testid='transactions-table']");
    const wrap = t.parentElement;
    let clipRight = vw, clipLeft = 0, clipBy = "viewport";
    for (let x = t.parentElement; x; x = x.parentElement) {
      const cs = getComputedStyle(x);
      if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 40)}`; break; }
    }
    const inClip = (r) => r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5;
    const cellInfo = (c) => {
      const r = c.getBoundingClientRect();
      const kids = [...c.querySelectorAll("*")].map((k) => k.getBoundingClientRect()).filter((k) => k.width > 0);
      return { text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 140), left: Math.round(r.left), right: Math.round(r.right),
        inside: inClip(r) && kids.every(inClip), truncated: c.scrollWidth > c.clientWidth + 1 };
    };
    const lineCount = (el) => {
      const range = document.createRange(); range.selectNodeContents(el);
      const tops = new Set([...range.getClientRects()].filter((r) => r.width > 0).map((r) => Math.round(r.top)));
      return tops.size;
    };
    const header = [...t.querySelectorAll("thead th")].map((h) => ({ ...cellInfo(h), width: Math.round(h.getBoundingClientRect().width) }));
    const rowEls = [...t.querySelectorAll("tbody tr")];
    const rows = rowEls.map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
    const typeCells = rowEls.map((tr) => {
      const id = (tr.getAttribute("data-testid") || "").replace("transaction-row-", "");
      const td = tr.querySelectorAll("td")[2];
      const badge = td.querySelector("[data-testid^='transaction-type-']");
      const desc = td.querySelector("[data-testid^='transaction-description-']");
      const br = badge ? badge.getBoundingClientRect() : null;
      const dr = desc ? desc.getBoundingClientRect() : null;
      const cr = td.getBoundingClientRect();
      const nextTd = tr.querySelectorAll("td")[3].getBoundingClientRect();
      const approve = tr.querySelector("[data-testid='approve-button']");
      const reject = tr.querySelector("[data-testid='reject-button']");
      const btn = (b) => { if (!b) return null; const r = b.getBoundingClientRect(); return { left: Math.round(r.left), right: Math.round(r.right), fullyVisible: inClip(r) && r.right <= vw && r.width > 0 }; };
      const status = tr.querySelectorAll("td")[4].innerText.replace(/\s+/g, " ").trim();
      return { id, label: badge ? badge.innerText.replace(/\s+/g, " ").trim() : null, hasTestid: !!badge,
        badgeWhiteSpace: badge ? getComputedStyle(badge).whiteSpace : null, badgeFlexShrink: badge ? getComputedStyle(badge).flexShrink : null,
        badgeW: br ? Math.round(br.width) : null, badgeH: br ? Math.round(br.height) : null, badgeLines: badge ? lineCount(badge) : null,
        badgeInsideCell: br ? br.left >= cr.left - 0.5 && br.right <= cr.right + 0.5 : null,
        badgeClearOfNextCell: br ? br.right <= nextTd.left + 0.5 : null,
        descLines: desc ? lineCount(desc) : null, descBelowBadge: br && dr ? dr.top >= br.bottom - 0.5 : null,
        cellW: Math.round(cr.width), status, approve: btn(approve), reject: btn(reject) };
    });
    const tr = t.getBoundingClientRect();
    const outside = rows.flat().filter((c) => !c.inside).length + header.filter((h) => !h.inside).length;
    const pending = typeCells.filter((c) => c.approve || c.reject);
    return { vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), wrapperWidth: wrap.clientWidth, wrapperScrollWidth: wrap.scrollWidth,
      tableLeft: Math.round(tr.left), tableRight: Math.round(tr.right), tableWidth: Math.round(tr.width),
      actionsHeader: header[5] ? { left: header[5].left, right: header[5].right } : null,
      columns: header.map((h) => `${h.text}:${h.width}`), cellsOutside: outside, rowCount: rows.length, header, rows, typeCells,
      pendingRows: pending.length, pendingBothButtonsVisible: pending.length > 0 && pending.every((c) => c.approve && c.reject && c.approve.fullyVisible && c.reject.fullyVisible),
      noHorizontalOverflow: wrap.scrollWidth <= wrap.clientWidth + 1, noneTruncated: rows.every((r) => r.every((c) => !c.truncated)) };
  });
}
async function measureItems(p) {
  return p.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    function measure(testid) {
      const el = document.querySelector(`[data-testid='${testid}']`);
      if (!el) return { testid, missing: true };
      const t = el.tagName === "TABLE" ? el : el.querySelector("table");
      if (!t) return { testid, missing: true };
      let clipRight = vw, clipLeft = 0, clipBy = "viewport";
      for (let x = t.parentElement; x; x = x.parentElement) {
        const cs = getComputedStyle(x);
        if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 40)}`; break; }
      }
      const inClip = (r) => r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5;
      const cellInfo = (c) => { const r = c.getBoundingClientRect(); const kids = [...c.querySelectorAll("*")].map((k) => k.getBoundingClientRect()).filter((k) => k.width > 0);
        return { text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 120), left: Math.round(r.left), right: Math.round(r.right), inside: inClip(r) && kids.every(inClip), truncated: c.scrollWidth > c.clientWidth + 1 }; };
      const header = [...t.querySelectorAll("thead th")].map(cellInfo);
      const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
      const tr = t.getBoundingClientRect();
      return { testid, vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), tableLeft: Math.round(tr.left), tableRight: Math.round(tr.right), tableWidth: Math.round(tr.width),
        wrapperWidth: t.parentElement.clientWidth, wrapperScrollWidth: t.parentElement.scrollWidth, rowCount: rows.length,
        cellsOutside: rows.flat().filter((c) => !c.inside).length + header.filter((h) => !h.inside).length, header, rows,
        noneTruncated: header.every((h) => !h.truncated) && rows.every((r) => r.every((c) => !c.truncated)),
        noHorizontalOverflow: t.parentElement.scrollWidth <= t.parentElement.clientWidth + 1 };
    }
    return { unidentified: measure("unidentified-deposits-table"), fund: measure("fund-ledger-table") };
  });
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
  ids.bob = val(`select id from T.members where email like 'bob%' limit 1`);
  sql("baseline: trust transactions", rowSql("true"));
  sql("baseline: statements / recons / items", `select (select count(*) from T.bank_statements) statements, (select count(*) from T.trust_reconciliations) recons, (select count(*) from T.unidentified_deposits) items`);

  const LINES = [
    { k: "A", date: addDays(sastToday, -7), desc: `DEPOSIT UNKNOWN REF A${RUN}`, ref: `UNK-A/RG647D/${RUN}`, amount: 4321.09 },
    { k: "B", date: addDays(sastToday, -5), desc: `DEPOSIT UNKNOWN REF B${RUN}`, ref: `UNK-B/RG647D/${RUN}`, amount: 1234.56 },
    { k: "C", date: addDays(sastToday, -4), desc: `DEPOSIT UNKNOWN REF C${RUN}`, ref: `UNK-C/RG647D/${RUN}`, amount: 777.0 },
    { k: "D", date: addDays(sastToday, -3), desc: `DEPOSIT UNKNOWN REF D${RUN}`, ref: `UNK-D/RG647D/${RUN}`, amount: 2500.25 },
  ];
  const L = Object.fromEntries(LINES.map((l) => [l.k, l]));
  const D = (result.dates = { today: sastToday, periodEnd: LINES.map((l) => l.date).sort().slice(-1)[0], allocationDateA: addDays(L.A.date, 4) });
  result.lines = LINES;
  let bal = 0;
  const csv = [`Standard Bank Trust Account - REGATE 647D ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of LINES) { bal = +(bal + l.amount).toFixed(2); csv.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
  const csvName = `statement-RG647D-${RUN}.csv`;
  const csvFile = `${OUT}/${csvName}`;
  fs.writeFileSync(csvFile, csv.join("\n") + "\n");

  await newSession("alice", "user_e2e_alice");
  await newSession("bob", "user_e2e_bob");
  result.steps.baseline = { books: books("baseline"), txCount: txCount() };
  save();

  // ── prep: wizard (Alice) — upload, auto-match, review; record A–D as Unidentified deposit ──
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
  for (const l of LINES) {
    await wiz.getByTestId(`bank-line-${l.lineId}`).click();
    await wiz.getByTestId(`record-btn-${l.lineId}`).click();
    const dlg = wiz.getByTestId("record-from-statement-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await wiz.waitForTimeout(300);
    await dlg.locator("button[role='radio'][value='UNIDENTIFIED_DEPOSIT']").click();
    await dlg.getByTestId("record-notes-input").fill(`RE-GATE 647D ${RUN} unknown credit ${l.k}`);
    await dlg.getByTestId("record-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await wiz.getByTestId(`recorded-badge-${l.lineId}`).waitFor({ timeout: 30_000 });
    l.badge = norm(await wiz.getByTestId(`recorded-badge-${l.lineId}`).innerText());
    await wiz.waitForTimeout(800);
  }
  prep.files = await snap(wiz, "prep-wizard-four-lines-recorded", [1440]);
  for (const l of LINES) {
    const [it] = rowsA(`select id from T.unidentified_deposits where bank_statement_line_id='${l.lineId}'`, true);
    l.itemId = it[0];
  }
  prep.checks = { fourRecorded: LINES.every((l) => l.badge === "Recorded: Unidentified deposit"), fourItems: LINES.every((l) => !!l.itemId) };
  prep.books = books("prep");
  prep.page = await itemsPage("alice", "prep-held");
  save();

  // ── allocation request (Alice) via the dialog ──
  async function requestAllocation(l, date) {
    const p = S.alice.pg;
    await itemsPage("alice", `alloc-${l.k}-before`);
    await p.getByTestId(`allocate-btn-${l.itemId}`).click();
    const dlg = p.getByTestId("allocate-unidentified-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await dlg.getByTestId("allocate-customer-trigger").click();
    await p.getByPlaceholder("Search clients...").fill("Moroka");
    await p.getByRole("option", { name: /Moroka Family Trust/ }).first().click();
    const mt = dlg.getByTestId("allocate-matter-trigger");
    for (let i = 0; i < 40 && ((await mt.isDisabled()) || /Loading/.test(await mt.innerText())); i++) await p.waitForTimeout(250);
    await mt.click();
    await p.getByRole("option", { name: /Estate Late Peter Moroka/ }).first().click();
    if (date) await dlg.getByTestId("allocate-date").fill(date);
    await dlg.getByTestId("allocate-notes").fill(`RE-GATE 647D ${RUN}: identified (${l.k})`);
    const cnt = txCount();
    await dlg.getByTestId("allocate-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    const txId = await waitSql(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${l.itemId}'`, (v) => !!v);
    return { txId, rowsAdded: txCount() - cnt, tx: txDb(txId) };
  }
  async function requestFund(l) {
    const p = S.alice.pg;
    await itemsPage("alice", `fund-${l.k}-before`);
    await p.getByTestId(`pay-to-fund-btn-${l.itemId}`).click();
    const dlg = p.getByTestId("pay-to-fund-dialog");
    await dlg.waitFor({ timeout: 15_000 });
    await dlg.getByTestId("pay-to-fund-reference").fill(`LPFF-${l.k}/RG647D/${RUN}`);
    await dlg.getByTestId("pay-to-fund-notes").fill(`RE-GATE 647D ${RUN}: unclaimed, pay over (${l.k})`);
    const cnt = txCount();
    await dlg.getByTestId("pay-to-fund-submit").click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    const txId = await waitSql(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${l.itemId}'`, (v) => !!v);
    return { txId, rowsAdded: txCount() - cnt, tx: txDb(txId) };
  }
  async function bobApprove(txId, label) {
    const row = await txRow("bob", txId);
    await row.getByTestId("approve-button").click();
    await waitSql(`select status from T.trust_transactions where id='${txId}'`, (v) => v === "APPROVED");
    await S.bob.pg.waitForTimeout(1500);
    return { toast: await S.bob.pg.locator("[data-sonner-toast]").first().innerText({ timeout: 3000 }).catch(() => null), files: await snap(S.bob.pg, `${label}-bob-approved`) };
  }

  // ── A: smoke — allocation requested by Alice, approved by Bob ──
  const A = (result.steps.A_smoke = {});
  A.before = books("A: before");
  A.req = await requestAllocation(L.A, D.allocationDateA);
  A.afterRequest = books("A: after request");
  A.bob = await bobApprove(A.req.txId, "A");
  ids.allocInA = await waitSql(`select coalesce(allocation_credit_transaction_id::text,'') from T.unidentified_deposits where id='${L.A.itemId}'`, (v) => !!v);
  A.out = txDb(A.req.txId);
  A.in = txDb(ids.allocInA);
  A.item = itemDb(L.A.itemId);
  sql("A: rows naming item A", rowSql(`t.unidentified_deposit_id='${L.A.itemId}'`));
  A.after = books("A: after Bob approved");
  A.delta = delta(A.before, A.after);
  A.position = (await api("alice", `/api/dashboard/trust-position?accountId=${ids.account}`, "A-trust-position")).body;
  const LA = L.A.amount;
  A.checks = {
    requestCreatedOnePendingRow: A.req.rowsAdded === 1 && A.req.tx.type === "UNIDENT_ALLOC_OUT" && A.req.tx.status === "AWAITING_APPROVAL" && !A.req.tx.posted,
    nothingMovedOnRequest: same(delta(A.before, A.afterRequest), {}),
    outPosted: A.out.status === "APPROVED" && A.out.posted && A.out.approvedBy === ids.bob && eq(A.out.ledgerEffect, -LA) && eq(A.out.cashbookEffect, 0) && A.out.creditor === ids.udAccount,
    inPosted: A.in.type === "UNIDENT_ALLOC_IN" && A.in.status === "APPROVED" && A.in.posted && A.in.ledger === "CLIENT" && A.in.customer === ids.moroka && A.in.project === ids.morokaMatter && eq(A.in.ledgerEffect, LA) && eq(A.in.cashbookEffect, 0) && A.in.date === D.allocationDateA,
    itemAllocated: A.item.status === "ALLOCATED" && A.item.creditTx === ids.allocInA && A.item.resolvedDate === D.allocationDateA,
    movesUnidentifiedMinusClientPlusCashbookZero: same(A.delta, { unidentified: -LA, moroka: LA, clientLedgers: LA }),
    booksBalance: A.after.balanced,
    positionDifferenceZero: !!A.position && eq(A.position.difference, 0),
  };
  save();

  // ── B: payment to the Fund requested by Alice, approved by Bob ──
  const B = (result.steps.B = {});
  B.before = books("B: before");
  B.req = await requestFund(L.B);
  B.bob = await bobApprove(B.req.txId, "B");
  B.tx = txDb(B.req.txId);
  B.item = itemDb(L.B.itemId);
  B.after = books("B: after Bob approved");
  B.delta = delta(B.before, B.after);
  const LB = L.B.amount;
  B.checks = { approvedPosted: B.tx.type === "UNIDENT_FUND_PAY" && B.tx.status === "APPROVED" && B.tx.posted && eq(B.tx.ledgerEffect, -LB) && eq(B.tx.cashbookEffect, -LB),
    itemPaid: B.item.status === "PAID_TO_FUND", moves: same(B.delta, { cashbook: -LB, unidentified: -LB }), booksBalance: B.after.balanced };
  save();

  // ── C: allocation left pending; D: payment to the Fund left pending ──
  const C = (result.steps.C_pending = {});
  C.req = await requestAllocation(L.C, null);
  const Dp = (result.steps.D_pending = {});
  Dp.req = await requestFund(L.D);
  ids.pendingAlloc = C.req.txId; ids.pendingFund = Dp.req.txId;
  sql("C/D: pending rows", rowSql(`t.id in ('${C.req.txId}','${Dp.req.txId}')`));
  result.steps.pendingBooks = books("after C/D requests");
  result.steps.pendingChecks = {
    cPending: C.req.tx.type === "UNIDENT_ALLOC_OUT" && C.req.tx.status === "AWAITING_APPROVAL" && !C.req.tx.posted,
    dPending: Dp.req.tx.type === "UNIDENT_FUND_PAY" && Dp.req.tx.status === "AWAITING_APPROVAL" && !Dp.req.tx.posted,
    nothingMoved: same(delta(B.after, result.steps.pendingBooks), {}),
  };
  sql("final: all trust rows", rowSql("true"));
  save();

  // ── h: Transactions page clip probe (Bob = approver, and Alice) at 1280 / 1440 / 1920 ──
  const h = (result.steps.h = { transactions: {}, items: {} });
  for (const who of ["bob", "alice"]) {
    const p = S[who].pg;
    await p.bringToFront();
    await p.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: "load" });
    await p.getByTestId("transactions-table").waitFor({ timeout: 60_000 });
    h.totalText = norm(await p.locator("text=/transactions? found/").first().innerText().catch(() => ""));
    h.transactions[who] = {};
    for (const width of [1280, 1440, 1920]) {
      await p.setViewportSize({ width, height: 1100 });
      await p.waitForTimeout(800);
      const m = await measureTx(p);
      h.transactions[who][width] = m;
      if (who === "bob" && width !== 1920) {
        await p.screenshot({ path: `${OUT}/h-transactions-bob-${width}.png`, fullPage: true });
        await p.getByTestId("transactions-table").locator("..").screenshot({ path: `${OUT}/h-transactions-table-bob-${width}-crop.png` });
        for (const [k, id] of [["pending-fund", ids.pendingFund], ["pending-alloc", ids.pendingAlloc], ["alloc-in", ids.allocInA]]) {
          await p.getByTestId(`transaction-row-${id}`).screenshot({ path: `${OUT}/h-row-${k}-bob-${width}-crop.png` });
          await p.getByTestId(`transaction-row-${id}`).locator("td").nth(2).screenshot({ path: `${OUT}/h-typecell-${k}-bob-${width}-crop.png` });
        }
      }
      if (who === "bob" && width === 1920) await p.screenshot({ path: `${OUT}/h-transactions-bob-1920.png`, fullPage: true });
    }
    await p.setViewportSize({ width: 1440, height: 1100 });
  }
  h.sqlTxCount = txCount();
  // items tables (Alice) at 1280 / 1440
  {
    const p = S.alice.pg;
    await p.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
    await p.getByTestId("unidentified-deposit-row").first().waitFor({ timeout: 60_000 });
    for (const width of [1280, 1440]) {
      await p.setViewportSize({ width, height: 1100 });
      await p.waitForTimeout(800);
      h.items[width] = await measureItems(p);
      await p.screenshot({ path: `${OUT}/h-other-creditors-alice-${width}.png`, fullPage: true });
      await p.getByTestId("unidentified-deposits-section").screenshot({ path: `${OUT}/h-unidentified-section-${width}-crop.png` });
    }
    await p.setViewportSize({ width: 1440, height: 1100 });
  }
  const txOk = (m) => m.cellsOutside === 0 && m.noHorizontalOverflow && m.pendingRows >= 2 && m.pendingBothButtonsVisible && m.typeCells.every((c) => c.hasTestid && c.badgeInsideCell && c.badgeClearOfNextCell && (c.descBelowBadge !== false));
  const itOk = (m) => !m.unidentified.missing && m.unidentified.cellsOutside === 0 && m.unidentified.noHorizontalOverflow && (m.fund.missing || (m.fund.cellsOutside === 0 && m.fund.noHorizontalOverflow));
  h.verdict = {
    transactions: Object.fromEntries(["bob", "alice"].flatMap((w) => [1280, 1440, 1920].map((x) => [`${w}-${x}`, txOk(h.transactions[w][x]) ? "OK" : "CLIPPED"]))),
    items: Object.fromEntries([1280, 1440].map((x) => [x, itOk(h.items[x]) ? "OK" : "CLIPPED"])),
  };
  h.PASS = ["bob", "alice"].every((w) => [1280, 1440].every((x) => txOk(h.transactions[w][x]))) && [1280, 1440].every((x) => itOk(h.items[x]));
  // summary table of labels → line count per width (Bob)
  h.labels = {};
  for (const width of [1280, 1440, 1920]) for (const c of h.transactions.bob[width].typeCells) {
    h.labels[c.label] = h.labels[c.label] || {};
    h.labels[c.label][width] = Math.max(h.labels[c.label][width] || 0, c.badgeLines);
  }
  result.verdict = { prep: prep.checks, A_smoke: A.checks, B: B.checks, pending: result.steps.pendingChecks, hPASS: h.PASS, h: h.verdict, labels: h.labels,
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length };
  result.finishedAt = new Date().toISOString();
  save();
  console.log(JSON.stringify(result.verdict, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("REGATE OBS FAILED:", err);
  try { await snap(S.alice.pg, "zz-fatal-alice", [1440]); } catch {}
  try { await snap(S.alice.wiz, "zz-fatal-wizard", [1440]); } catch {}
  try { await snap(S.bob.pg, "zz-fatal-bob", [1440]); } catch {}
  result.fatal = String((err && err.stack) || err);
  result.finishedAt = new Date().toISOString();
  save();
  process.exit(1);
});
