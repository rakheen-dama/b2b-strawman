/**
 * PROJ-03: Time Entries — Playwright E2E Tests
 *
 * Tests time entry CRUD (log, edit, delete), rate snapshot behavior,
 * billable flag defaults, marking non-billable, and My Work's list of today's entries.
 * Uses API-based setup with UI verification where appropriate.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test projects/project-time --reporter=list
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { apiOk, apiStatus } from "../../helpers/api";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

/** Opens Work → Tasks on the matter and returns the row of the task titled `title`. */
async function openTaskRow(page: Page, projectId: string, title: string) {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/matters/${projectId}`);
  await openGroupedTab(page, "work", "tasks");
  const row = page
    .getByRole("tabpanel")
    .getByRole("row")
    .filter({
      has: page.getByRole("button", { name: `Open task detail for ${title}`, exact: true }),
    });
  await expect(row, `task "${title}" should be listed`).toHaveCount(1, { timeout: 15_000 });
  return row;
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

  // Setup: create a project (Carol as a member) and a task via API
  test.beforeAll(async () => {
    // Phase 87 S1 (ADR-337): API path
    const { status, data: project } = await apiStatus("/api/matters", {
      method: "POST",
      body: {
        name: PROJECT_NAME,
        description: "Project for time entry E2E tests",
      },
    });
    expect(status).toBe(201);
    projectId = project.id;

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
    const { status: taskStatus, data: task } = await apiStatus(`/api/matters/${projectId}/tasks`, {
      method: "POST",
      body: {
        title: TASK_TITLE,
      },
    });
    expect(taskStatus).toBe(201);
    taskId = task.id;
  });

  test("1. Log time on task via UI", async ({ page }) => {
    await loginAs(page, "carol");
    const row = await openTaskRow(page, projectId, TASK_TITLE);
    await expect(page.getByRole("heading", { name: PROJECT_NAME, level: 1 })).toBeVisible();

    await row.getByRole("button", { name: "Log Time" }).click();
    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await expect(dialog).toBeVisible();

    // Duration 2h 15m. Hours / minutes have no accessible name (the "Duration" label has no
    // htmlFor) — product follow-up: label them (components/tasks/log-time-dialog.tsx); ids until then
    await dialog.locator("#time-hours").fill("2");
    await dialog.locator("#time-minutes").fill("15");
    await dialog.getByRole("textbox", { name: /^Description/ }).fill(`Time entry test ${RUN_ID}`);
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // Verify time entry via API
    const entries = await apiOk<
      Array<{
        id: string;
        durationMinutes: number;
        description: string;
      }>
    >(`/api/tasks/${taskId}/time-entries`, {}, "carol");
    const entry = entries.find((e) => e.description?.includes(`Time entry test ${RUN_ID}`));
    expect(entry).toBeTruthy();
    expect(entry!.durationMinutes).toBe(135); // 2h 15m = 135 min
    timeEntryId = entry!.id;
  });

  test("2. Edit time entry via API and verify", async ({ page }) => {
    // Update the time entry to 3 hours
    const { status, data } = await apiStatus(
      `/api/time-entries/${timeEntryId}`,
      {
        method: "PUT",
        body: {
          durationMinutes: 180,
          description: `Edited time ${RUN_ID}`,
        },
      },
      "carol"
    );

    expect(status).toBe(200);
    expect(data.durationMinutes).toBe(180);

    // Fees → Time recording: the "By Task" breakdown shows the task's 3h total
    await loginAs(page, "carol");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters/${projectId}`);
    // Phase 87 S8 (ADR-344): label — Time → Time recording (§12.6.5)
    await openGroupedTab(page, "finance", "time");
    const panel = page.getByRole("tabpanel");
    await expect(panel.getByRole("heading", { name: "By Task" })).toBeVisible({ timeout: 10_000 });
    await expect(panel.getByRole("row").filter({ hasText: TASK_TITLE })).toContainText("3h");
  });

  test("3. Delete time entry via API and verify", async () => {
    const { status } = await apiStatus(
      `/api/time-entries/${timeEntryId}`,
      { method: "DELETE" },
      "carol"
    );
    expect(status).toBe(204);

    // Verify entry is gone via API
    const entries = await apiOk<
      Array<{
        id: string;
      }>
    >(`/api/tasks/${taskId}/time-entries`, {}, "carol");
    const stillExists = entries.find((e) => e.id === timeEntryId);
    expect(stillExists).toBeFalsy();
  });

  test("4. Time entry captures rate snapshot", async () => {
    // Log a time entry via API and check that the rate snapshot fields are populated
    const { status, data } = await apiStatus(
      `/api/tasks/${taskId}/time-entries`,
      {
        method: "POST",
        body: {
          date: new Date().toISOString().split("T")[0],
          durationMinutes: 60,
          billable: true,
          description: `Rate snapshot test ${RUN_ID}`,
        },
      },
      "carol"
    );

    expect(status).toBe(201);

    // The entry carries the rate snapshot fields (null when no rate is configured)
    expect(data).toHaveProperty("billingRateSnapshot");
    expect(data).toHaveProperty("billingRateCurrency");

    timeEntryId = data.id;
  });

  test("5. Billable flag defaults to checked in UI", async ({ page }) => {
    await loginAs(page, "carol");
    const row = await openTaskRow(page, projectId, TASK_TITLE);

    await row.getByRole("button", { name: "Log Time" }).click();
    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("checkbox", { name: "Billable" })).toBeChecked();

    // Close dialog without submitting
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
  });

  test("6. Mark time entry non-billable", async () => {
    // Create a non-billable entry via API
    const { status, data } = await apiStatus(
      `/api/tasks/${taskId}/time-entries`,
      {
        method: "POST",
        body: {
          date: new Date().toISOString().split("T")[0],
          durationMinutes: 30,
          billable: false,
          description: `Non-billable entry ${RUN_ID}`,
        },
      },
      "carol"
    );

    expect(status).toBe(201);
    expect(data.billable).toBe(false);

    // Verify the entry is non-billable
    const entries = await apiOk<
      Array<{
        id: string;
        billable: boolean;
        description: string;
      }>
    >(`/api/tasks/${taskId}/time-entries`, {}, "carol");
    const nonBillable = entries.find((e) =>
      e.description?.includes(`Non-billable entry ${RUN_ID}`)
    );
    expect(nonBillable).toBeTruthy();
    expect(nonBillable!.billable).toBe(false);
  });

  test("7. My Work lists today's time entries", async ({ page }) => {
    // Carol logged the billable (test 4) and non-billable (test 6) entries today
    await loginAs(page, "carol");
    await page.goto(`${BASE}/my-work`);
    await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByText(`${PROJECT_NAME} \u2014 Non-billable entry ${RUN_ID}`)
    ).toBeVisible();
    await expect(
      page.getByText(`${PROJECT_NAME} \u2014 Rate snapshot test ${RUN_ID}`)
    ).toBeVisible();
  });
});
