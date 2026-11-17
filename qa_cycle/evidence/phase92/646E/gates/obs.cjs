// 646E OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI (Alice / Bob via /mock-login). psql is read-only EVIDENCE.
// API reads use the browser context's own mock-auth-token (the session) against the backend, because
// the frontend fetches server-side (no /api proxy on :3001) — so a page network capture cannot see them.
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-646E/frontend/node_modules/@playwright/test"
);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = "/tmp/646E-gates";
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [] };
const norm = (s) => (s || "").replace(/[  ]/g, " ").replace(/[ \t]+/g, " ");

const sastToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
function addDays(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const D = { today: sastToday, deposit: addDays(sastToday, -3), payment: addDays(sastToday, -2), from: addDays(sastToday, -5) };
result.dates = D;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const REF = { apexDeposit: `OBS646E-DEP-${RUN}`, apexPayment: `OBS646E-PAY-${RUN}` };
result.refs = REF;
const REASON_PAY = `OBS646E payee bank rejected the EFT ${RUN}`;
const REASON_DEP = `OBS646E cheque returned unpaid ${RUN}`;

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q], { encoding: "utf8" });
}
function psqlA(q) {
  return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
}
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.writeFileSync(SQL_FILE, `-- 646E OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 646E OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
function sql(label, q) {
  const qq = q.replace(/\bT\./g, `${SCHEMA}.`);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(q.replace(/\bT\./g, `${SCHEMA}.`));
const customerId = (prefix) => val(`select id from T.customers where name like '${prefix}%' order by created_at limit 1`);
function txRows(label, where) {
  return sql(
    label,
    `select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, reference from T.trust_transactions where ${where} order by created_at`
  );
}

// ── browser helpers ─────────────────────────────────────────────────
let browser, context, page;
async function newSession(userId) {
  if (context) await context.close();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  page = await context.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") result.consoleErrors.push({ user: userId, url: page.url(), text: m.text() });
  });
  page.on("pageerror", (e) => result.pageErrors.push({ user: userId, url: page.url(), text: String(e) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(userId);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  await page.getByRole("heading").first().waitFor({ timeout: 30_000 });
}
let shot = 0;
async function snap(name) {
  shot += 1;
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await page.waitForTimeout(800);
  await page.screenshot({ path: file, fullPage: true });
  const text = await page.locator("body").innerText().catch(() => "");
  fs.writeFileSync(file.replace(/\.png$/, ".txt"), text);
  return { file: file.replace(`${OUT}/`, ""), text };
}
async function api(path) {
  const tok = (await context.cookies()).find((c) => c.name === "mock-auth-token");
  const res = await context.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${tok ? tok.value : ""}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  return { status: res.status(), body };
}
async function openTransactions(query = "") {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions${query}`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 30_000 });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 }).catch(() => {});
}
// rows of a table: id (from testid), type badge text, amount cell direction evidence, status cell
async function tableRows(prefix) {
  return page.locator(`tr[data-testid^="${prefix}"]`).evaluateAll(
    (trs, prefix) =>
      trs.map((tr) => {
        const tds = Array.from(tr.querySelectorAll("td"));
        const amt = tds[3] ? tds[3].querySelector("span") : null;
        const svg = amt ? amt.querySelector("svg") : null;
        const cls = amt ? amt.getAttribute("class") || "" : "";
        const svgCls = svg ? svg.getAttribute("class") || "" : "";
        const dir = /arrow-down-left/.test(svgCls) ? "IN" : /arrow-up-right/.test(svgCls) ? "OUT" : "NONE";
        return {
          id: tr.getAttribute("data-testid").slice(prefix.length),
          cells: tds.map((td) => td.innerText.trim().replace(/\s+/g, " ")),
          typeBadge: tds[2] ? tds[2].innerText.trim() : null,
          green: /text-green-600/.test(cls),
          arrow: dir,
          svgClass: svgCls,
        };
      }),
    prefix
  );
}
async function pills() {
  const grp = page.getByTestId("type-filters");
  return grp.locator("a").evaluateAll((as) =>
    as.map((a) => ({ text: a.innerText.trim(), href: a.getAttribute("href"), active: /bg-slate-900/.test(a.className) }))
  );
}
async function approve(id) {
  const actions = page.getByTestId(`approval-actions-${id}`);
  await actions.getByRole("button", { name: "Approve" }).click();
  await page.waitForTimeout(2500);
  return (await actions.innerText().catch(() => "")).replace(/\s+/g, " ").trim();
}
async function pickOption(triggerTestId, dialog, re) {
  await dialog.getByTestId(triggerTestId).click();
  await page.getByRole("option", { name: re }).first().click();
}
async function openReverseDialog(id) {
  await page.getByTestId(`reversal-action-${id}`).getByTestId("reverse-button").click();
  const dialog = page.getByRole("dialog", { name: "Reverse Transaction" });
  await dialog.waitFor({ timeout: 15_000 });
  return dialog;
}
async function submitReverse(dialog, reason) {
  await dialog.getByTestId("reversal-reason-input").fill(reason);
  await dialog.getByTestId("confirm-reverse-button").click();
  const hidden = await dialog.waitFor({ state: "hidden", timeout: 15_000 }).then(() => true).catch(() => false);
  const err = hidden ? null : await dialog.locator("p.text-destructive").innerText().catch(() => null);
  return { closed: hidden, error: err };
}
async function recordViaDialog(menu, dialogName, f) {
  await openTransactions();
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: menu }).click();
  const dialog = page.getByRole("dialog", { name: dialogName });
  await dialog.waitFor({ timeout: 15_000 });
  await pickOption(f.customerTrigger, dialog, f.customerRe);
  await pickOption(f.matterTrigger, dialog, f.matterRe);
  await dialog.getByLabel("Amount").fill(f.amount);
  await dialog.getByLabel("Reference").fill(f.reference);
  await dialog.getByLabel(/Description/).fill(f.description);
  await dialog.getByLabel("Transaction Date").fill(f.date);
  await snap(`${f.shot}-dialog`);
  await dialog.getByRole("button", { name: dialogName }).click();
  const hidden = await dialog.waitFor({ state: "hidden", timeout: 20_000 }).then(() => true).catch(() => false);
  const err = hidden ? null : await dialog.locator("p.text-destructive").innerText().catch(() => "<no error text>");
  return { closed: hidden, error: err };
}
async function runReport(slug, params, shotName) {
  await page.goto(`${BASE}/org/${SLUG}/reports/${slug}`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Run Report" }).waitFor({ timeout: 30_000 });
  for (const [param, re, search, raw] of params.selects) {
    const trigger = page.locator(`#param-${param}`);
    if ((await trigger.evaluate((el) => el.tagName)) === "INPUT") {
      await trigger.fill(raw);
    } else {
      await trigger.click();
      if (search) await page.getByPlaceholder(/search/i).last().fill(search).catch(() => {});
      await page.getByRole("option", { name: re }).first().click();
    }
  }
  for (const [param, v] of params.dates) await page.locator(`#param-${param}`).fill(v);
  await page.getByRole("button", { name: "Run Report" }).click();
  await page.waitForTimeout(4000);
  const s = await snap(shotName);
  const table = await page.locator("main table").last().evaluate((t) => {
    const heads = Array.from(t.querySelectorAll("thead th")).map((th) => th.innerText.trim());
    const rows = Array.from(t.querySelectorAll("tbody tr")).map((tr) => Array.from(tr.querySelectorAll("td")).map((td) => td.innerText.trim()));
    return { heads, rows };
  }).catch((e) => ({ error: String(e) }));
  return { shot: s.file, table, text: s.text.slice(Math.max(0, s.text.indexOf("Run Report"))) };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const ids = {};
  ids.account = val("select id from T.trust_accounts order by created_at limit 1");
  ids.qc = customerId("QuickCollect Services");
  ids.apex = customerId("Apex Holdings");
  ids.moroka = customerId("Moroka Family Trust");
  ids.qcDeposit = val(`select id from T.trust_transactions where customer_id='${ids.qc}' and transaction_type='DEPOSIT' and status='RECORDED' order by created_at limit 1`);
  result.ids = ids;
  sql("baseline: customers", "select id, name from T.customers order by created_at");
  txRows("baseline: all trust transactions", "true");

  // ════ a. Type registry ════
  const a = (result.steps.a = {});
  await newSession("user_e2e_alice");
  const reg = await api("/api/trust-transaction-types");
  fs.writeFileSync(`${OUT}/a-trust-transaction-types.json`, JSON.stringify(reg, null, 2));
  const types = Array.isArray(reg.body) ? reg.body : [];
  a.status = reg.status;
  a.count = types.length;
  a.deposit = types.find((t) => t.code === "DEPOSIT");
  a.reversal = types.find((t) => t.code === "REVERSAL");
  a.labels = types.map((t) => `${t.code}=${t.label}`);
  a.keys = types[0] ? Object.keys(types[0]) : [];
  const anon = await context.request.get(`${BACKEND}/api/trust-transaction-types`, { headers: { Authorization: "" } });
  a.noTokenStatus = anon.status();
  const codes = types.map((t) => t.code);
  const codeRe = new RegExp(`\\b(${codes.join("|")}|INTEREST_CREDIT|INTEREST_LPFF|DISBURSEMENT_PAYMENT)\\b`, "g");

  // ════ b. Transactions page (Alice) ════
  const b = (result.steps.b = {});
  await openTransactions();
  b.pills = await pills();
  const typePills = b.pills.filter((p) => p.text !== "All");
  b.pillCount = typePills.length;
  b.pillLabelsMatchRegistryInOrder = JSON.stringify(typePills.map((p) => p.text)) === JSON.stringify(types.map((t) => t.label));
  b.pillHrefCodes = typePills.map((p) => (p.href.match(/type=([A-Z_]+)/) || [])[1]);
  b.pillHrefsMatchRegistry = JSON.stringify(b.pillHrefCodes) === JSON.stringify(codes);
  b.interestPills = typePills.filter((p) => /Interest Credit|Interest LPFF/i.test(p.text)).map((p) => p.text);
  b.titleCaseOldPills = typePills.filter((p) => /^(Transfer In|Transfer Out|Fee Transfer|Disbursement Payment)$/.test(p.text)).map((p) => p.text);
  const s1 = await snap("b1-transactions-page");
  b.shot = s1.file;
  b.rows = await tableRows("transaction-row-");
  b.rawCodesInBody = [...new Set(norm(s1.text).match(codeRe) || [])];
  // click the "Deposit" pill → filtered
  const depLabel = a.deposit ? a.deposit.label : "Deposit";
  await page.getByTestId("type-filters").getByRole("link", { name: depLabel, exact: true }).click();
  await page.waitForURL(/type=DEPOSIT/, { timeout: 20_000 });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
  b.filteredUrl = page.url();
  b.filteredRows = await tableRows("transaction-row-");
  b.filteredAllDeposit = b.filteredRows.length > 0 && b.filteredRows.every((r) => r.typeBadge === depLabel);
  b.filteredActivePill = (await pills()).filter((p) => p.active).map((p) => p.text);
  b.filteredShot = (await snap("b2-transactions-filtered-deposit")).file;
  b.sqlDepositCount = val("select count(*) from T.trust_transactions where transaction_type='DEPOSIT'");

  // ════ c. F8 — reverse an approved PAYMENT (Apex): REVERSAL must render as an inflow ════
  const c = (result.steps.c = {});
  c.deposit = await recordViaDialog("Record Deposit", "Record Deposit", {
    customerTrigger: "trust-deposit-customer-trigger", customerRe: /^Apex Holdings/,
    matterTrigger: "trust-deposit-matter-trigger", matterRe: /Apex/,
    amount: "20000", reference: REF.apexDeposit, description: "OBS 646E funding deposit", date: D.deposit, shot: "c1-apex-deposit",
  });
  ids.apexDeposit = val(`select id from T.trust_transactions where reference='${REF.apexDeposit}'`);
  c.payment = await recordViaDialog("Record Payment", "Record Payment", {
    customerTrigger: "trust-payment-customer-trigger", customerRe: /^Apex Holdings/,
    matterTrigger: "trust-payment-matter-trigger", matterRe: /Apex/,
    amount: "4000", reference: REF.apexPayment, description: "OBS 646E payee: Deeds Office", date: D.payment, shot: "c2-apex-payment",
  });
  ids.apexPayment = val(`select id from T.trust_transactions where reference='${REF.apexPayment}'`);
  await newSession("user_e2e_bob");
  await openTransactions();
  c.bobApprovePayment = await approve(ids.apexPayment);
  await page.reload({ waitUntil: "load" });
  await page.getByTestId("transactions-table").waitFor({ timeout: 30_000 });
  c.paymentRowApproved = (await tableRows("transaction-row-")).find((r) => r.id === ids.apexPayment);
  c.approvedShot = (await snap("c3-payment-approved-bob")).file;
  await newSession("user_e2e_alice");
  await openTransactions();
  c.reverseOfferedOnApprovedPayment = await page.getByTestId(`reversal-action-${ids.apexPayment}`).isVisible().catch(() => false);
  const dlgP = await openReverseDialog(ids.apexPayment);
  c.dialogText = (await dlgP.innerText()).replace(/\s+/g, " ").trim();
  c.reverseSubmit = await submitReverse(dlgP, REASON_PAY);
  ids.apexPayContra = val(`select id from T.trust_transactions where reversal_of='${ids.apexPayment}'`);
  await openTransactions();
  const rowsC = await tableRows("transaction-row-");
  c.contraRow = rowsC.find((r) => r.id === ids.apexPayContra);
  c.paymentRowAfter = rowsC.find((r) => r.id === ids.apexPayment);
  c.reversedShot = (await snap("c4-payment-reversed-contra-inflow")).file;
  c.apiContra = await api(`/api/trust-accounts/${ids.account}/transactions/${ids.apexPayContra}`);
  c.apiPayment = await api(`/api/trust-accounts/${ids.account}/transactions/${ids.apexPayment}`);
  txRows("c: Apex deposit + payment + contra", `id in ('${ids.apexDeposit}','${ids.apexPayment}','${ids.apexPayContra}')`);

  // ════ d. R1 — reverse the RECORDED QuickCollect deposit, leave its contra pending ════
  const d = (result.steps.d = {});
  await openTransactions();
  d.reverseOfferedBefore = await page.getByTestId(`reversal-action-${ids.qcDeposit}`).isVisible().catch(() => false);
  d.apiDepositBefore = await api(`/api/trust-accounts/${ids.account}/transactions/${ids.qcDeposit}`);
  const dlgD = await openReverseDialog(ids.qcDeposit);
  d.dialogText = (await dlgD.innerText()).replace(/\s+/g, " ").trim();
  d.reverseSubmit = await submitReverse(dlgD, REASON_DEP);
  ids.qcContra = val(`select id from T.trust_transactions where reversal_of='${ids.qcDeposit}'`);
  await openTransactions();
  const rowsD = await tableRows("transaction-row-");
  d.contraRow = rowsD.find((r) => r.id === ids.qcContra);
  d.depositRow = rowsD.find((r) => r.id === ids.qcDeposit);
  d.reverseOfferedWhilePending = await page.getByTestId(`reversal-action-${ids.qcDeposit}`).count();
  d.shot = (await snap("d1-deposit-contra-pending")).file;
  d.apiDeposit = await api(`/api/trust-accounts/${ids.account}/transactions/${ids.qcDeposit}`);
  d.apiContra = await api(`/api/trust-accounts/${ids.account}/transactions/${ids.qcContra}`);
  txRows("d: QC deposit + pending contra", `id in ('${ids.qcDeposit}','${ids.qcContra}')`);

  // ════ e. Client ledger page vs Transactions page (Apex, then QuickCollect) ════
  const e = (result.steps.e = {});
  await openTransactions();
  const allTxRows = await tableRows("transaction-row-");
  const txLabelById = Object.fromEntries(allTxRows.map((r) => [r.id, { label: r.typeBadge, arrow: r.arrow, green: r.green }]));
  const expectedClientPills = types
    .filter((t) => t.ledger === "CLIENT" || t.ledger === "CLIENT_INVESTMENT" || t.code === "REVERSAL")
    .map((t) => t.label);
  e.expectedClientPills = expectedClientPills;
  for (const [key, cid] of [["apex", ids.apex], ["qc", ids.qc]]) {
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting/client-ledgers/${cid}`, { waitUntil: "load" });
    await page.getByTestId("client-detail-page").waitFor({ timeout: 30_000 });
    await page.getByTestId("history-table").waitFor({ timeout: 30_000 }).catch(() => {});
    const r = (e[key] = {});
    r.pills = (await pills()).filter((p) => p.text !== "All").map((p) => p.text);
    r.pillsMatchExpected = JSON.stringify(r.pills) === JSON.stringify(expectedClientPills);
    r.interestPills = r.pills.filter((p) => /Interest/i.test(p));
    r.rows = await tableRows("history-row-");
    r.comparison = r.rows.map((row) => ({
      id: row.id,
      ledgerLabel: row.typeBadge,
      txPageLabel: txLabelById[row.id] ? txLabelById[row.id].label : "<not on transactions page 1>",
      sameLabel: txLabelById[row.id] ? txLabelById[row.id].label === row.typeBadge : null,
      ledgerArrow: row.arrow,
      txPageArrow: txLabelById[row.id] ? txLabelById[row.id].arrow : null,
    }));
    r.allSame = r.comparison.length > 0 && r.comparison.every((x) => x.sameLabel === true);
    const s = await snap(`e-${key}-client-ledger`);
    r.shot = s.file;
    r.rawCodesInBody = [...new Set(norm(s.text).match(codeRe) || [])];
  }

  // ════ f. Trust overview Recent Transactions ════
  const f = (result.steps.f = {});
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 30_000 });
  await page.waitForTimeout(1500);
  f.recent = await page.locator("[data-direction]").evaluateAll((els) =>
    els.map((el) => {
      const tr = el.closest("tr");
      const tds = tr ? Array.from(tr.querySelectorAll("td")).map((td) => td.innerText.trim().replace(/\s+/g, " ")) : [];
      return { direction: el.getAttribute("data-direction"), svg: (el.querySelector("svg") || { getAttribute: () => "" }).getAttribute("class") || "", cells: tds };
    })
  );
  f.directions = [...new Set(f.recent.map((x) => x.direction))];
  const s6 = await snap("f1-trust-overview");
  f.shot = s6.file;
  f.rawCodesInBody = [...new Set(norm(s6.text).match(codeRe) || [])];

  // ════ g. Firm reports (F7) ════
  const g = (result.steps.g = {});
  try {
    g.statement = await runReport("client-ledger-statement", {
      selects: [["trust_account_id", /Trust Account/i, null, ids.account], ["customer_id", /^Apex Holdings/, "Apex", ids.apex]],
      dates: [["dateFrom", D.from], ["dateTo", D.today]],
    }, "g1-client-ledger-statement-apex");
    g.statement.rawCodes = [...new Set(norm(g.statement.text).match(codeRe) || [])];
    g.statement.showsReversalReason = g.statement.text.includes(REASON_PAY);
  } catch (err) {
    g.statementError = String(err);
    await snap("g1-statement-error").catch(() => {});
  }
  try {
    g.receiptsPayments = await runReport("trust-receipts-payments", {
      selects: [["trust_account_id", /Trust Account/i, null, ids.account]],
      dates: [["dateFrom", D.from], ["dateTo", D.today]],
    }, "g2-receipts-payments");
    g.receiptsPayments.rawCodes = [...new Set(norm(g.receiptsPayments.text).match(codeRe) || [])];
  } catch (err) {
    g.receiptsPaymentsError = String(err);
    await snap("g2-receipts-payments-error").catch(() => {});
  }
  sql("g: statement-equivalent rows (posted, CLIENT, Apex, from..today)",
    `select transaction_date, transaction_type, status, ledger_effect, reference from T.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='${ids.apex}' and transaction_date between '${D.from}' and '${D.today}' order by transaction_date, created_at`);
  sql("g: receipts-payments-equivalent rows (from..today)",
    `select transaction_date, transaction_type, status, cashbook_effect, reference from T.trust_transactions where transaction_date between '${D.from}' and '${D.today}' order by transaction_date, created_at`);

  sql("final: EQ-2",
    `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers`);
  txRows("final: all trust transactions", "true");

  result.ids = ids;
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (err) => {
  console.error("OBS FAILED:", err);
  try { await snap("zz-fatal"); } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String(err) }, null, 2));
  process.exit(1);
});
