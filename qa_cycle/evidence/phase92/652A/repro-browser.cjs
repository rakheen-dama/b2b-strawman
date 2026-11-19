// 652A reproduce-first (task 652.1, browser): headless Chromium against the e2e mock-auth stack
// (:3001) as Alice. Every state change goes through the wizard UI. psql is read-only evidence.
//
// A REAL server refusal makes calculate fail after a saved change: tab A reconciles statement S1
// (last line yesterday) to period end today; tab B then imports S2 with a line dated today, so
// every later calculate on A is refused by the period rule "another statement covers the gap"
// (TrustReconciliationService.requireStatementFitsPeriodEnd, 646C). Then on tab A: match, exclude,
// unmatch — each saved, each followed by a refused calculate. Last, one calculate request is
// aborted (route abort) to record what the rejected-promise shape does.
//
// Run from the worktree's frontend/: NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652A/repro-browser.cjs
// PERIOD_END_OFFSET_DAYS (default 0) moves the period end back from today, so a re-run on the same
// stack is not refused at create by the statements an earlier run imported.
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const { chromium } = require(
  path.resolve(process.cwd(), "node_modules/@playwright/test"),
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const OUT = __dirname;
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
const today = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Africa/Johannesburg",
}).format(new Date());
const addDays = (iso, n) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const periodEnd = addDays(
  today,
  -Number(process.env.PERIOD_END_OFFSET_DAYS || 0),
);
const result = {
  run: RUN,
  today,
  periodEnd,
  steps: {},
  calculateResponses: [],
  pageErrors: [],
  consoleErrors: [],
};

function psql(q) {
  return execFileSync(
    "docker",
    [
      "exec",
      "e2e-postgres",
      "psql",
      "-U",
      "postgres",
      "-d",
      "app",
      "-X",
      "-At",
      "-F",
      "|",
      "-c",
      q,
    ],
    { encoding: "utf8" },
  ).trim();
}
const SCHEMA = psql(
  "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1",
);
const sql = (q) => psql(q.replace(/\bT\./g, `${SCHEMA}.`));

function writeCsv(name, lines) {
  let bal = 0;
  const rows = [
    `Standard Bank Trust Account - 652A ${RUN}`,
    "Date,Description,Reference,Amount,Balance",
  ];
  for (const l of lines) {
    bal = +(bal + l.amount).toFixed(2);
    rows.push(
      `${l.date},${l.desc},${l.ref},${l.amount.toFixed(2)},${bal.toFixed(2)}`,
    );
  }
  const file = path.join(OUT, `${name}-${RUN}.csv`);
  fs.writeFileSync(file, rows.join("\n") + "\n");
  return file;
}

async function wizardToUpload(page, accountId, csvFile) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, {
    waitUntil: "load",
  });
  await page.getByTestId("step-select").waitFor({ timeout: 60_000 });
  await page.getByTestId("account-select").selectOption(accountId);
  await page.getByTestId("period-end-input").fill(periodEnd);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("file-input").setInputFiles(csvFile);
  await page.getByTestId("upload-result").waitFor({ timeout: 60_000 });
}

// The page's state after an action: banner, the line's badge, the summary's figures.
async function observe(page, label, lineId, reconId) {
  await page.waitForTimeout(1500); // the action and its recalculation have settled
  const banner = page.getByTestId("reconciliation-error");
  const summary = page.getByTestId("balance-summary");
  const state = {
    banner: (await banner.count()) ? (await banner.innerText()).trim() : null,
    staleMarker: await page.getByTestId("reconciliation-figures-stale").count(),
    line: (await page.getByTestId(`bank-line-${lineId}`).innerText()).replace(
      /\s+/g,
      " ",
    ),
    progress: (await page.getByTestId("match-progress").innerText()).replace(
      /\s+/g,
      " ",
    ),
    summary: (await summary.innerText()).replace(/\s+/g, " ").slice(0, 400),
    dbLine: sql(
      `select match_status, trust_transaction_id from T.bank_statement_lines where id='${lineId}'`,
    ),
    dbRecon: sql(
      `select bank_balance, cashbook_balance, difference, is_balanced, updated_at from T.trust_reconciliations where id='${reconId}'`,
    ),
    shot: `repro-browser-${label}.png`,
  };
  await page.screenshot({ path: path.join(OUT, state.shot), fullPage: true });
  return state;
}

