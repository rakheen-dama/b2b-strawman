// 652A OBS run 2 (head f4ca97684, ruling D-6): headless Chromium against the e2e mock-auth stack
// (:3001) as Alice. Adapted from ../obs/obs.cjs. Every reconciliation change goes through the wizard
// UI; setup money goes through the backend API as the seeded users; psql is read-only.
//
// R1 (period end P1, statement S1): r1-e (calculate aborted), r1-f (success clears), r3 (unmatch with
// only the re-read aborted; then re-read + calculate aborted) + r4 layout, then r2-success (calculate
// held, wizard reset by a period change, released → succeeds) which moves the wizard on to
// R2 (period end P2, statement S2). Tab B imports S3 (a line on P2) → every R2 calculate is refused:
// r1-a/b/c, then r2-refused (held, reset, released → real server refusal).
//
// Run from the worktree's frontend/: NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652A/obs-run2/obs.cjs
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
// Clear of the earlier runs' statements (10–13 and 16–19 Nov 2026).
const P1 = addDays(today, -Number(process.env.P1_OFFSET_DAYS || 14));
const P2 = addDays(today, -Number(process.env.P2_OFFSET_DAYS || 10));
const P3 = addDays(P2, -1); // only typed into the wizard by r2-refused's reset
const result = { run: RUN, startedAt: new Date().toISOString(), P1, P2, P3, steps: {}, actions: [], calculateResponses: [], pageErrors: [], consoleErrors: [] };
const save = () => fs.writeFileSync(path.join(OUT, "result.json"), JSON.stringify(result, null, 2));

const psql = (q) =>
  execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const SCHEMA = psql("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
  const rows = [`Standard Bank Trust Account - 652A OBS run2 ${RUN}`, "Date,Description,Reference,Amount,Balance"];
  for (const l of lines) {
    bal = +(bal + l.amount).toFixed(2);
    rows.push(`${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`);
  }
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(file, rows.join("\n") + "\n");
  return file;
}
const statementLines = (P, tag, amount) => [
  { date: addDays(P, -3), desc: "Unknown credit", ref: `UNK/${tag}/${RUN}`, amount: 700 },
  { date: addDays(P, -2), desc: "Deeds office", ref: `DEEDS/${tag}/${RUN}`, amount: -amount },
  { date: addDays(P, -1), desc: "Bank charges", ref: `CHG/${tag}/${RUN}`, amount: -25 },
  { date: addDays(P, -1), desc: "Second unknown credit", ref: `UNK2/${tag}/${RUN}`, amount: 300 },
];

async function selectAndUpload(page, accountId, periodEnd, csvFile) {
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(periodEnd);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(csvFile);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
}
async function openWizard(page) {
  await page.goto(`${BASE}/org/e2e-test-org/trust-accounting/reconciliation/new`, { waitUntil: "load" });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
}
// Step 2 (uploaded) → step 4, returning the statement / reconciliation / line ids.
async function toReview(page, csvFile) {
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  const stmt = sql(`select id from T.bank_statements where file_name='${path.basename(csvFile)}'`);
  const reconId = sql(`select id from T.trust_reconciliations where bank_statement_id='${stmt}'`);
  const L = Object.fromEntries(sql(`select line_number, id from T.bank_statement_lines where bank_statement_id='${stmt}'`).split("\n").map((r) => r.split("|")));
  const resolved = sql(`select count(*) from T.bank_statement_lines where bank_statement_id='${stmt}' and match_status <> 'UNMATCHED'`);
  if (resolved !== "0") throw new Error(`auto-match resolved ${resolved} line(s) of ${path.basename(csvFile)}; the scenario expects all four unmatched`);
  return { stmt, reconId, L };
}

const squash = (s) => s.replace(/\s+/g, " ").trim();
const text = async (page, id) => {
  const el = page.getByTestId(id);
  return (await el.count()) ? squash(await el.innerText()) : null;
};
const dbRecon = (id) => sql(`select bank_balance, cashbook_balance, outstanding_deposits, outstanding_payments, difference, updated_at from T.trust_reconciliations where id='${id}'`);
async function observe(page, label, lineId, reconId, wait = 2000) {
  await page.waitForTimeout(wait);
  const state = {
    at: new Date().toISOString(),
    banner: await text(page, "reconciliation-error"),
    stale: await text(page, "reconciliation-figures-stale"),
    line: lineId ? await text(page, `bank-line-${lineId}`) : null,
    progress: await text(page, "match-progress"),
    unresolved: await text(page, "unresolved-line-count"),
    excluded: await text(page, "excluded-lines-total"),
    bankCashbook: await text(page, "comparison-bank-cashbook"),
    stepSelectVisible: (await page.getByTestId("step-select").count()) > 0,
    periodEndInput: (await page.getByTestId("period-end-input").count()) ? await page.getByTestId("period-end-input").inputValue() : null,
    dbLine: lineId ? sql(`select match_status, coalesce(trust_transaction_id::text,'') from T.bank_statement_lines where id='${lineId}'`) : null,
    dbRecon: reconId ? dbRecon(reconId) : null,
    shot: `obs-${label}.png`,
  };
  await page.screenshot({ path: path.join(OUT, state.shot), fullPage: true });
  return state;
}

