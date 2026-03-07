import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UtilizationTable } from "@/components/capacity/utilization-table";
import { UtilizationChart } from "@/components/capacity/utilization-chart";
import type { TeamUtilizationResponse } from "@/lib/api/capacity";

const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
  usePathname: () => "/org/test-org/resources/utilization",
}));

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  BarChart: ({
    children,
    data,
  }: {
    children: React.ReactNode;
    data: Array<Record<string, string | number>>;
  }) => (
    <div data-testid="bar-chart">
      {data?.map((item, i) => (
        <span key={i} data-testid={`bar-label-${i}`}>
          {item.label}
        </span>
      ))}
      {children}
    </div>
  ),
  Bar: ({ dataKey, fill }: { dataKey: string; fill: string }) => (
    <div data-testid={`bar-${dataKey}`} data-fill={fill} />
  ),
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: ({
    children,
    ...props
  }: {
    children?: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <div data-testid="legend" {...(props as Record<string, unknown>)}>
      {children}
    </div>
  ),
  ReferenceLine: () => <div data-testid="reference-line" />,
}));

function makeSampleData(): TeamUtilizationResponse {
  return {
    members: [
      {
        memberId: "m1",
        memberName: "Alice Smith",
        weeklyCapacity: 40,
        totalPlannedHours: 35,
        totalActualHours: 32,
        totalBillableHours: 28,
        avgPlannedUtilizationPct: 88,
        avgActualUtilizationPct: 80,
        avgBillableUtilizationPct: 70,
        overAllocatedWeeks: 1,
        weeks: [],
      },
      {
        memberId: "m2",
        memberName: "Bob Jones",
        weeklyCapacity: 40,
        totalPlannedHours: 45,
        totalActualHours: 42,
        totalBillableHours: 38,
        avgPlannedUtilizationPct: 113,
        avgActualUtilizationPct: 105,
        avgBillableUtilizationPct: 95,
        overAllocatedWeeks: 3,
        weeks: [],
      },
    ],
    teamAverages: {
      avgPlannedUtilizationPct: 100,
      avgActualUtilizationPct: 93,
      avgBillableUtilizationPct: 83,
    },
  };
}

const EMPTY_DATA: TeamUtilizationResponse = {
  members: [],
  teamAverages: {
    avgPlannedUtilizationPct: 0,
    avgActualUtilizationPct: 0,
    avgBillableUtilizationPct: 0,
  },
};

describe("UtilizationTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders member rows with utilization data", () => {
    render(<UtilizationTable data={makeSampleData()} slug="test-org" />);
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getAllByText("40h")).toHaveLength(2);
    expect(screen.getByText("32h")).toBeInTheDocument();
  });

  it("sorts by actual utilization descending by default", () => {
    render(<UtilizationTable data={makeSampleData()} slug="test-org" />);
    const rows = screen.getAllByRole("row");
    // Header + 2 data rows. Bob (105%) should be first (desc sort)
    expect(rows[1]).toHaveTextContent("Bob Jones");
    expect(rows[2]).toHaveTextContent("Alice Smith");
  });

  it("toggles sort direction on column click", async () => {
    const user = userEvent.setup();
    render(<UtilizationTable data={makeSampleData()} slug="test-org" />);

    // Click "Actual %" to toggle from desc to asc
    const actualBtn = screen.getByRole("button", { name: /Actual %/i });
    await user.click(actualBtn);

    const rows = screen.getAllByRole("row");
    // After toggling to asc, Alice (80%) should be first
    expect(rows[1]).toHaveTextContent("Alice Smith");
    expect(rows[2]).toHaveTextContent("Bob Jones");
  });

  it("renders colour-coded utilization badges", () => {
    render(<UtilizationTable data={makeSampleData()} slug="test-org" />);
    // Bob's planned util is 113% -> destructive badge
    expect(screen.getByText("113%")).toBeInTheDocument();
    // Alice's actual util is 80% -> warning badge
    expect(screen.getByText("80%")).toBeInTheDocument();
  });

  it("shows empty state when no utilization data", () => {
    render(<UtilizationTable data={EMPTY_DATA} slug="test-org" />);
    expect(
      screen.getByText("No utilization data available"),
    ).toBeInTheDocument();
  });
});

describe("UtilizationChart", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders bars for member data", () => {
    render(<UtilizationChart data={makeSampleData()} />);
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    expect(screen.getByTestId("bar-planned")).toBeInTheDocument();
    expect(screen.getByTestId("bar-actual")).toBeInTheDocument();
  });
});
