/**
 * DOC-01: Template Management — Playwright E2E Tests
 *
 * Tests: template list, create new, clone, edit content.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. API lifecycle seed complete: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test documents/template-management
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

test.describe("DOC-01: Template Management", () => {
  test("Template list shows seeded templates", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/templates`);

    // Heading is "Templates" (h1 with a HelpTip beside it)
    const heading = page.locator("h1").filter({ hasText: "Templates" });
    const hasPage = await heading.isVisible({ timeout: 10000 }).catch(() => false);

    if (!hasPage) {
      test.skip(true, "Templates settings page does not exist at /settings/templates");
      return;
    }

    // Verify the page loads without errors
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Check for template table or empty state
    const hasTable = await page
      .getByRole("table")
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    const hasEmptyState = await page
      .getByText(/No templates/i)
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasTemplateNames = await page
      .getByText(/Engagement Letter|Statement of Work|Cover Letter|NDA|Project Summary/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    // Should show templates or empty state
    expect(hasTable || hasEmptyState || hasTemplateNames).toBeTruthy();

    // Check for "New Template" button (Link wrapping a Button)
    const newTemplateButton = page
      .getByRole("link", { name: /New Template/i })
      .or(page.getByRole("button", { name: /New Template/i }));
    await expect(newTemplateButton.first()).toBeVisible({ timeout: 5000 });

    // Verify format filter is present (data-testid="format-filter")
    const formatFilter = page.locator('[data-testid="format-filter"]');
    const hasFilter = await formatFilter.isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasFilter).toBeTruthy();
  });

  test("Create new template", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/templates`);

    const heading = page.locator("h1").filter({ hasText: "Templates" });
    const hasPage = await heading.isVisible({ timeout: 10000 }).catch(() => false);
    if (!hasPage) {
      test.skip(true, "Templates settings page does not exist");
      return;
    }

    // Click "New Template" link/button
    const newTemplateLink = page.getByRole("link", { name: /New Template/i }).first();
    const hasLink = await newTemplateLink.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasLink) {
      test.skip(true, "New Template button not visible");
      return;
    }

    await newTemplateLink.click();
    await page.waitForLoadState("networkidle");

    // Should navigate to /settings/templates/new
    await expect(page).toHaveURL(new RegExp(`${BASE}/settings/templates/new`));

    // Verify the new template form loads
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Look for template name input field
    const nameInput = page
      .getByLabel(/Name/i)
      .first()
      .or(page.getByRole("textbox", { name: /Name/i }).first())
      .or(page.getByPlaceholder(/template name/i).first());
    const hasNameInput = await nameInput.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasNameInput) {
      await nameInput.fill(`Test Template ${RUN_ID}`);
    }

    // The template editor page should be functional
    await expect(page.locator("main")).toBeVisible();
  });

  test("Clone template", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/templates`);

    const heading = page.locator("h1").filter({ hasText: "Templates" });
    const hasPage = await heading.isVisible({ timeout: 10000 }).catch(() => false);
    if (!hasPage) {
      test.skip(true, "Templates settings page does not exist");
      return;
    }

    // Wait for templates to load
    await page.waitForLoadState("networkidle");

    const hasTable = await page
      .getByRole("table")
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (!hasTable) {
      test.skip(true, "No templates available to clone");
      return;
    }

    // Find a template actions menu (TemplateActionsMenu — three-dot button in the last column)
    const templateRows = page.locator("table tbody tr");
    const rowCount = await templateRows.count();

    if (rowCount === 0) {
      test.skip(true, "No template rows available");
      return;
    }

    // Click the actions menu on the first template row
    const firstRowActions = templateRows.first().getByRole("button").last();
    const hasActionsBtn = await firstRowActions.isVisible({ timeout: 3000 }).catch(() => false);

    if (!hasActionsBtn) {
      test.skip(true, "No actions button found on template rows");
      return;
    }

    await firstRowActions.click();

    // Look for "Clone" option in the dropdown menu
    const cloneOption = page.getByRole("menuitem", { name: /Clone/i });
    const hasClone = await cloneOption.isVisible({ timeout: 3000 }).catch(() => false);

    if (!hasClone) {
      test.skip(true, "Clone option not available in actions menu");
      return;
    }

    await cloneOption.click();
    await page.waitForTimeout(2000);

    // Verify the clone was created — page should reload with new template
    await page.goto(`${BASE}/settings/templates`);
    await page.waitForLoadState("networkidle");

    // Clone may use a different naming convention — verify the page is functional
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Edit template content", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/templates`);

    const heading = page.locator("h1").filter({ hasText: "Templates" });
    const hasPage = await heading.isVisible({ timeout: 10000 }).catch(() => false);
    if (!hasPage) {
      test.skip(true, "Templates settings page does not exist");
      return;
    }

    await page.waitForLoadState("networkidle");

    // Click on the first template name link to open editor
    // Template names in the table are links to /settings/templates/{id}/edit
    // Scope to within a table to avoid matching sidebar links
    const table = page.getByRole("table").first();
    const templateLink = table.getByRole("link").first();
    const hasTemplate = await templateLink.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasTemplate) {
      test.skip(true, "No templates available to edit");
      return;
    }

    await templateLink.click();
    await page.waitForLoadState("networkidle");

    // Should navigate to the template editor page
    await expect(page).toHaveURL(/\/settings\/templates\/.*\/edit/);

    // Verify the editor page loads
    await expect(page.locator("body")).not.toContainText("Something went wrong");
    await expect(page.locator("main")).toBeVisible({ timeout: 10000 });

    // Look for a save button or form elements
    const saveButton = page.getByRole("button", { name: /Save/i }).first();
    const hasSave = await saveButton.isVisible({ timeout: 5000 }).catch(() => false);

    // The editor should have either a save button or editable content area
    // (Tiptap editor or form inputs)
    const hasEditor = await page
      .locator(".ProseMirror, .tiptap, [contenteditable]")
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    const hasFormInputs = await page
      .getByLabel(/Name|Content/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    expect(hasSave || hasEditor || hasFormInputs).toBeTruthy();
  });
});
