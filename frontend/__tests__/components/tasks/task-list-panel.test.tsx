import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaskListPanel } from "@/components/tasks/task-list-panel";
import type { Task } from "@/lib/types";

// --- Mocks ---

const mockClaimTask = vi.fn();
const mockReleaseTask = vi.fn();
const mockUpdateTask = vi.fn();
const mockFetchTasks = vi.fn();
const mockFetchTask = vi.fn();
const mockRefresh = vi.fn();
const mockPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  claimTask: (...args: unknown[]) => mockClaimTask(...args),
  releaseTask: (...args: unknown[]) => mockReleaseTask(...args),
  updateTask: (...args: unknown[]) => mockUpdateTask(...args),
  fetchTasks: (...args: unknown[]) => mockFetchTasks(...args),
  fetchTask: (...args: unknown[]) => mockFetchTask(...args),
}));

const mockFetchTimeEntries = vi.fn();

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

// Mutable search params for per-test URL state control
let mockSearchParams = new URLSearchParams("tab=tasks");

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: mockRefresh,
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
}));

// --- Test data ---

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: "t1",
    projectId: "p1",
    title: "Fix login bug",
    description: null,
    status: "OPEN",
    priority: "MEDIUM",
    type: null,
    assigneeId: null,
    assigneeName: null,
    createdBy: "m1",
    createdByName: "Alice",
    dueDate: null,
    version: 0,
    createdAt: "2024-06-01T00:00:00Z",
    updatedAt: "2024-06-01T00:00:00Z",
    ...overrides,
  };
}

const openUnassigned = makeTask({ id: "t1", title: "Open unassigned task", status: "OPEN", assigneeId: null, priority: "HIGH" });
const inProgressOwn = makeTask({
  id: "t2",
  title: "My in-progress task",
  status: "IN_PROGRESS",
  assigneeId: "current-member",
  assigneeName: "Me",
  priority: "MEDIUM",
});
const doneTask = makeTask({ id: "t3", title: "Completed task", status: "DONE", priority: "LOW" });

