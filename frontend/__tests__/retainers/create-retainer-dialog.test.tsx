import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateRetainerDialog } from "@/components/retainers/create-retainer-dialog";

const mockCreateRetainer = vi.fn();

vi.mock("@/app/(app)/org/[slug]/retainers/actions", () => ({
  createRetainerAction: (...args: unknown[]) => mockCreateRetainer(...args),
  pauseRetainerAction: vi.fn(),
  resumeRetainerAction: vi.fn(),
  terminateRetainerAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const CUSTOMERS = [
  { id: "cust-1", name: "Acme Corp", email: "acme@example.com" },
  { id: "cust-2", name: "Beta Inc", email: "beta@example.com" },
];

function renderDialog() {
  return render(
    <CreateRetainerDialog slug="test-org" customers={CUSTOMERS}>
      <button>Open Create Retainer 126B</button>
    </CreateRetainerDialog>,
  );
}

describe("CreateRetainerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form fields when dialog is opened", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Create Retainer 126B"));

    await waitFor(() => {
      expect(screen.getByText("New Retainer")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Type" })).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: "Frequency" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Start Date")).toBeInTheDocument();
    expect(screen.getByText("Select a customer...")).toBeInTheDocument();
  });

  it("toggles HOUR_BANK fields when type is changed to FIXED_FEE", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Create Retainer 126B"));
    await waitFor(() => {
      expect(screen.getByText("New Retainer")).toBeInTheDocument();
    });

    // HOUR_BANK is default — allocated hours and rollover policy should be visible
    expect(screen.getByLabelText("Allocated Hours")).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: "Rollover Policy" }),
    ).toBeInTheDocument();

    // Switch to FIXED_FEE via Radix Select: click trigger, then click option
    await user.click(screen.getByRole("combobox", { name: "Type" }));
    await user.click(screen.getByRole("option", { name: "Fixed Fee" }));

    // HOUR_BANK fields should be gone
    expect(screen.queryByLabelText("Allocated Hours")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("combobox", { name: "Rollover Policy" }),
    ).not.toBeInTheDocument();
  });

  it("shows rolloverCapHours field when CARRY_CAPPED is selected", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Create Retainer 126B"));
    await waitFor(() => {
      expect(screen.getByText("New Retainer")).toBeInTheDocument();
    });

    // By default rollover cap should not be visible (FORFEIT is default)
    expect(
      screen.queryByLabelText("Rollover Cap (hours)"),
    ).not.toBeInTheDocument();

    // Change rollover policy to CARRY_CAPPED via Radix Select
    await user.click(
      screen.getByRole("combobox", { name: "Rollover Policy" }),
    );
    await user.click(
      screen.getByRole("option", { name: "Carry forward (capped)" }),
    );

    expect(screen.getByLabelText("Rollover Cap (hours)")).toBeInTheDocument();
  });

  it("calls createRetainerAction on submit with valid data", async () => {
    mockCreateRetainer.mockResolvedValue({ success: true, data: {} });
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Create Retainer 126B"));
    await waitFor(() => {
      expect(screen.getByText("New Retainer")).toBeInTheDocument();
    });

    // Select customer via combobox
    await user.click(screen.getByText("Select a customer..."));
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Acme Corp"));

    // Fill name
    await user.type(screen.getByLabelText("Name"), "Monthly Retainer");

    // Fill allocated hours and period fee (HOUR_BANK is default)
    await user.type(screen.getByLabelText("Allocated Hours"), "40");
    await user.type(screen.getByLabelText("Period Fee"), "5000");

    // Fill start date
    await user.type(screen.getByLabelText("Start Date"), "2026-03-01");

    // Submit
    await user.click(screen.getByText("Create Retainer"));

    await waitFor(() => {
      expect(mockCreateRetainer).toHaveBeenCalledWith("test-org", {
        customerId: "cust-1",
        name: "Monthly Retainer",
        type: "HOUR_BANK",
        frequency: "MONTHLY",
        startDate: "2026-03-01",
        allocatedHours: 40,
        periodFee: 5000,
        rolloverPolicy: "FORFEIT",
      });
    });
  });
});
