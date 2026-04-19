import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component under test.
const mockApprove = vi.fn();
const mockReject = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  approveDisbursementAction: (...args: unknown[]) => mockApprove(...args),
  rejectDisbursementAction: (...args: unknown[]) => mockReject(...args),
}));

import { DisbursementApprovalPanel } from "@/components/legal/disbursement-approval-panel";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

function makeDisbursement(
  overrides: Partial<DisbursementResponse> = {}
): DisbursementResponse {
  return {
    id: "d1",
    projectId: "p1",
    customerId: "c1",
    category: "SHERIFF_FEES",
    description: "Sheriff service of summons",
    amount: 500,
    vatTreatment: "ZERO_RATED_PASS_THROUGH",
    vatAmount: 0,
    paymentSource: "OFFICE_ACCOUNT",
    trustTransactionId: null,
    incurredDate: "2026-02-12",
    supplierName: "Sheriff Sandton",
    supplierReference: null,
    receiptDocumentId: null,
    approvalStatus: "PENDING_APPROVAL",
    approvedBy: null,
    approvedAt: null,
    approvalNotes: null,
    billingStatus: "UNBILLED",
    invoiceLineId: null,
    writeOffReason: null,
    createdBy: "u1",
    createdAt: "2026-02-12T10:00:00Z",
    updatedAt: "2026-02-12T10:00:00Z",
    ...overrides,
  };
}

describe("DisbursementApprovalPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApprove.mockResolvedValue({ success: true, data: makeDisbursement({ approvalStatus: "APPROVED" }) });
    mockReject.mockResolvedValue({ success: true, data: makeDisbursement({ approvalStatus: "REJECTED" }) });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders approve/reject buttons when user has capability and status is PENDING_APPROVAL", () => {
    const disbursement = makeDisbursement();
    render(
      <DisbursementApprovalPanel
        slug="test-org"
        disbursement={disbursement}
        canApprove={true}
      />
    );

    expect(screen.getByTestId("disbursement-approval-panel")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-approve-button")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-reject-button")).toBeInTheDocument();
  });

  it("renders nothing when user lacks the approve capability", () => {
    const disbursement = makeDisbursement();
    const { container } = render(
      <DisbursementApprovalPanel
        slug="test-org"
        disbursement={disbursement}
        canApprove={false}
      />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when disbursement is not PENDING_APPROVAL", () => {
    const disbursement = makeDisbursement({ approvalStatus: "APPROVED" });
    const { container } = render(
      <DisbursementApprovalPanel
        slug="test-org"
        disbursement={disbursement}
        canApprove={true}
      />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("approves happy path — calls action and invokes onApproved", async () => {
    const user = userEvent.setup();
    const onApproved = vi.fn();
    const disbursement = makeDisbursement();

    render(
      <DisbursementApprovalPanel
        slug="test-org"
        disbursement={disbursement}
        canApprove={true}
        onApproved={onApproved}
      />
    );

    await user.click(screen.getByTestId("disbursement-approve-button"));
    await waitFor(() =>
      expect(screen.getByTestId("disbursement-approve-dialog")).toBeInTheDocument()
    );
    await user.click(screen.getByTestId("disbursement-confirm-approve-button"));

    await waitFor(() => {
      expect(mockApprove).toHaveBeenCalledWith("test-org", "d1", "");
    });
    expect(onApproved).toHaveBeenCalled();
  });

  it("requires notes when rejecting — blocks submit until reason entered", async () => {
    const user = userEvent.setup();
    const onRejected = vi.fn();
    const disbursement = makeDisbursement();

    render(
      <DisbursementApprovalPanel
        slug="test-org"
        disbursement={disbursement}
        canApprove={true}
        onRejected={onRejected}
      />
    );

    await user.click(screen.getByTestId("disbursement-reject-button"));
    await waitFor(() =>
      expect(screen.getByTestId("disbursement-reject-dialog")).toBeInTheDocument()
    );

    // Submit without notes — action should NOT be called.
    await user.click(screen.getByTestId("disbursement-confirm-reject-button"));
    // Give react-hook-form a tick to run validation.
    await waitFor(() => {
      expect(mockReject).not.toHaveBeenCalled();
    });

    // Enter a reason and submit.
    await user.type(
      screen.getByTestId("disbursement-reject-notes-input"),
      "Missing receipt"
    );
    await user.click(screen.getByTestId("disbursement-confirm-reject-button"));

    await waitFor(() => {
      expect(mockReject).toHaveBeenCalledWith("test-org", "d1", "Missing receipt");
    });
    expect(onRejected).toHaveBeenCalled();
  });
});
