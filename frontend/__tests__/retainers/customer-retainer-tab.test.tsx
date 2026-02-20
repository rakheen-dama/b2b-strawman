import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomerRetainerTab } from "@/components/customers/customer-retainer-tab";
import { PeriodHistoryTable } from "@/components/retainers/period-history-table";
import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
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
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
  currentPeriod: {
    id: "per-1",
    periodStart: "2026-02-01",
    periodEnd: "2026-02-28",
    status: "OPEN",
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

const CLOSED_PERIOD: PeriodSummary = {
  id: "per-2",
  periodStart: "2026-01-01",
  periodEnd: "2026-01-31",
  status: "CLOSED",
  allocatedHours: 40,
  baseAllocatedHours: 40,
  consumedHours: 36,
  remainingHours: 4,
  rolloverHoursIn: 0,
  overageHours: 0,
  rolloverHoursOut: 0,
  invoiceId: "inv-abc",
  closedAt: "2026-02-01T00:00:00Z",
  closedBy: "user-1",
  readyToClose: false,
};

const OPEN_PERIOD: PeriodSummary = {
  id: "per-1",
  periodStart: "2026-02-01",
  periodEnd: "2026-02-28",
  status: "OPEN",
  allocatedHours: 40,
  baseAllocatedHours: 40,
  consumedHours: 36,
  remainingHours: 4,
  rolloverHoursIn: 0,
  overageHours: 0,
  rolloverHoursOut: 0,
  invoiceId: null,
  closedAt: null,
  closedBy: null,
  readyToClose: true,
};

describe("CustomerRetainerTab", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders active retainer card with progress", () => {
    render(
      <CustomerRetainerTab
        retainer={BASE_RETAINER}
        allRetainers={[BASE_RETAINER]}
        periods={[]}
        slug="acme"
        customerId="cust-1"
        canManage={true}
      />,
    );

    expect(
      screen.getByText("Monthly Bookkeeping Retainer"),
    ).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    // RetainerProgress renders "25.0 of 40.0 hrs"
    expect(screen.getByText(/25\.0 of 40\.0 hrs/)).toBeInTheDocument();
  });

  it("renders empty state when no retainer", () => {
    render(
      <CustomerRetainerTab
        retainer={null}
        allRetainers={[]}
        periods={[]}
        slug="acme"
        customerId="cust-1"
        canManage={true}
      />,
    );

    expect(screen.getByText("No retainer agreement")).toBeInTheDocument();
    const setupLink = screen.getByRole("link", { name: "Set Up Retainer" });
    expect(setupLink).toHaveAttribute("href", "/org/acme/retainers");
  });

  it("renders period history table with open and closed periods", () => {
    render(
      <PeriodHistoryTable
        periods={[OPEN_PERIOD, CLOSED_PERIOD]}
        slug="acme"
      />,
    );

    expect(screen.getByText("Open")).toBeInTheDocument();
    expect(screen.getByText("Closed")).toBeInTheDocument();
  });

  it("renders invoice link in period history row", () => {
    render(
      <PeriodHistoryTable periods={[CLOSED_PERIOD]} slug="acme" />,
    );

    const invoiceLink = screen.getByRole("link", { name: "View Invoice" });
    expect(invoiceLink).toHaveAttribute(
      "href",
      "/org/acme/invoices/inv-abc",
    );
  });

  it("renders amber progress bar at 90% consumption", () => {
    const retainerAt90: RetainerResponse = {
      ...BASE_RETAINER,
      currentPeriod: {
        ...BASE_RETAINER.currentPeriod!,
        consumedHours: 36,
        remainingHours: 4,
      },
    };

    render(
      <CustomerRetainerTab
        retainer={retainerAt90}
        allRetainers={[retainerAt90]}
        periods={[]}
        slug="acme"
        customerId="cust-1"
        canManage={true}
      />,
    );

    const progressBar = screen.getByTestId("progress-bar");
    expect(progressBar.className).toContain("bg-amber-500");
  });
});
