// 652C OBS gate (head 93c8af2d5): createReconciliation is get-or-create — one reconciliation per
// trust account + period end; a COMPLETED period refuses with 409 "This period is already reconciled".
// Headless Chromium (Playwright library, not MCP) against the e2e mock-auth stack (:3001) as Alice.
// Every state change goes through the wizard UI; psql is read-only SELECT evidence.
//
// a (PE_A): one wizard session — upload a1 → auto-match → Review (draft) → [control] Back → Review
//    → Back/Back → upload a2 → Review → Back/Back → upload a1 again → Review. Expect 1 draft
//    throughout, re-pointed to the latest statement; the list (2nd tab) shows one row.
// b (PE_B): b1 → Review (draft), abandon; reopen New Reconciliation → b2 → Review. Expect 1 draft.
// c (PE_C): c1 → Review (draft) → read cashbook/outstanding → upload c2 whose closing balance makes
//    adjusted bank = cashbook → Review (same draft, re-pointed) → exclude unresolved lines →
//    Complete → step 5. Then reopen New Reconciliation → c3 → Review → banner title
//    "This period is already reconciled"; DB count unchanged; backend log shows the 409.
// d: audit trust_reconciliation.created / .statement_changed rows for the three period ends.
//
// Run (from anywhere): NODE_OPTIONS="" node obs.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-652C/frontend/node_modules/@playwright/test",
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const PE_A = process.env.PE_A || "2026-11-16";
const PE_B = process.env.PE_B || "2026-11-17";
const PE_C = process.env.PE_C || "2026-11-18";
const START = new Date().toISOString();
// PHASES=cd (run 2) skips a/b and continues the existing PE_C draft (get-or-create re-points it)
const PHASES = process.env.PHASES || "abcd";
const TAG = process.env.TAG || "";
const LOG = path.join(OUT, `obs-requests${TAG}.log`);
fs.writeFileSync(LOG, "");
const log = (s) => fs.appendFileSync(LOG, `${new Date().toISOString()} ${s}\n`);
const R = { run: RUN, start: START, pe: { a: PE_A, b: PE_B, c: PE_C }, a: {}, b: {}, c: {}, d: {}, pageErrors: [], consoleErrors: [], actionCalls: [] };

function psql(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q],
    { encoding: "utf8" },
  ).trim();
}
const SCHEMA = psql("select schema_name from public.org_schema_mapping where external_org_id='e2e-test-org'");
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));
const lines = (s) => s.split("\n").filter(Boolean);

// server-action id → exported name, from the running frontend's manifest
const ACTIONS = (() => {
  const m = JSON.parse(execFileSync("docker", ["exec", "e2e-frontend", "cat", "/app/frontend/.next/server/server-reference-manifest.json"], { encoding: "utf8", maxBuffer: 64 << 20 }));
  return Object.fromEntries(Object.entries(m.node).map(([id, e]) => [id, e.exportedName]));
})();
let actionMark = 0;
const actionsSince = () => R.actionCalls.slice(actionMark).map((a) => `${a.name}:${a.status ?? "?"}`);
const mark = () => (actionMark = R.actionCalls.length);

function writeCsv(name, date, amount) {
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(
    file,
    [
      `Standard Bank Trust Account - 652C OBS ${RUN} ${name}`,
      "Date,Description,Reference,Amount,Balance",
      `${date},652C obs ${name} ${RUN},OBS/652C/${RUN}/${name},${amount.toFixed(2)},${amount.toFixed(2)}`,
    ].join("\n") + "\n",
  );
  return file;
}
const stmtId = (csv) => sql(`select id from T.bank_statements where file_name='${path.basename(csv)}' order by created_at desc limit 1`);
const stmtIds = (csv) => lines(sql(`select id from T.bank_statements where file_name='${path.basename(csv)}' order by created_at`));
const recons = (accountId, pe) =>
  lines(sql(`select r.id, r.status, r.bank_statement_id, s.file_name, r.updated_at from T.trust_reconciliations r left join T.bank_statements s on s.id=r.bank_statement_id where r.trust_account_id='${accountId}' and r.period_end='${pe}' order by r.created_at`));
