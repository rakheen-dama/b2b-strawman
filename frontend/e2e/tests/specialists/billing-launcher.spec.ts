/**
 * Billing Specialist — Launcher smoke tests (scaffold).
 *
 * Prerequisites: E2E stack running with mock auth (`compose/scripts/e2e-up.sh`).
 * These tests verify the launcher buttons appear on the correct surfaces.
 *
 * TODO: Flesh out once E2E stack supports the full specialist flow.
 */

import { test, expect } from "@playwright/test";

test.describe("Billing Specialist Launcher", () => {
  test.skip(true, "Scaffold — requires E2E stack with specialist backend");

  test("launcher visible on DRAFT invoice detail", async ({ page }) => {
    // Navigate to a DRAFT invoice
    await page.goto("/org/e2e-test-org/invoices/draft-invoice-id");
    await expect(page.getByRole("button", { name: /polish with ai/i })).toBeVisible();
  });

  test("launcher hidden on SENT invoice detail", async ({ page }) => {
    // Navigate to a SENT invoice
    await page.goto("/org/e2e-test-org/invoices/sent-invoice-id");
    await expect(page.getByRole("button", { name: /polish with ai/i })).not.toBeVisible();
  });

  test("launcher visible in invoice generation dialog step 2", async ({ page }) => {
    // Navigate to customers list, open invoice generation
    await page.goto("/org/e2e-test-org/customers");
    // TODO: Open generation dialog and advance to step 2
    await expect(page.getByRole("button", { name: /suggest line-item grouping/i })).toBeVisible();
  });
});
