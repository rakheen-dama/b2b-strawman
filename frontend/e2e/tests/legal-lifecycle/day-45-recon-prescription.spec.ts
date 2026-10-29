/**
 * Day 45 — Reconciliation & Prescription Tracking
 *
 * Bank reconciliation: start the wizard for the firm's trust account and reach the bank-statement
 * upload step (the CSV import + 3-way match is not driven — see the GAP note in the test). A
 * prescription tracker for Sipho's personal-injury claim (Delict, 3 years), the Sipho motion
 * postponed (the backend schedules the new date), and payment of Apex's R40,250 fee note.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–30 must have run on the
 * same stack (trust account, Sipho's matter + Day 7 motion, Apex's sent fee note).
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
  feeNoteTotalsRow,
  openFeeNote,
  randPattern,
  recordedValue,
  siphoMatter,
  TRUST_ACCOUNT,
} from "../../helpers/legal-lifecycle";

/** Opens Sipho's matter on its Diary tab (a single-tab group: no sub-tab menu). */
async function openSiphoDiary(page: Page): Promise<void> {
  await openMatter(page, siphoMatter());
  const diaryTab = page.getByTestId("tab-group-schedule");
  await diaryTab.click();
  await expect(diaryTab).toHaveAttribute("aria-selected", "true");
}

