// 649C OBS — trust integrity card + firm-wide tiles, reconciliation completion gate G1–G4, D-14.
// Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081), mock-login UI.
// State changes go through the firm UI, except where the UI has no path (each one is logged in
// result.apiWrites with its reason): a second user acting on an open reconciliation's statement
// line (the wizard holds its reconciliation in page state; no screen reopens it) and the D-14
// unmatch on a reconciled statement (no screen shows a completed reconciliation's lines).
// psql is read-only (SELECT). API reads use the context's mock-auth-token against :8081.
// Usage: node obs.cjs a|b|c|d|e      (b → c → d → e share obs/state.json)
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const W = path.resolve(__dirname, "../../../../..");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] }));

const FIRM = "http://localhost:3001", BACKEND = "http://localhost:8081", BASE = `${FIRM}/org/e2e-test-org`;
const OUT = path.join(__dirname, "obs");
fs.mkdirSync(OUT, { recursive: true });
const PHASE = process.argv[2];
const STATE = path.join(OUT, "state.json");
const state = fs.existsSync(STATE) ? JSON.parse(fs.readFileSync(STATE, "utf8")) : {};
const R = { phase: PHASE, startedAt: new Date().toISOString(), apiWrites: [], consoleErrors: [], pageErrors: [], checks: {} };
const saveAll = () => { fs.writeFileSync(STATE, JSON.stringify(state, null, 2)); fs.writeFileSync(path.join(OUT, `result-${PHASE}.json`), JSON.stringify(R, null, 2)); };
const TODAY = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
const day = (n, from = TODAY) => { const d = new Date(`${from}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const CODES = /\b(BANK_DIFFERENCE|BOOKS_OUT_OF_BALANCE|INTER_ACCOUNT_NOT_ZERO|UNIDENTIFIED_ITEMS_MISMATCH|INVESTMENT_BALANCE_MISMATCH|UNRESOLVED_STATEMENT_LINES|FUND_LEDGER_IN_DEBIT|CLIENT_LEDGER_IN_DEBIT|UNIDENTIFIED_OVER_ONE_YEAR|CHARGES_TO_REFUND|BLOCKING|WARNING|CREDITOR_ACCOUNT|BANK_STATEMENT|TRUST_ACCOUNT)\b/g;

// ── read-only SQL ──
const SCHEMA = execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-XAt", "-c",
  "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"], { encoding: "utf8" }).trim();
const SQL_OUT = path.join(OUT, `sql-${PHASE}.txt`);
fs.writeFileSync(SQL_OUT, `-- 649C OBS phase ${PHASE} read-only SQL (schema ${SCHEMA}) ${R.startedAt}\n`);
const q = (sql, label) => {
  const s = sql.replace(/\bT\./g, `${SCHEMA}.`);
  if (!/^\s*(select|with)\b/i.test(s)) throw new Error("read-only SQL only");
  const out = execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", s], { encoding: "utf8" });
  if (label) fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${s}\n${out}`);
  return out;
};
const qa = (sql) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-XAt", "-F", "|", "-c", sql.replace(/\bT\./g, `${SCHEMA}.`)], { encoding: "utf8" }).trim();
const POSITION_SQL = (asAt) => `select coalesce(t.trust_account_id::text,'FIRM') account, sum(t.cashbook_effect) cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) investment_creditors,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) inter_account, sum(t.ledger_effect) creditor_total
  from T.trust_transactions t left join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id
  where t.posted_at is not null and t.transaction_date <= date '${asAt}' group by rollup(t.trust_account_id) order by 1`;
const firmSqlRow = () => { const l = qa(POSITION_SQL(TODAY)).split("\n").find((x) => x.startsWith("FIRM|")).split("|").map(Number); return { cashbook: l[1], clientLedgers: l[2], investmentCreditors: l[3], fundLedger: l[4], unidentified: l[5], otherNonClient: l[6], interAccount: l[7], creditorTotal: l[8] }; };
const eq = (a, b) => a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
const money = (s) => { const m = String(s ?? "").match(/(-|−)?\s*R[\s  ]?(-|−)?[\d\s  ]*\d,\d{2}/); if (!m) return null; const n = Number(m[0].replace(/[^\d,]/g, "").replace(",", ".")); return /[-−]/.test(m[0]) ? -n : n; };

