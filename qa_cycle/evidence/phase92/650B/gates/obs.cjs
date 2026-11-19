// 650B OBS — trust interest & charges memo records (save, correct, a locked month; no balance moves).
// Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081), mock-login UI.
// Every state change goes through the firm UI (deposit, statement, wizard, memo dialog). psql is
// read-only (SELECT); API calls are reads (GET). Phases share obs/state.json; run in the order
// a b c d e f g h. Data: the seeded primary GENERAL account (the only one the page and wizard use).
// Usage: node obs.cjs a|b|c|d|e|f|g|h
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const W = path.resolve(__dirname, "../../../../..");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] }));

const FIRM = "http://localhost:3001", BACKEND = "http://localhost:8081", BASE = `${FIRM}/org/e2e-test-org`;
const PAGE = `${BASE}/trust-accounting/other-creditors`;
const OUT = path.join(__dirname, "obs");
fs.mkdirSync(OUT, { recursive: true });
const PHASE = process.argv[2];
const STATE = path.join(OUT, "state.json");
const state = fs.existsSync(STATE) ? JSON.parse(fs.readFileSync(STATE, "utf8")) : {};
const R = { phase: PHASE, startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [], checks: {} };
const saveAll = () => { fs.writeFileSync(STATE, JSON.stringify(state, null, 2)); fs.writeFileSync(path.join(OUT, `result-${PHASE}.json`), JSON.stringify(R, null, 2)); };
const RUN = state.run || (state.run = Date.now().toString(36).slice(-5).toUpperCase());
const M = "2026-10-31";
const LOCKED_NOTE = "This month's trust creditor list is locked. A memo is not part of the list: saving it changes no balance and no locked list.";
const CODES = /\b(GENERAL|SECTION_86|INVESTMENT|DEPOSIT|PAYMENT|RECORDED|AWAITING_APPROVAL|LOCKED|BACKDATED_AFTER_LOCK|HELD|FUND_INTEREST_AND_CHARGES|UNIDENTIFIED_DEPOSITS|trust_interest_memo(\.saved)?|INFO|FINANCIAL)\b|\b\d{4}-\d{2}(-\d{2})?\b|(?<!Rule )\b\d+\.\d{2}\b/g;
const MONEY_COLS = "bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference";
// Memo figures (rand). SEP1 → corrected to SEP2; OCT1 → corrected to OCT2 (the locked month).
const SEP1 = { month: "2026-09", grossInterest: "1234.56", grossCharges: "115.00", chargesVat: "15.00", sourceNote: `FNB interest advice IA/SEP26 (OBS 650B ${RUN})` };
const SEP2 = { grossInterest: "1300.10", grossCharges: "230.00", chargesVat: "30.00", sourceNote: `FNB interest advice IA/SEP26 corrected (OBS 650B ${RUN})` };
const OCT1 = { month: "2026-10", grossInterest: "45.67", grossCharges: "12.50", chargesVat: "1.63", sourceNote: `Fund statement October 2026 (OBS 650B ${RUN})` };
const OCT2 = { grossInterest: "50.00", grossCharges: "20.00", chargesVat: "2.61", sourceNote: `Fund statement October 2026 re-issued (OBS 650B ${RUN})` };

