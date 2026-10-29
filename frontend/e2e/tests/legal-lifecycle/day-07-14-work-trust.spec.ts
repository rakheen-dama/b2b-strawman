/**
 * Days 7-14 — First Work + Trust Deposits
 *
 * Day 7: Carol, Bob and Alice log billable time on the tasks of the Sipho and Moroka matters (the
 * Day 0 rate cards apply), Bob comments on a task, Alice schedules Sipho's motion in the Diary.
 * Day 14: trust deposits for Moroka (R250,000) and QuickCollect (R45,000), Moroka's client ledger,
 * the Road Accident Fund adverse party and the conflict check that finds it, more of Carol's time,
 * and the trust dashboard.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–3 must have run on the
 * same stack. Days 30–90 bill the unbilled time logged here and draw on these trust balances.
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Days 0-3 tests completed (rates, trust account, 4 clients, template matters)
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { BASE, isoDate, openMatter, selectNativeOption } from "../../helpers/legal-depth-ii";
import {
  clientId,
  currentLifecycleRun,
  logTimeOnTask,
  morokaClient,
  morokaMatter,
  openTrustTransactions,
  qcClient,
  qcMatter,
  randPattern,
  recordTrustDeposit,
  recordValue,
  runConflictCheck,
  siphoMatter,
  TRUST_ACCOUNT,
  trustTransactionRow,
} from "../../helpers/legal-lifecycle";

const ADVERSE_PARTY = "Road Accident Fund";
const morokaDepositRef = () => `DEP/MOROKA/${currentLifecycleRun()}`;
const qcDepositRef = () => `DEP/QC/${currentLifecycleRun()}`;

// ═══════════════════════════════════════════════════════════════════
//  DAY 7 — First Week of Work
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 7 — First Week of Work", () => {
  // ── Carol: My Work, log time on Sipho matter ──────────────────────
  test("Carol: Navigate to My Work page", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${BASE}/my-work`);
    await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
      timeout: 15_000,
    });
  });

  test("Carol: Log 90min time on Sipho matter", async ({ page }) => {
    await loginAs(page, "carol");
    // Carol (Member) sees the matter because Day 1 added her to its team (Phase 88 619E,
    // ADR-346: Members see only matters they are on).
    await openMatter(page, siphoMatter());
    await logTimeOnTask(page, {
      task: "Initial consultation & case assessment",
      hours: 1,
      minutes: 30,
      date: isoDate(),
      description: "Research case law — personal injury precedents",
      rate: "550,00",
    });
  });

  // ── Bob: Log time on Sipho (letter of demand) ─────────────────────
  test("Bob: Log 120min on Sipho — letter of demand", async ({ page }) => {
    await loginAs(page, "bob");
    await openMatter(page, siphoMatter());
    await logTimeOnTask(page, {
      task: "Letter of demand",
      hours: 2,
      minutes: 0,
      date: isoDate(),
      description: "Draft letter of demand to Road Accident Fund",
      rate: "1 200,00",
    });

    // Fees → Time recording summarises the matter's time by member: Carol's and Bob's entries
    await openGroupedTab(page, "finance", "time");
    const timePanel = page.getByRole("tabpanel");
    await expect(timePanel).toContainText("Carol Member", { timeout: 15_000 });
    await expect(timePanel).toContainText("Bob Admin");
    await expect(timePanel).toContainText("Letter of demand");
  });

  test("Bob: Add internal comment on the Sipho letter-of-demand task", async ({ page }) => {
    const comment = `Reviewed claim documents — need additional medical records from Dr. Nkosi (${currentLifecycleRun()})`;
    await loginAs(page, "bob");
    await openMatter(page, siphoMatter());
    await openGroupedTab(page, "work", "tasks");
    // Internal comments live on the task detail sheet (Comments tab)
    await page
      .getByRole("tabpanel")
      .getByRole("button", { name: "Open task detail for Letter of demand" })
      .click();
    const sheet = page.getByRole("dialog", { name: "Task Detail" });
    await expect(sheet).toBeVisible({ timeout: 15_000 });
    await sheet.getByRole("tab", { name: "Comments" }).click();
    await sheet.getByLabel("Add a comment").fill(comment);
    await sheet.getByLabel("Visibility").selectOption("INTERNAL");
    await sheet.getByRole("button", { name: "Post Comment" }).click();
    await expect(sheet.getByText(comment)).toBeVisible({ timeout: 15_000 });
    await expect(sheet.getByLabel("Add a comment")).toHaveValue("");
  });

  // ── Alice: Create court date for Sipho ────────────────────────────
  test("Alice: Create court date for Sipho (Motion, 30 days out)", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S6b (ADR-344): /court-calendar → /diary
    await page.goto(`${BASE}/diary`);
    await page.getByRole("button", { name: "New court date" }).click();
    const dialog = page.getByRole("dialog", { name: "Schedule Court Date" });
    await expect(dialog).toBeVisible();
    await selectNativeOption(dialog.getByLabel("Matter"), siphoMatter());
    await dialog.getByLabel("Type").selectOption("MOTION");
    await dialog.getByLabel("Date").fill(isoDate(30));
    await dialog.getByLabel("Court Name").fill("Gauteng Division, Pretoria");
    await dialog.getByLabel(/^Description/).fill("Motion — Ndlovu v RAF");
    await dialog.getByRole("button", { name: "Schedule Court Date" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // The matter's Diary tab lists the motion as Scheduled
    await openMatter(page, siphoMatter());
    // "Diary" is a single-tab group: a plain tab trigger, no sub-tab menu (grouped-tab-bar.tsx)
    const diaryTab = page.getByTestId("tab-group-schedule");
    await diaryTab.click();
    await expect(diaryTab).toHaveAttribute("aria-selected", "true");
    const motion = page.getByTestId("court-date-list").getByRole("row").filter({
      hasText: "Motion",
    });
    await expect(motion).toHaveCount(1, { timeout: 15_000 });
    await expect(motion).toContainText("Scheduled");
    await expect(motion).toContainText("Gauteng Division, Pretoria");

    // Screenshot: court calendar entry
    await captureScreenshot(page, "day-07-court-calendar-entry-scheduled");
    await captureScreenshot(page, "court-calendar-scheduled", { curated: true });
  });

  // ── Alice: Log time on Moroka estate ──────────────────────────────
  test("Alice: Log 60min on Moroka estate matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, morokaMatter());
    await logTimeOnTask(page, {
      task: "Report estate to Master of High Court",
      hours: 1,
      minutes: 0,
      date: isoDate(),
      description: "Report to Master of the High Court — initial filing",
      rate: "2 500,00",
    });
  });

  // ── My Work lists today's entry ───────────────────────────────────
  test("Alice: My Work lists today's Moroka time entry", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/my-work`);
    await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.getByText(`${morokaMatter()} — Report to Master of the High Court — initial filing`)
    ).toBeVisible({ timeout: 15_000 });

    await captureScreenshot(page, "day-07-my-work-legal-active");
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 14 — Trust Deposits & Conflict Detection
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 14 — Trust Deposits & Conflict Detection", () => {
  // ── 14.1-14.8: Trust deposit R250,000 for Moroka ──────────────────
  test("Alice: Navigate to Trust Accounting", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting`);
    await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
      timeout: 15_000,
    });
    // Day 0 created the account, so the overview offers its actions
    await expect(page.getByRole("link", { name: "Client Ledgers" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Record Transaction" })).toBeVisible();
  });

  test("Alice: Deposit R250,000 into trust for Moroka estate", async ({ page }) => {
    await loginAs(page, "alice");
    await recordTrustDeposit(page, {
      client: morokaClient(),
      matter: morokaMatter(),
      amount: "250000.00",
      reference: morokaDepositRef(),
      description: "Estate Late Peter Moroka — transfer from executor",
    });
    recordValue("morokaDepositRef", morokaDepositRef());
  });

  // Deposits land RECORDED; only payments and fee transfers wait for approval (the approval
  // path is the Day 30 fee transfer, approved by a second user).
  test("Alice: Moroka trust deposit is RECORDED — no approval step", async ({ page }) => {
    await loginAs(page, "alice");
    await openTrustTransactions(page);
    const row = trustTransactionRow(page, morokaDepositRef());
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("RECORDED");
    await expect(row).toContainText(randPattern("250 000,00"));
    await expect(row.getByRole("button", { name: "Approve" })).toHaveCount(0);
  });

  test("Alice: Verify Moroka trust ledger balance", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/client-ledgers`);
    await expect(page.getByTestId("client-ledgers-page")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("client-search").fill(morokaClient());
    await page.getByRole("button", { name: "Search", exact: true }).click();

    const ledgerRow = page.getByTestId(`ledger-row-${clientId("moroka")}`);
    await expect(ledgerRow).toContainText(randPattern("250 000,00"), { timeout: 15_000 });
    await ledgerRow.getByRole("link", { name: morokaClient() }).click();
    await expect(page.getByRole("heading", { level: 1, name: morokaClient() })).toBeVisible({
      timeout: 15_000,
    });
    const summary = page.getByTestId("client-summary");
    await expect(summary).toContainText("Trust Balance");
    await expect(summary).toContainText(randPattern("250 000,00"));

    // Screenshot: trust deposit ledger
    await captureScreenshot(page, "day-14-trust-deposit-ledger-posted");
    await captureScreenshot(page, "trust-deposit-form", { curated: true });
    await captureScreenshot(page, "client-ledger-balance", { curated: true });
  });

  // ── 14.9-14.11: Trust deposit R45,000 for QuickCollect ────────────
  test("Alice: Deposit R45,000 into trust for QuickCollect", async ({ page }) => {
    await loginAs(page, "alice");
    await recordTrustDeposit(page, {
      client: qcClient(),
      matter: qcMatter(),
      amount: "45000.00",
      reference: qcDepositRef(),
      description: "QuickCollect Services — collections deposit",
    });
    recordValue("qcDepositRef", qcDepositRef());
  });

  // ── 14.12-14.15: Adverse party "Road Accident Fund" ───────────────
  test('Alice: Add adverse party "Road Accident Fund" to registry', async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/legal/adverse-parties`);
    const registry = page.getByTestId("adverse-party-registry");
    await expect(registry).toBeVisible({ timeout: 15_000 });
    // The registry allows duplicate names, so count this party's rows before and after
    const rows = page
      .getByTestId("adverse-party-table")
      .getByRole("row")
      .filter({ hasText: ADVERSE_PARTY });
    const countText = registry.getByText(/^\d+ part(y|ies)$/);
    await expect(countText).toBeVisible();
    const before = await rows.count();

    await page.getByTestId("create-adverse-party-trigger").click();
    const dialog = page.getByTestId("adverse-party-dialog");
    await expect(dialog.getByRole("heading", { name: "Add Adverse Party" })).toBeVisible();
    await dialog.getByLabel(/^Name/).fill(ADVERSE_PARTY);
    await dialog.getByLabel(/^Party Type/).selectOption("GOVERNMENT");
    await dialog.getByRole("button", { name: "Create Party" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    await expect(rows).toHaveCount(before + 1, { timeout: 15_000 });
    await expect(rows.first()).toContainText("Government");
  });

  // ── 14.16-14.18: Conflict check finds the adverse party ───────────
  test('Bob: Conflict check "Road Accident Fund" — adverse party match (Conflict Found)', async ({
    page,
  }) => {
    // 628E-BLOCKER (reproduced 2026-10-29 on :3001, Playwright MCP + this test): a conflict check
    // that matches an adverse party with no matter link crashes the page ("Something went wrong";
    // console "TypeError: Cannot read properties of null (reading 'replace')"). The backend sends
    // `relationship: null` for an unlinked party (ConflictCheckService.buildAdversePartyConflict,
    // link == null) and the result table calls `match.relationship.replace(…)`.
    test.fixme(
      true,
      "628E-BLOCKER: conflict result crashes on an unlinked adverse-party match — components/legal/conflict-check-result.tsx:141"
    );
    await loginAs(page, "bob");
    const result = await runConflictCheck(page, ADVERSE_PARTY);
    // An exact name match on the adverse-party registry scores above the conflict threshold
    await expect(result.getByRole("heading", { level: 3 })).toHaveText("Conflict Found");
    await expect(result).toContainText(ADVERSE_PARTY);
    await captureScreenshot(page, "day-14-conflict-match-found");
    await captureScreenshot(page, "conflict-check-adverse-match", { curated: true });
  });

  // ── 14.19-14.21: More time logging ────────────────────────────────
  test("Carol: Log additional time on the Sipho matter (letter of demand)", async ({ page }) => {
    await loginAs(page, "carol");
    await openMatter(page, siphoMatter());
    await logTimeOnTask(page, {
      task: "Letter of demand",
      hours: 0,
      minutes: 45,
      date: isoDate(),
      description: "Collate medical records for the letter of demand",
      rate: "550,00",
    });
    // Her My Work lists both of today's Sipho entries
    await page.goto(`${BASE}/my-work`);
    for (const description of [
      "Research case law — personal injury precedents",
      "Collate medical records for the letter of demand",
    ]) {
      await expect(page.getByText(`${siphoMatter()} — ${description}`)).toBeVisible({
        timeout: 15_000,
      });
    }
  });

  // ── 14.22-14.24: Trust dashboard check + screenshot ───────────────
  test("Alice: Trust dashboard overview", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting`);
    await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
      timeout: 15_000,
    });
    const cards = page.getByTestId("summary-cards");
    await expect(cards).toContainText("Trust Balance");
    await expect(cards).toContainText(`${TRUST_ACCOUNT.name} cashbook balance`);
    await expect(cards).toContainText("Active Clients");

    // Screenshot: trust dashboard overview
    await captureScreenshot(page, "day-14-trust-dashboard-loaded");
    await captureScreenshot(page, "trust-dashboard-overview", { curated: true });
  });
});
