/**
 * Audit Log Smoke (Epic 506B / 506.12)
 *
 * Login as alice → navigate to /org/{slug}/settings/audit-log → assert heading
 * → apply Sensitive preset → assert at least one event row (or empty-state if
 * the seed has no CRITICAL/WARNING events in the last 30 days).
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

test.describe("Audit Log — Smoke (Epic 506B)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("apply Sensitive preset shows events or empty-state", async ({ page }) => {
    await page.goto(`${base}/settings/audit-log`);
    await page.waitForLoadState("networkidle");
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Page heading visible
    await expect(page.locator("h1").filter({ hasText: /Audit log/i })).toBeVisible({
      timeout: 10_000,
    });

    // Apply Sensitive preset (Shadcn / Radix select — click trigger then option).
    const presetSelect = page.getByTestId("audit-preset-select");
    await expect(presetSelect).toBeVisible();
    await presetSelect.click();
    await page.getByRole("option", { name: /Sensitive/i }).click();

    // Wait for the URL to reflect the preset (severities param is the canonical signal).
    await page.waitForURL(/severities=/);
    await page.waitForLoadState("networkidle");

    // Either at least one event row OR an empty-state copy. A green run with
    // events requires the seed to include at least one CRITICAL or WARNING
    // event in the last 30 days. If the seed is empty, the test falls back
    // to asserting the empty-state copy.
    // Tighten the locator so it matches event TableRow only (not the toggle
    // button or the details TableRow, which both also live under
    // data-testid^="audit-row-").
    const rowCount = await page
      .locator(
        '[data-testid^="audit-row-"]:not([data-testid^="audit-row-toggle-"]):not([data-testid^="audit-row-details-"])'
      )
      .count();
    if (rowCount === 0) {
      await expect(page.locator("body")).toContainText(/No audit events/i);
      test.info().annotations.push({
        type: "seed-warning",
        description:
          "No CRITICAL/WARNING events in the last 30 days; smoke fell back to empty-state assertion.",
      });
    } else {
      expect(rowCount).toBeGreaterThan(0);
    }
  });
});
