/**
 * EXPENSE-01: Project Expenses — Playwright E2E Tests
 *
 * Tests expense tab visibility, creating billable and non-billable expenses,
 * and editing expenses. Uses API-based setup with UI verification.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-expenses --reporter=list
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
//  EXPENSE-01: Project Expenses
// ═══════════════════════════════════════════════════════════════════

test.describe.serial("EXPENSE-01: Project Expenses", () => {
  const PROJECT_NAME = `Expense Test Project ${RUN_ID}`;
  let projectId: string;
  let expenseId: string;

  // Setup: create a project via API
  test.beforeAll(async () => {
    const token = await getToken("alice");
    const { data: project } = await apiPost(
      "/api/projects",
      {
        name: PROJECT_NAME,
        description: "Project for expense E2E tests",
      },
      token
    );
    projectId = project.id;

    // Add Bob as a project member
    const members = (await apiGet("/api/members", token)) as Array<{ id: string; name: string }>;
    const bob = members.find((m) => m.name?.includes("Bob"));
    if (bob) {
      await apiPost(
        `/api/projects/${projectId}/members`,
        {
          memberId: bob.id,
          projectRole: "contributor",
        },
        token
      );
    }
  });

  test("1. View expenses tab on project", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);
    await expect(page.getByRole("heading", { name: PROJECT_NAME, level: 1 })).toBeVisible({
      timeout: 10000,
    });

    // Click Expenses tab
    await page.getByRole("tab", { name: "Expenses" }).click();
    await expect(page.getByRole("tabpanel", { name: "Expenses" })).toBeVisible({ timeout: 5000 });

    // The tab should render (may show empty state or "Log Expense" button)
    await expect(page.getByRole("button", { name: /Log Expense/ })).toBeVisible({ timeout: 5000 });
  });

  test("2. Create billable expense via API and verify in UI", async ({ page }) => {
    const token = await getToken("bob");
    const today = new Date().toISOString().split("T")[0];

    const { status, data } = await apiPost(
      `/api/projects/${projectId}/expenses`,
      {
        date: today,
        description: `Billable expense ${RUN_ID}`,
        amount: 250.0,
        currency: "ZAR",
        category: "TRAVEL",
        billable: true,
      },
      token
    );

    expect(status).toBe(201);
    expect(data.billable).toBe(true);
    expect(data.category).toBe("TRAVEL");
    expenseId = data.id;

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);
    await page.getByRole("tab", { name: "Expenses" }).click();
    await expect(page.getByRole("tabpanel", { name: "Expenses" })).toBeVisible({ timeout: 5000 });

    // Should see the expense description
    await expect(page.getByText(`Billable expense ${RUN_ID}`).first()).toBeVisible({
      timeout: 10000,
    });
  });

  test("3. Create non-billable expense via API", async ({ page }) => {
    const token = await getToken("bob");
    const today = new Date().toISOString().split("T")[0];

    const { status, data } = await apiPost(
      `/api/projects/${projectId}/expenses`,
      {
        date: today,
        description: `Non-billable expense ${RUN_ID}`,
        amount: 100.0,
        currency: "ZAR",
        category: "SOFTWARE",
        billable: false,
      },
      token
    );

    expect(status).toBe(201);
    expect(data.billable).toBe(false);
    expect(data.category).toBe("SOFTWARE");

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);
    await page.getByRole("tab", { name: "Expenses" }).click();
    await expect(page.getByText(`Non-billable expense ${RUN_ID}`).first()).toBeVisible({
      timeout: 10000,
    });
  });

  test("4. Edit expense amount via API and verify", async ({ page }) => {
    const token = await getToken("bob");

    // Update the first expense to a different amount
    const { status, data } = await apiPut(
      `/api/projects/${projectId}/expenses/${expenseId}`,
      {
        amount: 375.5,
        description: `Updated expense ${RUN_ID}`,
      },
      token
    );

    expect(status).toBe(200);
    expect(Number(data.amount)).toBeCloseTo(375.5, 2);

    // Verify updated description in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects/${projectId}`);
    await page.getByRole("tab", { name: "Expenses" }).click();
    await expect(page.getByText(`Updated expense ${RUN_ID}`).first()).toBeVisible({
      timeout: 10000,
    });
  });
});
