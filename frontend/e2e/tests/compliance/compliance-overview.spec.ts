/**
 * COMPLY-01: Compliance Overview — Playwright E2E Tests
 *
 * Tests the compliance dashboard (Overview sections) and the Data Requests page.
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
    await expect(page.getByRole("heading", { level: 1, name: "Compliance" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("tab", { name: "Overview" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    await expect(page.getByRole("tab", { name: "AI Audit" })).toBeVisible();
    // The Overview tab's four sections (app/(app)/org/[slug]/compliance/page.tsx)
    const overview = page.getByRole("tabpanel");
    for (const section of [
      "Lifecycle Distribution",
      "Onboarding Pipeline",
      "Data Requests",
      "Dormancy Check",
    ]) {
      await expect(overview.getByRole("heading", { level: 2, name: section })).toBeVisible();
    }
  });

  test("Compliance requests list", async ({ page }) => {
    // The Overview's "View All Requests" link renders only while urgent requests exist
    // (components/compliance/DataRequestsSection.tsx) — the list page itself is always reachable
    await page.goto(`${base}/compliance/requests`);
    await expect(page.getByRole("heading", { level: 1, name: "Data Requests" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("button", { name: "New Request" })).toBeVisible();
    for (const filter of ["Received", "In Progress", "Completed", "Rejected"]) {
      await expect(page.locator("#main-content").getByRole("link", { name: filter })).toBeVisible();
    }
  });
});
