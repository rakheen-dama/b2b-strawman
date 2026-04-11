import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("AUTH-01: RBAC Capabilities", () => {
  test("Owner can access all settings pages", async ({ page }) => {
    await loginAs(page, "alice");
    const settingsPages = [`${base}/settings`, `${base}/settings/rates`, `${base}/settings/roles`];
    for (const path of settingsPages) {
      await page.goto(path);
      await expect(page.locator("body")).not.toContainText("Something went wrong");
      await expect(page).not.toHaveURL(/\/error/);
    }
  });

  test("Admin can access general settings", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${base}/settings`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Member sees permission denied on profitability", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/profitability`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Member sees permission denied on reports", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/reports`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);
  });

  test("Member sees permission denied on customers", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/customers`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);
  });

  test("Member sees permission denied on roles settings", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/settings/roles`);
    await expect(page.locator("body")).toContainText(/don.t have access|permission/i);
  });

  test("Member can access My Work", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/my-work`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Member can access Projects", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${base}/projects`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });
});
