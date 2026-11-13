// 643A before-OBS: mock-login as Alice; baseline trust dashboard + Home widget; drive the
// reconciliation wizard (upload Standard Bank CSV, auto-match, exclude opening-balance line,
// attempt complete); re-observe trust dashboard, Home widget, trust-position JSON, trend 404.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const fs = require("fs"), path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org";
const OUT = __dirname, CSV = path.join(OUT, "statement-2026-11-13.csv"), PERIOD_END = "2026-11-13";
const shot = (page, n) => page.screenshot({ path: path.join(OUT, n), fullPage: true });
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  const consoleMsgs = []; globalThis.__page = page; globalThis.__console = consoleMsgs;
  page.on("console", (m) => { if (["error", "warning"].includes(m.type())) consoleMsgs.push({ url: page.url(), type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => consoleMsgs.push({ url: page.url(), type: "pageerror", text: String(e).slice(0, 300) }));
  const out = { viewport: "1440x900 light", steps: [] };
  const step = (s) => { out.steps.push(s); console.log("STEP", s); };

  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  step("mock-login Sign In (Alice owner) -> /dashboard");
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };

  const readCards = async () => page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean)));
  const readDash = async (tag) => {
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
    await page.locator("h1", { hasText: "Trust Accounting" }).waitFor({ timeout: 60000 });
    await page.locator('[data-testid="summary-cards"]').waitFor();
    const cards = await readCards();
    const tbl = page.locator('[data-testid="transactions-table"]');
    const cols = await tbl.locator("thead th").allInnerTexts();
    const rows = await tbl.locator("tbody tr").count();
    await shot(page, `trust-dashboard-${tag}-1440.png`);
    return { cards, transactionsTable: { testid: "transactions-table", columns: cols, rowCount: rows } };
  };
  const readHome = async (tag) => {
    await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: "load" });
    const w = page.locator('[data-testid="trust-position-widget"]');
    await w.waitFor({ timeout: 60000 });
    await w.scrollIntoViewIfNeeded();
    await shot(page, `home-${tag}-1440.png`);
    await w.screenshot({ path: path.join(OUT, `home-trust-widget-${tag}-1440.png`) });
    return (await w.innerText()).split("\n").map((s) => s.trim()).filter(Boolean);
  };

  out.preReconciliation = { trustPosition: (await get("/api/dashboard/trust-position")).body, dashboard: await readDash("pre"), homeWidget: await readHome("pre") };
  step("observed pre-reconciliation trust dashboard + Home widget");

  // Reconciliation list -> New Reconciliation wizard
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.locator('[data-testid="new-reconciliation-btn"]').waitFor({ timeout: 60000 });
  await page.locator('[data-testid="new-reconciliation-btn"]').click();
  await page.locator('[data-testid="step-select"]').waitFor({ timeout: 60000 });
  await page.locator('[data-testid="account-select"]').waitFor();
  step("Trust accounting > Reconciliation > New Reconciliation");
  await page.locator('[data-testid="period-end-input"]').fill(PERIOD_END);
  await page.locator('[data-testid="step-next-btn"]').click();
  step(`Step 1: primary account preselected, Period End ${PERIOD_END}, Next`);
  await page.locator('[data-testid="file-input"]').setInputFiles(CSV);
  await page.locator('[data-testid="upload-result"]').waitFor({ timeout: 60000 });
  out.uploadResult = await page.locator('[data-testid="upload-result"]').innerText();
  await shot(page, "recon-step2-uploaded-1440.png");
  await page.locator('[data-testid="step-next-btn"]').click();
  step("Step 2: uploaded statement-2026-11-13.csv (Choose CSV File), Next");
  await page.locator('[data-testid="auto-match-btn"]').click();
  await page.locator('[data-testid="auto-match-result"]').waitFor({ timeout: 60000 });
  out.autoMatchResult = await page.locator('[data-testid="auto-match-result"]').innerText();
  await page.locator('[data-testid="step-next-btn"]').click();
  await page.locator('[data-testid="reconciliation-split-pane"]').waitFor({ timeout: 60000 });
  step("Step 3: Run Auto-Match, Review Matches");
  // Exclude the Opening Balance 0.00 line (only unmatched line) — triggers calculate
  const openLine = page.locator('[data-testid^="bank-line-"]', { hasText: "Opening Balance" });
  out.openingLineStatusBeforeExclude = (await openLine.innerText()).split("\n").pop();
  await openLine.click();
  await openLine.locator('[data-testid^="exclude-btn-"]').click();
  await page.locator('[data-testid="balance-summary"]').waitFor();
  await page.waitForFunction(() => document.querySelector('[data-testid^="bank-line-"]') && [...document.querySelectorAll('[data-testid^="bank-line-"]')].some((e) => e.innerText.includes("Opening Balance") && e.innerText.includes("Excluded")), null, { timeout: 30000 });
  await page.waitForTimeout(1500);
  step("Step 4: selected 'Opening Balance' 0.00 line, Exclude (recalculates three-way check)");
  out.matchProgress = await page.locator('[data-testid="match-progress"]').innerText();
  out.balanceSummary = (await page.locator('[data-testid="balance-summary"]').innerText()).split("\n").map((s) => s.trim()).filter(Boolean);
  const btn = page.locator('[data-testid="complete-reconciliation-btn"]');
  out.completeBtnDisabled = await btn.isDisabled();
  await shot(page, "recon-step4-review-1440.png");
  if (!out.completeBtnDisabled) {
    await btn.click();
    await page.locator('[data-testid="step-complete"]').waitFor({ timeout: 60000 });
    await shot(page, "recon-complete-1440.png");
    step("Step 4: Complete Reconciliation -> step 5 complete");
  } else step("Step 4: Complete Reconciliation button DISABLED (not balanced) — cannot complete");

  const acct = out.preReconciliation.trustPosition && out.preReconciliation.trustPosition.trustAccountId;
  out.reconciliationsApi = (await get(`/api/trust-accounts/${acct}/reconciliations`)).body;
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.locator('[data-testid="new-reconciliation-btn"]').waitFor({ timeout: 60000 });
  await shot(page, "recon-list-post-1440.png");
  out.reconListRows = await page.locator('[data-testid^="reconciliation-row-"]').allInnerTexts().catch(() => []);

  out.postReconciliation = { trustPosition: await get("/api/dashboard/trust-position"), dashboard: await readDash("post"), homeWidget: await readHome("post") };
  out.trend = await get("/api/dashboard/trust-balance-trend");
  step("observed post-reconciliation trust dashboard + Home widget + API");

  await page.setViewportSize({ width: 1024, height: 768 });
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.locator('[data-testid="summary-cards"]').waitFor({ timeout: 60000 });
  await shot(page, "trust-dashboard-post-1024.png");
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: "load" });
  await page.locator('[data-testid="trust-position-widget"]').waitFor({ timeout: 60000 });
  await shot(page, "home-post-1024.png");
  await page.locator('[data-testid="trust-position-widget"]').screenshot({ path: path.join(OUT, "home-trust-widget-post-1024.png") });

  out.console = consoleMsgs;
  fs.writeFileSync(path.join(OUT, "drive-raw.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch(async (e) => { console.error(e); try { await globalThis.__page.screenshot({ path: path.join(OUT, "failure.png"), fullPage: true }); console.error("CONSOLE", JSON.stringify(globalThis.__console)); console.error("BODY", (await globalThis.__page.locator("main").innerText()).slice(0, 3000)); } catch (x) { console.error(x); } process.exit(1); });
