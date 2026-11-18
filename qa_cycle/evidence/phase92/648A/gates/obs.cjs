// 648A OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI: Alice (owner) places investments from Trust › Investments (adding each
// investment account through "Add investment account" in the Place Investment dialog) and withdraws; Bob (admin)
// approves the placements on Trust › Transactions. The (d) reversal and (e) close probes are API evidence (no UI offers
// them); they are expected to be refused and create nothing. psql is read-only EVIDENCE. API reads use the browser
// context's own mock-auth-token against :8081.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648A";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const { measureIn } = require("./measure.cjs");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const ORG = `${BASE}/org/e2e-test-org`;
const EV = `${W}/qa_cycle/evidence/phase92/648A/gates`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const P4 = 7500, P3 = 2500;
const result = { steps: {}, consoleErrors: [], pageErrors: [], posts: [], notes: [], startedAt: new Date().toISOString() };
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
fs.writeFileSync(SQL_FILE, `-- 648A OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 648A OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
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
const num = (x) => Number(x);
const eq = (x, y) => x != null && y != null && Math.abs(Number(x) - Number(y)) < 0.005;
async function waitSql(q, pred, ms = 30_000) {
  const t0 = Date.now(); let v = val(q);
  while (!pred(v) && Date.now() - t0 < ms) { await new Promise((r) => setTimeout(r, 500)); v = val(q); }
  return v;
}
const ids = {};
// the client's figures by ledger, across every account (posted rows only — TrustLedgerQueryRepository's predicate)
function clientFigures(label) {
  const f = {
    general: num(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where customer_id='${ids.client}' and ledger='CLIENT' and posted_at is not null`)),
    invested: num(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where customer_id='${ids.client}' and ledger='CLIENT_INVESTMENT' and posted_at is not null`)),
    matterGeneral: num(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where project_id='${ids.matter}' and ledger='CLIENT' and posted_at is not null`)),
    matterTotal: num(val(`select coalesce(sum(ledger_effect),0) from T.trust_transactions where project_id='${ids.matter}' and ledger in ('CLIENT','CLIENT_INVESTMENT') and posted_at is not null`)),
  };
  f.total = f.general + f.invested;
  sql(`${label}: client figures by ledger and account`, `select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.customer_id='${ids.client}' and t.posted_at is not null group by 1,2 order by 1,2`);
  return f;
}
// every client's general balance (to prove an s86(3) placement moves none)
const allClientGeneral = () => val(`select coalesce(string_agg(customer_id||'='||s, ',' order by customer_id),'') from (select customer_id, sum(ledger_effect) s from T.trust_transactions where ledger='CLIENT' and posted_at is not null group by 1) x`);
function books(label) {
  const out = sql(`${label}: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)`,
    `select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from T.trust_accounts a left join T.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1`);
  const rows = rowsA(`select a.id, a.account_name, coalesce(sum(t.cashbook_effect),0), coalesce(sum(t.ledger_effect),0) from T.trust_accounts a left join T.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2`);
  return rows.map((r) => ({ id: r[0], name: r[1], cashbook: num(r[2]), creditors: num(r[3]), balanced: eq(r[2], r[3]) }));
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
const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
const money = (s) => { const m = norm(s).replace(/[^\d,\-−]/g, "").replace("−", "-").replace(",", "."); return m === "" ? null : Number(m); };
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
async function apiPost(ctx, path, data, saveAs, who) {
  const res = await ctx.request.post(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token(ctx)}`, "Content-Type": "application/json" }, data });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { label: "API EVIDENCE (probe; expected to be refused, creates nothing)", request: { method: "POST", url: `${BACKEND}${path}`, headers: { Authorization: `Bearer <${who} mock-auth-token from the browser context>` }, body: data },
    response: { status: res.status(), contentType: res.headers()["content-type"], body } };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
  return r;
}
// raw enum scan (case-sensitive, whole token); innerText applies CSS text-transform, so each hit is re-checked
// against textContent (the source text): a hit only in innerText is a CSS-uppercased label, not a raw enum.
const RAW = ["FIRM_DISCRETION", "CLIENT_INSTRUCTION", "INV_PLACE", "FIRM_INV_PLACE", "INV_RECEIVE", "FIRM_INV_RECEIVE", "INV_WITHDRAW", "INV_RETURN", "FIRM_INV_WITHDRAW", "FIRM_INV_RETURN",
  "ACTIVE", "MATURED", "WITHDRAWN", "CANCELLED", "AWAITING_APPROVAL", "CLIENT_INVESTMENT", "INTER_ACCOUNT", "INVESTMENT", "GENERAL", "S86_3", "S86_4", "RECORDED", "APPROVED"];
async function scanRaw(locator, label) {
  const { inner, content } = await locator.evaluate((el) => ({ inner: el.innerText, content: el.textContent }));
  const hit = (s, tok) => new RegExp(`(^|[^A-Za-z0-9_])${tok}([^A-Za-z0-9_]|$)`).test(s);
  const r = { label, rawInInnerText: RAW.filter((t) => hit(inner, t)), rawInTextContent: RAW.filter((t) => hit(content, t)) };
  r.cssUppercaseOnly = r.rawInInnerText.filter((t) => !r.rawInTextContent.includes(t));
  r.clean = r.rawInTextContent.length === 0;
  fs.writeFileSync(`${OUT}/scan-${label}.txt`, `innerText:\n${inner}\n\n---- textContent:\n${content}\n`);
  (result.steps.f ||= { scans: [] }).scans.push(r);
  return r;
}
async function clip(p, label, rootSel, opts = {}, widths = [1280, 1440]) {
  const res = [];
  for (const w of widths) {
    await p.setViewportSize({ width: w, height: opts.height || 1100 });
    await p.waitForTimeout(500);
    const m = await p.evaluate(measureIn, { rootSel, opts: { boxIsViewport: !!opts.boxIsViewport } });
    m.width = w;
    if (opts.extra) m.extra = await p.evaluate(opts.extra.fn, opts.extra.arg);
    res.push(m);
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(300);
  (result.steps.g ||= {})[label] = res;
  return res;
}

// ── UI flows ────────────────────────────────────────────────────────
const BASIS_RADIO = { s863: "Section 86(3) — the firm invests surplus trust money", s864: "Section 86(4) — invested on a client's instruction" };
// Create Account reachability inside the stacked dialog (F-5): scrollable content, and the button inside the viewport
// once scrolled into view (Playwright's own click scrolls the same way).
function createButtonReach() {
  const d = document.querySelector("[data-testid='create-trust-account-dialog']");
  if (!d) return { missing: true };
  const b = [...d.querySelectorAll("button")].find((x) => x.innerText.trim() === "Create Account");
  const vh = document.documentElement.clientHeight;
  const before = b.getBoundingClientRect();
  const cs = getComputedStyle(d);
  const r0 = { dialogTop: Math.round(d.getBoundingClientRect().top), dialogBottom: Math.round(d.getBoundingClientRect().bottom), vh,
    dialogClientHeight: d.clientHeight, dialogScrollHeight: d.scrollHeight, overflowY: cs.overflowY, maxHeight: cs.maxHeight,
    buttonTopBefore: Math.round(before.top), buttonBottomBefore: Math.round(before.bottom), buttonInViewportBefore: before.top >= 0 && before.bottom <= vh };
  b.scrollIntoView({ block: "nearest" });
  const after = b.getBoundingClientRect();
  Object.assign(r0, { buttonTopAfterScroll: Math.round(after.top), buttonBottomAfterScroll: Math.round(after.bottom), buttonInViewportAfterScroll: after.top >= 0 && after.bottom <= vh, dialogScrollTop: d.scrollTop });
  d.scrollTop = 0;
  return r0;
}
async function placeViaUi(p, key, o) {
  const s = (result.steps[key] = { ...o });
  await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  await p.getByRole("button", { name: "Place Investment" }).first().click();
  const dlg = p.getByTestId("place-investment-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  await dlg.getByRole("radio", { name: BASIS_RADIO[o.basis] }).check();
  await p.waitForTimeout(300);
  s.helpText = norm(await dlg.getByTestId("investment-basis-help").innerText());
  s.clientIdFieldCount = await dlg.getByLabel("Client ID").count();
  s.matterIdFieldCount = await dlg.getByLabel("Matter ID").count();
  await dlg.getByTestId("investment-account-select").click();
  await p.getByRole("listbox").waitFor({ timeout: 10_000 }).catch(() => {});
  s.pickerOptionsBefore = (await p.getByRole("option").allInnerTexts()).map(norm);
  await p.keyboard.press("Escape");
  await p.waitForTimeout(300);
  s.filesPlaceEmpty = await snap(p, `${key}1-place-dialog-${o.basis}`);
  await scanRaw(dlg, `${key}-place-dialog-${o.basis}`);
  // the stacked "Add Investment Account" dialog, preset to the basis
  await dlg.getByRole("button", { name: "Add investment account" }).click();
  const add = p.getByRole("dialog", { name: "Add Investment Account" });
  await add.waitFor({ timeout: 15_000 });
  await p.waitForTimeout(400);
  const typeSel = add.getByTestId("trust-account-type-select"), basisSel = add.getByTestId("trust-account-basis-select");
  s.addDialog = { typeText: norm(await typeSel.innerText()), typeDisabled: await typeSel.isDisabled(), basisText: norm(await basisSel.innerText()), basisDisabled: await basisSel.isDisabled(),
    description: norm(await add.locator("[data-slot='dialog-description'], p").first().innerText().catch(() => "")) };
  const cb = add.getByRole("checkbox", { name: "Set as primary trust account" });
  s.addDialog.primaryCheckboxCount = await cb.count();
  s.addDialog.primaryChecked = s.addDialog.primaryCheckboxCount ? await cb.getAttribute("aria-checked") : null;
  await clip(p, `${key}-stacked-add-dialog-720`, "[data-testid='create-trust-account-dialog']", { height: 720, extra: { fn: createButtonReach, arg: null } }, [1280, 1440, 1920]);
  s.filesAdd = await snap(p, `${key}2-stacked-add-investment-account-${o.basis}`);
  await crop(add, `${key}2-stacked-add-dialog-crop`);
  await scanRaw(add, `${key}-stacked-add-dialog-${o.basis}`);
  await add.getByLabel("Account Name").fill(o.accountName);
  await add.getByLabel("Bank Name").fill(o.bank);
  await add.getByLabel("Branch Code").fill("051001");
  await add.getByLabel("Account Number").fill(o.accountNumber);
  if (s.addDialog.primaryChecked === "true") { await cb.click(); s.addDialog.untickedPrimary = true; }
  const nAcc = Number(val("select count(*) from T.trust_accounts"));
  await add.getByRole("button", { name: "Create Account" }).click();
  await add.waitFor({ state: "hidden", timeout: 30_000 });
  s.accountsCountBefore = nAcc; s.accountsCountAfter = Number(val("select count(*) from T.trust_accounts"));
  s.newAccountDb = rowsA(`select id, account_type, investment_basis, is_primary, status from T.trust_accounts where account_name='${o.accountName}'`)[0];
  await p.waitForTimeout(500);
  s.pickerTextAfterCreate = norm(await dlg.getByTestId("investment-account-select").innerText());
  if (o.client) {
    await dlg.getByLabel("Client ID").fill(o.client.id);
    await dlg.getByLabel("Matter ID").fill(o.client.matter);
  }
  await dlg.getByLabel("Institution").fill(o.institution);
  await dlg.getByLabel("Account Number").fill(o.depositNumber);
  await dlg.getByLabel("Principal").fill(String(o.principal));
  await dlg.getByLabel("Interest Rate %").fill(String(o.rate));
  await dlg.getByLabel("Notes (optional)").fill(`OBS ${RUN} ${o.basis}`);
  s.filesPlaceFilled = await snap(p, `${key}3-place-dialog-filled-${o.basis}`);
  await crop(dlg, `${key}3-place-dialog-filled-crop`);
  await scanRaw(dlg, `${key}-place-dialog-filled-${o.basis}`);
  await clip(p, `${key}-place-dialog-filled`, "[data-testid='place-investment-dialog']", { height: 720 });
  const nInv = Number(val("select count(*) from T.trust_investments"));
  await dlg.getByRole("button", { name: "Place Investment" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  s.investmentsCountBefore = nInv;
  s.investmentsCountAfter = Number(await waitSql("select count(*) from T.trust_investments", (v) => Number(v) === nInv + 1));
  const [inv] = rowsA(`select id, deposit_transaction_id, investment_basis, coalesce(customer_id::text,'<null>'), coalesce(project_id::text,'<null>'), investment_account_id, trust_account_id, status, principal from T.trust_investments where account_number='${o.depositNumber}'`);
  s.investmentDb = { id: inv[0], depositTx: inv[1], basis: inv[2], customerId: inv[3], projectId: inv[4], investmentAccountId: inv[5], fundingAccountId: inv[6], status: inv[7], principal: inv[8] };
  return s;
}
async function registerRow(p, depositNumber, key, label) {
  await p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  const row = p.locator("main tbody tr").filter({ hasText: depositNumber });
  await row.first().waitFor({ timeout: 30_000 });
  const r = { text: norm(await row.first().innerText()), withdrawButtons: await row.first().getByRole("button", { name: "Withdraw" }).count(),
    cells: (await row.first().locator("td").allInnerTexts()).map(norm) };
  r.files = await snap(p, `${key}-register-${label}`);
  await crop(p.getByTestId("investments-table"), `${key}-register-${label}-table-crop`);
  await scanRaw(p.locator("main").first(), `${key}-investments-page-${label}`);
  return r;
}
async function clientUi(p, key, label) {
  await p.goto(`${ORG}/matters/${ids.matter}?tab=trust`, { waitUntil: "load" });
  const card = p.getByTestId("trust-balance-card");
  await card.waitFor({ timeout: 60_000 }).catch(() => {});
  await p.getByTestId("trust-balance-primary").or(card.getByText(/Unable to load/)).first().waitFor({ timeout: 30_000 }).catch(() => {});
  await p.waitForTimeout(1500);
  const u = { cardText: norm(await card.innerText().catch(() => "")) };
  u.matterTotalText = (await p.getByTestId("trust-balance-primary").count()) ? norm(await p.getByTestId("trust-balance-primary").innerText()) : null;
  u.clientLineText = (await p.getByTestId("trust-balance-client-line").count()) ? norm(await p.getByTestId("trust-balance-client-line").innerText()) : null;
  u.matterTotal = u.matterTotalText ? money(u.matterTotalText) : null;
  u.clientGeneral = u.clientLineText ? money(u.clientLineText.split(":").pop()) : null;
  u.files = await snap(p, `${key}-matter-trust-tab-${label}`);
  await crop(card, `${key}-matter-trust-card-${label}-crop`);
  // the client ledger page (general balance)
  await p.goto(`${ORG}/trust-accounting/client-ledgers/${ids.client}`, { waitUntil: "load" });
  await p.waitForTimeout(2500);
  u.clientLedgerPageText = norm(await p.locator("main").first().innerText().catch(() => "")).slice(0, 600);
  u.filesLedger = await snap(p, `${key}-client-ledger-page-${label}`, [1440]);
  return u;
}
async function clientApi(ctx, key) {
  const a = {};
  const card = await apiGet(ctx, `/api/trust-accounts/${ids.funding}/client-ledgers/${ids.client}`, `${key}-client-ledger-card`);
  a.clientLedgerCard = { status: card.status, balance: card.body && card.body.balance, title: card.body && card.body.title };
  const list = await apiGet(ctx, `/api/trust-accounts/${ids.funding}/client-ledgers?page=0&size=50`, `${key}-client-ledgers-list`);
  a.clientLedgersList = { status: list.status };
  const mt = await apiGet(ctx, `/api/matters/${ids.matter}/trust-balance`, `${key}-matter-trust-balance`);
  a.matterTotal = mt.body && mt.body.balance; a.matterTotalStatus = mt.status;
  return a;
}
async function positions(ctx, key) {
  const out = {};
  for (const [k, id] of Object.entries({ funding: ids.funding, inv4: ids.inv4Account, inv3: ids.inv3Account })) {
    if (!id) continue;
    const r = await apiGet(ctx, `/api/dashboard/trust-position?accountId=${id}`, `${key}-trust-position-${k}`);
    out[k] = { status: r.status, cashbook: r.body.cashbookBalance, creditorsTotal: r.body.creditorBreakdown && r.body.creditorBreakdown.total, difference: r.body.difference, breakdown: r.body.creditorBreakdown };
  }
  return out;
}
async function approveViaUi(p, key, txId) {
  const s = (result.steps[key] = { txId });
  await p.goto(`${ORG}/trust-accounting/transactions`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
  const row = p.getByTestId(`transaction-row-${txId}`);
  await row.waitFor({ timeout: 30_000 });
  s.rowTextBefore = norm(await row.innerText());
  s.descriptionText = (await p.getByTestId(`transaction-description-${txId}`).count()) ? norm(await p.getByTestId(`transaction-description-${txId}`).innerText()) : null;
  s.approveButtons = await p.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve" }).count();
  s.files = await snap(p, `${key}-transactions-awaiting`);
  await crop(row, `${key}-transactions-row-awaiting-crop`);
  await scanRaw(row, `${key}-approvals-row`);
  await scanRaw(p.locator("main").first(), `${key}-transactions-page`);
  await clip(p, `${key}-approvals-row`, `[data-testid='transaction-row-${txId}']`, { extra: { fn: (id) => {
    const tbl = document.querySelector(`[data-testid='transaction-row-${id}']`).closest("table"); const wrap = tbl.parentElement;
    const d = document.querySelector(`[data-testid='transaction-description-${id}']`); const dr = d && d.getBoundingClientRect(); const tr = document.querySelector(`[data-testid='transaction-row-${id}']`).getBoundingClientRect();
    const act = document.querySelector(`[data-testid='approval-actions-${id}']`); const ar = act && act.getBoundingClientRect();
    return { tableScrollWidth: tbl.scrollWidth, wrapClientWidth: wrap.clientWidth, tableOverflowsWrap: tbl.scrollWidth > wrap.clientWidth + 1, vw: document.documentElement.clientWidth,
      description: d ? { left: Math.round(dr.left), right: Math.round(dr.right), lines: Math.round(dr.height / 16), scrollWidth: d.scrollWidth, clientWidth: d.clientWidth, insideRow: dr.right <= tr.right + 0.5 } : null,
      actions: act ? { left: Math.round(ar.left), right: Math.round(ar.right), insideViewport: ar.right <= document.documentElement.clientWidth + 0.5 } : null };
  }, arg: txId } });
  await p.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve" }).click();
  await p.waitForFunction((id) => { const r = document.querySelector(`[data-testid='transaction-row-${id}']`); return r && /approved/i.test(r.innerText) && !/awaiting/i.test(r.innerText); }, txId, { timeout: 30_000 });
  s.rowTextAfter = norm(await row.innerText());
  s.filesAfter = await snap(p, `${key}-transactions-approved`, [1440]);
  await crop(row, `${key}-transactions-row-approved-crop`);
  s.dbAfter = rowsA(`select transaction_type, status, ledger, trust_account_id, amount, ledger_effect, cashbook_effect, posted_at is not null, coalesce(customer_id::text,'<null>') from T.trust_transactions where investment_id=(select investment_id from T.trust_transactions where id='${txId}') order by created_at`);
  return s;
}

// ── main ────────────────────────────────────────────────────────────
(async () => {
  browser = await chromium.launch({ headless: true });
  const A = await newSession("alice", "user_e2e_alice");
  const B = await newSession("bob", "user_e2e_bob");
  ids.funding = val("select id from T.trust_accounts where account_type<>'INVESTMENT' and is_primary order by created_at limit 1");
  const [top] = rowsA(`select customer_id, project_id, sum(ledger_effect) s from T.trust_transactions where ledger='CLIENT' and posted_at is not null and trust_account_id='${ids.funding}' and project_id is not null group by 1,2 order by s desc limit 1`);
  ids.client = top[0]; ids.matter = top[1];
  ids.clientName = val(`select name from T.customers where id='${ids.client}'`);
  ids.matterName = val(`select name from T.projects where id='${ids.matter}'`);
  result.ids = ids;
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());

  // ── baseline ──
  const base = (result.steps.baseline = {});
  base.books = books("baseline");
  base.client = clientFigures("baseline");
  base.allClientGeneral = allClientGeneral();
  base.api = await clientApi(A.ctx, "00-baseline");
  base.positions = await positions(A.ctx, "00-baseline");
  base.ui = await clientUi(A.p, "00", "baseline");
  save();

  // ── a: section 86(4) placement ──
  const a = await placeViaUi(A.p, "a", { basis: "s864", accountName: `OBS s86(4) Investments ${RUN}`, bank: "Nedbank", accountNumber: `0864${RUN}`,
    institution: "Nedbank", depositNumber: `OBS4-${RUN}`, principal: P4, rate: 7, client: { id: ids.client, matter: ids.matter } });
  ids.inv4Account = a.investmentDb.investmentAccountId;
  a.pendingTx = rowsA(`select transaction_type, status, ledger, posted_at is not null, amount from T.trust_transactions where id='${a.investmentDb.depositTx}'`)[0];
  a.pendingInvApi = (await apiGet(A.ctx, `/api/trust-investments/${a.investmentDb.id}`, "a-investment-pending")).body;
  a.pendingRegister = await registerRow(A.p, `OBS4-${RUN}`, "a4", "s86-4-awaiting");
  a.pendingClient = clientFigures("a: before approval");
  a.pendingApi = await clientApi(A.ctx, "a-pending");
  a.pendingUi = await clientUi(A.p, "a5", "pending");
  save();
  const a2 = await approveViaUi(B.p, "a6", a.investmentDb.depositTx);
  a.after = { client: clientFigures("a: after Bob approved"), books: books("a: after approval"), api: await clientApi(A.ctx, "a-approved"), positions: await positions(A.ctx, "a-approved") };
  a.after.investmentApi = (await apiGet(A.ctx, `/api/trust-investments/${a.investmentDb.id}`, "a-investment-approved")).body;
  a.after.register = await registerRow(A.p, `OBS4-${RUN}`, "a7", "s86-4-active");
  a.after.ui = await clientUi(A.p, "a8", "approved");
  const bk = (list, id) => list.find((x) => x.id === id) || { cashbook: 0, creditors: 0 };
  a.deltas = { clientTotal: a.after.client.total - base.client.total, general: a.after.client.general - base.client.general, invested: a.after.client.invested - base.client.invested,
    matterGeneral: a.after.client.matterGeneral - base.client.matterGeneral, matterTotal: a.after.client.matterTotal - base.client.matterTotal,
    cashbookFunding: bk(a.after.books, ids.funding).cashbook - bk(base.books, ids.funding).cashbook, cashbookInv4: bk(a.after.books, ids.inv4Account).cashbook };
  a.checks = {
    addDialogPreset: a.addDialog.typeText === "Investment" && a.addDialog.typeDisabled && /Section 86\(4\)/.test(a.addDialog.basisText) && a.addDialog.basisDisabled,
    accountCreatedNotPrimary: a.accountsCountAfter === a.accountsCountBefore + 1 && a.newAccountDb[1] === "INVESTMENT" && a.newAccountDb[2] === "CLIENT_INSTRUCTION" && a.newAccountDb[3] === "f",
    pickerSelectsNew: a.pickerTextAfterCreate.includes(a.accountName),
    pendingAwaiting: a.pendingTx[1] === "AWAITING_APPROVAL" && a.pendingTx[3] === "f" && /Awaiting approval/.test(a.pendingRegister.text) && a.pendingRegister.withdrawButtons === 0,
    pendingNothingMoved: eq(a.pendingClient.total, base.client.total) && eq(a.pendingClient.general, base.client.general),
    approverSeesDescription: /Placed in section 86\(4\) investment at/.test(a2.descriptionText || ""),
    approved: /approved/i.test(a2.rowTextAfter) && !/awaiting/i.test(a2.rowTextAfter),
    totalUnchanged: eq(a.deltas.clientTotal, 0) && eq(a.deltas.matterTotal, 0), generalDown: eq(a.deltas.general, -P4) && eq(a.deltas.matterGeneral, -P4), investedUp: eq(a.deltas.invested, P4),
    cashbooks: eq(a.deltas.cashbookFunding, -P4) && eq(a.deltas.cashbookInv4, P4),
    apiMatterTotalUnchanged: eq(a.after.api.matterTotal, base.api.matterTotal) && eq(a.after.api.matterTotal, a.after.client.matterTotal),
    uiMatterTotalUnchanged: eq(a.after.ui.matterTotal, a.after.client.matterTotal),
    apiClientCardGeneral: a.after.api.clientLedgerCard.status === 200 && eq(a.after.api.clientLedgerCard.balance, base.client.general - P4),
    uiClientGeneral: eq(a.after.ui.clientGeneral, a.after.client.general),
    registerActive: /Active/.test(a.after.register.text) && a.after.register.withdrawButtons === 1,
  };
  save();

  // ── b: section 86(3) placement ──
  const b = await placeViaUi(A.p, "b", { basis: "s863", accountName: `OBS s86(3) Investments ${RUN}`, bank: "Standard Bank", accountNumber: `0863${RUN}`,
    institution: "Standard Bank", depositNumber: `OBS3-${RUN}`, principal: P3, rate: 6 });
  ids.inv3Account = b.investmentDb.investmentAccountId;
  b.generalBefore = allClientGeneral();
  b.pendingRegister = await registerRow(A.p, `OBS3-${RUN}`, "b4", "s86-3-awaiting");
  save();
  const b2 = await approveViaUi(B.p, "b6", b.investmentDb.depositTx);
  b.after = { allClientGeneral: allClientGeneral(), books: books("b: after approval"), positions: await positions(A.ctx, "b-approved") };
  b.after.investmentDb = rowsA(`select coalesce(customer_id::text,'<null>'), coalesce(project_id::text,'<null>'), status from T.trust_investments where id='${b.investmentDb.id}'`)[0];
  sql("b: the s86(3) investment and its rows", `select i.account_number, i.investment_basis, i.customer_id is null customer_id_is_null, i.project_id is null project_id_is_null, i.status, t.transaction_type, t.status tx_status, t.ledger, t.customer_id is null tx_customer_null, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from T.trust_investments i join T.trust_transactions t on t.investment_id=i.id join T.trust_accounts a on a.id=t.trust_account_id where i.id='${b.investmentDb.id}' order by t.created_at`);
  b.after.register = await registerRow(A.p, `OBS3-${RUN}`, "b7", "s86-3-active");
  b.checks = {
    clientFieldsHidden: b.clientIdFieldCount === 0 && b.matterIdFieldCount === 0,
    addDialogPreset: b.addDialog.typeText === "Investment" && b.addDialog.typeDisabled && /Section 86\(3\)/.test(b.addDialog.basisText) && b.addDialog.basisDisabled,
    accountCreatedNotPrimary: b.newAccountDb[2] === "FIRM_DISCRETION" && b.newAccountDb[3] === "f",
    registerFirm: /Firm \(section 86\(3\)\)/.test(b.pendingRegister.text) && /Awaiting approval/.test(b.pendingRegister.text) && b.pendingRegister.withdrawButtons === 0,
    approverSeesDescription: /Placed in section 86\(3\) investment at/.test(b2.descriptionText || ""),
    noClientOnInvestment: b.after.investmentDb[0] === "<null>" && b.after.investmentDb[1] === "<null>",
    rowsInterAccount: b2.dbAfter.length === 2 && b2.dbAfter.every((r) => r[2] === "INTER_ACCOUNT" && r[8] === "<null>" && r[7] === "t"),
    noClientLedgerMoved: b.after.allClientGeneral === b.generalBefore,
    cashbooks: eq(bk(b.after.books, ids.funding).cashbook - bk(a.after.books, ids.funding).cashbook, -P3) && eq(bk(b.after.books, ids.inv3Account).cashbook, P3),
    registerActiveFirm: /Firm \(section 86\(3\)\)/.test(b.after.register.text) && /Active/.test(b.after.register.text),
  };
  save();

  // ── c: withdraw the section 86(4) investment ──
  const c = (result.steps.c = {});
  await A.p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await A.p.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  const row4 = A.p.locator("main tbody tr").filter({ hasText: `OBS4-${RUN}` }).first();
  await row4.getByRole("button", { name: "Withdraw" }).click();
  const wd = A.p.getByTestId("withdraw-investment-dialog");
  await wd.waitFor({ timeout: 15_000 });
  await A.p.waitForTimeout(400);
  c.amountText = norm(await wd.getByTestId("withdrawal-amount").innerText());
  c.amount = money(c.amountText);
  c.dialogText = norm(await wd.innerText());
  c.inputs = await wd.locator("input, textarea, select, [role='combobox']").evaluateAll((els) => els.map((e) => ({ tag: e.tagName.toLowerCase(), type: e.type || null, name: e.name || null, value: e.value || null, hidden: e.type === "hidden" })));
  c.files = await snap(A.p, "c1-withdraw-dialog");
  await crop(wd, "c1-withdraw-dialog-crop");
  await scanRaw(wd, "c-withdraw-dialog");
  await clip(A.p, "c-withdraw-dialog", "[data-testid='withdraw-investment-dialog']", { height: 720 });
  const nTx = Number(val("select count(*) from T.trust_transactions"));
  await wd.getByRole("button", { name: "Confirm Withdrawal" }).click();
  await wd.waitFor({ state: "hidden", timeout: 30_000 });
  c.txCountBefore = nTx;
  c.txCountAfter = Number(await waitSql("select count(*) from T.trust_transactions", (v) => Number(v) >= nTx + 2));
  c.rows = rowsA(`select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null, t.transaction_date from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.investment_id='${a.investmentDb.id}' order by t.created_at`);
  sql("c: the s86(4) investment's rows after the withdrawal", `select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted, t.transaction_date, t.reference from T.trust_transactions t join T.trust_accounts a on a.id=t.trust_account_id where t.investment_id='${a.investmentDb.id}' order by t.created_at`);
  c.investmentDb = rowsA(`select status, withdrawal_date, withdrawal_amount from T.trust_investments where id='${a.investmentDb.id}'`)[0];
  c.client = clientFigures("c: after the withdrawal");
  c.books = books("c: after the withdrawal");
  c.positions = await positions(A.ctx, "c-withdrawn");
  c.api = await clientApi(A.ctx, "c-withdrawn");
  c.investmentApi = (await apiGet(A.ctx, `/api/trust-investments/${a.investmentDb.id}`, "c-investment-withdrawn")).body;
  c.register = await registerRow(A.p, `OBS4-${RUN}`, "c2", "s86-4-withdrawn");
  c.ui = await clientUi(A.p, "c3", "withdrawn");
  await A.p.goto(`${ORG}/trust-accounting`, { waitUntil: "load" });
  await A.p.waitForTimeout(3000);
  c.trustOverviewText = norm(await A.p.locator("main").first().innerText().catch(() => "")).slice(0, 1500);
  c.filesOverview = await snap(A.p, "c4-trust-accounting-overview", [1440]);
  const wdRows = c.rows.filter((r) => r[0] === "INV_WITHDRAW" || r[0] === "INV_RETURN");
  c.checks = {
    amountIsBalance: eq(c.amount, P4),
    onlyTheDate: c.inputs.filter((i) => !i.hidden).length === 1 && c.inputs.filter((i) => !i.hidden)[0].type === "date",
    bothRecorded: wdRows.length === 2 && wdRows.every((r) => r[1] === "RECORDED" && r[6] === "t"),
    withdrawn: c.investmentDb[0] === "WITHDRAWN" && eq(c.investmentDb[2], P4),
    generalRestored: eq(c.client.general, base.client.general) && eq(c.client.invested, base.client.invested) && eq(c.client.total, base.client.total),
    booksBalancedSql: c.books.every((x) => x.balanced),
    booksBalancedApi: Object.values(c.positions).every((x) => x.status === 200 && eq(x.cashbook, x.creditorsTotal) && eq(x.difference, 0)),
    registerWithdrawn: /Withdrawn/.test(c.register.text) && c.register.withdrawButtons === 0,
    uiClientGeneralRestored: eq(c.ui.clientGeneral, base.client.general),
    apiClientCardRestored: c.api.clientLedgerCard.status === 200 && eq(c.api.clientLedgerCard.balance, base.client.general),
  };
  save();

  // ── d: reversal refused on investment placement rows ──
  const d = (result.steps.d = {});
  await A.p.goto(`${ORG}/trust-accounting/transactions`, { waitUntil: "load" });
  await A.p.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
  await A.p.waitForTimeout(800);
  d.ui = {};
  for (const [k, id] of Object.entries({ inv4Place: a.investmentDb.depositTx, inv3Place: b.investmentDb.depositTx })) {
    const r = A.p.getByTestId(`transaction-row-${id}`);
    d.ui[k] = { rowPresent: await r.count(), reverseOffered: await A.p.getByTestId(`reversal-action-${id}`).count(), reverseButtonsInRow: (await r.count()) ? await r.getByRole("button", { name: "Reverse" }).count() : null, rowText: (await r.count()) ? norm(await r.innerText()) : null };
  }
  d.files = await snap(A.p, "d1-transactions-no-reverse", [1440]);
  for (const k of Object.keys(d.ui)) if (d.ui[k].rowPresent) await crop(A.p.getByTestId(`transaction-row-${k === "inv4Place" ? a.investmentDb.depositTx : b.investmentDb.depositTx}`), `d1-${k}-row-crop`);
  d.txCountBefore = Number(val("select count(*) from T.trust_transactions"));
  d.probe4 = await apiPost(A.ctx, `/api/trust-transactions/${a.investmentDb.depositTx}/reverse`, { reason: "OBS probe - expected refusal", reversalDate: today }, "d-reverse-inv-place-s86-4", "alice");
  d.probe3 = await apiPost(A.ctx, `/api/trust-transactions/${b.investmentDb.depositTx}/reverse`, { reason: "OBS probe - expected refusal", reversalDate: today }, "d-reverse-firm-inv-place-s86-3", "alice");
  d.txCountAfter = Number(val("select count(*) from T.trust_transactions"));
  d.statusesAfter = rowsA(`select transaction_type, status, coalesce(reversed_by_id::text,'<null>') from T.trust_transactions where id in ('${a.investmentDb.depositTx}','${b.investmentDb.depositTx}')`);
  const msg = (r) => JSON.stringify(r.response.body);
  d.checks = { uiNoReverse: Object.values(d.ui).every((x) => x.rowPresent && x.reverseOffered === 0 && x.reverseButtonsInRow === 0),
    refused4: d.probe4.response.status >= 400 && d.probe4.response.status < 500 && /Withdraw the investment instead/.test(msg(d.probe4)),
    refused3: d.probe3.response.status >= 400 && d.probe3.response.status < 500 && /Withdraw the investment instead/.test(msg(d.probe3)),
    nothingCreated: d.txCountAfter === d.txCountBefore && d.statusesAfter.every((r) => r[2] === "<null>") };
  save();

  // ── e: close refused for the investment account holding the s86(3) investment ──
  const e = (result.steps.e = {});
  await A.p.goto(`${ORG}/settings/fees?tab=trust-accounting`, { waitUntil: "load" });
  await A.p.getByTestId("trust-accounts-section").waitFor({ timeout: 60_000 });
  await A.p.waitForTimeout(800);
  const sec = A.p.getByTestId("trust-accounts-section");
  e.closeControlsInUi = await sec.getByRole("button", { name: /close/i }).count() + await sec.getByRole("menuitem", { name: /close/i }).count();
  e.files = await snap(A.p, "e1-settings-trust-accounts", [1440]);
  await crop(sec, "e1-settings-trust-accounts-crop");
  e.settingsScan = await scanRaw(sec, "e-settings-trust-accounts-card-KNOWN-F4");
  e.before = rowsA(`select status, coalesce(closed_date::text,'<null>') from T.trust_accounts where id='${ids.inv3Account}'`)[0];
  e.probe = await apiPost(A.ctx, `/api/trust-accounts/${ids.inv3Account}/close`, undefined, "e-close-s86-3-investment-account", "alice");
  e.after = rowsA(`select status, coalesce(closed_date::text,'<null>') from T.trust_accounts where id='${ids.inv3Account}'`)[0];
  e.checks = { refused: e.probe.response.status >= 400 && e.probe.response.status < 500 && /has not been withdrawn/.test(JSON.stringify(e.probe.response.body)), unchanged: e.before.join() === e.after.join() && e.after[0] === "ACTIVE" };
  save();

  // ── g: register-table clip probe on the final register (read-only) ──
  await A.p.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await A.p.getByTestId("investments-table").waitFor({ timeout: 60_000 });
  await clip(A.p, "register-table", "[data-testid='investments-table']", { extra: { fn: () => {
    const t = document.querySelector("[data-testid='investments-table']"); const w = t.parentElement; const vw = document.documentElement.clientWidth;
    const badges = [...t.querySelectorAll("[data-slot='badge']")].map((x) => ({ text: x.innerText, scrollWidth: x.scrollWidth, clientWidth: x.clientWidth, truncated: x.scrollWidth > x.clientWidth + 1, right: Math.round(x.getBoundingClientRect().right) }));
    const card = t.closest("[data-slot='card']") || w; const cr = card.getBoundingClientRect();
    return { tableScrollWidth: t.scrollWidth, wrapClientWidth: w.clientWidth, wrapOverflowX: getComputedStyle(w).overflowX, horizontalScroll: t.scrollWidth > w.clientWidth + 1, cardRight: Math.round(cr.right), vw, cardInsideViewport: cr.right <= vw + 0.5, badges };
  }, arg: null } }, [1280, 1440, 1920]);
  for (const w of [1280, 1440]) { await A.p.setViewportSize({ width: w, height: 1100 }); await A.p.waitForTimeout(500); await A.p.getByTestId("investments-table").screenshot({ path: `${OUT}/g-register-table-${w}-crop.png` }); }
  await A.p.setViewportSize({ width: 1440, height: 1100 });
  await scanRaw(A.p.locator("main").first(), "g-investments-page-final");

  result.finishedAt = new Date().toISOString();
  const all = (o) => Object.values(o || {}).every(Boolean);
  result.summary = { run: RUN, a: a.checks, b: b.checks, c: c.checks, d: d.checks, e: e.checks,
    aPass: all(a.checks), bPass: all(b.checks), cPass: all(c.checks), dPass: all(d.checks), ePass: all(e.checks),
    fScans: result.steps.f.scans.map((s) => ({ label: s.label, raw: s.rawInTextContent, cssUpper: s.cssUppercaseOnly })),
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length };
  save();
  console.log(JSON.stringify(result.summary, null, 2));
  await browser.close();
})().catch(async (e) => { result.error = String(e.stack || e); save(); console.error("OBS FAILED", e); process.exit(1); });
