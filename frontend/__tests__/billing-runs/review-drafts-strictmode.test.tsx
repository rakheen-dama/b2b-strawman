/**
 * LZKC-006 reproduction: React StrictMode double-mounts the ReviewDraftsStep
 * generate-on-mount effect. Before the fix, the `hasGenerated` ref was only set
 * AFTER the `cancelled` early-return, so the first (cancelled) mount's successful
 * generate never marked the ref and the remount issued a SECOND generate — which
 * the backend rejects with "Only billing runs in PREVIEW status can be generated.
 * Current status: COMPLETED", dead-ending the wizard.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { StrictMode } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

// Mock server actions — assign to const BEFORE vi.mock
const mockGenerate = vi.fn();
const mockGetItems = vi.fn();

vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-step-actions", () => ({
  generateAction: (...args: unknown[]) => mockGenerate(...args),
  getItemsAction: (...args: unknown[]) => mockGetItems(...args),
  batchApproveAction: vi.fn(),
  batchSendAction: vi.fn(),
  getBillingRunAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  updateInvoice: vi.fn().mockResolvedValue({ success: true }),
}));

import { ReviewDraftsStep } from "@/components/billing-runs/review-drafts-step";
import type { BillingRunItem } from "@/lib/api/billing-runs";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const PREVIEW_ONLY_ERROR =
  "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED";

const strictModeItems: BillingRunItem[] = [
  {
    id: "item-sm-1",
    customerId: "cust-sm-1",
    customerName: "StrictMode Attorneys",
    status: "GENERATED",
    unbilledTimeAmount: 50000,
    unbilledExpenseAmount: 5000,
    unbilledTimeCount: 3,
    unbilledExpenseCount: 1,
    totalUnbilledAmount: 55000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: "inv-sm-12345678",
    failureReason: null,
  },
];

describe("ReviewDraftsStep under StrictMode (LZKC-006)", () => {
  it("generates exactly once across the StrictMode mount/cleanup/remount cycle", async () => {
    // Mirror the backend: the first generate succeeds (run goes PREVIEW → COMPLETED);
    // any subsequent generate is rejected with the PREVIEW-status error.
    mockGenerate.mockImplementation(() => {
      if (mockGenerate.mock.calls.length === 1) {
        return Promise.resolve({ success: true, billingRun: { id: "run-sm-1" } });
      }
      return Promise.resolve({ success: false, error: PREVIEW_ONLY_ERROR });
    });
    mockGetItems.mockResolvedValue({ success: true, items: strictModeItems });

    render(
      <StrictMode>
        <ReviewDraftsStep
          slug="test-org"
          billingRunId="run-sm-1"
          currency="ZAR"
          onBack={vi.fn()}
          onNext={vi.fn()}
        />
      </StrictMode>
    );

    // The wizard must reach the drafts table, not the dead-end error screen.
    await waitFor(() => {
      expect(screen.getByText("StrictMode Attorneys")).toBeInTheDocument();
    });
    expect(screen.queryByText(PREVIEW_ONLY_ERROR)).not.toBeInTheDocument();
    expect(mockGenerate).toHaveBeenCalledTimes(1);
  });

  it("keeps retry possible when generation genuinely fails", async () => {
    // Without StrictMode: a real generation failure must surface the error
    // (and not be swallowed by the guard).
    mockGenerate.mockResolvedValue({
      success: false,
      error: "Failed to generate invoices for this run.",
    });
    mockGetItems.mockResolvedValue({ success: true, items: [] });

    render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-sm-2"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Failed to generate invoices for this run.")).toBeInTheDocument();
    });
    expect(mockGenerate).toHaveBeenCalledTimes(1);
    expect(mockGetItems).not.toHaveBeenCalled();
  });
});