const figures = (id) => {
  const [bank, od, op, adj, cb, cr, diff, bal, status] = sql(
    `select bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, creditor_total, difference, is_balanced, status from T.trust_reconciliations where id='${id}'`,
  ).split("|");
  return { bank: +bank, od: +od, op: +op, adj: +adj, cb: +cb, cr: +cr, diff: +diff, balanced: bal === "t", status };
};

async function selectAccountPeriod(page, accountId, pe) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(pe);
  await page.getByTestId("step-next-btn").click();
}
async function upload(page, csv) {
  await page.getByTestId("bank-statement-upload").waitFor({ timeout: 30_000 });
  await page.getByTestId("file-input").setInputFiles(csv);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(300);
}
async function autoMatch(page) {
  await page.getByTestId("step-next-btn").click(); // 2 → 3
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
}
async function review(page, { expectError = false } = {}) {
  await page.getByTestId("step-next-btn").click(); // Review Matches → create/get + calculate
  if (expectError) {
    await page.getByTestId("reconciliation-error").waitFor({ timeout: 60_000 });
  } else {
    await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  }
  await page.waitForTimeout(800);
}
async function backToUpload(page) {
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await page.getByTestId("auto-match-back-btn").click(); // 3 → 2
}
const fmtDate = (iso) => {
  const [y, m, d] = iso.split("-").map(Number);
  return `${d} ${["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"][m - 1]} ${y}`;
};
async function listCheck(listPage, accountId, pe, shot) {
  await listPage.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await listPage.getByTestId("reconciliations-table").waitFor({ timeout: 60_000 });
  await listPage.waitForTimeout(500);
  const ids = lines(sql(`select id from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${pe}'`));
  const all = listPage.locator('[data-testid^="reconciliation-row-"]');
  const texts = [];
  for (let i = 0; i < (await all.count()); i++) texts.push((await all.nth(i).innerText()).replace(/\s+/g, " "));
  const rowsForPe = texts.filter((t) => t.includes(fmtDate(pe)));
  const byId = [];
  for (const id of ids) if (await listPage.getByTestId(`reconciliation-row-${id}`).count()) byId.push(id);
  if (shot) await listPage.screenshot({ path: path.join(OUT, shot), fullPage: true });
  return { pe, dbIds: ids, rowsWithDateText: rowsForPe.length, rowTexts: rowsForPe, dbIdsShownAsRows: byId.length, totalRows: texts.length, shot };
}

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  Object.assign(R, { schema: SCHEMA, accountId });
  R.before = {
    duplicates: lines(sql("select trust_account_id, period_end, count(*) from T.trust_reconciliations group by 1,2 having count(*)>1")),
    forOurPeriods: lines(sql(`select id, period_end, status from T.trust_reconciliations where period_end in ('${PE_A}','${PE_B}','${PE_C}')`)),
  };
  R.phases = PHASES;
  R.before.cDraft = recons(accountId, PE_C);
  if (PHASES.includes("a") && R.before.forOurPeriods.length) throw new Error(`period ends already used: ${R.before.forOurPeriods}`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  const wire = (p, tag) => {
    p.on("pageerror", (e) => R.pageErrors.push(`${tag}: ${String(e)}`));
    p.on("console", (m) => m.type() === "error" && R.consoleErrors.push(`${tag}: ${m.text().slice(0, 300)}`));
    p.on("request", (r) => {
      const id = r.headers()["next-action"];
      if (id) {
        const call = { tag, name: ACTIONS[id] || id, id, t: new Date().toISOString() };
        R.actionCalls.push(call);
        r._call = call;
        log(`REQ ${tag} server-action ${call.name} ${new URL(r.url()).pathname}`);
      }
    });
    p.on("response", async (r) => {
      const id = r.request().headers()["next-action"];
      if (id) {
        const call = R.actionCalls.slice().reverse().find((c) => c.id === id && c.status === undefined);
        if (call) call.status = r.status();
        log(`RES ${tag} server-action ${ACTIONS[id] || id} ${r.status()}`);
      }
    });
  };
  wire(page, "wizard");

  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  const listPage = await context.newPage();
  wire(listPage, "list");

  if (PHASES.includes("a")) {
  // ── a: one wizard session, re-upload via Back controls ──
  const a1 = writeCsv("a1", PE_A, 101.11);
  const a2 = writeCsv("a2", PE_A, 102.22);
  const aStep = async (label, shotWizard, shotList) => {
    const rows = recons(accountId, PE_A);
    await page.screenshot({ path: path.join(OUT, shotWizard), fullPage: true });
    const list = await listCheck(listPage, accountId, PE_A, shotList);
    R.a[label] = { db: rows, count: rows.length, actions: actionsSince(), list, wizardShot: shotWizard };
    log(`STEP a ${label}: ${JSON.stringify(R.a[label])}`);
  };
  mark();
  await selectAccountPeriod(page, accountId, PE_A);
  await upload(page, a1);
  await autoMatch(page);
  await review(page);
  R.a.a1Statement = stmtId(a1);
  await aStep("A1_upload_a1", "obs-a1-wizard-review.png", "obs-a1-list.png");

  mark();
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await page.getByTestId("step-auto-match").waitFor();
  await review(page);
  await aStep("A2_control_review_again", "obs-a2-wizard-review.png", "obs-a2-list.png");

  mark();
  await backToUpload(page);
  await upload(page, a2);
  await autoMatch(page);
  await review(page);
  R.a.a2Statement = stmtId(a2);
  await aStep("A3_reupload_a2", "obs-a3-wizard-review.png", "obs-a3-list.png");

  mark();
  await backToUpload(page);
  await upload(page, a1);
  await autoMatch(page);
  await review(page);
  R.a.a1Statements = stmtIds(a1);
  await aStep("A4_reupload_a1_same_file", "obs-a4-wizard-review.png", "obs-a4-list.png");

  // ── b: abandon at review, reopen New Reconciliation ──
  const b1 = writeCsv("b1", PE_B, 201.11);
  const b2 = writeCsv("b2", PE_B, 202.22);
  mark();
  await selectAccountPeriod(page, accountId, PE_B);
  await upload(page, b1);
  await autoMatch(page);
  await review(page);
  R.b.b1Statement = stmtId(b1);
  R.b.B1 = { db: recons(accountId, PE_B), actions: actionsSince() };
  await page.screenshot({ path: path.join(OUT, "obs-b1-wizard-review.png"), fullPage: true });
  mark();
  await selectAccountPeriod(page, accountId, PE_B); // abandon + reopen
  await upload(page, b2);
  await autoMatch(page);
  await review(page);
  R.b.b2Statement = stmtId(b2);
  R.b.B2 = { db: recons(accountId, PE_B), actions: actionsSince() };
  await page.screenshot({ path: path.join(OUT, "obs-b2-wizard-review.png"), fullPage: true });
  R.b.list = await listCheck(listPage, accountId, PE_B, "obs-b-list.png");
  log(`STEP b ${JSON.stringify(R.b)}`);
  }

  // ── c: complete a balanced reconciliation, then refuse a new one for the same period ──
  const c1 = writeCsv("c1", PE_C, 301.11);
  mark();
  await selectAccountPeriod(page, accountId, PE_C);
  await upload(page, c1);
  await autoMatch(page);
  await review(page);
  const cDraft = recons(accountId, PE_C);
  R.c.C1 = { db: cDraft, actions: actionsSince() };
  const cId = cDraft[0].split("|")[0];
  R.c.C1.figures = figures(cId);
  // closing balance that makes adjusted bank (= bank + OD − OP) equal the cashbook
  let attempt = 0;
  let f = R.c.C1.figures;
  R.c.attempts = [];
  while (attempt < 3) {
    attempt++;
    const needed = Math.round((f.cb - f.od + f.op) * 100) / 100;
    const csv = writeCsv(`c2-try${attempt}`, PE_C, needed);
    mark();
    await backToUpload(page);
    await upload(page, csv);
    await autoMatch(page);
    await review(page);
    f = figures(cId);
    R.c.attempts.push({ csv: path.basename(csv), needed, statement: stmtId(csv), db: recons(accountId, PE_C), figures: f, actions: actionsSince() });
    if (f.diff === 0) break;
  }
  // resolve every unresolved statement line by Exclude (the wizard's own control: select the
  // UNMATCHED line, then its Exclude button)
  mark();
  let n = 0;
  for (const lineId of lines(sql(`select l.id from T.bank_statement_lines l join T.trust_reconciliations r on r.bank_statement_id=l.bank_statement_id where r.id='${cId}' and l.match_status='UNMATCHED'`))) {
    await page.getByTestId(`bank-line-${lineId}`).click();
    await page.getByTestId(`exclude-btn-${lineId}`).click();
    await page.getByTestId(`exclude-btn-${lineId}`).waitFor({ state: "detached", timeout: 30_000 });
    await page.waitForTimeout(1500);
    n++;
  }
  R.c.excluded = n;
  R.c.afterExclude = { figures: figures(cId), actions: actionsSince(), completeEnabled: await page.getByTestId("complete-reconciliation-btn").isEnabled() };
  await page.screenshot({ path: path.join(OUT, `obs${TAG}-c1-ready-to-complete.png`), fullPage: true });
  if (R.c.afterExclude.completeEnabled) {
    mark();
    await page.getByTestId("complete-reconciliation-btn").click();
    await page.getByTestId("step-complete").waitFor({ timeout: 60_000 });
    await page.waitForTimeout(500);
    R.c.completed = { step5: (await page.getByTestId("step-complete").innerText()).replace(/\s+/g, " "), db: recons(accountId, PE_C), figures: figures(cId), actions: actionsSince() };
    await page.screenshot({ path: path.join(OUT, `obs${TAG}-c2-step5-completed.png`), fullPage: true });

    // refusal: a new reconciliation for the completed period end
    const c3 = writeCsv("c3", PE_C, 303.33);
    const countBefore = recons(accountId, PE_C).length;
    mark();
    const tRefuse = new Date().toISOString();
    await selectAccountPeriod(page, accountId, PE_C);
    await upload(page, c3);
    await autoMatch(page);
    await review(page, { expectError: true });
    const banner = page.getByTestId("reconciliation-error");
    R.c.refusal = {
      at: tRefuse,
      title: (await page.getByTestId("reconciliation-error-title").count()) ? await page.getByTestId("reconciliation-error-title").innerText() : null,
      banner: (await banner.innerText()).replace(/\s+/g, " "),
      stillOnStep3: (await page.getByTestId("step-auto-match").count()) > 0,
      splitPaneShown: (await page.getByTestId("reconciliation-split-pane").count()) > 0,
      countBefore,
      db: recons(accountId, PE_C),
      actions: actionsSince(),
      c3Statement: stmtId(c3),
    };
    await page.screenshot({ path: path.join(OUT, `obs${TAG}-c3-refused-banner.png`), fullPage: true });
    R.c.list = await listCheck(listPage, accountId, PE_C, `obs${TAG}-c-list.png`);
  }

  // ── d: audit ──
  R.d.audit = lines(sql(
    `select occurred_at, event_type, entity_id, details->>'period_end', details->>'previous_bank_statement_id', details->>'bank_statement_id' from T.audit_events where event_type like 'trust_reconciliation.%' and details->>'period_end' in ('${PE_A}','${PE_B}','${PE_C}') order by occurred_at`,
  ));
  R.d.auditCompleted = lines(sql(
    `select occurred_at, event_type, entity_id from T.audit_events where event_type like 'trust_reconciliation.%' and entity_id in (select id from T.trust_reconciliations where period_end in ('${PE_A}','${PE_B}','${PE_C}')) order by occurred_at`,
  ));
  R.after = {
    duplicates: lines(sql("select trust_account_id, period_end, count(*) from T.trust_reconciliations group by 1,2 having count(*)>1")),
    forOurPeriods: lines(sql(`select id, period_end, status, bank_statement_id from T.trust_reconciliations where period_end in ('${PE_A}','${PE_B}','${PE_C}') order by period_end`)),
    statements: lines(sql(`select id, file_name, created_at from T.bank_statements where file_name like '%-${RUN}.csv' order by created_at`)),
  };
  R.end = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, `obs-result${TAG}.json`), JSON.stringify(R, null, 2));
  await browser.close();
  console.log("OK", RUN);
})().catch(async (e) => {
  R.fatal = String(e);
  fs.writeFileSync(path.join(OUT, `obs-result${TAG}.json`), JSON.stringify(R, null, 2));
  console.error("FATAL", String(e).slice(0, 2000));
  process.exit(1);
});
