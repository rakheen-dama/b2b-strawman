import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AllocationGrid } from "@/components/capacity/allocation-grid";
import { AllocationPopover } from "@/components/capacity/allocation-popover";
import { GridFilters } from "@/components/capacity/grid-filters";
import { LeaveDialog } from "@/components/capacity/leave-dialog";
import type { TeamCapacityGrid, WeekCell } from "@/lib/api/capacity";

const mockPush = vi.fn();
const mockRefresh = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
  usePathname: () => "/org/test-org/resources",
}));

const mockCreateAllocation = vi.fn();
const mockUpdateAllocation = vi.fn();
const mockDeleteAllocation = vi.fn();
const mockBulkUpsert = vi.fn();
const mockCreateLeave = vi.fn();
const mockUpdateLeave = vi.fn();
const mockDeleteLeave = vi.fn();
const mockListLeave = vi.fn();
const mockCreateCapacity = vi.fn();
const mockUpdateCapacity = vi.fn();
const mockDeleteCapacity = vi.fn();
const mockListCapacity = vi.fn();
const mockListAllocations = vi.fn();

vi.mock("@/app/(app)/org/[slug]/resources/actions", () => ({
  createAllocationAction: (...args: unknown[]) => mockCreateAllocation(...args),
  updateAllocationAction: (...args: unknown[]) => mockUpdateAllocation(...args),
  deleteAllocationAction: (...args: unknown[]) => mockDeleteAllocation(...args),
  bulkUpsertAction: (...args: unknown[]) => mockBulkUpsert(...args),
  createLeaveAction: (...args: unknown[]) => mockCreateLeave(...args),
  updateLeaveAction: (...args: unknown[]) => mockUpdateLeave(...args),
  deleteLeaveAction: (...args: unknown[]) => mockDeleteLeave(...args),
  listLeaveAction: (...args: unknown[]) => mockListLeave(...args),
  createCapacityRecordAction: (...args: unknown[]) =>
    mockCreateCapacity(...args),
  updateCapacityRecordAction: (...args: unknown[]) =>
    mockUpdateCapacity(...args),
  deleteCapacityRecordAction: (...args: unknown[]) =>
    mockDeleteCapacity(...args),
  listCapacityRecordsAction: (...args: unknown[]) =>
    mockListCapacity(...args),
  listAllocationsAction: (...args: unknown[]) => mockListAllocations(...args),
}));

const testProjects = [
  { id: "p1", name: "Project Alpha" },
  { id: "p2", name: "Project Beta" },
];

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
              { projectId: "p1", projectName: "Project Alpha", hours: 30 },
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
          makeWeekCell({
            weekStart: "2026-03-09",
            totalAllocated: 48,
            effectiveCapacity: 40,
            remainingCapacity: -8,
            utilizationPct: 120,
            overAllocated: true,
            allocations: [
              { projectId: "p1", projectName: "Project Alpha", hours: 48 },
            ],
          }),
          makeWeekCell({ weekStart: "2026-03-16" }),
        ],
        totalAllocated: 48,
        totalCapacity: 80,
        avgUtilizationPct: 60,
      },
    ],
    weekSummaries: [
      {
        weekStart: "2026-03-09",
        teamTotalAllocated: 78,
        teamTotalCapacity: 80,
        teamUtilizationPct: 97.5,
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

describe("AllocationPopover", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens popover when trigger is clicked", async () => {
    const user = userEvent.setup();
    const cell = makeWeekCell({
      allocations: [
        { projectId: "p1", projectName: "Project Alpha", hours: 20 },
      ],
      totalAllocated: 20,
    });

    render(
      <AllocationPopover
        memberId="m1"
        memberName="Alice Smith"
        weekStart="2026-03-09"
        cell={cell}
        projects={testProjects}
        slug="test-org"
      >
        <button>Open Popover</button>
      </AllocationPopover>,
    );

    await user.click(screen.getByText("Open Popover"));

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Week of 2026-03-09")).toBeInTheDocument();
  });

  it("shows allocation list in popover", async () => {
    const user = userEvent.setup();
    const cell = makeWeekCell({
      allocations: [
        { projectId: "p1", projectName: "Project Alpha", hours: 20 },
        { projectId: "p2", projectName: "Project Beta", hours: 10 },
      ],
      totalAllocated: 30,
    });

    render(
      <AllocationPopover
        memberId="m1"
        memberName="Alice Smith"
        weekStart="2026-03-09"
        cell={cell}
        projects={testProjects}
        slug="test-org"
      >
        <button>Open Alloc Popover</button>
      </AllocationPopover>,
    );

    await user.click(screen.getByText("Open Alloc Popover"));

    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
    expect(screen.getByText("20h")).toBeInTheDocument();
    expect(screen.getByText("10h")).toBeInTheDocument();
  });

  it("shows add allocation form when button clicked", async () => {
    const user = userEvent.setup();
    const cell = makeWeekCell();

    render(
      <AllocationPopover
        memberId="m1"
        memberName="Alice Smith"
        weekStart="2026-03-09"
        cell={cell}
        projects={testProjects}
        slug="test-org"
      >
        <button>Open Add Form</button>
      </AllocationPopover>,
    );

    await user.click(screen.getByText("Open Add Form"));
    await user.click(screen.getByText("Add Allocation"));

    expect(screen.getByLabelText("Project")).toBeInTheDocument();
    expect(screen.getByLabelText("Hours")).toBeInTheDocument();
    expect(screen.getByLabelText("Note")).toBeInTheDocument();
  });

  it("shows over-allocation warning when cell is over-allocated", async () => {
    const user = userEvent.setup();
    const cell = makeWeekCell({
      overAllocated: true,
      totalAllocated: 48,
      effectiveCapacity: 40,
      utilizationPct: 120,
      allocations: [
        { projectId: "p1", projectName: "Project Alpha", hours: 48 },
      ],
    });

    render(
      <AllocationPopover
        memberId="m1"
        memberName="Alice Smith"
        weekStart="2026-03-09"
        cell={cell}
        projects={testProjects}
        slug="test-org"
      >
        <button>Open Over Popover</button>
      </AllocationPopover>,
    );

    await user.click(screen.getByText("Open Over Popover"));

    expect(
      screen.getByTestId("over-allocation-warning"),
    ).toBeInTheDocument();
  });
});

