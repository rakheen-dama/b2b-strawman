import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";

test.describe("SET-02: Rate Cards", () => {
  test("Rates page loads without crash (AvatarCircle regression)", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`/org/${ORG}/settings/fees?tab=tariffs`);
    // This was BUG-REG-001: TypeError on null name.length in AvatarCircle — the page renders its
    // content (heading + the member rates) instead of the error boundary
    const main = page.locator("#main-content");
    await expect(main.getByRole("heading", { level: 1, name: /^Rates & Currency/ })).toBeVisible({
      timeout: 10_000,
    });
    await expect(main).not.toContainText("Something went wrong");
    await expect(main).toContainText("Alice Owner");
  });

  test("Rates page loads for Admin", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`/org/${ORG}/settings/fees?tab=tariffs`);
    const main = page.locator("#main-content");
    await expect(main.getByRole("heading", { level: 1, name: /^Rates & Currency/ })).toBeVisible({
      timeout: 10_000,
    });
    await expect(main).not.toContainText("You do not have permission");
  });

  test("Member sees permission denied on rates", async ({ page }) => {
    await loginAs(page, "carol");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`/org/${ORG}/settings/fees?tab=tariffs`);
    await expect(page.locator("#main-content")).toContainText(
      "You do not have permission to manage rates and currency settings.",
      { timeout: 10_000 }
    );
  });
});
