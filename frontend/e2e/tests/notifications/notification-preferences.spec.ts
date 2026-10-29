/**
 * NOTIF-02: Notification Preferences — Playwright E2E Tests
 *
 * Tests notification preference management (Settings › Connections › Notifications): view the
 * categories, turn email on for one notification type and save, turn it off again and save — each
 * persisted across a reload.
 *
 * Alice's preferences are shared state: the toggled type ("Time Reminders") starts at the product
 * default (email off — NotificationService.getPreferences), the two tests return it there, and
 * `afterAll` restores the default if a run died in between (628F R-5).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { apiOk } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const TYPE = "TIME_REMINDER";
const TYPE_LABEL = "Time Reminders";

interface Preference {
  notificationType: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
}

async function preference(): Promise<Preference> {
  const { preferences } = await apiOk<{ preferences: Preference[] }>(
    "/api/notifications/preferences"
  );
  const pref = preferences.find((p) => p.notificationType === TYPE);
  expect(pref, TYPE).toBeDefined();
  return pref!;
}

/** Product default for the toggled type: in-app on, email off. */
async function restoreDefault() {
  const pref = await preference();
  if (!pref.inAppEnabled || pref.emailEnabled) {
    await apiOk("/api/notifications/preferences", {
      method: "PUT",
      body: { preferences: [{ notificationType: TYPE, inAppEnabled: true, emailEnabled: false }] },
    });
  }
}

/** /settings/notifications is a legacy redirect to the Connections › Notifications tab. */
async function openPreferences(page: Page) {
  await page.goto(`${base}/settings/notifications`);
  await expect(page).toHaveURL(/\/settings\/connections\?tab=notifications/);
  await expect(
    page.getByRole("heading", { level: 1, name: /^Notification Preferences/ })
  ).toBeVisible({ timeout: 15_000 });
}

/**
 * The type's email switch. It has no accessible name — the row's <Label htmlFor> targets only the
 * in-app switch (product follow-up: label it, components/notifications/notification-preferences-
 * form.tsx:169-176) — so it is addressed by id.
 */
function emailSwitch(page: Page) {
  return page.locator(`#email-${TYPE}`);
}

/** Flips the email switch, saves, and checks the saved state after a reload. */
async function setEmail(page: Page, enabled: boolean) {
  await expect(emailSwitch(page)).toHaveAttribute("aria-checked", String(!enabled));
  await emailSwitch(page).click();
  await expect(emailSwitch(page)).toHaveAttribute("aria-checked", String(enabled));
  await page.getByRole("button", { name: "Save preferences" }).click();
  await expect(page.getByText("Preferences saved successfully.")).toBeVisible({ timeout: 15_000 });
  await page.reload();
  await expect(emailSwitch(page)).toHaveAttribute("aria-checked", String(enabled), {
    timeout: 15_000,
  });
  expect((await preference()).emailEnabled).toBe(enabled);
}

test.describe.serial("NOTIF-02: Notification Preferences", () => {
  test.beforeAll(restoreDefault);
  test.afterAll(restoreDefault);

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View preference categories", async ({ page }) => {
    await openPreferences(page);
    // Scoped to the tab panel: the settings navigation has a "Matters" link too
    const panel = page.getByRole("tabpanel");
    for (const category of ["Tasks", "Matters", "Billing", "Time Tracking"]) {
      await expect(panel.getByText(category, { exact: true })).toBeVisible();
    }
    // Each type row: label + In-App switch (named by the label) + Email switch
    await expect(page.getByRole("switch", { name: TYPE_LABEL })).toHaveAttribute(
      "aria-checked",
      "true"
    );
    await expect(emailSwitch(page)).toHaveAttribute("aria-checked", "false");
  });

  test("Enable email for a category", async ({ page }) => {
    await openPreferences(page);
    await setEmail(page, true);
  });

  test("Disable email for a category", async ({ page }) => {
    await openPreferences(page);
    await setEmail(page, false);
  });
});
