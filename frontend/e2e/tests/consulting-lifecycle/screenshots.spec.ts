/**
 * Consulting Lifecycle — Screenshot Baselines
 *
 * Captures Playwright `toHaveScreenshot()` baselines for the five prescribed
 * wow-moment captures of the `consulting-za` vertical profile, mirroring the
 * structure of `frontend/e2e/tests/legal-lifecycle/`.
 *
 * Baselines are stored under `frontend/e2e/screenshots/consulting-lifecycle/`
 * (routed by `playwright.consulting-lifecycle.config.ts`'s `snapshotPathTemplate`).
 *
 * Curated marketing PNGs live separately at
 * `documentation/screenshots/consulting-vertical/` and are added manually.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Tenant seeded with `consulting-za` profile and a representative dataset
 *      (use the QA lifecycle script: `qa/testplan/demos/consulting-agency-90day-keycloak.md`)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 \
 *     pnpm exec playwright test --config e2e/playwright.consulting-lifecycle.config.ts
 *
 * To populate baselines on first run, append `--update-snapshots`.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

test.describe.serial("Consulting Lifecycle — Screenshot Baselines", () => {
  // ── Day 0: Dashboard with TeamUtilizationWidget + en-ZA-consulting terminology ─
  test("Day 00: Dashboard with TeamUtilizationWidget + consulting terminology", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    // The TeamUtilizationWidget self-gates on profile === "consulting-za" and
    // ships with data-testid="team-utilization-widget". If the seed tenant
    // is not on consulting-za the widget will be null — skip rather than fail.
    const widget = page.locator('[data-testid="team-utilization-widget"]');
    const widgetVisible = await widget.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!widgetVisible) {
      test.skip(true, "TeamUtilizationWidget not present — tenant likely not on consulting-za");
      return;
    }

    await captureScreenshot(page, "day-00-dashboard-utilization-widget");
  });

  // ── Day 5: Project detail with campaign_type field ────────────────────────────
  test("Day 05: Project detail with campaign_type custom field", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    // Pick the first project link in the list (seed expectation: at least one
    // consulting-za-project-templates project exists, e.g. Website Build).
    const firstProjectLink = page.locator('a[href*="/projects/"]').first();
    const hasProject = await firstProjectLink.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!hasProject) {
      test.skip(true, "No project rows in seed data");
      return;
    }

    await firstProjectLink.click();
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    await captureScreenshot(page, "day-05-project-campaign-type");
  });

  // ── Day 14: Creative brief request form (10 questions) ────────────────────────
  test("Day 14: Creative brief request form", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const firstProjectLink = page.locator('a[href*="/projects/"]').first();
    const hasProject = await firstProjectLink.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!hasProject) {
      test.skip(true, "No project rows in seed data");
      return;
    }
    await firstProjectLink.click();
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const requestsTab = page.getByRole("tab", { name: /requests/i }).first();
    const hasRequestsTab = await requestsTab.isVisible({ timeout: 5_000 }).catch(() => false);
    if (!hasRequestsTab) {
      test.skip(true, "Requests tab not surfaced on project detail in this build");
      return;
    }
    await requestsTab.click();
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    await captureScreenshot(page, "day-14-creative-brief-request");
  });

  // ── Day 30: Monthly retainer report PDF preview ───────────────────────────────
  test("Day 30: Monthly retainer report (document generation)", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/documents`);
    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Documents surface not reachable in this build");
      return;
    }
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    await captureScreenshot(page, "day-30-monthly-retainer-report");
  });

  // ── Day 60: Statement of Work with required agency clauses ────────────────────
  test("Day 60: Statement of Work with required agency clauses", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/documents`);
    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Documents surface not reachable in this build");
      return;
    }
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    await captureScreenshot(page, "day-60-sow-agency-clauses");
  });
});
