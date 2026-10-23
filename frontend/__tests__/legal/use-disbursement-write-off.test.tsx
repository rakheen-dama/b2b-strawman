import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions + toast BEFORE importing the components under test.
const mockWriteOff = vi.fn();
const mockRestore = vi.fn();
const { mockToastSuccess, mockToastError } = vi.hoisted(() => ({
  mockToastSuccess: vi.fn(),
  mockToastError: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  writeOffDisbursementAction: (...args: unknown[]) => mockWriteOff(...args),
  restoreDisbursementAction: (...args: unknown[]) => mockRestore(...args),
}));
vi.mock("sonner", () => ({ toast: { success: mockToastSuccess, error: mockToastError } }));

import { CapabilityProvider } from "@/lib/capabilities";
import { DisbursementListView } from "@/components/legal/disbursement-list-view";
import { useDisbursementWriteOff } from "@/components/legal/use-disbursement-write-off";
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

function Harness({ rows, onChanged }: { rows: DisbursementResponse[]; onChanged: () => void }) {
  const writeOff = useDisbursementWriteOff({ slug: "test-org", onChanged });
  return (
    <>
      <DisbursementListView
        disbursements={rows}
        onWriteOff={writeOff.onWriteOff}
        onRestore={writeOff.onRestore}
      />
      {writeOff.dialog}
    </>
  );
}

function renderHarness(rows: DisbursementResponse[], capabilities = ["WRITE_OFF_DISBURSEMENTS"]) {
  const onChanged = vi.fn();
  render(
    <CapabilityProvider capabilities={capabilities} role="member" isAdmin={false} isOwner={false}>
      <Harness rows={rows} onChanged={onChanged} />
    </CapabilityProvider>
  );
  return { onChanged };
}

async function openWriteOffFromMenu(user: ReturnType<typeof userEvent.setup>, id: string) {
  await user.click(screen.getByTestId(`disbursement-actions-${id}`));
  await user.click(screen.getByTestId(`disbursement-write-off-item-${id}`));
  return screen.findByRole("dialog", { name: /write[\s-]*off/i });
}

describe("useDisbursementWriteOff (row-menu write-off / undo)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("offers no write-off actions without WRITE_OFF_DISBURSEMENTS", () => {
    renderHarness([makeDisbursement({ id: "d-nocap" })], []);
    expect(screen.queryByTestId("disbursement-actions-d-nocap")).not.toBeInTheDocument();
  });

  it("Escape closes the row-menu dialog, clears body pointer-events and refocuses the trigger", async () => {
    const user = userEvent.setup();
    renderHarness([makeDisbursement({ id: "d-esc" })]);

    await openWriteOffFromMenu(user, "d-esc");
    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(document.body.style.pointerEvents).toBe("");
    await waitFor(() => expect(screen.getByTestId("disbursement-actions-d-esc")).toHaveFocus());
  });

  it("confirmed write-off toasts, refetches and refocuses the trigger", async () => {
    const user = userEvent.setup();
    mockWriteOff.mockResolvedValue({
      success: true,
      data: makeDisbursement({ id: "d-ok", billingStatus: "WRITTEN_OFF" }),
    });
    const { onChanged } = renderHarness([makeDisbursement({ id: "d-ok" })]);

    const dialog = await openWriteOffFromMenu(user, "d-ok");
    expect(dialog).toHaveTextContent("Sheriff Johannesburg North");
    await user.type(screen.getByRole("textbox", { name: /reason/i }), "Client dispute");
    await user.click(screen.getByTestId("disbursement-write-off-confirm-button"));

    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    expect(mockWriteOff).toHaveBeenCalledWith("test-org", "d-ok", "Client dispute");
    expect(mockToastSuccess).toHaveBeenCalledWith("Disbursement written off");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(document.body.style.pointerEvents).toBe("");
    await waitFor(() => expect(screen.getByTestId("disbursement-actions-d-ok")).toHaveFocus());
  });

  it("Undo write-off toasts success and refetches", async () => {
    const user = userEvent.setup();
    mockRestore.mockResolvedValue({ success: true, data: makeDisbursement({ id: "d-ro" }) });
    const { onChanged } = renderHarness([
      makeDisbursement({ id: "d-ro", billingStatus: "WRITTEN_OFF", writeOffReason: "Absorbed" }),
    ]);

    await user.click(screen.getByTestId("disbursement-actions-d-ro"));
    await user.click(screen.getByTestId("disbursement-restore-item-d-ro"));

    await waitFor(() => expect(mockToastSuccess).toHaveBeenCalledWith("Write-off undone"));
    expect(mockRestore).toHaveBeenCalledWith("test-org", "d-ro");
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it("Undo write-off surfaces the action error without refetching", async () => {
    const user = userEvent.setup();
    mockRestore.mockResolvedValue({ success: false, error: "Disbursement is not written off" });
    const { onChanged } = renderHarness([
      makeDisbursement({ id: "d-rf", billingStatus: "WRITTEN_OFF" }),
    ]);

    await user.click(screen.getByTestId("disbursement-actions-d-rf"));
    await user.click(screen.getByTestId("disbursement-restore-item-d-rf"));

    await waitFor(() =>
      expect(mockToastError).toHaveBeenCalledWith("Disbursement is not written off")
    );
    expect(onChanged).not.toHaveBeenCalled();
  });

  it("Undo write-off that throws shows an error toast instead of an unhandled rejection", async () => {
    const user = userEvent.setup();
    mockRestore.mockRejectedValue(new Error("network down"));
    const { onChanged } = renderHarness([
      makeDisbursement({ id: "d-rt", billingStatus: "WRITTEN_OFF" }),
    ]);

    await user.click(screen.getByTestId("disbursement-actions-d-rt"));
    await user.click(screen.getByTestId("disbursement-restore-item-d-rt"));

    await waitFor(() =>
      expect(mockToastError).toHaveBeenCalledWith(
        "An unexpected error occurred while undoing the write-off"
      )
    );
    expect(onChanged).not.toHaveBeenCalled();
  });
});
