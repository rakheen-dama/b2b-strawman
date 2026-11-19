// 652C reproduce-first (task 652.7, browser half): does re-uploading a statement in the
// reconciliation wizard leave a second DRAFT reconciliation for the same account + period end?
// Headless Chromium against the e2e mock-auth stack (:3001) as Alice (owner). Every state change
// goes through the UI; psql is read-only evidence (SELECT only).
//
// Path A (one wizard session, period end PE_A): select → upload S1 → auto-match → Review Matches
//   (creates the draft) → Back → Review Matches again (control: must reuse) → Back → Back to
//   upload → upload S2 (corrected statement, same account + period end) → auto-match → Review.
// Path B (abandon + reopen, period end PE_B):
//   B1: select → upload S3 → abandon at step 2 (navigate away) — no draft expected yet.
//   B2: reopen /new → same account + PE_B → upload S3 again → auto-match → Review (draft #1);
//       abandon at step 4 (navigate away).
//   B3: reopen /new → same account + PE_B → upload S4 → auto-match → Review.
// The list page (primary account) is counted after each path; DB counts before/after.
//
// Run: NODE_OPTIONS="" node qa_cycle/evidence/phase92/652C/repro-browser.cjs   (from the worktree)
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/b2b-strawman/frontend/node_modules/@playwright/test",
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const PE_A = process.env.PE_A || "2026-11-15";
const PE_B = process.env.PE_B || "2026-11-14";
const LOG = path.join(OUT, "repro-browser-requests.log");
fs.writeFileSync(LOG, "");
const log = (s) => fs.appendFileSync(LOG, `${new Date().toISOString()} ${s}\n`);
const result = { run: RUN, periodEndA: PE_A, periodEndB: PE_B, db: {}, list: {}, steps: [], pageErrors: [], consoleErrors: [] };
const step = (s) => {
  result.steps.push(s);
  log(`STEP ${s}`);
};

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

function writeCsv(name, date, amount, ref) {
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(
    file,
    [
      `Standard Bank Trust Account - 652C ${RUN} ${name}`,
      "Date,Description,Reference,Amount,Balance",
      `${date},652C repro ${name},${ref},${amount.toFixed(2)},${amount.toFixed(2)}`,
    ].join("\n") + "\n",
  );
  return file;
}

function dbSnapshot(accountId, label) {
  result.db[label] = {
    duplicatesAnyPeriod: lines(sql(
      "select trust_account_id, period_end, status, count(*) from T.trust_reconciliations group by 1,2,3 having count(*)>1 order by 2",
    )),
    forPeA: lines(sql(
      `select r.id, r.status, s.file_name, r.created_at from T.trust_reconciliations r join T.bank_statements s on s.id=r.bank_statement_id where r.trust_account_id='${accountId}' and r.period_end='${PE_A}' order by r.created_at`,
    )),
    forPeB: lines(sql(
      `select r.id, r.status, s.file_name, r.created_at from T.trust_reconciliations r join T.bank_statements s on s.id=r.bank_statement_id where r.trust_account_id='${accountId}' and r.period_end='${PE_B}' order by r.created_at`,
    )),
  };
}

async function countList(page, accountId, pe, label, shot) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.getByTestId("reconciliations-table").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(500);
  const ids = lines(sql(`select id from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${pe}'`));
  const rows = [];
  for (const id of ids) {
    const row = page.getByTestId(`reconciliation-row-${id}`);
    if (await row.count()) rows.push({ id, text: (await row.innerText()).replace(/\s+/g, " ") });
  }
  result.list[label] = {
    periodEnd: pe,
    dbIds: ids.length,
    rowsOnListPage: rows.length,
    rows,
    totalRowsOnPage: await page.locator('[data-testid^="reconciliation-row-"]').count(),
  };
  await page.screenshot({ path: path.join(OUT, shot), fullPage: true });
}

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

async function autoMatchAndReview(page) {
  await page.getByTestId("step-next-btn").click(); // step 2 → 3
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  await reviewFromStep3(page);
}

