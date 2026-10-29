/**
 * INV-01: Invoice CRUD — Playwright E2E Tests
 *
 * Tests: a draft created via the API opens as a Draft; a line is added, edited and removed through
 * the fee-note line editor (Add Line / Edit / Delete); the draft's totals. Requires an ACTIVE client
 * (created via the API).
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
import { test, expect, type Page } from "@playwright/test";
import {
  addFeeNoteLineViaApi,
  apiOk,
  createActiveClient,
  createDraftFeeNote,
  type FeeNote,
} from "../../helpers/api";
import {
  addFeeNoteLine,
  feeNoteTotalsRow,
  openFeeNote,
  randPattern,
} from "../../helpers/legal-lifecycle";
import { loginAs } from "../../fixtures/auth";

const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT_PREFIX = "InvCrud Client";

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

/** The fee note's status badge (the header also has a "Delete Draft" button). */
function draftBadge(page: Page) {
  return page.getByText("Draft", { exact: true }).first();
}

/** The open fee note's line-table row for `description`. */
function lineRow(page: Page, description: string) {
  return page.locator("main").getByRole("row").filter({ hasText: description });
}

function getFeeNote(id: string): Promise<FeeNote> {
  return apiOk<FeeNote>(`/api/invoices/${id}`);
}

test.describe.serial("INV-01: Invoice CRUD", () => {
  let activeCustomerId: string;
  let invoiceId: string;

  test.beforeAll(async () => {
    activeCustomerId = await createOwnClient();
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Draft created via the API opens as a Draft", async ({ page }) => {
    invoiceId = (await createDraftFeeNote(activeCustomerId, `E2E test invoice ${RUN_ID}`)).id;

    await openFeeNote(page, invoiceId);
    await expect(draftBadge(page)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: "Delete Draft" })).toBeVisible();
  });

  test("Add line item to draft", async ({ page }) => {
    await openFeeNote(page, invoiceId);
    await addFeeNoteLine(page, {
      description: "Consulting services",
      quantity: "3",
      unitPrice: "450",
    });

    // Persisted: listed after a reload, and 3 × 450 on the fee note
    await page.reload();
    await expect(lineRow(page, "Consulting services")).toHaveCount(1, { timeout: 10_000 });
    const invoice = await getFeeNote(invoiceId);
    expect(invoice.lines).toHaveLength(1);
    expect(invoice.subtotal).toBe(1350);
  });

  test("Edit line item on draft", async ({ page }) => {
    await openFeeNote(page, invoiceId);
    await lineRow(page, "Consulting services")
      .getByRole("button", { name: "Edit Consulting services" })
      .click();
    // The editor has no role / test id and its labels have no htmlFor (product follow-up, see
    // helpers/legal-lifecycle.ts addFeeNoteLine): the innermost div holding its heading, inputs by type
    const editor = page
      .locator("main")
      .locator("div")
      .filter({ has: page.getByRole("heading", { level: 3, name: "Edit Line Item" }) })
      .last();
    await expect(editor).toBeVisible();
    await editor.locator('input[type="text"]').fill("Updated consulting");
    const numbers = editor.locator('input[type="number"]');
    await numbers.nth(0).fill("5");
    await numbers.nth(1).fill("500");
    await editor.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByRole("heading", { level: 3, name: "Edit Line Item" })).toBeHidden({
      timeout: 15_000,
    });

    // Persisted: the edited line after a reload, and the subtotal recalculated (5 × 500)
    await page.reload();
    await expect(lineRow(page, "Updated consulting")).toHaveCount(1, { timeout: 10_000 });
    await expect(lineRow(page, "Consulting services")).toHaveCount(0);
    expect((await getFeeNote(invoiceId)).subtotal).toBe(2500);
  });

  test("Remove line item from draft", async ({ page }) => {
    await openFeeNote(page, invoiceId);
    await addFeeNoteLine(page, { description: "Line to remove", quantity: "1", unitPrice: "100" });
    await expect.poll(async () => (await getFeeNote(invoiceId)).subtotal).toBe(2600);

    await lineRow(page, "Line to remove")
      .getByRole("button", { name: "Delete Line to remove" })
      .click();
    await expect(lineRow(page, "Line to remove")).toHaveCount(0, { timeout: 15_000 });

    // Persisted: after a reload the kept line renders and the removed one is gone
    await page.reload();
    await expect(lineRow(page, "Updated consulting")).toHaveCount(1, { timeout: 10_000 });
    await expect(lineRow(page, "Line to remove")).toHaveCount(0);
    const invoice = await getFeeNote(invoiceId);
    expect(invoice.lines.map((l) => l.description)).toEqual(["Updated consulting"]);
    expect(invoice.subtotal).toBe(2500);
  });

  test("Draft invoice shows correct totals", async ({ page }) => {
    // A clean draft with known lines: (2 × 500) + (1 × 1500) = 2500
    const fresh = await createDraftFeeNote(activeCustomerId, `E2E totals ${RUN_ID}`);
    await addFeeNoteLineViaApi(fresh.id, "Service A", 2, 500);
    const invoice = await addFeeNoteLineViaApi(fresh.id, "Service B", 1, 1500);
    expect(invoice.subtotal).toBe(2500);

    await openFeeNote(page, fresh.id);
    await expect(draftBadge(page)).toBeVisible({ timeout: 10_000 });
    await expect(lineRow(page, "Service A")).toHaveCount(1);
    await expect(lineRow(page, "Service B")).toHaveCount(1);
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("2 500,00"));
  });
});
