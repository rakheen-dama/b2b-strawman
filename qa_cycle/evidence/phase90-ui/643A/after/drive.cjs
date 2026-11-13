// 643A after-OBS step 1: mock-login (Alice owner); record the pre-reconciliation trust dashboard tiles;
// drive the reconciliation wizard (New Reconciliation -> Period End -> Choose CSV File -> Run Auto-Match ->
// Review Matches, which creates a DRAFT then crashes — known pre-existing bug); record the result.
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643A/frontend/node_modules/@playwright/test");
const fs = require("fs"), path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org";
const OUT = __dirname, CSV = path.join(OUT, "statement-2026-11-13.csv"), PERIOD_END = "2026-11-13";
const lines = (t) => t.split("\n").map((s) => s.trim()).filter(Boolean);
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "light" });
  const page = await ctx.newPage();
  const consoleMsgs = [];
  page.on("console", (m) => { if (["error", "warning"].includes(m.type())) consoleMsgs.push({ url: page.url(), type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => consoleMsgs.push({ url: page.url(), type: "pageerror", text: String(e).slice(0, 300) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  const out = { steps: [] };
  const step = (s) => { out.steps.push(s); console.log("STEP", s); };
  out.preTrustPosition = (await get("/api/dashboard/trust-position")).body;
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: "load" });
  await page.locator("h1", { hasText: "Trust Accounting" }).waitFor({ timeout: 60000 });
  await page.locator('[data-testid="summary-cards"]').waitFor();
  out.preCards = await page.locator('[data-testid="summary-cards"] > *').evaluateAll((els) => els.map((el) => el.innerText.split("\n").map((s) => s.trim()).filter(Boolean)));
  await page.locator('[data-testid="summary-cards"]').screenshot({ path: path.join(OUT, "summary-cards-pre-recon-1440.png") });
  step("pre-reconciliation trust dashboard tiles recorded");

  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation`, { waitUntil: "load" });
  await page.locator('[data-testid="new-reconciliation-btn"]').waitFor({ timeout: 60000 });
  await page.locator('[data-testid="new-reconciliation-btn"]').click();
  await page.locator('[data-testid="step-select"]').waitFor({ timeout: 60000 });
  await page.locator('[data-testid="account-select"]').waitFor();
  await page.locator('[data-testid="period-end-input"]').fill(PERIOD_END);
  await page.locator('[data-testid="step-next-btn"]').click();
  step(`Step 1: primary account preselected, Period End ${PERIOD_END}, Next`);
  await page.locator('[data-testid="file-input"]').setInputFiles(CSV);
  await page.locator('[data-testid="upload-result"]').waitFor({ timeout: 60000 });
  out.uploadResult = lines(await page.locator('[data-testid="upload-result"]').innerText());
  await page.locator('[data-testid="step-next-btn"]').click();
  step("Step 2: Choose CSV File statement-2026-11-13.csv, Next");
  await page.locator('[data-testid="auto-match-btn"]').click();
  await page.locator('[data-testid="auto-match-result"]').waitFor({ timeout: 60000 });
  out.autoMatchResult = lines(await page.locator('[data-testid="auto-match-result"]').innerText());
  await page.locator('[data-testid="step-next-btn"]').click();
  step("Step 3: Run Auto-Match, Review Matches");
  const res = await Promise.race([
    page.locator('[data-testid="reconciliation-split-pane"]').waitFor({ timeout: 30000 }).then(() => "split-pane rendered"),
    page.getByText("Something went wrong").waitFor({ timeout: 30000 }).then(() => "Something went wrong"),
  ]).catch((e) => "neither: " + String(e).slice(0, 120));
  out.step4 = res;
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, "recon-step4-crash-1440.png"), fullPage: true });
  step("Step 4: " + res);
  const acct = out.preTrustPosition.trustAccountId;
  out.reconciliationsApi = (await get(`/api/trust-accounts/${acct}/reconciliations`)).body;
  out.postTrustPosition = (await get("/api/dashboard/trust-position")).body;
  out.console = consoleMsgs;
  fs.writeFileSync(path.join(OUT, "drive-raw.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