(async () => {
  const accountId = sql(
    "select id from T.trust_accounts where is_primary order by created_at limit 1",
  );
  const payment = sql(
    "select id, amount, coalesce(bank_statement_line_id::text, '') from T.trust_transactions where status='APPROVED' and transaction_type='PAYMENT' order by created_at limit 1",
  ).split("|");
  if (!payment[0])
    throw new Error("no APPROVED payment to match on this stack");
  const amount = Number(payment[1]);
  const s1 = writeCsv("s1", [
    {
      date: addDays(periodEnd, -2),
      desc: "Unknown credit",
      ref: `UNK/652A/${RUN}`,
      amount: 700,
    },
    {
      date: addDays(periodEnd, -1),
      desc: "Deeds office",
      ref: `DEEDS/652A/${RUN}`,
      amount: -amount,
    },
  ]);
  const s2 = writeCsv("s2", [
    {
      date: periodEnd,
      desc: "Service fee",
      ref: `SVC/652A/${RUN}`,
      amount: -10,
    },
  ]);
  Object.assign(result, {
    schema: SCHEMA,
    accountId,
    paymentId: payment[0],
    amount,
  });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1400 },
    timezoneId: "Africa/Johannesburg",
  });
  const page = await context.newPage();
  page.on("pageerror", (e) => result.pageErrors.push(String(e)));
  page.on(
    "console",
    (m) =>
      m.type() === "error" && result.consoleErrors.push(m.text().slice(0, 300)),
  );
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click(); // Alice (owner) is the default
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  if (payment[2]) {
    // A re-run: an earlier run left the payment matched — Alice unmatches it through the API.
    const token = (await context.cookies()).find(
      (c) => c.name === "mock-auth-token",
    )?.value;
    const res = await context.request.post(
      `http://localhost:8081/api/bank-statement-lines/${payment[2]}/unmatch`,
      {
        headers: { Authorization: `Bearer ${token}` },
      },
    );
    result.setupUnmatch = { line: payment[2], status: res.status() };
  }

  // Tab A: S1 through steps 1-3 into review (first calculate succeeds: no other statement yet)
  await wizardToUpload(page, accountId, s1);
  await page.getByTestId("step-next-btn").click();
  await page.getByTestId("auto-match-btn").click();
  await page.getByTestId("auto-match-result").waitFor({ timeout: 60_000 });
  await page.getByTestId("step-next-btn").click();
  await page
    .getByTestId("reconciliation-split-pane")
    .waitFor({ timeout: 60_000 });
  const stmt = sql(
    `select id from T.bank_statements where file_name='${path.basename(s1)}'`,
  );
  const reconId = sql(
    `select id from T.trust_reconciliations where bank_statement_id='${stmt}'`,
  );
  const lineIds = Object.fromEntries(
    sql(
      `select line_number, id from T.bank_statement_lines where bank_statement_id='${stmt}'`,
    )
      .split("\n")
      .map((r) => r.split("|")),
  );
  Object.assign(result, { statementId: stmt, reconId, lineIds });
  const [unknownLine, deedsLine] = [lineIds["1"], lineIds["2"]];
  // A server action POSTs to the page URL with a Next-Action header; calculate's body is [reconId]
  // (no line id). Its RSC response carries the action's return value.
  const isCalculate = (req) => {
    const body = req.postData() || "";
    return (
      req.method() === "POST" &&
      !!req.headers()["next-action"] &&
      body.includes(reconId) &&
      !body.includes(unknownLine) &&
      !body.includes(deedsLine)
    );
  };
  page.on("response", async (res) => {
    if (!isCalculate(res.request())) return;
    const text = await res.text().catch((e) => `<no body: ${e}>`);
    result.calculateResponses.push({
      status: res.status(),
      result: (text.match(/\{"success".*\}/) || [text.slice(0, 300)])[0],
    });
  });
  // If auto-match took the payment, unmatch it now, while calculate still succeeds.
  if (
    sql(
      `select match_status from T.bank_statement_lines where id='${deedsLine}'`,
    ) !== "UNMATCHED"
  ) {
    await page.getByTestId(`unmatch-btn-${deedsLine}`).click();
    await page.waitForTimeout(1500);
    result.steps.preUnmatch = sql(
      `select match_status from T.bank_statement_lines where id='${deedsLine}'`,
    );
  }
  result.steps.review = await observe(page, "0-review", deedsLine, reconId);

  // Tab B: import S2 (a line dated today) — from now on calculate on A is refused.
  const pageB = await context.newPage();
  await wizardToUpload(pageB, accountId, s2);
  result.steps.importS2 = (
    await pageB.getByTestId("upload-result").innerText()
  ).replace(/\s+/g, " ");
  await pageB.close();

  // 1. Match: the line to the payment
  await page.getByTestId(`bank-line-${deedsLine}`).click();
  await page.getByTestId(`match-btn-${payment[0]}`).click();
  result.steps.match = await observe(page, "match", deedsLine, reconId);
  // 2. Exclude: the unknown credit
  await page.getByTestId(`bank-line-${unknownLine}`).click();
  await page.getByTestId(`exclude-btn-${unknownLine}`).click();
  result.steps.exclude = await observe(page, "exclude", unknownLine, reconId);
  // 3. Unmatch: the matched line
  await page.getByTestId(`unmatch-btn-${deedsLine}`).click();
  result.steps.unmatch = await observe(page, "unmatch", deedsLine, reconId);

  // 4. Rejected-promise shape: match again, with the calculate request aborted (route abort).
  let aborted = null;
  await page.route("**/trust-accounting/reconciliation/new", async (route) => {
    const req = route.request();
    if (!aborted && isCalculate(req)) {
      aborted = {
        nextAction: req.headers()["next-action"],
        body: (req.postData() || "").slice(0, 200),
      };
      return route.abort("failed");
    }
    return route.continue();
  });
  await page.getByTestId(`bank-line-${deedsLine}`).click();
  await page.getByTestId(`match-btn-${payment[0]}`).click();
  const afterAbort = await observe(
    page,
    "match-calc-aborted",
    deedsLine,
    reconId,
  );
  result.steps.matchCalcAborted = { aborted, ...afterAbort };

  fs.writeFileSync(
    path.join(OUT, "repro-browser-result.json"),
    JSON.stringify(result, null, 2),
  );
  await browser.close();
  console.log(JSON.stringify(result, null, 2));
})().catch((e) => {
  fs.writeFileSync(
    path.join(OUT, "repro-browser-result.json"),
    JSON.stringify({ ...result, fatal: String(e) }, null, 2),
  );
  console.error(e);
  process.exit(1);
});
