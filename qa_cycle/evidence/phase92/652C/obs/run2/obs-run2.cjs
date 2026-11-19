// 652C OBS run 2 (head bc4684518): calculate and complete name the statement the wizard reviewed
// (expectedBankStatementId); a draft re-pointed since is refused 409 "This reconciliation now uses a
// different statement" — nothing changes, the wizard shows the banner and stays on its step.
// Headless Chromium (Playwright library, not MCP) on the e2e mock-auth stack (:3001) as Alice.
// Every state change goes through the wizard UI; psql is read-only SELECT evidence.
//
// Period end PE (default 2026-11-19, today; the surviving V29 DRAFT D = 258446a3 is continued).
// g  Tab A: upload sa0 → auto-match → Review (D → sa0) → read figures → Back/Back → upload SA whose
//    closing balance balances D → Review (same D → SA). One draft for PE throughout.
//    Tab A: select SA's line → Exclude → calculate → Complete enabled.
// e  Tab B (same context): upload SB (same balancing amount) → auto-match → Review → D → SB.
//    tcpdump sidecar on. Tab A: Complete → expect 409 complete + 409 calculate, banner title,
//    no step 5; then Back → Review Matches → 409 calculate, banner, stays on step 3.
//    DB: D DRAFT on SB, updated_at unchanged, SA/SB not RECONCILED, no monthly list, no audit.
// f  Tab B: select SB's line → Exclude → Complete → step 5; DB D COMPLETED, SB RECONCILED.
//
// Run: NODE_OPTIONS="" node obs-run2.cjs
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
const PE = process.env.PE || "2026-11-19";
const SIDECAR = "obs652c-run2-tcpdump";
const LOG = path.join(OUT, "obs-run2-requests.log");
fs.writeFileSync(LOG, "");
const log = (s) => fs.appendFileSync(LOG, `${new Date().toISOString()} ${s}\n`);
const R = { run: RUN, pe: PE, start: new Date().toISOString(), g: {}, e: {}, f: {}, pageErrors: [], consoleErrors: [], actionCalls: [] };

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

const ACTIONS = (() => {
  const m = JSON.parse(execFileSync("docker", ["exec", "e2e-frontend", "cat", "/app/frontend/.next/server/server-reference-manifest.json"], { encoding: "utf8", maxBuffer: 64 << 20 }));
  return Object.fromEntries(Object.entries(m.node).map(([id, e]) => [id, e.exportedName]));
})();
let actionMark = 0;
const actionsSince = () => R.actionCalls.slice(actionMark).map((a) => `${a.tag}:${a.name}:${a.status ?? "?"}`);
const mark = () => (actionMark = R.actionCalls.length);

function writeCsv(name, amount) {
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(
    file,
    [
      `Standard Bank Trust Account - 652C OBS run2 ${RUN} ${name}`,
      "Date,Description,Reference,Amount,Balance",
      `${PE},652C obs2 ${name} ${RUN},OBS2/652C/${RUN}/${name},${amount.toFixed(2)},${amount.toFixed(2)}`,
    ].join("\n") + "\n",
  );
  return file;
}
const stmtId = (csv) => sql(`select id from T.bank_statements where file_name='${path.basename(csv)}' order by created_at desc limit 1`);
const recons = (accountId) =>
  lines(sql(`select r.id, r.status, r.bank_statement_id, s.file_name, r.updated_at from T.trust_reconciliations r left join T.bank_statements s on s.id=r.bank_statement_id where r.trust_account_id='${accountId}' and r.period_end='${PE}' order by r.created_at`));
const figures = (id) => {
  const [bank, od, op, adj, cb, cr, diff, bal, status, stmt, upd] = sql(
    `select bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, creditor_total, difference, is_balanced, status, bank_statement_id, updated_at from T.trust_reconciliations where id='${id}'`,
  ).split("|");
  return { bank: +bank, od: +od, op: +op, adj: +adj, cb: +cb, cr: +cr, diff: +diff, balanced: bal === "t", status, statement: stmt, updatedAt: upd };
};
const stmtStatus = (ids) => lines(sql(`select id, file_name, status from T.bank_statements where id in (${ids.map((i) => `'${i}'`).join(",")}) order by created_at`));
const monthlyLists = () => +sql("select count(*) from T.trust_monthly_lists");
const reconciledCount = () => +sql("select count(*) from T.bank_statements where status='RECONCILED'");
const auditFor = (id, since) =>
  lines(sql(`select occurred_at, event_type, details->>'previous_bank_statement_id', details->>'bank_statement_id' from T.audit_events where entity_id='${id}' and occurred_at >= '${since}' order by occurred_at`));
