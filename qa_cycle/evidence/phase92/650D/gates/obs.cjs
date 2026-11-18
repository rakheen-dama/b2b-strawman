// 650D OBS — lockedThrough on trust accounts + the locked-month warning in the five posting dialogs.
// Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081), mock-login UI.
// Every state change goes through the firm UI (deposits, statement, wizard). psql is read-only
// (SELECT); API calls are reads. Phases share obs/state.json; run in the order l w p c.
// Data: the seeded primary GENERAL account (the only one the wizard and Transactions page use).
// Usage: node obs.cjs l|w|p|c
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
const R = { phase: PHASE, startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [], checks: {} };
const saveAll = () => { fs.writeFileSync(STATE, JSON.stringify(state, null, 2)); fs.writeFileSync(path.join(OUT, `result-${PHASE}.json`), JSON.stringify(R, null, 2)); };
const RUN = state.run || (state.run = Date.now().toString(36).slice(-5).toUpperCase());
const M = "2026-10-31", BEFORE = "2026-10-28", AFTER = "2026-11-05";
const WARNING = "This date falls in a month whose list is locked. The entry will be recorded and shown as a later adjustment.";
const CODES = /\b(GENERAL|SECTION_86|INVESTMENT|DEPOSIT|PAYMENT|TRANSFER_IN|TRANSFER_OUT|FEE_TRANSFER|REFUND|RECORDED|AWAITING_APPROVAL|APPROVED|LOCKED|BACKDATED_AFTER_LOCK|WARNING|CLIENT|FUND)\b/g;
const MONEY_COLS = "bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference";
// The five dialogs on the Transactions page: menu item = dialog title = submit label
const DIALOGS = ["Record Deposit", "Record Payment", "Record Transfer", "Record Fee Transfer", "Record Refund"];
const slug = (t) => t.replace(/^Record /, "").toLowerCase().replace(/\s+/g, "-");