describe("TaskListPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams("tab=tasks");
    mockFetchTasks.mockResolvedValue([openUnassigned, inProgressOwn, doneTask]);
    mockFetchTask.mockResolvedValue(makeTask({ id: "t1", projectId: "p1", title: "Open unassigned task" }));
    mockFetchTimeEntries.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  // 40.10: Task list renders correct columns
  it("renders task rows with priority, title, status, assignee, due date columns", () => {
    render(
      <TaskListPanel
        tasks={[openUnassigned, inProgressOwn, doneTask]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    expect(screen.getByText("Open unassigned task")).toBeInTheDocument();
    expect(screen.getByText("My in-progress task")).toBeInTheDocument();
    expect(screen.getByText("Completed task")).toBeInTheDocument();
    // Multiple rows can show "Unassigned" — use getAllByText
    expect(screen.getAllByText("Unassigned").length).toBeGreaterThan(0);
    expect(screen.getByText("Me")).toBeInTheDocument();
  });

  // 40.10: Claim button appears for unassigned OPEN task
  it("shows Claim button for OPEN unassigned tasks", () => {
    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // Find the Claim button within the table (not in filter bar)
    const table = screen.getByRole("table");
    expect(within(table).getByRole("button", { name: /Claim/i })).toBeInTheDocument();
  });

  // 40.10: Release + Done buttons for own IN_PROGRESS task
  it("shows Release and Mark Done buttons for own IN_PROGRESS tasks", () => {
    render(
      <TaskListPanel
        tasks={[inProgressOwn]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const table = screen.getByRole("table");
    expect(within(table).getByRole("button", { name: /Release/i })).toBeInTheDocument();
    // The "Done" action button is inside the table
    expect(within(table).getByRole("button", { name: /Done/i })).toBeInTheDocument();
  });

  // 40.10: No Claim/Release buttons for other people's tasks, but Done visible for managers
  it("does not show Claim or Release buttons for tasks assigned to others", () => {
    const othersTask = makeTask({
      id: "t4",
      title: "Someone else task",
      status: "IN_PROGRESS",
      assigneeId: "other-member",
      assigneeName: "Bob",
    });

    render(
      <TaskListPanel
        tasks={[othersTask]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const table = screen.getByRole("table");
    expect(within(table).queryByRole("button", { name: /Claim/i })).not.toBeInTheDocument();
    expect(within(table).queryByRole("button", { name: /Release/i })).not.toBeInTheDocument();
    // Managers CAN mark other people's IN_PROGRESS tasks as Done
    expect(within(table).getByRole("button", { name: /^Done$/i })).toBeInTheDocument();
  });

  // Verify non-managers cannot mark other people's tasks as Done
  it("does not show Done button for non-managers on other people's tasks", () => {
    const othersTask = makeTask({
      id: "t4",
      title: "Someone else task",
      status: "IN_PROGRESS",
      assigneeId: "other-member",
      assigneeName: "Bob",
    });

    render(
      <TaskListPanel
        tasks={[othersTask]}
        slug="acme"
        projectId="p1"
        canManage={false}
        currentMemberId="current-member"
      />,
    );

    const table = screen.getByRole("table");
    expect(within(table).queryByRole("button", { name: /^Done$/i })).not.toBeInTheDocument();
  });

  // 40.10: Filter toggles
  it("renders filter pills and changes active filter on click", async () => {
    const user = userEvent.setup();

    render(
      <TaskListPanel
        tasks={[openUnassigned, inProgressOwn, doneTask]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const filterGroup = screen.getByRole("group", { name: /task filters/i });
    expect(within(filterGroup).getByText("All")).toBeInTheDocument();
    expect(within(filterGroup).getByText("Open")).toBeInTheDocument();
    expect(within(filterGroup).getByText("In Progress")).toBeInTheDocument();
    expect(within(filterGroup).getByText("Done")).toBeInTheDocument();
    expect(within(filterGroup).getByText("My Tasks")).toBeInTheDocument();

    await user.click(within(filterGroup).getByText("Open"));

    await waitFor(() => {
      expect(mockFetchTasks).toHaveBeenCalledWith("p1", { status: "OPEN" });
    });
  });

  // 40.10: Priority badge variants (HIGH=destructive, MEDIUM=warning, LOW=neutral)
  it("renders priority badges with correct variants", () => {
    render(
      <TaskListPanel
        tasks={[openUnassigned, inProgressOwn, doneTask]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const highBadge = screen.getByText("High");
    expect(highBadge).toHaveAttribute("data-variant", "destructive");

    // There is exactly one "Medium" badge (priority) in the table
    const mediumBadges = screen.getAllByText("Medium");
    const mediumPriorityBadge = mediumBadges.find(
      (el) => el.getAttribute("data-slot") === "badge",
    );
    expect(mediumPriorityBadge).toHaveAttribute("data-variant", "warning");

    const lowBadge = screen.getByText("Low");
    expect(lowBadge).toHaveAttribute("data-variant", "neutral");
  });

  // 40.10: Due date overdue styling
  it("displays overdue dates in red with alert icon for non-done tasks", () => {
    const overdueTask = makeTask({
      id: "t5",
      title: "Overdue task",
      status: "OPEN",
      priority: "HIGH",
      dueDate: "2020-01-01",
    });

    const { container } = render(
      <TaskListPanel
        tasks={[overdueTask]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // The overdue date span should have red text styling
    const dateSpan = container.querySelector("[class*='text-red']");
    expect(dateSpan).toBeInTheDocument();
  });

  // 40.10: Done tasks with past due dates should NOT show overdue styling
  it("does not show overdue styling for done tasks", () => {
    const donePastDue = makeTask({
      id: "t6",
      title: "Done past due",
      status: "DONE",
      priority: "LOW",
      dueDate: "2020-01-01",
    });

    const { container } = render(
      <TaskListPanel
        tasks={[donePastDue]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const dateSpan = container.querySelector("[class*='text-red']");
    expect(dateSpan).not.toBeInTheDocument();
  });

  // 40.10: 409 conflict error handling
  it("displays conflict error and refreshes on 409 claim failure", async () => {
    mockClaimTask.mockResolvedValue({
      success: false,
      error: "This task was just claimed by someone else. Please refresh.",
    });
    mockFetchTasks.mockResolvedValue([openUnassigned]);
    const user = userEvent.setup();

    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    const table = screen.getByRole("table");
    await user.click(within(table).getByRole("button", { name: /Claim/i }));

    await waitFor(() => {
      expect(mockClaimTask).toHaveBeenCalledWith("acme", "t1", "p1");
    });

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "This task was just claimed by someone else",
      );
    });

    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
  });

  // 40.10: Empty state
  it("shows empty state when no tasks exist", () => {
    render(
      <TaskListPanel
        tasks={[]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    expect(screen.getByText("No tasks yet")).toBeInTheDocument();
  });

  // 114A: Filtered empty state uses EmptyState component
  it("shows filtered empty state when filter returns zero tasks", async () => {
    const user = userEvent.setup();
    // First render with tasks so the filter bar appears
    mockFetchTasks.mockResolvedValue([]);

    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // Click "Done" filter — fetchTasks returns []
    const filterGroup = screen.getByRole("group", { name: /task filters/i });
    await user.click(within(filterGroup).getByText("Done"));

    await waitFor(() => {
      expect(screen.getByText("No tasks match this filter")).toBeInTheDocument();
    });
    expect(
      screen.getByText("Try a different filter or clear the selection."),
    ).toBeInTheDocument();
  });

  // 130B: Clicking task title updates URL with ?taskId=
  it("clicking a task title opens the sheet by pushing ?taskId= to URL", async () => {
    const user = userEvent.setup();

    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // Click the task title button (now a sheet opener, not an expand toggle)
    const titleButton = screen.getByRole("button", { name: /Open task detail for Open unassigned task/i });
    await user.click(titleButton);

    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining("taskId=t1"),
      { scroll: false },
    );
  });

  // 130B: Sheet renders when URL has ?taskId=
  it("renders TaskDetailSheet when URL has ?taskId=", async () => {
    // Set URL to have taskId
    mockSearchParams = new URLSearchParams("tab=tasks&taskId=t1");

    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // TaskDetailSheet fetches task when taskId is non-null
    await waitFor(() => {
      expect(mockFetchTask).toHaveBeenCalledWith("t1");
    });
  });

  // 130B: Closing the sheet clears ?taskId= from URL
  it("closing the sheet clears taskId from URL", async () => {
    // Set URL to have taskId
    mockSearchParams = new URLSearchParams("tab=tasks&taskId=t1");

    const user = userEvent.setup();

    render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // Wait for sheet to load task
    await waitFor(() => {
      expect(mockFetchTask).toHaveBeenCalledWith("t1");
    });

    // The sheet's close button triggers onClose, which calls closeTask()
    const closeButton = await screen.findByRole("button", { name: /Close task detail/i });
    await user.click(closeButton);

    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining("tab=tasks"),
      { scroll: false },
    );
    // taskId should NOT be in the push URL
    const pushCall = mockPush.mock.calls[0][0] as string;
    expect(pushCall).not.toContain("taskId");
  });

  // 130B: Inline expansion row is fully removed
  it("does not render an inline expanded row (colSpan row) when task is clicked", async () => {
    const user = userEvent.setup();

    const { container } = render(
      <TaskListPanel
        tasks={[openUnassigned]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
      />,
    );

    // Click the task title
    const titleButton = screen.getByRole("button", { name: /Open task detail for Open unassigned task/i });
    await user.click(titleButton);

    // No colSpan row should appear in the table
    const cells = container.querySelectorAll("td[colspan]");
    expect(cells).toHaveLength(0);
  });
});