const dbState = (D, ids) => ({ at: new Date().toISOString(), D: figures(D), rowsForPe: recons(R.accountId), statements: stmtStatus(ids), monthlyLists: monthlyLists(), reconciledStatements: reconciledCount() });

async function selectAccountPeriod(page, accountId) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(PE);
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
async function review(page) {
  await page.getByTestId("step-next-btn").click(); // Review Matches → create/get + calculate
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  await page.waitForTimeout(800);
}
async function backToUpload(page) {
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await page.getByTestId("auto-match-back-btn").click(); // 3 → 2
}
async function excludeUnmatched(page, statementId) {
  let n = 0;
  for (const lineId of lines(sql(`select id from T.bank_statement_lines where bank_statement_id='${statementId}' and match_status='UNMATCHED'`))) {
    await page.getByTestId(`bank-line-${lineId}`).click();
    await page.getByTestId(`exclude-btn-${lineId}`).click();
    await page.getByTestId(`exclude-btn-${lineId}`).waitFor({ state: "detached", timeout: 30_000 });
    await page.waitForTimeout(1500);
    n++;
  }
  return n;
}
const banner = async (page) => ({
  title: (await page.getByTestId("reconciliation-error-title").count()) ? await page.getByTestId("reconciliation-error-title").innerText() : null,
  text: (await page.getByTestId("reconciliation-error").innerText()).replace(/\s+/g, " "),
});
const where = async (page) => ({
  step3AutoMatch: (await page.getByTestId("step-auto-match").count()) > 0,
  step4SplitPane: (await page.getByTestId("reconciliation-split-pane").count()) > 0,
  step5Complete: (await page.getByTestId("step-complete").count()) > 0,
  completeBtnEnabled: (await page.getByTestId("complete-reconciliation-btn").count()) ? await page.getByTestId("complete-reconciliation-btn").isEnabled() : null,
});

