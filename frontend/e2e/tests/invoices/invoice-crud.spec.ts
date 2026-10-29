/**
 * INV-01: Invoice CRUD — Playwright E2E Tests
 *
 * Tests: create draft, add/edit/remove line items, verify totals.
 * Requires an ACTIVE customer (created via API).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test invoices/invoice-crud --reporter=list
 */
import { test, expect } from "@playwright/test";
import { createActiveClient } from "../../helpers/api";
import { loginAs } from "../../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT_PREFIX = "InvCrud Client";

async function getToken(user: "alice" | "bob" | "carol"): Promise<string> {
  const users = {
    alice: { userId: "user_e2e_alice", orgRole: "org:owner" },
    bob: { userId: "user_e2e_bob", orgRole: "org:admin" },
    carol: { userId: "user_e2e_carol", orgRole: "org:member" },
  };
  const u = users[user];
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...u, orgId: "org_e2e_test", orgSlug: ORG }),
  });
  const { access_token } = await res.json();
  return access_token;
}

/**
 * This file's own ACTIVE client (628F R-5): fee notes are never raised on the boot seed's Acme Corp,
 * whose fee notes the portal suite and REGRESSION read — and "the first ACTIVE client" is not
 * deterministic while sibling spec files create clients in parallel.
 */
async function createOwnClient(): Promise<string> {
  return createActiveClient(
    `${CLIENT_PREFIX} ${RUN_ID}`,
    `${CLIENT_PREFIX.toLowerCase().replace(/ /g, "-")}-${RUN_ID}@example.com`
  );
}

// Create a draft invoice via API
async function createDraftInvoice(
  customerId: string,
  token: string
): Promise<Record<string, unknown>> {
  const res = await fetch(`${BACKEND_URL}/api/invoices`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      // Phase 87 S3 (ADR-337): request body
      clientId: customerId,
      currency: "ZAR",
      timeEntryIds: [],
      notes: `E2E test invoice ${RUN_ID}`,
    }),
  });
  expect(res.status).toBe(201);
  return res.json();
}

// Add a line item via API
async function addLineItem(
  invoiceId: string,
  description: string,
  quantity: number,
  unitPrice: number,
  token: string
): Promise<Record<string, unknown>> {
  const res = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}/lines`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      description,
      quantity,
      unitPrice,
      sortOrder: 0,
    }),
  });
  expect(res.status).toBe(201);
  return res.json();
}

test.describe("INV-01: Invoice CRUD", () => {
  let activeCustomerId: string;
  let invoiceId: string;

  test.beforeAll(async () => {
    activeCustomerId = await createOwnClient();
  });

  test("Create draft invoice for customer", async ({ page }) => {
    const token = await getToken("alice");
    const invoice = await createDraftInvoice(activeCustomerId, token);
    invoiceId = invoice.id;

    // Verify in UI — navigate to invoice detail
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);

    // Should show "Draft" status
    await expect(page.getByText(/Draft/i).first()).toBeVisible({ timeout: 10000 });
  });

  test("Add line item to draft", async ({ page }) => {
    const token = await getToken("alice");
    const invoice = await addLineItem(invoiceId, "Consulting services", 3, 450, token);

    // Verify totals via API response
    expect(invoice.subtotal).toBeGreaterThan(0);

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    await expect(page.getByText("Consulting services").first()).toBeVisible({ timeout: 10000 });
  });

  test("Edit line item on draft", async ({ page }) => {
    const token = await getToken("alice");

    // Get current invoice to find line ID
    const getRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const invoice = await getRes.json();
    const line = invoice.lines[0];
    expect(line).toBeDefined();

    // Update the line item
    const updateRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}/lines/${line.id}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        description: "Updated consulting",
        quantity: 5,
        unitPrice: 500,
        sortOrder: 0,
      }),
    });
    expect(updateRes.ok).toBeTruthy();
    const updated = await updateRes.json();

    // Verify totals recalculated
    expect(updated.subtotal).toBe(2500);

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    await expect(page.getByText("Updated consulting").first()).toBeVisible({ timeout: 10000 });
  });

  test("Remove line item from draft", async ({ page }) => {
    const token = await getToken("alice");

    // Add a second line to then remove it
    await addLineItem(invoiceId, "Line to remove", 1, 100, token);

    // Get invoice with both lines
    const getRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const invoice = await getRes.json();
    const removeTarget = (invoice.lines as Array<{ description: string; id: string }>).find(
      (l) => l.description === "Line to remove"
    );
    expect(removeTarget).toBeDefined();

    const subtotalBefore = invoice.subtotal;

    // Delete the line
    const delRes = await fetch(
      `${BACKEND_URL}/api/invoices/${invoiceId}/lines/${removeTarget.id}`,
      {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      }
    );
    expect(delRes.status).toBe(204);

    // Verify totals updated
    const verifyRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const verified = await verifyRes.json();
    expect(verified.subtotal).toBeLessThan(subtotalBefore);

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    // The kept line renders (page hydrated) and the removed one is gone
    await expect(page.getByText("Updated consulting").first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText("Line to remove")).toHaveCount(0);
  });

  test("Draft invoice shows correct totals", async ({ page }) => {
    const token = await getToken("alice");

    // Create a clean invoice with known line items
    const freshInvoice = await createDraftInvoice(activeCustomerId, token);
    await addLineItem(freshInvoice.id, "Service A", 2, 500, token);
    await addLineItem(freshInvoice.id, "Service B", 1, 1500, token);

    // Verify totals via API: (2 * 500) + (1 * 1500) = 2500
    const getRes = await fetch(`${BACKEND_URL}/api/invoices/${freshInvoice.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const invoice = await getRes.json();
    expect(invoice.subtotal).toBe(2500);

    // Verify in UI
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${freshInvoice.id}`);
    await expect(page.getByText(/Draft/i).first()).toBeVisible({ timeout: 10000 });

    // Check that both line items are visible
    await expect(page.getByText("Service A").first()).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("Service B").first()).toBeVisible({ timeout: 5000 });
  });
});
