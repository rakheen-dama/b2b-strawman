import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactElement } from "react";

const mockFetchApproved = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchApprovedTrustDisbursementPayments: (...args: unknown[]) => mockFetchApproved(...args),
}));

import { TrustTransactionLinkDialog } from "@/components/legal/trust-transaction-link-dialog";
import type { TrustTransaction } from "@/lib/types";

// Each render gets a fresh SWR cache so test order / shared keys don't leak data
// between cases (e.g. "populated list" test bleeding into "empty state" test).
function renderWithFreshSWR(element: ReactElement) {
  return render(<SWRConfig value={{ provider: () => new Map() }}>{element}</SWRConfig>);
}

function makeTx(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx1",
    trustAccountId: "ta1",
    // The ledger has no DISBURSEMENT_PAYMENT type — a disbursement is paid from trust by a PAYMENT.
    transactionType: "PAYMENT",
    amount: 1500,
    clientId: "c1",
    matterId: "p1",
    counterpartyClientId: null,
    invoiceId: null,
    reference: "REF-001",
    description: "Payment to sheriff",
    transactionDate: "2026-03-01",
    status: "APPROVED",
    approvedBy: "u1",
    approvedAt: "2026-03-01T10:00:00Z",
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    bankStatementLineId: null,
    recordedBy: "u1",
    createdAt: "2026-03-01T10:00:00Z",
    ...overrides,
  };
}

describe("TrustTransactionLinkDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Drop queued *Once results so a failing case can't leak them into the next one.
    mockFetchApproved.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("lists approved PAYMENT transactions for the matter", async () => {
    mockFetchApproved.mockResolvedValue([
      makeTx({ id: "tx1", reference: "REF-001", amount: 1500 }),
      makeTx({ id: "tx2", reference: "REF-002", amount: 2500 }),
    ]);

    renderWithFreshSWR(
      <TrustTransactionLinkDialog
        open={true}
        onOpenChange={() => {}}
        projectId="p1"
        onSelect={() => {}}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-table")).toBeInTheDocument();
    });
    expect(mockFetchApproved).toHaveBeenCalledWith("p1");
    expect(screen.getByText("REF-001")).toBeInTheDocument();
    expect(screen.getByText("REF-002")).toBeInTheDocument();
    // formatCurrency uses en-ZA locale; the exact separators vary by ICU version
    // (e.g. "R 1,500.00" on newer ICU, "R 1 500,00" on older). Match both.
    expect(screen.getByText(/R[\s\u00A0]*1[\s,\u00A0]?500[.,]00/)).toBeInTheDocument();
    expect(screen.getByText(/R[\s\u00A0]*2[\s,\u00A0]?500[.,]00/)).toBeInTheDocument();
  });

  it("renders empty-state message when no trust transactions are returned", async () => {
    mockFetchApproved.mockResolvedValue([]);

    renderWithFreshSWR(
      <TrustTransactionLinkDialog
        open={true}
        onOpenChange={() => {}}
        projectId="p1"
        onSelect={() => {}}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-empty")).toBeInTheDocument();
    });
    expect(screen.getByTestId("trust-tx-empty")).toHaveTextContent(
      /No approved trust payments on this matter yet/
    );
    expect(
      screen.getByText("Select an approved trust payment on this matter.")
    ).toBeInTheDocument();
  });

  it("re-open shows the fresh list, never the cached one (a just-linked payment is not offered)", async () => {
    const tx = makeTx({ id: "tx1", reference: "REF-001", amount: 1500 });
    mockFetchApproved.mockResolvedValueOnce([tx]);

    // One cache shared across the close / re-open, like the app-wide SWR cache, with SWR's default
    // 2 s dedupe window (a quick re-open must still fetch).
    const cache = new Map();
    const withCache = (open: boolean) => (
      <SWRConfig value={{ provider: () => cache }}>
        <TrustTransactionLinkDialog
          open={open}
          onOpenChange={() => {}}
          projectId="p1"
          onSelect={() => {}}
        />
      </SWRConfig>
    );

    const { rerender } = render(withCache(true));
    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-row-tx1")).toBeInTheDocument();
    });

    rerender(withCache(false));
    await waitFor(() => {
      expect(screen.queryByTestId("trust-transaction-link-dialog")).not.toBeInTheDocument();
    });

    // tx1 has since been linked to a disbursement — the server no longer returns it.
    let resolveFresh: (rows: TrustTransaction[]) => void = () => {};
    mockFetchApproved.mockReturnValueOnce(
      new Promise<TrustTransaction[]>((resolve) => {
        resolveFresh = resolve;
      })
    );

    rerender(withCache(true));
    // While the fresh list is in flight the stale cached row must not be offered.
    expect(screen.getByTestId("trust-tx-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("trust-tx-row-tx1")).not.toBeInTheDocument();
    expect(mockFetchApproved).toHaveBeenCalledTimes(2);

    resolveFresh([]);
    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-empty")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("trust-tx-row-tx1")).not.toBeInTheDocument();
  });

  it("a new dialog instance (the next Create Disbursement session) fetches fresh too", async () => {
    const tx = makeTx({ id: "tx1", reference: "REF-001", amount: 1500 });
    mockFetchApproved.mockResolvedValueOnce([tx]);

    // The create dialog unmounts the link dialog with its own content; the app-wide cache stays.
    const cache = new Map();
    const first = render(
      <SWRConfig value={{ provider: () => cache }}>
        <TrustTransactionLinkDialog
          open={true}
          onOpenChange={() => {}}
          projectId="p1"
          onSelect={() => {}}
        />
      </SWRConfig>
    );
    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-row-tx1")).toBeInTheDocument();
    });
    first.unmount();

    mockFetchApproved.mockResolvedValueOnce([]);
    render(
      <SWRConfig value={{ provider: () => cache }}>
        <TrustTransactionLinkDialog
          open={true}
          onOpenChange={() => {}}
          projectId="p1"
          onSelect={() => {}}
        />
      </SWRConfig>
    );
    expect(screen.getByTestId("trust-tx-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("trust-tx-row-tx1")).not.toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-empty")).toBeInTheDocument();
    });
    expect(mockFetchApproved).toHaveBeenCalledTimes(2);
  });

  it("invokes onSelect with the chosen transaction when user clicks Link Transaction", async () => {
    const user = userEvent.setup();
    const tx = makeTx({ id: "tx1", reference: "REF-001", amount: 1500 });
    mockFetchApproved.mockResolvedValue([tx]);

    const onSelect = vi.fn();
    renderWithFreshSWR(
      <TrustTransactionLinkDialog
        open={true}
        onOpenChange={() => {}}
        projectId="p1"
        onSelect={onSelect}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId("trust-tx-row-tx1")).toBeInTheDocument();
    });

    // Initially the confirm button is disabled; selecting a row enables it.
    const confirmBtn = screen.getByTestId("trust-tx-link-confirm");
    expect(confirmBtn).toBeDisabled();

    await user.click(screen.getByTestId("trust-tx-radio-tx1"));
    expect(confirmBtn).not.toBeDisabled();

    await user.click(confirmBtn);
    expect(onSelect).toHaveBeenCalledWith(tx);
  });
});
