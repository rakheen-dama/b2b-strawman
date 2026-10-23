import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig, type Cache } from "swr";

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
import {
  UnbilledDisbursementsSummary,
  unbilledDisbursementsSummaryKey,
} from "@/components/legal/unbilled-disbursements-summary";
import { formatCurrency } from "@/lib/format";
import type {
  DisbursementResponse,
  UnbilledDisbursementsResponse,
} from "@/lib/api/legal-disbursements";

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

  describe("evicts the matter's cached unbilled strip (Phase 88 621C)", () => {
    // Production shape: the strip is NOT mounted on the detail page. It lives on the matter tab /
    // firm list and its last total sits in the shared SWR cache. A mutation here must evict that
    // entry, or the strip paints the stale total when the user navigates back (observed on :3001:
    // approve → back → R 150,00 then R 2 150,00).
    const key = unbilledDisbursementsSummaryKey("p1");

    function summary(totalAmount: number): UnbilledDisbursementsResponse {
      return {
        matterId: "p1",
        currency: "ZAR",
        items: [
          {
            id: "d1",
            incurredDate: "2026-10-01",
            category: "SHERIFF_FEES",
            description: "Sheriff fee",
            amount: totalAmount,
            vatTreatment: "ZERO_RATED_PASS_THROUGH",
            vatAmount: 0,
            supplierName: "Sheriff",
          },
        ],
        totalAmount,
        totalVat: 0,
      };
    }
    const STALE = summary(150);
    const FRESH = summary(2150);

    function Harness({
      cache,
      disbursement,
      canApprove,
      page,
    }: {
      cache: Map<string, unknown>;
      disbursement: DisbursementResponse;
      canApprove: boolean;
      page: "matter-tab" | "detail";
    }) {
      return (
        <SWRConfig value={{ provider: () => cache as Cache, dedupingInterval: 0 }}>
          {page === "matter-tab" ? (
            <UnbilledDisbursementsSummary matterId="p1" />
          ) : (
            <DisbursementDetailClient
              slug="test-org"
              disbursement={disbursement}
              canApprove={canApprove}
              canWriteOff={true}
            />
          )}
        </SWRConfig>
      );
    }

    const totalText = () => screen.queryByTestId("unbilled-disbursements-total")?.textContent;

    /** Matter tab paints STALE into the shared cache, then the user opens the detail page. */
    async function openDetailAfterStripCached(
      disbursement: DisbursementResponse,
      canApprove = false
    ) {
      const cache = new Map<string, unknown>();
      mockFetchUnbilled.mockResolvedValueOnce(STALE).mockResolvedValue(FRESH);
      const props = { cache, disbursement, canApprove };
      const view = render(<Harness {...props} page="matter-tab" />);
      await waitFor(() => expect(totalText()).toBe(formatCurrency(STALE.totalAmount, "ZAR")));
      view.rerender(<Harness {...props} page="detail" />);
      await screen.findByTestId("disbursement-detail");
      mockFetchUnbilled.mockClear();
      // Browser back to the matter tab, sharing the same SWR cache.
      const navigateBack = () => view.rerender(<Harness {...props} page="matter-tab" />);
      return { cache, navigateBack };
    }

    async function expectStripNeverStale(navigateBack: () => void) {
      navigateBack();
      // First paint after navigating back: no stale total (the evicted entry has no data).
      expect(totalText()).toBeUndefined();
      await waitFor(() => expect(totalText()).toBe(formatCurrency(FRESH.totalAmount, "ZAR")));
      expect(mockFetchUnbilled).toHaveBeenCalledTimes(1);
      expect(mockFetchUnbilled).toHaveBeenCalledWith("p1");
    }

    it("after approve", async () => {
      const user = userEvent.setup();
      mockApprove.mockResolvedValue({ success: true, data: makeDisbursement() });
      const { cache, navigateBack } = await openDetailAfterStripCached(
        makeDisbursement({
          approvalStatus: "PENDING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
        }),
        true
      );

      await user.click(screen.getByTestId("disbursement-approve-button"));
      await user.click(await screen.findByTestId("disbursement-confirm-approve-button"));

      await waitFor(() => expect(mockApprove).toHaveBeenCalled());
      await waitFor(() => expect(cacheData(cache)).toBeUndefined());
      expect(mockFetchUnbilled).not.toHaveBeenCalled();
      await expectStripNeverStale(navigateBack);
    });

    it("after write-off", async () => {
      const user = userEvent.setup();
      mockWriteOff.mockResolvedValue({ success: true, data: writtenOff });
      const { cache, navigateBack } = await openDetailAfterStripCached(makeDisbursement());

      await user.click(screen.getByTestId("disbursement-write-off-button"));
      await user.type(await screen.findByRole("textbox", { name: /reason/i }), "Client dispute");
      await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));

      await screen.findByTestId("disbursement-restore-button");
      await waitFor(() => expect(cacheData(cache)).toBeUndefined());
      await expectStripNeverStale(navigateBack);
    });

    it("after restore, but not after a failed restore", async () => {
      const user = userEvent.setup();
      mockRestore
        .mockResolvedValueOnce({ success: false, error: "Disbursement is not written off" })
        .mockResolvedValueOnce({ success: true, data: makeDisbursement() });
      const { cache, navigateBack } = await openDetailAfterStripCached(writtenOff);

      await user.click(screen.getByTestId("disbursement-restore-button"));
      await screen.findByRole("alert");
      expect(cacheData(cache)).toEqual(STALE);

      await user.click(screen.getByTestId("disbursement-restore-button"));
      await screen.findByTestId("disbursement-write-off-button");
      await waitFor(() => expect(cacheData(cache)).toBeUndefined());
      await expectStripNeverStale(navigateBack);
    });

    function cacheData(cache: Map<string, unknown>): unknown {
      return (cache.get(key) as { data?: unknown } | undefined)?.data;
    }
  });
});
