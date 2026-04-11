import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WeeklyTimeGrid, type GridTaskRow } from "@/components/time-tracking/weekly-time-grid";
import type { MyWorkTimeEntryItem } from "@/lib/types";
import { toast } from "sonner";

// Mock server actions
const mockSaveWeeklyEntries = vi.fn();
const mockFetchWeekEntries = vi.fn();
const mockFetchPreviousWeekEntries = vi.fn();

vi.mock("@/app/(app)/org/[slug]/my-work/timesheet/actions", () => ({
  saveWeeklyEntries: (...args: unknown[]) => mockSaveWeeklyEntries(...args),
  fetchWeekEntries: (...args: unknown[]) => mockFetchWeekEntries(...args),
  fetchPreviousWeekEntries: (...args: unknown[]) => mockFetchPreviousWeekEntries(...args),
}));

// Mock sonner toast
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}));

const sampleTasks: GridTaskRow[] = [
  {
    id: "t1",
    projectId: "p1",
    projectName: "Project Alpha",
    title: "Design wireframes",
  },
  {
    id: "t2",
    projectId: "p2",
    projectName: "Project Beta",
    title: "Write API docs",
  },
];

const emptyEntries: MyWorkTimeEntryItem[] = [];
const weekStart = "2026-03-16"; // Monday

describe("Copy Previous Week", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("button click triggers fetch of previous week entries", async () => {
    const user = userEvent.setup();

    // Previous week entries (Mar 9-15)
    const previousWeekEntries: MyWorkTimeEntryItem[] = [
      {
        id: "e1",
        taskId: "t1",
        taskTitle: "Design wireframes",
        projectId: "p1",
        projectName: "Project Alpha",
        date: "2026-03-09", // Previous Monday
        durationMinutes: 120,
        billable: true,
        description: null,
      },
      {
        id: "e2",
        taskId: "t1",
        taskTitle: "Design wireframes",
        projectId: "p1",
        projectName: "Project Alpha",
        date: "2026-03-11", // Previous Wednesday
        durationMinutes: 180,
        billable: true,
        description: null,
      },
    ];

    mockFetchPreviousWeekEntries.mockResolvedValue(previousWeekEntries);

    render(
      <WeeklyTimeGrid
        tasks={sampleTasks}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={sampleTasks}
        slug="test-org"
      />
    );

    // Click "Copy Previous Week" button
    const copyButton = screen.getByRole("button", {
      name: /Copy Previous Week/,
    });
    await user.click(copyButton);

    // Should have called fetchPreviousWeekEntries with the current week start
    expect(mockFetchPreviousWeekEntries).toHaveBeenCalledTimes(1);
    expect(mockFetchPreviousWeekEntries).toHaveBeenCalledWith("2026-03-16");
  });

  it("grid pre-fills with previous week hours shifted to current week", async () => {
    const user = userEvent.setup();
    const singleTask: GridTaskRow[] = [
      {
        id: "t1",
        projectId: "p1",
        projectName: "Project Alpha",
        title: "Design wireframes",
      },
    ];

    // Previous week: task t1 had 2h on Monday (Mar 9) and 3h on Wednesday (Mar 11)
    const previousWeekEntries: MyWorkTimeEntryItem[] = [
      {
        id: "e1",
        taskId: "t1",
        taskTitle: "Design wireframes",
        projectId: "p1",
        projectName: "Project Alpha",
        date: "2026-03-09",
        durationMinutes: 120,
        billable: true,
        description: null,
      },
      {
        id: "e2",
        taskId: "t1",
        taskTitle: "Design wireframes",
        projectId: "p1",
        projectName: "Project Alpha",
        date: "2026-03-11",
        durationMinutes: 180,
        billable: true,
        description: null,
      },
    ];

    mockFetchPreviousWeekEntries.mockResolvedValue(previousWeekEntries);

    render(
      <WeeklyTimeGrid
        tasks={singleTask}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={singleTask}
        slug="test-org"
      />
    );

    const copyButton = screen.getByRole("button", {
      name: /Copy Previous Week/,
    });
    await user.click(copyButton);

    // Wait for the copy to complete
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalled();
    });

    // Row total should show 5h (2h Monday + 3h Wednesday)
    const rows = screen.getAllByRole("row");
    // Row 0 = header, Row 1 = task row, Row 2 = daily total
    const taskRow = rows[1];
    expect(within(taskRow).getByText("5h")).toBeDefined();
  });

  it("copied entries are marked dirty (Save button enabled)", async () => {
    const user = userEvent.setup();
    const singleTask: GridTaskRow[] = [
      {
        id: "t1",
        projectId: "p1",
        projectName: "Project Alpha",
        title: "Design wireframes",
      },
    ];

    const previousWeekEntries: MyWorkTimeEntryItem[] = [
      {
        id: "e1",
        taskId: "t1",
        taskTitle: "Design wireframes",
        projectId: "p1",
        projectName: "Project Alpha",
        date: "2026-03-09",
        durationMinutes: 60,
        billable: true,
        description: null,
      },
    ];

    mockFetchPreviousWeekEntries.mockResolvedValue(previousWeekEntries);

    render(
      <WeeklyTimeGrid
        tasks={singleTask}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={singleTask}
        slug="test-org"
      />
    );

    // Save button should initially be disabled (no dirty state)
    const saveButton = screen.getByRole("button", { name: "Save" });
    expect(saveButton).toBeDisabled();

    // Click Copy Previous Week
    const copyButton = screen.getByRole("button", {
      name: /Copy Previous Week/,
    });
    await user.click(copyButton);

    // Wait for copy to complete
    await waitFor(() => {
      expect(toast.success).toHaveBeenCalled();
    });

    // Save button should now be enabled (dirty state)
    expect(saveButton).not.toBeDisabled();
  });
});
