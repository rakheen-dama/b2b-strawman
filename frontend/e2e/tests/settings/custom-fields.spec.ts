/**
 * CF-01: Custom Fields — Playwright E2E Tests
 *
 * Tests custom field management: view definitions, create a text field, the legal-za intake field
 * on the New Client form, and a client custom-field value persisting across a reload.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh (legal-za: "SA Legal — Client Details"
 *      field group, auto-applied to clients)
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { apiOk, createClient } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const ID_FIELD = "ID / Passport Number"; // legal-za-customer field pack, slug id_passport_number

async function openCustomFieldSettings(page: import("@playwright/test").Page) {
  // Phase 87 S9 (ADR-344): /settings/custom-fields → /settings/matters?tab=custom-fields
  await page.goto(`${base}/settings/matters?tab=custom-fields`);
  await expect(page.getByRole("heading", { level: 1, name: /^Custom Fields/ })).toBeVisible({
    timeout: 10_000,
  });
}

test.describe("CF-01: Custom Fields", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View custom field definitions", async ({ page }) => {
    await openCustomFieldSettings(page);
    // Per-entity tabs; the legal-za pack seeds client fields
    // Phase 88 628A (ADR-357): label — entity "Customers" → "Clients"
    await page.getByRole("tab", { name: "Clients" }).click();
    await expect(page.getByRole("heading", { name: "Field Definitions" })).toBeVisible();
    await expect(page.getByTestId("field-row").filter({ hasText: ID_FIELD })).toBeVisible();
  });

  test("Create text custom field", async ({ page }) => {
    const fieldName = `Test Field ${RUN_ID}`;
    await openCustomFieldSettings(page);
    await page.getByRole("tab", { name: "Clients" }).click();

    await page.getByRole("button", { name: "Add Field" }).click();
    const dialog = page.getByRole("dialog", { name: "Add Field Definition" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Entity Type").selectOption("CUSTOMER");
    await dialog.getByLabel("Name", { exact: true }).fill(fieldName);
    await dialog.getByLabel("Field Type").selectOption("TEXT");
    await dialog.getByRole("button", { name: "Create Field" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // Listed under Clients
    await page.getByRole("tab", { name: "Clients" }).click();
    await expect(page.getByTestId("field-row").filter({ hasText: fieldName })).toBeVisible({
      timeout: 10_000,
    });
  });

  test("Custom field appears on entity form", async ({ page }) => {
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients`);
    // Phase 87 S5 (ADR-344): label
    await page.getByRole("button", { name: "New Client" }).click();
    const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
    await expect(dialog).toBeVisible();

    // Step 1 requires a name and an email to advance
    await dialog.getByRole("textbox", { name: "Name", exact: true }).fill(`CF Temp ${RUN_ID}`);
    await dialog
      .getByRole("textbox", { name: "Email", exact: true })
      .fill(`cf-${RUN_ID}@example.com`);
    await dialog.getByRole("button", { name: "Next" }).click();

    // Step 2 renders the auto-applied legal-za intake field
    await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
    await expect(dialog.getByLabel(ID_FIELD)).toBeVisible();

    // Abandon without creating the client
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
  });

  test("Custom field value persists", async ({ page }) => {
    // Own client (seed rows are read-only for specs)
    const name = `CF Persist ${RUN_ID}`;
    const clientId = (
      await createClient(name, `cf-persist-${RUN_ID}@example.com`, { withAddress: false })
    ).id;
    const value = `E2E-${RUN_ID}-7001015009087`;

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients/${clientId}`);
    await expect(page.getByRole("heading", { level: 1, name, exact: true })).toBeVisible({
      timeout: 10_000,
    });
    await openGroupedTab(page, "details", "fields");
    const panel = page.getByRole("tabpanel");
    await panel.getByLabel(ID_FIELD).fill(value);
    await panel.getByRole("button", { name: "Save Custom Fields" }).click();
    await expect(panel.getByText("Saved successfully")).toBeVisible({ timeout: 10_000 });

    // Persisted: shown again after a reload, and stored on the client
    await page.reload();
    await openGroupedTab(page, "details", "fields");
    await expect(page.getByRole("tabpanel").getByLabel(ID_FIELD)).toHaveValue(value, {
      timeout: 10_000,
    });
    const client = await apiOk<{ customFields?: Record<string, unknown> }>(
      `/api/clients/${clientId}`
    );
    expect(client.customFields?.id_passport_number).toBe(value);
  });
});
