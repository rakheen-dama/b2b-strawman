import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TeamCapacityWidget } from "@/components/dashboard/team-capacity-widget";
import { MyScheduleWidget } from "@/components/dashboard/my-schedule-widget";
import { ProjectStaffingTab } from "@/components/capacity/project-staffing-tab";
import type {
  TeamCapacityGrid,
  WeekCell,
  AllocationResponse,
  LeaveBlockResponse,
  ProjectStaffingResponse,
} from "@/lib/api/capacity";

const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
  usePathname: () => "/org/test-org/dashboard",
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
            totalAllocated: 35,
            remainingCapacity: 5,
            utilizationPct: 87.5,
            allocations: [
              { id: "a1", projectId: "p1", projectName: "Alpha", hours: 35 },
            ],
          }),
        ],
        totalAllocated: 35,
        totalCapacity: 40,
        avgUtilizationPct: 87.5,
      },
      {
        memberId: "m2",
        memberName: "Bob Jones",
        avatarUrl: null,
        weeks: [
          makeWeekCell({
            totalAllocated: 45,
            remainingCapacity: -5,
            utilizationPct: 112.5,
            overAllocated: true,
          }),
        ],
        totalAllocated: 45,
        totalCapacity: 40,
        avgUtilizationPct: 112.5,
      },
      {
        memberId: "m3",
        memberName: "Carol Lee",
        avatarUrl: null,
        weeks: [
          makeWeekCell({
            totalAllocated: 10,
            remainingCapacity: 30,
            utilizationPct: 25,
          }),
        ],
        totalAllocated: 10,
        totalCapacity: 40,
        avgUtilizationPct: 25,
      },
    ],
    weekSummaries: [
      {
        weekStart: "2026-03-09",
        teamTotalAllocated: 90,
        teamTotalCapacity: 120,
        teamUtilizationPct: 75,
      },
    ],
    ...overrides,
  };
}

function makeAllocation(overrides: Partial<AllocationResponse> = {}): AllocationResponse {
  return {
    id: "alloc-1",
    memberId: "m1",
    projectId: "p1",
    weekStart: "2026-03-09",
    allocatedHours: 20,
    note: null,
    overAllocated: false,
    overageHours: 0,
    createdAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

function makeLeaveBlock(overrides: Partial<LeaveBlockResponse> = {}): LeaveBlockResponse {
  return {
    id: "leave-1",
    memberId: "m1",
    startDate: "2026-03-20",
    endDate: "2026-03-21",
    note: "Personal leave",
    createdBy: "admin",
    createdAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

function makeStaffing(overrides: Partial<ProjectStaffingResponse> = {}): ProjectStaffingResponse {
  return {
    projectId: "p1",
    projectName: "Project Alpha",
    members: [
      {
        memberId: "m1",
        memberName: "Alice Smith",
        weeks: [
          { weekStart: "2026-03-09", allocatedHours: 20 },
          { weekStart: "2026-03-16", allocatedHours: 15 },
        ],
        totalAllocatedHours: 35,
      },
    ],
    totalPlannedHours: 35,
    budgetHours: 100,
    budgetUsedPct: 35,
    ...overrides,
  };
}

describe("TeamCapacityWidget", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders utilization donut and team stats", () => {
    render(<TeamCapacityWidget data={makeGrid()} orgSlug="test-org" />);

    expect(screen.getByText("Team Capacity")).toBeInTheDocument();
    // The donut shows 75% via aria-label
    expect(screen.getByLabelText("75%")).toBeInTheDocument();
    // Stats text
    expect(screen.getByText(/90h/)).toBeInTheDocument();
    expect(screen.getByText(/120h/)).toBeInTheDocument();
  });

  it("shows over-allocated count with badge", () => {
    render(<TeamCapacityWidget data={makeGrid()} orgSlug="test-org" />);

    expect(screen.getByText("1 over-allocated")).toBeInTheDocument();
  });

  it("shows under-utilized count", () => {
    render(<TeamCapacityWidget data={makeGrid()} orgSlug="test-org" />);

    // Carol has 25% utilization (< 50%)
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("under-utilized")).toBeInTheDocument();
  });

  it("shows empty state when no members", () => {
    render(
      <TeamCapacityWidget
        data={{ members: [], weekSummaries: [] }}
        orgSlug="test-org"
      />,
    );

    expect(
      screen.getByText("No team members with capacity data."),
    ).toBeInTheDocument();
  });

  it("shows error state when data is null", () => {
    render(<TeamCapacityWidget data={null} orgSlug="test-org" />);

    expect(
      screen.getByText("Unable to load team capacity data."),
    ).toBeInTheDocument();
  });
});

