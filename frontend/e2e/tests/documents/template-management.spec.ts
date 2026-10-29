/**
 * DOC-01: Template Management — Playwright E2E Tests
 *
 * Tests: template list, create a custom template, clone a platform template, edit the clone.
 *
 * Templates this run creates are deactivated in `afterAll` (DELETE /api/templates/{id} is a soft
 * deactivate) so they never linger in Generate Document menus (628F R-5). A platform template can be
 * cloned once per tenant (DocumentTemplateService.cloneTemplate: 409 "Clone exists", inactive
 * clones included), so the clone test picks a platform template no earlier run has cloned — read
 * from the audit log's `template.cloned` events — making a reused stack good for one run per
 * platform template.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e e2e/tests/documents/template-management
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { apiOk } from "../../helpers/api";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const CREATED_NAME = `E2E Cover Letter ${RUN_ID}`;

interface TemplateRow {
  id: string;
  name: string;
  source: "PLATFORM" | "ORG_CUSTOM";
}

/** A platform template that has never been cloned on this tenant. */
async function notYetClonedPlatformTemplate(): Promise<TemplateRow> {
  const templates = await apiOk<TemplateRow[]>("/api/templates");
  const audit = await apiOk<{ content: { details: { original_id?: string } }[] }>(
    "/api/audit-events?eventType=template.cloned&size=200"
  );
  const cloned = new Set(audit.content.map((e) => e.details.original_id));
  const candidate = templates.find((t) => t.source === "PLATFORM" && !cloned.has(t.id));
  expect(candidate, "a platform template not yet cloned on this stack").toBeDefined();
  return candidate!;
}

/** Settings › Documents › Templates (/settings/templates is a legacy redirect to it). */
async function openTemplates(page: Page) {
  await page.goto(`${BASE}/settings/templates`);
  await expect(page).toHaveURL(/\/settings\/documents\?tab=templates/);
  await expect(page.getByRole("heading", { level: 1, name: /^Templates/ })).toBeVisible({
    timeout: 15_000,
  });
}

/** The template-list row whose name link is exactly `name`. */
function templateRow(page: Page, name: string) {
  return page
    .getByTestId("template-list-item")
    .filter({ has: page.getByRole("link", { name, exact: true }) });
}

test.describe.serial("DOC-01: Template Management", () => {
  const created: string[] = [];
  let cloneName: string;

  test.afterAll(async () => {
    for (const id of created) {
      await apiOk(`/api/templates/${id}`, { method: "DELETE" });
    }
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Template list shows seeded templates", async ({ page }) => {
    await openTemplates(page);
    await expect(page.getByRole("heading", { level: 2, name: "Engagement Letter" })).toBeVisible();
    const row = templateRow(page, "Engagement Letter — General");
    await expect(row).toHaveCount(1);
    await expect(row).toContainText("Platform");
    await expect(row).toContainText("Active");
    await expect(page.getByRole("link", { name: "New Template" })).toBeVisible();
    await expect(page.getByTestId("format-filter")).toBeVisible();
  });

  test("Create new template", async ({ page }) => {
    await openTemplates(page);
    await page.getByRole("link", { name: "New Template" }).click();
    await expect(page).toHaveURL(new RegExp(`${BASE}/settings/documents/templates/new`));
    await expect(page.getByRole("heading", { level: 1, name: "New Template" })).toBeVisible({
      timeout: 15_000,
    });

    // Form defaults: an engagement letter for a matter
    const category = page.getByLabel("Category", { exact: true });
    const entityType = page.getByLabel("Primary Entity Type");
    await expect(category).toHaveValue("ENGAGEMENT_LETTER");
    await expect(entityType).toHaveValue("PROJECT");

    await page.getByLabel("Name", { exact: true }).fill(CREATED_NAME);
    await category.selectOption({ label: "Cover Letter" });
    await entityType.selectOption({ label: "Fee note" });
    await page.getByRole("button", { name: "Create Template" }).click();

    // Creating opens the new template's editor
    await expect(page).toHaveURL(/\/settings\/documents\/templates\/[0-9a-f-]{36}\/edit/, {
      timeout: 15_000,
    });
    created.push(page.url().match(/templates\/([0-9a-f-]{36})\/edit/)![1]);
    await expect(page.getByLabel("Template name")).toHaveValue(CREATED_NAME);

    await openTemplates(page);
    const row = templateRow(page, CREATED_NAME);
    await expect(row).toContainText("Custom");
    await expect(row).toContainText("Fee note");
  });

  test("Clone template", async ({ page }) => {
    const source = await notYetClonedPlatformTemplate();
    cloneName = `${source.name} (Custom)`;
    await openTemplates(page);
    // The row's last button opens its actions menu (TemplateActionsMenu — an unnamed icon button)
    await templateRow(page, source.name).getByRole("button").last().click();
    await page.getByRole("menuitem", { name: "Clone & Customize" }).click();

    // Cloning opens the clone's editor — a custom template, so editable
    await expect(page).toHaveURL(/\/settings\/documents\/templates\/[0-9a-f-]{36}\/edit/, {
      timeout: 15_000,
    });
    const cloneId = page.url().match(/templates\/([0-9a-f-]{36})\/edit/)![1];
    expect(cloneId).not.toBe(source.id);
    created.push(cloneId);
    await expect(page.getByLabel("Template name")).toHaveValue(cloneName);
    await expect(page.getByRole("button", { name: "Save", exact: true })).toBeVisible();

    await openTemplates(page);
    await expect(templateRow(page, cloneName)).toContainText("Custom");
    await expect(templateRow(page, source.name)).toContainText("Platform");
  });

  test("Edit template content", async ({ page }) => {
    const renamed = `${cloneName} ${RUN_ID}`;
    await openTemplates(page);
    await page.getByRole("link", { name: cloneName, exact: true }).click();
    await expect(page).toHaveURL(/\/settings\/documents\/templates\/[0-9a-f-]{36}\/edit/);

    // Custom templates are editable: name input, editable Tiptap body, Save
    const nameInput = page.getByLabel("Template name");
    await expect(nameInput).toHaveValue(cloneName, { timeout: 15_000 });
    await expect(page.locator(".ProseMirror").first()).toHaveAttribute("contenteditable", "true");
    await nameInput.fill(renamed);
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByText("Template saved successfully.")).toBeVisible({ timeout: 15_000 });

    await page.reload();
    await expect(page.getByLabel("Template name")).toHaveValue(renamed, { timeout: 15_000 });
  });
});
