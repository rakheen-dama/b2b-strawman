import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component under test.
const mockWriteOff = vi.fn();
const mockRestore = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  writeOffDisbursementAction: (...args: unknown[]) => mockWriteOff(...args),
  restoreDisbursementAction: (...args: unknown[]) => mockRestore(...args),
  submitForApprovalAction: vi.fn(),
  uploadReceiptAction: vi.fn(),
  updateDisbursementAction: vi.fn(),
  approveDisbursementAction: vi.fn(),
  rejectDisbursementAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({ getDownloadUrl: vi.fn() }));

import { DisbursementDetailClient } from "@/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

function makeDisbursement(overrides: Partial<DisbursementResponse> = {}): DisbursementResponse {
  return {
    id: "d1",
    matterId: "p1",
    clientId: "c1",
    category: "SHERIFF_FEES",
    description: "Sheriff fee — service of process",
    amount: 750,
    vatTreatment: "ZERO_RATED_PASS_THROUGH",
    vatAmount: 0,
    paymentSource: "OFFICE_ACCOUNT",
    trustTransactionId: null,
    incurredDate: "2026-10-01",
    supplierName: "Sheriff Johannesburg North",
    supplierReference: null,
    receiptDocumentId: null,
    approvalStatus: "APPROVED",
    approvedBy: "u1",
    approvedAt: "2026-10-02T10:00:00Z",
    approvalNotes: null,
    billingStatus: "UNBILLED",
    invoiceLineId: null,
    writeOffReason: null,
    createdBy: "u1",
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    ...overrides,
  };
}

const writtenOff = makeDisbursement({
  billingStatus: "WRITTEN_OFF",
  writeOffReason: "Client dispute",
});

function renderDetail(disbursement: DisbursementResponse, canWriteOff = true) {
  render(
    <DisbursementDetailClient
      slug="test-org"
      disbursement={disbursement}
      canApprove={false}
      canWriteOff={canWriteOff}
    />
  );
  return screen.getByTestId("disbursement-detail");
}

describe("DisbursementDetailClient write-off / undo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("hides Write off and Undo without WRITE_OFF_DISBURSEMENTS", () => {
    renderDetail(makeDisbursement(), false);
    expect(screen.queryByTestId("disbursement-write-off-button")).not.toBeInTheDocument();
    cleanup();

    renderDetail(writtenOff, false);
    expect(screen.queryByTestId("disbursement-restore-button")).not.toBeInTheDocument();
  });

  it("write-off swaps in Undo, shows the reason and moves focus to Undo", async () => {
    const user = userEvent.setup();
    mockWriteOff.mockResolvedValue({ success: true, data: writtenOff });
    const detail = renderDetail(makeDisbursement());

    await user.click(within(detail).getByRole("button", { name: /write[\s-]*off/i }));
    await user.type(await screen.findByRole("textbox", { name: /reason/i }), "Client dispute");
    await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));

    const undo = await screen.findByTestId("disbursement-restore-button");
    expect(within(detail).getByText("Written off", { exact: true })).toBeInTheDocument();
    expect(detail).toHaveTextContent("Write-off reason");
    expect(screen.queryByTestId("disbursement-write-off-button")).not.toBeInTheDocument();
    await waitFor(() => expect(undo).toHaveFocus());
  });

  it("restore success puts the row back to Unbilled and moves focus to Write off", async () => {
    const user = userEvent.setup();
    mockRestore.mockResolvedValue({ success: true, data: makeDisbursement() });
    const detail = renderDetail(writtenOff);

    await user.click(screen.getByTestId("disbursement-restore-button"));

    expect(mockRestore).toHaveBeenCalledWith("test-org", "d1");
    const writeOff = await screen.findByTestId("disbursement-write-off-button");
    expect(within(detail).getByText("Unbilled", { exact: true })).toBeInTheDocument();
    expect(detail).not.toHaveTextContent("Write-off reason");
    expect(screen.queryByTestId("disbursement-restore-button")).not.toBeInTheDocument();
    await waitFor(() => expect(writeOff).toHaveFocus());
  });

  it("restore failure shows the error and keeps the row written off", async () => {
    const user = userEvent.setup();
    mockRestore.mockResolvedValue({ success: false, error: "Disbursement is not written off" });
    const detail = renderDetail(writtenOff);

    await user.click(screen.getByTestId("disbursement-restore-button"));

    expect(await screen.findByRole("alert")).toHaveTextContent("Disbursement is not written off");
    expect(within(detail).getByText("Written off", { exact: true })).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-restore-button")).toBeEnabled();
  });
});