// ── browser ──
let browser;
async function login(user = "user_e2e_alice", width = 1280) {
  const ctx = await browser.newContext({ viewport: { width, height: 1000 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ user, url: p.url(), text: m.text().slice(0, 300) }); });
  p.on("pageerror", (e) => R.pageErrors.push({ user, url: p.url(), text: String(e).slice(0, 300) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption(user);
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  return { ctx, p };
}
async function api(ctx, method, url, data, save) {
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token")?.value;
  const res = await ctx.request.fetch(`${BACKEND}${url}`, { method, data, headers: { Authorization: `Bearer ${token}` } });
  const body = await res.json().catch(() => null);
  const r = { method, url, status: res.status(), body };
  if (save) fs.writeFileSync(path.join(OUT, `api-${save}.json`), JSON.stringify(r, null, 2));
  return r;
}
async function snap(p, name, el) {
  await p.waitForTimeout(500);
  const f = path.join(OUT, `${PHASE}-${name}.png`);
  if (el) await el.screenshot({ path: f }); else await p.screenshot({ path: f, fullPage: true });
  if (!el) fs.writeFileSync(f.replace(/\.png$/, ".txt"), await p.locator("body").innerText());
  return path.basename(f);
}
/** Clip probe: every element with its own text must lie inside `sel` and the viewport, and not be cut by its own overflow. */
const clip = (p, sel) => p.evaluate((sel) => {
  const root = document.querySelector(sel);
  if (!root) return { missing: sel };
  const B = root.getBoundingClientRect(), vw = document.documentElement.clientWidth, bad = [];
  let n = 0;
  for (const el of [root, ...root.querySelectorAll("*")]) {
    if (![...el.childNodes].some((c) => c.nodeType === 3 && c.textContent.trim())) continue;
    const r = el.getBoundingClientRect(), cs = getComputedStyle(el);
    if (!r.width || (cs.position === "absolute" && r.width <= 1)) continue; // sr-only text is not drawn
    n++;
    const cut = cs.overflowX !== "visible" && el.scrollWidth > el.clientWidth + 1;
    if (r.left < B.left - 0.5 || r.right > B.right + 0.5 || r.right > vw + 0.5 || cut) bad.push({ text: el.textContent.trim().slice(0, 80), left: r.left, right: r.right, cut });
  }
  return { sel, root: [B.left, B.right], vw, textElements: n, bad };
}, sel);
const rawCodes = async (p) => [...new Set(((await p.locator("main").innerText()).match(CODES) || []))];

// ── trust overview: the card, the tiles, API + SQL cross-check ──
async function overview(tag, widths = [1280]) {
  const o = { tag };
  for (const w of widths) {
    const { ctx, p } = await login("user_e2e_alice", w);
    await p.goto(`${BASE}/trust-accounting`, { waitUntil: "load" });
    await p.getByTestId("trust-integrity-card").waitFor({ timeout: 60_000 });
    o[`shot${w}`] = await snap(p, `overview-${w}`);
    o[`card${w}`] = await snap(p, `card-${w}`, p.getByTestId("trust-integrity-card"));
    o[`tiles${w}`] = await snap(p, `tiles-${w}`, p.getByTestId("integrity-tiles"));
    o[`clip${w}`] = [await clip(p, '[data-testid="trust-integrity-card"]'), await clip(p, '[data-testid="integrity-tiles"]'), await clip(p, '[data-testid="summary-cards"]')];
    if (w !== 1280) { await ctx.close(); continue; }
    o.rawCodes = await rawCodes(p);
    o.frozenTiles = (await p.getByTestId("summary-cards").innerText()).replace(/\s+/g, " ");
    o.tiles = Object.fromEntries(await Promise.all(["Cashbook", "Client ledgers", "Other trust creditors", "Invested"].map(async (label) => {
      const t = await p.getByTestId("integrity-tiles").locator("> *").filter({ hasText: label }).first().innerText();
      return [label, { text: t.replace(/\s+/g, " "), value: money(t) }];
    })));
    o.cardText = (await p.getByTestId("trust-integrity-card").innerText()).replace(/\s+/g, " ");
    o.flagsUi = await p.locator('[data-testid^="integrity-flag-"]').evaluateAll((els) => els.map((e) => ({
      testid: e.dataset.testid, text: e.innerText.replace(/\s+/g, " "), links: [...e.querySelectorAll("a")].map((a) => ({ text: a.innerText, href: a.getAttribute("href") })) })));
    const firm = await api(ctx, "GET", "/api/trust-integrity", null, `${tag}-trust-integrity`);
    const primary = qa("select id from T.trust_accounts where is_primary order by created_at limit 1") || state.general;
    o.position = (await api(ctx, "GET", `/api/dashboard/trust-position?accountId=${primary}`, null, `${tag}-trust-position`)).body;
    q(POSITION_SQL(TODAY), `${tag}: EQ-2 terms per account + FIRM (rollup), as at ${TODAY}`);
    const s = firmSqlRow(), b = firm.body;
    o.figures = { sql: s, api: b && { cashbook: b.cashbook, clientLedgers: b.clientLedgers, fundLedger: b.fundLedger, unidentifiedDeposits: b.unidentifiedDeposits, otherNonClient: b.otherNonClient, investmentCreditors: b.investmentCreditors, interAccount: b.interAccount, creditorTotal: b.creditorTotal, difference: b.difference, balanced: b.balanced } };
    o.apiFlags = b ? [...b.flags.map((f) => ({ account: "FIRM", ...f })), ...b.accounts.flatMap((a) => a.flags.map((f) => ({ account: a.trustAccountId, ...f })))] : [];
    const other = b.fundLedger + b.unidentifiedDeposits + b.otherNonClient;
    R.checks[`${tag}: tiles = API = SQL`] = eq(o.tiles.Cashbook.value, b.cashbook) && eq(b.cashbook, s.cashbook)
      && eq(o.tiles["Client ledgers"].value, b.clientLedgers) && eq(b.clientLedgers, s.clientLedgers)
      && eq(o.tiles["Other trust creditors"].value, other) && eq(other, s.fundLedger + s.unidentified + s.otherNonClient)
      && eq(o.tiles.Invested.value, b.investmentCreditors) && eq(b.investmentCreditors, s.investmentCreditors);
    R.checks[`${tag}: Cashbook = Client ledgers + Other + Invested (firm-wide, interAccount ${s.interAccount})`] = eq(s.cashbook, s.clientLedgers + s.fundLedger + s.unidentified + s.otherNonClient + s.investmentCreditors) && eq(s.interAccount, 0);
    const pos = o.position || {};
    R.checks[`${tag}: frozen four tiles (labels in order; Trust Balance + Pending Approvals = trust-position API)`] = /^Trust Balance .*Active Clients .*Pending Approvals .*Reconciliation /.test(o.frozenTiles)
      && eq(money(o.frozenTiles), pos.cashbookBalance) && new RegExp(`Pending Approvals ${pos.pendingApprovalCount} `).test(o.frozenTiles);
    R.checks[`${tag}: no raw enum code on page`] = o.rawCodes.length === 0;
    R.checks[`${tag}: every API flag rendered once`] = o.flagsUi.length === o.apiFlags.length && o.apiFlags.every((f) => o.flagsUi.some((u) => u.testid === `integrity-flag-${f.code}` && u.text.includes(f.message.replace(/\s+/g, " "))));
    R.checks[`${tag}: card balanced state = API`] = b.balanced ? /Books balance/.test(o.cardText) : /do not balance/.test(o.cardText);
    R.checks[`${tag}: no clipped text at ${w}`] = o[`clip${w}`].every((c) => !c.missing && c.bad.length === 0);
    await ctx.close();
  }
  for (const w of widths.filter((x) => x !== 1280)) R.checks[`${tag}: no clipped text at ${w}`] = o[`clip${w}`].every((c) => !c.missing && c.bad.length === 0);
  return o;
}

// ── the reconciliation wizard ──
function writeCsv(name, lines) {
  let bal = 0; const rows = [`Standard Bank Trust Account - OBS 649C ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of lines) { bal = +(bal + l.amount).toFixed(2); rows.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
  const f = path.join(OUT, `${name}-${RUN}.csv`); fs.writeFileSync(f, rows.join("\n") + "\n"); return f;
}
async function wizard(p, accountId, periodEnd, csv) {
  await p.goto(`${BASE}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await p.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await p.getByTestId("account-select").locator(`option[value="${accountId}"]`).waitFor({ state: "attached", timeout: 30_000 });
  await p.getByTestId("account-select").selectOption(accountId);
  await p.getByTestId("period-end-input").fill(periodEnd);
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("file-input").setInputFiles(csv);
  await p.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("auto-match-btn").click();
  await p.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  const autoMatch = (await p.getByTestId("auto-match-result").innerText()).replace(/\s+/g, " ");
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  const statement = qa(`select id from T.bank_statements where file_name='${path.basename(csv)}'`);
  const lines = qa(`select line_number, id, match_status from T.bank_statement_lines where bank_statement_id='${statement}' order by line_number`).split("\n").map((l) => l.split("|"));
  return { statement, recon: qa(`select id from T.trust_reconciliations where bank_statement_id='${statement}'`), autoMatch, lines: lines.map(([n, id, s]) => ({ n: +n, id, s })) };
}
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(1500); };
async function exclude(p, id) { await p.getByTestId(`bank-line-${id}`).click(); await p.getByTestId(`exclude-btn-${id}`).click(); await settle(p); }
async function record(p, id, classification) {
  await p.getByTestId(`bank-line-${id}`).click();
  await p.getByTestId(`record-btn-${id}`).click();
  const dlg = p.getByTestId("record-from-statement-dialog");
  await dlg.waitFor();
  await dlg.locator(`button[role='radio'][value='${classification}']`).click();
  if (classification === "BANK_CHARGE") { await dlg.locator("#charge-recoverable-no").click(); await dlg.getByTestId("charge-vat-input").fill("0"); }
  await dlg.getByTestId("record-submit").click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await settle(p);
}
const summary = async (p) => ({ text: (await p.getByTestId("balance-summary").innerText()).replace(/\s+/g, " "), completeEnabled: await p.getByTestId("complete-reconciliation-btn").isEnabled() });
const reconSql = (id, label) => q(`select r.id, r.status, r.period_end, r.bank_balance, r.outstanding_deposits, r.outstanding_payments, r.adjusted_bank_balance, r.cashbook_balance, r.client_ledger_total, r.creditor_total, r.difference, r.is_balanced, r.integrity_flags, r.completed_at, s.status statement_status, s.closing_balance, s.matched_count
  from T.trust_reconciliations r join T.bank_statements s on s.id=r.bank_statement_id where r.id='${id}'`, label);
const auditSql = (id, label) => q(`select event_type, details, occurred_at from T.audit_events where entity_id='${id}' order by occurred_at`, label);

// ── phases ──
const phases = {
  async a() { R.overview = await overview("a", [1280, 1440]); },

  // §16.5.3 on a small statement: refused once with G1 + G4, then re-resolved and completed on screen.
  // Context 2 unmatches the PAYMENT line: its transaction is APPROVED, so the refreshed pane can list it
  // (the pane lists APPROVED only — N-10 — and the deposit is RECORDED, so a deposit line could not be
  // re-matched on screen whatever the wizard re-reads).
  async b() {
    const general = (state.general = qa("select id from T.trust_accounts where account_type='GENERAL' order by created_at limit 1"));
    const tx = (type, amount) => qa(`select id || '|' || reference from T.trust_transactions where trust_account_id='${general}' and transaction_type='${type}' and amount=${amount} and posted_at is not null and bank_statement_line_id is null order by created_at limit 1`).split("|");
    const [depTx, depRef] = tx("DEPOSIT", 5000), [payTx, payRef] = tx("PAYMENT", 1250);
    q(`select id, transaction_type, status, reference, amount, transaction_date from T.trust_transactions where id in ('${depTx}','${payTx}')`, "b: the two seeded transactions the statement matches");
    const L = [
      { date: day(-1), desc: "EFT CREDIT SIPHO NDLOVU", ref: depRef, amount: 5000 },
      { date: day(-1), desc: "EFT DEBIT DEEDS OFFICE", ref: payRef, amount: -1250 },
      { date: day(-1), desc: "EFT CREDIT RETURNED ITEM", ref: `RTN/OBS649C/${RUN}`, amount: 999 },
      { date: day(-1), desc: "EFT CREDIT RETURNED ITEM REVERSAL", ref: `RTN/OBS649C/${RUN}`, amount: -999 },
    ];
    const csv = writeCsv("b-statement", L);
    const c1 = await login("user_e2e_alice");
    const w = (R.wizard = await wizard(c1.p, general, TODAY, csv));
    Object.assign(state, { b: { ...w, csv: path.basename(csv), depositTx: depTx, paymentTx: payTx } });
    R.afterAutoMatch = await snap(c1.p, "01-review-after-auto-match");
    for (const l of w.lines.filter((x) => x.s === "UNMATCHED")) await exclude(c1.p, l.id);
    R.ready = await summary(c1.p);
    R.readyProgress = await c1.p.getByTestId("match-progress").innerText();
    R.readyShot = await snap(c1.p, "02-all-resolved-complete-enabled");
    reconSql(w.recon, "b: reconciliation when every line is resolved");
    // context 2 (Bob): unmatch line 2 — no screen opens another user's reconciliation, so the firm API
    const c2 = await login("user_e2e_bob");
    const line = w.lines[1].id;
    R.apiWrites.push({ why: "context 2: the wizard cannot reopen an existing reconciliation, so a second user can reach its line only through the firm API the wizard calls", ...(await api(c2.ctx, "POST", `/api/bank-statement-lines/${line}/unmatch`, undefined, "b-ctx2-unmatch")) });
    q(`select line_number, match_status, trust_transaction_id from T.bank_statement_lines where bank_statement_id='${w.statement}' order by line_number`, "b: lines after context-2 unmatch");
    // context 1: Complete (still enabled on its stale state) → one refusal; the wizard re-reads lines + transactions
    R.beforeComplete = await summary(c1.p);
    await c1.p.getByTestId("complete-reconciliation-btn").click();
    await c1.p.getByTestId("reconciliation-error").waitFor({ timeout: 30_000 });
    await settle(c1.p);
    const banner = c1.p.getByTestId("reconciliation-error");
    R.refusal = { banners: await banner.count(), text: await banner.innerText(), title: await banner.getByTestId("reconciliation-error-title").innerText().catch(() => null),
      titleFirst: await banner.evaluate((b) => b.firstElementChild?.dataset.testid === "reconciliation-error-title"), after: await summary(c1.p),
      progress: await c1.p.getByTestId("match-progress").innerText(), lineShown: (await c1.p.getByTestId(`bank-line-${line}`).innerText()).replace(/\s+/g, " "),
      lineUnmatchBtn: await c1.p.getByTestId(`unmatch-btn-${line}`).count(), payTxListed: await c1.p.getByTestId(`transaction-${payTx}`).count(),
      depTxListed: await c1.p.getByTestId(`transaction-${depTx}`).count(), stillOnReview: await c1.p.getByTestId("reconciliation-split-pane").isVisible() };
    R.refusalShot = await snap(c1.p, "03-refused-g1-g4");
    await snap(c1.p, "03b-refusal-banner-crop", banner);
    R.refusalClip = await clip(c1.p, '[data-testid="reconciliation-error"]');
    reconSql(w.recon, "b: reconciliation after the refusal (wizard recalculated)");
    auditSql(w.recon, "b: audit events of the reconciliation after the refusal");
    R.completedEventsAfterRefusal = +qa(`select count(*) from T.audit_events where entity_id='${w.recon}' and event_type='trust_reconciliation.completed'`);
    // re-resolve in context 1 on screen: select the line → its actions → match it to its payment in the pane
    await c1.p.getByTestId(`bank-line-${line}`).click();
    R.lineActions = { exclude: await c1.p.getByTestId(`exclude-btn-${line}`).count(), record: await c1.p.getByTestId(`record-btn-${line}`).count(), match: await c1.p.getByTestId(`match-btn-${payTx}`).count() };
    R.lineSelectedShot = await snap(c1.p, "04-line-selected-actions");
    if (R.lineActions.match) { await c1.p.getByTestId(`match-btn-${payTx}`).click(); await settle(c1.p); R.reResolveVia = "UI match (context 1)"; }
    else {
      R.apiWrites.push({ why: "re-resolve: the pane does not offer a match for the line (last resort)", ...(await api(c2.ctx, "POST", `/api/bank-statement-lines/${line}/match`, { transactionId: payTx }, "b-ctx2-rematch")) });
      R.reResolveVia = "API match (context 2)";
    }
    R.beforeSecondComplete = { ...(await summary(c1.p)), progress: await c1.p.getByTestId("match-progress").innerText() };
    await snap(c1.p, "05-re-resolved-complete-enabled");
    await c1.p.getByTestId("complete-reconciliation-btn").click();
    await c1.p.getByTestId("step-complete").waitFor({ timeout: 30_000 });
    R.completeShot = await snap(c1.p, "06-completed");
    R.completeText = (await c1.p.getByTestId("step-complete").innerText()).replace(/\s+/g, " ");
    reconSql(w.recon, "b: reconciliation after completion");
    auditSql(w.recon, "b: audit events of the reconciliation after completion");
    q(`select id, status, closing_balance, matched_count from T.bank_statements where id='${w.statement}'`, "b: statement after completion");
    q(`select line_number, match_status, trust_transaction_id from T.bank_statement_lines where bank_statement_id='${w.statement}' order by line_number`, "b: lines after completion");
    R.reconApi = (await api(c1.ctx, "GET", `/api/trust-reconciliations/${w.recon}`, null, "b-reconciliation-after-complete")).body;
    const [st, ss, nEv, det] = qa(`select r.status, s.status, (select count(*) from T.audit_events a where a.entity_id=r.id and a.event_type='trust_reconciliation.completed'), (select details from T.audit_events a where a.entity_id=r.id and a.event_type='trust_reconciliation.completed' limit 1) from T.trust_reconciliations r join T.bank_statements s on s.id=r.bank_statement_id where r.id='${w.recon}'`).split("|");
    const d = JSON.parse(det || "{}");
    Object.assign(R.checks, {
      "b: all four lines resolved → Complete enabled, 4 / 4": R.ready.completeEnabled && /4 \/ 4 lines resolved/.test(R.readyProgress),
      "b: one refusal banner": R.refusal.banners === 1,
      "b: banner heading is the title 'Reconciliation does not balance' (O-OBS-2)": R.refusal.title === "Reconciliation does not balance" && R.refusal.titleFirst,
      "b: banner carries the G1 sentence and the G4 sentence": /Adjusted bank balance .* differs from the cashbook/.test(R.refusal.text) && /1 statement line is neither matched, recorded nor excluded\./.test(R.refusal.text),
      "b: figures refreshed after refusal (D-9): not balanced, Complete disabled": !R.refusal.after.completeEnabled && R.refusal.after.text !== R.beforeComplete.text,
      "b: pane re-read after refusal (F-OBS-1): line Unmatched, no Unmatch button, 3 / 4, payment listed": /Unmatched/.test(R.refusal.lineShown) && !/Auto-matched/.test(R.refusal.lineShown) && R.refusal.lineUnmatchBtn === 0 && /3 \/ 4 lines resolved/.test(R.refusal.progress) && R.refusal.payTxListed === 1,
      "b: the line offers its actions on screen (exclude, record, match)": R.lineActions.exclude === 1 && R.lineActions.record === 1 && R.lineActions.match === 1,
      "b: re-resolved through the UI → 4 / 4, Complete enabled": R.reResolveVia.startsWith("UI") && R.beforeSecondComplete.completeEnabled && /4 \/ 4 lines resolved/.test(R.beforeSecondComplete.progress),
      "b: refused attempt left no completed audit event": R.completedEventsAfterRefusal === 0,
      "b: COMPLETED + RECONCILED + one completed event": st === "COMPLETED" && ss === "RECONCILED" && nEv === "1",
      "b: completed event details carry creditor_total, difference, flags": "creditor_total" in d && "difference" in d && Array.isArray(d.flags),
      "b: no clipped text in the refusal banner": !R.refusalClip.missing && R.refusalClip.bad.length === 0,
    });
    state.b.completedDetails = d;
  },

  // D-14: unmatch on the reconciled statement
  async c() {
    const { p, ctx } = await login();
    await p.goto(`${BASE}/trust-accounting/reconciliation`, { waitUntil: "load" });
    await p.getByTestId(`reconciliation-row-${state.b.recon}`).waitFor({ timeout: 60_000 });
    R.listShot = await snap(p, "01-reconciliation-list");
    R.unmatchOffered = await p.getByRole("button", { name: /unmatch/i }).count();
    const line = state.b.lines[1].id;
    const before = qa(`select match_status, trust_transaction_id, (select matched_count from T.bank_statements where id='${state.b.statement}') from T.bank_statement_lines where id='${line}'`);
    q(`select id, line_number, match_status, trust_transaction_id from T.bank_statement_lines where id='${line}'`, "c: line 2 before the unmatch attempt");
    R.apiWrites.push({ why: "D-14: no screen shows a completed reconciliation's lines (the wizard ends at step 5; the list has no Unmatch), so the unmatch is sent once through the firm API", ...(await api(ctx, "POST", `/api/bank-statement-lines/${line}/unmatch`, undefined, "c-unmatch-reconciled")) });
    const after = qa(`select match_status, trust_transaction_id, (select matched_count from T.bank_statements where id='${state.b.statement}') from T.bank_statement_lines where id='${line}'`);
    q(`select id, line_number, match_status, trust_transaction_id from T.bank_statement_lines where id='${line}'`, "c: line 2 after the refused unmatch");
    q(`select event_type, entity_id, occurred_at from T.audit_events where entity_id='${line}' order by occurred_at`, "c: audit events of line 2");
    const res = R.apiWrites.at(-1);
    Object.assign(R.checks, { "c: no Unmatch on screen": R.unmatchOffered === 0, "c: refused 400 'Statement already reconciled'": res.status === 400 && /already reconciled/i.test(JSON.stringify(res.body)), "c: line + matched_count unchanged": before === after });
    R.before = before; R.after = after;
  },

  // warnings on a separate GENERAL trust account created through the UI
  async d() {
    const { p } = await login();
    const name = `OBS 649C Warnings ${RUN}`;
    await p.goto(`${BASE}/settings/fees?tab=trust-accounting`, { waitUntil: "load" }); // the add-account button lives here once an account exists
    await p.getByTestId("add-trust-account-button").first().click();
    const dlg = p.getByTestId("create-trust-account-dialog");
    await dlg.getByLabel("Account Name").fill(name);
    await dlg.getByLabel("Bank Name").fill("FNB");
    await dlg.getByLabel("Branch Code").fill("250655");
    await dlg.getByLabel("Account Number").fill(`649${Date.now().toString().slice(-7)}`);
    await dlg.getByLabel("Opened Date").fill("2025-01-01");
    const primaryBox = dlg.getByRole("checkbox", { name: /primary/i }); // defaults to checked: keep the existing primary account
    if ((await primaryBox.getAttribute("aria-checked")) === "true") await primaryBox.click();
    R.primaryUnchecked = (await primaryBox.getAttribute("aria-checked")) === "false";
    R.dialogShot = await snap(p, "01-create-account-dialog", dlg);
    await dlg.getByRole("button", { name: "Create Account" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    const acct = (state.warnAccount = qa(`select id from T.trust_accounts where account_name='${name}'`));
    q(`select id, account_name, account_type, investment_basis, is_primary, opened_date from T.trust_accounts where id='${acct}'`, "d: the new account");
    const L = [
      { date: "2025-10-10", desc: "DEPOSIT UNKNOWN REMITTER", ref: `UNK/OBS649C/${RUN}`, amount: 1500 },
      { date: "2025-10-20", desc: "MONTHLY SERVICE FEE", ref: `SVC/OBS649C/${RUN}`, amount: -85 },
      { date: "2025-10-25", desc: "EFT CREDIT NOT YET IDENTIFIED", ref: `OPEN/OBS649C/${RUN}`, amount: 250 },
    ];
    const w = (R.wizard = await wizard(p, acct, "2025-10-31", writeCsv("d-statement", L)));
    await record(p, w.lines[0].id, "UNIDENTIFIED_DEPOSIT");
    await record(p, w.lines[1].id, "BANK_CHARGE");
    R.wizardShot = await snap(p, "02-wizard-two-recorded-one-open");
    R.wizardFlags = (await p.getByTestId("reconciliation-integrity-flags").innerText().catch(() => "")).replace(/\s+/g, " ");
    state.d = w;
    q(`select transaction_type, amount, transaction_date, ledger, ledger_effect, cashbook_effect, charge_recoverable, bank_statement_line_id from T.trust_transactions where trust_account_id='${acct}' order by transaction_date`, "d: rows on the new account");
    q(`select id, received_date, amount, status from T.unidentified_deposits where trust_account_id='${acct}'`, "d: unidentified deposits on the new account");
    R.overview = await overview("d", [1280, 1440]);
    // every link on the card navigates somewhere sensible
    const { p: p2 } = await login();
    R.links = [];
    for (const f of R.overview.flagsUi) for (const l of f.links) {
      await p2.goto(`${FIRM}${l.href}`, { waitUntil: "load" });
      await p2.locator("h1").first().waitFor({ timeout: 30_000 }).catch(() => {});
      R.links.push({ flag: f.testid, link: l.text, href: l.href, landed: p2.url(), h1: await p2.locator("h1").first().innerText().catch(() => null), error: /not found|something went wrong/i.test(await p2.locator("body").innerText()) });
    }
    const want = ["FUND_LEDGER_IN_DEBIT", "CHARGES_TO_REFUND", "UNIDENTIFIED_OVER_ONE_YEAR", "UNRESOLVED_STATEMENT_LINES"];
    for (const c of want) R.checks[`d: ${c} on the card (human label, message, amount=API)`] = R.overview.apiFlags.some((f) => f.code === c && f.account === acct && f.severity === "WARNING")
      && R.overview.flagsUi.some((u) => u.testid === `integrity-flag-${c}` && u.text.includes(name));
    R.checks["d: every link lands on a page without error"] = R.links.length > 0 && R.links.every((l) => l.landed.endsWith(l.href) && l.h1 && !l.error);
  },

  // blocking flag: a statement whose closing balance disagrees, reconciled as at today
  async e() {
    const { p } = await login();
    const acct = state.warnAccount;
    const w = (R.wizard = await wizard(p, acct, TODAY, writeCsv("e-statement", [{ date: day(-1), desc: "EFT CREDIT OBS 649C", ref: `E/OBS649C/${RUN}`, amount: 100 }])));
    R.wizardSummary = await summary(p);
    R.wizardShot = await snap(p, "01-wizard-bank-difference");
    state.e = w;
    reconSql(w.recon, "e: the DRAFT reconciliation as at today");
    R.overview = await overview("e", [1280, 1440]);
    const bd = R.overview.apiFlags.find((f) => f.code === "BANK_DIFFERENCE");
    R.checks["e: BANK_DIFFERENCE blocking on the card, out-of-balance rendering"] = !!bd && bd.severity === "BLOCKING" && /do not balance/.test(R.overview.cardText) && /Blocking/.test(R.overview.cardText);
    R.checks["e: wizard Complete disabled"] = !R.wizardSummary.completeEnabled;
  },

};

(async () => {
  browser = await chromium.launch({ headless: true });
  let fatal = null;
  try { await phases[PHASE](); } catch (e) { fatal = String(e.stack || e).slice(0, 1500); R.fatal = fatal; }
  R.endedAt = new Date().toISOString();
  saveAll();
  await browser.close();
  console.log(JSON.stringify({ phase: PHASE, fatal, checks: R.checks, apiWrites: R.apiWrites.map((a) => `${a.method} ${a.url} → ${a.status}`), consoleErrors: R.consoleErrors.length, pageErrors: R.pageErrors.length }, null, 2));
  process.exit(fatal || Object.values(R.checks).some((v) => !v) ? 1 : 0);
})();
