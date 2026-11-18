// 648A OBS extra @ b6afe8c69 (run after obs.cjs on the same stack).
// c2: discriminate the withdrawal date input's min (deposit date) from max (today): Alice places a R1 000 s86(3)
//     investment through the UI with a PAST deposit date on the OBS s86(3) account, Bob approves it on Transactions,
//     Alice opens its Withdraw dialog, reads min/max, tries a date before the deposit date (expects a refusal and no
//     POST), then cancels — no withdrawal is made.
// k2: the client trust statement preview for the OBS s86(4) client after the withdrawal (preview only, nothing saved).
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648A";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const EV = `${W}/qa_cycle/evidence/phase92/648A/gates-b6afe8c69`;
const OUT = `${EV}/obs`;
const ORG = "http://localhost:3001/org/e2e-test-org";
const R1 = JSON.parse(fs.readFileSync(`${OUT}/obs-result.json`, "utf8"));
const ids = R1.ids, RUN = R1.run;
const psqlA = (q) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const val = (q) => psqlA(q.replace(/\bT\./g, `${SCHEMA}.`));
const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
const saDate = (d) => new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(d);
const TODAY = saDate(new Date()), PAST = saDate(new Date(Date.now() - 8 * 86400000)), BEFORE_PAST = saDate(new Date(Date.now() - 10 * 86400000));
const res = { at: new Date().toISOString(), today: TODAY, past: PAST, beforePast: BEFORE_PAST, consoleErrors: [], pageErrors: [], posts: [], steps: {} };
const save = () => fs.writeFileSync(`${OUT}/obs-extra-result.json`, JSON.stringify(res, null, 2));
let browser;
async function session(name, user) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ tab: name, url: p.url(), text: m.text() }); });
  p.on("pageerror", (e) => res.pageErrors.push({ tab: name, text: String(e) }));
  p.on("request", (r) => { if (r.method() === "POST") res.posts.push({ tab: name, at: new Date().toISOString(), url: r.url() }); });
  await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
  await p.locator("#user-select").selectOption(user);
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.getByRole("heading").first().waitFor({ timeout: 30_000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  return p;
}
(async () => {
  browser = await chromium.launch({ headless: true });
  const A = await session("alice", "user_e2e_alice"), B = await session("bob", "user_e2e_bob");
  const c2 = (res.steps.c2 = {});
  // place (UI), past deposit date, on the existing OBS s86(3) account
  await A.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await A.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  await A.getByRole("button", { name: "Place Investment" }).first().click();
  const dlg = A.getByTestId("place-investment-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  await dlg.getByRole("radio", { name: "Section 86(3) — the firm invests surplus trust money" }).check();
  await dlg.getByTestId("investment-account-select").click();
  await A.getByRole("option", { name: new RegExp(`OBS s86\\(3\\) Investments ${RUN}`) }).click();
  await dlg.getByLabel("Institution").fill("Standard Bank");
  await dlg.getByLabel("Account Number").fill(`OBSP3-${RUN}`);
  await dlg.getByLabel("Principal").fill("1000");
  await dlg.getByLabel("Interest Rate %").fill("6");
  await dlg.getByLabel("Deposit Date").fill(PAST);
  await dlg.getByLabel("Notes (optional)").fill(`OBS ${RUN} past-dated`);
  await dlg.getByRole("button", { name: "Place Investment" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  for (let i = 0; i < 40 && !val(`select id from T.trust_investments where account_number='OBSP3-${RUN}'`); i++) await A.waitForTimeout(500);
  const [invId, txId, dep] = val(`select id, deposit_transaction_id, deposit_date from T.trust_investments where account_number='OBSP3-${RUN}'`).split("|");
  c2.investment = { id: invId, depositTx: txId, depositDate: dep };
  // Bob approves (UI)
  await B.goto(`${ORG}/trust-accounting/transactions`, { waitUntil: "load" });
  await B.getByRole("heading", { level: 1, name: "Transactions" }).waitFor({ timeout: 60_000 });
  await B.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve" }).click();
  await B.waitForFunction((id) => { const r = document.querySelector(`[data-testid='transaction-row-${id}']`); return r && /approved/i.test(r.innerText) && !/awaiting/i.test(r.innerText); }, txId, { timeout: 30_000 });
  c2.approvedRow = val(`select transaction_type, status, posted_at is not null from T.trust_transactions where id='${txId}'`);
  // Alice opens Withdraw, reads min/max, tries a date before the deposit date, cancels
  await A.goto(`${ORG}/trust-accounting/investments`, { waitUntil: "load" });
  await A.getByTestId("investments-page").waitFor({ timeout: 60_000 });
  const row = A.locator("main tbody tr").filter({ hasText: `OBSP3-${RUN}` }).first();
  c2.rowText = norm(await row.innerText());
  await row.getByRole("button", { name: "Withdraw" }).click();
  const wd = A.getByTestId("withdraw-investment-dialog");
  await wd.waitFor({ timeout: 15_000 });
  const inp = wd.locator("input[type='date']").first();
  c2.input = await inp.evaluate((el) => ({ name: el.name, min: el.getAttribute("min"), max: el.getAttribute("max"), value: el.value }));
  await A.screenshot({ path: `${OUT}/x1-c2-withdraw-dialog-past-deposit-1440.png` });
  const nTx = Number(val("select count(*) from T.trust_transactions")), p0 = res.posts.length;
  await inp.fill(BEFORE_PAST);
  c2.beforeDepositTry = { typed: BEFORE_PAST, validity: await inp.evaluate((el) => ({ rangeUnderflow: el.validity.rangeUnderflow, valid: el.validity.valid, validationMessage: el.validationMessage })) };
  await wd.getByRole("button", { name: "Confirm Withdrawal" }).click();
  await A.waitForTimeout(2500);
  c2.beforeDepositTry.dialogStillOpen = await wd.isVisible();
  c2.beforeDepositTry.dialogText = norm(await wd.innerText());
  c2.beforeDepositTry.posts = res.posts.slice(p0).map((x) => x.url);
  c2.beforeDepositTry.txBefore = nTx; c2.beforeDepositTry.txAfter = Number(val("select count(*) from T.trust_transactions"));
  await A.screenshot({ path: `${OUT}/x2-c2-withdraw-before-deposit-try-1440.png` });
  await wd.getByRole("button", { name: "Cancel" }).click();
  await wd.waitFor({ state: "hidden", timeout: 10_000 });
  c2.statusAfter = val(`select status from T.trust_investments where id='${invId}'`);
  c2.checks = { minIsDeposit: c2.input.min === dep, maxIsToday: c2.input.max === TODAY, minDiffersFromMax: c2.input.min !== c2.input.max,
    beforeDepositRefused: c2.beforeDepositTry.dialogStillOpen && c2.beforeDepositTry.txAfter === nTx && c2.beforeDepositTry.posts.length === 0, stillActive: c2.statusAfter === "ACTIVE" };
  save();
  // k2: client trust statement preview after the withdrawal (read-only)
  const k2 = (res.steps.k2 = {});
  await A.goto(`${ORG}/clients/${ids.client}`, { waitUntil: "load" });
  await A.waitForLoadState("networkidle").catch(() => {});
  await A.getByRole("button", { name: /Generate Document/ }).first().click();
  await A.getByRole("menuitem", { name: "Client Trust Statement" }).click();
  const g = A.getByRole("dialog", { name: /Generate: Client Trust Statement/ });
  await g.waitFor({ timeout: 15_000 });
  if (await g.getByRole("button", { name: /Next/ }).count()) await g.getByRole("button", { name: /Next/ }).click();
  const prev = g.frameLocator('iframe[title="Document Preview"]').locator("body");
  await prev.getByText("Closing balance").first().waitFor({ timeout: 30_000 }).catch(() => {});
  k2.text = norm(await prev.innerText().catch(() => ""));
  fs.writeFileSync(`${OUT}/x3-k2-client-trust-statement-after-withdrawal.txt`, k2.text);
  await A.screenshot({ path: `${OUT}/x3-k2-client-trust-statement-after-withdrawal-1440.png` });
  await g.screenshot({ path: `${OUT}/x3-k2-client-trust-statement-dialog-crop.png` }).catch(() => {});
  const m = k2.text.match(/Account:\s*([^:]+?)\s*Opening balance:/); k2.accountLine = m ? m[1] : null;
  k2.typeWords = [...k2.text.matchAll(/\d{4} (Deposit|Fee transfer|Inv [a-z]+|Placed in [^R]+?|Returned [^R]+?) /g)].map((x) => x[1]);
  await A.keyboard.press("Escape");
  res.finishedAt = new Date().toISOString();
  save();
  console.log(JSON.stringify({ c2: c2.checks, c2input: c2.input, dep, k2: { accountLine: k2.accountLine, typeWords: k2.typeWords }, consoleErrors: res.consoleErrors.length, pageErrors: res.pageErrors.length }, null, 2));
  await browser.close();
})().catch((e) => { res.error = String(e.stack || e); save(); console.error("OBS EXTRA FAILED", e); process.exit(1); });