async function layoutProbe(page, width, label) {
  await page.setViewportSize({ width, height: 1400 });
  await page.waitForTimeout(800);
  await page.getByTestId("reconciliation-error").screenshot({ path: path.join(OUT, `obs-r4-${label}-banner-${width}.png`) });
  await page.getByTestId("balance-summary").screenshot({ path: path.join(OUT, `obs-r4-${label}-summary-${width}.png`) });
  const probeResult = await page.evaluate(() => {
    const probe = (el, container) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const c = container.getBoundingClientRect();
      const clippers = [];
      for (let a = el.parentElement; a; a = a.parentElement) {
        const s = getComputedStyle(a);
        if (s.overflowX === "visible" && s.overflowY === "visible") continue;
        const ar = a.getBoundingClientRect();
        if (r.left < ar.left - 0.5 || r.right > ar.right + 0.5 || r.top < ar.top - 0.5 || r.bottom > ar.bottom + 0.5)
          clippers.push(a.tagName + "." + String(a.className).slice(0, 40));
      }
      return {
        rect: { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: r.width, height: r.height },
        container: { left: c.left, right: c.right },
        insideContainer: r.left >= c.left - 0.5 && r.right <= c.right + 0.5,
        ownOverflow: el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight,
        clippedBy: clippers,
        lineHeightPx: parseFloat(getComputedStyle(el).lineHeight),
        text: el.innerText,
      };
    };
    const q = (id) => document.querySelector(`[data-testid="${id}"]`);
    return {
      pageHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      viewportWidth: document.documentElement.clientWidth,
      banner: probe(q("reconciliation-error"), document.documentElement),
      stale: probe(q("reconciliation-figures-stale"), q("balance-summary")),
    };
  });
  await page.setViewportSize({ width: 1440, height: 1400 });
  return probeResult;
}

