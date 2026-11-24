import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({
  savePosition: vi.fn(),
  validateBatch: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { TrustPositionForm } from "@/components/take-on/trust-position-form";
import { trustBatch } from "./fixtures";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

/** The X3 position input labels (§17.10), the accessible names of the inputs. */
const LABELS = {
  bank: "Bank statement balance at cutover",
  deposits: "Outstanding deposits at cutover",
  payments: "Outstanding payments at cutover",
  cashbook: "Cashbook balance at cutover",
  fund: "Fund interest and charges ledger at cutover",
  invested: "Total invested (per the institutions' confirmations)",
  midMonth: "This is a mid-month cutover",
};

const FULL_REQUEST = {
  cutoverDate: "2026-10-31",
  bankBalance: 418500,
  outstandingDepositsTotal: 5000,
  outstandingPaymentsTotal: 20000,
  cashbookBalance: 403500,
  fundLedgerBalance: 1250,
  investedTotal: 300000,
  midMonthAcknowledged: false,
};

async function retype(label: string, value: string) {
  const user = userEvent.setup();
  const input = screen.getByLabelText(label);
  await user.clear(input);
  if (value) await user.type(input, value);
  return user;
}

describe("TrustPositionForm (657B, P-11, §17.10 X3)", () => {
  it("prefills the stored position under the X3 labels", () => {
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={vi.fn()} />);
    expect(screen.getByTestId("take-on-position-form")).toBeInTheDocument();
    expect(screen.getByLabelText("Cutover date")).toHaveValue("2026-10-31");
    expect(screen.getByLabelText(LABELS.bank)).toHaveValue("418500.00");
    expect(screen.getByLabelText(LABELS.deposits)).toHaveValue("5000.00");
    expect(screen.getByLabelText(LABELS.payments)).toHaveValue("20000.00");
    expect(screen.getByLabelText(LABELS.cashbook)).toHaveValue("403500.00");
    expect(screen.getByLabelText(LABELS.fund)).toHaveValue("1250.00");
    expect(screen.getByLabelText(LABELS.invested)).toHaveValue("300000.00");
    expect(screen.getByLabelText(LABELS.midMonth)).toHaveAttribute("aria-checked", "false");
    // "Fund" in full on its first use on the screen (X3 terms).
    expect(
      screen.getByText(/The Legal Practitioners' Fidelity Fund interest and charges ledger/)
    ).toBeInTheDocument();
  });

  it("saves every field, re-runs the dry run when the batch came back Mapped and hands on the validated batch", async () => {
    const mapped = trustBatch({ status: "MAPPED", summary: null, validatedAt: null });
    const validated = trustBatch({ validatedAt: "2026-11-24T10:00:00Z" });
    actions.savePosition.mockResolvedValue({ success: true, data: mapped });
    actions.validateBatch.mockResolvedValue({ success: true, data: validated });
    const onBatchChange = vi.fn();
    const user = userEvent.setup();
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={onBatchChange} />);

    await user.click(screen.getByTestId("take-on-position-save"));

    await waitFor(() => expect(onBatchChange).toHaveBeenCalledWith(validated));
    expect(actions.savePosition).toHaveBeenCalledWith("batch-657b", FULL_REQUEST);
    expect(actions.validateBatch).toHaveBeenCalledWith("batch-657b");
  });

  it("does not re-run the dry run when the position did not change (the batch stays Validated)", async () => {
    const same = trustBatch();
    actions.savePosition.mockResolvedValue({ success: true, data: same });
    const onBatchChange = vi.fn();
    const user = userEvent.setup();
    render(<TrustPositionForm batch={same} onBatchChange={onBatchChange} />);

    await user.click(screen.getByTestId("take-on-position-save"));

    await waitFor(() => expect(onBatchChange).toHaveBeenCalledWith(same));
    expect(actions.validateBatch).not.toHaveBeenCalled();
  });

  it("accepts 418 500,00 with a space or a no-break space and a comma, and sends 418500", async () => {
    actions.savePosition.mockResolvedValue({ success: true, data: trustBatch() });
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={vi.fn()} />);
    await retype(LABELS.bank, "418 500,00");
    await retype(LABELS.cashbook, "403 500,00");
    const user = userEvent.setup();
    await user.click(screen.getByTestId("take-on-position-save"));

    await waitFor(() => expect(actions.savePosition).toHaveBeenCalledTimes(1));
    expect(actions.savePosition.mock.calls[0][1]).toMatchObject({
      bankBalance: 418500,
      cashbookBalance: 403500,
    });
  });

  it("refuses a negative outstanding deposits total and a blank required figure before saving", async () => {
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={vi.fn()} />);
    await retype(LABELS.deposits, "-5000");
    await retype(LABELS.bank, "");
    const user = userEvent.setup();
    await user.click(screen.getByTestId("take-on-position-save"));

    expect(await screen.findByText("This amount cannot be negative.")).toBeInTheDocument();
    expect(screen.getByText("Enter the bank statement balance at cutover.")).toBeInTheDocument();
    expect(actions.savePosition).not.toHaveBeenCalled();
  });

  it("refuses an amount with two separators or three decimals", async () => {
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={vi.fn()} />);
    await retype(LABELS.payments, "20.000,00");
    await retype(LABELS.fund, "1250.005");
    const user = userEvent.setup();
    await user.click(screen.getByTestId("take-on-position-save"));

    expect(
      await screen.findAllByText("Enter an amount in rand and cents, for example 418 500,00.")
    ).toHaveLength(2);
    expect(actions.savePosition).not.toHaveBeenCalled();
  });

  it("shows the backend's refusal verbatim", async () => {
    const detail =
      "Outstanding deposits, outstanding payments and the total invested cannot be negative.";
    actions.savePosition.mockResolvedValue({ success: false, error: detail });
    const onBatchChange = vi.fn();
    const user = userEvent.setup();
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={onBatchChange} />);

    await user.click(screen.getByTestId("take-on-position-save"));

    expect(await screen.findByTestId("take-on-position-error")).toHaveTextContent(detail);
    expect(onBatchChange).not.toHaveBeenCalled();
    expect(actions.validateBatch).not.toHaveBeenCalled();
  });

  it("sends a blank Fund ledger and total invested as R0,00, and the mid-month tick", async () => {
    actions.savePosition.mockResolvedValue({ success: true, data: trustBatch() });
    render(<TrustPositionForm batch={trustBatch()} onBatchChange={vi.fn()} />);
    await retype(LABELS.fund, "");
    await retype(LABELS.invested, "");
    const user = userEvent.setup();
    await user.click(screen.getByLabelText(LABELS.midMonth));
    await user.click(screen.getByTestId("take-on-position-save"));

    await waitFor(() => expect(actions.savePosition).toHaveBeenCalledTimes(1));
    expect(actions.savePosition.mock.calls[0][1]).toMatchObject({
      fundLedgerBalance: 0,
      investedTotal: 0,
      midMonthAcknowledged: true,
    });
  });

  it("is read-only once the batch is no longer open", () => {
    render(
      <TrustPositionForm batch={trustBatch({ allowedActions: [] })} onBatchChange={vi.fn()} />
    );
    expect(screen.getByLabelText(LABELS.bank)).toBeDisabled();
    expect(screen.queryByTestId("take-on-position-save")).not.toBeInTheDocument();
  });
});
