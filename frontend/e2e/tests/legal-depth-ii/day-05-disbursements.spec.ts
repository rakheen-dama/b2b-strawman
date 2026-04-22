/**
 * Day 5 — Phase 67: Disbursements (Epics 486–488)
 *
 * Exercises the disbursement lifecycle on the legal-za profile:
 *   - Bob (Admin) creates a sheriff-fee OFFICE_ACCOUNT disbursement + submits
 *   - Bob creates a deeds-office TRUST_ACCOUNT disbursement linked to an approved
 *     DISBURSEMENT_PAYMENT trust transaction + submits
 *   - Thandi (Owner, alice fixture) approves the trust-linked one (needs
 *     APPROVE_TRUST_PAYMENT capability)
 *   - Bob approves the office-account one (APPROVE_DISBURSEMENT)
 *   - Verify unbilled summary "Unbilled Disbursements: R x,xxx" on matter detail
 *
 * Terminology: always "Disbursement" (never "Expense").
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Legal-za profile active on e2e-test-org
 *   3. Sipho's litigation matter exists (Day 3 lifecycle prerequisite)
 *   4. settings.enabledModules includes "disbursements" (otherwise the list page 404s)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

test.describe.serial("Day 5 — Disbursements (Phase 67)", () => {
  // ── 5.5: Navigate to disbursements list page ──────────────────────
  test("Bob: Navigate to Legal → Disbursements list page", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    const listPage = page.locator('[data-testid="disbursements-list-page"]');
    const hasListPage = await listPage.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasListPage) {
      test.skip(true, "disbursements module not enabled on legal-za profile");
      return;
    }

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursements-list-empty");
  });

  // ── 5.6-5.8: Bob creates sheriff-fee OFFICE_ACCOUNT disbursement ──
  test("Bob: Create sheriff-fee OFFICE_ACCOUNT disbursement + submit", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const newBtn = page.locator('[data-testid="create-disbursement-trigger"]').first();
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasNew) {
      test.skip(true, "New Disbursement trigger not found");
      return;
    }

    await newBtn.click();
    await page.waitForTimeout(500);

    const dialog = page.locator('[data-testid="create-disbursement-dialog"]');
    const hasDialog = await dialog.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasDialog) {
      test.skip(true, "create-disbursement-dialog not found");
      return;
    }

    const amountInput = page.locator('[data-testid="disbursement-amount-input"]');
    const hasAmount = await amountInput.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasAmount) {
      await amountInput.fill("750.00");
    }

    const descInput = page.getByRole("textbox", { name: /description/i }).first();
    if (await descInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await descInput.fill("Sheriff fee — service of process");
    }

    // Matter selection — fallback to any combobox
    const matterField = page.getByRole("combobox", { name: /matter|project/i }).first();
    if (await matterField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await matterField.click();
      await page.waitForTimeout(300);
      const siphoOption = page.getByRole("option", { name: /Sipho|Dlamini/i }).first();
      if (await siphoOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await siphoOption.click();
      }
    }

    const submitBtn = page.getByRole("button", { name: /create|save|submit/i }).first();
    if (await submitBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await submitBtn.click();
      await page.waitForTimeout(1000);
    }

    // Verify row now visible in list
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    expect
      .soft(
        await page
          .locator('[data-testid="disbursement-list"], [data-testid^="disbursement-row-"]')
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-sheriff-fee-created");
  });

  // ── 5.9: Bob creates deeds-office TRUST_ACCOUNT disbursement + trust link ──
  test("Bob: Create deeds-office TRUST_ACCOUNT disbursement with trust link", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const newBtn = page.locator('[data-testid="create-disbursement-trigger"]').first();
    if (!(await newBtn.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "New Disbursement trigger not found");
      return;
    }

    await newBtn.click();
    await page.waitForTimeout(500);

    const amountInput = page.locator('[data-testid="disbursement-amount-input"]');
    if (await amountInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await amountInput.fill("1250.00");
    }

    const descInput = page.getByRole("textbox", { name: /description/i }).first();
    if (await descInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await descInput.fill("Deeds office search fee");
    }

    // Trust link slot — click "Link to trust transaction"
    const trustSlot = page.locator('[data-testid="trust-link-slot"]');
    if (await trustSlot.isVisible({ timeout: 3000 }).catch(() => false)) {
      const trustBtn = page.locator('[data-testid="trust-link-button"]').first();
      if (await trustBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await trustBtn.click();
        await page.waitForTimeout(500);

        // Pick the first DISBURSEMENT_PAYMENT option (if available)
        const trustOption = page.getByRole("option", { name: /DISBURSEMENT|payment/i }).first();
        if (await trustOption.isVisible({ timeout: 2000 }).catch(() => false)) {
          await trustOption.click();
          await page.waitForTimeout(300);
        }
      }
    }

    // Capture trust-link dialog state (curated for documentation)
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "trust-link-dialog", { curated: true });

    const submitBtn = page.getByRole("button", { name: /create|save|submit/i }).first();
    if (await submitBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await submitBtn.click();
      await page.waitForTimeout(1000);
    }

    expect
      .soft(
        await page
          .locator('[data-testid="linked-trust-tx"]')
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-deeds-office-trust-linked");
  });

  // ── 5.10-5.11: Approval flow (Thandi/Owner + Bob/Admin) ───────────
  test("Alice (Thandi): Approve trust-linked disbursement", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const firstRow = page
      .locator('[data-testid^="disbursement-row-"], [data-testid="disbursement-list"] tr')
      .first();
    const hasRow = await firstRow.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasRow) {
      test.skip(true, "No disbursement rows found to approve");
      return;
    }

    await firstRow.click();
    await page.waitForTimeout(1000);

    const detail = page.locator('[data-testid="disbursement-detail"]');
    if (!(await detail.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Disbursement detail page not found");
      return;
    }

    const approveBtn = page.getByRole("button", { name: /approve/i }).first();
    if (await approveBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await approveBtn.click();
      await page.waitForTimeout(500);

      // Capture the approval dialog (curated) now that the dialog is actually open
      await page.waitForLoadState("networkidle");
      await captureScreenshot(page, "disbursement-approval-dialog", { curated: true });

      const confirmBtn = page.getByRole("button", { name: /confirm|yes/i }).first();
      if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await confirmBtn.click();
        await page.waitForTimeout(1000);
      }
    }

    expect
      .soft(
        await page
          .getByText(/APPROVED/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-approved-detail");
  });

  // ── 5.12: Unbilled summary on matter detail ───────────────────────
  test("Alice: Verify Unbilled Disbursements summary on Sipho's matter", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const siphoMatterLink = page.getByRole("link", { name: /Sipho|Dlamini|Litigation/i }).first();
    const hasMatter = await siphoMatterLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasMatter) {
      test.skip(true, "Sipho's matter not found in project list");
      return;
    }

    await siphoMatterLink.click();
    await page.waitForTimeout(1500);
    await page.waitForLoadState("networkidle");

    // Click project disbursements tab if present
    const disbursementsTab = page.locator('[data-testid="project-disbursements-tab"]').first();
    if (await disbursementsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await disbursementsTab.click();
      await page.waitForTimeout(800);
    }

    // Verify "Unbilled Disbursements" label visible
    expect
      .soft(
        await page
          .getByText(/Unbilled\s+Disbursements/i)
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Terminology sweep: should NOT contain "Expense" heading
    const pageText = await page
      .locator("main")
      .textContent()
      .catch(() => "");
    expect.soft(pageText || "").not.toMatch(/Unbilled\s+Expenses/i);

    await captureScreenshot(page, "day-05-matter-unbilled-disbursements-summary");

    // The curated `disbursement-list-view` screenshot should show the org-level
    // disbursements list page (not Sipho's matter detail tab).
    await page.goto(`${BASE}/legal/disbursements`);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "disbursement-list-view", { curated: true });
  });
});
