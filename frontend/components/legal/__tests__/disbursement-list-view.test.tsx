import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DisbursementListView } from "@/components/legal/disbursement-list-view";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

function makeDisbursement(overrides: Partial<DisbursementResponse> = {}): DisbursementResponse {
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
    approvalStatus: "DRAFT",
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

describe("DisbursementListView", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders an empty state when no disbursements are provided", () => {
    render(<DisbursementListView disbursements={[]} />);

    expect(screen.getByText("No disbursements found.")).toBeInTheDocument();
    expect(screen.queryByTestId("disbursement-list")).not.toBeInTheDocument();
  });

  it("renders one row per disbursement with supplier, category and amount", () => {
    const rows = [
      makeDisbursement({ id: "d1", supplierName: "Sheriff Sandton" }),
      makeDisbursement({
        id: "d2",
        supplierName: "Adv. Jane Doe",
        category: "COUNSEL_FEES",
        amount: 2000,
        vatAmount: 300,
      }),
    ];

    render(<DisbursementListView disbursements={rows} />);

    expect(screen.getByTestId("disbursement-list")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-row-d1")).toBeInTheDocument();
    expect(screen.getByTestId("disbursement-row-d2")).toBeInTheDocument();
    expect(screen.getByText("Sheriff Sandton")).toBeInTheDocument();
    expect(screen.getByText("Adv. Jane Doe")).toBeInTheDocument();
    expect(screen.getByText("Sheriff Fees")).toBeInTheDocument();
    expect(screen.getByText("Counsel Fees")).toBeInTheDocument();
  });

  it("calls onSelect when a row is clicked", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const row = makeDisbursement({ id: "row-1" });

    render(<DisbursementListView disbursements={[row]} onSelect={onSelect} />);

    await user.click(screen.getByTestId("disbursement-row-row-1"));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(row);
  });

  it("resolves matter name from projectNames map", () => {
    const row = makeDisbursement({ id: "d1", projectId: "p1" });
    render(
      <DisbursementListView
        disbursements={[row]}
        projectNames={{ p1: "Matter 2026/001" }}
      />
    );

    expect(screen.getByText("Matter 2026/001")).toBeInTheDocument();
  });

  it("hides the actions menu for APPROVED rows", () => {
    const approved = makeDisbursement({
      id: "d-approved",
      approvalStatus: "APPROVED",
    });

    render(
      <DisbursementListView
        disbursements={[approved]}
        onEdit={vi.fn()}
        onUploadReceipt={vi.fn()}
      />
    );

    // Actions dropdown trigger should not be rendered for APPROVED status
    expect(screen.queryByRole("button", { name: /Actions/i })).not.toBeInTheDocument();
  });
});