(async () => {
  const accountId = sql("select id from T.trust_accounts where is_primary order by created_at limit 1");
  const [clientId, matterId] = sql("select c.id, p.id from T.customers c join T.projects p on p.customer_id = c.id order by c.created_at limit 1").split("|");
  const alice = await token("alice", "org:owner");
  const bob = await token("bob", "org:admin");
  const amount = 1250;
  const money = async (P, tag) => {
    const deposit = await api(alice, `/api/trust-accounts/${accountId}/transactions/deposit`, {
      clientId, matterId, amount: 5000, reference: `DEP/${tag}/${RUN}`, description: "652A OBS run2 deposit", transactionDate: P,
    });
    const payment = await api(alice, `/api/trust-accounts/${accountId}/transactions/payment`, {
      clientId, matterId, amount, reference: `PAY/${tag}/${RUN}`, description: "652A OBS run2 payment", transactionDate: P,
    });
    const approved = await api(bob, `/api/trust-transactions/${payment.id}/approve`);
    return { depositId: deposit.id, paymentId: payment.id, paymentStatus: approved.status };
  };
  const m1 = await money(P1, "R1");
  const m2 = await money(P2, "R2");
  result.setup = { accountId, clientId, matterId, m1, m2 };
  const s1 = writeCsv("s1", statementLines(P1, "R1", amount));
  const s2 = writeCsv("s2", statementLines(P2, "R2", amount));
  const s3 = writeCsv("s3", [{ date: P2, desc: "Service fee", ref: `SVC/R2/${RUN}`, amount: -10 }]);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push({ at: new Date().toISOString(), error: String(e) }));
  page.on("console", (m) => m.type() === "error" && result.consoleErrors.push({ at: new Date().toISOString(), text: m.text().slice(0, 300) }));

  // Server-action bookkeeping: every Next-Action POST is logged; the calculate and the re-read
  // action ids are learned from their bodies ([reconId] / [accountId, {status: "APPROVED", size: 1000}]).
  const reconIds = new Set();
  let calcAction = null;
  let rereadAction = null;
  const actionOf = (req) => (req.method() === "POST" ? req.headers()["next-action"] : undefined);
  const isCalc = (req) => {
    const id = actionOf(req);
    if (!id) return false;
    if (calcAction) return id === calcAction;
    const body = req.postData() || "";
    return [...reconIds].some((r) => body === JSON.stringify([r]));
  };
  const isReread = (req) => {
    const id = actionOf(req);
    if (!id) return false;
    if (rereadAction) return id === rereadAction;
    const body = req.postData() || "";
    return body.includes(accountId) && body.includes('"APPROVED"') && body.includes('"size":1000');
  };
  page.on("request", (req) => {
    const id = actionOf(req);
    if (!id) return;
    const body = (req.postData() || "<multipart/none>").slice(0, 160);
    let kind = "other";
    if (isCalc(req)) { kind = "calculate"; calcAction = calcAction || id; }
    else if (isReread(req)) { kind = "reread"; rereadAction = rereadAction || id; }
    result.actions.push({ at: new Date().toISOString(), nextAction: id, kind, body });
  });
  page.on("response", async (res) => {
    if (!isCalc(res.request())) return;
    const t = await res.text().catch((e) => `<no body: ${e}>`);
    result.calculateResponses.push({ at: new Date().toISOString(), status: res.status(), result: (t.match(/\{"success".*\}/) || [t.slice(0, 300)])[0].slice(0, 400) });
  });
  page.on("requestfailed", (req) => {
    if (actionOf(req)) result.actions.push({ at: new Date().toISOString(), nextAction: actionOf(req), kind: "FAILED", error: req.failure()?.errorText });
  });

  // Interceptors: the first unused one whose predicate matches takes the request.
  const interceptors = [];
  const intercept = (name, pred, mode) => {
    const i = { name, pred, mode, hit: null };
    if (mode === "hold") i.gate = new Promise((r) => (i.release = r));
    interceptors.push(i);
    return i;
  };
  await page.route("**/trust-accounting/reconciliation/new", async (route) => {
    const req = route.request();
    const i = interceptors.find((x) => !x.hit && x.pred(req));
    if (!i) return route.continue();
    i.hit = { at: new Date().toISOString(), nextAction: actionOf(req), body: req.postData() };
    if (i.mode === "abort") return route.abort("failed");
    const how = await i.gate; // "continue" | "abort"
    i.releasedAt = new Date().toISOString();
    return how === "abort" ? route.abort("failed") : route.continue();
  });
  const waitHit = async (i, ms = 30_000) => {
    for (let t = 0; t < ms && !i.hit; t += 100) await sleep(100);
    if (!i.hit) throw new Error(`interceptor ${i.name} never saw its request`);
  };

  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner)
  await page.waitForURL(/\/org\//, { timeout: 60_000 });

  // ── R1 ───────────────────────────────────────────────────────────
  await openWizard(page);
  await selectAndUpload(page, accountId, P1, s1);
  // the reconciliation id is not known before create: let the first [uuid] calculate after create teach it
  const r1PreCount = sql(`select count(*) from T.trust_reconciliations`);
  const reviewPromise = toReview(page, s1);
  const R1 = await reviewPromise;
  reconIds.add(R1.reconId);
  result.R1 = { ...R1, reconsBefore: r1PreCount };
  // calcAction is learned on the first calculate after this point; the review calculate already ran,
  // so classify it from the log now.
  for (const a of result.actions) if (a.body === JSON.stringify([R1.reconId])) { a.kind = "calculate"; calcAction = calcAction || a.nextAction; }
  for (const a of result.actions) if (!rereadAction && a.body.includes('"APPROVED"') && a.body.includes('"size":1000')) { a.kind = "reread"; rereadAction = a.nextAction; }
  result.learned = { calcAction, rereadAction };
  const [unk1, deeds1, , unk2_1] = [R1.L["1"], R1.L["2"], R1.L["3"], R1.L["4"]];
  const pay1 = m1.paymentId;
  result.steps.review = await observe(page, "0-review", deeds1, R1.reconId);
  const match = async (lineId, txId) => {
    await page.getByTestId(`bank-line-${lineId}`).click();
    await page.getByTestId(`match-btn-${txId}`).click();
  };
  const backToStep3AndReview = async () => {
    await page.getByRole("button", { name: "Back", exact: true }).click(); // step 4 → 3
    await page.getByTestId("step-auto-match").waitFor();
    await page.getByTestId("step-next-btn").click(); // Review Matches: re-read + recalculate
    await page.getByTestId("reconciliation-split-pane").waitFor({ timeout: 60_000 });
  };

  // r1-e: match, only the calculate aborted
  const iE = intercept("r1e-calc-abort", isCalc, "abort");
  await match(deeds1, pay1);
  await waitHit(iE);
  result.steps.r1e_matchAborted = { aborted: iE.hit, pageErrorsSoFar: result.pageErrors.length, ...(await observe(page, "r1e-match-aborted", deeds1, R1.reconId)) };
  // r1-f: exclude → successful recalculation clears the stale line
  await page.getByTestId(`bank-line-${unk2_1}`).click();
  await page.getByTestId(`exclude-btn-${unk2_1}`).click();
  result.steps.r1f_excludeSucceeds = await observe(page, "r1f-cleared", unk2_1, R1.reconId);
  // successful unmatch (new Promise.all path, both succeed) → no banner, pane re-read
  await page.getByTestId(`unmatch-btn-${deeds1}`).click();
  result.steps.r1f_unmatchSucceeds = await observe(page, "r1f-unmatch-ok", deeds1, R1.reconId);
  result.steps.r1f_unmatchSucceeds.pay1InPane = (await page.getByTestId(`transaction-${pay1}`).count()) > 0;

  // r3 (single): match (succeeds), then unmatch with ONLY the re-read aborted
  await match(deeds1, pay1);
  result.steps.r3_matchedFirst = await observe(page, "r3-0-matched", deeds1, R1.reconId);
  const calcCountBefore = result.calculateResponses.length;
  const iR3 = intercept("r3-reread-abort", isReread, "abort");
  await page.getByTestId(`unmatch-btn-${deeds1}`).click();
  await waitHit(iR3);
  result.steps.r3_rereadFailed = { aborted: iR3.hit, ...(await observe(page, "r3-1-reread-failed", deeds1, R1.reconId, 3000)) };
  result.steps.r3_rereadFailed.calculateResponsesSince = result.calculateResponses.slice(calcCountBefore);
  result.steps.r3_rereadFailed.pay1InPane = (await page.getByTestId(`transaction-${pay1}`).count()) > 0;
  result.steps.r4_single1280 = await layoutProbe(page, 1280, "single");
  // refresh the pane as a user would (Back → Review Matches), then match again
  await backToStep3AndReview();
  result.steps.r3_refreshed = await observe(page, "r3-2-refreshed", deeds1, R1.reconId);
  result.steps.r3_refreshed.pay1InPane = (await page.getByTestId(`transaction-${pay1}`).count()) > 0;
  await match(deeds1, pay1);
  result.steps.r3_matchedAgain = await observe(page, "r3-3-matched-again", deeds1, R1.reconId);
  // r3 (both): unmatch with the re-read AND the calculate aborted
  const iR3b1 = intercept("r3b-reread-abort", isReread, "abort");
  const iR3b2 = intercept("r3b-calc-abort", isCalc, "abort");
  await page.getByTestId(`unmatch-btn-${deeds1}`).click();
  await waitHit(iR3b1);
  await waitHit(iR3b2);
  result.steps.r3_bothFailed = { aborted: [iR3b1.hit, iR3b2.hit], ...(await observe(page, "r3-4-both-failed", deeds1, R1.reconId, 3000)) };
  result.steps.r4_both1280 = await layoutProbe(page, 1280, "both");
  // clear: Back → Review Matches (re-read + successful recalculation)
  await backToStep3AndReview();
  result.steps.r3_cleared = await observe(page, "r3-5-cleared", deeds1, R1.reconId);
  save();

  // ── r2-success: hold the calculate after a match, reset, release (succeeds) ─────────
  await match(deeds1, pay1);
  const iS = intercept("r2s-calc-hold", isCalc, "hold");
  await waitHit(iS);
  const r1Before = dbRecon(R1.reconId);
  result.steps.r2s_heldInReview = await observe(page, "r2s-0-held-in-review", deeds1, R1.reconId, 500);
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 4 → 3
  await page.getByTestId("auto-match-back-btn").click(); // 3 → 2
  await page.getByRole("button", { name: "Back", exact: true }).click(); // 2 → 1
  await page.getByTestId("step-select").waitFor();
  await page.getByTestId("period-end-input").fill(P2); // reset (resetFromStep2)
  result.steps.r2s_afterReset = await observe(page, "r2s-1-reset-before-release", null, R1.reconId, 800);
  // try to upload S2 for the new period while the old calculate is still held
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(s2);
  const uploadedDuringHold = await page.getByTestId("upload-result").waitFor({ timeout: 8000 }).then(() => true, () => false);
  result.steps.r2s_uploadDuringHold = { uploadedDuringHold, actionsSinceHold: result.actions.filter((a) => a.at > iS.hit.at).map((a) => a.kind + ":" + a.nextAction) };
  result.steps.r2s_beforeRelease = await observe(page, "r2s-2-before-release", null, R1.reconId, 300);
  const relResp = page.waitForResponse((r) => isCalc(r.request()) && r.request().postData() === JSON.stringify([R1.reconId]), { timeout: 30_000 });
  iS.release("continue");
  const resp = await relResp;
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
  result.steps.r2s_afterRelease = { releasedAt: iS.releasedAt, responseStatus: resp.status(), r1Before, ...(await observe(page, "r2s-3-after-release", null, R1.reconId, 2500)) };
  result.steps.r2s_afterRelease.r1CalculatedEvents = sql(`select count(*) from T.audit_events where entity_id='${R1.reconId}' and event_type='trust_reconciliation.calculated' and occurred_at > '${iS.hit.at}'`);
  // proceed into R2: a new reconciliation, not R1's figures
  const R2 = await toReview(page, s2);
  reconIds.add(R2.reconId);
  result.R2 = R2;
  result.steps.r2s_newReview = await observe(page, "r2s-4-new-review", R2.L["2"], R2.reconId);
  result.steps.r2s_newReview.r1Now = dbRecon(R1.reconId);
  save();

  // ── tab B: import S3 (a line on P2) → every R2 calculate is refused ─────────────
  const pageB = await context.newPage();
  await openWizard(pageB);
  await selectAndUpload(pageB, accountId, P2, s3);
  result.steps.importS3 = squash(await pageB.getByTestId("upload-result").innerText());
  await pageB.close();

  const [unk1_2, deeds2] = [R2.L["1"], R2.L["2"]];
  const pay2 = m2.paymentId;
  // r1-a/b/c: refused match, exclude, unmatch
  await match(deeds2, pay2);
  result.steps.r1a_match = await observe(page, "r1a-match", deeds2, R2.reconId);
  await page.getByTestId(`bank-line-${unk1_2}`).click();
  await page.getByTestId(`exclude-btn-${unk1_2}`).click();
  result.steps.r1b_exclude = await observe(page, "r1b-exclude", unk1_2, R2.reconId);
  await page.getByTestId(`unmatch-btn-${deeds2}`).click();
  result.steps.r1c_unmatch = await observe(page, "r1c-unmatch", deeds2, R2.reconId);
  save();

  // ── r2-refused: hold, reset, release → a real server refusal ─────────────
  const iF = intercept("r2f-calc-hold", isCalc, "hold");
  await match(deeds2, pay2);
  await waitHit(iF);
  const r2Before = dbRecon(R2.reconId);
  result.steps.r2f_heldInReview = await observe(page, "r2f-0-held-in-review", deeds2, R2.reconId, 500);
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await page.getByTestId("auto-match-back-btn").click();
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await page.getByTestId("step-select").waitFor();
  await page.getByTestId("period-end-input").fill(P3);
  result.steps.r2f_beforeRelease = await observe(page, "r2f-1-reset-before-release", null, R2.reconId, 800);
  const relRespF = page.waitForResponse((r) => isCalc(r.request()) && r.request().postData() === JSON.stringify([R2.reconId]), { timeout: 30_000 });
  const nResp = result.calculateResponses.length;
  iF.release("continue");
  const respF = await relRespF;
  result.steps.r2f_afterRelease = { releasedAt: iF.releasedAt, responseStatus: respF.status(), r2Before, ...(await observe(page, "r2f-2-after-release", null, R2.reconId, 2500)) };
  result.steps.r2f_afterRelease.releasedResponse = result.calculateResponses.slice(nResp);
  // the step-1 wizard still works: Next → step 2 with no banner
  await page.getByTestId("step-next-btn").click();
  result.steps.r2f_step2 = await observe(page, "r2f-3-step2", null, null, 1000);

  result.finishedAt = new Date().toISOString();
  save();
  await browser.close();
  console.log(JSON.stringify({ ok: true, run: RUN }, null, 2));
})().catch((e) => {
  result.fatal = String(e && e.stack ? e.stack : e);
  save();
  console.error(e);
  process.exit(1);
});
