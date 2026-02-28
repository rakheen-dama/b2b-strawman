import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  formatRecurrenceRule,
  parseRecurrenceRule,
  describeRecurrence,
} from "@/lib/recurrence";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams(),
}));

// Mock server actions
const mockCreateTask = vi.fn();
const mockFetchTasks = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  createTask: (...args: unknown[]) => mockCreateTask(...args),
  fetchTasks: (...args: unknown[]) => mockFetchTasks(...args),
  completeTask: vi.fn(),
  cancelTask: vi.fn(),
  reopenTask: vi.fn(),
  claimTask: vi.fn(),
  releaseTask: vi.fn(),
  fetchTask: vi.fn(),
  updateTask: vi.fn(),
  deleteTask: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  fetchTimeEntries: vi.fn().mockResolvedValue([]),
  updateTimeEntry: vi.fn(),
  deleteTimeEntry: vi.fn(),
}));

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn().mockResolvedValue({ success: true }),
  updateComment: vi.fn().mockResolvedValue({ success: true }),
  deleteComment: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({
    userId: "user-1",
    orgId: "org-1",
    orgRole: "org:owner",
    memberId: "member-1",
  }),
}));

import { CreateTaskDialog } from "@/components/tasks/create-task-dialog";
import { TaskListPanel } from "@/components/tasks/task-list-panel";
import type { Task } from "@/lib/types";

describe("Task Recurrence", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: CreateTaskDialog renders recurrence fields when frequency selected
  it("renders recurrence interval and end date when frequency is selected", async () => {
    const user = userEvent.setup();

    render(
      <CreateTaskDialog slug="test-org" projectId="proj-1">
        <button>Create Recurrence Task</button>
      </CreateTaskDialog>
    );

    // Open the dialog
    await user.click(screen.getByText("Create Recurrence Task"));

    // Frequency select should be present
    const freqSelect = screen.getByLabelText(/Recurrence/);
    expect(freqSelect).toBeInTheDocument();

    // Interval and end date should NOT be visible yet
    expect(screen.queryByLabelText("Interval")).not.toBeInTheDocument();

    // Select "Weekly"
    await user.selectOptions(freqSelect, "WEEKLY");

    // Now interval and end date should be visible
    expect(screen.getByLabelText("Interval")).toBeInTheDocument();
    expect(screen.getByLabelText(/End Date/)).toBeInTheDocument();
  });

  // Test 2: CreateTaskDialog hides recurrence fields when "None" selected
  it("hides recurrence fields when None is selected", async () => {
    const user = userEvent.setup();

    render(
      <CreateTaskDialog slug="test-org" projectId="proj-1">
        <button>Create None Task</button>
      </CreateTaskDialog>
    );

    await user.click(screen.getByText("Create None Task"));

    const freqSelect = screen.getByLabelText(/Recurrence/);

    // Select Monthly, then back to None
    await user.selectOptions(freqSelect, "MONTHLY");
    expect(screen.getByLabelText("Interval")).toBeInTheDocument();

    await user.selectOptions(freqSelect, "");
    expect(screen.queryByLabelText("Interval")).not.toBeInTheDocument();
  });

  // Test 3: Recurrence rule formatting
  it("formats recurrence rules correctly", () => {
    expect(formatRecurrenceRule("MONTHLY", 2)).toBe("FREQ=MONTHLY;INTERVAL=2");
    expect(formatRecurrenceRule("WEEKLY", 1)).toBe("FREQ=WEEKLY;INTERVAL=1");
    expect(formatRecurrenceRule("DAILY", 3)).toBe("FREQ=DAILY;INTERVAL=3");
    expect(formatRecurrenceRule("YEARLY", 1)).toBe("FREQ=YEARLY;INTERVAL=1");
    expect(formatRecurrenceRule(null, 1)).toBeNull();
  });

  // Test 4: Parse recurrence rule back to frequency + interval
  it("parses recurrence rules back to frequency and interval", () => {
    expect(parseRecurrenceRule("FREQ=WEEKLY;INTERVAL=2")).toEqual({
      frequency: "WEEKLY",
      interval: 2,
    });
    expect(parseRecurrenceRule("FREQ=MONTHLY;INTERVAL=1")).toEqual({
      frequency: "MONTHLY",
      interval: 1,
    });
    expect(parseRecurrenceRule(null)).toBeNull();
    expect(parseRecurrenceRule("")).toBeNull();

    // describeRecurrence
    expect(describeRecurrence("FREQ=DAILY;INTERVAL=1")).toBe("Daily");
    expect(describeRecurrence("FREQ=WEEKLY;INTERVAL=2")).toBe("Every 2 weeks");
    expect(describeRecurrence("FREQ=MONTHLY;INTERVAL=3")).toBe("Every 3 months");
    expect(describeRecurrence("FREQ=YEARLY;INTERVAL=1")).toBe("Yearly");
    expect(describeRecurrence(null)).toBe("None");
  });

  // Test 5: Task list shows recurring badge for recurring tasks
  it("shows recurring badge for recurring tasks in the task list", async () => {
    const recurringTask: Task = {
      id: "task-1",
      projectId: "proj-1",
      title: "Recurring Report",
      description: null,
      status: "OPEN",
      priority: "MEDIUM",
      type: null,
      assigneeId: null,
      assigneeName: null,
      createdBy: "user-1",
      createdByName: "Alice",
      dueDate: null,
      version: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      completedAt: null,
      completedBy: null,
      completedByName: null,
      cancelledAt: null,
      recurrenceRule: "FREQ=WEEKLY;INTERVAL=1",
      recurrenceEndDate: null,
      parentTaskId: null,
      isRecurring: true,
    };

    const nonRecurringTask: Task = {
      ...recurringTask,
      id: "task-2",
      title: "One-off Task",
      recurrenceRule: null,
      isRecurring: false,
    };

    mockFetchTasks.mockResolvedValue([recurringTask, nonRecurringTask]);

    render(
      <TaskListPanel
        tasks={[recurringTask, nonRecurringTask]}
        slug="test-org"
        projectId="proj-1"
        canManage={true}
        currentMemberId="member-1"
      />
    );

    // The recurring task should have a repeat icon (aria-label "Recurring task")
    expect(screen.getByLabelText("Recurring task")).toBeInTheDocument();

    // The non-recurring task should not have the icon
    const recurringLabels = screen.getAllByLabelText("Recurring task");
    expect(recurringLabels).toHaveLength(1);
  });
});
