import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RetainerList } from "@/components/retainers/retainer-list";

const mockPauseRetainer = vi.fn();
const mockResumeRetainer = vi.fn();
const mockTerminateRetainer = vi.fn();

vi.mock("@/app/(app)/org/[slug]/retainers/actions", () => ({
  pauseRetainerAction: (...args: unknown[]) => mockPauseRetainer(...args),
  resumeRetainerAction: (...args: unknown[]) => mockResumeRetainer(...args),
  terminateRetainerAction: (...args: unknown[]) => mockTerminateRetainer(...args),
  createRetainerAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const ACTIVE_RETAINER = {
  id: "ret-1",
  customerId: "cust-1",
  scheduleId: null,
  customerName: "Acme Corp",
  name: "Monthly Bookkeeping Retainer",
  type: "HOUR_BANK" as const,
  status: "ACTIVE" as const,
  frequency: "MONTHLY" as const,
  startDate: "2026-01-01",
  endDate: null,
  allocatedHours: 40,
  periodFee: 5000,
  rolloverPolicy: "FORFEIT" as const,
  rolloverCapHours: null,
  notes: null,
  createdBy: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
  currentPeriod: {
    id: "per-1",
    periodStart: "2026-02-01",
    periodEnd: "2026-02-28",
    status: "OPEN" as const,
    allocatedHours: 40,
    baseAllocatedHours: 40,
    consumedHours: 25,
    remainingHours: 15,
    rolloverHoursIn: 0,
    overageHours: 0,
    rolloverHoursOut: 0,
    invoiceId: null,
    closedAt: null,
    closedBy: null,
    readyToClose: false,
  },
  recentPeriods: [],
};

const PAUSED_RETAINER = {
  ...ACTIVE_RETAINER,
  id: "ret-2",
  name: "Quarterly Tax Review Retainer",
  customerName: "Beta Inc",
  type: "FIXED_FEE" as const,
  status: "PAUSED" as const,
  frequency: "QUARTERLY" as const,
  allocatedHours: null,
  currentPeriod: null,
};

const TERMINATED_RETAINER = {
  ...ACTIVE_RETAINER,
  id: "ret-3",
  name: "Old Advisory Retainer",
  customerName: "Gamma LLC",
  status: "TERMINATED" as const,
  currentPeriod: null,
};

describe("RetainerList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders retainer rows with agreement name and customer name", () => {
    render(<RetainerList slug="acme" retainers={[ACTIVE_RETAINER]} />);
    expect(screen.getByText("Monthly Bookkeeping Retainer")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("shows Active status badge with success variant", () => {
    render(<RetainerList slug="acme" retainers={[ACTIVE_RETAINER]} />);
    const badge = screen.getByText("Active", { selector: "[data-slot='badge']" });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("shows Paused status badge with warning variant on Paused tab", async () => {
    const user = userEvent.setup();
    render(
      <RetainerList
        slug="acme"
        retainers={[ACTIVE_RETAINER, PAUSED_RETAINER]}
      />,
    );
    // Switch to Paused tab to see paused retainer
    const pausedTab = screen.getByRole("button", { name: "Paused" });
    await user.click(pausedTab);
    const badge = screen.getByText("Paused", { selector: "[data-slot='badge']" });
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("shows progress bar for HOUR_BANK type retainer", () => {
    render(<RetainerList slug="acme" retainers={[ACTIVE_RETAINER]} />);
    expect(screen.getByText("25.0h")).toBeInTheDocument();
    expect(screen.getByText("40.0h")).toBeInTheDocument();
  });

  it("shows empty state when no retainers match filter", () => {
    render(<RetainerList slug="acme" retainers={[]} />);
    expect(screen.getByText("No retainers found.")).toBeInTheDocument();
  });

  it("shows Pause button for ACTIVE retainer but not for TERMINATED", async () => {
    const user = userEvent.setup();
    render(
      <RetainerList
        slug="acme"
        retainers={[ACTIVE_RETAINER, TERMINATED_RETAINER]}
      />,
    );
    // Switch to All tab
    await user.click(screen.getByText("All"));
    expect(screen.getByTitle("Pause retainer")).toBeInTheDocument();
    // Terminated retainer should not have pause button - there's only one
    const pauseButtons = screen.getAllByTitle("Pause retainer");
    expect(pauseButtons).toHaveLength(1);
  });

  it("filters to only ACTIVE retainers on Active tab by default", () => {
    render(
      <RetainerList
        slug="acme"
        retainers={[ACTIVE_RETAINER, PAUSED_RETAINER, TERMINATED_RETAINER]}
      />,
    );
    expect(screen.getByText("Monthly Bookkeeping Retainer")).toBeInTheDocument();
    expect(screen.queryByText("Quarterly Tax Review Retainer")).not.toBeInTheDocument();
    expect(screen.queryByText("Old Advisory Retainer")).not.toBeInTheDocument();
  });

  it("confirming pause calls pauseRetainerAction", async () => {
    mockPauseRetainer.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<RetainerList slug="acme" retainers={[ACTIVE_RETAINER]} />);
    await user.click(screen.getByTitle("Pause retainer"));
    await user.click(screen.getByRole("button", { name: "Pause Retainer" }));
    await waitFor(() => {
      expect(mockPauseRetainer).toHaveBeenCalledWith("acme", "ret-1");
    });
  });
});
