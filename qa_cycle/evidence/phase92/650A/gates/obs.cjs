// 650A OBS — Rule 54.15 monthly trust creditor list locked on month-end reconciliation completion.
// Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081), mock-login UI.
// Every state change goes through the firm UI (matters, client, deposits, statements, wizard).
// psql is read-only (SELECT). API calls are reads for evidence (the Section 35 execute is a POST
// that only reads). Phases share obs/state.json; run in the order s b a c g.
// The reconciliation list and Record Deposit work on the primary account only, so the run uses the
// primary GENERAL account (empty on a fresh stack: its figures are this run's alone).
// Usage: node obs.cjs s|b|a|c|g
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
const RUN = state.run || (state.run = Date.now().toString(36).slice(-5).toUpperCase());
const CODES = /\b(BACKDATED_AFTER_LOCK|MONTHLY_LIST|CLIENT_INVESTMENT|INTER_ACCOUNT|UNIDENTIFIED|FUND|CLIENT|DEPOSIT|PAYMENT|RECORDED|APPROVED|COMPLETED|DRAFT|BANK_DIFFERENCE|BOOKS_OUT_OF_BALANCE|UNRESOLVED_STATEMENT_LINES|FUND_LEDGER_IN_DEBIT|CHARGES_TO_REFUND|BLOCKING|WARNING|TRUST_ACCOUNT|GENERAL)\b/g;
const MONEY_COLS = "bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference";

