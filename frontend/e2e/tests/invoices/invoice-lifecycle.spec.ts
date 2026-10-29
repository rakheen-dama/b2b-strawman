/**
 * INV-02: Invoice Lifecycle — Playwright E2E Tests
 *
 * Tests: DRAFT -> APPROVED -> SENT -> PAID (amount/method payment form, Phase 88 ADR-347), VOID,
 * edit restrictions, skip rejections.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test invoices/invoice-lifecycle --reporter=list
 */
import { test, expect, type Page } from "@playwright/test";
import {
  apiOk,
  createActiveClient,
  createMatter,
  createMatterRate,
  createTask,
  logTime,
  memberNamed,
} from "../../helpers/api";
import { loginAs } from "../../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT_PREFIX = "InvLife Client";

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

async function createDraftWithLine(
  token: string,
  customerId: string
): Promise<Record<string, unknown>> {
  // Create draft
  const draftRes = await fetch(`${BACKEND_URL}/api/invoices`, {
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
      notes: `Lifecycle test ${RUN_ID}`,
    }),
  });
  expect(draftRes.status).toBe(201);
  const draft = await draftRes.json();

  // Add a line item so approve succeeds
  const lineRes = await fetch(`${BACKEND_URL}/api/invoices/${draft.id}/lines`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      description: `Lifecycle line ${RUN_ID}`,
      quantity: 2,
      unitPrice: 750,
      sortOrder: 0,
    }),
  });
  expect(lineRes.status).toBe(201);
  return lineRes.json();
}

async function approveAndSend(token: string, invoiceId: string): Promise<void> {
  const approve = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}/approve`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(approve.ok, `approve ${invoiceId}: ${approve.status}`).toBeTruthy();
  const send = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}/send`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ overrideWarnings: false }),
  });
  expect(send.ok, `send ${invoiceId}: ${send.status} ${await send.text()}`).toBeTruthy();
}

/** Voids the open (SENT) fee note through the header button and its native confirm(). */
async function voidInUi(page: Page): Promise<void> {
  // Void confirms with window.confirm (components/invoices/use-invoice-detail.ts) — accept it
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Void", exact: true }).click();
  await expect(page.getByRole("button", { name: "Void", exact: true })).toHaveCount(0, {
    timeout: 15_000,
  });
  await expect(page.getByText("Void", { exact: true }).first()).toBeVisible();
}

async function timeEntry(taskId: string, entryId: string): Promise<{ invoiceId: string | null }> {
  const entries = await apiOk<{ id: string; invoiceId: string | null }[]>(
    `/api/tasks/${taskId}/time-entries`
  );
  const entry = entries.find((e) => e.id === entryId);
  expect(entry, `time entry ${entryId}`).toBeDefined();
  return entry!;
}

