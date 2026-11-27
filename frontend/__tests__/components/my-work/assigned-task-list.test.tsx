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
    matterId: "p1",
    matterName: "Project Alpha",
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

  it("renders the Matter column header (literal legal copy, Phase 87 611C, ADR-338)", () => {
    render(<AssignedTaskList tasks={[makeTask()]} slug="acme" />);
    expect(screen.getByText("Matter")).toBeInTheDocument();
    expect(screen.queryByText("Project")).toBeNull();
  });

  it("renders tasks with project names, titles, and count badge", () => {
    const tasks = [
      makeTask({ id: "t1", matterName: "Project Alpha", title: "Task One" }),
      makeTask({ id: "t2", matterName: "Project Beta", title: "Task Two" }),
    ];

    render(<AssignedTaskList tasks={tasks} slug="acme" />);

    expect(screen.getByText("My Tasks")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument(); // count badge
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
    expect(screen.getByText("Task One")).toBeInTheDocument();
    expect(screen.getByText("Task Two")).toBeInTheDocument();
  });

  it("shows a compact empty state under the My Tasks heading when no tasks assigned", () => {
    const { container } = render(<AssignedTaskList tasks={[]} slug="acme" />);

    expect(screen.getByRole("heading", { level: 2, name: "My Tasks" })).toBeInTheDocument();
    expect(container.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "compact"
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "No tasks assigned" })
    ).toBeInTheDocument();
    expect(screen.getByText("Tasks assigned to you will appear here")).toBeInTheDocument();
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

    const { container } = render(<AssignedTaskList tasks={tasks} slug="acme" />);

    const dateSpan = container.querySelector(".text-destructive");
    expect(dateSpan).toBeInTheDocument();
    expect(dateSpan).toHaveTextContent("1 Jan 2020");
  });

  it("truncates the matter badge and the title with their full text on hover (K-09)", () => {
    const longMatter = "Khumalo — lease dispute over the commercial premises in Braamfontein";
    const longTitle = "Prepare the heads of argument for the lease dispute hearing";
    render(
      <AssignedTaskList
        tasks={[makeTask({ matterName: longMatter, title: longTitle })]}
        slug="acme"
      />
    );

    const badge = screen.getByText(longMatter);
    expect(badge).toHaveClass("truncate", "max-w-28");
    expect(badge).toHaveAttribute("title", longMatter);
    const title = screen.getByText(longTitle);
    expect(title).toHaveClass("truncate");
    expect(title).toHaveAttribute("title", longTitle);
  });

  it("shows Priority, Status and Logged only from measured frame widths (K-09)", () => {
    render(<AssignedTaskList tasks={[makeTask({ totalTimeMinutes: 90 })]} slug="acme" />);

    expect(screen.getByRole("columnheader", { name: "Priority" })).toHaveClass(
      "hidden",
      "@min-[34rem]:table-cell"
    );
    expect(screen.getByRole("columnheader", { name: "Status" })).toHaveClass(
      "hidden",
      "@min-[34rem]:table-cell"
    );
    expect(screen.getByRole("columnheader", { name: "Logged" })).toHaveClass(
      "hidden",
      "@min-[38rem]:table-cell"
    );
    // Matter, Title and Due Date always show.
    for (const name of ["Matter", "Title", "Due Date"]) {
      expect(screen.getByRole("columnheader", { name })).not.toHaveClass("hidden");
    }
    // The kit frame is kept (DA-8): one bordered @container frame, no "Columns" menu.
    expect(screen.getByRole("table").closest(".\\@container")).toHaveClass("rounded-lg", "border");
    expect(screen.queryByRole("button", { name: /columns/i })).toBeNull();
  });
});
