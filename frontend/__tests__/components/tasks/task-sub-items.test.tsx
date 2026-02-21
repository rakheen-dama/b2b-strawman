import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaskSubItems } from "@/components/tasks/task-sub-items";
import type { TaskItem } from "@/lib/types";

// --- Mocks ---

const mockFetchTaskItems = vi.fn();
const mockAddTaskItem = vi.fn();
const mockToggleTaskItem = vi.fn();
const mockDeleteTaskItem = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/projects/[id]/task-item-actions",
  () => ({
    fetchTaskItems: (...args: unknown[]) => mockFetchTaskItems(...args),
    addTaskItem: (...args: unknown[]) => mockAddTaskItem(...args),
    toggleTaskItem: (...args: unknown[]) => mockToggleTaskItem(...args),
    deleteTaskItem: (...args: unknown[]) => mockDeleteTaskItem(...args),
  })
);

vi.mock("server-only", () => ({}));

// --- Test data ---

const item1: TaskItem = {
  id: "item-1",
  taskId: "task-1",
  title: "Collect IRP5s",
  completed: false,
  sortOrder: 0,
  createdAt: "2026-01-10T10:00:00Z",
  updatedAt: "2026-01-10T10:00:00Z",
};

const item2: TaskItem = {
  id: "item-2",
  taskId: "task-1",
  title: "Verify tax certificates",
  completed: true,
  sortOrder: 1,
  createdAt: "2026-01-10T11:00:00Z",
  updatedAt: "2026-01-10T11:00:00Z",
};

const item3: TaskItem = {
  id: "item-3",
  taskId: "task-1",
  title: "Submit return",
  completed: true,
  sortOrder: 2,
  createdAt: "2026-01-10T12:00:00Z",
  updatedAt: "2026-01-10T12:00:00Z",
};

const defaultProps = {
  taskId: "task-1",
  slug: "test-org",
  projectId: "proj-1",
  canManage: false,
};

// --- Tests ---

describe("TaskSubItems", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTaskItems.mockResolvedValue([]);
    mockAddTaskItem.mockResolvedValue({ success: true });
    mockToggleTaskItem.mockResolvedValue({ success: true });
    mockDeleteTaskItem.mockResolvedValue({ success: true });
  });

  afterEach(() => cleanup());

  it("renders items with checkboxes", async () => {
    mockFetchTaskItems.mockResolvedValue([item1, item2]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Collect IRP5s")).toBeInTheDocument();
      expect(screen.getByText("Verify tax certificates")).toBeInTheDocument();
    });

    // Both should have toggle checkboxes
    expect(screen.getByLabelText("Toggle Collect IRP5s")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Toggle Verify tax certificates")
    ).toBeInTheDocument();
  });

  it("shows progress indicator", async () => {
    mockFetchTaskItems.mockResolvedValue([item1, item2, item3]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("2/3")).toBeInTheDocument();
    });
  });

  it("toggles item on checkbox click", async () => {
    mockFetchTaskItems.mockResolvedValue([item1]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Collect IRP5s")).toBeInTheDocument();
    });

    const checkbox = screen.getByLabelText("Toggle Collect IRP5s");
    await userEvent.click(checkbox);

    expect(mockToggleTaskItem).toHaveBeenCalledWith(
      "test-org",
      "proj-1",
      "task-1",
      "item-1"
    );
  });

  it("adds new item via input form", async () => {
    mockFetchTaskItems.mockResolvedValue([]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Add a sub-item...")).toBeInTheDocument();
    });

    const input = screen.getByPlaceholderText("Add a sub-item...");
    await userEvent.type(input, "New sub-item");

    const addButton = screen.getByRole("button", { name: "Add" });
    await userEvent.click(addButton);

    expect(mockAddTaskItem).toHaveBeenCalledWith(
      "test-org",
      "proj-1",
      "task-1",
      "New sub-item",
      0
    );
  });

  it("shows delete button only when canManage is true", async () => {
    mockFetchTaskItems.mockResolvedValue([item1]);

    // canManage = false — no delete button
    const { unmount } = render(<TaskSubItems {...defaultProps} canManage={false} />);

    await waitFor(() => {
      expect(screen.getByText("Collect IRP5s")).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Delete Collect IRP5s")).not.toBeInTheDocument();

    unmount();

    // canManage = true — delete button present
    render(<TaskSubItems {...defaultProps} canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText("Collect IRP5s")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Delete Collect IRP5s")).toBeInTheDocument();
  });

  it("shows empty state when no items", async () => {
    mockFetchTaskItems.mockResolvedValue([]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("No sub-items yet")).toBeInTheDocument();
    });
  });

  it("completed items have line-through styling", async () => {
    mockFetchTaskItems.mockResolvedValue([item2]);

    render(<TaskSubItems {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Verify tax certificates")).toBeInTheDocument();
    });

    const titleEl = screen.getByTestId("item-title-item-2");
    expect(titleEl.className).toContain("line-through");
    expect(titleEl.className).toContain("text-slate-400");
  });
});
