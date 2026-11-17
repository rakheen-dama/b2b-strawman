// 647B OBS (f-prep) continuation, through the UI. obs.cjs completed a–e and recorded line 2 as a Bank charge,
// then its re-open of line 1 timed out (the click selecting line 1 was lost — see README). The wizard cannot
// be resumed, so this script runs a new wizard with a second one-line statement (an unknown credit dated
// today-2), selects the line (waiting for its Record button, retrying the click once), records it as an
// Unidentified deposit, and reads the items. Result: a HELD item next to the REVERSED one, for the clip probe.
// Usage: node obs-fprep.cjs
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647B";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const OUT = `${W}/qa_cycle/evidence/phase92/647B/gates/obs`;
const SQL_OUT = `${W}/qa_cycle/evidence/phase92/647B/gates/obs-sums.out`;
const psqlA = (q) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const psql = (q) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-c", q], { encoding: "utf8" });
const S = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const res = { startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [] };
(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push(m.text()); });
  p.on("pageerror", (e) => res.pageErrors.push(String(e)));
  await p.goto("http://localhost:3001/mock-login", { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  // There is no resume for an in-progress reconciliation in the UI (the wizard state lives in its tab), so a
  // second statement is uploaded through a new wizard: one unknown credit dated today-2, period end the same day.
  // Attempt 1 (period end = today) was refused at step 4 — "Statement does not fit the period end": OBS statement 1
  // has a line on today, and attempt 1 left its one-line statement (today-1) imported with no reconciliation.
  const acct = psqlA(`select id from ${S}.trust_accounts order by created_at limit 1`);
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
  const y = new Date(`${today}T12:00:00Z`); y.setUTCDate(y.getUTCDate() - 2); const yday = y.toISOString().slice(0, 10);
  const RUN = Date.now().toString(36).slice(-5).toUpperCase();
  const csvName = `statement-OBS647B-FPREP-${RUN}.csv`;
  fs.writeFileSync(`${OUT}/${csvName}`, [`Standard Bank Trust Account - OBS 647B f-prep ${RUN}`, "Date,Description,Reference,Amount,Balance", `${yday},DEPOSIT UNKNOWN REF 67890,UNK2/OBS647B/${RUN},1500.00,1500.00`].join("\n") + "\n");
  res.csv = { name: csvName, date: yday, today };
  await p.goto("http://localhost:3001/org/e2e-test-org/trust-accounting/reconciliation/new", { waitUntil: "load" });
  await p.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await p.getByTestId("account-select").selectOption(acct);
  await p.getByTestId("period-end-input").fill(yday); // period end = the line's date: a later end is refused while another statement has lines in between
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("bank-statement-upload").waitFor({ timeout: 30_000 });
  await p.getByTestId("file-input").setInputFiles(`${OUT}/${csvName}`);
  await p.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  res.uploadResult = (await p.getByTestId("upload-result").innerText()).replace(/\s+/g, " ");
  const stmt2 = psqlA(`select id from ${S}.bank_statements where file_name='${csvName}'`);
  const L1b = psqlA(`select id from ${S}.bank_statement_lines where bank_statement_id='${stmt2}'`);
  res.statement2 = stmt2; res.line = L1b;
  await p.getByTestId("step-next-btn").click();
  await p.getByTestId("auto-match-btn").click();
  await p.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  await p.getByTestId("step-next-btn").click();
  const pane = p.getByTestId("reconciliation-split-pane");
  await pane.waitFor({ timeout: 60_000 });
  res.refusalBanner = await p.getByTestId("reconciliation-error").innerText().catch(() => null);
  await p.screenshot({ path: `${OUT}/14-fprep-second-statement-review.png`, fullPage: true });
  fs.writeFileSync(`${OUT}/14-fprep-second-statement-review.txt`, await p.locator("body").innerText().catch(() => ""));
  const L1 = res.line;
  const line = p.getByTestId(`bank-line-${L1}`);
  const btn = p.getByTestId(`record-btn-${L1}`);
  for (let i = 0; i < 2 && !(await btn.isVisible().catch(() => false)); i++) {
    await line.click();
    await btn.waitFor({ timeout: 5_000 }).catch(() => {});
  }
  await btn.click();
  const dlg = p.getByTestId("record-from-statement-dialog");
  await dlg.waitFor({ timeout: 15_000 });
  res.offered = await dlg.getByTestId("classification-radio").locator("button[role='radio']").evaluateAll((bs) => bs.map((x) => x.getAttribute("value")));
  await dlg.locator("button[role='radio'][value='UNIDENTIFIED_DEPOSIT']").click();
  await dlg.getByTestId("record-notes-input").fill("OBS 647B re-record (probe prep)");
  await dlg.getByTestId("record-submit").click();
  await dlg.waitFor({ state: "hidden", timeout: 30_000 });
  await p.getByTestId(`recorded-badge-${L1}`).waitFor({ timeout: 30_000 });
  res.line1Badge = (await p.getByTestId(`recorded-badge-${L1}`).innerText()).trim();
  await p.waitForTimeout(800);
  await p.screenshot({ path: `${OUT}/15-fprep-both-lines-recorded.png`, fullPage: true });
  fs.writeFileSync(`${OUT}/15-fprep-both-lines-recorded.txt`, await p.locator("body").innerText().catch(() => ""));
  const items = `select d.id, d.received_date, d.bank_description, d.amount, d.status, d.resolved_date, d.deposit_transaction_id, (select count(*) from ${S}.trust_transactions t where t.unidentified_deposit_id=d.id) rows_naming_item from ${S}.unidentified_deposits d order by d.received_date, d.created_at, d.id`;
  const eqq = `select (select coalesce(sum(cashbook_effect),0) from ${S}.trust_transactions where posted_at is not null) cashbook, (select coalesce(sum(ledger_effect),0) from ${S}.trust_transactions where posted_at is not null) all_creditors`;
  fs.appendFileSync(SQL_OUT, `\n== [f-prep (obs-fprep.cjs): items after re-record]\n${items}\n${psql(items)}\n== [f-prep: EQ-2 (all dates)]\n${eqq}\n${psql(eqq)}`);
  res.items = psqlA(`select status from ${S}.unidentified_deposits order by received_date, created_at, id`).split("\n");
  const [eqr] = psqlA(eqq).split("\n").map((l) => l.split("|"));
  res.eq = { cashbook: Number(eqr[0]), allCreditors: Number(eqr[1]) };
  res.checks = { offered: res.offered.includes("UNIDENTIFIED_DEPOSIT"), line1Badge: res.line1Badge === "Recorded: Unidentified deposit",
    itemsReversedThenHeld: res.items.join(",") === "REVERSED,HELD", noRefusal: !res.refusalBanner, equation: Math.abs(res.eq.cashbook - res.eq.allCreditors) < 0.005 };
  res.finishedAt = new Date().toISOString();
  fs.writeFileSync(`${OUT}/obs-fprep-result.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify(res, null, 2));
  await browser.close();
})().catch(async (e) => {
  console.error("FPREP FAILED", e);
  fs.writeFileSync(`${OUT}/obs-fprep-result.json`, JSON.stringify({ ...res, fatal: String((e && e.stack) || e) }, null, 2));
  process.exit(1);
});