// ── read-only SQL ──
const psql = (args) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", ...args], { encoding: "utf8" });
const SCHEMA = psql(["-XAt", "-c", "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"]).trim();
const SQL_OUT = path.join(OUT, `sql-${PHASE}.txt`);
fs.writeFileSync(SQL_OUT, `-- 650A OBS phase ${PHASE} read-only SQL (schema ${SCHEMA}) ${R.startedAt}\n`);
const ro = (sql) => { const s = sql.replace(/\bT\./g, `${SCHEMA}.`); if (!/^\s*(select|with)\b/i.test(s)) throw new Error("read-only SQL only"); return s; };
const q = (sql, label) => { const s = ro(sql); const out = psql(["-X", "-v", "ON_ERROR_STOP=1", "-c", s]); fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${s}\n${out}`); return out; };
const qa = (sql) => psql(["-XAt", "-F", "|", "-c", ro(sql)]).trim();
const eq = (a, b) => a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
const money = (s) => { const m = String(s ?? "").match(/(-|−)?\s*R[\s  ]?(-|−)?[\d\s  ]*\d,\d{2}/); if (!m) return null; const n = Number(m[0].replace(/[^\d,]/g, "").replace(",", ".")); return /[-−]/.test(m[0]) ? -n : n; };
const flat = (s) => String(s ?? "").replace(/\s+/g, " ").trim();
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

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
  const r = { method, url, status: res.status(), body: await res.json().catch(() => null) };
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
    if (!r.width || (cs.position === "absolute" && r.width <= 1)) continue;
    n++;
    const cut = cs.overflowX !== "visible" && el.scrollWidth > el.clientWidth + 1;
    if (r.left < B.left - 0.5 || r.right > B.right + 0.5 || r.right > vw + 0.5 || cut) bad.push({ text: el.textContent.trim().slice(0, 80), left: r.left, right: r.right, cut });
  }
  return { sel, root: [B.left, B.right], vw, textElements: n, bad };
}, sel);
const rawCodes = async (loc) => [...new Set(((await loc.innerText()).match(CODES) || []))];
const errorState = async (p) => /something went wrong|failed to load|not found|application error/i.test(await p.locator("main").innerText());
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(1500); };

// ── UI writes ──
async function newMatter(p, template, name, client) {
  await p.goto(`${BASE}/matters`, { waitUntil: "load" });
  await p.getByRole("button", { name: "New from Template" }).click();
  const dlg = p.getByRole("dialog", { name: /New from Template/ });
  await dlg.getByRole("option", { name: template }).click();
  await dlg.getByRole("button", { name: "Next" }).click();
  await dlg.getByLabel(/Matter name/).fill(name);
  const sel = dlg.getByLabel(/^Client/), opt = sel.locator("option", { hasText: client }).first();
  await opt.waitFor({ state: "attached", timeout: 15_000 });
  await sel.selectOption(await opt.getAttribute("value"));
  await dlg.getByRole("button", { name: "Create Matter" }).click();
  await p.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 30_000 });
  return p.url().match(/matters\/([0-9a-f-]{36})/)[1];
}
async function newClient(p, name) {
  await p.goto(`${BASE}/clients`, { waitUntil: "load" });
  await p.getByRole("button", { name: "New Client" }).click();
  const dlg = p.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await dlg.getByLabel("Name", { exact: true }).fill(name);
  const type = dlg.getByLabel("Type", { exact: true });
  await type.selectOption(await type.locator("option", { hasText: "Company" }).first().getAttribute("value"));
  await dlg.getByLabel("Email", { exact: true }).fill(`obs650a-${RUN.toLowerCase()}@example.com`);
  await dlg.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
  await dlg.locator('input[name="city"]').fill("Sandton");
  await dlg.locator('input[name="postalCode"]').fill("2196");
  await dlg.getByLabel(/^Country/).selectOption("ZA");
  await dlg.getByRole("button", { name: "Next" }).click();
  await dlg.getByText("Step 2 of 2").waitFor();
  await dlg.getByRole("button", { name: "Create Client" }).click();
  await p.getByRole("heading", { level: 1, name }).waitFor({ timeout: 30_000 });
  await p.getByRole("button", { name: "Start Onboarding" }).first().click();
  const confirm = p.getByRole("alertdialog", { name: "Start Onboarding" });
  await confirm.getByRole("button", { name: "Start Onboarding" }).click();
  await confirm.waitFor({ state: "hidden", timeout: 15_000 });
  await p.getByTestId("lifecycle-badge").filter({ hasText: "Onboarding" }).waitFor({ timeout: 15_000 });
  return p.url().match(/clients\/([0-9a-f-]{36})/)[1];
}
/** Record Deposit on the Transactions page (primary account). Returns the dialog outcome and the row. */
async function deposit(p, d) {
  await p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
  await p.getByTestId("record-transaction-button").click();
  await p.getByRole("menuitem", { name: "Record Deposit" }).click();
  const dlg = p.getByRole("dialog", { name: "Record Deposit" });
  await dlg.getByTestId("trust-deposit-customer-trigger").click();
  await p.getByPlaceholder("Search clients...").fill(d.client);
  await p.getByRole("option", { name: new RegExp(esc(d.client)) }).first().click();
  await dlg.getByTestId("trust-deposit-matter-trigger").click();
  await p.getByPlaceholder("Search matters...").fill(d.matter);
  await p.getByRole("option", { name: new RegExp(esc(d.matter)) }).first().click();
  await dlg.getByLabel("Amount", { exact: true }).fill(String(d.amount));
  await dlg.getByLabel("Reference", { exact: true }).fill(d.ref);
  await dlg.getByLabel("Transaction Date").fill(d.date);
  await dlg.getByRole("button", { name: "Record Deposit" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await settle(p);
  const row = qa(`select id || '|' || status || '|' || amount || '|' || transaction_date || '|' || coalesce(posted_at::text,'') from T.trust_transactions where reference='${d.ref}'`).split("|");
  return { ...d, id: row[0], status: row[1], amountSql: +row[2], dateSql: row[3], postedAt: row[4] };
}
function writeCsv(name, lines, opening = 0) {
  let bal = opening; const rows = [`Standard Bank Trust Account - OBS 650A ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of lines) { bal = +(bal + l.amount).toFixed(2); rows.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
  const f = path.join(OUT, `${name}-${RUN}.csv`); fs.writeFileSync(f, rows.join("\n") + "\n"); return f;
}
/** The wizard to the review step: select account + period end, upload, auto-match. */
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
  const autoMatch = flat(await p.getByTestId("auto-match-result").innerText());
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  await settle(p);
  const statement = qa(`select id from T.bank_statements where file_name='${path.basename(csv)}'`);
  return { statement, recon: qa(`select id from T.trust_reconciliations where bank_statement_id='${statement}'`), autoMatch };
}
async function complete(p) {
  const s = { summary: flat(await p.getByTestId("balance-summary").innerText()), completeEnabled: await p.getByTestId("complete-reconciliation-btn").isEnabled(), progress: await p.getByTestId("match-progress").innerText() };
  await p.getByTestId("complete-reconciliation-btn").click();
  await p.getByTestId("step-complete").waitFor({ timeout: 30_000 });
  await settle(p);
  return { before: s, step5: flat(await p.getByTestId("step-complete").innerText()) };
}
const reconSql = (id, label) => q(`select r.id, r.status, r.period_end, r.bank_balance, r.outstanding_deposits, r.outstanding_payments, r.adjusted_bank_balance, r.cashbook_balance, r.client_ledger_total, r.creditor_total, r.difference, r.is_balanced, r.integrity_flags, r.completed_at, s.status statement_status, s.closing_balance
  from T.trust_reconciliations r join T.bank_statements s on s.id=r.bank_statement_id where r.id='${id}'`, label);
