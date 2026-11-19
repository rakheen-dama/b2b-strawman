// 652C OBS (check c, wire-level): reopen New Reconciliation for the COMPLETED period end (PE_C),
// upload, auto-match, Review Matches → banner "This period is already reconciled". Captures the
// createReconciliation server-action response body; the backend HTTP status is captured separately
// by a tcpdump sidecar in the e2e-backend network namespace (obs-refusal-tcpdump.txt).
// Run: NODE_OPTIONS="" node obs-refusal.cjs
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
const PE_C = process.env.PE_C || "2026-11-18";
const psql = (q) =>
  execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const SCHEMA = psql("select schema_name from public.org_schema_mapping where external_org_id='e2e-test-org'");
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));
const ACTIONS = (() => {
  const m = JSON.parse(execFileSync("docker", ["exec", "e2e-frontend", "cat", "/app/frontend/.next/server/server-reference-manifest.json"], { encoding: "utf8", maxBuffer: 64 << 20 }));
  return Object.fromEntries(Object.entries(m.node).map(([id, e]) => [id, e.exportedName]));
})();
const R = { run: RUN, pe: PE_C, actions: [], pageErrors: [], consoleErrors: [] };

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  const q = `select id, status, bank_statement_id, completed_at, updated_at from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_C}'`;
  R.dbBefore = sql(q);
  const csv = path.join(OUT, `c4-${RUN}.csv`);
  fs.writeFileSync(csv, [`Standard Bank Trust Account - 652C OBS ${RUN} c4`, "Date,Description,Reference,Amount,Balance", `${PE_C},652C obs c4 ${RUN},OBS/652C/${RUN}/c4,404.44,404.44`].join("\n") + "\n");

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => R.pageErrors.push(String(e)));
  page.on("console", (m) => m.type() === "error" && R.consoleErrors.push(m.text().slice(0, 300)));
  page.on("response", async (r) => {
    const id = r.request().headers()["next-action"];
    if (!id) return;
    const entry = { name: ACTIONS[id] || id, status: r.status(), t: new Date().toISOString() };
    if (entry.name === "createReconciliation") entry.body = (await r.text().catch(() => "")).slice(0, 2000);
    R.actions.push(entry);
  });
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });

  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(PE_C);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(csv);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  R.reviewClickedAt = new Date().toISOString();
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-error").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(800);
  R.title = await page.getByTestId("reconciliation-error-title").innerText();
  R.banner = (await page.getByTestId("reconciliation-error").innerText()).replace(/\s+/g, " ");
  R.onStep3 = (await page.getByTestId("step-auto-match").count()) > 0;
  await page.screenshot({ path: path.join(OUT, "obs-refusal-banner.png"), fullPage: true });
  R.dbAfter = sql(q);
  R.countAfter = sql(`select count(*) from T.trust_reconciliations where trust_account_id='${accountId}' and period_end='${PE_C}'`);
  R.auditSince = sql(`select occurred_at, event_type, entity_id from T.audit_events where event_type like 'trust_reconciliation.%' and occurred_at >= '${R.reviewClickedAt}'`);
  fs.writeFileSync(path.join(OUT, "obs-refusal-result.json"), JSON.stringify(R, null, 2));
  await browser.close();
  console.log("OK", RUN, R.title);
})().catch((e) => {
  R.fatal = String(e);
  fs.writeFileSync(path.join(OUT, "obs-refusal-result.json"), JSON.stringify(R, null, 2));
  console.error("FATAL", String(e).slice(0, 1500));
  process.exit(1);
});
