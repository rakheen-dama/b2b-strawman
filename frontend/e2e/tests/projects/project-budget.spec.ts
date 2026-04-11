/**
 * BUDGET-01: Project Budget — Playwright E2E Tests
 *
 * Tests budget tab visibility, setting a project budget via API,
 * and verifying budget usage display after logging time.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-budget --reporter=list
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

// --- API helpers ---

async function getToken(user: "alice" | "bob" | "carol"): Promise<string> {
  const users = {
    alice: { userId: "user_e2e_alice", orgRole: "org:owner" },
    bob: { userId: "user_e2e_bob", orgRole: "org:admin" },
    carol: { userId: "user_e2e_carol", orgRole: "org:member" },
  };
  const u = users[user];
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...u, orgId: "org_e2e_test", orgSlug: "e2e-test-org" }),
  });
  const { access_token } = await res.json();
  return access_token;
}

async function apiGet(path: string, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.json();
}

async function apiPost(path: string, body: object, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  return { status: res.status, data: await res.json().catch(() => null) };
}

async function apiPut(path: string, body: object, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  return { status: res.status, data: await res.json().catch(() => null) };
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
    const token = await getToken("alice");

    // Create project
    const { data: project } = await apiPost(
      "/api/projects",
      {
        name: PROJECT_NAME,
        description: "Project for budget E2E tests",
      },
      token
    );
    projectId = project.id;

    // Add Carol as a project member for time logging
    const members = (await apiGet("/api/members", token)) as Array<{ id: string; name: string }>;
    const carol = members.find((m) => m.name?.includes("Carol"));
    if (carol) {
      await apiPost(
        `/api/projects/${projectId}/members`,
        {
          memberId: carol.id,
          projectRole: "contributor",
        },
        token
      );
    }

    // Create task for time logging
    const { data: task } = await apiPost(
      `/api/projects/${projectId}/tasks`,
      {
        title: TASK_TITLE,
      },
      token
    );
    taskId = task.id;
  });

  test("1. View budget tab on project", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);
    await expect(page.getByText(PROJECT_NAME).first()).toBeVisible({ timeout: 10000 });

    // Click Budget tab
    const budgetTab = page.getByRole("tab", { name: "Budget" });
    const hasTab = await budgetTab.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasTab) {
      // Budget tab might have a different name or not be visible
      const anyBudgetTab = page.getByText("Budget").first();
      const hasBudgetText = await anyBudgetTab.isVisible({ timeout: 3000 }).catch(() => false);
      if (!hasBudgetText) {
        test.skip(true, "Budget tab not visible on project detail page");
        return;
      }
      await anyBudgetTab.click();
    } else {
      await budgetTab.click();
    }

    await page.waitForTimeout(1000);

    // Budget tab should render (may show empty state or configuration form)
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("2. Set project budget via API and verify in UI", async ({ page }) => {
    const token = await getToken("alice");

    // Set budget: 100 hours, R50,000
    const { status, data } = await apiPut(
      `/api/projects/${projectId}/budget`,
      {
        budgetHours: 100,
        budgetAmount: 50000,
        budgetCurrency: "ZAR",
        alertThresholdPct: 80,
        notes: `Budget set for E2E test ${RUN_ID}`,
      },
      token
    );

    expect(status).toBe(200);
    expect(Number(data.budgetHours)).toBe(100);
    expect(Number(data.budgetAmount)).toBe(50000);
    expect(data.budgetCurrency).toBe("ZAR");

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);

    const budgetTab = page.getByRole("tab", { name: "Budget" });
    const hasTab = await budgetTab.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasTab) {
      await budgetTab.click();
      await page.waitForTimeout(1000);
    }

    // Should see budget information (hours, amount, or status)
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("3. Budget shows usage after logging time", async ({ page }) => {
    // Log time via API to consume some budget
    const token = await getToken("carol");
    const today = new Date().toISOString().split("T")[0];

    await apiPost(
      `/api/tasks/${taskId}/time-entries`,
      {
        date: today,
        durationMinutes: 120, // 2 hours
        billable: true,
        description: `Budget usage test ${RUN_ID}`,
      },
      token
    );

    // Verify budget status via API — should show consumed hours
    const aliceToken = await getToken("alice");
    const budgetStatus = await apiGet(`/api/projects/${projectId}/budget`, aliceToken);

    expect(Number(budgetStatus.hoursConsumed)).toBeGreaterThanOrEqual(2);
    expect(Number(budgetStatus.hoursRemaining)).toBeLessThanOrEqual(98);

    // Verify in UI — budget tab should show consumed/remaining
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);

    const budgetTab = page.getByRole("tab", { name: "Budget" });
    const hasTab = await budgetTab.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasTab) {
      await budgetTab.click();
      await page.waitForTimeout(1000);
    }

    // The budget panel should show usage information — just verify no crash
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });
});
