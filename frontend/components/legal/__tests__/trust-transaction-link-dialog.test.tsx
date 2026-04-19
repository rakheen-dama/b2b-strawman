import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactElement } from "react";

const mockFetchApproved = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchApprovedTrustDisbursementPayments: (...args: unknown[]) =>
    mockFetchApproved(...args),
}));

import { TrustTransactionLinkDialog } from "@/components/legal/trust-transaction-link-dialog";
import type { TrustTransaction } from "@/lib/types";

// Each render gets a fresh SWR cache so test order / shared keys don't leak data
// between cases (e.g. "populated list" test bleeding into "empty state" test).
function renderWithFreshSWR(element: ReactElement) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>{element}</SWRConfig>
  );
}

function makeTx(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx1",
    trustAccountId: "ta1",
    transactionType: "DISBURSEMENT_PAYMENT",
    amount: 1500,
    customerId: "c1",
    projectId: "p1",
    counterpartyCustomerId: null,
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
  });

  afterEach(() => {
    cleanup();
  });

  it("lists approved DISBURSEMENT_PAYMENT transactions for the project", async () => {
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
    expect(screen.getByText("R 1500.00")).toBeInTheDocument();
    expect(screen.getByText("R 2500.00")).toBeInTheDocument();
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

    await user.click(screen.getByTestId("trust-tx-row-tx1"));
    expect(confirmBtn).not.toBeDisabled();

    await user.click(confirmBtn);
    expect(onSelect).toHaveBeenCalledWith(tx);
  });
});