const listSql = (where, label) => q(`select id, trust_account_id, period_end, reconciliation_id, ${MONEY_COLS}, jsonb_pretty(lines) lines, integrity_flags, locked_by, locked_at from T.trust_monthly_lists where ${where}`, label);
const listMoney = (id) => qa(`select ${MONEY_COLS.split(", ").map((c) => `${c}::text`).join(" || '|' || ")} || '|' || md5(lines::text) || '|' || md5(integrity_flags::text) || '|' || locked_at::text from T.trust_monthly_lists where id='${id}'`);

/** Reconciliation list page: the row of a reconciliation and its Monthly list cell. */
async function reconList(p, reconId) {
  await p.goto(`${BASE}/trust-accounting/reconciliation`, { waitUntil: "load" });
  const row = p.getByTestId(`reconciliation-row-${reconId}`);
  await row.waitFor({ timeout: 60_000 });
  const headers = (await p.locator("thead th").allInnerTexts()).map(flat);
  const cells = (await row.locator("td").allInnerTexts()).map(flat);
  return { headers, cells, monthlyListCell: cells[headers.indexOf("Monthly list")], badge: await p.getByTestId(`list-locked-${reconId}`).count() };
}
/** The locked-list page as rendered. */
async function readListPage(p) {
  const page = p.getByTestId("monthly-list-page");
  await page.waitFor({ timeout: 60_000 });
  const tiles = {};
  for (const label of ["Adjusted bank balance", "Cashbook", "Trust creditors", "Difference"]) {
    const t = await page.getByRole("group", { name: label, exact: true }).first().innerText();
    tiles[label] = { text: flat(t), value: money(t) };
  }
  const rows = await p.getByTestId("monthly-list-creditors").locator("tbody tr").evaluateAll((trs) => trs.map((tr) => ({ matter: tr.dataset.testid === "monthly-list-matter", cells: [...tr.querySelectorAll("td")].map((td) => td.innerText.trim()) })));
  return {
    url: p.url(), title: await p.locator("h1").first().innerText(), description: flat(await page.locator("p").first().innerText()),
    tiles, rows: rows.map((r) => ({ ...r, value: money(r.cells[2]) })),
    total: flat(await p.getByTestId("monthly-list-creditors").locator("tfoot").innerText()),
    flagsSection: await p.getByTestId("monthly-list-flags").count(),
    afterLock: flat(await p.getByTestId("monthly-list-after-lock").innerText()),
    afterLockRows: await p.locator('[data-testid^="after-lock-entry-"]').evaluateAll((els) => els.map((e) => ({ testid: e.dataset.testid, cells: [...e.querySelectorAll("td")].map((td) => td.innerText.trim()) }))),
    rawCodes: await rawCodes(p.locator("main")), errorState: await errorState(p),
  };
}
/** Page figures = API JSON = SQL row (money columns), creditor rows = API lines. */
function crossCheck(tag, ui, apiBody, listId) {
  const s = qa(`select ${MONEY_COLS} from T.trust_monthly_lists where id='${listId}'`).split("|").map(Number);
  const col = Object.fromEntries(MONEY_COLS.split(", ").map((c, i) => [c, s[i]]));
  const b = apiBody;
  R.checks[`${tag}: tiles = API = SQL (adjusted, cashbook, creditors, difference)`] = eq(ui.tiles["Adjusted bank balance"].value, b.adjustedBankBalance) && eq(b.adjustedBankBalance, col.adjusted_bank_balance)
    && eq(ui.tiles.Cashbook.value, b.cashbookBalance) && eq(b.cashbookBalance, col.cashbook_balance)
    && eq(ui.tiles["Trust creditors"].value, b.creditorTotal) && eq(b.creditorTotal, col.creditor_total)
    && eq(ui.tiles.Difference.value, b.difference) && eq(b.difference, col.difference) && eq(col.difference, 0);
  const apiRows = b.lines.flatMap((l) => [{ value: l.balance, matter: false, kind: l.kind, name: l.name }, ...l.matters.map((m) => ({ value: m.balance, matter: true, name: m.name }))]);
  R.checks[`${tag}: creditor rows on screen = API lines (names, kinds, balances, matters)`] = ui.rows.length === apiRows.length && ui.rows.every((r, i) => eq(r.value, apiRows[i].value) && r.matter === apiRows[i].matter && (!apiRows[i].name || r.cells[0].startsWith(apiRows[i].name)));
  R.checks[`${tag}: API lines sum to creditor total`] = eq(b.lines.reduce((a, l) => a + l.balance, 0), b.creditorTotal);
  return col;
}

