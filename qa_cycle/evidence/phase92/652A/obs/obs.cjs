// 652A OBS gate: headless Chromium against a fresh e2e mock-auth stack (:3001) as Alice. Every
// reconciliation change goes through the wizard UI; setup money (a deposit, a payment Bob approves)
// goes through the backend API as the seeded users. psql is read-only evidence.
//
// e/f run first, on a reconciliation whose calculate still succeeds: one calculate request is
// aborted (route), then a successful action clears the stale marking. Then tab B imports S2 (a line
// on the period end, in S1's gap), so every later calculate is refused by the period rule (646C):
// a match, b exclude, c unmatch, d record — each saved, each followed by a refused recalculation.
// g probes the layout of the banner and the stale line at 1280 and 1440.
//
// Run from the worktree's frontend/: NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652A/obs/obs.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(path.resolve(process.cwd(), "node_modules/@playwright/test"));

const BASE = "http://localhost:3001";
const API = "http://localhost:8081";
const OUT = __dirname;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
const addDays = (iso, n) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
// PERIOD_END_OFFSET_DAYS moves the period end back from today, so a re-run on the same stack is not
// refused at create by the S2 an earlier run imported.
const periodEnd = addDays(today, -Number(process.env.PERIOD_END_OFFSET_DAYS || 0));
const result = { run: RUN, startedAt: new Date().toISOString(), periodEnd, steps: {}, calculateResponses: [], pageErrors: [], consoleErrors: [] };

