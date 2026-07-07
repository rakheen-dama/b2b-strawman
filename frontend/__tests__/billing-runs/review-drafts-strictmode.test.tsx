/**
 * LZKC-006 reproduction: React StrictMode double-mounts the ReviewDraftsStep
 * generate-on-mount effect. Before the fix, the `hasGenerated` ref was only set
 * AFTER the `cancelled` early-return, so the first (cancelled) mount's successful
 * generate never marked the ref and the remount issued a SECOND generate — which
 * the backend rejects with "Only billing runs in PREVIEW status can be generated.
 * Current status: COMPLETED", dead-ending the wizard.
 *
 * Also covers the production-reachable variant (review finding): the wizard
 * unmounts this step on Back, so a Back → Next remount is a brand-new instance
 * whose generate hits the same backend rejection — it must load the existing
 * drafts instead of dead-ending.
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
  it("generates exactly once across the StrictMode mount/cleanup/remount cycle and only loads drafts after generation settles", async () => {
    // Mirror the backend: the first generate succeeds (run goes PREVIEW →
    // COMPLETED) after a short delay; any subsequent generate is rejected with
    // the PREVIEW-status error. getItems reflects the run's REAL state — no
    // drafts exist until generation has settled — so a remount that reloads
    // items before the in-flight generate completes would render an empty
    // table instead of the drafts.
    let generationCompleted = false;
    mockGenerate.mockImplementation(() => {
      if (mockGenerate.mock.calls.length > 1) {
        return Promise.resolve({ success: false, error: PREVIEW_ONLY_ERROR });
      }
      return new Promise((resolve) =>
        setTimeout(() => {
          generationCompleted = true;
          resolve({ success: true, billingRun: { id: "run-sm-1" } });
        }, 20)
      );
    });
    mockGetItems.mockImplementation(() =>
      Promise.resolve(
        generationCompleted
          ? { success: true, items: strictModeItems }
          : { success: true, items: [] }
      )
    );

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

    // The wizard must reach the drafts table, not the dead-end error screen
    // and not a prematurely-loaded empty table.
    await waitFor(() => {
      expect(screen.getByText("StrictMode Attorneys")).toBeInTheDocument();
    });
    expect(screen.queryByText(PREVIEW_ONLY_ERROR)).not.toBeInTheDocument();
    expect(mockGenerate).toHaveBeenCalledTimes(1);
  });

  it("recovers when a fresh remount re-generates an already-generated run (Back → Next)", async () => {
    // The wizard conditionally renders this step, so Back fully unmounts it.
    // A later Next mounts a NEW instance whose generate call hits the
    // backend's PREVIEW-status rejection — the step must treat that as
    // "already generated" and load the existing drafts.
    mockGenerate.mockImplementation(() => {
      if (mockGenerate.mock.calls.length === 1) {
        return Promise.resolve({ success: true, billingRun: { id: "run-sm-3" } });
      }
      return Promise.resolve({ success: false, error: PREVIEW_ONLY_ERROR });
    });
    mockGetItems.mockResolvedValue({ success: true, items: strictModeItems });

    const first = render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-sm-3"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );
    await waitFor(() => {
      expect(screen.getByText("StrictMode Attorneys")).toBeInTheDocument();
    });
    first.unmount();

    render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-sm-3"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );
    await waitFor(() => {
      expect(screen.getByText("StrictMode Attorneys")).toBeInTheDocument();
    });
    expect(screen.queryByText(PREVIEW_ONLY_ERROR)).not.toBeInTheDocument();
    // Fresh instance did re-call generate (per-instance guard cannot prevent
    // that) but the rejection was handled as already-generated.
    expect(mockGenerate).toHaveBeenCalledTimes(2);
  });

  it("keeps retry possible when generation genuinely fails", async () => {
    // Without StrictMode: a real generation failure must surface the error
    // (and not be swallowed by the already-generated fallback).
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
