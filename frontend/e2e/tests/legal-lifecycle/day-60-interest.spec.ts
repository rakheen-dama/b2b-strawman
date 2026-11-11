/**
 * Day 60 — Interest Run & Investments
 *
 * Interest run on the trust account: create the run for the last 30 days and calculate the
 * per-client allocations at the Day 0 LPFF rate (6.5%, 50% LPFF share). The creator cannot approve
 * her own run (two-member rule): Bob approves it from the runs table's Actions column and Alice
 * posts it (Phase 89 630B, G88-F39). Investments: Moroka R200,000 Standard
 * Bank fixed deposit (firm surplus funds, s86(3)) and QuickCollect R30,000 Nedbank call account
 * (client instruction, s86(4)). Second billing cycle (new Sipho time → fee note), timesheet report
 * and profitability report.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–45 must have run on the
 * same stack (trust account + LPFF rate, deposits, Moroka's fee transfer, rated time).
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { BASE, clickThrough, isoDate, openMatter } from "../../helpers/legal-depth-ii";
import {
  clientId,
  feeNoteTotalsRow,
  generateFeeNoteDraft,
  logTimeOnTask,
  matterId,
  morokaClient,
  openClientFeeNotes,
  openTrustTransactions,
  qcClient,
  randPattern,
  recordValue,
  siphoClient,
  siphoMatter,
} from "../../helpers/legal-lifecycle";

const SIPHO_SUMMONS_TIME = "Draft combined summons — RAF claim";

async function openInvestments(page: Page): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/investments`);
  await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("heading", { level: 1, name: "Investments" })).toBeVisible();
}

/** Places a trust investment for a client on its matter (the dialog takes raw UUIDs). */
async function placeInvestment(
  page: Page,
  opts: {
    clientId: string;
    /** The client's matter whose trust money is invested (required since G89-F28). */
    matterId: string;
    institution: string;
    accountNumber: string;
    principal: string;
    ratePercent: string;
    maturityDate?: string;
    basis: "Firm (surplus trust funds)" | "Client instruction";
    notes: string;
  }
): Promise<void> {
  await openInvestments(page);
  await page.getByRole("button", { name: "Place Investment" }).click();
  const dialog = page.getByTestId("place-investment-dialog");
  await expect(dialog.getByRole("heading", { name: "Place Investment" })).toBeVisible();
  await dialog.getByLabel("Client ID").fill(opts.clientId);
  await dialog.getByLabel("Matter ID").fill(opts.matterId);
  await dialog.getByLabel("Institution").fill(opts.institution);
  await dialog.getByLabel("Account Number").fill(opts.accountNumber);
  await dialog.getByLabel("Principal").fill(opts.principal);
  await dialog.getByLabel("Interest Rate %").fill(opts.ratePercent);
  if (opts.maturityDate) {
    await dialog.getByLabel("Maturity Date (optional)").fill(opts.maturityDate);
  }
  await dialog.getByLabel("Notes (optional)").fill(opts.notes);
  await dialog.getByRole("radio", { name: opts.basis }).check();
  await expect(dialog.getByTestId("investment-basis-help")).toHaveText(
    opts.basis === "Client instruction" ? /5%|statutory/i : /arrangement/i
  );
  // Pressed from the keyboard: on the suite's 1280×720 viewport the dialog is taller than the
  // screen and does not scroll, so its "Place Investment" button sits off-screen (follow-up:
  // components/ui/dialog.tsx:56-70 has no max-height / overflow-y-auto)
  await dialog.getByRole("button", { name: "Place Investment" }).press("Enter");
  await expect(dialog).toBeHidden({ timeout: 15_000 });
}

