/**
 * PROJ-02: Project Tasks — Playwright E2E Tests
 *
 * Tests task CRUD, status transitions (OPEN -> IN_PROGRESS -> DONE),
 * reopen, cancel, and member assignment. Uses a mix of API setup
 * and UI verification.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-tasks --reporter=list
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";

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

async function apiPatch(path: string, token: string, body?: object) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, data: await res.json().catch(() => null) };
}

/** Opens the matter's Work → Tasks sub-tab and returns the (unnamed) tab panel. */
async function openTasksTab(page: Page, projectId: string) {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/matters/${projectId}`);
  await openGroupedTab(page, "work", "tasks");
  const panel = page.getByRole("tabpanel");
  await expect(panel.getByRole("button", { name: "New Task" })).toBeVisible({ timeout: 10_000 });
  return panel;
}

/** The task-list row of the task titled `title`. */
function taskRow(page: Page, title: string) {
  return page
    .getByRole("tabpanel")
    .getByRole("row")
    .filter({
      has: page.getByRole("button", { name: `Open task detail for ${title}`, exact: true }),
    });
}

/** Toggles a task-list status filter chip and waits for the refetch to settle. */
async function toggleFilterChip(page: Page, label: string) {
  const chip = page
    .getByRole("group", { name: "Task filters" })
    .getByRole("button", { name: label });
  await chip.click();
  await expect(chip).toHaveAttribute("aria-pressed", "true");
  await expect(chip).toBeEnabled();
}

// ═══════════════════════════════════════════════════════════════════
//  PROJ-02: Project Tasks
// ═══════════════════════════════════════════════════════════════════

test.describe.serial("PROJ-02: Project Tasks", () => {
  const PROJECT_NAME = `Task Test Project ${RUN_ID}`;
  const TASK_TITLE = `Test Task ${RUN_ID}`;
  const EDITED_TITLE = `Edited Task ${RUN_ID}`;
  const ASSIGNABLE_TITLE = `Assignable Task ${RUN_ID}`;
  // Keeps the default (Open + In Progress) list non-empty: with zero visible tasks the panel shows
  // the "No tasks yet" empty state without the filter chips (task-list-panel.tsx:304), so a DONE /
  // CANCELLED task could not be revealed. Product follow-up: that empty state hides existing tasks.
  const COMPANION_TITLE = `Companion Task ${RUN_ID}`;
  let projectId: string;
  let taskId: string;

  // Setup: create a project via API for task testing, with Carol as a member (assignable)
  test.beforeAll(async () => {
    const token = await getToken("alice");
    const { status, data } = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      "/api/matters",
      {
        name: PROJECT_NAME,
        description: "Project for task E2E tests",
      },
      token
    );
    expect(status).toBe(201);
    projectId = data.id;

    const members = (await apiGet("/api/members", token)) as Array<{ id: string; name: string }>;
    const carol = members.find((m) => m.name?.includes("Carol"));
    expect(carol, "seed member Carol").toBeDefined();
    const added = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      `/api/matters/${projectId}/members`,
      {
        memberId: carol!.id,
        projectRole: "contributor",
      },
      token
    );
    expect(added.status).toBeLessThan(300);

    const companion = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      `/api/matters/${projectId}/tasks`,
      { title: COMPANION_TITLE },
      token
    );
    expect(companion.status).toBe(201);
  });

  test("1. Create task on project via UI", async ({ page }) => {
    await loginAs(page, "alice");
    const panel = await openTasksTab(page, projectId);
    await expect(page.getByRole("heading", { name: PROJECT_NAME, level: 1 })).toBeVisible();

    await panel.getByRole("button", { name: "New Task" }).click();
    const dialog = page.getByRole("dialog", { name: "Create Task" });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("textbox", { name: "Title" }).fill(TASK_TITLE);
    await dialog.getByRole("button", { name: "Create Task" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // Persisted: listed again after a reload
    await page.reload();
    await openGroupedTab(page, "work", "tasks");
    await expect(taskRow(page, TASK_TITLE)).toBeVisible({ timeout: 10_000 });

    // Get the task ID via API for subsequent tests
    const token = await getToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const tasks = (await apiGet(`/api/matters/${projectId}/tasks`, token)) as Array<{
      id: string;
      title: string;
    }>;
    const task = tasks.find((t) => t.title === TASK_TITLE);
    expect(task).toBeTruthy();
    taskId = task!.id;
  });

  test("2. Edit task title via API and verify in UI", async ({ page }) => {
    const token = await getToken("alice");

    // Get current task to preserve fields for the PUT request
    const task = (await apiGet(`/api/tasks/${taskId}`, token)) as {
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    };

    const { status } = await apiPut(
      `/api/tasks/${taskId}`,
      {
        title: EDITED_TITLE,
        description: task.description || "",
        priority: task.priority || "MEDIUM",
        status: task.status || "OPEN",
        type: task.type,
      },
      token
    );
    expect(status).toBe(200);

    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toBeVisible({ timeout: 10_000 });
    await expect(taskRow(page, TASK_TITLE)).toHaveCount(0);
  });

  test("3. Change task status: OPEN -> IN_PROGRESS", async ({ page }) => {
    const token = await getToken("alice");
    const task = (await apiGet(`/api/tasks/${taskId}`, token)) as {
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    };

    const { status, data } = await apiPut(
      `/api/tasks/${taskId}`,
      {
        ...task,
        status: "IN_PROGRESS",
      },
      token
    );

    expect(status).toBe(200);
    expect(data.status).toBe("IN_PROGRESS");

    // The row's status badge (not the "In Progress" filter chip)
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toContainText("In Progress", { timeout: 10_000 });
  });

  test("4. Change task status: IN_PROGRESS -> DONE", async ({ page }) => {
    const token = await getToken("alice");

    // Use the PATCH complete endpoint
    const { status, data } = await apiPatch(`/api/tasks/${taskId}/complete`, token);

    expect(status).toBe(200);
    expect(data.status).toBe("DONE");

    // DONE tasks are hidden by the default filter (Open + In Progress); the "Done" chip shows them
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toHaveCount(0);
    await toggleFilterChip(page, "Done");
    await expect(taskRow(page, EDITED_TITLE)).toContainText("Done", { timeout: 10_000 });
  });

  test("5. Reopen completed task", async ({ page }) => {
    const token = await getToken("alice");

    const { status, data } = await apiPatch(`/api/tasks/${taskId}/reopen`, token);

    expect(status).toBe(200);
    expect(data.status).toBe("OPEN");

    // Back in the default (Open + In Progress) list
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toContainText("Open", { timeout: 10_000 });
  });

  test("6. Cancel task", async ({ page }) => {
    const token = await getToken("alice");

    const { status, data } = await apiPatch(`/api/tasks/${taskId}/cancel`, token);

    expect(status).toBe(200);
    expect(data.status).toBe("CANCELLED");

    // Hidden by the default filter; listed as Cancelled under the "Cancelled" chip
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toHaveCount(0);
    await toggleFilterChip(page, "Cancelled");
    await expect(taskRow(page, EDITED_TITLE)).toContainText("Cancelled", { timeout: 10_000 });
  });

  test("7. Assign member to task", async ({ page }) => {
    const token = await getToken("alice");

    // Create a fresh task to assign (cancelled task cannot be edited)
    const { status: created, data: newTask } = await apiPost(
      // Phase 87 S1 (ADR-337): API path
      `/api/matters/${projectId}/tasks`,
      {
        title: ASSIGNABLE_TITLE,
      },
      token
    );
    expect(created).toBe(201);
    const newTaskId = newTask.id;

    // Get Carol's member ID
    const members = (await apiGet("/api/members", token)) as Array<{ id: string; name: string }>;
    const carol = members.find((m) => m.name?.includes("Carol"));
    expect(carol).toBeTruthy();

    // Assign Carol to the task
    const task = (await apiGet(`/api/tasks/${newTaskId}`, token)) as {
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    };

    const { status, data: updatedTask } = await apiPut(
      `/api/tasks/${newTaskId}`,
      {
        ...task,
        assigneeId: carol!.id,
      },
      token
    );

    expect(status).toBe(200);
    expect(updatedTask.assigneeId).toBe(carol!.id);

    // The assignee column of that task's row names Carol
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, ASSIGNABLE_TITLE)).toContainText(carol!.name, { timeout: 10_000 });
  });
});
