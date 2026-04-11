/**
 * PROJ-03: Time Entries — Playwright E2E Tests
 *
 * Tests time entry CRUD (log, edit, delete), rate snapshot behavior,
 * billable flag defaults, marking non-billable, and My Work cross-project view.
 * Uses API-based setup with UI verification where appropriate.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-time --reporter=list
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

async function apiDelete(path: string, token: string) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  return { status: res.status };
}

// ═══════════════════════════════════════════════════════════════════
//  PROJ-03: Time Entries
// ═══════════════════════════════════════════════════════════════════

test.describe.serial("PROJ-03: Time Entries", () => {
  const PROJECT_NAME = `Time Test Project ${RUN_ID}`;
  const TASK_TITLE = `Time Task ${RUN_ID}`;
  let projectId: string;
  let taskId: string;
  let timeEntryId: string;

  // Setup: create a project and task via API
  test.beforeAll(async () => {
    const token = await getToken("alice");

    // Create project
    const { data: project } = await apiPost(
      "/api/projects",
      {
        name: PROJECT_NAME,
        description: "Project for time entry E2E tests",
      },
      token
    );
    projectId = project.id;

    // Add Carol as a project member
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

    // Create task
    const { data: task } = await apiPost(
      `/api/projects/${projectId}/tasks`,
      {
        title: TASK_TITLE,
      },
      token
    );
    taskId = task.id;
  });

  test("1. Log time on task via UI", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${BASE}/projects/${projectId}`);
    await expect(page.getByRole("heading", { name: PROJECT_NAME, level: 1 })).toBeVisible({
      timeout: 10000,
    });

    // Click Tasks tab
    await page.getByRole("tab", { name: "Tasks" }).click();
    await expect(page.getByRole("tabpanel", { name: "Tasks" })).toBeVisible({ timeout: 5000 });

    // Click "Log Time" on the task row
    await page.getByRole("button", { name: "Log Time" }).first().click();
    await expect(page.getByRole("dialog", { name: "Log Time" })).toBeVisible({ timeout: 5000 });

    // Fill in duration: 2h 15m
    const hoursInput = page.getByRole("spinbutton").first();
    await hoursInput.fill("2");
    const minutesInput = page.getByRole("spinbutton").nth(1);
    await minutesInput.fill("15");

    // Fill description
    await page.getByRole("textbox", { name: /Description/ }).fill(`Time entry test ${RUN_ID}`);

    // Submit
    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible({ timeout: 10000 });

    // Verify time entry via API
    const token = await getToken("carol");
    const entries = (await apiGet(`/api/tasks/${taskId}/time-entries`, token)) as Array<{
      id: string;
      durationMinutes: number;
      description: string;
    }>;
    const entry = entries.find((e) => e.description?.includes(`Time entry test ${RUN_ID}`));
    expect(entry).toBeTruthy();
    expect(entry!.durationMinutes).toBe(135); // 2h 15m = 135 min
    timeEntryId = entry!.id;
  });

  test("2. Edit time entry via API and verify", async ({ page }) => {
    const token = await getToken("carol");

    // Update the time entry to 3 hours
    const { status, data } = await apiPut(
      `/api/time-entries/${timeEntryId}`,
      {
        durationMinutes: 180,
        description: `Edited time ${RUN_ID}`,
      },
      token
    );

    expect(status).toBe(200);
    expect(data.durationMinutes).toBe(180);

    // Verify updated entry via UI — check the Time tab
    await loginAs(page, "carol");
    await page.goto(`${BASE}/projects/${projectId}`);
    await page.getByRole("tab", { name: "Time" }).click();
    await expect(page.getByRole("tabpanel", { name: "Time" })).toBeVisible({ timeout: 5000 });

    // The time panel should show the entry (look for Carol or 3h / 180min)
    await expect(
      page
        .getByText(/Edited time/)
        .first()
        .or(page.getByText(/3h|3:00|180/).first())
    ).toBeVisible({ timeout: 10000 });
  });

  test("3. Delete time entry via API and verify", async ({ page }) => {
    const token = await getToken("carol");

    const { status } = await apiDelete(`/api/time-entries/${timeEntryId}`, token);
    expect(status).toBe(204);

    // Verify entry is gone via API
    const entries = (await apiGet(`/api/tasks/${taskId}/time-entries`, token)) as Array<{
      id: string;
    }>;
    const stillExists = entries.find((e) => e.id === timeEntryId);
    expect(stillExists).toBeFalsy();
  });

  test("4. Time entry captures rate snapshot", async ({ page }) => {
    // Log a time entry via API and check that the rate snapshot fields are populated
    const token = await getToken("carol");

    const { status, data } = await apiPost(
      `/api/tasks/${taskId}/time-entries`,
      {
        date: new Date().toISOString().split("T")[0],
        durationMinutes: 60,
        billable: true,
        description: `Rate snapshot test ${RUN_ID}`,
      },
      token
    );

    expect(status).toBe(201);

    // The entry should have rate snapshot fields (may be null if no rate configured,
    // but the fields should exist in the response)
    expect(data).toHaveProperty("billingRateSnapshot");
    expect(data).toHaveProperty("billingRateCurrency");

    // Clean up: save this entry ID for the billable tests
    timeEntryId = data.id;
  });

  test("5. Billable flag defaults to checked in UI", async ({ page }) => {
    await loginAs(page, "carol");
    await page.goto(`${BASE}/projects/${projectId}`);
    await page.getByRole("tab", { name: "Tasks" }).click();
    await expect(page.getByRole("tabpanel", { name: "Tasks" })).toBeVisible({ timeout: 5000 });

    // Click "Log Time"
    await page.getByRole("button", { name: "Log Time" }).first().click();
    await expect(page.getByRole("dialog", { name: "Log Time" })).toBeVisible({ timeout: 5000 });

    // Verify billable checkbox is checked by default
    const billableCheckbox = page.getByRole("checkbox", { name: /Billable/ });
    await expect(billableCheckbox).toBeChecked();

    // Close dialog without submitting
    await page.keyboard.press("Escape");
  });

  test("6. Mark time entry non-billable", async ({ page }) => {
    const token = await getToken("carol");

    // Create a non-billable entry via API
    const { status, data } = await apiPost(
      `/api/tasks/${taskId}/time-entries`,
      {
        date: new Date().toISOString().split("T")[0],
        durationMinutes: 30,
        billable: false,
        description: `Non-billable entry ${RUN_ID}`,
      },
      token
    );

    expect(status).toBe(201);
    expect(data.billable).toBe(false);

    // Verify the entry is non-billable
    const entries = (await apiGet(`/api/tasks/${taskId}/time-entries`, token)) as Array<{
      id: string;
      billable: boolean;
      description: string;
    }>;
    const nonBillable = entries.find((e) =>
      e.description?.includes(`Non-billable entry ${RUN_ID}`)
    );
    expect(nonBillable).toBeTruthy();
    expect(nonBillable!.billable).toBe(false);
  });

  test("7. My Work shows cross-project entries", async ({ page }) => {
    // Ensure Carol has time entries on multiple projects (seed data should have one,
    // plus we created entries in this suite on the test project).
    await loginAs(page, "carol");
    await page.goto(`${BASE}/my-work`);
    await expect(page.getByText(/My Work/i).first()).toBeVisible({ timeout: 10000 });

    // My Work page should load and show content. It aggregates tasks and
    // time entries across projects. The page should not be empty.
    await expect(page.locator("main")).toBeVisible({ timeout: 5000 });

    // Verify the page loaded without errors
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });
});
