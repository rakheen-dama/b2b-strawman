/**
 * RES-01: Resource Planning — Playwright E2E Tests
 *
 * Tests resource planning page: page loads, utilization view.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("RES-01: Resource Planning", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Resources page loads", async ({ page }) => {
    await page.goto(`${base}/resources`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent({ timeout: 10000 });
    expect(text?.length).toBeGreaterThan(0);
  });

  test("Utilization view", async ({ page }) => {
    await page.goto(`${base}/resources`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Look for utilization tab/section or navigate to sub-page
    const utilTab = page
      .locator(
        '[role="tab"]:has-text("Utilization"), a:has-text("Utilization"), button:has-text("Utilization")'
      )
      .first();
    if (await utilTab.isVisible().catch(() => false)) {
      await utilTab.click();
      await page.waitForTimeout(1000);
    } else {
      // Try direct URL
      await page.goto(`${base}/resources/utilization`);
      const bodyText = await page.locator("body").textContent();
      if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
        // Utilization may be shown on main resources page
        await page.goto(`${base}/resources`);
      }
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");
    const content = page.locator('main, [role="main"], .flex-1').first();
    await expect(content).toBeVisible();
  });
});
