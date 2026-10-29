/**
 * CUST-01: Customer CRUD — Playwright E2E Tests
 *
 * Tests: create client (step 1 + step 2 intake field), edit name, search the list, list rows.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test customers/customer-crud --reporter=list
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { apiGet } from "../../helpers/seed";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);

interface ClientRow {
  id: string;
  name: string;
  customFields?: Record<string, unknown>;
}

async function clientByName(name: string): Promise<ClientRow | undefined> {
  const body = await apiGet<ClientRow[] | { content: ClientRow[] }>("/api/clients?size=500");
  return (Array.isArray(body) ? body : body.content).find((c) => c.name === name);
}

/** Opens the New Client dialog on the Clients page and returns it (title changes on step 2). */
async function openNewClientDialog(page: Page) {
  // Phase 87 S4 (ADR-344): route rename
  await page.goto(`${BASE}/clients`);
  await expect(page.getByRole("heading", { level: 1, name: "Clients" })).toBeVisible({
    timeout: 10_000,
  });
  await page.getByRole("button", { name: "New Client" }).click();
  const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await expect(dialog).toBeVisible();
  return dialog;
}

test.describe.serial("CUST-01: Customer CRUD", () => {
  const CUSTOMER_NAME = `TestCust ${RUN_ID}`;
  const CUSTOMER_EMAIL = `testcust-${RUN_ID}@example.com`;
  const CUSTOMER_PHONE = "+27-11-555-0001";
  const EDITED_NAME = `TestCust Edited ${RUN_ID}`;

  test("Create customer with required fields", async ({ page }) => {
    await loginAs(page, "alice");
    const dialog = await openNewClientDialog(page);

    // Step 1 — scoped to the dialog with exact labels: "Name" / "Email" / "Phone" are substrings
    // of the "Contact Name" / "Contact Email" / "Contact Phone" fields further down the form.
    await dialog.getByLabel("Name", { exact: true }).fill(CUSTOMER_NAME);
    await dialog.getByLabel("Email", { exact: true }).fill(CUSTOMER_EMAIL);
    await dialog.getByLabel(/^Phone/).fill(CUSTOMER_PHONE);
    await dialog.getByRole("button", { name: "Next" }).click();

    // Step 2 — intake fields; creating the client closes the dialog and opens the client page
    await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
    // Phase 87 S5 (ADR-344): label
    await dialog.getByRole("button", { name: "Create Client" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
    await expect(
      page.getByRole("heading", { level: 1, name: CUSTOMER_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    // Listed on the Clients page
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("link", { name: CUSTOMER_NAME, exact: true })).toBeVisible({
      timeout: 10_000,
    });
  });

  test("Create customer with custom fields (step 2)", async ({ page }) => {
    const name2 = `TestCust2 ${RUN_ID}`;
    const email2 = `testcust2-${RUN_ID}@example.com`;
    const idNumber = `E2E-ID-${RUN_ID}`;

    await loginAs(page, "alice");
    const dialog = await openNewClientDialog(page);

    await dialog.getByLabel("Name", { exact: true }).fill(name2);
    await dialog.getByLabel("Email", { exact: true }).fill(email2);
    await dialog.getByRole("button", { name: "Next" }).click();

    // Step 2: the legal-za "SA Legal — Client Details" intake field
    await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
    await dialog.getByLabel(/ID \/ Passport Number/).fill(idNumber);
    // Phase 87 S5 (ADR-344): label
    await dialog.getByRole("button", { name: "Create Client" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: name2, exact: true })).toBeVisible({
      timeout: 10_000,
    });

    // The custom field value was saved with the client
    const created = await clientByName(name2);
    expect(created, `client "${name2}" should exist`).toBeDefined();
    expect(created?.customFields?.id_passport_number).toBe(idNumber);
  });

  test("Edit customer name", async ({ page }) => {
    const target = await clientByName(CUSTOMER_NAME);
    expect(target, `client "${CUSTOMER_NAME}" created by test 1`).toBeDefined();

    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients/${target!.id}`);
    await expect(
      page.getByRole("heading", { level: 1, name: CUSTOMER_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    // Editing lives in the client overflow menu (the header action of a PROSPECT is "Start Onboarding")
    await page.getByTestId("client-overflow-trigger").click();
    await page.getByRole("menuitem", { name: "Edit Client" }).click();
    const dialog = page.getByRole("dialog", { name: "Edit Client" });
    await expect(dialog).toBeVisible();

    // Exact: "Contact Name" further down the form also contains "Name"
    const nameInput = dialog.getByRole("textbox", { name: "Name", exact: true });
    await nameInput.fill(EDITED_NAME);
    await dialog.getByRole("button", { name: "Save Changes" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    await page.reload();
    await expect(
      page.getByRole("heading", { level: 1, name: EDITED_NAME, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });
  });

  test("Search customer list", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { level: 1, name: "Clients" })).toBeVisible({
      timeout: 10_000,
    });

    // Role queries skip the hidden copy of the page a re-render can leave in the DOM
    await page
      .getByRole("search", { name: "Search clients" })
      .getByRole("textbox")
      .fill(`Edited ${RUN_ID}`);
    await expect(page).toHaveURL(new RegExp(`[?&]search=Edited`));
    await expect(page.getByRole("link", { name: EDITED_NAME, exact: true })).toBeVisible({
      timeout: 10_000,
    });
    // The search narrows the list: this run's other client is filtered out
    await expect(page.getByRole("link", { name: `TestCust2 ${RUN_ID}`, exact: true })).toHaveCount(
      0
    );
  });

  test("Client list renders rows", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { level: 1, name: "Clients" })).toBeVisible({
      timeout: 10_000,
    });

    // The seed client and this run's clients exist, so the table (not the empty state) renders
    await expect(page.getByTestId("client-row").filter({ visible: true }).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByRole("link", { name: "Acme Corp", exact: true })).toBeVisible();
    await expect(page.getByTestId("clients-footer").filter({ visible: true })).toContainText(
      /Showing \d+/
    );
  });
});