test.describe.serial("Day 45 — Reconciliation & Prescription", () => {
  // ── 45.1-45.8: Bank reconciliation ────────────────────────────────
  test("Alice: Navigate to Trust Reconciliation page", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/reconciliation`);
    await expect(page.getByRole("heading", { level: 1, name: "Reconciliation" })).toBeVisible({
      timeout: 15_000,
    });
    // The firm's trust account (Day 0) can be reconciled
    await expect(page.getByTestId("new-reconciliation-btn")).toBeVisible();
  });

  test("Alice: Start a bank reconciliation — select the trust account and period end, reach the statement upload step", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/reconciliation`);
    await clickThrough(
      page.getByTestId("new-reconciliation-btn"),
      page.getByRole("heading", { level: 1, name: "New Reconciliation" })
    );
    const steps = page.getByTestId("step-indicator");
    for (const step of ["Select Account", "Upload Statement", "Auto-Match", "Review & Match"]) {
      await expect(steps).toContainText(step);
    }

    // Step 1: account + period end
    const account = page.getByTestId("account-select");
    const option = account.locator("option", { hasText: TRUST_ACCOUNT.name });
    await expect(option).toBeAttached({ timeout: 15_000 });
    await expect(option).toContainText(`${TRUST_ACCOUNT.bank} - ${TRUST_ACCOUNT.accountNumber}`);
    await account.selectOption({ value: (await option.getAttribute("value"))! });
    await page.getByTestId("period-end-input").fill(isoDate());
    await expect(page.getByTestId("step-next-btn")).toBeEnabled();
    await page.getByTestId("step-next-btn").click();

    // Step 2: the bank-statement upload (a CSV picker behind a "Choose CSV File" button)
    const upload = page.getByTestId("bank-statement-upload");
    await expect(upload).toBeVisible({ timeout: 15_000 });
    await expect(upload.getByTestId("file-input")).toBeAttached();
    await expect(upload.getByTestId("file-input")).toHaveAttribute("accept", ".csv");
    await expect(upload.getByRole("button", { name: "Choose CSV File" })).toBeVisible();
    // No statement yet, so the wizard cannot advance
    await expect(page.getByTestId("step-next-btn")).toBeDisabled();

    // GAP: Bank CSV upload feature is not yet implemented. The test verifies that the
    // reconciliation page loads and the manual matching UI is accessible. Actual CSV import
    // and 3-way reconciliation (bank = cashbook = client ledger) will be tested once the
    // upload endpoint and parsing logic are built.
    // (Phase 88 628E: the wizard is driven to its upload step; no CSV is uploaded and auto-match
    // is not run — OD-7 item 6.)

    // Screenshot: the reconciliation wizard at the bank-statement upload step
    await captureScreenshot(page, "day-45-reconciliation-upload-step");
    await captureScreenshot(page, "bank-reconciliation-upload-step", { curated: true });
  });

  // ── 45.9-45.13: Prescription tracking for Sipho ───────────────────
  test("Alice: Create prescription tracker for Sipho (Delict, 3 years)", async ({ page }) => {
    await loginAs(page, "alice");
    await openSiphoDiary(page);
    const trackers = page.getByTestId("prescription-tracker-list");
    await expect(trackers).toBeVisible({ timeout: 15_000 });
    await expect(trackers.locator('[data-testid^="prescription-tracker-row-"]')).toHaveCount(0);

    await trackers.getByRole("button", { name: "New prescription tracker" }).click();
    const dialog = page.getByTestId("create-prescription-dialog");
    await expect(
      dialog.getByRole("heading", { name: "Create Prescription Tracker" })
    ).toBeVisible();
    // Opened from the matter, so the Matter is fixed to Sipho's
    await expect(dialog.getByLabel("Matter")).toBeDisabled();
    await expect(dialog.getByLabel("Matter").locator("option:checked")).toHaveText(siphoMatter());
    // Accident date: 60 days ago → prescribes three years later
    const causeOfAction = isoDate(-60);
    await dialog.getByLabel("Cause of Action Date").fill(causeOfAction);
    await dialog.getByLabel("Prescription Type").selectOption("DELICT_3Y");
    await dialog.getByRole("button", { name: "Create Tracker" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    const row = trackers.locator('[data-testid^="prescription-tracker-row-"]');
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Delict (3yr)");
    await expect(row).toContainText("Running");
    await expect(row).toContainText(causeOfAction);
    const [y, m, d] = causeOfAction.split("-");
    await expect(row).toContainText(`${Number(y) + 3}-${m}-${d}`);
    await expect(row.getByRole("button", { name: "Interrupt" })).toBeVisible();

    await captureScreenshot(page, "day-45-prescription-tracker-active");
    await captureScreenshot(page, "prescription-tracker-days", { curated: true });
  });

  // ── 45.14-45.17: Court date lifecycle ─────────────────────────────
  test("Alice: Court date lifecycle — Sipho's motion SCHEDULED → POSTPONED, the new date SCHEDULED", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openSiphoDiary(page);
    const list = page.getByTestId("court-date-list");
    const motions = list.getByRole("row").filter({ hasText: "Motion" });
    await expect(motions).toHaveCount(1, { timeout: 15_000 });
    await expect(motions).toContainText("Scheduled");

    await motions.getByRole("button", { name: "Actions" }).click();
    await page.getByRole("menuitem", { name: "Postpone" }).click();
    const dialog = page.getByTestId("postpone-dialog");
    await expect(dialog.getByRole("heading", { name: "Postpone Court Date" })).toBeVisible();
    await dialog.getByLabel("New Date").fill(isoDate(60));
    await dialog.getByLabel("Reason").fill("Opposing counsel unavailable — postponed by agreement");
    await dialog.getByRole("button", { name: "Postpone" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // The original date is Postponed; the backend schedules the new date as its own row
    await expect(motions).toHaveCount(2, { timeout: 15_000 });
    await expect(motions.filter({ hasText: "Postponed" })).toHaveCount(1);
    await expect(motions.filter({ hasText: "Scheduled" })).toHaveCount(1);

    await captureScreenshot(page, "day-45-court-date-postponed");
  });

  // ── 45.18-45.20: Payment on Apex fee note -> PAID ─────────────────
  test("Alice: Record payment on Apex fee note (R40,250) → PAID", async ({ page }) => {
    await loginAs(page, "alice");
    await openFeeNote(page, recordedValue("apexFeeNote"));
    const main = page.locator("main");
    await expect(main.getByText("Sent", { exact: true })).toBeVisible();
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("40 250,00"));

    await main.getByRole("button", { name: "Record Payment" }).click();
    const form = page.getByTestId("record-payment-form");
    await expect(form).toBeVisible();
    await expect(form.getByTestId("payment-form-balance-due")).toContainText(
      randPattern("40 250,00")
    );
    // Amount defaults to the balance due; method defaults to EFT
    await expect(form.getByLabel("Amount")).toHaveValue("40250");
    await form.getByLabel("Payment Reference (optional)").fill("EFT APEX-INV-PAYMENT");
    await form.getByRole("button", { name: "Confirm Payment" }).click();
    await expect(form).toBeHidden({ timeout: 15_000 });
    await expect(main.getByText("Paid", { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(main.getByRole("button", { name: "Record Payment" })).toHaveCount(0);
  });
});
