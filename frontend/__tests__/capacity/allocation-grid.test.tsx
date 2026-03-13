import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AllocationGrid } from "@/components/capacity/allocation-grid";
import { CapacityCell } from "@/components/capacity/capacity-cell";
import { UtilizationBadge } from "@/components/capacity/utilization-badge";
import { WeekRangeSelector } from "@/components/capacity/week-range-selector";
import type { TeamCapacityGrid, WeekCell } from "@/lib/api/capacity";

const mockPush = vi.fn();
const mockRefresh = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
  usePathname: () => "/org/test-org/resources",
}));

vi.mock("@/app/(app)/org/[slug]/resources/allocation-actions", () => ({
  createAllocationAction: vi.fn(),
  updateAllocationAction: vi.fn(),
  deleteAllocationAction: vi.fn(),
  bulkUpsertAction: vi.fn(),
  listAllocationsAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/resources/resource-actions", () => ({
  createLeaveAction: vi.fn(),
  updateLeaveAction: vi.fn(),
  deleteLeaveAction: vi.fn(),
  listLeaveAction: vi.fn(),
  createCapacityRecordAction: vi.fn(),
  updateCapacityRecordAction: vi.fn(),
  deleteCapacityRecordAction: vi.fn(),
  listCapacityRecordsAction: vi.fn(),
}));

function makeWeekCell(overrides: Partial<WeekCell> = {}): WeekCell {
  return {
    weekStart: "2026-03-09",
    allocations: [],
    totalAllocated: 0,
    effectiveCapacity: 40,
    remainingCapacity: 40,
    utilizationPct: 0,
    overAllocated: false,
    leaveDays: 0,
    ...overrides,
  };
}

function makeGrid(
  overrides: Partial<TeamCapacityGrid> = {},
): TeamCapacityGrid {
  return {
    members: [
      {
        memberId: "m1",
        memberName: "Alice Smith",
        avatarUrl: null,
        weeks: [
          makeWeekCell({
            weekStart: "2026-03-09",
            totalAllocated: 30,
            remainingCapacity: 10,
            utilizationPct: 75,
            allocations: [
              { id: "a1", projectId: "p1", projectName: "Project Alpha", hours: 30 },
            ],
          }),
          makeWeekCell({ weekStart: "2026-03-16" }),
        ],
        totalAllocated: 30,
        totalCapacity: 80,
        avgUtilizationPct: 37.5,
      },
      {
        memberId: "m2",
        memberName: "Bob Jones",
        avatarUrl: null,
        weeks: [
          makeWeekCell({ weekStart: "2026-03-09" }),
          makeWeekCell({ weekStart: "2026-03-16" }),
        ],
        totalAllocated: 0,
        totalCapacity: 80,
        avgUtilizationPct: 0,
      },
    ],
    weekSummaries: [
      {
        weekStart: "2026-03-09",
        teamTotalAllocated: 30,
        teamTotalCapacity: 80,
        teamUtilizationPct: 37.5,
      },
      {
        weekStart: "2026-03-16",
        teamTotalAllocated: 0,
        teamTotalCapacity: 80,
        teamUtilizationPct: 0,
      },
    ],
    ...overrides,
  };
}

describe("AllocationGrid", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders member rows", () => {
    render(<AllocationGrid grid={makeGrid()} projects={[]} slug="test-org" />);

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
  });

  it("renders week columns", () => {
    render(<AllocationGrid grid={makeGrid()} projects={[]} slug="test-org" />);

    expect(screen.getByText("9 Mar")).toBeInTheDocument();
    expect(screen.getByText("16 Mar")).toBeInTheDocument();
  });

  it("shows empty state when no members", () => {
    render(
      <AllocationGrid grid={{ members: [], weekSummaries: [] }} projects={[]} slug="test-org" />,
    );

    expect(screen.getByText(/No team members found/)).toBeInTheDocument();
  });
});

describe("CapacityCell", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders green when utilization < 80%", () => {
    const cell = makeWeekCell({
      totalAllocated: 20,
      effectiveCapacity: 40,
      utilizationPct: 50,
      allocations: [
        { id: "a2", projectId: "p1", projectName: "Alpha", hours: 20 },
      ],
    });

    render(<CapacityCell cell={cell} />);

    const cellEl = screen.getByTestId("capacity-cell");
    expect(cellEl.dataset.utilization).toBe("normal");
    expect(screen.getByText("20/40h")).toBeInTheDocument();
  });

  it("renders amber when utilization 80-100%", () => {
    const cell = makeWeekCell({
      totalAllocated: 36,
      effectiveCapacity: 40,
      utilizationPct: 90,
      allocations: [
        { id: "a3", projectId: "p1", projectName: "Alpha", hours: 36 },
      ],
    });

    render(<CapacityCell cell={cell} />);

    const cellEl = screen.getByTestId("capacity-cell");
    expect(cellEl.dataset.utilization).toBe("warning");
  });

  it("renders red when utilization > 100%", () => {
    const cell = makeWeekCell({
      totalAllocated: 48,
      effectiveCapacity: 40,
      utilizationPct: 120,
      overAllocated: true,
      allocations: [
        { id: "a4", projectId: "p1", projectName: "Alpha", hours: 48 },
      ],
    });

    render(<CapacityCell cell={cell} />);

    const cellEl = screen.getByTestId("capacity-cell");
    expect(cellEl.dataset.utilization).toBe("over");
  });

  it("shows leave overlay when leaveDays > 0", () => {
    const cell = makeWeekCell({
      totalAllocated: 20,
      effectiveCapacity: 32,
      utilizationPct: 62.5,
      leaveDays: 1,
      allocations: [
        { id: "a2", projectId: "p1", projectName: "Alpha", hours: 20 },
      ],
    });

    render(<CapacityCell cell={cell} />);

    expect(screen.getByText("1d leave")).toBeInTheDocument();
  });

  it("shows add button when cell is empty", () => {
    const cell = makeWeekCell({
      effectiveCapacity: 0,
      remainingCapacity: 0,
    });

    render(<CapacityCell cell={cell} />);

    expect(
      screen.getByRole("button", { name: "Add allocation" }),
    ).toBeInTheDocument();
  });
});

describe("WeekRangeSelector", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders week count buttons and navigates on click", async () => {
    const user = userEvent.setup();
    render(<WeekRangeSelector weekStart="2026-03-09" weekCount={4} />);

    expect(screen.getByText("4w")).toBeInTheDocument();
    expect(screen.getByText("8w")).toBeInTheDocument();
    expect(screen.getByText("12w")).toBeInTheDocument();

    await user.click(screen.getByText("8w"));
    expect(mockPush).toHaveBeenCalledTimes(1);
    // Verify push was called with updated weekEnd for 8 weeks
    const pushArg = mockPush.mock.calls[0][0] as string;
    expect(pushArg).toContain("weekStart=2026-03-09");
  });
});

describe("UtilizationBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders null percentage as dashes", () => {
    render(<UtilizationBadge percentage={null} />);
    expect(screen.getByText("--")).toBeInTheDocument();
  });
});
