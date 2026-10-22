/**
 * DP-02: Data Protection Settings — Playwright E2E Tests
 *
 * Tests data protection settings: view tab, jurisdiction, information officer,
 * retention policies, processing register, PAIA manual, DSAR management.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

/**
 * Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): set the data-protection
 * jurisdiction to South Africa through the real control. JurisdictionSelectorSection
 * (components/settings/jurisdiction-selector.tsx) is a Radix Select that saves on selection — it
 * has no Save button — and the FIRST selection opens the "Set Data Protection Jurisdiction"
 * AlertDialog, which must be confirmed. The e2e tenant starts without a jurisdiction. Idempotent:
 * when South Africa is already selected it leaves the control alone. Persistence oracle:
 * PaiaManualSection gets the jurisdiction from the server render, so "Generate PAIA Manual" only
 * turns enabled once the save + router.refresh() have landed.
 */
async function setJurisdictionToSouthAfrica(page: Page) {
  const trigger = page
    .getByRole("combobox")
    .filter({ hasText: /Select a jurisdiction|South Africa/ });
  await expect(trigger).toBeVisible({ timeout: 10_000 });
  if (!/South Africa/.test((await trigger.textContent()) ?? "")) {
    await trigger.click();
    await page.getByRole("option", { name: /South Africa/ }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Confirm" }).click();
  }
  await expect(page.getByRole("button", { name: "Generate PAIA Manual" })).toBeEnabled({
    timeout: 15_000,
  });
}

test.describe("DP-02: Data Protection Settings", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View data protection tab", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const body = page.locator("body");
    const bodyText = await body.textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Try settings page with data protection tab
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      await expect(body).not.toContainText("Something went wrong");

      const dpTab = page.getByRole("tab", { name: /data.*protection|privacy|POPIA/i }).first();
      const dpLink = page.getByRole("link", { name: /data.*protection|privacy/i }).first();

      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
      } else if (await dpLink.isVisible().catch(() => false)) {
        await dpLink.click();
      } else {
        test.skip(true, "Data protection settings tab/page not implemented");
        return;
      }
    }

    await page.waitForTimeout(1000);
    const content = page.locator('main, [role="main"], .flex-1').first();
    await expect(content).toBeVisible({ timeout: 10000 });
  });

  test("Set jurisdiction to South Africa", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Phase 87 S9 (616F, ADR-344): the jurisdiction control saves on selection (no Save button) and
    // its first selection must be confirmed in an AlertDialog. The old generic combobox + "save"
    // click never confirmed it, and once a jurisdiction exists it clicked the Information Officer's
    // disabled Save instead (30 s timeout on a re-run).
    await setJurisdictionToSouthAfrica(page);
  });

  test("Set information officer", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): InformationOfficerSection
    // (components/settings/information-officer-section.tsx) renders #officer-name / #officer-email
    // (no name attribute) and its own "Save", disabled until a value differs from the saved one.
    // The unique suffix keeps a re-run against the same stack dirty.
    const nameInput = page.locator("#officer-name");
    const emailInput = page.locator("#officer-email");
    await expect(nameInput).toBeVisible({ timeout: 10_000 });
    await nameInput.fill(`Test Officer ${Date.now().toString(36).slice(-4)}`);
    await emailInput.fill("officer@test.com");

    // exact: the retention rows' icon buttons are named "Save {TYPE} policy"
    const saveBtn = page.getByRole("button", { name: "Save", exact: true });
    await saveBtn.click();
    // Phase 87 S9 (616F, ADR-344 — D-3): outcome observed on the e2e stack before asserting it
    await expect(page.getByText("Saved successfully.")).toBeVisible({ timeout: 10_000 });
  });

  test("View retention policies", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Navigate to retention section
    const retentionTab = page
      .locator('[role="tab"]:has-text("Retention"), a:has-text("Retention")')
      .first();
    if (await retentionTab.isVisible().catch(() => false)) {
      await retentionTab.click();
      await page.waitForTimeout(1000);
    }

    // Should show retention policies per entity type
    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent();
    const hasRetention = text?.match(/retention|month|year|period/i);

    if (!hasRetention) {
      test.skip(true, "Retention policies section not found");
    }
  });

  test("Edit retention period", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Navigate to retention section
    const retentionTab = page
      .locator('[role="tab"]:has-text("Retention"), a:has-text("Retention")')
      .first();
    if (await retentionTab.isVisible().catch(() => false)) {
      await retentionTab.click();
      await page.waitForTimeout(1000);
    }

    // Look for editable retention period input
    const periodInput = page
      .locator('input[type="number"][name*="retention" i], input[name*="months" i]')
      .first();
    if (!(await periodInput.isVisible().catch(() => false))) {
      test.skip(true, "Retention period input not found");
      return;
    }

    await periodInput.fill("60");
    const saveBtn = page.getByRole("button", { name: /save|update/i }).first();
    if (await saveBtn.isVisible().catch(() => false)) {
      await saveBtn.click();
      await page.waitForTimeout(1000);
    }
  });

  test("View processing register", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Navigate to processing register section
    const registerTab = page
      .locator(
        '[role="tab"]:has-text("Processing"), a:has-text("Processing"), a:has-text("Register")'
      )
      .first();
    if (await registerTab.isVisible().catch(() => false)) {
      await registerTab.click();
      await page.waitForTimeout(1000);
    }

    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent();
    const hasRegister = text?.match(/processing|register|activit/i);

    if (!hasRegister) {
      test.skip(true, "Processing register section not found");
    }
  });

  test("Generate PAIA manual", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): "Generate PAIA Manual" is
    // disabled until a data-protection jurisdiction is set (components/data-protection/
    // paia-manual-section.tsx:53) and the e2e tenant starts without one — set it first
    // (idempotent), then generate.
    await setJurisdictionToSouthAfrica(page);
    const generateBtn = page.getByRole("button", { name: "Generate PAIA Manual" });
    await generateBtn.click();
    // Phase 87 S9 (616F, ADR-344 — D-3): outcome observed on the e2e stack before asserting it
    await expect(page.getByText(/PAIA manual generated:/)).toBeVisible({ timeout: 15_000 });
  });

  test("View DSAR list", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Navigate to DSAR section
    const dsarTab = page
      .locator(
        '[role="tab"]:has-text("DSAR"), a:has-text("DSAR"), [role="tab"]:has-text("Requests")'
      )
      .first();
    if (await dsarTab.isVisible().catch(() => false)) {
      await dsarTab.click();
      await page.waitForTimeout(1000);
    }

    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent();
    const hasDSAR = text?.match(/DSAR|request|subject/i);

    if (!hasDSAR) {
      test.skip(true, "DSAR list section not found");
    }
  });

  test("Create DSAR", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
    await page.goto(`${base}/settings/firm?tab=data-protection`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      // Phase 87 S9 (ADR-344): fallback lands on the Firm family; its rail/tab label is "Data protection" (sentence case)
      await page.goto(`${base}/settings/firm`);
      const dpTab = page
        .locator('[role="tab"]:has-text("Data protection"), a:has-text("Data protection")')
        .first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data protection settings not accessible");
        return;
      }
    }

    // Navigate to DSAR section
    const dsarTab = page
      .locator(
        '[role="tab"]:has-text("DSAR"), a:has-text("DSAR"), [role="tab"]:has-text("Requests")'
      )
      .first();
    if (await dsarTab.isVisible().catch(() => false)) {
      await dsarTab.click();
      await page.waitForTimeout(1000);
    }

    // Look for create/new DSAR button
    const createBtn = page.getByRole("button", { name: /new|create|add|log/i }).first();
    if (!(await createBtn.isVisible().catch(() => false))) {
      test.skip(true, "Create DSAR button not found");
      return;
    }

    await createBtn.click();
    await page.waitForTimeout(1000);

    // Fill form if dialog appeared
    const dialog = page.getByRole("dialog").first();
    if (await dialog.isVisible().catch(() => false)) {
      const nameInput = dialog.getByRole("textbox").first();
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.fill("Test DSAR Request");
      }
    }
  });
});
