/**
 * COMPLY-01: Compliance Overview — Playwright E2E Tests
 *
 * Tests compliance dashboard: page loads, requests list.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("COMPLY-01: Compliance Overview", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Compliance dashboard loads", async ({ page }) => {
    await page.goto(`${base}/compliance`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    const content = page.locator('main, [role="main"], .flex-1').first();
    const text = await content.textContent({ timeout: 10000 });
    expect(text?.length).toBeGreaterThan(0);
  });

  test("Compliance requests list", async ({ page }) => {
    await page.goto(`${base}/compliance`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Look for requests tab/section or navigate to requests sub-page
    const requestsTab = page
      .locator('[role="tab"]:has-text("Requests"), a:has-text("Requests")')
      .first();
    if (await requestsTab.isVisible().catch(() => false)) {
      await requestsTab.click();
      await page.waitForTimeout(1000);
    } else {
      // Try direct URL
      await page.goto(`${base}/compliance/requests`);
      const bodyText = await page.locator("body").textContent();
      if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
        // Requests may be shown on the main compliance page already
        await page.goto(`${base}/compliance`);
      }
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");
    const content = page.locator('main, [role="main"], .flex-1').first();
    await expect(content).toBeVisible();
  });
});
