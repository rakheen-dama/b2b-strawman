import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { AssignedTaskList } from "@/components/my-work/assigned-task-list";
import type { MyWorkTaskItem } from "@/lib/types";

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
}));

// --- Test data ---

function makeTask(overrides: Partial<MyWorkTaskItem> = {}): MyWorkTaskItem {
  return {
    id: "t1",
    projectId: "p1",
    projectName: "Project Alpha",
    title: "Implement feature X",
    status: "OPEN",
    priority: "MEDIUM",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

describe("AssignedTaskList", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders tasks with project names, titles, and count badge", () => {
    const tasks = [
      makeTask({ id: "t1", projectName: "Project Alpha", title: "Task One" }),
      makeTask({ id: "t2", projectName: "Project Beta", title: "Task Two" }),
    ];

    render(<AssignedTaskList tasks={tasks} slug="acme" />);

    expect(screen.getByText("My Tasks")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument(); // count badge
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
    expect(screen.getByText("Task One")).toBeInTheDocument();
    expect(screen.getByText("Task Two")).toBeInTheDocument();
  });

  it("shows empty state when no tasks assigned", () => {
    render(<AssignedTaskList tasks={[]} slug="acme" />);

    expect(screen.getByText("No tasks assigned")).toBeInTheDocument();
    expect(
      screen.getByText("Tasks assigned to you will appear here")
    ).toBeInTheDocument();
  });

  it("renders priority badge with correct variant", () => {
    const tasks = [
      makeTask({ id: "t1", priority: "HIGH", title: "High priority task" }),
      makeTask({ id: "t2", priority: "LOW", title: "Low priority task" }),
    ];

    render(<AssignedTaskList tasks={tasks} slug="acme" />);

    const highBadge = screen.getByText("High");
    expect(highBadge).toHaveAttribute("data-variant", "destructive");

    const lowBadge = screen.getByText("Low");
    expect(lowBadge).toHaveAttribute("data-variant", "neutral");
  });

  it("displays overdue dates with red styling for open tasks", () => {
    const tasks = [
      makeTask({
        id: "t1",
        title: "Overdue task",
        status: "OPEN",
        dueDate: "2020-01-01",
      }),
    ];

    const { container } = render(
      <AssignedTaskList tasks={tasks} slug="acme" />
    );

    const dateSpan = container.querySelector("[class*='text-red']");
    expect(dateSpan).toBeInTheDocument();
  });
});