// ── phases ──
const phases = {
  // setup through the UI: a second matter for Acme Corp, a second client (onboarding) with a matter
  async s() {
    const { p } = await login();
    state.primary = qa("select id from T.trust_accounts where account_type='GENERAL' and is_primary");
    state.acme = { client: "Acme Corp", matter1: "Website Redesign", matter2: `OBS 650A Transfer ${RUN}` };
    state.acme.matter2Id = await newMatter(p, /Property Transfer/, state.acme.matter2, "Acme Corp");
    state.holdings = { client: `OBS 650A Holdings ${RUN}`, matter: `OBS 650A Contract ${RUN}` };
    state.holdings.clientId = await newClient(p, state.holdings.client);
    state.holdings.matterId = await newMatter(p, /Commercial/, state.holdings.matter, state.holdings.client);
    q(`select c.id, c.name, c.lifecycle_status, p.id matter_id, p.name matter from T.customers c join T.projects p on p.customer_id=c.id order by c.name, p.name`, "s: clients and matters");
    q(`select id, account_name, account_type, is_primary from T.trust_accounts`, "s: trust accounts (the primary is used)");
    q(`select count(*) postings, (select count(*) from T.trust_monthly_lists) lists, (select count(*) from T.trust_reconciliations) recons from T.trust_transactions`, "s: primary account empty before the run");
    R.checks["s: matters + client created"] = !!(state.acme.matter2Id && state.holdings.clientId && state.holdings.matterId);
  },

  // b: a mid-month reconciliation (period end 15 Oct 2026) completes without locking a list
  async b() {
    const { p } = await login();
    const d1 = (state.d1 = await deposit(p, { client: "Acme Corp", matter: "Website Redesign", amount: 10000, ref: `DEP/OBS650A/${RUN}/1`, date: "2026-10-05" }));
    const csv = writeCsv("b-statement", [{ date: "2026-10-05", desc: "EFT CREDIT ACME CORP", ref: d1.ref, amount: 10000 }]);
    const w = (state.b = await wizard(p, state.primary, "2026-10-15", csv));
    await snap(p, "01-review");
    R.complete = await complete(p);
    R.notLocked = flat(await p.getByTestId("monthly-list-not-locked").innerText().catch(() => ""));
    R.lockedShown = await p.getByTestId("monthly-list-locked").count();
    R.step5Codes = await rawCodes(p.getByTestId("step-complete"));
    await snap(p, "02-step5-not-locked");
    reconSql(w.recon, "b: the mid-month reconciliation");
    q(`select count(*) lists_for_this_reconciliation from T.trust_monthly_lists where reconciliation_id='${w.recon}'`, "b: no list for it");
    q(`select count(*) lists_total from T.trust_monthly_lists`, "b: no list at all");
    R.list = await reconList(p, w.recon);
    await snap(p, "03-reconciliation-list");
    Object.assign(R.checks, {
      "b: deposit recorded through the UI, dated 5 Oct 2026, posted": !!d1.id && d1.dateSql === "2026-10-05" && !!d1.postedAt && d1.amountSql === 10000,
      "b: step 5 'No monthly list was locked: 15 Oct 2026 is not the last day of a month.'": R.notLocked === "No monthly list was locked: 15 Oct 2026 is not the last day of a month." && R.lockedShown === 0,
      "b: reconciliation COMPLETED, no trust_monthly_lists row": qa(`select status from T.trust_reconciliations where id='${w.recon}'`) === "COMPLETED" && qa(`select count(*) from T.trust_monthly_lists where reconciliation_id='${w.recon}'`) === "0",
      "b: list row Monthly list cell is a dash, no badge": /^-+$|^—$/.test(R.list.monthlyListCell) && R.list.badge === 0,
      "b: no raw enum in step 5": R.step5Codes.length === 0,
    });
  },

  // a: the month-end reconciliation (31 Oct 2026) locks the October list
  async a() {
    const { p, ctx } = await login();
    const d2 = (state.d2 = await deposit(p, { client: "Acme Corp", matter: state.acme.matter2, amount: 2500, ref: `DEP/OBS650A/${RUN}/2`, date: "2026-10-20" }));
    const d3 = (state.d3 = await deposit(p, { client: state.holdings.client, matter: state.holdings.matter, amount: 4000, ref: `DEP/OBS650A/${RUN}/3`, date: "2026-10-22" }));
    // the bank shows D2 only; D3 is an outstanding deposit at 31 Oct
    const csv = writeCsv("a-statement", [{ date: "2026-10-20", desc: "EFT CREDIT ACME CORP", ref: d2.ref, amount: 2500 }], 10000);
    const w = (state.a = await wizard(p, state.primary, "2026-10-31", csv));
    await snap(p, "01-review");
    R.complete = await complete(p);
    R.locked = flat(await p.getByTestId("monthly-list-locked").innerText().catch(() => ""));
    R.step5Codes = await rawCodes(p.getByTestId("step-complete"));
    await snap(p, "02-step5-locked");
    const listId = (state.listId = qa(`select id from T.trust_monthly_lists where reconciliation_id='${w.recon}'`));
    // step 5 link
    await p.getByTestId("view-locked-list").click();
    await p.waitForURL(new RegExp(`/reconciliation/lists/${listId}$`), { timeout: 30_000 });
    R.ui = await readListPage(p);
    await snap(p, "03-list-page-from-step5");
    R.apiList = (await api(ctx, "GET", `/api/trust-monthly-lists/${listId}`, null, "a-monthly-list")).body;
    R.apiSummaries = (await api(ctx, "GET", `/api/trust-accounts/${state.primary}/monthly-lists`, null, "a-monthly-lists-of-account")).body;
    R.apiRecon = (await api(ctx, "GET", `/api/trust-reconciliations/${w.recon}`, null, "a-reconciliation")).body;
    reconSql(w.recon, "a: the month-end reconciliation");
    listSql(`trust_account_id='${state.primary}'`, "a: trust_monthly_lists rows of the account");
    q(`select event_type, entity_type, entity_id, details, occurred_at from T.audit_events where event_type='trust_monthly_list.locked' order by occurred_at`, "a: trust_monthly_list.locked audit events");
    q(`select id, transaction_type, status, amount, transaction_date, customer_id, project_id, bank_statement_line_id is not null matched, posted_at from T.trust_transactions where trust_account_id='${state.primary}' order by transaction_date`, "a: postings on the account");
    // reconciliation list → badge → list page
    R.list = await reconList(p, w.recon);
    R.listB = await reconList(p, state.b.recon);
    await snap(p, "04-reconciliation-list");
    await p.getByTestId(`list-locked-${w.recon}`).click();
    await p.waitForURL(new RegExp(`/reconciliation/lists/${listId}$`), { timeout: 30_000 });
    await p.getByTestId("monthly-list-page").waitFor({ timeout: 60_000 });
    R.fromListTitle = await p.locator("h1").first().innerText();
    const col = crossCheck("a", R.ui, R.apiList, listId);
    const rs = qa(`select adjusted_bank_balance, cashbook_balance, creditor_total, difference, bank_balance, outstanding_deposits from T.trust_reconciliations where id='${w.recon}'`).split("|").map(Number);
    const ev = qa(`select count(*) || '|' || max(details::text) from T.audit_events where event_type='trust_monthly_list.locked' and entity_id='${listId}'`).split("|");
    const L = R.apiList.lines, acme = L.find((l) => l.name === "Acme Corp"), hold = L.find((l) => l.name === state.holdings.client), fund = L.find((l) => l.kind === "FUND");
    Object.assign(R.checks, {
      "a: deposits D2, D3 recorded through the UI (20 + 22 Oct 2026)": d2.dateSql === "2026-10-20" && d3.dateSql === "2026-10-22" && !!d2.postedAt && !!d3.postedAt,
      "a: step 5 'October 2026 list locked — R 16 500,00 agrees with the cashbook and the adjusted bank balance.'": /^October 2026 list locked — R\s?16\s?500,00 agrees with the cashbook and the adjusted bank balance\.$/.test(R.locked),
      "a: one trust_monthly_lists row for (account, 2026-10-31)": qa(`select count(*) from T.trust_monthly_lists where trust_account_id='${state.primary}' and period_end='2026-10-31'`) === "1",
      "a: list row = reconciliation row (adjusted, cashbook, creditors, difference, bank, outstanding deposits)": eq(col.adjusted_bank_balance, rs[0]) && eq(col.cashbook_balance, rs[1]) && eq(col.creditor_total, rs[2]) && eq(col.difference, rs[3]) && eq(col.bank_balance, rs[4]) && eq(col.outstanding_deposits, rs[5]),
      "a: figures 12 500 bank + 4 000 outstanding = 16 500 = cashbook = creditors, difference 0": eq(col.bank_balance, 12500) && eq(col.outstanding_deposits, 4000) && eq(col.adjusted_bank_balance, 16500) && eq(col.cashbook_balance, 16500) && eq(col.creditor_total, 16500),
      "a: creditors — Acme Corp 12 500 (Website Redesign 10 000 + transfer 2 500), Holdings 4 000, Fund line at 0": !!acme && eq(acme.balance, 12500) && acme.matters.length === 2 && acme.matters.some((m) => m.name === "Website Redesign" && eq(m.balance, 10000)) && acme.matters.some((m) => m.name === state.acme.matter2 && eq(m.balance, 2500))
        && !!hold && eq(hold.balance, 4000) && !!fund && eq(fund.balance, 0),
      "a: title 'Trust creditors list — 31 Oct 2026'": R.ui.title === "Trust creditors list — 31 Oct 2026",
      "a: subtitle account · Locked … by member · Rule 54.15": /^Mathebula & Partners Trust Account · Locked .+ by .+ · Rule 54\.15$/.test(R.ui.description),
      "a: 'Nothing has been entered after this list was locked.'": R.ui.afterLock.includes("Nothing has been entered after this list was locked.") && R.ui.afterLockRows.length === 0,
      "a: no flags stored → no 'Flags at completion' section": R.apiList.integrityFlags.length === 0 ? R.ui.flagsSection === 0 : R.ui.flagsSection === 1,
      "a: audit trust_monthly_list.locked once, details carry period_end + creditor_total": ev[0] === "1" && /2026-10-31/.test(ev[1]) && /16500/.test(ev[1]),
      "a: reconciliation list 'List locked' badge for 31 Oct; '---' for 15 Oct": R.list.badge === 1 && R.list.monthlyListCell === "List locked" && R.listB.badge === 0,
      "a: badge link lands on the list page": R.fromListTitle === R.ui.title,
      "a: reconciliation API monthlyListId = the list": R.apiRecon.monthlyListId === listId,
      "a: no raw enum / error on step 5 or list page": R.step5Codes.length === 0 && R.ui.rawCodes.length === 0 && !R.ui.errorState,
    });
    state.lockedBefore = listMoney(listId);
  },

  // c: a deposit back-dated into October after the lock: recorded, shown against the list, list unchanged
  async c() {
    const { p, ctx } = await login();
    const listId = state.listId, before = listMoney(listId);
    listSql(`id='${listId}'`, "c: the list before the back-dated deposit");
    const d4 = (state.d4 = await deposit(p, { client: "Acme Corp", matter: "Website Redesign", amount: 750, ref: `DEP/OBS650A/${RUN}/4`, date: "2026-10-28" }));
    R.d4 = d4;
    const after = listMoney(listId);
    listSql(`id='${listId}'`, "c: the list after the back-dated deposit");
    q(`select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.posted_at, l.locked_at, t.posted_at > l.locked_at posted_after_lock from T.trust_transactions t, T.trust_monthly_lists l where l.id='${listId}' and t.reference='${d4.ref}'`, "c: the back-dated deposit vs the lock");
    R.apiList = (await api(ctx, "GET", `/api/trust-monthly-lists/${listId}`, null, "c-monthly-list")).body;
    R.apiIntegrity = (await api(ctx, "GET", "/api/trust-integrity", null, "c-trust-integrity")).body;
    R.widths = {};
    for (const w of [1280, 1440]) {
      const { ctx: c2, p: p2 } = await login("user_e2e_alice", w);
      await p2.goto(`${BASE}/trust-accounting/reconciliation/lists/${listId}`, { waitUntil: "load" });
      const ui = await readListPage(p2);
      await snap(p2, `list-page-${w}`);
      ui.clip = await clip(p2, '[data-testid="monthly-list-page"]');
      await p2.goto(`${BASE}/trust-accounting`, { waitUntil: "load" });
      const card = p2.getByTestId("trust-integrity-card");
      await card.waitFor({ timeout: 60_000 });
      await snap(p2, `overview-${w}`);
      await snap(p2, `card-${w}`, card);
      ui.cardClip = await clip(p2, '[data-testid="trust-integrity-card"]');
      ui.cardText = flat(await card.innerText());
      ui.cardCodes = await rawCodes(card);
      ui.flag = await p2.getByTestId("integrity-flag-BACKDATED_AFTER_LOCK").evaluateAll((els) => els.map((e) => ({ text: e.innerText.replace(/\s+/g, " "), links: [...e.querySelectorAll("a")].map((a) => ({ text: a.innerText, href: a.getAttribute("href") })) })));
      if (w === 1280 && ui.flag[0]?.links?.length) {
        await p2.getByTestId("integrity-flag-BACKDATED_AFTER_LOCK").getByRole("link", { name: "October 2026 list" }).click();
        await p2.waitForURL(new RegExp(`/reconciliation/lists/${listId}$`), { timeout: 30_000 });
        await p2.getByTestId("monthly-list-page").waitFor({ timeout: 60_000 });
        ui.cardLinkLanded = p2.url();
      }
      R.widths[w] = ui;
      await c2.close();
    }
    const ui = R.widths[1280], adj = R.apiList.adjustmentsSinceLock, entry = adj.entries.find((e) => e.transactionId === d4.id);
    crossCheck("c", ui, R.apiList, listId);
    const flagApi = R.apiIntegrity.accounts.flatMap((a) => a.flags).find((f) => f.code === "BACKDATED_AFTER_LOCK");
    Object.assign(R.checks, {
      "c: back-dated deposit recorded through the UI (28 Oct 2026, posted after the lock)": !!d4.id && d4.dateSql === "2026-10-28" && qa(`select t.posted_at > l.locked_at from T.trust_transactions t, T.trust_monthly_lists l where l.id='${listId}' and t.id='${d4.id}'`) === "t",
      "c: list row money columns, lines, flags, locked_at unchanged (SQL before = after = after a)": before === after && after === state.lockedBefore,
      "c: list page shows it under 'Entered after this list was locked'": ui.afterLockRows.length === 1 && ui.afterLockRows[0].testid === `after-lock-entry-${d4.id}` && ui.afterLockRows[0].cells.includes("Acme Corp") && ui.afterLockRows[0].cells.includes(d4.ref) && eq(money(ui.afterLockRows[0].cells[5]), 750),
      "c: 'now R 17 250,00, a change of R 750,00' = API": /are now R\s?17\s?250,00, a change of R\s?750,00 from the locked list\./.test(ui.afterLock) && eq(adj.liveCreditorTotal, 17250) && eq(adj.changeInCreditorTotal, 750) && !!entry,
      "c: tiles unchanged (16 500 / 16 500 / 16 500 / 0)": eq(ui.tiles["Adjusted bank balance"].value, 16500) && eq(ui.tiles.Cashbook.value, 16500) && eq(ui.tiles["Trust creditors"].value, 16500) && eq(ui.tiles.Difference.value, 0),
      "c: card warning 'Entered after a monthly list was locked' with 'October 2026 list' link": ui.flag.length === 1 && ui.flag[0].text.includes("Entered after a monthly list was locked") && ui.flag[0].links.some((l) => l.text.includes("October 2026 list") && l.href.endsWith(`/reconciliation/lists/${listId}`)),
      "c: card flag = API flag (warning, message)": !!flagApi && flagApi.severity === "WARNING" && ui.flag[0].text.includes(flat(flagApi.message)),
      "c: card link lands on the list page": (ui.cardLinkLanded || "").endsWith(`/reconciliation/lists/${listId}`),
      "c/d: no raw enum / error on list page + card at 1280 and 1440": [1280, 1440].every((w) => R.widths[w].rawCodes.length === 0 && R.widths[w].cardCodes.length === 0 && !R.widths[w].errorState),
      "c/d: no clipped text on list page + card at 1280 and 1440": [1280, 1440].every((w) => !R.widths[w].clip.missing && R.widths[w].clip.bad.length === 0 && !R.widths[w].cardClip.missing && R.widths[w].cardClip.bad.length === 0),
    });
  },

  // g: the Section 35 data pack preview from the Reports page — "Client Trust Balances" lists clients only
  async g() {
    const { p, ctx } = await login();
    R.runs = {};
    for (const fye of ["2026-10-31", "2026-11-18"]) {
      await p.goto(`${BASE}/reports/section-35-data-pack`, { waitUntil: "load" });
      await settle(p); // the form is briefly mounted twice while the route settles
      await p.locator("#param-trust_account_id").locator("visible=true").first().click();
      await p.getByRole("option", { name: /Mathebula & Partners Trust Account/ }).first().click();
      await p.locator("#param-financial_year_end").locator("visible=true").first().fill(fye);
      await p.getByRole("button", { name: "Run Report" }).click();
      await p.getByRole("cell", { name: "Client Trust Balances" }).first().waitFor({ timeout: 60_000 });
      await settle(p);
      await snap(p, `pack-${fye}`);
      const rows = await p.locator("tbody tr").evaluateAll((trs) => trs.map((tr) => [...tr.querySelectorAll("td")].map((td) => td.innerText.trim())));
      const api2 = (await api(ctx, "POST", "/api/report-definitions/section-35-data-pack/execute", { parameters: { trust_account_id: state.primary, financial_year_end: fye }, page: 0, size: 100 }, `g-pack-${fye}`)).body;
      const all = rows.filter((r) => r[0] === "Client Trust Balances"), total = all.find((r) => r[1] === "Total"), cb = all.filter((r) => r !== total);
      R.runs[fye] = { clientRows: cb, totalRow: total, codes: await rawCodes(p.locator("main")) };
      const want = fye === "2026-10-31" ? { "Acme Corp": 12500, [state.holdings.client]: 4000 } : { "Acme Corp": 13250, [state.holdings.client]: 4000 };
      const apiCb = (api2?.rows || []).filter((r) => r._section === "Client Trust Balances" && !r._total);
      R.checks[`g (${fye}): section Total row = sum of the client rows`] = !!total && eq(money(total[2]), Object.values(want).reduce((a, v) => a + v, 0));
      R.checks[`g (${fye}): Client Trust Balances rows are exactly the two clients with their balances`] = cb.length === 2 && Object.entries(want).every(([n, v]) => cb.some((r) => r[1] === n && eq(money(r[2]), v)));
      R.checks[`g (${fye}): no Fund / unidentified / s86(3) line among the clients`] = !cb.some((r) => /Fidelity Fund|Unidentified|86\(3\)/i.test(r[1]));
      R.checks[`g (${fye}): screen rows = API rows`] = apiCb.length === cb.length && apiCb.every((a) => cb.some((r) => r[1] === a.label && eq(money(r[2]) ?? 0, a.amount)));
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
  console.log(JSON.stringify({ phase: PHASE, fatal, checks: R.checks, apiWrites: R.apiWrites.map((a) => `${a.method} ${a.url} → ${a.status}`), consoleErrors: R.consoleErrors.length, pageErrors: R.pageErrors.length }, null, 2));
  process.exit(fatal || Object.values(R.checks).some((v) => !v) ? 1 : 0);
})();