function sidecarStart() {
  try { execFileSync("docker", ["rm", "-f", SIDECAR], { stdio: "ignore" }); } catch {}
  execFileSync("docker", ["run", "-d", "--name", SIDECAR, "--net", "container:e2e-backend", "alpine:3", "sh", "-c",
    "apk add --no-cache tcpdump >/dev/null 2>&1 && exec tcpdump -i any -A -s0 -l 'tcp port 8080'"], { encoding: "utf8" });
  const t0 = Date.now();
  while (Date.now() - t0 < 90_000) {
    const l = execFileSync("docker", ["logs", SIDECAR], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
    let err = "";
    try { err = execFileSync("sh", ["-c", `docker logs ${SIDECAR} 2>&1 >/dev/null`], { encoding: "utf8" }); } catch {}
    if (/listening on/.test(l + err)) return;
    execFileSync("sleep", ["1"]);
  }
  throw new Error("tcpdump sidecar not listening");
}
function sidecarStop() {
  const raw = execFileSync("sh", ["-c", `docker logs ${SIDECAR} 2>&1`], { encoding: "utf8", maxBuffer: 256 << 20 });
  execFileSync("docker", ["rm", "-f", SIDECAR], { stdio: "ignore" });
  const red = raw.replace(/^(authorization|cookie):.*$/gim, "$1: <redacted>");
  // keep only the calculate/complete exchanges (request packet + the next response packet on that flow)
  const blocks = red.split(/\n(?=\d\d:\d\d:\d\d\.\d{6} )/);
  const keep = [];
  for (let i = 0; i < blocks.length; i++) {
    const b = blocks[i];
    if (/POST \/api\/trust-reconciliations\/[0-9a-f-]+\/(calculate|complete) HTTP/.test(b)) {
      keep.push(b);
      // the response on the same port pair follows
      const port = (b.match(/\.(\d+) > [^ ]+\.8080:/) || [])[1];
      for (let j = i + 1; j < blocks.length && j < i + 60; j++) {
        if (port && blocks[j].includes(`.8080 > `) && blocks[j].includes(`.${port}:`) && /HTTP\/1\.1 \d{3}|"title"/.test(blocks[j])) {
          keep.push(blocks[j]);
          if (j + 1 < blocks.length && blocks[j + 1].includes(`.${port}:`) && /"title"|"detail"/.test(blocks[j + 1])) keep.push(blocks[j + 1]);
          break;
        }
      }
    }
  }
  return { raw: red, excerpt: keep.join("\n") };
}

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  Object.assign(R, { schema: SCHEMA, accountId });
  R.before = { rowsForPe: recons(accountId), monthlyLists: monthlyLists(), reconciledStatements: reconciledCount(), duplicates: lines(sql("select trust_account_id, period_end, count(*) from T.trust_reconciliations group by 1,2 having count(*)>1")) };
  if (R.before.rowsForPe.length !== 1 || !R.before.rowsForPe[0].includes("|DRAFT|")) throw new Error(`expected one DRAFT for ${PE}: ${R.before.rowsForPe}`);
  const D = R.before.rowsForPe[0].split("|")[0];
  R.D = D;

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const wire = (p, tag) => {
    p.on("pageerror", (e) => R.pageErrors.push(`${tag}: ${String(e)}`));
    p.on("console", (m) => m.type() === "error" && R.consoleErrors.push(`${tag}: ${m.text().slice(0, 300)}`));
    p.on("request", (r) => {
      const id = r.headers()["next-action"];
      if (id) {
        R.actionCalls.push({ tag, name: ACTIONS[id] || id, id, t: new Date().toISOString() });
        log(`REQ ${tag} server-action ${ACTIONS[id] || id}`);
      }
    });
    p.on("response", (r) => {
      const id = r.request().headers()["next-action"];
      if (id) {
        const call = R.actionCalls.slice().reverse().find((c) => c.id === id && c.tag === tag && c.status === undefined);
        if (call) call.status = r.status();
        log(`RES ${tag} server-action ${ACTIONS[id] || id} ${r.status()}`);
      }
    });
  };
  const A = await context.newPage();
  wire(A, "tabA");
  await A.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await A.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await A.waitForURL(/\/org\//, { timeout: 60_000 });
  const B = await context.newPage();
  wire(B, "tabB");

  // ── g + set-up: tab A, one wizard session, re-upload to a balancing statement ──
  const sa0 = writeCsv("sa0", 1.11);
  mark();
  await selectAccountPeriod(A, accountId);
  await upload(A, sa0);
  await autoMatch(A);
  await review(A);
  R.g.G1 = { statement: stmtId(sa0), rowsForPe: recons(accountId), figures: figures(D), actions: actionsSince() };
  await A.screenshot({ path: path.join(OUT, "g1-tabA-review-sa0.png"), fullPage: true });
  let f = R.g.G1.figures;
  let SA = null;
  R.g.attempts = [];
  for (let attempt = 1; attempt <= 3; attempt++) {
    const needed = Math.round((f.cb - f.od + f.op) * 100) / 100;
    const csv = writeCsv(`sa-try${attempt}`, needed);
    mark();
    await backToUpload(A);
    await upload(A, csv);
    await autoMatch(A);
    await review(A);
    f = figures(D);
    SA = stmtId(csv);
    R.g.attempts.push({ csv: path.basename(csv), needed, statement: SA, rowsForPe: recons(accountId), figures: f, actions: actionsSince() });
    if (f.diff === 0) break;
  }
  R.needed = R.g.attempts.at(-1).needed;
  R.SA = SA;
  await A.screenshot({ path: path.join(OUT, "g2-tabA-review-SA-same-draft.png"), fullPage: true });
  mark();
  R.g.excludedInA = await excludeUnmatched(A, SA);
  R.g.tabAReady = { figures: figures(D), where: await where(A), actions: actionsSince() };
  await A.screenshot({ path: path.join(OUT, "e1-tabA-ready-to-complete-on-SA.png"), fullPage: true });

  // ── e: tab B continues D with SB ──
  const sbCsv = writeCsv("sb", R.needed);
  mark();
  await selectAccountPeriod(B, accountId);
  await upload(B, sbCsv);
  await autoMatch(B);
  await review(B);
  const SB = stmtId(sbCsv);
  R.SB = SB;
  R.e.tabBReview = { statement: SB, rowsForPe: recons(accountId), figures: figures(D), actions: actionsSince(), audit: auditFor(D, R.start) };
  await B.screenshot({ path: path.join(OUT, "e2-tabB-review-SB.png"), fullPage: true });

  // tab A is now stale: Complete, then Back → Review Matches (calculate)
  R.e.before = dbState(D, [SA, SB]);
  sidecarStart();
  R.e.sidecarStartedAt = new Date().toISOString();
  const tStale = new Date().toISOString();
  mark();
  R.e.tabABeforeClick = await where(A);
  if (R.e.tabABeforeClick.completeBtnEnabled) {
    await A.getByTestId("complete-reconciliation-btn").click();
    await A.getByTestId("reconciliation-error").waitFor({ timeout: 60_000 });
    await A.getByTestId("complete-reconciliation-btn").waitFor({ timeout: 30_000 });
    await A.waitForTimeout(1500);
    R.e.completeRefusal = { banner: await banner(A), where: await where(A), actions: actionsSince(), db: dbState(D, [SA, SB]) };
    await A.screenshot({ path: path.join(OUT, "e3-tabA-complete-refused-banner.png"), fullPage: true });
  } else {
    R.e.completeRefusal = { skipped: "Complete not enabled in tab A" };
  }

  mark();
  await A.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await A.getByTestId("step-auto-match").waitFor();
  await A.getByTestId("step-next-btn").click(); // Review Matches → calculate(D, SA)
  await A.getByTestId("reconciliation-error").waitFor({ timeout: 60_000 });
  await A.waitForTimeout(1500);
  R.e.calculateRefusal = { banner: await banner(A), where: await where(A), actions: actionsSince(), db: dbState(D, [SA, SB]) };
  await A.screenshot({ path: path.join(OUT, "e4-tabA-review-calculate-refused-banner.png"), fullPage: true });
  await A.waitForTimeout(1000);
  const cap = sidecarStop();
  R.e.auditDuringStale = auditFor(D, tStale);
  fs.writeFileSync(path.join(OUT, "e-wire-tcpdump-excerpt.txt"),
    `# tcpdump sidecar (alpine:3 + tcpdump, --net container:e2e-backend, tcp port 8080) during e (tab A stale Complete, then Back → Review Matches); calculate/complete exchanges only; Authorization/Cookie redacted\n${cap.excerpt}\n`);
  fs.writeFileSync(path.join(OUT, "e-wire-tcpdump-full.txt"), cap.raw);

  // ── f: tab B (current statement) completes D ──
  mark();
  R.f.excludedInB = await excludeUnmatched(B, SB);
  R.f.ready = { figures: figures(D), where: await where(B), actions: actionsSince() };
  await B.screenshot({ path: path.join(OUT, "f1-tabB-ready-to-complete-on-SB.png"), fullPage: true });
  if (R.f.ready.where.completeBtnEnabled) {
    mark();
    await B.getByTestId("complete-reconciliation-btn").click();
    await B.getByTestId("step-complete").waitFor({ timeout: 60_000 });
    await B.waitForTimeout(800);
    R.f.completed = { step5: (await B.getByTestId("step-complete").innerText()).replace(/\s+/g, " "), actions: actionsSince(), db: dbState(D, [SA, SB]) };
    await B.screenshot({ path: path.join(OUT, "f2-tabB-step5-completed.png"), fullPage: true });
  }
  await A.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await A.getByTestId("reconciliations-table").waitFor({ timeout: 60_000 });
  await A.waitForTimeout(500);
  await A.screenshot({ path: path.join(OUT, "f3-list.png"), fullPage: true });

  R.auditAll = auditFor(D, R.start);
  R.after = { rowsForPe: recons(accountId), monthlyLists: monthlyLists(), reconciledStatements: reconciledCount(), duplicates: lines(sql("select trust_account_id, period_end, count(*) from T.trust_reconciliations group by 1,2 having count(*)>1")), statements: lines(sql(`select id, file_name, status from T.bank_statements where file_name like '%-${RUN}.csv' order by created_at`)) };
  R.end = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "obs-run2-result.json"), JSON.stringify(R, null, 2));
  await browser.close();
  console.log("OK", RUN);
})().catch(async (e) => {
  R.fatal = String(e);
  try { execFileSync("docker", ["rm", "-f", SIDECAR], { stdio: "ignore" }); } catch {}
  fs.writeFileSync(path.join(OUT, "obs-run2-result.json"), JSON.stringify(R, null, 2));
  console.error("FATAL", String(e).slice(0, 2000));
  process.exit(1);
});
