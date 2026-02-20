import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaskListPanel } from "@/components/tasks/task-list-panel";
import type { Task, SavedViewResponse } from "@/lib/types";

// --- Mocks ---

const mockFetchTasks = vi.fn();
const mockFetchTask = vi.fn();
const mockPush = vi.fn();
const mockRefresh = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  claimTask: vi.fn(),
  releaseTask: vi.fn(),
  updateTask: vi.fn(),
  fetchTasks: (...args: unknown[]) => mockFetchTasks(...args),
  fetchTask: (...args: unknown[]) => mockFetchTask(...args),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  fetchTimeEntries: vi.fn().mockResolvedValue([]),
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

const taskView: SavedViewResponse = {
  id: "view-task-1",
  entityType: "TASK",
  name: "High Priority",
  filters: { priority: "HIGH" },
  columns: null,
  shared: true,
  createdBy: "user-1",
  sortOrder: 0,
  createdAt: "2025-06-01T10:00:00Z",
  updatedAt: "2025-06-01T10:00:00Z",
};

const mockOnSave = vi.fn().mockResolvedValue({ success: true });
const task = makeTask({ id: "t1", title: "Open unassigned task", status: "OPEN" });

describe("TaskListPanel — ViewSelectorClient integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams("tab=tasks");
    mockFetchTasks.mockResolvedValue([task]);
    mockFetchTask.mockResolvedValue(task);
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: ViewSelectorClient renders in task list header
  it("renders ViewSelectorClient with task views in header when savedViews and onSave are provided", () => {
    render(
      <TaskListPanel
        tasks={[task]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
        orgRole="org:admin"
        savedViews={[taskView]}
        onSave={mockOnSave}
      />,
    );

    // The SavedViewSelector renders the view name tab
    expect(screen.getByText("High Priority")).toBeInTheDocument();
    // "Save View" button should appear since canManage=true and onSave is provided
    expect(screen.getByText("Save View")).toBeInTheDocument();
  });

  // Test 2: Selecting a view updates ?view= URL param
  it("selecting a view tab updates ?view= URL param while preserving existing params", async () => {
    const user = userEvent.setup();

    render(
      <TaskListPanel
        tasks={[task]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
        orgRole="org:admin"
        savedViews={[taskView]}
        onSave={mockOnSave}
      />,
    );

    await user.click(screen.getByText("High Priority"));

    // URL should include ?view=view-task-1 (and preserve tab=tasks)
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith(
        expect.stringContaining("view=view-task-1"),
      );
    });
    // tab param should also be preserved
    const pushUrl = mockPush.mock.calls[0][0] as string;
    expect(pushUrl).toContain("tab=tasks");
  });

  // Test 3: Task list re-fetches with viewId when filter is changed while a view is active
  it("forwards viewId to fetchTasks when a filter is changed while a view is active", async () => {
    const user = userEvent.setup();

    // Set URL to have a view selected
    mockSearchParams = new URLSearchParams("tab=tasks&view=view-task-1");

    render(
      <TaskListPanel
        tasks={[task]}
        slug="acme"
        projectId="p1"
        canManage={true}
        currentMemberId="current-member"
        orgRole="org:admin"
        savedViews={[taskView]}
        onSave={mockOnSave}
      />,
    );

    // Click the "Open" status filter
    const filterGroup = screen.getByRole("group", { name: /task filters/i });
    const openButtons = filterGroup.querySelectorAll("button");
    // "Open" is the second filter button (index 1)
    const openButton = Array.from(openButtons).find((btn) => btn.textContent === "Open");
    expect(openButton).toBeDefined();
    await user.click(openButton!);

    await waitFor(() => {
      expect(mockFetchTasks).toHaveBeenCalledWith("p1", expect.objectContaining({
        status: "OPEN",
        viewId: "view-task-1",
      }));
    });
  });
});