// ── read-only SQL ──
const psql = (args) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", ...args], { encoding: "utf8" });
const SCHEMA = psql(["-XAt", "-c", "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"]).trim();
const SQL_OUT = path.join(OUT, `sql-${PHASE}.txt`);
fs.writeFileSync(SQL_OUT, `-- 650B OBS phase ${PHASE} read-only SQL (schema ${SCHEMA}) ${R.startedAt}\n`);
const ro = (sql) => { const s = sql.replace(/\bT\./g, `${SCHEMA}.`); if (!/^\s*(select|with)\b/i.test(s)) throw new Error("read-only SQL only"); return s; };
const q = (sql, label) => { const s = ro(sql); const out = psql(["-X", "-v", "ON_ERROR_STOP=1", "-c", s]); fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${s}\n${out}`); return out; };
const qa = (sql) => psql(["-XAt", "-F", "|", "-c", ro(sql)]).trim();
const eq = (a, b) => a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
const flat = (s) => String(s ?? "").replace(/\s+/g, " ").trim();
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const rand = (v) => "R " + Number(v).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, " ").replace(".", ",");
const listMoney = (id) => qa(`select ${MONEY_COLS.split(", ").map((c) => `${c}::text`).join(" || '|' || ")} || '|' || md5(lines::text) || '|' || md5(integrity_flags::text) || '|' || locked_at::text from T.trust_monthly_lists where id='${id}'`);
const memoSql = (month) => qa(`select id || '|' || period_month || '|' || gross_interest || '|' || gross_charges || '|' || charges_vat || '|' || source_note || '|' || recorded_by || '|' || created_at || '|' || updated_at from T.trust_interest_memos where period_month='${month}-01'`).split("|");
const audits = () => JSON.parse(qa(`select coalesce(json_agg(json_build_object('id', id, 'entityType', entity_type, 'entityId', entity_id, 'actorId', actor_id, 'details', details, 'occurredAt', occurred_at) order by occurred_at), '[]') from T.audit_events where event_type='trust_interest_memo.saved'`));
const auditSql = (label) => q(`select event_type, entity_type, entity_id, actor_id, jsonb_pretty(details) details, occurred_at from T.audit_events where event_type='trust_interest_memo.saved' order by occurred_at`, label);

// ── browser ──
let browser;
async function login(width = 1280, user = "user_e2e_alice") {
  const ctx = await browser.newContext({ viewport: { width, height: 1000 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ url: p.url(), text: m.text().slice(0, 300) }); });
  p.on("pageerror", (e) => R.pageErrors.push({ url: p.url(), text: String(e).slice(0, 300) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption(user);
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  return { ctx, p };
}
async function api(ctx, url, save) {
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token")?.value;
  const res = await ctx.request.get(`${BACKEND}${url}`, { headers: { Authorization: `Bearer ${token}` } });
  const r = { method: "GET", url, status: res.status(), body: await res.json().catch(() => null) };
  if (save) fs.writeFileSync(path.join(OUT, `api-${save}.json`), JSON.stringify(r, null, 2));
  return r;
}
async function snap(p, name, el) {
  await p.waitForTimeout(400);
  const f = path.join(OUT, `${PHASE}-${name}.png`);
  if (el) await el.screenshot({ path: f }); else await p.screenshot({ path: f, fullPage: true });
  return path.basename(f);
}
/** Clip probe: every element with its own text lies inside `sel` and the viewport, and is not cut by its own overflow. */
const clip = (p, sel) => p.evaluate((sel) => {
  const root = document.querySelector(sel);
  if (!root) return { missing: sel };
  const B = root.getBoundingClientRect(), vw = document.documentElement.clientWidth, bad = [];
  let n = 0;
  for (const el of [root, ...root.querySelectorAll("*")]) {
    if (![...el.childNodes].some((c) => c.nodeType === 3 && c.textContent.trim())) continue;
    const r = el.getBoundingClientRect(), cs = getComputedStyle(el);
    if (!r.width || (cs.position === "absolute" && r.width <= 1)) continue;
    n++;
    const cut = (cs.overflowX !== "visible" && el.scrollWidth > el.clientWidth + 1) || (cs.overflowY !== "visible" && el.scrollHeight > el.clientHeight + 1);
    if (r.left < B.left - 0.5 || r.right > B.right + 0.5 || r.right > vw + 0.5 || cut) bad.push({ text: el.textContent.trim().slice(0, 80), left: r.left, right: r.right, cut });
  }
  return { sel, root: [B.left, B.right, B.top, B.bottom], vw, textElements: n, bad };
}, sel);
const rawCodes = async (loc) => [...new Set(((await loc.innerText()).match(CODES) || []))];
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(1000); };
const testIdCount = (loc, id) => loc.getByTestId(id).count();

// ── memo page + dialog ──
async function openPage(p, query = "") {
  await p.goto(`${PAGE}${query}`, { waitUntil: "load" });
  await p.getByTestId("interest-memos-section").waitFor({ timeout: 60_000 });
  await settle(p);
  return p.getByTestId("interest-memos-section");
}
async function openDialog(p, triggerTestId) {
  await p.getByTestId(triggerTestId).click();
  const dlg = p.getByTestId("interest-memo-dialog");
  await dlg.waitFor();
  return dlg;
}
async function fill(dlg, v) {
  if (v.month) await dlg.getByTestId("interest-memo-month").fill(v.month);
  await dlg.getByTestId("interest-memo-gross-interest").fill(v.grossInterest);
  await dlg.getByTestId("interest-memo-gross-charges").fill(v.grossCharges);
  await dlg.getByTestId("interest-memo-vat").fill(v.chargesVat);
  await dlg.getByTestId("interest-memo-source").fill(v.sourceNote);
}
/** What the open dialog shows: notes, fixed month, prefilled values, inline messages. */
async function dialogState(dlg) {
  const text = async (id) => ((await testIdCount(dlg, id)) ? flat(await dlg.getByTestId(id).innerText()) : null);
  const value = async (id) => ((await testIdCount(dlg, id)) ? dlg.getByTestId(id).inputValue() : null);
  const lockedNote = dlg.getByTestId("interest-memo-locked-note");
  return {
    title: flat(await dlg.getByRole("heading").first().innerText()),
    lockedNote: await text("interest-memo-locked-note"),
    lockedNoteRole: (await lockedNote.count()) ? await lockedNote.getAttribute("role") : null,
    replacesNote: await text("interest-memo-replaces-note"),
    replaceUnknown: await text("interest-memo-replaces-unknown-note"),
    postingWarning: await testIdCount(dlg, "locked-month-warning"),
    monthInput: await value("interest-memo-month"),
    monthFixed: await text("interest-memo-month-fixed"),
    grossInterest: await value("interest-memo-gross-interest"),
    grossCharges: await value("interest-memo-gross-charges"),
    chargesVat: await value("interest-memo-vat"),
    sourceNote: await value("interest-memo-source"),
    messages: (await dlg.locator('[data-slot="form-message"], p[role="alert"]').allInnerTexts()).map(flat).filter(Boolean),
  };
}
async function submit(p, dlg) {
  await dlg.getByTestId("interest-memo-submit").click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await settle(p);
}
const rows = (p) => p.locator('[data-testid="interest-memo-row"]').evaluateAll((els) => els.map((e) => ({ cells: [...e.querySelectorAll("td")].map((td) => td.innerText.replace(/\s+/g, " ").trim()), locked: !!e.querySelector('[data-testid="interest-memo-locked"]'), correct: !!e.querySelector('[data-testid^="correct-interest-memo-btn-"]') })));
/** The UI row = the figures (labels, R x xxx,xx, the saver). */
const rowShows = (row, label, v) => !!row && row.cells[0].startsWith(label) && row.cells[1] === rand(v.grossInterest) && row.cells[2] === rand(v.grossCharges) && row.cells[3] === rand(v.chargesVat) && row.cells[4] === v.sourceNote && row.cells[5].startsWith("Alice Owner");
const apiShows = (m, month, v) => !!m && m.periodMonth === `${month}-01` && eq(m.grossInterest, v.grossInterest) && eq(m.grossCharges, v.grossCharges) && eq(m.chargesVat, v.chargesVat) && m.sourceNote === v.sourceNote && m.recordedBy === state.alice && m.recordedByName === "Alice Owner";
const sqlShows = (s, month, v) => s[1] === `${month}-01` && eq(s[2], v.grossInterest) && eq(s[3], v.grossCharges) && eq(s[4], v.chargesVat) && s[5] === v.sourceNote && s[6] === state.alice;
const detailsShow = (d, month, v) => d.trust_account_id === state.primary && d.period_month === month && d.gross_interest === Number(v.grossInterest).toFixed(2) && d.gross_charges === Number(v.grossCharges).toFixed(2) && d.charges_vat === Number(v.chargesVat).toFixed(2) && d.source_note === v.sourceNote;
const previousShow = (d, v) => d.previous_gross_interest === Number(v.grossInterest).toFixed(2) && d.previous_gross_charges === Number(v.grossCharges).toFixed(2) && d.previous_charges_vat === Number(v.chargesVat).toFixed(2) && d.previous_source_note === v.sourceNote;
const hasPrevious = (d) => Object.keys(d).some((k) => k.startsWith("previous_"));
const prefilled = (s, v) => eq(s.grossInterest, v.grossInterest) && eq(s.grossCharges, v.grossCharges) && eq(s.chargesVat, v.chargesVat) && s.sourceNote === v.sourceNote;

// ── term snapshot: every balance term (API) + byte fingerprints of the posting tables (SQL) ──
async function terms(ctx, tag) {
  const a = `/api/trust-accounts/${state.primary}`;
  const t = { tag, at: new Date().toISOString(), sql: {}, api: {} };
  t.sql.transactionsByTypeStatus = qa(`select coalesce(string_agg(s, '; ' order by s), '') from (select transaction_type || '/' || status || ' n=' || count(*) || ' sum=' || sum(amount) s from T.trust_transactions group by transaction_type, status) x`);
  for (const tb of ["trust_transactions", "client_ledger_cards", "trust_accounts", "trust_creditor_accounts", "unidentified_deposits", "trust_reconciliations", "trust_monthly_lists", "bank_statements", "bank_statement_lines", "trust_investments"]) {
    t.sql[tb] = qa(`select count(*) || ' rows md5=' || coalesce(md5(string_agg(x::text, ',' order by x.id)), '-') from T.${tb} x`);
  }
  const urls = { account: a, accounts: "/api/trust-accounts", cashbook: `${a}/cashbook-balance`, cashbookOct: `${a}/cashbook-balance?asAt=${M}`, total: `${a}/total-balance`, totalOct: `${a}/total-balance?asAt=${M}`, clientLedgers: `${a}/client-ledgers`, creditorAccounts: `${a}/creditor-accounts`, unidentified: `${a}/unidentified-deposits`, integrity: `${a}/integrity`, integrityOct: `${a}/integrity?asAt=${M}`, firmIntegrity: "/api/trust-integrity", monthlyLists: `${a}/monthly-lists` };
  for (const [k, u] of Object.entries(urls)) { const r = await api(ctx, u); t.api[k] = { status: r.status, body: r.body }; }
  t.flagCodes = [...new Set(JSON.stringify(t.api).match(/"code":"[A-Z_]+"/g) || [])].sort();
  fs.writeFileSync(path.join(OUT, `terms-${tag}.json`), JSON.stringify(t, null, 2));
  return t;
}
/** Paths at which two JSON values differ. */
function diff(a, b, p = "") {
  if (typeof a !== typeof b || a === null || b === null || typeof a !== "object") return a === b ? [] : [{ path: p, before: a, after: b }];
  return [...new Set([...Object.keys(a), ...Object.keys(b)])].flatMap((k) => diff(a[k], b[k], `${p}/${k}`));
}

// ── phases ──
const phases = {
  // a: before — the card's empty state, API [], SQL 0; term snapshot S0
  async a() {
    const { p, ctx } = await login();
    state.primary = qa("select id from T.trust_accounts where account_type='GENERAL' and is_primary");
    state.alice = qa("select id from T.members where email='alice@e2e-test.local'");
    q(`select id, account_name, account_type, is_primary from T.trust_accounts`, "a: trust accounts");
    q(`select id, name, email from T.members order by email`, "a: members");
    q(`select (select count(*) from T.trust_interest_memos) memos, (select count(*) from T.trust_transactions) postings, (select count(*) from T.trust_monthly_lists) lists, (select count(*) from T.audit_events where event_type='trust_interest_memo.saved') memo_audits`, "a: empty before the run");
    const memos = await api(ctx, `/api/trust-accounts/${state.primary}/interest-memos?year=2026`, "a-memos-2026");
    const memosDefault = await api(ctx, `/api/trust-accounts/${state.primary}/interest-memos`, "a-memos-default-year");
    const section = await openPage(p);
    R.empty = flat(await section.getByTestId("interest-memos-empty").innerText().catch(() => ""));
    R.title = flat(await section.locator('[data-slot="card-title"]').first().innerText().catch(() => ""));
    R.recordButtons = await testIdCount(section, "record-interest-memo-btn");
    R.nextYearDisabled = await section.getByTestId("interest-memos-next-year").isDisabled();
    await snap(p, "01-page-empty");
    await snap(p, "02-memo-card-empty", section);
    state.S0 = (await terms(ctx, "S0-before")).at;
    Object.assign(R.checks, {
      "a: memo card shows the empty state for 2026": R.empty === "No memos for 2026. Record one when the bank swept interest or charges at source.",
      "a: Alice (owner) sees Record memo; Next year disabled at the current year": R.recordButtons === 1 && R.nextYearDisabled,
      "a: GET interest-memos?year=2026 → 200 []": memos.status === 200 && Array.isArray(memos.body) && memos.body.length === 0,
      "a: GET interest-memos (default year) → 200 []": memosDefault.status === 200 && Array.isArray(memosDefault.body) && memosDefault.body.length === 0,
      "a: SQL trust_interest_memos count = 0": qa("select count(*) from T.trust_interest_memos") === "0",
    });
  },

  // b: lock October 2026 through the UI (deposit → one-line statement → wizard to 31 Oct); S1
  async b() {
    const { p, ctx } = await login();
    const ref = `DEP/OBS650B/${RUN}/1`;
    await p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
    await p.getByTestId("record-transaction-button").click();
    await p.getByRole("menuitem", { name: "Record Deposit", exact: true }).click();
    const dlg = p.getByRole("dialog", { name: "Record Deposit" });
    await dlg.waitFor();
    await dlg.getByTestId("trust-deposit-customer-trigger").click();
    await p.getByPlaceholder("Search clients...").fill("Acme Corp");
    await p.getByRole("option", { name: /Acme Corp/ }).first().click();
    await dlg.getByTestId("trust-deposit-matter-trigger").click();
    await p.getByPlaceholder("Search matters...").fill("Website Redesign");
    await p.getByRole("option", { name: /Website Redesign/ }).first().click();
    await dlg.getByLabel("Amount", { exact: true }).fill("10000");
    await dlg.getByLabel("Reference", { exact: true }).fill(ref);
    await dlg.getByLabel("Transaction Date").fill("2026-10-05");
    await dlg.getByRole("button", { name: "Record Deposit", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await settle(p);
    const d1 = qa(`select id || '|' || status || '|' || amount || '|' || transaction_date from T.trust_transactions where reference='${ref}'`).split("|");
    state.d1 = { id: d1[0], ref };
    const csv = path.join(OUT, `b-statement-${RUN}.csv`);
    fs.writeFileSync(csv, [`Standard Bank Trust Account - OBS 650B ${RUN}`, "Date,Description,Reference,Amount,Balance", `2026-10-05,EFT CREDIT ACME CORP,${ref},10000.00,10000.00`].join("\n") + "\n");
    await p.goto(`${BASE}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
    await p.getByTestId("account-select").locator(`option[value="${state.primary}"]`).waitFor({ state: "attached", timeout: 60_000 });
    await p.getByTestId("account-select").selectOption(state.primary);
    await p.getByTestId("period-end-input").fill(M);
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("file-input").setInputFiles(csv);
    await p.getByTestId("upload-result").waitFor({ timeout: 60_000 });
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("auto-match-btn").click();
    await p.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
    await settle(p);
    await p.getByTestId("complete-reconciliation-btn").click();
    await p.getByTestId("step-complete").waitFor({ timeout: 30_000 });
    await settle(p);
    R.locked = flat(await p.getByTestId("monthly-list-locked").innerText().catch(() => ""));
    await snap(p, "01-step5-locked");
    state.listId = qa(`select id from T.trust_monthly_lists where trust_account_id='${state.primary}' and period_end='${M}'`);
    state.listFingerprint = listMoney(state.listId);
    q(`select id, period_end, ${MONEY_COLS}, md5(lines::text) lines_md5, md5(integrity_flags::text) flags_md5, locked_at from T.trust_monthly_lists where id='${state.listId}'`, "b: the locked October 2026 list (fingerprint)");
    q(`select id, transaction_type, status, amount, transaction_date, reference from T.trust_transactions`, "b: postings");
    const acct = (await api(ctx, `/api/trust-accounts/${state.primary}`, "b-account")).body;
    const S1 = await terms(ctx, "S1-after-lock");
    state.S1 = S1.at;
    R.d1 = d1; R.listFingerprint = state.listFingerprint; R.flagCodes = S1.flagCodes;
    Object.assign(R.checks, {
      "b: deposit R 10 000,00 dated 5 Oct 2026 recorded through the UI": d1[1] === "RECORDED" && eq(d1[2], 10000) && d1[3] === "2026-10-05",
      "b: step 5 'October 2026 list locked — R 10 000,00 agrees …'": /^October 2026 list locked — R\s?10\s?000,00 agrees/.test(R.locked),
      "b: lockedThrough = 2026-10-31": acct.lockedThrough === M,
      "b: locked list row captured": !!state.listId && state.listFingerprint.split("|").length === 15,
    });
  },

  // c: save a memo for an unlocked month (September 2026) through the dialog
  async c() {
    const { p, ctx } = await login();
    await openPage(p);
    const dlg = await openDialog(p, "record-interest-memo-btn");
    R.opened = await dialogState(dlg);
    await snap(p, "01-dialog-default", dlg);
    await fill(dlg, SEP1);
    R.filled = await dialogState(dlg);
    await snap(p, "02-dialog-filled", dlg);
    await submit(p, dlg);
    const section = p.getByTestId("interest-memos-section");
    await snap(p, "03-table-saved", section);
    R.rows = await rows(p);
    R.codes = await rawCodes(section);
    const memos = (await api(ctx, `/api/trust-accounts/${state.primary}/interest-memos?year=2026`, "c-memos-2026")).body;
    const s = memoSql("2026-09");
    state.sepId = s[0];
    q(`select * from T.trust_interest_memos order by period_month`, "c: trust_interest_memos");
    auditSql("c: audit trust_interest_memo.saved");
    const au = audits();
    R.audits = au;
    Object.assign(R.checks, {
      "c: dialog opens on the previous SA month (2026-10), title 'Record an interest and charges memo'": R.opened.monthInput === "2026-10" && R.opened.title === "Record an interest and charges memo",
      "c: September 2026 chosen → no locked note, no replaces note, no 650D posting warning": R.filled.lockedNote === null && R.filled.replacesNote === null && R.filled.postingWarning === 0,
      "c: one table row: September 2026 · R 1 234,56 · R 115,00 · R 15,00 · source · Alice Owner": R.rows.length === 1 && rowShows(R.rows[0], "September 2026", SEP1) && !R.rows[0].locked && R.rows[0].correct,
      "c: no raw codes / ISO dates / raw decimals in the memo card": R.codes.length === 0,
      "c: API = the figures, recordedBy = Alice": memos.length === 1 && memos[0].id === s[0] && apiShows(memos[0], "2026-09", SEP1),
      "c: SQL row = the figures, recorded_by = Alice's member id": sqlShows(s, "2026-09", SEP1),
      "c: one audit trust_interest_memo.saved (entity trust_interest_memo, id = memo id, actor Alice), no previous_* keys": au.length === 1 && au[0].entityType === "trust_interest_memo" && au[0].entityId === s[0] && au[0].actorId === state.alice && detailsShow(au[0].details, "2026-09", SEP1) && !hasPrevious(au[0].details),
    });
  },

  // d: correct the September memo (Correct mode: month fixed, prefilled) — same row, audit with previous_*
  async d() {
    const { p, ctx } = await login();
    await openPage(p);
    const before = memoSql("2026-09");
    const dlg = await openDialog(p, `correct-interest-memo-btn-${state.sepId}`);
    R.opened = await dialogState(dlg);
    await snap(p, "01-correct-prefilled", dlg);
    await fill(dlg, SEP2);
    await submit(p, dlg);
    await snap(p, "02-table-corrected", p.getByTestId("interest-memos-section"));
    R.rows = await rows(p);
    const memos = (await api(ctx, `/api/trust-accounts/${state.primary}/interest-memos?year=2026`, "d-memos-2026")).body;
    const s = memoSql("2026-09");
    q(`select * from T.trust_interest_memos order by period_month`, "d: trust_interest_memos");
    auditSql("d: audit trust_interest_memo.saved");
    const au = (R.audits = audits());
    R.sqlBefore = before; R.sqlAfter = s;
    Object.assign(R.checks, {
      "d: Correct mode — title, month fixed as text (no month input), fields prefilled": R.opened.title === "Correct the memo for September 2026" && R.opened.monthFixed === "September 2026" && R.opened.monthInput === null && prefilled(R.opened, SEP1),
      "d: still one row, showing the corrected figures": R.rows.length === 1 && rowShows(R.rows[0], "September 2026", SEP2),
      "d: API one memo, same id, corrected figures": memos.length === 1 && memos[0].id === state.sepId && apiShows(memos[0], "2026-09", SEP2) && memos[0].updatedAt > memos[0].createdAt,
      "d: SQL one row (same id), corrected figures, updated_at advanced, created_at kept": qa("select count(*) from T.trust_interest_memos") === "1" && s[0] === state.sepId && sqlShows(s, "2026-09", SEP2) && s[7] === before[7] && s[8] > before[8],
      "d: second audit row carries previous_* = the first figures": au.length === 2 && au[1].entityId === state.sepId && detailsShow(au[1].details, "2026-09", SEP2) && previousShow(au[1].details, SEP1),
    });
  },

  // e: the locked month (October 2026): save then correct; the list is untouched
  async e() {
    const { p, ctx } = await login();
    await openPage(p);
    const dlg = await openDialog(p, "record-interest-memo-btn");
    await fill(dlg, OCT1);
    R.record = await dialogState(dlg);
    await snap(p, "01-record-locked-note", dlg);
    await submit(p, dlg);
    const first = memoSql("2026-10");
    state.octId = first[0];
    R.rowsAfterSave = await rows(p);
    const dlg2 = await openDialog(p, `correct-interest-memo-btn-${state.octId}`);
    R.correct = await dialogState(dlg2);
    await snap(p, "02-correct-locked-note", dlg2);
    await fill(dlg2, OCT2);
    await submit(p, dlg2);
    const section = p.getByTestId("interest-memos-section");
    await snap(p, "03-table-locked-marker", section);
    R.rows = await rows(p);
    R.codes = await rawCodes(section);
    const s = memoSql("2026-10");
    q(`select * from T.trust_interest_memos order by period_month`, "e: trust_interest_memos");
    auditSql("e: audit trust_interest_memo.saved");
    q(`select id, period_end, ${MONEY_COLS}, md5(lines::text) lines_md5, md5(integrity_flags::text) flags_md5, integrity_flags, locked_at from T.trust_monthly_lists where id='${state.listId}'`, "e: the locked list after the memos");
    R.listFingerprint = listMoney(state.listId);
    const list = (await api(ctx, `/api/trust-monthly-lists/${state.listId}`, "e-monthly-list")).body;
    const acct = (await api(ctx, `/api/trust-accounts/${state.primary}`, "e-account")).body;
    const memos = (await api(ctx, `/api/trust-accounts/${state.primary}/interest-memos?year=2026`, "e-memos-2026")).body;
    await p.goto(`${BASE}/trust-accounting/reconciliation/lists/${state.listId}`, { waitUntil: "load" });
    await p.getByTestId("monthly-list-page").waitFor({ timeout: 60_000 });
    await settle(p);
    await snap(p, "04-list-page");
    R.afterLockSection = (await testIdCount(p, "monthly-list-after-lock")) ? flat(await p.getByTestId("monthly-list-after-lock").innerText()) : null;
    R.afterLockRows = await p.locator('[data-testid^="after-lock-entry-"]').count();
    const au = (R.audits = audits());
    R.adjustments = list.adjustmentsSinceLock;
    const listJson = JSON.stringify(list);
    const oct = R.rows.find((r) => r.cells[0].startsWith("October 2026"));
    Object.assign(R.checks, {
      "e: record mode, October 2026 → the locked-month memo note (role=status), no 650D posting warning": R.record.lockedNote === LOCKED_NOTE && R.record.lockedNoteRole === "status" && R.record.postingWarning === 0,
      "e: the save for the locked month is not refused (dialog closed, SQL row = figures)": sqlShows(first, "2026-10", OCT1) && R.rowsAfterSave.length === 2,
      "e: correct mode for October 2026 → the same note, prefilled, month fixed": R.correct.lockedNote === LOCKED_NOTE && R.correct.monthFixed === "October 2026" && prefilled(R.correct, OCT1) && R.correct.postingWarning === 0,
      "e: corrected (same id), row marked 'List locked', oldest month first": s[0] === state.octId && sqlShows(s, "2026-10", OCT2) && R.rows.length === 2 && R.rows[0].cells[0].startsWith("September 2026") && rowShows(oct, "October 2026", OCT2) && oct.locked,
      "e: API = UI = SQL for October": memos.length === 2 && apiShows(memos.find((m) => m.id === state.octId), "2026-10", OCT2),
      "e: audit — 4 rows; October save without previous_*, correction with previous_* = OCT1": au.length === 4 && au[2].entityId === state.octId && !hasPrevious(au[2].details) && detailsShow(au[2].details, "2026-10", OCT1) && detailsShow(au[3].details, "2026-10", OCT2) && previousShow(au[3].details, OCT1),
      "e: locked list row byte-identical to b (12 money columns, md5 lines/flags, locked_at)": R.listFingerprint === state.listFingerprint,
      "e: GET monthly list → adjustmentsSinceLock has no entries": Array.isArray(list.adjustmentsSinceLock?.entries) && list.adjustmentsSinceLock.entries.length === 0,
      "e: no BACKDATED_AFTER_LOCK flag (list API, list row)": !listJson.includes("BACKDATED_AFTER_LOCK") && !qa(`select integrity_flags::text from T.trust_monthly_lists where id='${state.listId}'`).includes("BACKDATED_AFTER_LOCK"),
      "e: list page shows no 'entered after the lock' entry": R.afterLockRows === 0,
      "e: lockedThrough still 2026-10-31": acct.lockedThrough === M,
      "e: no raw codes in the memo card": R.codes.length === 0,
    });
  },

  // f: no balance moves — the term snapshot after c–e is identical to S1 (after the lock)
  async f() {
    const { ctx } = await login();
    const S1 = JSON.parse(fs.readFileSync(path.join(OUT, "terms-S1-after-lock.json"), "utf8"));
    const S2 = await terms(ctx, "S2-after-memos");
    R.diffSql = diff(S1.sql, S2.sql);
    R.diffApi = diff(S1.api, S2.api);
    R.diffFlags = diff(S1.flagCodes, S2.flagCodes);
    R.S1 = { sql: S1.sql, flagCodes: S1.flagCodes }; R.S2 = { sql: S2.sql, flagCodes: S2.flagCodes };
    fs.writeFileSync(path.join(OUT, "f-terms-diff.json"), JSON.stringify({ S1: S1.at, S2: S2.at, sql: R.diffSql, api: R.diffApi, flagCodes: R.diffFlags }, null, 2));
    q(`select transaction_type, status, count(*), sum(amount) from T.trust_transactions group by 1, 2 order by 1, 2`, "f: postings by type/status after the memos");
    q(`select count(*) memos from T.trust_interest_memos`, "f: memos");
    Object.assign(R.checks, {
      "f: SQL — postings, ledgers, accounts, creditors, items, reconciliations, lists, statements identical to S1": R.diffSql.length === 0,
      "f: API — every balance term (account, cashbook, total + breakdown, ledgers, creditors, items, integrity, firm integrity, lists) identical to S1": R.diffApi.length === 0,
      "f: integrity flag codes identical to S1": R.diffFlags.length === 0,
      "f: the snapshot had data (one posting, one list)": /DEPOSIT\/RECORDED n=1 sum=10000.00/.test(S2.sql.transactionsByTypeStatus) && S2.sql.trust_monthly_lists.startsWith("1 rows"),
    });
  },

  // g: refusals and guards in the UI (nothing persists); cross-year notice; Carol (member) gating
  async g() {
    const memoRows = () => qa("select coalesce(string_agg(id || ':' || updated_at, ',' order by period_month), '') from T.trust_interest_memos");
    const before = { memos: memoRows(), audits: audits().length };
    const { p, ctx } = await login();
    await openPage(p);
    const dlg = await openDialog(p, "record-interest-memo-btn");
    // VAT above the charges
    await fill(dlg, { month: "2026-08", grossInterest: "10.00", grossCharges: "10.00", chargesVat: "20.00", sourceNote: `OBS 650B ${RUN} refusal probe` });
    await dlg.getByTestId("interest-memo-submit").click();
    await p.waitForTimeout(800);
    R.vat = { ...(await dialogState(dlg)), open: await dlg.isVisible() };
    await snap(p, "01-vat-refused", dlg);
    // a future month (December 2026; today in SA is in November 2026)
    await fill(dlg, { month: "2026-12", grossInterest: "10.00", grossCharges: "10.00", chargesVat: "1.00", sourceNote: `OBS 650B ${RUN} refusal probe` });
    await dlg.getByTestId("interest-memo-submit").click();
    await p.waitForTimeout(800);
    R.future = { ...(await dialogState(dlg)), open: await dlg.isVisible(), max: await dlg.getByTestId("interest-memo-month").getAttribute("max") };
    await snap(p, "02-future-month-refused", dlg);
    // in flight: hold the save's server-action request, observe the disabled form, then abort it (never sent)
    await fill(dlg, { month: "2026-08", grossInterest: "10.00", grossCharges: "10.00", chargesVat: "1.00", sourceNote: `OBS 650B ${RUN} in-flight probe (aborted)` });
    let release;
    const held = new Promise((r) => (release = r));
    R.heldRequests = [];
    await p.route("**/*", async (route) => {
      const r = route.request();
      if (r.method() === "POST" && r.headers()["next-action"]) { R.heldRequests.push(r.url()); await held; await route.abort(); } else await route.fallback();
    });
    await dlg.getByTestId("interest-memo-submit").click();
    await p.waitForFunction(() => document.querySelector('[data-testid="interest-memo-fields"]')?.disabled === true, null, { timeout: 5000 }).catch(() => {});
    R.inFlight = await dlg.evaluate((d) => {
      const submitBtn = d.querySelector('[data-testid="interest-memo-submit"]');
      return {
        fieldsetDisabled: d.querySelector('[data-testid="interest-memo-fields"]').disabled,
        controlsDisabled: [...d.querySelectorAll('[data-testid="interest-memo-fields"] input, [data-testid="interest-memo-fields"] textarea')].map((e) => e.matches(":disabled")),
        submitDisabled: submitBtn.disabled,
        submitText: submitBtn.innerText.trim(),
        cancelDisabled: [...d.querySelectorAll("button")].find((b) => b.innerText.trim() === "Cancel")?.disabled,
      };
    });
    await snap(p, "03-in-flight-disabled", dlg);
    await p.keyboard.press("Escape");
    await p.waitForTimeout(300);
    R.inFlight.openAfterEscape = await dlg.isVisible();
    const inducedFrom = R.consoleErrors.length;
    release();
    await p.waitForFunction(() => document.querySelector('[data-testid="interest-memo-fields"]')?.disabled === false, null, { timeout: 10_000 }).catch(() => {});
    R.afterAbort = { ...(await dialogState(dlg)), fieldsetDisabled: await dlg.getByTestId("interest-memo-fields").isDisabled() };
    await p.unroute("**/*");
    R.inducedConsoleErrors = R.consoleErrors.splice(inducedFrom);
    await dlg.getByRole("button", { name: "Cancel" }).click();
    await dlg.waitFor({ state: "hidden" });
    // the cross-year replacement notice (D-21): the 2025 page, Record for October 2026 (it has a memo)
    const s2025 = await openPage(p, "?memoYear=2025");
    R.empty2025 = flat(await s2025.getByTestId("interest-memos-empty").innerText().catch(() => ""));
    const dlg2 = await openDialog(p, "record-interest-memo-btn");
    await dlg2.getByTestId("interest-memo-replaces-note").waitFor({ timeout: 15_000 }).catch(() => {});
    R.crossYear = await dialogState(dlg2);
    await snap(p, "04-cross-year-replaces", dlg2);
    await dlg2.getByTestId("interest-memo-month").fill("2026-08");
    await p.waitForTimeout(1500);
    R.crossYearAugust = await dialogState(dlg2);
    await dlg2.getByRole("button", { name: "Cancel" }).click();
    await dlg2.waitFor({ state: "hidden" });
    await ctx.close();
    // Carol (member): VIEW_TRUST, no MANAGE_TRUST → the list, no Record / Correct
    const c = await login(1280, "user_e2e_carol");
    const caps = (await api(c.ctx, "/api/me/capabilities", "g-carol-capabilities")).body;
    const carolMemos = await api(c.ctx, `/api/trust-accounts/${state.primary}/interest-memos?year=2026`, "g-carol-memos-2026");
    const cs = await openPage(c.p);
    R.carol = {
      viewTrust: caps.capabilities.includes("VIEW_TRUST"), manageTrust: caps.capabilities.includes("MANAGE_TRUST"), role: caps.role,
      rows: (await rows(c.p)).map((r) => ({ month: r.cells[0], correct: r.correct, cells: r.cells.length })),
      record: await testIdCount(cs, "record-interest-memo-btn"),
      correct: await cs.locator('[data-testid^="correct-interest-memo-btn-"]').count(),
      apiStatus: carolMemos.status, apiCount: carolMemos.body?.length,
    };
    await snap(c.p, "05-carol-memo-card", cs);
    const after = { memos: memoRows(), audits: audits().length };
    R.before = before; R.after = after;
    q(`select id, period_month, gross_interest, gross_charges, charges_vat, updated_at from T.trust_interest_memos order by period_month`, "g: memos unchanged by the probes");
    Object.assign(R.checks, {
      "g: VAT > charges refused inline ('The VAT cannot be more than the gross charges'), dialog stays open": R.vat.open && R.vat.messages.includes("The VAT cannot be more than the gross charges"),
      "g: December 2026 refused inline ('A memo can be saved for this month or an earlier one'), max = 2026-11": R.future.open && R.future.messages.includes("A memo can be saved for this month or an earlier one") && R.future.max === "2026-11",
      "g: in flight — fieldset + every field, Cancel and Submit disabled, 'Saving...'; Escape keeps it open": R.heldRequests.length === 1 && R.inFlight.fieldsetDisabled && R.inFlight.controlsDisabled.length === 5 && R.inFlight.controlsDisabled.every(Boolean) && R.inFlight.submitDisabled && R.inFlight.cancelDisabled && R.inFlight.submitText === "Saving..." && R.inFlight.openAfterEscape,
      "g: aborted save → inline error, fields re-enabled, dialog open": !R.afterAbort.fieldsetDisabled && R.afterAbort.messages.includes("An unexpected error occurred"),
      "g: ?memoYear=2025 → empty state for 2025": R.empty2025 === "No memos for 2025. Record one when the bank swept interest or charges at source.",
      "g: cross-year — Record on the 2025 page for October 2026 shows the 'replaces' notice (+ locked note)": R.crossYear.monthInput === "2026-10" && R.crossYear.replacesNote === "A memo for October 2026 is already saved. Saving replaces its figures; the previous figures stay in the audit trail." && R.crossYear.lockedNote === LOCKED_NOTE,
      "g: cross-year — August 2026 (no memo) → no replaces note": R.crossYearAugust.replacesNote === null && R.crossYearAugust.replaceUnknown === null,
      "g: Carol has VIEW_TRUST, not MANAGE_TRUST; sees the 2 memos, no Record, no Correct": R.carol.viewTrust && !R.carol.manageTrust && R.carol.rows.length === 2 && R.carol.record === 0 && R.carol.correct === 0 && R.carol.apiStatus === 200 && R.carol.apiCount === 2,
      "g: nothing persisted by the probes (memo rows + updated_at, audit count unchanged)": before.memos === after.memos && before.audits === after.audits,
    });
  },

  // h: 1280 / 1440 clip probe — the memo table and the dialog in both modes; raw-code scan
  async h() {
    R.widths = {};
    for (const w of [1280, 1440]) {
      const { p, ctx } = await login(w);
      const section = await openPage(p);
      const out = (R.widths[w] = {});
      out.table = { clip: await clip(p, '[data-testid="interest-memos-section"]'), codes: await rawCodes(section), shot: await snap(p, `table-${w}`, section) };
      out.pageCodes = await rawCodes(p.locator("main"));
      // Readability (not clipping): the Source cell's width and any word broken across lines.
      out.sourceCells = await p.locator('[data-testid="interest-memo-source-note"]').evaluateAll((els) => els.map((el) => {
        const node = [...el.childNodes].find((c) => c.nodeType === 3), broken = [];
        let at = 0;
        for (const word of node.textContent.split(" ")) {
          const range = document.createRange();
          range.setStart(node, at); range.setEnd(node, at + word.length);
          if (new Set([...range.getClientRects()].map((r) => Math.round(r.top))).size > 1) broken.push(word);
          at += word.length + 1;
        }
        return { width: Math.round(el.getBoundingClientRect().width), brokenWords: broken };
      }));
      const rec = await openDialog(p, "record-interest-memo-btn");
      await rec.getByTestId("interest-memo-replaces-note").waitFor({ timeout: 15_000 }).catch(() => {});
      out.record = { state: await dialogState(rec), clip: await clip(p, '[data-testid="interest-memo-dialog"]'), codes: await rawCodes(rec), shot: await snap(p, `dialog-record-${w}`, rec) };
      await rec.getByRole("button", { name: "Cancel" }).click();
      await rec.waitFor({ state: "hidden" });
      const cor = await openDialog(p, `correct-interest-memo-btn-${state.octId}`);
      out.correct = { state: await dialogState(cor), clip: await clip(p, '[data-testid="interest-memo-dialog"]'), codes: await rawCodes(cor), shot: await snap(p, `dialog-correct-${w}`, cor) };
      await cor.getByRole("button", { name: "Cancel" }).click();
      await cor.waitFor({ state: "hidden" });
      await ctx.close();
    }
    const ok = (x) => !x.clip.missing && x.clip.bad.length === 0 && x.clip.root[3] <= 1000.5;
    for (const w of [1280, 1440]) {
      const o = R.widths[w];
      R.checks[`h: ${w} — memo table: no clipped text, inside the card and the viewport`] = !o.table.clip.missing && o.table.clip.bad.length === 0;
      R.checks[`h: ${w} — record dialog (locked + replaces notes on): no clipped text, inside the viewport`] = ok(o.record) && !!o.record.state.lockedNote && !!o.record.state.replacesNote;
      R.checks[`h: ${w} — correct dialog (locked note on): no clipped text, inside the viewport`] = ok(o.correct) && !!o.correct.state.lockedNote;
      R.checks[`h: ${w} — no raw codes / ISO dates / raw decimals in the table or either dialog`] = [o.table, o.record, o.correct].every((x) => x.codes.length === 0);
    }
  },
};

(async () => {
  browser = await chromium.launch({ headless: true });
  let fatal = null;
  try { await phases[PHASE](); } catch (e) { fatal = String(e.stack || e).slice(0, 1500); R.fatal = fatal; }
  R.endedAt = new Date().toISOString();
  saveAll();
  await browser.close();
  console.log(JSON.stringify({ phase: PHASE, fatal, checks: R.checks, consoleErrors: R.consoleErrors.length, pageErrors: R.pageErrors.length }, null, 2));
  process.exit(fatal || Object.values(R.checks).some((v) => !v) ? 1 : 0);
})();