const psql = (q) =>
  execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const SCHEMA = psql("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));

async function token(user, role) {
  const res = await fetch("http://localhost:8090/token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: `user_e2e_${user}`, orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: role }),
  });
  return (await res.json()).access_token;
}
async function api(tok, url, body) {
  const res = await fetch(`${API}${url}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  if (!res.ok) throw new Error(`${url} → ${res.status} ${JSON.stringify(json)}`);
  return json;
}

function writeCsv(name, lines) {
  let bal = 0;
  const rows = [`Standard Bank Trust Account - 652A OBS ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of lines) {
    bal = +(bal + l.amount).toFixed(2);
    rows.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`);
  }
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(file, rows.join("\n") + "\n");
  return file;
}

async function wizardToUpload(page, accountId, csvFile) {
  await page.goto(`${BASE}/org/e2e-test-org/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(periodEnd);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(csvFile);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
}

const squash = (s) => s.replace(/\s+/g, " ").trim();
// The page after an action has settled: banner, stale line, the line, the summary, the database.
async function observe(page, label, lineId, reconId) {
  await page.waitForTimeout(2000);
  const text = async (id) => {
    const el = page.getByTestId(id);
    return (await el.count()) ? squash(await el.innerText()) : null;
  };
  const state = {
    at: new Date().toISOString(),
    banner: await text("reconciliation-error"),
    stale: await text("reconciliation-figures-stale"),
    line: await text(`bank-line-${lineId}`),
    progress: await text("match-progress"),
    unresolved: await text("unresolved-line-count"),
    excluded: await text("excluded-lines-total"),
    bankCashbook: (await text("comparison-bank-cashbook"))?.slice(0, 260),
    dbLine: sql(`select match_status, coalesce(trust_transaction_id::text,''), coalesce(classification,'') from T.bank_statement_lines where id='${lineId}'`),
    dbRecon: sql(`select bank_balance, cashbook_balance, outstanding_deposits, outstanding_payments, difference, updated_at from T.trust_reconciliations where id='${reconId}'`),
    shot: `obs-${label}.png`,
  };
  await page.screenshot({ path: path.join(OUT, state.shot), fullPage: true });
  return state;
}

// g: neither the banner nor the stale line is clipped or overflows its box, the card or the page.
async function layoutProbe(page, width) {
  await page.setViewportSize({ width, height: 1400 });
  await page.waitForTimeout(800);
  const banner = page.getByTestId("reconciliation-error");
  const card = page.getByTestId("balance-summary");
  await banner.screenshot({ path: path.join(OUT, `obs-g-banner-${width}.png`) });
  await card.screenshot({ path: path.join(OUT, `obs-g-summary-${width}.png`) });
  return page.evaluate(() => {
    const probe = (el, container) => {
      const r = el.getBoundingClientRect();
      const c = container.getBoundingClientRect();
      const clippers = [];
      for (let a = el.parentElement; a; a = a.parentElement) {
        const s = getComputedStyle(a);
        if (s.overflowX === "visible" && s.overflowY === "visible") continue;
        const ar = a.getBoundingClientRect();
        if (r.left < ar.left - 0.5 || r.right > ar.right + 0.5 || r.top < ar.top - 0.5 || r.bottom > ar.bottom + 0.5)
          clippers.push(a.tagName + "." + a.className.slice(0, 40));
      }
      return {
        rect: { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: r.width, height: r.height },
        container: { left: c.left, right: c.right },
        insideContainer: r.left >= c.left - 0.5 && r.right <= c.right + 0.5,
        ownOverflow: el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight,
        clippedBy: clippers,
        text: el.innerText,
      };
    };
    const q = (id) => document.querySelector(`[data-testid="${id}"]`);
    const card = q("balance-summary");
    return {
      pageHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      viewportWidth: document.documentElement.clientWidth,
      banner: probe(q("reconciliation-error"), document.documentElement),
      stale: probe(q("reconciliation-figures-stale"), card),
      card: probe(card, document.documentElement),
    };
  });
}

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  const [clientId, matterId] = sql("select c.id, p.id from T.customers c join T.projects p on p.customer_id = c.id order by c.created_at limit 1").split("|");
  // Setup money: Alice deposits, Alice records a payment, Bob approves it (no self-approval).
  const alice = await token("alice", "org:owner");
  const bob = await token("bob", "org:admin");
  const amount = 1250;
  const deposit = await api(alice, `/api/trust-accounts/${accountId}/transactions/deposit`, {
    clientId, matterId, amount: 5000, reference: `DEP/652A/${RUN}`, description: "652A OBS deposit", transactionDate: periodEnd,
  });
  const payment = await api(alice, `/api/trust-accounts/${accountId}/transactions/payment`, {
    clientId, matterId, amount, reference: `PAY/652A/${RUN}`, description: "652A OBS payment", transactionDate: periodEnd,
  });
  const approved = await api(bob, `/api/trust-transactions/${payment.id}/approve`);
  result.setup = { accountId, clientId, matterId, depositId: deposit.id, paymentId: payment.id, paymentStatus: approved.status };

  const s1 = writeCsv("s1", [
    { date: addDays(periodEnd, -3), desc: "Unknown credit", ref: `UNK/652A/${RUN}`, amount: 700 },
    { date: addDays(periodEnd, -2), desc: "Deeds office", ref: `DEEDS/652A/${RUN}`, amount: -amount },
    { date: addDays(periodEnd, -1), desc: "Bank charges", ref: `CHG/652A/${RUN}`, amount: -25 },
    { date: addDays(periodEnd, -1), desc: "Second unknown credit", ref: `UNK2/652A/${RUN}`, amount: 300 },
  ]);
  const s2 = writeCsv("s2", [{ date: periodEnd, desc: "Service fee", ref: `SVC/652A/${RUN}`, amount: -10 }]);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push({ at: new Date().toISOString(), error: String(e) }));
  page.on("console", (m) => m.type() === "error" && result.consoleErrors.push({ at: new Date().toISOString(), text: m.text().slice(0, 300) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await page.waitForURL(/\/org\//, { timeout: 60_000 });

  // Tab A: S1 into review (the first calculate succeeds: no other statement yet)
  await wizardToUpload(page, accountId, s1);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  const stmt = sql(`select id from T.bank_statements where file_name='${path.basename(s1)}'`);
  const reconId = sql(`select id from T.trust_reconciliations where bank_statement_id='${stmt}'`);
  const L = Object.fromEntries(sql(`select line_number, id from T.bank_statement_lines where bank_statement_id='${stmt}'`).split("\n").map((r) => r.split("|")));
  Object.assign(result, { statementId: stmt, reconId, lineIds: L });
  const [unknown, deeds, charge, unknown2] = [L["1"], L["2"], L["3"], L["4"]];
  if (sql(`select count(*) from T.bank_statement_lines where bank_statement_id='${stmt}' and match_status <> 'UNMATCHED'`) !== "0")
    throw new Error("auto-match resolved a line; the scenario expects all four unmatched");

  // Calculate's body is exactly [reconId]; its RSC response carries the action's return value.
  const isCalculate = (req) => req.method() === "POST" && !!req.headers()["next-action"] && req.postData() === JSON.stringify([reconId]);
  page.on("response", async (res) => {
    if (!isCalculate(res.request())) return;
    const text = await res.text().catch((e) => `<no body: ${e}>`);
    result.calculateResponses.push({ at: new Date().toISOString(), nextAction: res.request().headers()["next-action"], status: res.status(), result: (text.match(/\{"success".*\}/) || [text.slice(0, 300)])[0] });
  });
  result.steps.review = await observe(page, "0-review", deeds, reconId);

  // e: match, with only the calculate request aborted
  let aborted = null;
  const abortCalculate = async (route) => {
    const req = route.request();
    if (!aborted && isCalculate(req)) {
      aborted = { at: new Date().toISOString(), nextAction: req.headers()["next-action"], body: req.postData() };
      return route.abort("failed");
    }
    return route.continue();
  };
  await page.route("**/trust-accounting/reconciliation/new", abortCalculate);
  await page.getByTestId(`bank-line-${deeds}`).click();
  await page.getByTestId(`match-btn-${payment.id}`).click();
  const afterAbort = await observe(page, "e-match-aborted", deeds, reconId); // the abort happens in here
  result.steps.e_matchAborted = { aborted, pageErrorsSoFar: result.pageErrors.length, ...afterAbort };
  await page.unroute("**/trust-accounting/reconciliation/new", abortCalculate);

  // f: exclude the second credit — its recalculation succeeds and clears the stale marking
  await page.getByTestId(`bank-line-${unknown2}`).click();
  await page.getByTestId(`exclude-btn-${unknown2}`).click();
  result.steps.f_excludeSucceeds = await observe(page, "f-cleared", unknown2, reconId);
  // Unmatch the deeds line again (still succeeding) so step a starts from a clean, unmatched line
  await page.getByTestId(`unmatch-btn-${deeds}`).click();
  result.steps.f_unmatchSucceeds = await observe(page, "f-unmatch-ok", deeds, reconId);

  // Tab B: import S2 (a line on the period end) — every later calculate on tab A is refused
  const pageB = await context.newPage();
  await wizardToUpload(pageB, accountId, s2);
  result.steps.importS2 = squash(await pageB.getByTestId("upload-result").innerText());
  await pageB.close();

  // a: match
  await page.getByTestId(`bank-line-${deeds}`).click();
  await page.getByTestId(`match-btn-${payment.id}`).click();
  result.steps.a_match = await observe(page, "a-match", deeds, reconId);
  // b: exclude
  await page.getByTestId(`bank-line-${unknown}`).click();
  await page.getByTestId(`exclude-btn-${unknown}`).click();
  result.steps.b_exclude = await observe(page, "b-exclude", unknown, reconId);
  // c: unmatch
  await page.getByTestId(`unmatch-btn-${deeds}`).click();
  result.steps.c_unmatch = await observe(page, "c-unmatch", deeds, reconId);
  // d: record the bank charge from the statement
  await page.getByTestId(`bank-line-${charge}`).click();
  await page.getByTestId(`record-btn-${charge}`).click();
  const dialog = page.getByTestId("record-from-statement-dialog");
  await dialog.waitFor();
  await dialog.locator("#classification-BANK_CHARGE").click();
  await dialog.locator("#charge-recoverable-no").click();
  await dialog.getByTestId("charge-vat-input").fill("0");
  await dialog.getByTestId("record-submit").click();
  await dialog.waitFor({ state: "hidden", timeout: 30_000 });
  result.steps.d_record = await observe(page, "d-record", charge, reconId);
  result.steps.d_record.dbCreated = sql(`select transaction_type, amount, status, transaction_date, ledger, charge_recoverable from T.trust_transactions where bank_statement_line_id='${charge}'`);

  // g: layout at 1280 and 1440 with the banner and the stale line showing
  result.steps.g_layout = { 1280: await layoutProbe(page, 1280), 1440: await layoutProbe(page, 1440) };

  result.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify(result, null, 2));
  await browser.close();
  console.log(JSON.stringify(result, null, 2));
})().catch((e) => {
  fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  console.error(e);
  process.exit(1);
});
