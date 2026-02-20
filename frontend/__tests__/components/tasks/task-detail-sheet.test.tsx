import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import type { Task } from "@/lib/types";

// --- Mocks ---

const mockFetchTask = vi.fn();
const mockUpdateTask = vi.fn();
const mockFetchTimeEntries = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  fetchTask: (...args: unknown[]) => mockFetchTask(...args),
  updateTask: (...args: unknown[]) => mockUpdateTask(...args),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  fetchTimeEntries: (...args: unknown[]) => mockFetchTimeEntries(...args),
  updateTimeEntry: vi.fn(),
  deleteTimeEntry: vi.fn(),
}));

vi.mock("server-only", () => ({}));

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn().mockResolvedValue({ success: true }),
  updateComment: vi.fn().mockResolvedValue({ success: true }),
  deleteComment: vi.fn().mockResolvedValue({ success: true }),
}));

// --- Test data ---

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: "t1",
    projectId: "p1",
    title: "Fix login bug",
    description: "Steps to reproduce the issue.",
    status: "IN_PROGRESS",
    priority: "HIGH",
    type: "BUG",
    assigneeId: null,
    assigneeName: null,
    createdBy: "m1",
    createdByName: "Alice",
    dueDate: "2026-03-15",
    version: 1,
    createdAt: "2026-01-10T10:00:00Z",
    updatedAt: "2026-02-20T08:00:00Z",
    ...overrides,
  };
}

const defaultProps = {
  projectId: "p1",
  slug: "acme",
  canManage: true,
  currentMemberId: "current-member",
  orgRole: "org:member",
  members: [
    { id: "m1", name: "Alice", email: "alice@example.com" },
    { id: "m2", name: "Bob", email: "bob@example.com" },
  ],
  onClose: vi.fn(),
};

describe("TaskDetailSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTimeEntries.mockResolvedValue([]);
    mockFetchTask.mockResolvedValue(makeTask());
    mockUpdateTask.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup(); // REQUIRED: Radix Sheet is built on Dialog, leaks DOM between tests
  });

  // Test 1: Sheet renders when taskId provided
  it("renders with task title, status badge, and priority badge when taskId is provided", async () => {
    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    expect(mockFetchTask).toHaveBeenCalledWith("t1");
    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("High")).toBeInTheDocument();
  });

  // Test 2: Sheet is closed when taskId is null
  it("does not show sheet content when taskId is null", () => {
    render(<TaskDetailSheet {...defaultProps} taskId={null} />);

    // When taskId is null, Sheet open=false; task fetch never called
    expect(mockFetchTask).not.toHaveBeenCalled();
    // Task title should not be visible
    expect(screen.queryByRole("heading", { name: "Fix login bug" })).not.toBeInTheDocument();
  });

  // Test 3: Assignee change calls update endpoint
  it("calls updateTask with new assigneeId when assignee is changed", async () => {
    const user = userEvent.setup();

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Open the assignee selector (combobox button labeled "Unassigned")
    const assigneeButton = screen.getByRole("combobox");
    await user.click(assigneeButton);

    // Select "Alice" from the dropdown — use the option role to avoid the "Created By: Alice" text
    const aliceOption = await screen.findByRole("option", { name: /Alice/ });
    await user.click(aliceOption);

    await waitFor(() => {
      expect(mockUpdateTask).toHaveBeenCalledWith(
        "acme",
        "t1",
        "p1",
        expect.objectContaining({ assigneeId: "m1" }),
      );
    });
  });

  // Test 4: Time Entries tab shows TimeEntryList
  it("shows TimeEntryList in the Time Entries tab", async () => {
    mockFetchTimeEntries.mockResolvedValue([]);

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // "Time Entries" tab trigger should be present and active by default
    expect(
      screen.getByRole("tab", { name: "Time Entries" }),
    ).toBeInTheDocument();
    // TimeEntryList renders its empty state
    await waitFor(() => {
      expect(screen.getByText("No time logged yet")).toBeInTheDocument();
    });
  });

  // Test 5: Comments tab shows CommentSectionClient
  it("shows CommentSectionClient after clicking the Comments tab", async () => {
    const user = userEvent.setup();

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Click the Comments tab
    const commentsTab = screen.getByRole("tab", { name: "Comments" });
    await user.click(commentsTab);

    // CommentSectionClient renders its content — AddCommentForm has this label
    await waitFor(() => {
      expect(screen.getByLabelText("Add a comment")).toBeInTheDocument();
    });
  });
});
