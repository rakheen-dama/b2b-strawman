/**
 * BR-01: Billing Runs — Playwright E2E Tests
 *
 * Tests the fee runs page (Fee notes › Fee runs) and opening the new fee run wizard.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (bulk_billing is on for legal-za)
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("BR-01: Billing Runs", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${base}/invoices/billing-runs`);
    await expect(page.getByRole("heading", { level: 1, name: "Fee runs" })).toBeVisible({
      timeout: 15_000,
    });
  });

  test("Billing runs page loads", async ({ page }) => {
    const main = page.locator("#main-content");
    // Fee notes / Fee runs sub-navigation
    await expect(main.getByRole("link", { name: "Fee notes", exact: true })).toBeVisible();
    await expect(main.getByRole("link", { name: "Fee runs", exact: true })).toBeVisible();
    // The header action (the empty state, when no run exists yet, repeats it)
    await expect(main.getByRole("link", { name: "New Billing Run" }).first()).toBeVisible();
  });

  test("New billing run wizard", async ({ page }) => {
    // "New Billing Run" is a link to the wizard page, not a dialog
    await page
      .locator("#main-content")
      .getByRole("link", { name: "New Billing Run" })
      .first()
      .click();
    await expect(page).toHaveURL(/\/invoices\/billing-runs\/new/);
    await expect(page.getByRole("heading", { level: 1, name: "New fee run" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.getByRole("heading", { level: 2, name: "Configure Billing Run" })
    ).toBeVisible();
    await expect(page.getByLabel("Period From")).toBeVisible();
  });
});
