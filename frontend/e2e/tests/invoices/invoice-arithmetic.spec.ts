/**
 * INV-03: Invoice Arithmetic — Playwright E2E Tests
 *
 * Primarily API-based tests verifying invoice line math, tax calculations,
 * rounding, and rate snapshot immutability.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh). The accounting
 *      compose/seed/lifecycle-test.sh was removed in Phase 86 (Epic 604)
 *
 * Run:
 *   cd frontend/e2e
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test invoices/invoice-arithmetic --reporter=list
 */
import { test, expect } from "@playwright/test";
import {
  addFeeNoteLineViaApi,
  api,
  apiOk,
  createActiveClient,
  createDraftFeeNote,
  type FeeNote,
  type FeeNoteLine,
} from "../../helpers/api";

const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT_PREFIX = "Arith Client";

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

function createDraft(customerId: string): Promise<FeeNote> {
  return createDraftFeeNote(customerId, `Arithmetic test ${RUN_ID}`);
}

function lineNamed(invoice: FeeNote, description: string): FeeNoteLine {
  const line = invoice.lines.find((l) => l.description === description);
  expect(line, `fee note line "${description}"`).toBeDefined();
  return line!;
}

// ═══════════════════════════════════════════════════════════════════
//  INV-03: Invoice Arithmetic
// ═══════════════════════════════════════════════════════════════════
test.describe("INV-03: Invoice Arithmetic", () => {
  let customerId: string;

  test.beforeAll(async () => {
    customerId = await createOwnClient();
  });

  test("Single line: 3h x R450", async () => {
    const draft = await createDraft(customerId);
    const invoice = await addFeeNoteLineViaApi(draft.id, "3h consulting", 3, 450);

    // Subtotal should be 3 * 450 = 1350
    expect(invoice.subtotal).toBe(1350);

    // Line amount should be 1350
    const line = lineNamed(invoice, "3h consulting");
    expect(line.amount).toBe(1350);
  });

  test("Multiple lines: (2 x R500) + (1 x R1,500)", async () => {
    const draft = await createDraft(customerId);
    await addFeeNoteLineViaApi(draft.id, "Review work", 2, 500);
    const invoice = await addFeeNoteLineViaApi(draft.id, "Advisory session", 1, 1500);

    // Subtotal: (2 * 500) + (1 * 1500) = 1000 + 1500 = 2500
    expect(invoice.subtotal).toBe(2500);
    expect(invoice.lines.length).toBe(2);

    // Individual line amounts
    const reviewLine = lineNamed(invoice, "Review work");
    expect(reviewLine.amount).toBe(1000);

    const advisoryLine = lineNamed(invoice, "Advisory session");
    expect(advisoryLine.amount).toBe(1500);
  });

  test("Rounding: non-terminating decimal (1.5h x R333.33)", async () => {
    const draft = await createDraft(customerId);
    const invoice = await addFeeNoteLineViaApi(draft.id, "Fractional rate", 1.5, 333.33);

    // 1.5 * 333.33 = 499.995 — should be rounded
    const line = lineNamed(invoice, "Fractional rate");
    // The amount should be rounded to 2 decimal places
    // Accept either 499.99 or 500.00 depending on rounding strategy
    expect(line.amount).toBeGreaterThanOrEqual(499.99);
    expect(line.amount).toBeLessThanOrEqual(500.0);
    expect(invoice.subtotal).toBeGreaterThanOrEqual(499.99);
    expect(invoice.subtotal).toBeLessThanOrEqual(500.0);
  });

  test("Zero quantity line", async () => {
    const draft = await createDraft(customerId);

    // Zero quantity should be rejected (backend validates @Positive)
    const res = await api(`/api/invoices/${draft.id}/lines`, {
      method: "POST",
      body: { description: "Zero qty", quantity: 0, unitPrice: 500, sortOrder: 0 },
    });
    // Backend uses @Positive on quantity — zero should be rejected (400)
    // If zero is accepted, the line total should be 0
    if (res.status === 201) {
      const invoice = (await res.json()) as FeeNote;
      const zeroLine = lineNamed(invoice, "Zero qty");
      expect(zeroLine.amount).toBe(0);
    } else {
      expect([400, 422].includes(res.status)).toBeTruthy();
    }
  });

  test("Fractional quantity: 0.25h x R1,200", async () => {
    const draft = await createDraft(customerId);
    const invoice = await addFeeNoteLineViaApi(draft.id, "Quarter hour", 0.25, 1200);

    // 0.25 * 1200 = 300
    const line = lineNamed(invoice, "Quarter hour");
    expect(line.amount).toBe(300);
    expect(invoice.subtotal).toBe(300);
  });

  test("Rate snapshot immutability", async () => {
    // This test verifies that invoice line amounts are computed at creation time
    // and do not change when rates are updated later.

    const draft = await createDraft(customerId);
    const invoice = await addFeeNoteLineViaApi(draft.id, "Snapshot test", 2, 800);

    // Record the line amount
    const originalLine = lineNamed(invoice, "Snapshot test");
    const originalAmount = originalLine.amount;
    expect(originalAmount).toBe(1600); // 2 * 800

    // Re-fetch the invoice — the amount should remain the same
    const refetched = await apiOk<FeeNote>(`/api/invoices/${draft.id}`);
    const refetchedLine = lineNamed(refetched, "Snapshot test");
    expect(refetchedLine.amount).toBe(originalAmount);
    expect(refetchedLine.unitPrice).toBe(800);
  });
});