test.describe.serial("Day 60 — Interest Run & Investments", () => {
  // ── 60.1-60.10: Interest run with LPFF split ─────────────────────
  test("Alice: Navigate to Trust Interest page", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);
    await expect(page.getByTestId("interest-page")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: "Interest" })).toBeVisible();
    await expect(page.getByRole("button", { name: "New Interest Run" })).toBeVisible();
    // Day 0's LPFF rate is in force
    await expect(page.getByTestId("lpff-rates-table")).toContainText("6.50%");
  });

  test("Alice: Interest run — create for the last 30 days and calculate allocations at the 6.5% LPFF rate", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);
    await page.getByRole("button", { name: "New Interest Run" }).click();
    const wizard = page.getByTestId("interest-run-wizard");
    await expect(wizard).toContainText("Step 1 of 4");
    const periodStart = isoDate(-30);
    const periodEnd = isoDate();
    await wizard.getByLabel("Period Start").fill(periodStart);
    await wizard.getByLabel("Period End").fill(periodEnd);
    await wizard.getByRole("button", { name: "Create Run" }).click();

    await expect(wizard).toContainText("Step 2 of 4", { timeout: 15_000 });
    await expect(wizard).toContainText(`Period: ${periodStart} to ${periodEnd}`);
    await wizard.getByRole("button", { name: "Calculate Interest" }).click();

    // One allocation per client with a trust balance in the period (31 days, both funded today):
    // Moroka R241,500 and QuickCollect R45,000 for one day at 6.5% p.a. (actual/365), split with
    // the LPFF under the firm's arrangement
    const allocations = wizard.getByTestId("allocations-table");
    await expect(allocations).toBeVisible({ timeout: 15_000 });
    for (const header of ["Avg Daily Balance", "Gross Interest", "LPFF Share", "Client Share"]) {
      await expect(allocations.getByRole("columnheader", { name: header })).toBeVisible();
    }
    await expect(
      allocations.getByRole("columnheader", { name: "Client", exact: true })
    ).toBeVisible();
    for (const [key, name, avgDaily, gross] of [
      ["moroka", morokaClient(), "7 790,32", "43,01"],
      ["qc", qcClient(), "1 451,61", "8,01"],
    ] as const) {
      // G89-F10: the row names the client (never its UUID) and the LPFF arrangement share (50%)
      const row = allocations.getByRole("row").filter({ hasText: name });
      await expect(row, `${key} allocation`).toHaveCount(1);
      await expect(row).not.toContainText(clientId(key));
      await expect(row).toContainText(randPattern(avgDaily));
      await expect(row.getByRole("cell").nth(2)).toContainText(randPattern(gross));
      await expect(row).toContainText("LPFF arrangement rate 50.00%");
    }

    // The run's creator reviews (never approves) her own run — 630B hides self-approval via
    // MyCapabilitiesResponse.memberId
    await expect(wizard.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
    await wizard.getByRole("button", { name: "Review", exact: true }).click();
    await expect(wizard).toContainText("Step 3 of 4");
    await expect(wizard.getByRole("heading", { name: "Interest Run Totals" })).toBeVisible();
    for (const total of ["Total Interest", "LPFF Share", "Client Share"]) {
      await expect(wizard.getByText(total, { exact: true })).toBeVisible();
    }

    // Screenshot: interest run with LPFF split
    await captureScreenshot(page, "day-60-interest-lpff-calculated");
    await captureScreenshot(page, "interest-run-lpff-split", { curated: true });

    // The run's creator cannot approve it: Approve Run is disabled client-side and the backend
    // still refuses self-approval (InterestService.approveInterestRun)
    await expect(wizard.getByRole("button", { name: "Approve Run" })).toBeDisabled();
    await expect(wizard.getByTestId("interest-run-awaiting-approval")).toHaveText(
      "Awaiting approval by another member"
    );
    await page.keyboard.press("Escape");
    await expect(wizard).toBeHidden();

    // The calculated run persists as a Draft awaiting a second member (G88-F39)
    await page.reload();
    const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
    await expect(run).toHaveCount(1, { timeout: 15_000 });
    await expect(run.getByTestId("run-status")).toHaveText("Draft");
    await expect(run.getByRole("button", { name: "Review", exact: true })).toBeVisible();
    await expect(run.getByRole("button", { name: "Review & approve" })).toHaveCount(0);
    await expect(run).toContainText("Awaiting approval by another member");
    await expect(run.getByRole("button", { name: "Delete draft" })).toBeVisible();
  });

  test("Bob: Review & approve Alice's interest run as the second member", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/trust-accounting/interest`);
    const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
    await expect(run).toHaveCount(1, { timeout: 15_000 });
    await expect(run.getByTestId("run-status")).toHaveText("Draft");
    await run.getByRole("button", { name: "Review & approve" }).click();
    const wizard = page.getByTestId("interest-run-wizard");
    await expect(wizard).toContainText("Step 3 of 4", { timeout: 15_000 });
    await wizard.getByRole("button", { name: "Approve Run" }).click();
    await expect(wizard).toContainText("Step 4 of 4", { timeout: 15_000 });
    await expect(wizard.getByRole("heading", { name: "Approved Interest Run" })).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(wizard).toBeHidden();

    // The table behind the wizard refreshed: Approved, no Delete draft, Post offered
    await expect(run.getByTestId("run-status")).toHaveText("Approved", { timeout: 15_000 });
    await expect(run.getByRole("button", { name: "Delete draft" })).toHaveCount(0);
    await expect(run.getByRole("button", { name: "Post", exact: true })).toBeVisible();
  });

  test("Alice: Post the approved interest run to the client ledgers", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);
    const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
    await expect(run).toHaveCount(1, { timeout: 15_000 });
    await expect(run.getByTestId("run-status")).toHaveText("Approved");
    await run.getByRole("button", { name: "Post", exact: true }).click();
    const wizard = page.getByTestId("interest-run-wizard");
    await expect(wizard).toContainText("Step 4 of 4", { timeout: 15_000 });
    await wizard.getByRole("button", { name: "Post to Ledger" }).click();
    await expect(wizard).toBeHidden({ timeout: 15_000 });

    // The run is Posted with a posted date and no further actions …
    await expect(run.getByTestId("run-status")).toHaveText("Posted", { timeout: 15_000 });
    await expect(run.getByTestId("run-posted-at")).not.toHaveText("—");
    await expect(run.getByRole("button")).toHaveCount(0);
    // … and each allocated client (Moroka, QuickCollect) has an Interest Credit on the trust ledger
    await openTrustTransactions(page);
    await expect(
      page.locator("main tbody tr").filter({ hasText: "Interest Credit" }),
      "one Interest Credit per allocated client"
    ).toHaveCount(2, { timeout: 15_000 });
  });

  // ── 60.11-60.17: Investment — Moroka R200,000 Fixed Deposit ───────
  test("Alice: Navigate to Investment Register", async ({ page }) => {
    await loginAs(page, "alice");
    await openInvestments(page);
    await expect(page.getByRole("button", { name: "Place Investment" })).toBeVisible();
    await expect(page.getByTestId("empty-investments")).toBeVisible();
  });

  test("Alice: Place investment — Moroka R200,000 Standard Bank Fixed Deposit (s86(3))", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await placeInvestment(page, {
      clientId: clientId("moroka"),
      matterId: matterId("moroka"),
      institution: "Standard Bank",
      accountNumber: "FD-0987654321",
      principal: "200000",
      ratePercent: "8.5",
      maturityDate: isoDate(90),
      basis: "Firm (surplus trust funds)",
      notes: "90-day fixed deposit — surplus estate funds",
    });
    const row = page.locator("main tbody tr").filter({ hasText: morokaClient() });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Firm");
    await expect(row).toContainText("Standard Bank");
    await expect(row).toContainText(randPattern("200 000,00"));
    await expect(row).toContainText("8.50%");
    await expect(row).toContainText("Arrangement");
    await expect(row).toContainText("ACTIVE");

    // Screenshot: investment placed
    await captureScreenshot(page, "day-60-investment-s86-placed");
    await captureScreenshot(page, "investment-s86-selection", { curated: true });
  });

  // ── 60.18-60.20: Investment — QuickCollect R30,000 Call Account ────
  test("Alice: Place investment — QuickCollect R30,000 Nedbank Call Account (s86(4))", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await placeInvestment(page, {
      clientId: clientId("qc"),
      matterId: matterId("qc"),
      institution: "Nedbank",
      accountNumber: "CALL-1122334455",
      principal: "30000",
      ratePercent: "7.25",
      basis: "Client instruction",
      notes: "Call account on QuickCollect's written instruction",
    });
    const row = page.locator("main tbody tr").filter({ hasText: qcClient() });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Client Instruction");
    await expect(row).toContainText("Nedbank");
    await expect(row).toContainText(randPattern("30 000,00"));
    await expect(row).toContainText("5% (statutory)");
    await expect(row).toContainText("Call deposit");
  });

  // ── 60.21-60.24: Second billing cycle fee notes ───────────────────
  test("Alice: Second billing cycle — log new Sipho time and generate its fee note", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());
    await logTimeOnTask(page, {
      task: "Issue summons / combined summons",
      hours: 1,
      minutes: 0,
      date: isoDate(),
      description: SIPHO_SUMMONS_TIME,
      rate: "2 500,00",
    });

    // Only the new entry is unbilled: Day 7–14 time is on the first (sent) fee note
    await openClientFeeNotes(page, siphoClient());
    const feeNoteId = await generateFeeNoteDraft(page, { matter: siphoMatter(), items: 1 });
    recordValue("siphoFeeNote2", feeNoteId);
    await expect(
      page.locator("main").getByRole("row").filter({ hasText: SIPHO_SUMMONS_TIME })
    ).toHaveCount(1);
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("2 500,00"));
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("2 875,00"));
  });

  // ── 60.25-60.29: Reports ──────────────────────────────────────────
  test("Alice: Run the timesheet report for this month", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/reports`);
    await clickThrough(
      page.getByRole("link", { name: /Timesheet Report/ }),
      page.getByRole("heading", { level: 1, name: "Timesheet Report" })
    );
    await expect(page).toHaveURL(/\/reports\/timesheet/);
    await page.getByLabel(/^From/).fill(isoDate(-30));
    await page.getByLabel(/^To/).fill(isoDate());
    await page.getByRole("button", { name: "Run Report" }).click();
    await expect(page.getByRole("button", { name: "Export CSV" })).toBeEnabled({
      timeout: 15_000,
    });
    // The lifecycle's fee earners appear in the results
    const results = page.locator("main table");
    for (const member of ["Alice Owner", "Bob Admin", "Carol Member"]) {
      await expect(results.getByText(member).first()).toBeVisible({ timeout: 15_000 });
    }
  });

  test("Alice: Check profitability report", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S6a (ADR-344): route — /profitability 308s to /reports/profitability (612A)
    await page.goto(`${BASE}/profitability`);
    await expect(page).toHaveURL(/\/reports\/profitability/);
    await expect(page.getByRole("heading", { level: 1, name: "Profitability" })).toBeVisible({
      timeout: 15_000,
    });
    // (a second, hidden copy of the page renders outside #main-content)
    await expect(
      page.locator("#main-content").getByTestId("profitability-cost-note")
    ).toBeVisible();
    // This month's rated time puts Sipho's matter on the matter table
    await expect(page.getByRole("row").filter({ hasText: siphoMatter() }).first()).toBeVisible();

    // Screenshot: profitability report
    await captureScreenshot(page, "day-60-profitability-loaded");
    await captureScreenshot(page, "profitability-per-matter", { curated: true });
  });
});
