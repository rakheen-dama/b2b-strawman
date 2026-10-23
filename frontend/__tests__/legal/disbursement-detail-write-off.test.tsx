import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRTestProvider } from "@/lib/swr/test-utils";

// Mock server actions BEFORE importing the component under test.
const mockWriteOff = vi.fn();
const mockRestore = vi.fn();
const mockApprove = vi.fn();
const mockFetchUnbilled = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  writeOffDisbursementAction: (...args: unknown[]) => mockWriteOff(...args),
  restoreDisbursementAction: (...args: unknown[]) => mockRestore(...args),
  submitForApprovalAction: vi.fn(),
  uploadReceiptAction: vi.fn(),
  updateDisbursementAction: vi.fn(),
  approveDisbursementAction: (...args: unknown[]) => mockApprove(...args),
  rejectDisbursementAction: vi.fn(),
  fetchUnbilledDisbursementsAction: (...args: unknown[]) => mockFetchUnbilled(...args),
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/actions", () => ({ getDownloadUrl: vi.fn() }));

import { DisbursementDetailClient } from "@/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client";
import { UnbilledDisbursementsSummary } from "@/components/legal/unbilled-disbursements-summary";
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

  describe("revalidates the matter's unbilled strip (Phase 88 621C)", () => {
    // The strip lives on the matter tab / firm list, not here — but a mutation on this page must
    // invalidate its shared SWR key so the strip is fresh when the user navigates back. Mounting
    // it alongside makes the revalidation observable.
    function renderWithStrip(disbursement: DisbursementResponse, canApprove = false) {
      mockFetchUnbilled.mockResolvedValue({
        matterId: "p1",
        currency: "ZAR",
        items: [],
        totalAmount: 0,
        totalVat: 0,
      });
      render(
        <SWRTestProvider>
          <DisbursementDetailClient
            slug="test-org"
            disbursement={disbursement}
            canApprove={canApprove}
            canWriteOff={true}
          />
          <UnbilledDisbursementsSummary matterId="p1" />
        </SWRTestProvider>
      );
    }

    async function stripLoaded() {
      await screen.findByTestId("unbilled-disbursements-summary");
      expect(mockFetchUnbilled).toHaveBeenCalledTimes(1);
    }

    it("after approve", async () => {
      const user = userEvent.setup();
      mockApprove.mockResolvedValue({ success: true, data: makeDisbursement() });
      renderWithStrip(
        makeDisbursement({
          approvalStatus: "PENDING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
        }),
        true
      );
      await stripLoaded();

      await user.click(screen.getByTestId("disbursement-approve-button"));
      await user.click(await screen.findByTestId("disbursement-confirm-approve-button"));

      await waitFor(() => expect(mockApprove).toHaveBeenCalled());
      await waitFor(() => expect(mockFetchUnbilled).toHaveBeenCalledTimes(2));
      expect(mockFetchUnbilled).toHaveBeenLastCalledWith("p1");
    });

    it("after write-off", async () => {
      const user = userEvent.setup();
      mockWriteOff.mockResolvedValue({ success: true, data: writtenOff });
      renderWithStrip(makeDisbursement());
      await stripLoaded();

      await user.click(screen.getByTestId("disbursement-write-off-button"));
      await user.type(await screen.findByRole("textbox", { name: /reason/i }), "Client dispute");
      await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));

      await screen.findByTestId("disbursement-restore-button");
      await waitFor(() => expect(mockFetchUnbilled).toHaveBeenCalledTimes(2));
    });

    it("after restore, but not after a failed restore", async () => {
      const user = userEvent.setup();
      mockRestore
        .mockResolvedValueOnce({ success: false, error: "Disbursement is not written off" })
        .mockResolvedValueOnce({ success: true, data: makeDisbursement() });
      renderWithStrip(writtenOff);
      await stripLoaded();

      await user.click(screen.getByTestId("disbursement-restore-button"));
      await screen.findByRole("alert");
      expect(mockFetchUnbilled).toHaveBeenCalledTimes(1);

      await user.click(screen.getByTestId("disbursement-restore-button"));
      await screen.findByTestId("disbursement-write-off-button");
      await waitFor(() => expect(mockFetchUnbilled).toHaveBeenCalledTimes(2));
    });
  });
});