describe("MyScheduleWidget", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows this week's allocations with project names", () => {
    const allocations = [
      makeAllocation({ id: "a1", allocatedHours: 20 }),
      makeAllocation({ id: "a2", projectId: "p2", allocatedHours: 15 }),
    ];

    render(
      <MyScheduleWidget
        allocations={allocations}
        leaveBlocks={[]}
        weeklyCapacity={40}
        projectNames={{ p1: "Project Alpha", p2: "Project Beta" }}
      />,
    );

    expect(screen.getByText("My Schedule")).toBeInTheDocument();
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
    expect(screen.getByText("20h")).toBeInTheDocument();
    expect(screen.getByText("15h")).toBeInTheDocument();
    // Capacity remaining: 40 - 20 - 15 = 5h (in the summary box)
    expect(screen.getByText("capacity remaining")).toBeInTheDocument();
  });

  it("falls back to projectId when projectNames not provided", () => {
    const allocations = [
      makeAllocation({ id: "a1", projectId: "uuid-123", allocatedHours: 20 }),
    ];

    render(
      <MyScheduleWidget
        allocations={allocations}
        leaveBlocks={[]}
        weeklyCapacity={40}
      />,
    );

    expect(screen.getByText("uuid-123")).toBeInTheDocument();
  });

  it("shows over-allocation warning when allocated exceeds capacity", () => {
    const allocations = [
      makeAllocation({ id: "a1", allocatedHours: 30 }),
      makeAllocation({ id: "a2", projectId: "p2", allocatedHours: 20 }),
    ];

    render(
      <MyScheduleWidget
        allocations={allocations}
        leaveBlocks={[]}
        weeklyCapacity={40}
      />,
    );

    expect(screen.getByText("Over by")).toBeInTheDocument();
    expect(screen.getByText("10h")).toBeInTheDocument();
  });

  it("shows empty state when no allocations", () => {
    render(
      <MyScheduleWidget
        allocations={[]}
        leaveBlocks={[]}
        weeklyCapacity={40}
      />,
    );

    expect(
      screen.getByText("No allocations this week."),
    ).toBeInTheDocument();
  });

  it("shows upcoming leave blocks", () => {
    // Use dates in the near future
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 2);

    const fmt = (d: Date) => d.toLocaleDateString("en-CA");

    render(
      <MyScheduleWidget
        allocations={[]}
        leaveBlocks={[
          makeLeaveBlock({
            startDate: fmt(tomorrow),
            endDate: fmt(dayAfter),
            note: "Vacation",
          }),
        ]}
        weeklyCapacity={40}
      />,
    );

    expect(screen.getByText("Upcoming Leave")).toBeInTheDocument();
  });
});

describe("ProjectStaffingTab", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders allocated members table", () => {
    render(<ProjectStaffingTab staffing={makeStaffing()} />);

    expect(screen.getByText("Allocated Members")).toBeInTheDocument();
    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    // 35h appears in the member total column and budget section
    expect(screen.getAllByText("35h").length).toBeGreaterThanOrEqual(1);
  });

  it("shows budget comparison when budget exists", () => {
    render(<ProjectStaffingTab staffing={makeStaffing()} />);

    expect(
      screen.getByText("Planned Hours vs Budget"),
    ).toBeInTheDocument();
    expect(screen.getByText(/100h/)).toBeInTheDocument();
    // Budget donut shows 35% via aria-label
    expect(screen.getByLabelText("35%")).toBeInTheDocument();
  });

  it("shows empty state when no members allocated", () => {
    render(
      <ProjectStaffingTab
        staffing={makeStaffing({ members: [] })}
      />,
    );

    expect(
      screen.getByText("No team members allocated to this project."),
    ).toBeInTheDocument();
  });

  it("shows error state when data is null", () => {
    render(<ProjectStaffingTab staffing={null} />);

    expect(
      screen.getByText("Unable to load staffing data."),
    ).toBeInTheDocument();
  });
});