// ═══════════════════════════════════════════════════════════════════
//  INV-02: Invoice Lifecycle Transitions
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("INV-02: Invoice Lifecycle", () => {
  let customerId: string;
  let invoiceId: string;

  test.beforeAll(async () => {
    customerId = await createOwnClient();
  });

  test("DRAFT -> APPROVED", async ({ page }) => {
    const token = await getToken("alice");
    const invoice = await createDraftWithLine(token, customerId);
    invoiceId = invoice.id;

    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    await expect(page.getByText("Draft", { exact: true }).first()).toBeVisible({
      timeout: 10000,
    });

    // Click Approve button
    const approveBtn = page.getByRole("button", { name: "Approve" });
    await expect(approveBtn).toBeVisible({ timeout: 5000 });
    await approveBtn.click();
    // APPROVED offers Send fee note instead of Approve (invoice-header-actions.tsx)
    await expect(page.getByRole("button", { name: "Send fee note" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(approveBtn).toHaveCount(0);

    // Verify invoice number assigned (INV-XXXX)
    await expect(page.getByText(/INV-\d+/).first()).toBeVisible({ timeout: 10000 });
  });

  test("APPROVED -> SENT", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    await expect(page.getByText("Approved", { exact: true }).first()).toBeVisible({
      timeout: 10_000,
    });

    // Phase 87 S5 (ADR-344): label
    await page.getByRole("button", { name: "Send fee note" }).click();
    // Once SENT the header offers Record Payment and no longer Send (invoice-header-actions.tsx)
    await expect(page.getByRole("button", { name: "Record Payment" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("button", { name: "Send fee note" })).toHaveCount(0);
    await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible();

    const token = await getToken("alice");
    const res = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok).toBeTruthy();
    expect((await res.json()).status).toBe("SENT");
  });

  test("SENT -> PAID (record payment)", async ({ page }) => {
    // Verify the invoice is actually in SENT status via API first
    const token = await getToken("alice");

    // The previous test left the fee note SENT (serial chain)
    const invoiceRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(invoiceRes.ok).toBeTruthy();
    const invoice = await invoiceRes.json();
    expect(invoice.status).toBe("SENT");

    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoiceId}`);
    await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible({ timeout: 10000 });

    // Click "Record Payment" button (in the header actions)
    const paymentBtn = page.getByRole("button", { name: /Record Payment/i });
    await expect(paymentBtn).toBeVisible({ timeout: 5000 });
    await paymentBtn.click();

    // (Phase 88, ADR-347) The inline payment form records the money that moved: amount
    // (defaults to the balance due — the full total on a SENT fee note), method (defaults to
    // EFT) and an optional reference.
    const form = page.getByTestId("record-payment-form");
    await expect(form).toBeVisible({ timeout: 5000 });
    await expect(form.getByLabel("Amount", { exact: true })).toHaveValue(
      String(Number(invoice.total))
    );
    await expect(form.getByTestId("payment-method-select")).toHaveText("EFT");

    // Fill the optional payment reference
    const refInput = form.getByPlaceholder(/CHK-12345|Wire transfer/i);
    await expect(refInput).toBeVisible();
    await refInput.fill(`PAY-${RUN_ID}`);

    // Click "Confirm Payment" — records the default amount (full balance) by EFT
    const confirmBtn = form.getByRole("button", { name: /Confirm Payment/i });
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();
    await expect(form).toBeHidden({ timeout: 10000 });

    // Verify status shows Paid
    await page.reload();
    await expect(page.getByText("Paid", { exact: true }).first()).toBeVisible({ timeout: 10000 });

    // (Phase 88, ADR-347) A full payment marks PAID: amount_paid = total, balance due 0, one
    // EFT payment for the whole total carrying the reference.
    const paidRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(paidRes.ok).toBeTruthy();
    const paid = await paidRes.json();
    expect(paid.status).toBe("PAID");
    expect(Number(paid.amountPaid)).toBe(Number(paid.total));
    expect(Number(paid.balanceDue)).toBe(0);
    expect(paid.payments).toHaveLength(1);
    expect(paid.payments[0]).toMatchObject({
      paymentMethod: "EFT",
      paymentReference: `PAY-${RUN_ID}`,
    });
    expect(Number(paid.payments[0].amount)).toBe(Number(paid.total));
  });

  test("VOID a sent invoice", async ({ page }) => {
    const token = await getToken("alice");

    // Create a separate invoice for voiding, approved and sent via the API
    const invoice = await createDraftWithLine(token, customerId);
    await approveAndSend(token, invoice.id as string);

    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoice.id}`);
    await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible({ timeout: 10000 });
    await voidInUi(page);

    const res = await fetch(`${BACKEND_URL}/api/invoices/${invoice.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect((await res.json()).status).toBe("VOID");
  });

  test("VOID releases time entries", async ({ page }) => {
    // A fee note billed from logged time: voiding it returns the entry to unbilled
    const alice = await memberNamed("Alice");
    const matterId = await createMatter(`Void Matter ${RUN_ID}`, customerId);
    await createMatterRate(matterId, alice.id, 1500);
    const taskId = await createTask(matterId, `Void Task ${RUN_ID}`);
    const entryId = await logTime(taskId, 60, `Void release ${RUN_ID}`);

    const feeNote = await apiOk<{ id: string; status: string }>("/api/invoices", {
      method: "POST",
      body: { clientId: customerId, currency: "ZAR", timeEntryIds: [entryId] },
    });
    const token = await getToken("alice");
    await approveAndSend(token, feeNote.id);
    expect((await timeEntry(taskId, entryId)).invoiceId).toBe(feeNote.id);

    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${feeNote.id}`);
    await expect(page.getByText(`Void release ${RUN_ID}`).first()).toBeVisible({ timeout: 10_000 });
    await voidInUi(page);

    // The entry is unbilled again: no fee note linked
    await expect.poll(async () => (await timeEntry(taskId, entryId)).invoiceId).toBeNull();
  });

  test("Cannot edit approved invoice", async ({ page }) => {
    const token = await getToken("alice");

    // Create and approve an invoice
    const invoice = await createDraftWithLine(token, customerId);
    const approve = await fetch(`${BACKEND_URL}/api/invoices/${invoice.id}/approve`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(approve.ok).toBeTruthy();

    // The approved fee note is read-only in the UI: no draft actions
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices/${invoice.id}`);
    await expect(page.getByText("Approved", { exact: true }).first()).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByRole("button", { name: "Delete Draft" })).toHaveCount(0);

    // Attempt to add a line item via API — should fail
    const lineRes = await fetch(`${BACKEND_URL}/api/invoices/${invoice.id}/lines`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        description: "Should fail",
        quantity: 1,
        unitPrice: 100,
        sortOrder: 0,
      }),
    });
    // Approved invoices should reject line edits
    expect([400, 409, 422].includes(lineRes.status)).toBeTruthy();
  });

  test("Cannot skip DRAFT -> SENT", async () => {
    const token = await getToken("alice");
    const invoice = await createDraftWithLine(token, customerId);

    // Try to send without approving first
    const sendRes = await fetch(`${BACKEND_URL}/api/invoices/${invoice.id}/send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ overrideWarnings: true }),
    });
    expect([400, 409].includes(sendRes.status)).toBeTruthy();
  });

  test("Cannot transition PAID -> VOID", async () => {
    const token = await getToken("alice");

    // The main test invoice is now PAID — try to void it
    const voidRes = await fetch(`${BACKEND_URL}/api/invoices/${invoiceId}/void`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    // PAID invoices should not be voidable
    expect([400, 409].includes(voidRes.status)).toBeTruthy();
  });
});
