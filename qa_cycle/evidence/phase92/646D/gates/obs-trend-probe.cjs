// 646D OBS supplement (stack #3, fresh): does the trust balance trend chart draw its series?
// State changes through the UI only (Alice records two client deposits); psql read-only; API GET only.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-646D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const BASE = "http://localhost:3001", BACKEND = "http://localhost:8081", SLUG = "e2e-test-org";
const OUT = `${W}/qa_cycle/evidence/phase92/646D/gates/obs/trend-probe`;
fs.mkdirSync(OUT, { recursive: true });
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const psqlA = (q) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const S = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const r = { today, run: RUN, consoleErrors: [], pageErrors: [], deposits: [] };
(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const page = await ctx.newPage();
  page.on("console", (m) => { if (m.type() === "error") r.consoleErrors.push({ url: page.url(), text: m.text() }); });
  page.on("pageerror", (e) => r.pageErrors.push({ url: page.url(), text: String(e) }));
  await page.goto(`${BASE}/mock-login`); await page.locator("#user-select").selectOption("user_e2e_alice");
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); await page.waitForURL(/\/org\//, { timeout: 60_000 });
  const client = psqlA(`select c.name||'|'||p.name from ${S}.customers c join ${S}.projects p on p.customer_id=c.id where p.status='ACTIVE' order by c.created_at, p.created_at limit 1`).split("|");
  r.client = client;
  for (const [date, amount] of [["2026-10-20", "2000"], [today, "5000"]]) {
    const ref = `DEP/OBS646D-TREND/${RUN}/${date}`;
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`);
    await page.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 30_000 });
    await page.getByTestId("record-transaction-button").click();
    await page.getByRole("menuitem", { name: "Record Deposit" }).click();
    const d = page.getByRole("dialog", { name: "Record Deposit" });
    await d.waitFor();
    await d.getByTestId("trust-deposit-customer-trigger").click();
    await page.getByRole("option", { name: new RegExp(`^${client[0].replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(\\s|$)`) }).click();
    await d.getByTestId("trust-deposit-matter-trigger").click();
    await page.getByRole("option", { name: new RegExp(client[1].replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) }).first().click();
    await d.getByLabel("Amount").fill(amount); await d.getByLabel("Reference").fill(ref);
    await d.getByLabel(/Description/).fill("OBS 646D trend probe"); await d.getByLabel("Transaction Date").fill(date);
    await d.getByRole("button", { name: "Record Deposit" }).click(); await d.waitFor({ state: "hidden", timeout: 30_000 });
    r.deposits.push({ date, amount, ref, sql: psqlA(`select transaction_date||'|'||amount||'|'||status||'|'||(posted_at is not null) from ${S}.trust_transactions where reference='${ref}'`) });
  }
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  const chart = page.getByTestId("trust-balance-trend").first();
  await chart.waitFor({ timeout: 60_000 });
  r.frames = [];
  for (const ms of [0, 1000, 3000, 8000, 15000]) {
    if (ms) await page.waitForTimeout(ms - (r.frames.at(-1)?.at ?? 0));
    const paths = await chart.locator("svg path.recharts-curve, svg path.recharts-area-curve, svg path.recharts-area-area").evaluateAll((ps) => ps.map((p) => ({ cls: p.getAttribute("class"), d: (p.getAttribute("d") || "").slice(0, 400) })));
    const clips = await chart.locator("svg clipPath rect").evaluateAll((rs) => rs.map((x) => ({ w: x.getAttribute("width"), h: x.getAttribute("height") })));
    await chart.screenshot({ path: `${OUT}/trend-${String(ms).padStart(5, "0")}ms.png` });
    r.frames.push({ at: ms, paths, clips });
  }
  r.summary = await chart.textContent();
  const tbl = chart.getByRole("button", { name: /View as table/ });
  if (await tbl.count()) { await tbl.click(); await page.waitForTimeout(800); r.table = await page.locator("table").last().innerText().catch(() => null); await page.screenshot({ path: `${OUT}/trend-table.png`, fullPage: true }); }
  const tok = (await ctx.cookies()).find((c) => c.name === "mock-auth-token");
  const acct = psqlA(`select id from ${S}.trust_accounts limit 1`);
  const res = await ctx.request.get(`${BACKEND}/api/dashboard/trust-balance-trend?accountId=${acct}&months=12`, { headers: { Authorization: `Bearer ${tok.value}` } });
  r.api = { status: res.status(), body: await res.json() };
  r.sqlMonthEnds = psqlA(`select '2026-10-31', sum(cashbook_effect), sum(ledger_effect) from ${S}.trust_transactions where posted_at is not null and transaction_date <= date '2026-10-31' union all select '${today}', sum(cashbook_effect), sum(ledger_effect) from ${S}.trust_transactions where posted_at is not null and transaction_date <= date '${today}'`);
  fs.writeFileSync(`${OUT}/trend-probe-result.json`, JSON.stringify(r, null, 2));
  console.log(JSON.stringify({ ...r, frames: r.frames.map((f) => ({ at: f.at, n: f.paths.length, d0: f.paths[0]?.d.slice(0, 160), clips: f.clips })) }, null, 2));
  await browser.close();
})().catch((e) => { console.error("PROBE FAILED", e); fs.writeFileSync(`${OUT}/trend-probe-result.json`, JSON.stringify({ ...r, fatal: String(e.stack || e) }, null, 2)); process.exit(1); });
