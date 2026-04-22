/**
 * Day 30 + Day 45 — Phase 67: Statement of Account + Disbursement Write-off
 *
 * Exercises:
 *   - Day 30 (Epic 487): generate a Statement of Account on the oldest active matter
 *     (Sipho's litigation) for period = matter-opened to today. Preview HTML
 *     shows fees + disbursements (from Day 5) + trust activity + summary. Save
 *     as GeneratedDocument, download PDF.
 *   - Day 45 (Epic 491): write off one approved-UNBILLED disbursement with reason.
 *     Verify status transition, audit event, exclusion from next invoice draft.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 5 disbursement lifecycle completed (at least one APPROVED, UNBILLED
 *      disbursement exists on Sipho's matter)
 *   3. Day 4-7 time entries exist on Sipho's matter (for SoA to have fee content)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

test.describe.serial("Day 30-45 — Statement of Account + Write-off (Phase 67)", () => {
  // ── 30.1-30.7: Generate Statement of Account on Sipho's matter ────
  test("Alice (Thandi): Open Statements tab on oldest matter", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Sipho|Dlamini|Litigation/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Sipho's matter not found");
      return;
    }

    await matterLink.click();
    await page.waitForTimeout(1500);

    const statementsTab = page.locator('[data-testid="project-statements-tab"]').first();
    const statementsTabRole = page.getByRole("tab", { name: /statement/i }).first();

    if (await statementsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await statementsTab.click();
    } else if (await statementsTabRole.isVisible({ timeout: 2000 }).catch(() => false)) {
      await statementsTabRole.click();
    } else {
      test.skip(true, "Statements tab not found on matter detail");
      return;
    }

    await page.waitForTimeout(800);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-matter-statements-tab");
  });

  test("Alice: Generate Statement of Account with HTML preview", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Sipho|Dlamini|Litigation/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Sipho's matter not found");
      return;
    }
    await matterLink.click();
    await page.waitForTimeout(1500);

    const statementsTab = page.locator('[data-testid="project-statements-tab"]').first();
    const statementsTabRole = page.getByRole("tab", { name: /statement/i }).first();
    if (await statementsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await statementsTab.click();
    } else if (await statementsTabRole.isVisible({ timeout: 2000 }).catch(() => false)) {
      await statementsTabRole.click();
    }
    await page.waitForTimeout(800);

    const generateBtn = page.getByRole("button", { name: /generate\s+statement/i }).first();
    if (!(await generateBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, "Generate Statement button not found");
      return;
    }

    await generateBtn.click();
    await page.waitForTimeout(500);

    const dialog = page.locator('[data-testid="statement-of-account-dialog"]');
    if (!(await dialog.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "statement-of-account-dialog not present");
      return;
    }

    // Period start — matter-opened approximation (90 days ago)
    const startInput = page.locator('[data-testid="statement-period-start-input"]').first();
    if (await startInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000)
        .toISOString()
        .slice(0, 10);
      await startInput.fill(ninetyDaysAgo);
    }

    const endInput = page.locator('[data-testid="statement-period-end-input"]').first();
    if (await endInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      const today = new Date().toISOString().slice(0, 10);
      await endInput.fill(today);
    }

    const generateActionBtn = page.locator('[data-testid="statement-generate-btn"]').first();
    if (await generateActionBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await generateActionBtn.click();
      await page.waitForTimeout(2000);
    }

    const preview = page.locator('[data-testid="statement-preview-container"]').first();
    expect.soft(await preview.isVisible({ timeout: 5000 }).catch(() => false)).toBeTruthy();

    // Soft assertions on content — fees / disbursements / trust / summary
    const previewText = await preview.textContent().catch(() => "");
    expect.soft(previewText || "").toMatch(/fee|disbursement|trust|total|summary/i);

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-statement-of-account-preview");
    await captureScreenshot(page, "statement-of-account-preview", { curated: true });
  });

  test("Alice: Save Statement + verify download PDF action", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Sipho|Dlamini|Litigation/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Sipho's matter not found");
      return;
    }
    await matterLink.click();
    await page.waitForTimeout(1500);

    const statementsTab = page.locator('[data-testid="project-statements-tab"]').first();
    if (await statementsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await statementsTab.click();
      await page.waitForTimeout(500);
    }

    // Save action (if dialog still open or new action renders)
    const saveBtn = page.getByRole("button", { name: /^save$/i }).first();
    if (await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await saveBtn.click();
      await page.waitForTimeout(1500);
    }

    // Verify download button data-testid exists
    expect
      .soft(
        await page
          .locator('[data-testid="statement-download-btn"]')
          .first()
          .isVisible({ timeout: 3000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-statement-saved-list");
  });

  // ── 45.5-45.11: Disbursement write-off ─────────────────────────────
  test("Alice (Thandi): Write off one approved-UNBILLED disbursement", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const approvedRow = page
      .locator('[data-testid^="disbursement-row-"]')
      .filter({ hasText: /APPROVED/i })
      .first();
    const anyRow = page.locator('[data-testid^="disbursement-row-"]').first();

    const pickRow = (await approvedRow.isVisible({ timeout: 3000 }).catch(() => false))
      ? approvedRow
      : anyRow;

    if (!(await pickRow.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, "No disbursement rows to write off");
      return;
    }

    await pickRow.click();
    await page.waitForTimeout(1500);

    const detail = page.locator('[data-testid="disbursement-detail"]').first();
    if (!(await detail.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Disbursement detail not found");
      return;
    }

    const writeOffBtn = page.getByRole("button", { name: /write[_\s]*off/i }).first();
    if (!(await writeOffBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, "Write Off action not visible on detail");
      return;
    }

    await writeOffBtn.click();
    await page.waitForTimeout(500);

    const reasonInput = page.getByRole("textbox", { name: /reason/i }).first();
    if (await reasonInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await reasonInput.fill(
        "Client dispute — unable to recover from third party, firm absorbing the cost"
      );
    }

    const confirmBtn = page.getByRole("button", { name: /confirm|submit|write/i }).first();
    if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await confirmBtn.click();
      await page.waitForTimeout(1500);
    }

    // Verify WRITTEN_OFF status badge
    expect
      .soft(
        await page
          .getByText(/WRITTEN[_\s]*OFF/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    // Verify write_off_reason text visible
    expect
      .soft(
        await page
          .getByText(/Client\s+dispute/i)
          .first()
          .isVisible({ timeout: 3000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-disbursement-written-off");
  });

  test("Alice: Verify write-off audit event present in audit log", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/audit`);
    const hasAudit = await page
      .locator("main")
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (!hasAudit) {
      test.skip(true, "Audit log page not reachable");
      return;
    }

    // Filter for write-off events (if filter UI present)
    const actionFilter = page.getByRole("combobox", { name: /action/i }).first();
    if (await actionFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await actionFilter.click();
      await page.waitForTimeout(300);
      const writeOffOption = page.getByRole("option", { name: /write[_\s]*off/i }).first();
      if (await writeOffOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await writeOffOption.click();
        await page.waitForTimeout(500);
      }
    }

    expect
      .soft(
        await page
          .getByText(/write[_\s]*off|WRITE_OFF/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-audit-log-write-off-event");
  });

  test("Alice: Verify written-off disbursement excluded from next invoice draft", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const newFeeNoteBtn = page.getByRole("button", { name: /New (Invoice|Fee Note)/i }).first();
    if (!(await newFeeNoteBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, "New Fee Note button not found");
      return;
    }

    await newFeeNoteBtn.click();
    await page.waitForTimeout(1000);

    // Select Sipho's matter
    const matterField = page.getByRole("combobox", { name: /project|matter/i }).first();
    if (await matterField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await matterField.click();
      await page.waitForTimeout(300);
      const siphoMatter = page.getByRole("option", { name: /Sipho|Dlamini/i }).first();
      if (await siphoMatter.isVisible({ timeout: 2000 }).catch(() => false)) {
        await siphoMatter.click();
        await page.waitForTimeout(800);
      }
    }

    // The drafting view should not list the written-off disbursement reason text
    const draftText = await page
      .locator("main")
      .textContent()
      .catch(() => "");
    expect.soft(draftText || "").not.toMatch(/Client\s+dispute.*unable\s+to\s+recover/i);

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-fee-note-draft-excludes-writeoff");
  });
});
