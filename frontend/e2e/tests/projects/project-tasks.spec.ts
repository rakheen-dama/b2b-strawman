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
import { apiOk, apiStatus } from "../../helpers/api";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

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
    // Phase 87 S1 (ADR-337): API path
    const { status, data } = await apiStatus("/api/matters", {
      method: "POST",
      body: {
        name: PROJECT_NAME,
        description: "Project for task E2E tests",
      },
    });
    expect(status).toBe(201);
    projectId = data.id;

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

    // Phase 87 S1 (ADR-337): API path
    const companion = await apiStatus(`/api/matters/${projectId}/tasks`, {
      method: "POST",
      body: { title: COMPANION_TITLE },
    });
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
    // Phase 87 S1 (ADR-337): API path
    const tasks = await apiOk<
      Array<{
        id: string;
        title: string;
      }>
    >(`/api/matters/${projectId}/tasks`);
    const task = tasks.find((t) => t.title === TASK_TITLE);
    expect(task).toBeTruthy();
    taskId = task!.id;
  });

  test("2. Edit task title via API and verify in UI", async ({ page }) => {
    // Get current task to preserve fields for the PUT request
    const task = await apiOk<{
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    }>(`/api/tasks/${taskId}`);

    const { status } = await apiStatus(`/api/tasks/${taskId}`, {
      method: "PUT",
      body: {
        title: EDITED_TITLE,
        description: task.description || "",
        priority: task.priority || "MEDIUM",
        status: task.status || "OPEN",
        type: task.type,
      },
    });
    expect(status).toBe(200);

    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toBeVisible({ timeout: 10_000 });
    await expect(taskRow(page, TASK_TITLE)).toHaveCount(0);
  });

  test("3. Change task status: OPEN -> IN_PROGRESS", async ({ page }) => {
    const task = await apiOk<{
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    }>(`/api/tasks/${taskId}`);

    const { status, data } = await apiStatus(`/api/tasks/${taskId}`, {
      method: "PUT",
      body: {
        ...task,
        status: "IN_PROGRESS",
      },
    });

    expect(status).toBe(200);
    expect(data.status).toBe("IN_PROGRESS");

    // The row's status badge (not the "In Progress" filter chip)
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toContainText("In Progress", { timeout: 10_000 });
  });

  test("4. Change task status: IN_PROGRESS -> DONE", async ({ page }) => {
    // Use the PATCH complete endpoint
    const { status, data } = await apiStatus(`/api/tasks/${taskId}/complete`, { method: "PATCH" });

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
    const { status, data } = await apiStatus(`/api/tasks/${taskId}/reopen`, { method: "PATCH" });

    expect(status).toBe(200);
    expect(data.status).toBe("OPEN");

    // Back in the default (Open + In Progress) list
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, EDITED_TITLE)).toContainText("Open", { timeout: 10_000 });
  });

  test("6. Cancel task", async ({ page }) => {
    const { status, data } = await apiStatus(`/api/tasks/${taskId}/cancel`, { method: "PATCH" });

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
    // Create a fresh task to assign (cancelled task cannot be edited)
    // Phase 87 S1 (ADR-337): API path
    const { status: created, data: newTask } = await apiStatus(`/api/matters/${projectId}/tasks`, {
      method: "POST",
      body: {
        title: ASSIGNABLE_TITLE,
      },
    });
    expect(created).toBe(201);
    const newTaskId = newTask.id;

    // Get Carol's member ID
    const members = await apiOk<Array<{ id: string; name: string }>>("/api/members");
    const carol = members.find((m) => m.name?.includes("Carol"));
    expect(carol).toBeTruthy();

    // Assign Carol to the task
    const task = await apiOk<{
      title: string;
      description: string;
      priority: string;
      status: string;
      type: string | null;
    }>(`/api/tasks/${newTaskId}`);

    const { status, data: updatedTask } = await apiStatus(`/api/tasks/${newTaskId}`, {
      method: "PUT",
      body: {
        ...task,
        assigneeId: carol!.id,
      },
    });

    expect(status).toBe(200);
    expect(updatedTask.assigneeId).toBe(carol!.id);

    // The assignee column of that task's row names Carol
    await loginAs(page, "alice");
    await openTasksTab(page, projectId);
    await expect(taskRow(page, ASSIGNABLE_TITLE)).toContainText(carol!.name, { timeout: 10_000 });
  });
});