async function reviewFromStep3(page) {
  await page.getByTestId("step-next-btn").click(); // Review Matches → creates/reuses + calculates
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(800);
}

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  Object.assign(result, { schema: SCHEMA, accountId });
  dbSnapshot(accountId, "before");

  const base = 652 + (parseInt(RUN, 36) % 900) / 100;
  const s1 = writeCsv("s1", PE_A, base, `BANK/652C/${RUN}/1`);
  const s2 = writeCsv("s2", PE_A, base + 1, `BANK/652C/${RUN}/2`); // "corrected" statement
  const s3 = writeCsv("s3", PE_B, base + 2, `BANK/652C/${RUN}/3`);
  const s4 = writeCsv("s4", PE_B, base + 3, `BANK/652C/${RUN}/4`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push(String(e)));
  page.on("console", (m) => m.type() === "error" && result.consoleErrors.push(m.text().slice(0, 300)));
  page.on("request", (r) => {
    const action = r.headers()["next-action"];
    if (action) log(`REQ server-action ${r.method()} ${new URL(r.url()).pathname} next-action=${action}`);
    else if (r.url().includes(":8081") || r.url().includes("/api/")) log(`REQ api ${r.method()} ${r.url()}`);
  });
  page.on("response", (r) => {
    if (r.request().headers()["next-action"]) log(`RES server-action ${r.status()} next-action=${r.request().headers()["next-action"]}`);
  });

  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await page.waitForURL(/\/org\//, { timeout: 60_000 });

  // ── Path A: one wizard session, re-upload via the wizard's own Back controls ──
  step(`A1 select account + period end ${PE_A}, upload ${path.basename(s1)}, auto-match, Review Matches`);
  await selectAccountPeriod(page, accountId, PE_A);
  await upload(page, s1);
  await autoMatchAndReview(page);
  result.db.afterA1 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_A}'`));
  await page.screenshot({ path: path.join(OUT, "repro-browser-1-A-review-first.png"), fullPage: true });

  step("A2 (control) step 4 Back → step 3 → Review Matches again (same statement)");
  await page.getByRole("button", { name: "Back", exact: true }).click(); // step 4 → 3
  await page.getByTestId("step-auto-match").waitFor();
  await reviewFromStep3(page);
  result.db.afterA2 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_A}'`));

  step(`A3 step 4 Back → step 3 Back → step 2, upload corrected ${path.basename(s2)}, auto-match, Review Matches`);
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await page.getByTestId("auto-match-back-btn").click(); // 3 → 2
  await upload(page, s2);
  await autoMatchAndReview(page);
  result.db.afterA3 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_A}'`));
  await page.screenshot({ path: path.join(OUT, "repro-browser-2-A-review-after-reupload.png"), fullPage: true });

  step(`A4 step 4 Back → 3 → 2, upload ${path.basename(s1)} again (same file), auto-match, Review Matches`);
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await page.getByTestId("auto-match-back-btn").click();
  await upload(page, s1);
  await autoMatchAndReview(page);
  result.db.afterA4 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_A}'`));

  await countList(page, accountId, PE_A, "pathA", "repro-browser-3-list-after-path-A.png");

  // ── Path B: abandon and reopen "New reconciliation" ──
  step(`B1 new wizard, period end ${PE_B}, upload ${path.basename(s3)}, abandon at step 2`);
  await selectAccountPeriod(page, accountId, PE_B);
  await upload(page, s3);
  result.db.afterB1 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_B}'`));

  step(`B2 reopen New reconciliation, same account + ${PE_B}, upload ${path.basename(s3)} again, auto-match, Review Matches; abandon at step 4`);
  await selectAccountPeriod(page, accountId, PE_B);
  await upload(page, s3);
  await autoMatchAndReview(page);
  result.db.afterB2 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_B}'`));

  step(`B3 reopen New reconciliation, same account + ${PE_B}, upload ${path.basename(s4)}, auto-match, Review Matches`);
  await selectAccountPeriod(page, accountId, PE_B);
  await upload(page, s4);
  await autoMatchAndReview(page);
  result.db.afterB3 = lines(sql(`select id, status from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_B}'`));

  await countList(page, accountId, PE_B, "pathB", "repro-browser-4-list-after-path-B.png");

  dbSnapshot(accountId, "after");
  result.db.auditCreated = lines(sql(
    `select occurred_at, entity_id, details->>'period_end', details->>'bank_statement_id' from T.audit_events where event_type='trust_reconciliation.created' and details->>'period_end' in ('${PE_A}','${PE_B}') order by occurred_at`,
  ));
  result.db.statements = lines(sql(
    `select id, file_name, created_at from T.bank_statements where file_name like '%-${RUN}.csv' order by created_at`,
  ));

  fs.writeFileSync(path.join(OUT, "repro-browser-result.json"), JSON.stringify(result, null, 2));
  await browser.close();
  console.log("OK", RUN);
})().catch(async (e) => {
  fs.writeFileSync(path.join(OUT, "repro-browser-result.json"), JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  console.error("FATAL", String(e).slice(0, 2000));
  process.exit(1);
});