describe("AllocationGrid with popover and panel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListCapacity.mockResolvedValue({ success: true, records: [] });
    mockListLeave.mockResolvedValue({ success: true, blocks: [] });
    mockListAllocations.mockResolvedValue({
      success: true,
      allocations: [],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("opens member detail panel on member name click", async () => {
    const user = userEvent.setup();

    render(
      <AllocationGrid
        grid={makeGrid()}
        projects={testProjects}
        slug="test-org"
      />,
    );

    await user.click(screen.getByTestId("member-name-m1"));

    // Sheet opens with member info
    // The SheetTitle is sr-only, so check for utilization section
    expect(screen.getByText("Utilization")).toBeInTheDocument();
  });
});

describe("LeaveDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("validates that end date is not before start date", async () => {
    const user = userEvent.setup();
    mockCreateLeave.mockResolvedValue({ success: true });

    render(
      <LeaveDialog
        open={true}
        onOpenChange={() => {}}
        slug="test-org"
        memberId="m1"
      />,
    );

    const startDateInput = screen.getByLabelText("Start Date");
    const endDateInput = screen.getByLabelText("End Date");

    await user.clear(startDateInput);
    await user.type(startDateInput, "2026-03-20");
    await user.clear(endDateInput);
    await user.type(endDateInput, "2026-03-15");

    // Click save
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      screen.getByText("End date must be on or after start date."),
    ).toBeInTheDocument();
    expect(mockCreateLeave).not.toHaveBeenCalled();
  });
});

describe("GridFilters", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("filters members by search term", async () => {
    const user = userEvent.setup();
    const grid = makeGrid();
    const onFilteredGrid = vi.fn();

    render(
      <GridFilters
        grid={grid}
        projects={testProjects}
        onFilteredGrid={onFilteredGrid}
      />,
    );

    // Initial call should pass all members
    expect(onFilteredGrid).toHaveBeenCalled();
    const initialCall =
      onFilteredGrid.mock.calls[onFilteredGrid.mock.calls.length - 1][0];
    expect(initialCall.members).toHaveLength(2);

    // Type a search term
    const searchInput = screen.getByPlaceholderText("Search members...");
    await user.type(searchInput, "Alice");

    // Should filter to just Alice
    const filteredCall =
      onFilteredGrid.mock.calls[onFilteredGrid.mock.calls.length - 1][0];
    expect(filteredCall.members).toHaveLength(1);
    expect(filteredCall.members[0].memberName).toBe("Alice Smith");
  });

  it("filters by over-allocated toggle", async () => {
    const user = userEvent.setup();
    const grid = makeGrid();
    const onFilteredGrid = vi.fn();

    render(
      <GridFilters
        grid={grid}
        projects={testProjects}
        onFilteredGrid={onFilteredGrid}
      />,
    );

    // Toggle over-allocated only
    const toggle = screen.getByRole("switch");
    await user.click(toggle);

    // Should filter to only Bob (over-allocated)
    const filteredCall =
      onFilteredGrid.mock.calls[onFilteredGrid.mock.calls.length - 1][0];
    expect(filteredCall.members).toHaveLength(1);
    expect(filteredCall.members[0].memberName).toBe("Bob Jones");
  });
});
