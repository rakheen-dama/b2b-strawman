/**
 * NOTIF-01: Notification Bell — Playwright E2E Tests
 *
 * Tests the notification bell in the header: unread count, dropdown, marking one notification read
 * by opening it, the notifications page and "Mark all as read" (as Carol).
 *
 * Setup (API, run-unique, 628F R-5): Alice assigns Carol two tasks on a matter of this run, which
 * gives Carol two TASK_ASSIGNED in-app notifications — so the unread state is known rather than left
 * to whatever other specs happened to generate. Carol (member), not Bob (admin): admins and owners
 * also receive fan-out notifications (fee note events, …) from spec files running in parallel, which
 * would push this run's notifications down the list.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (seed members Alice and Carol)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { addMatterMember, apiOk, createMatter, createTask, memberNamed } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const TASK_OPENED = `Bell Task Opened ${RUN_ID}`;
const TASK_LISTED = `Bell Task Listed ${RUN_ID}`;

interface Notification {
  id: string;
  title: string;
  isRead: boolean;
}

/** Carol's TASK_ASSIGNED notifications for this run's two tasks (newest first). */
async function runNotifications(): Promise<Notification[]> {
  const page = await apiOk<{ content: Notification[] }>("/api/notifications?size=100", {}, "carol");
  return page.content.filter((n) => n.title.includes(TASK_OPENED) || n.title.includes(TASK_LISTED));
}

/** Alice assigns the task to Carol (TaskService publishes TaskAssignedEvent → Carol's notification). */
async function assignToCarol(taskId: string, carolId: string) {
  const task = await apiOk<Record<string, unknown>>(`/api/tasks/${taskId}`);
  await apiOk(`/api/tasks/${taskId}`, { method: "PUT", body: { ...task, assigneeId: carolId } });
}

function bell(page: Page) {
  return page.getByRole("button", { name: "Notifications", exact: true });
}

test.describe.serial("NOTIF-01: Notification Bell", () => {
  let matterId: string;

  test.beforeAll(async () => {
    const carol = await memberNamed("Carol");
    matterId = await createMatter(`Bell Matter ${RUN_ID}`);
    await addMatterMember(matterId, carol.id);
    for (const title of [TASK_OPENED, TASK_LISTED]) {
      await assignToCarol(await createTask(matterId, title), carol.id);
    }
    await expect
      .poll(async () => (await runNotifications()).filter((n) => !n.isRead).length)
      .toBe(2);
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "carol");
  });

  test("Bell shows unread count", async ({ page }) => {
    await page.goto(`${base}/dashboard`);
    await expect(bell(page)).toBeVisible({ timeout: 15_000 });
    // The badge is aria-hidden; the bell announces the count in a polite live region
    await expect(bell(page)).toContainText(/\d+\+? unread notifications/, { timeout: 15_000 });
    await expect(bell(page).locator('span[aria-hidden="true"]')).toHaveText(/^(\d+|99\+)$/);
  });

  test("Click bell opens dropdown", async ({ page }) => {
    await page.goto(`${base}/dashboard`);
    await bell(page).click();
    const panel = page.getByRole("dialog", { name: "Notifications" });
    await expect(panel).toBeVisible();
    for (const title of [TASK_OPENED, TASK_LISTED]) {
      const item = panel.getByRole("button").filter({ hasText: title });
      await expect(item).toHaveCount(1, { timeout: 15_000 });
      await expect(item.getByLabel("Unread")).toBeVisible();
    }
    await expect(panel.getByRole("link", { name: "View all notifications" })).toBeVisible();
  });

  test("Mark notification as read", async ({ page }) => {
    await page.goto(`${base}/dashboard`);
    await bell(page).click();
    const panel = page.getByRole("dialog", { name: "Notifications" });
    const item = panel.getByRole("button").filter({ hasText: TASK_OPENED });
    await expect(item.getByLabel("Unread")).toBeVisible({ timeout: 15_000 });

    // Opening a notification marks it read and deep-links to the matter's tasks
    await item.click();
    await expect(page).toHaveURL(new RegExp(`/matters/${matterId}\\?tab=tasks`), {
      timeout: 15_000,
    });
    await expect
      .poll(
        async () => (await runNotifications()).find((n) => n.title.includes(TASK_OPENED))?.isRead
      )
      .toBe(true);
    const other = (await runNotifications()).find((n) => n.title.includes(TASK_LISTED));
    expect(other?.isRead).toBe(false);
  });

  test("Navigate to notifications page", async ({ page }) => {
    await page.goto(`${base}/notifications`);
    await expect(page.getByRole("heading", { level: 1, name: "Notifications" })).toBeVisible({
      timeout: 15_000,
    });
    const main = page.locator("#main-content");
    const opened = main.getByRole("button").filter({ hasText: TASK_OPENED });
    const listed = main.getByRole("button").filter({ hasText: TASK_LISTED });
    await expect(opened).toHaveCount(1);
    await expect(opened.getByLabel("Unread")).toHaveCount(0);
    await expect(listed.getByLabel("Unread")).toBeVisible();
  });

  test("Mark all as read", async ({ page }) => {
    await page.goto(`${base}/notifications`);
    const main = page.locator("#main-content");
    // Carol has this run's unread TASK_LISTED notification, so the action is offered
    const markAll = main.getByRole("button", { name: "Mark all as read" });
    await expect(markAll).toBeVisible({ timeout: 15_000 });
    await markAll.click();
    await expect(markAll).toBeHidden({ timeout: 15_000 });
    await expect(main.getByLabel("Unread")).toHaveCount(0);
    expect((await runNotifications()).every((n) => n.isRead)).toBe(true);

    // Persisted: after a reload nothing of this run is unread. (Not asserted: the bell badge —
    // it polls every 30 s and the page's mark-all does not refetch it, hooks/
    // use-notification-polling.ts; and a sibling spec may notify Carol in the meantime.)
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: "Notifications" })).toBeVisible({
      timeout: 15_000,
    });
    for (const title of [TASK_OPENED, TASK_LISTED]) {
      const item = main.getByRole("button").filter({ hasText: title });
      await expect(item).toHaveCount(1);
      await expect(item.getByLabel("Unread")).toHaveCount(0);
    }
  });
});
