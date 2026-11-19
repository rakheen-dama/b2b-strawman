// 650C OBS — trust reports pack v4: nine report cards; the Rule 54.28 schedule identity on a recorded
// month; every report runs (preview table + CSV + PDF); age analysis; Section 35; trust position; Carol.
// Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081), mock-login UI.
// Every state change goes through the firm UI (deposit dialog, reconciliation wizard + Record from
// statement, memo dialog, allocate dialog, Bob's approval). psql is read-only (SELECT); API calls are GETs.
// Phases share obs/state.json; run in the order a b s c d e f g.
// Usage: [OBS_OUT=obs-r2] node obs.cjs a|b|s|c|d|e|f|g|s2|r
const L = require("./obs-lib.cjs");
const { runReport, readResults, fillParams } = require("./obs-report.cjs");
const { fs, path, OUT, BASE, qa, eq, flat, rand, parseRand, api, settle, snap, scanAll } = L;

const PHASE = process.argv[2];
const TAG = PHASE + (process.env.OBS_SUFFIX || "");
const STATE = path.join(OUT, "state.json");
const state = fs.existsSync(STATE) ? JSON.parse(fs.readFileSync(STATE, "utf8")) : {};
const R = { phase: PHASE, startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [], checks: {} };
const q = L.sqlLog(TAG, R.startedAt);
const B = L.makeBrowser(R);
const RUN = state.run || (state.run = Date.now().toString(36).slice(-5).toUpperCase());
const saveAll = () => { fs.writeFileSync(STATE, JSON.stringify(state, null, 2)); fs.writeFileSync(path.join(OUT, `result-${TAG}.json`), JSON.stringify(R, null, 2)); };
const NINE = ["trust-receipts-payments", "client-trust-balances", "trust-position", "client-ledger-statement", "trust-reconciliation", "investment-register", "trust-interest-charges-schedule", "unidentified-deposits-age-analysis", "section-35-data-pack"];
const SCOPE_NOTE = "Trust cash, estates and other trust assets are not recorded in Kazi and are not included in this comparison.";
const ACCT = { pick: /Mathebula & Partners Trust Account/ };
const ACME = { pick: /Acme Corp/ };

// The statement (line order = line number). Mixed-case descriptions so the raw-code scan has no false hits.
const LINES = (run) => [
  { date: "2026-08-03", desc: "Eft credit Acme", ref: `DEP/OBS650C/${run}/1`, amount: 10000.0, as: "MATCH" },
  { date: "2026-08-12", desc: "Eft credit unknown remitter", ref: `UNK1/OBS650C/${run}`, amount: 600.0, as: "UNIDENTIFIED_DEPOSIT" },
  { date: "2026-08-31", desc: "Credit interest August", ref: `INT/AUG/${run}`, amount: 30.0, as: "FUND_INTEREST" },
  { date: "2026-09-08", desc: "Monthly service fee", ref: `SVC/SEP/${run}`, amount: -57.5, as: "BANK_CHARGE", recoverable: true, vat: "7.50" },
  { date: "2026-09-15", desc: "Cash handling fee", ref: `CHF/SEP/${run}`, amount: -12.0, as: "BANK_CHARGE", recoverable: false, vat: "1.57" },
  { date: "2026-09-20", desc: "Fee refund from business account", ref: `RFD/SEP/${run}`, amount: 57.5, as: "CHARGE_REFUND" },
  { date: "2026-09-25", desc: "Eft credit no reference", ref: `UNK2/OBS650C/${run}`, amount: 750.0, as: "UNIDENTIFIED_DEPOSIT" },
  { date: "2026-09-26", desc: "Eft credit estate payer", ref: `UNK3/OBS650C/${run}`, amount: 200.0, as: "UNIDENTIFIED_DEPOSIT" },
  { date: "2026-09-30", desc: "Credit interest September", ref: `INT/SEP/${run}`, amount: 123.45, as: "FUND_INTEREST" },
  { date: "2026-09-30", desc: "Lpff transfer", ref: `LPFF/SEP/${run}`, amount: -100.0, as: "FUND_SWEEP" },
];
const MEMO = { month: "2026-09", grossInterest: "200.00", grossCharges: "15.00", chargesVat: "1.96", sourceNote: `Bank advice IA/SEP26 at source (OBS 650C ${RUN})` };
const ALLOC_DATE = "2026-10-15";
/** Fund-ledger rows of one month by type, from the posted rows themselves (the schedule's source). */
const fundSql = (from, to) => `select t.transaction_type, count(*) n, sum(t.ledger_effect) ledger_effect, sum(t.amount) amount,
  sum(t.amount) filter (where t.charge_recoverable) recoverable, sum(t.amount) filter (where t.charge_recoverable is false) non_recoverable, sum(t.charge_vat_amount) vat
  from T.trust_transactions t join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id
  where t.trust_account_id='${state.primary}' and ca.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date between '${from}' and '${to}'
  group by t.transaction_type order by 1`;
