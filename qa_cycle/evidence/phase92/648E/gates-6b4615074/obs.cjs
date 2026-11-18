// 648E OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Adapted from 648A gates-b6afe8c69/obs.cjs. Every state change goes through the UI: Alice (owner) places the
// section 86(4) investment on Trust › Investments (into the lifecycle's existing s86(4) investment account) and
// withdraws it; Bob (admin) approves the placement on Trust › Transactions. psql is read-only EVIDENCE. API reads use
// the browser context's own mock-auth-token against :8081. No API writes.
// Steps: (a) QuickCollect after the lifecycle; (c) Moroka baseline (s86(3) placement names no client);
// (d) client cards on the investment account; (b) Moroka s86(4) R7 500 pending → approved → withdrawn;
// (f) client Trust tab at every state; (g) raw-enum substring scans; (h) clip probes; (i) console / page errors.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648E";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const { measureIn } = require("./measure.cjs");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const ORG = `${BASE}/org/e2e-test-org`;
const EV = `${W}/qa_cycle/evidence/phase92/648E/gates-6b4615074`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const P = 7500;
const result = { steps: {}, consoleErrors: [], pageErrors: [], posts: [], notes: [], scans: [], clips: {}, startedAt: new Date().toISOString() };
const save = () => fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;

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
fs.writeFileSync(SQL_FILE, `-- 648E OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 648E OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
// SQL derivation of one client's card on account A (brief §8.4; qualified columns — 648A H-1)
function sqlCard(label, client, account) {
  const general = num(val(`select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t where t.trust_account_id='${account}' and t.customer_id='${client}' and t.ledger='CLIENT' and t.posted_at is not null`));
  const invested = num(val(`select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t join T.trust_investments i on i.id=t.investment_id where i.trust_account_id='${account}' and t.trust_account_id=i.investment_account_id and t.customer_id='${client}' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null`));
  const byType = Object.fromEntries(rowsA(`select t.transaction_type, sum(t.amount), sum(t.ledger_effect) from T.trust_transactions t where t.trust_account_id='${account}' and t.customer_id='${client}' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1`).map((r) => [r[0], { amount: num(r[1]), effect: num(r[2]) }]));
  const amt = (k) => (byType[k] ? byType[k].amount : 0);
  sql(`${label}: general (CLIENT on the account)`, `select coalesce(sum(t.ledger_effect),0) general from T.trust_transactions t where t.trust_account_id='${account}' and t.customer_id='${client}' and t.ledger='CLIENT' and t.posted_at is not null`);
  sql(`${label}: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)`, `select coalesce(sum(t.ledger_effect),0) invested from T.trust_transactions t join T.trust_investments i on i.id=t.investment_id where i.trust_account_id='${account}' and t.trust_account_id=i.investment_account_id and t.customer_id='${client}' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null`);
  sql(`${label}: CLIENT-ledger totals by type on the account (posted)`, `select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from T.trust_transactions t where t.trust_account_id='${account}' and t.customer_id='${client}' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1`);
  sql(`${label}: the client's rows by account and ledger (posted)`, `select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.customer_id='${client}' and t.posted_at is not null group by 1,2 order by 1,2`);
  return { general, invested, held: general + invested, byType,
    deposits: amt("DEPOSIT") + amt("UNIDENT_ALLOC_IN"), transfersIn: amt("TRANSFER_IN"), payments: amt("PAYMENT"), feeTransfers: amt("FEE_TRANSFER"),
    refunds: amt("REFUND"), transfersOut: amt("TRANSFER_OUT"), placements: amt("INV_PLACE"), returns: amt("INV_RETURN") };
}

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
// API: the client's card + its row in the list + Σ s86(4) investment balances placed from the account (AC-4)
async function apiCard(ctx, key, client, account) {
  const card = await apiGet(ctx, `/api/trust-accounts/${account}/client-ledgers/${client}`, `${key}-card`);
  const list = await apiGet(ctx, `/api/trust-accounts/${account}/client-ledgers?page=0&size=100`, `${key}-list`);
  const rows = (list.body && (list.body.content || list.body)) || [];
  const row = Array.isArray(rows) ? rows.find((x) => x.clientId === client) : null;
  const inv = await apiGet(ctx, `/api/trust-accounts/${account}/investments?page=0&size=100`, `${key}-investments`);
  const invRows = (inv.body && (inv.body.content || inv.body)) || [];
  const mine = Array.isArray(invRows) ? invRows.filter((x) => x.clientId === client && x.investmentBasis === "CLIENT_INSTRUCTION") : [];
  const b = card.body || {};
  const pick = (o) => o && ({ balance: Number(o.balance), investedBalance: Number(o.investedBalance), totalDeposits: Number(o.totalDeposits), totalPayments: Number(o.totalPayments),
    totalFeeTransfers: Number(o.totalFeeTransfers), totalRefunds: Number(o.totalRefunds), totalTransfersIn: Number(o.totalTransfersIn), totalTransfersOut: Number(o.totalTransfersOut),
    totalInvestmentPlacements: Number(o.totalInvestmentPlacements), totalInvestmentReturns: Number(o.totalInvestmentReturns) });
  return { status: card.status, card: pick(b), listStatus: list.status, listRow: pick(row), listRowPresent: !!row,
    investmentsStatus: inv.status, s864FromAccount: mine.map((x) => ({ id: x.id, status: x.status, balance: Number(x.balance) })),
    sumInvestmentBalances: mine.reduce((s, x) => s + Number(x.balance || 0), 0) };
}
const ROW_KEYS = ["deposits", "transfers-in", "investment-returns", "payments", "fee-transfers", "refunds", "transfers-out", "investment-placements", "balance", "invested", "held"];
async function readBreakdown(scope) {
  const out = {};
  for (const k of ROW_KEYS) {
    const row = scope.getByTestId(`ledger-total-${k}`);
    const n = await row.count();
    if (!n) { out[k] = null; continue; }
    const text = norm(await row.first().innerText());
    const dt = norm(await row.first().locator("dt").innerText().catch(() => ""));
    const dd = norm(await row.first().locator("dd").innerText().catch(() => ""));
    out[k] = { text, dt, dd, amount: money(dd) };
  }
  return out;
}
// substring scan (648A H-2): case-sensitive substring search of textContent
const RAW = ["CLIENT_INVESTMENT", "INV_PLACE", "INV_RETURN", "INV_RECEIVE", "INV_WITHDRAW"];
async function scanRaw(locator, label) {
  const content = await locator.evaluate((el) => el.textContent || "");
  const r = { label, hits: RAW.filter((t) => content.includes(t)) };
  r.clean = r.hits.length === 0;
  fs.writeFileSync(`${OUT}/scan-${label}.txt`, content);
  result.scans.push(r);
  return r;
}
async function clip(p, label, rootSel, widths = [1280, 1440, 1920]) {
  const res = [];
  for (const w of widths) {
    await p.setViewportSize({ width: w, height: 1100 });
    await p.waitForTimeout(600);
    const m = await p.evaluate(measureIn, { rootSel, opts: {} });
    m.width = w;
    m.page = await p.evaluate(() => ({ docScrollWidth: document.documentElement.scrollWidth, docClientWidth: document.documentElement.clientWidth,
      pageHorizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1 }));
    m.figures = await p.evaluate((sel) => {
      const root = document.querySelector(sel); if (!root) return [];
      const vw = document.documentElement.clientWidth;
      return [...root.querySelectorAll("dd, [data-testid='client-summary-invested'], .text-2xl, [data-testid='trust-balance-primary']")].map((e) => {
        const r = e.getBoundingClientRect();
        return { text: (e.innerText || "").replace(/\s+/g, " ").trim().slice(0, 90), left: Math.round(r.left), right: Math.round(r.right), insideViewport: r.left >= -0.5 && r.right <= vw + 0.5,
          clipped: e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflowX !== "visible" };
      });
    }, rootSel);
    m.figuresOk = m.figures.every((f) => f.insideViewport && !f.clipped);
    res.push(m);
    const el = p.locator(rootSel).first();
    if (await el.count()) await el.screenshot({ path: `${OUT}/h-${label}-${w}-crop.png` }).catch(() => {});
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(300);
  result.clips[label] = res.map((m) => ({ width: m.width, ok: m.ok, figuresOk: m.figuresOk, pageHorizontalScroll: m.page.pageHorizontalScroll, root: m.root,
    offenders: m.offenders, truncated: m.truncated, figures: m.figures }));
  return res;
}

// ── UI reads ────────────────────────────────────────────────────────
// The firm's client-ledger detail page (primary account — page.tsx accounts.find(isPrimary))
async function ledgerPageUi(p, key, client, label, { clips = false } = {}) {
  await p.goto(`${ORG}/trust-accounting/client-ledgers/${client}`, { waitUntil: "load" });
  const summary = p.getByTestId("client-summary");
  await summary.waitFor({ timeout: 60_000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  const u = { summaryText: norm(await summary.innerText()) };
  u.trustBalanceFigure = money(await summary.locator(".text-2xl").first().innerText().catch(() => ""));
  u.summaryInvested = (await summary.getByTestId("client-summary-invested").count()) ? norm(await summary.getByTestId("client-summary-invested").innerText()) : null;
  u.summaryInvestedAmounts = u.summaryInvested ? amounts(u.summaryInvested) : null;
  u.rows = await readBreakdown(summary);
  u.hasInvestedLabel = u.summaryText.includes("Invested (section 86(4))");
  u.hasHeldLabel = u.summaryText.includes("Total held in trust");
  u.files = await snap(p, `${key}-client-ledger-page-${label}`);
  await crop(summary, `${key}-client-summary-${label}-crop`);
  u.scan = await scanRaw(p.locator("main").first(), `${key}-client-ledger-page-${label}`);
  if (clips) {
    await clip(p, `${key}-client-summary-${label}`, "[data-testid='client-summary']");
    await clip(p, `${key}-breakdown-${label}`, "[data-testid='client-summary'] [data-testid='ledger-totals-breakdown']");
  }
  return u;
}
// The client's Trust tab (TrustBalanceCard client mode; shares LedgerTotalsBreakdown)
async function trustTabUi(p, key, client, label, { clips = false } = {}) {
  await p.goto(`${ORG}/clients/${client}?tab=trust`, { waitUntil: "load" });
  const card = p.getByTestId("trust-balance-card");
  await card.waitFor({ timeout: 60_000 });
  await p.getByTestId("trust-balance-primary").or(card.getByText(/Unable to load|No trust/)).first().waitFor({ timeout: 30_000 }).catch(() => {});
  await card.getByTestId("ledger-totals-breakdown").waitFor({ timeout: 15_000 }).catch(() => {});
  await p.waitForTimeout(1200);
  const u = { cardText: norm(await card.innerText()) };
  u.headline = (await p.getByTestId("trust-balance-primary").count()) ? money(await p.getByTestId("trust-balance-primary").innerText()) : null;
  u.badges = (await card.locator("[data-slot='badge']").allInnerTexts()).map(norm);
  u.rows = await readBreakdown(card);
  u.files = await snap(p, `${key}-client-trust-tab-${label}`);
  await crop(card, `${key}-client-trust-card-${label}-crop`);
  u.scan = await scanRaw(card, `${key}-client-trust-tab-${label}`);
  if (clips) await clip(p, `${key}-trust-tab-card-${label}`, "[data-testid='trust-balance-card']");
  return u;
}
// UI = API = SQL for one state
function agree(ui, tab, api, s) {
  const r = ui.rows, t = tab.rows, c = api.card;
  const has = (o, k) => o[k] !== null;
  const checks = {
    apiStatus200: api.status === 200,
    apiGeneralEqSql: eq(c.balance, s.general), apiInvestedEqSql: eq(c.investedBalance, s.invested),
    apiPlacementsEqSql: eq(c.totalInvestmentPlacements, s.placements), apiReturnsEqSql: eq(c.totalInvestmentReturns, s.returns),
    apiTransfersOutEqSql: eq(c.totalTransfersOut, s.transfersOut), apiDepositsEqSql: eq(c.totalDeposits, s.deposits),
    apiListRowEqCard: api.listRowPresent && ["balance", "investedBalance", "totalInvestmentPlacements", "totalInvestmentReturns"].every((k) => eq(api.listRow[k], c[k])),
    apiInvestedEqSumInvestmentBalances: eq(c.investedBalance, api.sumInvestmentBalances),
    apiIdentity: eq(c.totalDeposits + c.totalTransfersIn + c.totalInvestmentReturns - c.totalPayments - c.totalFeeTransfers - c.totalRefunds - c.totalTransfersOut - c.totalInvestmentPlacements, c.balance),
    uiSummaryFigureEqApi: eq(ui.trustBalanceFigure, c.balance),
    uiBalanceRowEqApi: has(r, "balance") && eq(r.balance.amount, c.balance),
    uiBalanceLabel: has(r, "balance") && (c.investedBalance !== 0 ? /General balance$/.test(r.balance.dt) : /Balance$/.test(r.balance.dt) && !/General/.test(r.balance.dt)),
    uiInvestedRow: c.investedBalance !== 0 ? has(r, "invested") && eq(r.invested.amount, c.investedBalance) : !has(r, "invested") && !ui.hasInvestedLabel,
    uiHeldRow: c.investedBalance !== 0 ? has(r, "held") && eq(r.held.amount, c.balance + c.investedBalance) : !has(r, "held") && !ui.hasHeldLabel,
    uiSummaryLine: c.investedBalance !== 0 ? !!ui.summaryInvested && eq(ui.summaryInvestedAmounts[0], c.investedBalance) && eq(ui.summaryInvestedAmounts[1], c.balance + c.investedBalance) : ui.summaryInvested === null,
    uiPlacementsRow: c.totalInvestmentPlacements !== 0 ? has(r, "investment-placements") && eq(r["investment-placements"].amount, c.totalInvestmentPlacements) : !has(r, "investment-placements"),
    uiReturnsRow: c.totalInvestmentReturns !== 0 ? has(r, "investment-returns") && eq(r["investment-returns"].amount, c.totalInvestmentReturns) : !has(r, "investment-returns"),
    uiTransfersOutRow: c.totalTransfersOut !== 0 ? has(r, "transfers-out") && eq(r["transfers-out"].amount, c.totalTransfersOut) : !has(r, "transfers-out"),
    tabBreakdownEqPage: ROW_KEYS.every((k) => (r[k] === null) === (t[k] === null) && (r[k] === null || eq(r[k].amount, t[k].amount))),
    noRawEnum: ui.scan.clean && tab.scan.clean,
  };
  return checks;
}
const allTrue = (o) => Object.values(o || {}).every(Boolean);

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
  await dlg.getByLabel("Notes (optional)").fill(`OBS ${RUN} 648E s86(4)`);
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
async function withdrawViaUi(p, depositNumber) {
  const s = {};
  await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  const row = p.locator("main tbody tr").filter({ hasText: depositNumber }).first();
  await row.waitFor({ timeout: 30_000 });
  s.rowBefore = norm(await row.innerText());
  await row.getByRole("button", { name: "Withdraw" }).click();
  const wd = p.getByTestId("withdraw-investment-dialog");
  await wd.waitFor({ timeout: 15_000 });
  await p.waitForTimeout(400);
  s.amountText = norm(await wd.getByTestId("withdrawal-amount").innerText());
  await crop(wd, "b5-withdraw-dialog-crop");
  const nTx = Number(val("select count(*) from T.trust_transactions"));
  await wd.getByRole("button", { name: "Confirm Withdrawal" }).click();
  await wd.waitFor({ state: "hidden", timeout: 30_000 });
  s.txCountBefore = nTx;
  s.txCountAfter = Number(await waitSql("select count(*) from T.trust_transactions", (v) => Number(v) >= nTx + 2));
  return s;
}
async function state(key, label, client, opts = {}) {
  const st = { label };
  st.sql = sqlCard(`${key} ${label}`, client, ids.funding);
  st.api = await apiCard(S.alice.ctx, `${key}-${label}`, client, ids.funding);
  st.ui = await ledgerPageUi(S.alice.p, key, client, label, opts);
  st.tab = await trustTabUi(S.alice.p, key, client, label, opts);
  st.checks = agree(st.ui, st.tab, st.api, st.sql);
  st.pass = allTrue(st.checks);
  return st;
}

// ── main ────────────────────────────────────────────────────────────
(async () => {
  browser = await chromium.launch({ headless: true });
  await newSession("alice", "user_e2e_alice");
  await newSession("bob", "user_e2e_bob");
  ids.funding = val("select id from T.trust_accounts where account_type<>'INVESTMENT' and is_primary order by created_at limit 1");
  ids.fundingName = val(`select account_name from T.trust_accounts where id='${ids.funding}'`);
  ids.qc = val("select id from T.customers where name ilike 'QuickCollect%' order by created_at limit 1");
  ids.qcName = val(`select name from T.customers where id='${ids.qc}'`);
  ids.moroka = val("select id from T.customers where name ilike 'Moroka%' order by created_at limit 1");
  ids.morokaName = val(`select name from T.customers where id='${ids.moroka}'`);
  ids.morokaMatter = val(`select project_id from T.trust_transactions where customer_id='${ids.moroka}' and trust_account_id='${ids.funding}' and ledger='CLIENT' and posted_at is not null and project_id is not null group by 1 order by sum(ledger_effect) desc limit 1`);
  ids.morokaMatterName = val(`select name from T.projects where id='${ids.morokaMatter}'`);
  ids.inv4 = val("select id from T.trust_accounts where account_type='INVESTMENT' and investment_basis='CLIENT_INSTRUCTION' order by created_at limit 1");
  ids.inv4Name = val(`select account_name from T.trust_accounts where id='${ids.inv4}'`);
  ids.inv3 = val("select id from T.trust_accounts where account_type='INVESTMENT' and investment_basis='FIRM_DISCRETION' order by created_at limit 1");
  result.ids = ids;
  sql("context: trust accounts", "select id, account_name, account_type, investment_basis, is_primary, status from T.trust_accounts order by created_at");
  sql("context: investments after the lifecycle", "select i.id, i.account_number, i.investment_basis, i.status, i.principal, c.name client, fa.account_name funding, ia.account_name holding from T.trust_investments i left join T.customers c on c.id=i.customer_id join T.trust_accounts fa on fa.id=i.trust_account_id join T.trust_accounts ia on ia.id=i.investment_account_id order by i.created_at");
  sql("context: investment rows after the lifecycle", "select i.account_number, t.transaction_type, t.status, t.ledger, a.account_name, coalesce(c.name,'<no client>') client, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from T.trust_transactions t join T.trust_investments i on i.id=t.investment_id join T.trust_accounts a on a.id=t.trust_account_id left join T.customers c on c.id=t.customer_id order by t.created_at");
  save();

  // ── a: QuickCollect after the lifecycle's s86(4) R30 000 (approved on day 60) ──
  const a = (result.steps.a = await state("a", "quickcollect-after-lifecycle", ids.qc, { clips: true }));
  a.observed = { general: a.api.card.balance, invested: a.api.card.investedBalance, held: a.api.card.balance + a.api.card.investedBalance,
    placements: a.api.card.totalInvestmentPlacements, returns: a.api.card.totalInvestmentReturns, transfersOut: a.api.card.totalTransfersOut, deposits: a.api.card.totalDeposits,
    uiPlacements: a.ui.rows["investment-placements"] && a.ui.rows["investment-placements"].text, uiBalance: a.ui.rows.balance && a.ui.rows.balance.text,
    uiInvested: a.ui.rows.invested && a.ui.rows.invested.text, uiHeld: a.ui.rows.held && a.ui.rows.held.text, uiSummaryLine: a.ui.summaryInvested };
  a.briefFigures = { general: 15000, invested: 30000, held: 45000, placements: 30000 };
  a.matchesBriefFigures = eq(a.observed.general, 15000) && eq(a.observed.invested, 30000) && eq(a.observed.held, 45000) && eq(a.observed.placements, 30000);
  a.transfersOutExcludesPlacement = eq(a.api.card.totalTransfersOut, a.sql.transfersOut) && eq(a.sql.placements, a.api.card.totalInvestmentPlacements);
  a.f4 = { headline: a.tab.headline, badges: a.tab.badges, heldOnBreakdown: a.tab.rows.held && a.tab.rows.held.amount, note: "F-4: headline/badge read ledger.balance (general) — recorded, not judged" };
  save();

  // ── c: Moroka baseline — the lifecycle's s86(3) placement names no client; Moroka's card has nothing invested ──
  const c = (result.steps.c = await state("c", "moroka-baseline", ids.moroka));
  c.s863Rows = rowsA(`select t.transaction_type, t.ledger, coalesce(t.customer_id::text,'<null>'), t.posted_at is not null from T.trust_transactions t join T.trust_investments i on i.id=t.investment_id where i.investment_basis='FIRM_DISCRETION' order by t.created_at`);
  sql("c: s86(3) investment rows (expect INTER_ACCOUNT, customer_id NULL)", "select i.account_number, i.customer_id is null inv_customer_null, t.transaction_type, t.ledger, t.customer_id is null tx_customer_null, t.ledger_effect, t.posted_at is not null posted from T.trust_transactions t join T.trust_investments i on i.id=t.investment_id where i.investment_basis='FIRM_DISCRETION' order by t.created_at");
  c.specific = { investedZero: eq(c.api.card.investedBalance, 0), placementsZero: eq(c.api.card.totalInvestmentPlacements, 0), noInvestedRowsOnPage: c.ui.rows.invested === null && c.ui.rows.held === null && !c.ui.hasInvestedLabel,
    noInvestedRowsOnTab: c.tab.rows.invested === null && c.tab.rows.held === null, s863HasRows: c.s863Rows.length >= 2, s863NoClient: c.s863Rows.every((r) => r[1] === "INTER_ACCOUNT" && r[2] === "<null>") };
  save();

  // ── d: the clients' cards on the s86(4) INVESTMENT account (UI exposes only the primary account) ──
  const d = (result.steps.d = {});
  d.uiExposesInvestmentAccountLedgers = false;
  d.uiNote = "client-ledgers list and detail pages resolve accounts.find(a => a.isPrimary) ?? accounts[0] (page.tsx:117, list page.tsx:57); no account picker — API evidence only";
  d.qc = await apiGet(S.alice.ctx, `/api/trust-accounts/${ids.inv4}/client-ledgers/${ids.qc}`, "d-inv4-card-quickcollect");
  d.moroka = await apiGet(S.alice.ctx, `/api/trust-accounts/${ids.inv4}/client-ledgers/${ids.moroka}`, "d-inv4-card-moroka");
  d.list = await apiGet(S.alice.ctx, `/api/trust-accounts/${ids.inv4}/client-ledgers?page=0&size=100`, "d-inv4-list");
  const dRows = (d.list.body && (d.list.body.content || d.list.body)) || [];
  d.summary = { qc: { status: d.qc.status, balance: d.qc.body.balance, investedBalance: d.qc.body.investedBalance }, moroka: { status: d.moroka.status, balance: d.moroka.body.balance, investedBalance: d.moroka.body.investedBalance },
    listStatus: d.list.status, listRows: Array.isArray(dRows) ? dRows.map((x) => ({ client: x.clientName, balance: x.balance, investedBalance: x.investedBalance })) : dRows };
  d.sqlQcClientInvestmentOnInv4 = num(val(`select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t where t.trust_account_id='${ids.inv4}' and t.customer_id='${ids.qc}' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null`));
  d.checks = { qcInvestedZero: d.qc.status === 200 && eq(d.qc.body.investedBalance, 0), morokaInvestedZero: d.moroka.status === 200 && eq(d.moroka.body.investedBalance, 0),
    listInvestedZero: Array.isArray(dRows) && dRows.every((x) => eq(x.investedBalance, 0)) };
  save();

  // ── b: Moroka s86(4) R7 500 into the lifecycle's s86(4) account — pending → approved → withdrawn ──
  const b = (result.steps.b = {});
  b.place = await placeViaUi(S.alice.p, { client: ids.moroka, matter: ids.morokaMatter, accountName: ids.inv4Name, institution: "Nedbank", depositNumber: `OBS4E-${RUN}`, principal: P, rate: 7 });
  b.pendingTx = rowsA(`select transaction_type, status, ledger, posted_at is not null, amount from T.trust_transactions where id='${b.place.investmentDb.depositTx}'`)[0];
  b.pending = await state("b2", "moroka-pending", ids.moroka);
  save();
  b.approve = await approveViaUi(S.bob.p, b.place.investmentDb.depositTx);
  sql("b: the placement's rows after approval", `select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.investment_id='${b.place.investmentDb.id}' order by t.created_at`);
  b.approved = await state("b4", "moroka-approved", ids.moroka, { clips: true });
  b.approvedQc = (await apiCard(S.alice.ctx, "b4-qc-unchanged", ids.qc, ids.funding)).card;
  save();
  b.withdraw = await withdrawViaUi(S.alice.p, `OBS4E-${RUN}`);
  sql("b: the placement's rows after the withdrawal", `select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.investment_id='${b.place.investmentDb.id}' order by t.created_at`);
  b.withdrawn = await state("b6", "moroka-withdrawn", ids.moroka, { clips: true });
  const base = c.api.card, pend = b.pending.api.card, appr = b.approved.api.card, wd = b.withdrawn.api.card;
  b.figures = {
    baseline: { general: base.balance, invested: base.investedBalance, held: base.balance + base.investedBalance, placements: base.totalInvestmentPlacements, returns: base.totalInvestmentReturns },
    pending: { general: pend.balance, invested: pend.investedBalance, held: pend.balance + pend.investedBalance, placements: pend.totalInvestmentPlacements, returns: pend.totalInvestmentReturns },
    approved: { general: appr.balance, invested: appr.investedBalance, held: appr.balance + appr.investedBalance, placements: appr.totalInvestmentPlacements, returns: appr.totalInvestmentReturns },
    withdrawn: { general: wd.balance, invested: wd.investedBalance, held: wd.balance + wd.investedBalance, placements: wd.totalInvestmentPlacements, returns: wd.totalInvestmentReturns },
  };
  b.uiTexts = {
    approved: { placements: b.approved.ui.rows["investment-placements"] && b.approved.ui.rows["investment-placements"].text, balance: b.approved.ui.rows.balance.text,
      invested: b.approved.ui.rows.invested && b.approved.ui.rows.invested.text, held: b.approved.ui.rows.held && b.approved.ui.rows.held.text, summaryLine: b.approved.ui.summaryInvested },
    withdrawn: { placements: b.withdrawn.ui.rows["investment-placements"] && b.withdrawn.ui.rows["investment-placements"].text, returns: b.withdrawn.ui.rows["investment-returns"] && b.withdrawn.ui.rows["investment-returns"].text,
      balance: b.withdrawn.ui.rows.balance.text, invested: b.withdrawn.ui.rows.invested, held: b.withdrawn.ui.rows.held, summaryLine: b.withdrawn.ui.summaryInvested },
  };
  b.checks = {
    pendingAwaiting: b.pendingTx[1] === "AWAITING_APPROVAL" && b.pendingTx[3] === "f",
    pendingNothingMoved: eq(pend.balance, base.balance) && eq(pend.investedBalance, 0) && eq(pend.totalInvestmentPlacements, base.totalInvestmentPlacements),
    pendingPageUnchanged: b.pending.ui.summaryText === c.ui.summaryText,
    approvedGeneralDown: eq(appr.balance, base.balance - P), approvedInvestedUp: eq(appr.investedBalance, P), approvedHeldUnchanged: eq(appr.balance + appr.investedBalance, base.balance + base.investedBalance),
    approvedPlacements: eq(appr.totalInvestmentPlacements, base.totalInvestmentPlacements + P), approvedTransfersOutUnchanged: eq(appr.totalTransfersOut, base.totalTransfersOut),
    approvedQcUnchanged: eq(b.approvedQc.investedBalance, a.api.card.investedBalance) && eq(b.approvedQc.balance, a.api.card.balance),
    withdrawnInvestedZero: eq(wd.investedBalance, 0), withdrawnGeneralBack: eq(wd.balance, base.balance), withdrawnReturns: eq(wd.totalInvestmentReturns, base.totalInvestmentReturns + P),
    withdrawnRowsHidden: b.withdrawn.ui.rows.invested === null && b.withdrawn.ui.rows.held === null && !b.withdrawn.ui.hasInvestedLabel && !b.withdrawn.ui.hasHeldLabel,
    withdrawnReturnsShown: !!b.withdrawn.ui.rows["investment-returns"] && /Returned from section 86\(4\) investments R 7 500,00/.test(b.withdrawn.ui.rows["investment-returns"].text),
    approvedPlacedText: !!b.approved.ui.rows["investment-placements"] && /Placed in section 86\(4\) investments R 7 500,00/.test(b.approved.ui.rows["investment-placements"].text),
    approvedInvestedText: !!b.approved.ui.rows.invested && /Invested \(section 86\(4\)\) R 7 500,00/.test(b.approved.ui.rows.invested.text),
    pendingAgree: b.pending.pass, approvedAgree: b.approved.pass, withdrawnAgree: b.withdrawn.pass,
  };
  save();

  result.finishedAt = new Date().toISOString();
  result.summary = { run: RUN, ids,
    a: { pass: a.pass, checks: a.checks, observed: a.observed, matchesBriefFigures: a.matchesBriefFigures, transfersOutExcludesPlacement: a.transfersOutExcludesPlacement, f4: a.f4 },
    c: { pass: c.pass && allTrue(c.specific), checks: c.checks, specific: c.specific },
    d: d.summary, dChecks: d.checks,
    b: { checks: b.checks, figures: b.figures, uiTexts: b.uiTexts, pass: allTrue(b.checks) },
    f: { a: { headline: a.tab.headline, badges: a.tab.badges }, bApproved: { headline: b.approved.tab.headline, badges: b.approved.tab.badges, held: b.approved.tab.rows.held && b.approved.tab.rows.held.amount } },
    g: { scans: result.scans.length, dirty: result.scans.filter((s) => !s.clean) },
    h: Object.fromEntries(Object.entries(result.clips).map(([k, v]) => [k, v.map((m) => ({ w: m.width, ok: m.ok, figuresOk: m.figuresOk, pageHScroll: m.pageHorizontalScroll, offenders: m.offenders.length, truncated: m.truncated.length }))])),
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length, posts: result.posts.length };
  save();
  console.log(JSON.stringify(result.summary, null, 2));
  await browser.close();
})().catch(async (e) => { result.error = String(e.stack || e); save(); console.error("OBS FAILED", e); try { await browser.close(); } catch {} process.exit(1); });
