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

  // G88-M6 (Phase 89 634C): a comment notification opens the task sheet on Comments with the
  // comment in view. Alice mentions Carol (on the matter since beforeAll — also the C2 filter's
  // positive path for a plain member); Carol, not Bob, so parallel admin fan-out cannot push the
  // row out of the 10-item dropdown.
  test("8. Comment notification deep-links to the comment in the task sheet (G88-M6)", async ({
    page,
  }) => {
    const title = `Deep Link Task ${RUN_ID}`;
    const commentBody = `@Carol please review ${RUN_ID}`;
    const { status: created, data: task } = await apiStatus(`/api/matters/${projectId}/tasks`, {
      method: "POST",
      body: { title },
    });
    expect(created).toBe(201);
    const { status, data: comment } = await apiStatus(`/api/matters/${projectId}/comments`, {
      method: "POST",
      body: { entityType: "TASK", entityId: task.id, body: commentBody, visibility: "INTERNAL" },
    });
    expect(status).toBe(201);

    await loginAs(page, "carol");
    await page.goto(`${BASE}/dashboard`);
    await page.getByRole("button", { name: "Notifications", exact: true }).click();
    const popover = page.getByRole("dialog", { name: "Notifications" });
    const item = popover.getByRole("button").filter({ hasText: title });
    await expect(item).toHaveCount(1, { timeout: 15_000 });
    await item.click();

    await expect(page).toHaveURL(
      new RegExp(`/matters/${projectId}\\?tab=tasks&taskId=${task.id}&commentId=${comment.id}`),
      { timeout: 15_000 }
    );
    // D13: the popover closes on navigation, so it cannot cover the sheet
    await expect(popover).toHaveCount(0);
    const sheet = page.getByRole("dialog", { name: "Task Detail" });
    await expect(sheet.getByRole("tab", { name: "Comments" })).toHaveAttribute(
      "aria-selected",
      "true",
      { timeout: 15_000 }
    );
    const target = page.locator(`#comment-${comment.id}`);
    await expect(target).toBeVisible({ timeout: 15_000 });
    await expect(target).toBeInViewport();
    await expect(target).toContainText(commentBody);
  });

  // §14.12 #6 / B7 (Phase 89 634E): a matter (PROJECT-target) comment notification opens the matter's
  // Comments tab with that comment in view and highlighted. Posted through the panel itself (proves
  // the SHARED lock — no visibility select). Carol is mentioned for the same reason as case 8.
  test("9. Matter comment notification deep-links to the highlighted comment on the Comments tab (B7)", async ({
    page,
  }) => {
    const commentBody = `@Carol matter thread ${RUN_ID}`;

    await loginAs(page, "alice");
    await page.goto(`${BASE}/matters/${projectId}`);
    await openGroupedTab(page, "client", "comments");
    const panel = page.getByRole("tabpanel");
    await expect(panel.getByText("Shared with client")).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByLabel("Visibility")).toHaveCount(0);
    await panel.getByLabel("Add a comment").fill(commentBody);
    await panel.getByRole("button", { name: "Post Comment" }).click();
    await expect(panel.getByText(commentBody)).toBeVisible({ timeout: 15_000 });

    const comments = await apiOk<Array<{ id: string; body: string; visibility: string }>>(
      `/api/matters/${projectId}/comments?entityType=PROJECT`
    );
    const comment = comments.find((c) => c.body === commentBody);
    expect(comment, "posted matter comment").toBeDefined();
    expect(comment!.visibility).toBe("SHARED");

    // Notifications are written after commit — wait until Carol's exists before opening the bell.
    // (Never mark-all-read for Carol: notifications/notification-bell.spec.ts relies on her unread state.)
    await expect
      .poll(
        async () => {
          const res = await apiOk<{ content: Array<{ referenceCommentId: string | null }> }>(
            "/api/notifications?size=20",
            {},
            "carol"
          );
          return res.content.some((n) => n.referenceCommentId === comment!.id);
        },
        { timeout: 15_000 }
      )
      .toBe(true);

    await loginAs(page, "carol");
    await page.goto(`${BASE}/dashboard`);
    await page.getByRole("button", { name: "Notifications", exact: true }).click();
    const popover = page.getByRole("dialog", { name: "Notifications" });
    // PROJECT titles carry no matter name ("<actor> commented on matter"); newest first, so ours is
    // first — the exact commentId in the URL below fails loudly on a wrong pick.
    const item = popover.getByRole("button").filter({ hasText: "commented on matter" }).first();
    await expect(item).toBeVisible({ timeout: 15_000 });
    await item.click();

    await expect(page).toHaveURL(
      new RegExp(`/matters/${projectId}\\?tab=comments&commentId=${comment!.id}`),
      { timeout: 15_000 }
    );
    await expect(popover).toHaveCount(0);
    const target = page.locator(`#comment-${comment!.id}`);
    await expect(target).toBeVisible({ timeout: 15_000 });
    // The ring lasts ~2 s after mount — assert it before anything slower.
    await expect(target).toHaveAttribute("data-highlighted", "true");
    await expect(target).toBeInViewport();
    await expect(target).toContainText(commentBody);
    await expect(page.getByTestId("tab-group-client").filter({ visible: true })).toHaveAttribute(
      "aria-selected",
      "true"
    );
  });
});
