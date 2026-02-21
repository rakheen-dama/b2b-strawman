import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ClosePeriodDialog } from "@/components/retainers/close-period-dialog";
import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/retainers/[id]/actions", () => ({
  closePeriodAction: vi.fn(),
}));

const BASE_RETAINER: RetainerResponse = {
  id: "ret-1",
  customerId: "cust-1",
  scheduleId: null,
  customerName: "Acme Corp",
  name: "Monthly Bookkeeping Retainer",
  type: "HOUR_BANK",
  status: "ACTIVE",
  frequency: "MONTHLY",
  startDate: "2026-01-01",
  endDate: null,
  allocatedHours: 40,
  periodFee: 5000,
  rolloverPolicy: "FORFEIT",
  rolloverCapHours: null,
  notes: null,
  createdBy: "user-1",
  createdByName: "Test User",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
  currentPeriod: null,
  recentPeriods: [],
};

const PERIOD_WITH_OVERAGE: PeriodSummary = {
  id: "per-1",
  periodStart: "2026-02-01",
  periodEnd: "2026-02-28",
  status: "OPEN",
  allocatedHours: 40,
  baseAllocatedHours: 40,
  consumedHours: 48,
  remainingHours: 0,
  rolloverHoursIn: 0,
  overageHours: 8,
  rolloverHoursOut: 0,
  invoiceId: null,
  closedAt: null,
  closedBy: null,
  closedByName: null,
  readyToClose: true,
};

const FIXED_FEE_RETAINER: RetainerResponse = {
  ...BASE_RETAINER,
  id: "ret-2",
  name: "Fixed Fee Advisory",
  type: "FIXED_FEE",
  allocatedHours: null,
  rolloverPolicy: null,
  periodFee: 3000,
};

const FIXED_FEE_PERIOD: PeriodSummary = {
  id: "per-2",
  periodStart: "2026-02-01",
  periodEnd: "2026-02-28",
  status: "OPEN",
  allocatedHours: null,
  baseAllocatedHours: null,
  consumedHours: 12,
  remainingHours: null,
  rolloverHoursIn: 0,
  overageHours: null,
  rolloverHoursOut: 0,
  invoiceId: null,
  closedAt: null,
  closedBy: null,
  closedByName: null,
  readyToClose: true,
};

describe("ClosePeriodDialog", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows invoice preview with base fee and overage line", () => {
    render(
      <ClosePeriodDialog
        slug="acme"
        retainerId="ret-1"
        period={PERIOD_WITH_OVERAGE}
        retainer={BASE_RETAINER}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    // Base fee
    expect(screen.getByText("Base fee")).toBeInTheDocument();
    expect(screen.getByText("$5,000.00")).toBeInTheDocument();

    // Overage line
    expect(screen.getByText(/Overage.*8\.0h/)).toBeInTheDocument();
    expect(screen.getByText("Calculated at close")).toBeInTheDocument();

    // Overage warning banner
    expect(
      screen.getByText(/8\.0 overage hours recorded/),
    ).toBeInTheDocument();

    // Confirm button
    expect(
      screen.getByText("Close Period & Generate Invoice"),
    ).toBeInTheDocument();
  });

  it("renders without crashing for FIXED_FEE retainer with null hour fields", () => {
    render(
      <ClosePeriodDialog
        slug="acme"
        retainerId="ret-2"
        period={FIXED_FEE_PERIOD}
        retainer={FIXED_FEE_RETAINER}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    // Consumed hours should still show
    expect(screen.getByText("12.0h")).toBeInTheDocument();

    // Allocated Hours row should NOT appear (null)
    expect(screen.queryByText("Allocated Hours:")).not.toBeInTheDocument();

    // Overage warning should NOT appear (null overageHours)
    expect(screen.queryByText(/overage hours recorded/)).not.toBeInTheDocument();

    // Rollover preview should NOT appear
    expect(screen.queryByText(/roll over/)).not.toBeInTheDocument();

    // Base fee should show (appears in both "Base fee" line and "Estimated Total")
    expect(screen.getAllByText("$3,000.00").length).toBeGreaterThanOrEqual(1);

    // Confirm button should still work
    expect(
      screen.getByText("Close Period & Generate Invoice"),
    ).toBeInTheDocument();
  });
});
