/**
 * BUDGET-01: Project Budget — Playwright E2E Tests
 *
 * Tests budget tab visibility, setting a project budget via API,
 * and verifying budget usage display after logging time.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-budget --reporter=list
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { apiOk, apiStatus } from "../../helpers/api";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

/** Opens the matter's Fees → Fee estimate sub-tab and returns the (unnamed) tab panel. */
async function openFeeEstimateTab(page: Page, projectId: string) {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/matters/${projectId}`);
  // Phase 87 S8 (ADR-344): label — Budget → Fee estimate (§12.6.5); a sub-tab of the Fees group
  await openGroupedTab(page, "finance", "budget");
  return page.getByRole("tabpanel");
}

// ═══════════════════════════════════════════════════════════════════
//  BUDGET-01: Project Budget
// ═══════════════════════════════════════════════════════════════════

test.describe.serial("BUDGET-01: Project Budget", () => {
  const PROJECT_NAME = `Budget Test Project ${RUN_ID}`;
  const TASK_TITLE = `Budget Task ${RUN_ID}`;
  let projectId: string;
  let taskId: string;

  // Setup: create a project and task via API
  test.beforeAll(async () => {
    // Create project
    // Phase 87 S1 (ADR-337): API path
    const { data: project } = await apiStatus("/api/matters", {
      method: "POST",
      body: {
        name: PROJECT_NAME,
        description: "Project for budget E2E tests",
      },
    });
    projectId = project.id;

    // Add Carol as a project member for time logging
    const members = await apiOk<Array<{ id: string; name: string }>>("/api/members");
    const carol = members.find((m) => m.name?.includes("Carol"));
    expect(carol, "seed member Carol").toBeDefined();
    // Phase 87 S1 (ADR-337): API path
    const added = await apiStatus(`/api/matters/${projectId}/members`, {
      method: "POST",
      body: {
        memberId: carol!.id,
        projectRole: "contributor",
      },
    });
    expect(added.status).toBeLessThan(300);

    // Create task for time logging
    // Phase 87 S1 (ADR-337): API path
    const { data: task } = await apiStatus(`/api/matters/${projectId}/tasks`, {
      method: "POST",
      body: {
        title: TASK_TITLE,
      },
    });
    taskId = task.id;
  });

  test("1. View budget tab on project", async ({ page }) => {
    await loginAs(page, "alice");
    const panel = await openFeeEstimateTab(page, projectId);
    await expect(page.getByRole("heading", { level: 1, name: PROJECT_NAME })).toBeVisible();

    // A fresh matter has no fee estimate: the empty state offers to set one
    await expect(panel.getByText("No fee estimate set")).toBeVisible();
    await expect(panel.getByRole("button", { name: "Set fee estimate" })).toBeVisible();
  });

  test("2. Set project budget via API and verify in UI", async ({ page }) => {
    // Set budget: 100 hours, R50,000
    // Phase 87 S1 (ADR-337): API path
    const { status, data } = await apiStatus(`/api/matters/${projectId}/budget`, {
      method: "PUT",
      body: {
        budgetHours: 100,
        budgetAmount: 50000,
        budgetCurrency: "ZAR",
        alertThresholdPct: 80,
        notes: `Budget set for E2E test ${RUN_ID}`,
      },
    });

    expect(status).toBe(200);
    expect(Number(data.budgetHours)).toBe(100);
    expect(Number(data.budgetAmount)).toBe(50000);
    expect(data.budgetCurrency).toBe("ZAR");

    // Verify in UI: the Hours and Amount sections render the estimate
    await loginAs(page, "alice");
    const panel = await openFeeEstimateTab(page, projectId);
    await expect(panel.getByRole("heading", { name: "Hours", exact: true })).toBeVisible({
      timeout: 10_000,
    });
    await expect(panel.getByRole("heading", { name: "Amount", exact: true })).toBeVisible();
    await expect(panel).toContainText("100h");
    await expect(panel.getByText("No fee estimate set")).toHaveCount(0);
  });

  test("3. Budget shows usage after logging time", async ({ page }) => {
    // Log time via API to consume some budget
    const today = new Date().toISOString().split("T")[0];

    const logged = await apiStatus(
      `/api/tasks/${taskId}/time-entries`,
      {
        method: "POST",
        body: {
          date: today,
          durationMinutes: 120, // 2 hours
          billable: true,
          description: `Budget usage test ${RUN_ID}`,
        },
      },
      "carol"
    );
    expect(logged.status).toBe(201);

    // Verify budget status via API — should show consumed hours
    // Phase 87 S1 (ADR-337): API path
    const budgetStatus = await apiOk(`/api/matters/${projectId}/budget`);

    expect(Number(budgetStatus.hoursConsumed)).toBeGreaterThanOrEqual(2);
    expect(Number(budgetStatus.hoursRemaining)).toBeLessThanOrEqual(98);

    // Verify in UI: 2h of the 100h estimate consumed → 98h remaining
    await loginAs(page, "alice");
    const panel = await openFeeEstimateTab(page, projectId);
    await expect(panel.getByRole("heading", { name: "Hours", exact: true })).toBeVisible({
      timeout: 10_000,
    });
    await expect(panel).toContainText("98h");
  });
});