const fundBal = (asOf) => qa(`select coalesce(sum(t.ledger_effect),0) from T.trust_transactions t join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.trust_account_id='${state.primary}' and ca.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date <= '${asOf}'`);

const phases = {
  // a: catalogue — nine cards, no interest-allocation; DB definitions + pack version 4
  async a() {
    const { p, ctx } = await B.login();
    state.primary = qa("select id from T.trust_accounts where account_type='GENERAL' and is_primary");
    state.alice = qa("select id from T.members where email='alice@e2e-test.local'");
    q(`select id, account_name, account_type, is_primary, opened_date, closed_date from T.trust_accounts`, "a: trust accounts");
    q(`select slug, name, category, sort_order, is_system, length(template_body) template_chars, position('${SCOPE_NOTE}' in template_body) > 0 template_has_scope_note from T.report_definitions where category='TRUST' order by sort_order`, "a: TRUST report definitions");
    q(`select count(*) interest_allocation_rows from T.report_definitions where slug='interest-allocation'`, "a: no interest-allocation row");
    q(`select pack_id, pack_type, pack_version, installed_at from T.pack_install where pack_id in ('trust-reports','standard-reports')`, "a: pack_install ledger rows");
    q(`select jsonb_pretty(report_pack_status) from T.org_settings`, "a: org_settings.report_pack_status");
    q(`select count(*) postings, (select count(*) from T.bank_statements) statements, (select count(*) from T.trust_reconciliations) recons from T.trust_transactions`, "a: empty books before the run");
    const defs = await api(ctx, "/api/report-definitions", "a-report-definitions");
    const trustCat = defs.body?.categories?.find((c) => c.category === "TRUST");
    await p.goto(`${BASE}/trust-accounting/reports`, { waitUntil: "load" });
    await p.getByTestId("trust-reports-page").waitFor({ timeout: 60_000 });
    await settle(p);
    const cards = await p.locator('[data-testid^="report-card-"]').evaluateAll((els) => els.map((e) => ({ testid: e.getAttribute("data-testid"), title: e.querySelector('[data-slot="card-title"]')?.textContent.trim() ?? e.querySelector("h3,div")?.textContent.trim(), description: e.querySelector('[data-slot="card-description"]')?.textContent.trim() ?? null, href: e.closest("a")?.getAttribute("href") })));
    R.cards = cards;
    R.shot = await snap(p, TAG, "01-trust-reports-page");
    const pageText = await p.getByTestId("trust-reports-page").textContent();
    R.pageScan = scanAll(pageText);
    const slugs = cards.map((c) => c.testid.replace("report-card-", ""));
    const dbSlugs = qa("select string_agg(slug, ',' order by sort_order) from T.report_definitions where category='TRUST'").split(",");
    const status = JSON.parse(qa("select report_pack_status::text from T.org_settings"));
    Object.assign(R.checks, {
      "a: Trust › Reports shows exactly nine cards": cards.length === 9,
      "a: the nine card slugs = the nine of §16.4.6": slugs.slice().sort().join() === NINE.slice().sort().join(),
      "a: no interest-allocation card": !slugs.includes("interest-allocation"),
      "a: card names are words (Rule 54.28 schedule, age analysis, trust position, 'Trust Creditors List (Rule 54.15)')": cards.some((c) => /Rule 54\.28/.test(c.title)) && cards.some((c) => /Unidentified Deposits Age Analysis/i.test(c.title)) && cards.some((c) => /Trust Position/i.test(c.title)) && cards.some((c) => c.title === "Trust Creditors List (Rule 54.15)"),
      "a: every card links to /reports/{slug}": cards.every((c) => c.href === `/org/e2e-test-org/reports/${c.testid.replace("report-card-", "")}`),
      "a: no raw code on the reports page": R.pageScan.codes.length === 0,
      "a: DB report_definitions TRUST = the nine, no interest-allocation": dbSlugs.length === 9 && dbSlugs.slice().sort().join() === NINE.slice().sort().join() && qa("select count(*) from T.report_definitions where slug='interest-allocation'") === "0",
      "a: pack_install trust-reports pack_version = 4": qa("select pack_version from T.pack_install where pack_id='trust-reports'") === "4",
      "a: org_settings report_pack_status trust-reports version 4": status.some((s) => s.packId === "trust-reports" && s.version === 4),
      "a: GET /api/report-definitions TRUST category = the nine": !!trustCat && trustCat.reports.length === 9 && trustCat.reports.every((r) => NINE.includes(r.slug)),
      "a: every template body carries the scope sentence": qa(`select bool_and(position('${SCOPE_NOTE}' in template_body) > 0) from T.report_definitions where category='TRUST'`) === "t",
    });
  },

  // b: record two months of Fund-ledger movements through the UI on the primary GENERAL account
  async b() {
    const { p, ctx } = await B.login();
    const lines = LINES(RUN);
    // deposit D1 through the Record Deposit dialog
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
    await dlg.getByLabel("Reference", { exact: true }).fill(lines[0].ref);
    await dlg.getByLabel("Transaction Date").fill(lines[0].date);
    await dlg.getByRole("button", { name: "Record Deposit", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 30_000 });
    await settle(p);
    R.d1 = qa(`select id || '|' || status || '|' || amount || '|' || transaction_date from T.trust_transactions where reference='${lines[0].ref}'`).split("|");
    // the statement
    let bal = 0;
    const rows = [`Standard Bank Trust Account - OBS 650C ${RUN}`, "Date,Description,Reference,Amount,Balance"];
    for (const l of lines) { bal = +(bal + l.amount).toFixed(2); rows.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`); }
    const csvName = `b-statement-${RUN}.csv`;
    state.statementFile = csvName;
    fs.writeFileSync(path.join(OUT, csvName), rows.join("\n") + "\n");
    // wizard: account, period end 30 Sep 2026, upload, auto-match, review
    await p.goto(`${BASE}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
    await p.getByTestId("account-select").locator(`option[value="${state.primary}"]`).waitFor({ state: "attached", timeout: 60_000 });
    await p.getByTestId("account-select").selectOption(state.primary);
    await p.getByTestId("period-end-input").fill("2026-09-30");
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("file-input").setInputFiles(path.join(OUT, csvName));
    await p.getByTestId("upload-result").waitFor({ timeout: 60_000 });
    R.upload = flat(await p.getByTestId("upload-result").textContent());
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("auto-match-btn").click();
    await p.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
    R.autoMatch = flat(await p.getByTestId("auto-match-result").textContent());
    await p.getByTestId("step-next-btn").click();
    await p.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
    await settle(p);
    state.statement = qa(`select id from T.bank_statements where file_name='${csvName}'`);
    const ids = qa(`select line_number || ':' || id || ':' || match_status from T.bank_statement_lines where bank_statement_id='${state.statement}' order by line_number`).split("\n").map((s) => s.split(":"));
    state.lines = ids.map(([n, id, s]) => ({ n: +n, id, s }));
    R.recorded = [];
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i], line = state.lines[i];
      if (l.as === "MATCH") continue;
      await p.getByTestId(`bank-line-${line.id}`).click();
      await p.getByTestId(`record-btn-${line.id}`).click();
      const rd = p.getByTestId("record-from-statement-dialog");
      await rd.waitFor();
      await rd.locator(`button[role='radio'][value='${l.as}']`).click();
      if (l.as === "BANK_CHARGE") { await rd.locator(l.recoverable ? "#charge-recoverable-yes" : "#charge-recoverable-no").click(); await rd.getByTestId("charge-vat-input").fill(l.vat); }
      await rd.getByTestId("record-notes-input").fill(`OBS 650C ${RUN} line ${line.n}`);
      if (i === 3) await snap(p, TAG, "01-record-bank-charge-dialog", rd);
      if (i === 9) await snap(p, TAG, "02-record-fund-sweep-dialog", rd);
      await rd.getByTestId("record-submit").click();
      const closed = await rd.waitFor({ state: "hidden", timeout: 30_000 }).then(() => true).catch(() => false);
      const err = closed ? null : flat(await rd.textContent());
      R.recorded.push({ n: line.n, as: l.as, closed, err });
      if (!closed) await rd.getByRole("button", { name: "Cancel" }).click().catch(() => {});
      await settle(p, 1200);
    }
    R.summaryBeforeComplete = flat(await p.getByTestId("balance-summary").textContent().catch(() => ""));
    await snap(p, TAG, "03-wizard-all-resolved");
    await p.getByTestId("complete-reconciliation-btn").click();
    await p.getByTestId("step-complete").waitFor({ timeout: 30_000 });
    await settle(p);
    R.step5 = flat(await p.getByTestId("step-complete").textContent());
    await snap(p, TAG, "04-step5-complete");
    state.recon = qa(`select id from T.trust_reconciliations where bank_statement_id='${state.statement}'`);
    // memo for September 2026 (650B dialog) — memo figures must sit in their own columns
    await p.goto(`${BASE}/trust-accounting/other-creditors`, { waitUntil: "load" });
    await p.getByTestId("interest-memos-section").waitFor({ timeout: 60_000 });
    await settle(p);
    await p.getByTestId("record-interest-memo-btn").click();
    const md = p.getByTestId("interest-memo-dialog");
    await md.waitFor();
    await md.getByTestId("interest-memo-month").fill(MEMO.month);
    await md.getByTestId("interest-memo-gross-interest").fill(MEMO.grossInterest);
    await md.getByTestId("interest-memo-gross-charges").fill(MEMO.grossCharges);
    await md.getByTestId("interest-memo-vat").fill(MEMO.chargesVat);
    await md.getByTestId("interest-memo-source").fill(MEMO.sourceNote);
    await md.getByTestId("interest-memo-submit").click();
    R.memoSaved = await md.waitFor({ state: "hidden", timeout: 30_000 }).then(() => true).catch(() => false);
    await settle(p);
    // allocate UNK3 (R 200, received 26 Sep) to Acme / Website Redesign dated 15 Oct; Bob approves
    const unk3 = qa(`select id from T.unidentified_deposits where bank_reference='${lines[7].ref}'`);
    state.unk3 = unk3;
    await p.getByTestId(`allocate-btn-${unk3}`).click();
    const ad = p.getByTestId("allocate-unidentified-dialog");
    await ad.waitFor({ timeout: 15_000 });
    await ad.getByTestId("allocate-customer-trigger").click();
    await p.getByPlaceholder("Search clients...").fill("Acme Corp");
    await p.getByRole("option", { name: /Acme Corp/ }).first().click();
    const mt = ad.getByTestId("allocate-matter-trigger");
    for (let i = 0; i < 40 && ((await mt.isDisabled()) || /Loading/.test(await mt.innerText())); i++) await p.waitForTimeout(250);
    await mt.click();
    await p.getByRole("option", { name: /Website Redesign/ }).first().click();
    await ad.getByTestId("allocate-date").fill(ALLOC_DATE);
    await ad.getByTestId("allocate-notes").fill(`OBS 650C ${RUN}: payer identified`);
    await ad.getByTestId("allocate-submit").click();
    R.allocRequested = await ad.waitFor({ state: "hidden", timeout: 30_000 }).then(() => true).catch(() => false);
    await settle(p);
    const allocOut = qa(`select coalesce(resolution_transaction_id::text,'') from T.unidentified_deposits where id='${unk3}'`);
    state.allocOut = allocOut;
    await ctx.close();
    const bob = await B.login("user_e2e_bob");
    await bob.p.goto(`${BASE}/trust-accounting/transactions`, { waitUntil: "load" });
    const row = bob.p.getByTestId(`transaction-row-${allocOut}`);
    await row.waitFor({ timeout: 60_000 });
    await row.getByTestId("approve-button").click();
    for (let i = 0; i < 40 && qa(`select status from T.unidentified_deposits where id='${unk3}'`) === "HELD"; i++) await bob.p.waitForTimeout(500);
    await settle(bob.p);
    await snap(bob.p, TAG, "05-bob-approved-allocation");
    R.unk3 = qa(`select status || '|' || coalesce(resolved_date::text,'') from T.unidentified_deposits where id='${unk3}'`);
    // evidence
    q(`select line_number, transaction_date, description, reference, amount, match_status from T.bank_statement_lines where bank_statement_id='${state.statement}' order by line_number`, "b: statement lines");
    q(`select t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.charge_recoverable, t.charge_vat_amount, ca.account_type creditor, t.reference from T.trust_transactions t left join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.trust_account_id='${state.primary}' order by t.transaction_date, t.created_at`, "b: postings");
    q(fundSql("2026-08-01", "2026-08-31"), "b: Fund-ledger rows, August 2026, by type");
    q(fundSql("2026-09-01", "2026-09-30"), "b: Fund-ledger rows, September 2026, by type");
    q(`select '2026-07-31' as_of, ${fundBal("2026-07-31")} fund union all select '2026-08-31', ${fundBal("2026-08-31")} union all select '2026-09-30', ${fundBal("2026-09-30")}`, "b: Fund ledger balance at month ends");
    q(`select id, status, period_end, bank_balance, adjusted_bank_balance, cashbook_balance, client_ledger_total, creditor_total, difference, is_balanced from T.trust_reconciliations where id='${state.recon}'`, "b: the reconciliation");
    q(`select id, period_end, creditor_total, fund_ledger_balance, unidentified_deposit_total, locked_at from T.trust_monthly_lists`, "b: monthly lists");
    q(`select period_month, gross_interest, gross_charges, charges_vat, source_note from T.trust_interest_memos`, "b: memos");
    q(`select received_date, bank_description, bank_reference, amount, status, resolved_date, allocated_customer_id from T.unidentified_deposits order by received_date`, "b: unidentified deposits");
    const posted = qa(`select count(*) from T.trust_transactions where trust_account_id='${state.primary}' and posted_at is not null`);
    Object.assign(R.checks, {
      "b: deposit R 10 000,00 dated 3 Aug 2026 recorded through the UI": R.d1[1] === "RECORDED" && eq(R.d1[2], 10000) && R.d1[3] === "2026-08-03",
      "b: every unmatched line recorded from its statement line (9 dialogs closed, no refusal)": R.recorded.length === 9 && R.recorded.every((x) => x.closed),
      "b: reconciliation to 30 Sep 2026 completed (step 5)": /locked|complete/i.test(R.step5) && qa(`select status from T.trust_reconciliations where id='${state.recon}'`) === "COMPLETED",
      "b: memo for September 2026 saved": R.memoSaved && qa("select count(*) from T.trust_interest_memos where period_month='2026-09-01'") === "1",
      "b: UNK3 allocated (Bob approved) dated 15 Oct 2026": R.allocRequested && R.unk3 === `ALLOCATED|${ALLOC_DATE}`,
      "b: postings on the account (deposit + 9 lines + allocation pair)": Number(posted) >= 12,
      "b: Fund ledger 31 Aug = 30.00, 30 Sep = 41.45 (SQL)": eq(fundBal("2026-08-31"), 30) && eq(fundBal("2026-09-30"), 41.45),
    });
  },
};
// phases s c d e f g live in obs-phases2.cjs (same shared context)
Object.assign(phases, require("./obs-phases2.cjs")({ state, R, B, q, TAG, RUN, NINE, SCOPE_NOTE, ACCT, ACME, LINES, MEMO, ALLOC_DATE, fundSql, fundBal, runReport, readResults, fillParams }));
// run 2 (re-probe @733b8352f): phase r — the OBS-1..5 fixes (OBS_OUT=obs-r2; run after a b s c d e f)
Object.assign(phases, require("./obs-r2.cjs")({ state, R, B, q, TAG, RUN, NINE, SCOPE_NOTE, ACCT, ACME, runReport }));

(async () => {
  await B.open();
  let fatal = null;
  try { await phases[PHASE](); } catch (e) { fatal = String(e.stack || e).slice(0, 2000); R.fatal = fatal; }
  R.endedAt = new Date().toISOString();
  saveAll();
  await B.close();
  console.log(JSON.stringify({ phase: PHASE, fatal, checks: R.checks, consoleErrors: R.consoleErrors.length, pageErrors: R.pageErrors.length }, null, 2));
  process.exit(fatal || Object.values(R.checks).some((v) => !v) ? 1 : 0);
})();
