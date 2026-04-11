import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WeeklyTimeGrid, type GridTaskRow } from "@/components/time-tracking/weekly-time-grid";
import type { MyWorkTimeEntryItem } from "@/lib/types";

// Mock server actions
const mockSaveWeeklyEntries = vi.fn();
const mockFetchWeekEntries = vi.fn();

vi.mock("@/app/(app)/org/[slug]/my-work/timesheet/actions", () => ({
  saveWeeklyEntries: (...args: unknown[]) => mockSaveWeeklyEntries(...args),
  fetchWeekEntries: (...args: unknown[]) => mockFetchWeekEntries(...args),
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

// Use a fixed Monday for all tests
const weekStart = "2026-03-09";

describe("WeeklyTimeGrid", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders correct day columns and task rows", () => {
    render(
      <WeeklyTimeGrid
        tasks={sampleTasks}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={sampleTasks}
        slug="test-org"
      />
    );

    // Check day columns are present (Mon 9 through Sun 15)
    expect(screen.getByText("Mon 9")).toBeDefined();
    expect(screen.getByText("Tue 10")).toBeDefined();
    expect(screen.getByText("Wed 11")).toBeDefined();
    expect(screen.getByText("Thu 12")).toBeDefined();
    expect(screen.getByText("Fri 13")).toBeDefined();
    expect(screen.getByText("Sat 14")).toBeDefined();
    expect(screen.getByText("Sun 15")).toBeDefined();

    // Check task row labels
    expect(screen.getByText("Design wireframes")).toBeDefined();
    expect(screen.getByText("Write API docs")).toBeDefined();

    // Check Save button is present
    expect(screen.getByRole("button", { name: "Save" })).toBeDefined();
  });

  it("editing a cell updates row total and column total", async () => {
    const user = userEvent.setup();
    const singleTask: GridTaskRow[] = [
      {
        id: "t1",
        projectId: "p1",
        projectName: "Project Alpha",
        title: "Design wireframes",
      },
    ];

    render(
      <WeeklyTimeGrid
        tasks={singleTask}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={singleTask}
        slug="test-org"
      />
    );

    // Find the Monday cell input (day 1 = first day)
    const mondayInput = screen.getByLabelText("Hours for day 1");
    await user.click(mondayInput);
    await user.type(mondayInput, "2");
    await user.tab(); // trigger blur

    // Row total should show "2h"
    const rows = screen.getAllByRole("row");
    // Row 0 = header, Row 1 = task row, Row 2 = daily total
    const taskRow = rows[1];
    expect(within(taskRow).getByText("2h")).toBeDefined();

    // Column total row should show "2h" for Monday and grand total
    const totalRow = rows[2];
    const totalCells = within(totalRow).getAllByText("2h");
    // Monday column total + grand total = 2 cells with "2h"
    expect(totalCells.length).toBe(2);

    // Save button should be enabled (dirty state)
    const saveButton = screen.getByRole("button", { name: "Save" });
    expect(saveButton).not.toBeDisabled();
  });

  it("save button sends correct batch payload", async () => {
    const user = userEvent.setup();
    const singleTask: GridTaskRow[] = [
      {
        id: "t1",
        projectId: "p1",
        projectName: "Project Alpha",
        title: "Design wireframes",
      },
    ];

    mockSaveWeeklyEntries.mockResolvedValue({
      success: true,
      result: {
        created: [{ id: "e1", taskId: "t1", date: "2026-03-09" }],
        errors: [],
        totalCreated: 1,
        totalErrors: 0,
      },
    });

    render(
      <WeeklyTimeGrid
        tasks={singleTask}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={singleTask}
        slug="test-org"
      />
    );

    // Set Monday cell to 2 hours
    const mondayInput = screen.getByLabelText("Hours for day 1");
    await user.click(mondayInput);
    await user.type(mondayInput, "2");
    await user.tab();

    // Click Save
    const saveButton = screen.getByRole("button", { name: "Save" });
    await user.click(saveButton);

    // Assert saveWeeklyEntries was called with correct payload
    expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1);
    const [slug, entries] = mockSaveWeeklyEntries.mock.calls[0];
    expect(slug).toBe("test-org");
    expect(entries).toEqual([
      {
        taskId: "t1",
        date: "2026-03-09",
        durationMinutes: 120,
        billable: true,
      },
    ]);
  });

  it("error display shows inline markers on failed cells", async () => {
    const user = userEvent.setup();
    const singleTask: GridTaskRow[] = [
      {
        id: "t1",
        projectId: "p1",
        projectName: "Project Alpha",
        title: "Design wireframes",
      },
    ];

    mockSaveWeeklyEntries.mockResolvedValue({
      success: true,
      result: {
        created: [],
        errors: [{ index: 0, taskId: "t1", message: "Task not found" }],
        totalCreated: 0,
        totalErrors: 1,
      },
    });

    render(
      <WeeklyTimeGrid
        tasks={singleTask}
        existingEntries={emptyEntries}
        weekStart={weekStart}
        allTasks={singleTask}
        slug="test-org"
      />
    );

    // Set Monday cell to 2 hours
    const mondayInput = screen.getByLabelText("Hours for day 1");
    await user.click(mondayInput);
    await user.type(mondayInput, "2");
    await user.tab();

    // Click Save
    const saveButton = screen.getByRole("button", { name: "Save" });
    await user.click(saveButton);

    // Wait for the save to complete and re-render
    // The cell should now have a red border indicating an error
    const updatedInput = await screen.findByLabelText("Hours for day 1");
    expect(updatedInput.className).toContain("border-red-500");
  });
});
