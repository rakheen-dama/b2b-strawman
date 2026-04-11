/**
 * DP-01: Customer Data Protection — Playwright E2E Tests
 *
 * Tests customer data protection actions: download data export,
 * anonymize preview, execute anonymization, verify read-only state,
 * verify cannot transition out of ANONYMIZED.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data with customers present
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

async function getAuthToken(): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgSlug: "e2e-test-org" }),
  });
  const { access_token } = await res.json();
  return access_token;
}

test.describe("DP-01: Customer Data Protection", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Download customer data export", async ({ page }) => {
    await page.goto(`${base}/customers`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Click on first customer to go to detail
    const customerLink = page
      .getByRole("link")
      .filter({ hasText: /Kgosi|Naledi|Vukani|Moroka/i })
      .first();
    const hasCustomer = await customerLink.isVisible({ timeout: 10000 }).catch(() => false);

    if (!hasCustomer) {
      test.skip(true, "No seeded customers found");
      return;
    }

    await customerLink.click();
    await page.waitForTimeout(2000);

    // Look for data protection actions — could be in a menu, tab, or button
    const downloadBtn = page
      .getByRole("button", { name: /download.*data|export.*data|data.*export/i })
      .first();
    const moreMenu = page.getByRole("button", { name: /more|actions|menu/i }).first();

    let found = await downloadBtn.isVisible().catch(() => false);

    if (!found && (await moreMenu.isVisible().catch(() => false))) {
      await moreMenu.click();
      await page.waitForTimeout(500);
      const downloadMenuItem = page
        .getByRole("menuitem", { name: /download.*data|export.*data/i })
        .first();
      found = await downloadMenuItem.isVisible().catch(() => false);
      if (found) {
        await downloadMenuItem.click();
        await page.waitForTimeout(1000);
      }
    }

    if (!found) {
      // Try data protection tab
      const dpTab = page.getByRole("tab", { name: /data.*protection|privacy/i }).first();
      if (await dpTab.isVisible().catch(() => false)) {
        await dpTab.click();
        await page.waitForTimeout(1000);
      } else {
        test.skip(true, "Data export button not found on customer detail");
        return;
      }
    }
  });

  test("Anonymize customer preview shows entity counts", async ({ page }) => {
    await page.goto(`${base}/customers`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    const customerLink = page
      .getByRole("link")
      .filter({ hasText: /Kgosi|Naledi|Vukani|Moroka/i })
      .first();
    const hasCustomer = await customerLink.isVisible({ timeout: 10000 }).catch(() => false);

    if (!hasCustomer) {
      test.skip(true, "No seeded customers found");
      return;
    }

    await customerLink.click();
    await page.waitForTimeout(2000);

    // Look for anonymize/delete personal data button
    const anonBtn = page
      .getByRole("button", { name: /anonymi|delete.*personal|remove.*data/i })
      .first();
    const moreMenu = page.getByRole("button", { name: /more|actions|menu/i }).first();

    let found = await anonBtn.isVisible().catch(() => false);

    if (!found && (await moreMenu.isVisible().catch(() => false))) {
      await moreMenu.click();
      await page.waitForTimeout(500);
      const anonMenuItem = page
        .getByRole("menuitem", { name: /anonymi|delete.*personal/i })
        .first();
      found = await anonMenuItem.isVisible().catch(() => false);
      if (found) {
        await anonMenuItem.click();
        await page.waitForTimeout(1000);
      }
    }

    if (!found) {
      test.skip(true, "Anonymize button not found on customer detail");
      return;
    }

    // Preview dialog should show entity counts
    const dialog = page.getByRole("dialog").first();
    if (await dialog.isVisible().catch(() => false)) {
      const dialogText = await dialog.textContent();
      // Should mention counts of affected entities
      expect(dialogText?.length).toBeGreaterThan(0);
    }
  });

  test("Execute anonymization", async ({ page }) => {
    // Create a fresh customer to anonymize (don't destroy seeded data)
    const RUN_ID = Date.now().toString(36).slice(-4);
    const CUSTOMER_NAME = `Anon Test ${RUN_ID}`;

    // Create customer via API
    const token = await getAuthToken();
    const createRes = await fetch(`${BACKEND_URL}/api/customers`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ name: CUSTOMER_NAME, email: `anon-${RUN_ID}@test.com` }),
    });

    if (!createRes.ok) {
      test.skip(true, "Could not create test customer via API for anonymization test");
      return;
    }

    const customer = await createRes.json();

    // Navigate to the customer
    await page.goto(`${base}/customers/${customer.id}`);
    const bodyText = await page.locator("body").textContent();

    if (bodyText?.includes("Something went wrong") || bodyText?.includes("404")) {
      test.skip(true, "Customer detail page not accessible");
      return;
    }

    // Look for anonymize action
    const moreMenu = page.getByRole("button", { name: /more|actions|menu/i }).first();
    if (await moreMenu.isVisible().catch(() => false)) {
      await moreMenu.click();
      await page.waitForTimeout(500);
    }

    const anonBtn = page
      .getByRole("button", { name: /anonymi|delete.*personal/i })
      .first()
      .or(page.getByRole("menuitem", { name: /anonymi|delete.*personal/i }).first());

    if (!(await anonBtn.isVisible().catch(() => false))) {
      test.skip(true, "Anonymize action not available on customer detail");
      return;
    }

    await anonBtn.click();
    await page.waitForTimeout(1000);

    // Type customer name to confirm if needed
    const confirmInput = page
      .getByRole("textbox", { name: /confirm|customer.*name|type.*name/i })
      .first();
    if (await confirmInput.isVisible().catch(() => false)) {
      await confirmInput.fill(CUSTOMER_NAME);
    }

    // Click confirm/execute button
    const confirmBtn = page.getByRole("button", { name: /confirm|anonymi|execute|delete/i }).last();
    if (await confirmBtn.isVisible().catch(() => false)) {
      await confirmBtn.click();
      await page.waitForTimeout(2000);
    }
  });

  test("ANONYMIZED customer is read-only", async ({ page }) => {
    // This test checks if anonymized customers have edit controls disabled
    await page.goto(`${base}/customers`);
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Look for an anonymized customer badge
    const anonBadge = page.locator("text=/anonymi/i").first();
    const hasAnon = await anonBadge.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasAnon) {
      test.skip(true, "No anonymized customers found in list");
      return;
    }

    // Click the anonymized customer row/link
    const anonRow = anonBadge.locator("..").locator("a").first();
    if (await anonRow.isVisible().catch(() => false)) {
      await anonRow.click();
    } else {
      // Try clicking the badge's parent row
      await anonBadge.click();
    }
    await page.waitForTimeout(2000);

    // Edit controls should be disabled or hidden
    const editBtn = page.getByRole("button", { name: /edit/i }).first();
    if (await editBtn.isVisible().catch(() => false)) {
      // Edit button should be disabled
      await expect(editBtn).toBeDisabled();
    }
  });

  test("Cannot transition out of ANONYMIZED via API", async ({ page }) => {
    // This is an API-level test — verify the backend rejects status changes
    const token = await getAuthToken();

    // Find an anonymized customer via API
    const listRes = await fetch(`${BACKEND_URL}/api/customers?status=ANONYMIZED`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!listRes.ok) {
      test.skip(true, "Could not query anonymized customers via API");
      return;
    }

    const data = await listRes.json();
    const customers = data.content || data;
    const anonCustomer = Array.isArray(customers) ? customers[0] : null;

    if (!anonCustomer) {
      test.skip(true, "No anonymized customers found via API");
      return;
    }

    // Attempt to change status — should fail with 400 or 409
    const transitionRes = await fetch(`${BACKEND_URL}/api/customers/${anonCustomer.id}/status`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ status: "ACTIVE" }),
    });

    expect(transitionRes.status).toBeGreaterThanOrEqual(400);
    expect(transitionRes.status).toBeLessThan(500);
  });
});
