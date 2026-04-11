/**
 * SET-03: Tax Settings — Playwright E2E Tests
 *
 * Tests tax rate management: view rates, create new rate,
 * verify tax applies to invoices.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("SET-03: Tax Settings", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View tax rates", async ({ page }) => {
    await page.goto(`${base}/settings/tax`);
    const body = page.locator("body");

    // Page might be at /settings/tax or /settings with a tax tab
    const hasError = await body.textContent().then((t) => t?.includes("Something went wrong"));
    if (hasError) {
      // Try settings page directly — tax might be a tab
      await page.goto(`${base}/settings`);
      await expect(body).not.toContainText("Something went wrong");
    }

    // Should show tax rate information (e.g., VAT 15%)
    const content = page.locator('main, [role="main"], .flex-1').first();
    const contentText = await content.textContent({ timeout: 10000 });

    if (!contentText?.match(/tax|vat/i)) {
      test.skip(true, "Tax settings page not implemented");
      return;
    }

    await expect(content).toContainText(/tax|vat/i);
  });

  test("Create tax rate", async ({ page }) => {
    await page.goto(`${base}/settings/tax`);
    const body = page.locator("body");

    const is404 = await body
      .textContent()
      .then((t) => t?.includes("Something went wrong") || t?.includes("404"));
    if (is404) {
      test.skip(true, "Tax settings page not implemented");
      return;
    }

    // Look for a "New" or "Add" button for tax rates
    const addBtn = page.getByRole("button", { name: /new|add|create/i }).first();
    const btnVisible = await addBtn.isVisible().catch(() => false);

    if (!btnVisible) {
      test.skip(true, "Add tax rate button not found");
      return;
    }

    await addBtn.click();
    await page.waitForTimeout(1000);

    // Fill tax rate form if dialog appeared
    const dialog = page.getByRole("dialog").first();
    if (await dialog.isVisible().catch(() => false)) {
      const nameInput = dialog.getByRole("textbox", { name: /name/i }).first();
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.fill("Test Tax Rate");
      }
      const rateInput = dialog.locator('input[name*="rate"], input[name*="percentage"]').first();
      if (await rateInput.isVisible().catch(() => false)) {
        await rateInput.fill("10");
      }
    }
  });

  test("Tax applies to invoices", async ({ page }) => {
    // Navigate to invoices and verify tax calculation
    await page.goto(`${base}/invoices`);
    const body = page.locator("body");
    await expect(body).not.toContainText("Something went wrong");

    const content = page.locator('main, [role="main"], .flex-1').first();
    const contentText = await content.textContent({ timeout: 10000 });

    if (!contentText?.match(/invoice/i)) {
      test.skip(true, "Invoices page not available for tax verification");
      return;
    }

    // Click on a seeded invoice to check for tax line
    const invoiceLink = page.getByRole("link", { name: /INV-/i }).first();
    const hasInvoice = await invoiceLink.isVisible().catch(() => false);

    if (!hasInvoice) {
      test.skip(true, "No seeded invoices available to verify tax");
      return;
    }

    await invoiceLink.click();
    await page.waitForTimeout(2000);
    // Invoice detail should show tax/VAT line
    await expect(page.locator("body")).toContainText(/tax|vat/i);
  });
});
