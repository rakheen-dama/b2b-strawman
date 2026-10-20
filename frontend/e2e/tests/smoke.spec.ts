import { test, expect } from "@playwright/test";
import { loginAs } from "../fixtures/auth";

test("mock login redirects to dashboard", async ({ page }) => {
  await page.goto("/mock-login");
  // Alice Owner is the default selection (first option)
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
});

test("owner can create a project", async ({ page }) => {
  const name = `E2E Test ${Date.now()}`;
  await loginAs(page, "alice");
  // Phase 87 S4 (ADR-344): route rename
  await page.goto("/org/e2e-test-org/matters");
  await page.getByRole("button", { name: /New (Project|Engagement)/i }).click();
  await page.getByLabel("Name").fill(name);
  await page.getByRole("button", { name: /Create (Project|Engagement)/i }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible({ timeout: 10000 });
  // Phase 87 S4 (ADR-344): route rename
  await page.goto("/org/e2e-test-org/matters");
  await expect(page.getByText(name).first()).toBeVisible({ timeout: 10000 });
});

test("member cannot access admin settings", async ({ page }) => {
  await loginAs(page, "carol");
  await page.goto("/org/e2e-test-org/settings/rates");
  await expect(page.getByText(/permission/i)).toBeVisible();
});