// ── read-only SQL ──
const psql = (args) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", ...args], { encoding: "utf8" });
const SCHEMA = psql(["-XAt", "-c", "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"]).trim();
const SQL_OUT = path.join(OUT, `sql-${PHASE}.txt`);
fs.writeFileSync(SQL_OUT, `-- 650D OBS phase ${PHASE} read-only SQL (schema ${SCHEMA}) ${R.startedAt}\n`);
const ro = (sql) => { const s = sql.replace(/\bT\./g, `${SCHEMA}.`); if (!/^\s*(select|with)\b/i.test(s)) throw new Error("read-only SQL only"); return s; };
const q = (sql, label) => { const s = ro(sql); const out = psql(["-X", "-v", "ON_ERROR_STOP=1", "-c", s]); fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${s}\n${out}`); return out; };
const qa = (sql) => psql(["-XAt", "-F", "|", "-c", ro(sql)]).trim();
const eq = (a, b) => a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
const flat = (s) => String(s ?? "").replace(/\s+/g, " ").trim();
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const listMoney = (id) => qa(`select ${MONEY_COLS.split(", ").map((c) => `${c}::text`).join(" || '|' || ")} || '|' || md5(lines::text) || '|' || md5(integrity_flags::text) || '|' || locked_at::text from T.trust_monthly_lists where id='${id}'`);

// ── browser ──
let browser;
async function login(width = 1280) {
  const ctx = await browser.newContext({ viewport: { width, height: 1000 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ url: p.url(), text: m.text().slice(0, 300) }); });
  p.on("pageerror", (e) => R.pageErrors.push({ url: p.url(), text: String(e).slice(0, 300) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
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
    const cut = cs.overflowX !== "visible" && el.scrollWidth > el.clientWidth + 1;
    if (r.left < B.left - 0.5 || r.right > B.right + 0.5 || r.right > vw + 0.5 || cut) bad.push({ text: el.textContent.trim().slice(0, 80), left: r.left, right: r.right, cut });
  }
  const w = root.querySelector('[data-testid="locked-month-warning"]');
  const wr = w?.getBoundingClientRect();
  return { sel, root: [B.left, B.right], vw, textElements: n, bad, warning: wr && { left: wr.left, right: wr.right, top: wr.top, bottom: wr.bottom, scrollW: w.scrollWidth, clientW: w.clientWidth, scrollH: w.scrollHeight, clientH: w.clientHeight } };
}, sel);
const rawCodes = async (loc) => [...new Set(((await loc.innerText()).match(CODES) || []))];
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(1000); };

/** The dialog's warning state as rendered. */
async function warningState(dlg, submitName) {
  const w = dlg.getByTestId("locked-month-warning");
  const n = await w.count();
  return {
    shown: n,
    text: n ? flat(await w.innerText()) : null,
    role: n ? await w.getAttribute("role") : null,
    submitEnabled: await dlg.getByRole("button", { name: submitName, exact: true }).isEnabled(),
  };
}
const dateInput = (dlg, title) => (title === "Record Fee Transfer" ? dlg.getByTestId("fee-transfer-date-input") : dlg.getByLabel("Transaction Date"));
const okShown = (s) => s.shown === 1 && s.text === WARNING && s.role === "status" && s.submitEnabled;
const okHidden = (s) => s.shown === 0 && s.submitEnabled;

/** Open a dialog from the Transactions page menu. */
async function openFromMenu(p, title) {
  await p.getByTestId("record-transaction-button").click();
  await p.getByRole("menuitem", { name: title, exact: true }).click();
  const dlg = p.getByRole("dialog", { name: title });
  await dlg.waitFor();
  return dlg;
}
/** Probe one open dialog at the given dates; screenshot + clip with the warning on. Cancels (submits nothing). */
async function probe(p, dlg, title, dates, tag) {
  const out = { initial: await warningState(dlg, title), initialDate: await dateInput(dlg, title).inputValue() };
  for (const d of dates) {
    await dateInput(dlg, title).fill(d);
    out[d] = await warningState(dlg, title);
  }
  if (tag) {
    await dateInput(dlg, title).fill(BEFORE);
    out.codes = await rawCodes(dlg);
    out.shot = await snap(p, tag, dlg);
    out.clip = await clip(p, '[role="dialog"]');
  }
  await dlg.getByRole("button", { name: "Cancel" }).click();
  await dlg.waitFor({ state: "hidden" });
  return out;
}
/** Record Deposit / Payment / Refund on the Transactions page (client + matter pickers). */
async function post(p, title, d) {
  await p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
  const dlg = await openFromMenu(p, title);
  const k = slug(title);
  await dlg.getByTestId(`trust-${k}-customer-trigger`).click();
  await p.getByPlaceholder("Search clients...").fill(d.client);
  await p.getByRole("option", { name: new RegExp(esc(d.client)) }).first().click();
  await dlg.getByTestId(`trust-${k}-matter-trigger`).click();
  await p.getByPlaceholder("Search matters...").fill(d.matter);
  await p.getByRole("option", { name: new RegExp(esc(d.matter)) }).first().click();
  await dlg.getByLabel("Amount", { exact: true }).fill(String(d.amount));
  await dlg.getByLabel("Reference", { exact: true }).fill(d.ref);
  await dlg.getByLabel("Transaction Date").fill(d.date);
  const before = await warningState(dlg, title);
  if (d.shot) await snap(p, d.shot, dlg);
  await dlg.getByRole("button", { name: title, exact: true }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await settle(p);
  const row = qa(`select id || '|' || status || '|' || amount || '|' || transaction_date || '|' || coalesce(posted_at::text,'') from T.trust_transactions where reference='${d.ref}'`).split("|");
  return { ...d, warningBeforeSubmit: before, id: row[0], status: row[1], amountSql: +row[2], dateSql: row[3], postedAt: row[4] };
}

// ── phases ──
const phases = {
  // l: (a) before the lock no warning anywhere and lockedThrough null; (b) a month-end reconciliation to M locks a list
  async l() {
    const { p, ctx } = await login();
    state.primary = qa("select id from T.trust_accounts where account_type='GENERAL' and is_primary");
    q(`select id, account_name, account_type, is_primary from T.trust_accounts`, "l: trust accounts");
    q(`select (select count(*) from T.trust_transactions) postings, (select count(*) from T.trust_monthly_lists) lists, (select count(*) from T.trust_reconciliations) recons`, "l: empty before the run");
    const before = (await api(ctx, `/api/trust-accounts/${state.primary}`, "l-account-before")).body;
    const listBefore = (await api(ctx, "/api/trust-accounts", "l-accounts-before")).body;
    await p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
    R.noLock = {};
    for (const t of DIALOGS) R.noLock[t] = await probe(p, await openFromMenu(p, t), t, [BEFORE, M]);
    await p.getByTestId("record-transaction-button").waitFor();
    await snap(p, "01-transactions-before-lock");
    const d1 = (state.d1 = await post(p, "Record Deposit", { client: "Acme Corp", matter: "Website Redesign", amount: 10000, ref: `DEP/OBS650D/${RUN}/1`, date: "2026-10-05" }));
    // the statement: one line, the deposit; reconcile to M (month end → locks the October list)
    const csv = path.join(OUT, `l-statement-${RUN}.csv`);
    fs.writeFileSync(csv, [`Standard Bank Trust Account - OBS 650D ${RUN}`, "Date,Description,Reference,Amount,Balance", `2026-10-05,EFT CREDIT ACME CORP,${d1.ref},10000.00,10000.00`].join("\n") + "\n");
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
    await snap(p, "02-review");
    await p.getByTestId("complete-reconciliation-btn").click();
    await p.getByTestId("step-complete").waitFor({ timeout: 30_000 });
    await settle(p);
    R.locked = flat(await p.getByTestId("monthly-list-locked").innerText().catch(() => ""));
    await snap(p, "03-step5-locked");
    state.listId = qa(`select id from T.trust_monthly_lists where trust_account_id='${state.primary}' and period_end='${M}'`);
    state.lockedBefore = listMoney(state.listId);
    const after = (await api(ctx, `/api/trust-accounts/${state.primary}`, "l-account-after")).body;
    const listAfter = (await api(ctx, "/api/trust-accounts", "l-accounts-after")).body;
    q(`select trust_account_id, max(period_end) locked_through, count(*) lists from T.trust_monthly_lists group by 1`, "l: lockedThrough per account (SQL)");
    q(`select id, period_end, reconciliation_id, ${MONEY_COLS}, locked_by, locked_at from T.trust_monthly_lists`, "l: the locked list");
    const sqlMax = qa(`select max(period_end) from T.trust_monthly_lists where trust_account_id='${state.primary}'`);
    Object.assign(R.checks, {
      "a: GET account before lock → lockedThrough key present, null": "lockedThrough" in before && before.lockedThrough === null,
      "a: GET accounts before lock → every element has lockedThrough null": Array.isArray(listBefore) && listBefore.length > 0 && listBefore.every((x) => "lockedThrough" in x && x.lockedThrough === null),
      "a: no dialog shows the warning before the lock (dates 2026-10-28, 2026-10-31, default)": DIALOGS.every((t) => okHidden(R.noLock[t].initial) && okHidden(R.noLock[t][BEFORE]) && okHidden(R.noLock[t][M])),
      "b: deposit D1 recorded through the UI (5 Oct 2026)": d1.status === "RECORDED" && d1.dateSql === "2026-10-05",
      "b: step 5 'October 2026 list locked …'": /^October 2026 list locked — R\s?10\s?000,00 agrees/.test(R.locked),
      "b: GET account after → lockedThrough = 2026-10-31": after.lockedThrough === M,
      "b: GET accounts after → primary lockedThrough = 2026-10-31, others null": listAfter.every((x) => (x.id === state.primary ? x.lockedThrough === M : x.lockedThrough === null)),
      "b: SQL max(period_end) for the primary = API lockedThrough": sqlMax === M && sqlMax === after.lockedThrough,
    });
  },

  // w: (c) each dialog on the Transactions page: before M / M / after M; (f) 1280 + 1440 shots and clip probe
  async w() {
    R.widths = {};
    for (const w of [1280, 1440]) {
      const { p, ctx } = await login(w);
      await p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
      R.widths[w] = {};
      for (const t of DIALOGS) R.widths[w][t] = await probe(p, await openFromMenu(p, t), t, [BEFORE, M, AFTER], `${slug(t)}-warning-${w}`);
      if (w === 1280) R.apiAccount = (await api(ctx, `/api/trust-accounts/${state.primary}`, "w-account")).body;
      await ctx.close();
    }
    const r = R.widths[1280];
    for (const t of DIALOGS) {
      R.checks[`c: ${t} — 2026-10-28 shows the exact warning (role=status), submit enabled`] = okShown(r[t][BEFORE]);
      R.checks[`c: ${t} — 2026-10-31 (boundary) shows it`] = okShown(r[t][M]);
      R.checks[`c: ${t} — 2026-11-05 hides it, submit enabled`] = okHidden(r[t][AFTER]);
      R.checks[`c: ${t} — default date shows no warning`] = okHidden(r[t].initial);
      R.checks[`c: ${t} — no raw enum codes in the dialog`] = [1280, 1440].every((w) => R.widths[w][t].codes.length === 0);
      R.checks[`f: ${t} — no clipped text, warning inside the dialog at 1280 and 1440`] = [1280, 1440].every((w) => {
        const c = R.widths[w][t].clip;
        return !c.missing && c.bad.length === 0 && !!c.warning && c.warning.left >= c.root[0] - 0.5 && c.warning.right <= c.root[1] + 0.5 && c.warning.scrollW <= c.warning.clientW + 1 && c.warning.scrollH <= c.warning.clientH + 1;
      });
    }
    R.checks["c: Record Fee Transfer — blank date (today) shows no warning"] = r["Record Fee Transfer"].initialDate === "" && okHidden(r["Record Fee Transfer"].initial);
    R.checks["c: the warning keys on API lockedThrough 2026-10-31"] = R.apiAccount.lockedThrough === M;
  },

  // p: (d) a deposit dated before M records with no refusal; the list is unchanged; shown after the lock
  async p() {
    const { p, ctx } = await login();
    const listId = state.listId, before = listMoney(listId);
    q(`select id, period_end, ${MONEY_COLS}, md5(lines::text) lines_md5, md5(integrity_flags::text) flags_md5, locked_at from T.trust_monthly_lists where id='${listId}'`, "p: the list before the back-dated postings");
    const d2 = (state.d2 = await post(p, "Record Deposit", { client: "Acme Corp", matter: "Website Redesign", amount: 750, ref: `DEP/OBS650D/${RUN}/BACK`, date: BEFORE, shot: "01-deposit-before-submit" }));
    const p1 = (state.p1 = await post(p, "Record Payment", { client: "Acme Corp", matter: "Website Redesign", amount: 100, ref: `PAY/OBS650D/${RUN}/BACK`, date: "2026-10-29" }));
    const after = listMoney(listId);
    q(`select id, period_end, ${MONEY_COLS}, md5(lines::text) lines_md5, md5(integrity_flags::text) flags_md5, locked_at from T.trust_monthly_lists where id='${listId}'`, "p: the list after the back-dated postings");
    q(`select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.reference, t.posted_at, l.locked_at, t.posted_at > l.locked_at posted_after_lock from T.trust_transactions t, T.trust_monthly_lists l where l.id='${listId}' and t.trust_account_id='${state.primary}' order by t.posted_at`, "p: postings vs the lock");
    const apiList = (await api(ctx, `/api/trust-monthly-lists/${listId}`, "p-monthly-list")).body;
    const acct = (await api(ctx, `/api/trust-accounts/${state.primary}`, "p-account")).body;
    await p.goto(`${BASE}/trust-accounting/reconciliation/lists/${listId}`, { waitUntil: "load" });
    await p.getByTestId("monthly-list-page").waitFor({ timeout: 60_000 });
    await settle(p);
    await snap(p, "02-list-page");
    R.afterLock = flat(await p.getByTestId("monthly-list-after-lock").innerText());
    R.afterLockRows = await p.locator('[data-testid^="after-lock-entry-"]').evaluateAll((els) => els.map((e) => ({ testid: e.dataset.testid, cells: [...e.querySelectorAll("td")].map((td) => td.innerText.trim()) })));
    R.listCodes = await rawCodes(p.locator("main"));
    R.d2 = d2; R.p1 = p1;
    const ids = (apiList.adjustmentsSinceLock?.entries || []).map((e) => e.transactionId);
    const postedAfter = (id) => qa(`select t.posted_at > l.locked_at from T.trust_transactions t, T.trust_monthly_lists l where l.id='${listId}' and t.id='${id}'`) === "t";
    Object.assign(R.checks, {
      "d: warning visible in the deposit dialog before submit, submit enabled": okShown(d2.warningBeforeSubmit),
      "d: deposit 2026-10-28 R 750 recorded (RECORDED, posted after the lock)": d2.status === "RECORDED" && d2.dateSql === BEFORE && eq(d2.amountSql, 750) && postedAfter(d2.id),
      "d: payment 2026-10-29 R 100 recorded (a row exists, no refusal)": !!p1.id && ["RECORDED", "AWAITING_APPROVAL", "APPROVED"].includes(p1.status) && p1.dateSql === "2026-10-29" && okShown(p1.warningBeforeSubmit),
      "d: locked list row unchanged (12 money columns, md5 lines/flags, locked_at)": before === after && after === state.lockedBefore,
      "d: list page 'Entered after this list was locked' shows the deposit": R.afterLockRows.some((r) => r.testid === `after-lock-entry-${d2.id}` && r.cells.includes(d2.ref)),
      "d: API adjustmentsSinceLock.entries contains the deposit": ids.includes(d2.id),
      "d: lockedThrough still 2026-10-31": acct.lockedThrough === M,
      "d: no raw enum on the list page": R.listCodes.length === 0,
    });
  },

  // c: (e) the TrustBalanceCard mount site — client page Trust tab quick actions
  async c() {
    const { p } = await login();
    state.acmeId = qa("select id from T.customers where name='Acme Corp'");
    await p.goto(`${BASE}/clients/${state.acmeId}?tab=trust`, { waitUntil: "load" });
    const card = p.getByTestId("trust-balance-card");
    await card.waitFor({ timeout: 60_000 });
    await card.getByRole("button", { name: "Record Deposit" }).waitFor({ timeout: 60_000 });
    await snap(p, "01-client-trust-tab");
    R.card = {};
    for (const [btn, title] of [["Record Deposit", "Record Deposit"], ["Record Payment", "Record Payment"], ["Fee Transfer", "Record Fee Transfer"]]) {
      await card.getByRole("button", { name: btn, exact: true }).click();
      const dlg = p.getByRole("dialog", { name: title });
      await dlg.waitFor();
      R.card[title] = await probe(p, dlg, title, [BEFORE, M, AFTER, "2026-10-30"], `card-${slug(title)}-warning`);
    }
    const d = R.card["Record Deposit"], f = R.card["Record Fee Transfer"], y = R.card["Record Payment"];
    Object.assign(R.checks, {
      "e: card Record Deposit — 2026-10-28 and 2026-10-31 show the warning; 2026-11-05 hides it": okShown(d[BEFORE]) && okShown(d[M]) && okHidden(d[AFTER]),
      "e: card Record Payment — same": okShown(y[BEFORE]) && okShown(y[M]) && okHidden(y[AFTER]),
      "e: card Fee Transfer — blank shows none; 2026-10-30 shows it": f.initialDate === "" && okHidden(f.initial) && okShown(f["2026-10-30"]),
      "e: no raw enum codes in the card dialogs": Object.values(R.card).every((x) => x.codes.length === 0),
    });
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
