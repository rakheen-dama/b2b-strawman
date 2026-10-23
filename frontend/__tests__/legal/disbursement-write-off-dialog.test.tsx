import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component under test.
const mockWriteOff = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  writeOffDisbursementAction: (...args: unknown[]) => mockWriteOff(...args),
}));

import {
  DisbursementWriteOffDialog,
  canOfferWriteOff,
} from "@/components/legal/disbursement-write-off-dialog";
import type {
  DisbursementApprovalStatus,
  DisbursementBillingStatus,
  DisbursementResponse,
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

describe("DisbursementWriteOffDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockWriteOff.mockResolvedValue({
      success: true,
      data: makeDisbursement({ billingStatus: "WRITTEN_OFF", writeOffReason: "Client dispute" }),
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("owns a 'Write off' trigger that opens the write-off dialog", async () => {
    const user = userEvent.setup();
    render(<DisbursementWriteOffDialog slug="test-org" disbursement={makeDisbursement()} />);

    const trigger = screen.getByTestId("disbursement-write-off-button");
    expect(trigger).toHaveAccessibleName(/write off/i);
    expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    await user.click(trigger);

    const dialog = await screen.findByRole("dialog", { name: /write[\s-]*off/i });
    expect(dialog).toHaveAttribute("data-testid", "disbursement-write-off-dialog");
    expect(screen.getByRole("textbox", { name: /reason/i })).toHaveAttribute(
      "data-testid",
      "disbursement-write-off-reason-input"
    );
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("requires a reason — empty or whitespace-only blocks the action", async () => {
    const user = userEvent.setup();
    render(<DisbursementWriteOffDialog slug="test-org" disbursement={makeDisbursement()} />);

    await user.click(screen.getByTestId("disbursement-write-off-button"));
    await screen.findByTestId("disbursement-write-off-dialog");

    await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));
    expect(await screen.findByText("Reason is required")).toBeInTheDocument();
    expect(mockWriteOff).not.toHaveBeenCalled();

    await user.type(screen.getByTestId("disbursement-write-off-reason-input"), "   ");
    await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));
    expect(await screen.findByText("Reason is required")).toBeInTheDocument();
    expect(mockWriteOff).not.toHaveBeenCalled();
  });

  it("confirm calls the write-off action, hands back the updated row and closes", async () => {
    const user = userEvent.setup();
    const onWrittenOff = vi.fn();
    render(
      <DisbursementWriteOffDialog
        slug="test-org"
        disbursement={makeDisbursement()}
        onWrittenOff={onWrittenOff}
      />
    );

    await user.click(screen.getByTestId("disbursement-write-off-button"));
    await user.type(
      await screen.findByTestId("disbursement-write-off-reason-input"),
      "Client dispute"
    );
    await user.click(screen.getByRole("button", { name: /^write off$/i, hidden: false }));

    await waitFor(() => {
      expect(mockWriteOff).toHaveBeenCalledWith("test-org", "d1", "Client dispute");
    });
    expect(onWrittenOff).toHaveBeenCalledWith(
      expect.objectContaining({ billingStatus: "WRITTEN_OFF", writeOffReason: "Client dispute" })
    );
    await waitFor(() => {
      expect(screen.queryByTestId("disbursement-write-off-dialog")).not.toBeInTheDocument();
    });
  });

  it("shows the action error and keeps the dialog open on failure", async () => {
    const user = userEvent.setup();
    mockWriteOff.mockResolvedValue({ success: false, error: "Cannot write off disbursement" });
    const onWrittenOff = vi.fn();
    render(
      <DisbursementWriteOffDialog
        slug="test-org"
        disbursement={makeDisbursement()}
        onWrittenOff={onWrittenOff}
      />
    );

    await user.click(screen.getByTestId("disbursement-write-off-button"));
    await user.type(await screen.findByTestId("disbursement-write-off-reason-input"), "Absorbed");
    await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));

    expect(await screen.findByRole("alert")).toHaveTextContent("Cannot write off disbursement");
    expect(screen.getByTestId("disbursement-write-off-dialog")).toBeInTheDocument();
    expect(onWrittenOff).not.toHaveBeenCalled();
  });
});

describe("canOfferWriteOff (A14 — the billing axis only)", () => {
  it.each<DisbursementApprovalStatus>(["DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED"])(
    "offers write-off for an UNBILLED %s row",
    (approvalStatus) => {
      expect(
        canOfferWriteOff(makeDisbursement({ approvalStatus, billingStatus: "UNBILLED" }))
      ).toBe(true);
    }
  );

  it.each<DisbursementBillingStatus>(["BILLED", "WRITTEN_OFF"])(
    "does not offer write-off for a %s row",
    (billingStatus) => {
      expect(canOfferWriteOff(makeDisbursement({ billingStatus }))).toBe(false);
    }
  );
});
