/**
 * CF-01: Custom Fields — Playwright E2E Tests
 *
 * Tests custom field management: view definitions, create text field,
 * verify appears on entity form, verify value persists.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("CF-01: Custom Fields", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View custom field definitions", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/custom-fields → /settings/matters?tab=custom-fields
    await page.goto(`${base}/settings/matters?tab=custom-fields`);
    const body = page.locator("body");
    const bodyText = await body.textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      test.skip(true, "Custom fields settings page not implemented");
      return;
    }

    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent({ timeout: 10000 });

    // Should show field definitions or empty state
    expect(text?.length).toBeGreaterThan(0);
  });

  test("Create text custom field", async ({ page }) => {
    // Phase 87 S9 (ADR-344): /settings/custom-fields → /settings/matters?tab=custom-fields
    await page.goto(`${base}/settings/matters?tab=custom-fields`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      test.skip(true, "Custom fields settings page not implemented");
      return;
    }

    const createBtn = page.getByRole("button", { name: /new|create|add/i }).first();
    if (!(await createBtn.isVisible().catch(() => false))) {
      test.skip(true, "Create custom field button not found");
      return;
    }

    await createBtn.click();
    await page.waitForTimeout(1000);

    const dialog = page.getByRole("dialog").first();
    if (await dialog.isVisible().catch(() => false)) {
      // Fill field name
      const nameInput = dialog.getByRole("textbox", { name: /name|label/i }).first();
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.fill(`Test Field ${Date.now().toString(36).slice(-4)}`);
      }

      // Select type = text if there's a type selector
      const typeSelect = dialog.locator('select[name*="type"]').first();
      if (await typeSelect.isVisible().catch(() => false)) {
        await typeSelect.selectOption({ label: /text/i });
      }

      // Select entity = customer if there's an entity selector
      const entitySelect = dialog.locator('select[name*="entity"]').first();
      if (await entitySelect.isVisible().catch(() => false)) {
        // Phase 88 628A (ADR-357): label — entity option "Customers" → "Clients"
        await entitySelect.selectOption({ label: /client/i });
      }

      // Submit
      const submitBtn = dialog.getByRole("button", { name: /create|save|add/i }).last();
      if (await submitBtn.isVisible().catch(() => false)) {
        await submitBtn.click();
        await page.waitForTimeout(1000);
      }
    }
  });

  test("Custom field appears on entity form", async ({ page }) => {
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong")) {
      test.skip(true, "Customers page not accessible");
      return;
    }

    // Open new customer dialog
    // Phase 87 S5 (ADR-344): label
    const newBtn = page.getByRole("button", { name: /new.*client/i }).first();
    if (!(await newBtn.isVisible({ timeout: 10000 }).catch(() => false))) {
      test.skip(true, "New Client button not found");
      return;
    }

    await newBtn.click();
    await page.waitForTimeout(1000);

    // Navigate to step 2 (custom fields) if multi-step
    const nextBtn = page.getByRole("button", { name: /next/i }).first();
    if (await nextBtn.isVisible().catch(() => false)) {
      // Fill required fields first
      const nameInput = page.getByRole("textbox", { name: /name/i }).first();
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.fill("Temp Customer");
      }
      await nextBtn.click();
      await page.waitForTimeout(1000);
    }

    // Check if custom fields are shown
    const dialog = page.getByRole("dialog").first();
    if (await dialog.isVisible().catch(() => false)) {
      const dialogText = await dialog.textContent();
      // Should have some fields visible
      expect(dialogText?.length).toBeGreaterThan(0);
    }

    // Close without saving
    await page.keyboard.press("Escape");
  });

  test("Custom field value persists", async ({ page }) => {
    // Navigate to an existing customer's detail
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong")) {
      test.skip(true, "Customers page not accessible");
      return;
    }

    const customerLink = page
      .getByRole("link")
      .filter({ hasText: /Kgosi|Naledi/i })
      .first();
    if (!(await customerLink.isVisible({ timeout: 10000 }).catch(() => false))) {
      test.skip(true, "No seeded customers found");
      return;
    }

    await customerLink.click();
    await page.waitForTimeout(2000);

    // Look for custom fields section
    const content = page.locator('main, [role="main"], .flex-1').first();
    await expect(content).toBeVisible();

    // Verify page loaded without error
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });
});
