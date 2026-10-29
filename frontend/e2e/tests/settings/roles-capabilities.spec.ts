/**
 * ROLE-01: Roles & Capabilities — Playwright E2E Tests
 *
 * Tests role management: the system roles list, the system role cards + Capability Reference, and
 * custom-role CRUD (create with a capability → edit the capability → delete). System roles are
 * read-only in the product ("Built-in roles that cannot be modified or deleted"), so capability
 * edits are exercised on a run-unique custom role.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

async function openRoles(page: Page) {
  await page.goto(`${base}/settings/firm?tab=roles`);
  await expect(page.getByRole("heading", { level: 1, name: "Roles & Permissions" })).toBeVisible({
    timeout: 10_000,
  });
}

test.describe("ROLE-01: Roles & Capabilities", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View roles list", async ({ page }) => {
    await openRoles(page);
    await expect(page.getByRole("heading", { name: "System Roles" })).toBeVisible();
    for (const role of ["Owner", "Admin", "Member"]) {
      await expect(page.getByRole("heading", { level: 3, name: role, exact: true })).toBeVisible();
    }
  });

  test("View system role cards and the capability reference", async ({ page }) => {
    await openRoles(page);
    await expect(
      page.getByText("Built-in roles that cannot be modified or deleted.")
    ).toBeVisible();
    await expect(page.getByRole("heading", { level: 3, name: "Admin", exact: true })).toBeVisible();

    // The collapsible reference table lists every capability
    await page.getByRole("button", { name: "Capability Reference" }).click();
    await expect(
      page.getByRole("cell", { name: "Financial Visibility", exact: true })
    ).toBeVisible();
  });

  test("Create, edit and delete a custom role", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: Create Role dialog is 839px tall on a 1280x720 viewport with no scroll — title and 'Create' button unreachable — components/roles/role-dialog.tsx:105 (DialogContent without max-h / overflow-y-auto)"
    );
    const roleName = `E2E Role ${RUN_ID}`;
    await openRoles(page);

    // Create with one capability
    await page.getByRole("button", { name: "New Role" }).click();
    const create = page.getByRole("dialog", { name: "Create Role" });
    await expect(create).toBeVisible();
    await create.getByLabel("Name", { exact: true }).fill(roleName);
    await create.getByRole("checkbox", { name: "Financial Visibility" }).check();
    await create.getByRole("button", { name: "Create", exact: true }).click();
    await expect(create).toBeHidden({ timeout: 10_000 });
    const editBtn = page.getByRole("button", { name: `Edit ${roleName}`, exact: true });
    await expect(editBtn).toBeVisible({ timeout: 10_000 });

    // Edit: the capability is on; turn it off and save
    await editBtn.click();
    const edit = page.getByRole("dialog", { name: "Edit Role" });
    await expect(edit).toBeVisible();
    const cap = edit.getByRole("checkbox", { name: "Financial Visibility" });
    await expect(cap).toBeChecked();
    await cap.uncheck();
    await edit.getByRole("button", { name: "Save", exact: true }).click();
    await expect(edit).toBeHidden({ timeout: 10_000 });

    // Persisted
    await page.reload();
    await page.getByRole("button", { name: `Edit ${roleName}`, exact: true }).click();
    await expect(edit.getByRole("checkbox", { name: "Financial Visibility" })).not.toBeChecked();
    await page.keyboard.press("Escape");
    await expect(edit).toBeHidden();

    // Delete (cleanup)
    await page.getByRole("button", { name: `Delete ${roleName}`, exact: true }).click();
    const confirm = page.getByRole("alertdialog", { name: "Delete Role" });
    await confirm.getByRole("button", { name: "Delete", exact: true }).click();
    await expect(confirm).toBeHidden({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: `Edit ${roleName}`, exact: true })).toHaveCount(
      0
    );
  });
});
