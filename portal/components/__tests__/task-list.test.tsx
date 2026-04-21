import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { TaskList } from "@/components/task-list";

describe("TaskList", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders tasks with name, status, and assignee", () => {
    render(
      <TaskList
        tasks={[
          {
            id: "t-1",
            name: "Design homepage",
            status: "IN_PROGRESS",
            assigneeName: "Alice",
            sortOrder: 1,
          },
          {
            id: "t-2",
            name: "Write tests",
            status: "OPEN",
            assigneeName: null,
            sortOrder: 2,
          },
        ]}
      />,
    );

    // Both mobile and desktop layouts render; use getAllByText
    expect(screen.getAllByText("Design homepage").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("IN PROGRESS").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Alice").length).toBeGreaterThanOrEqual(1);

    expect(screen.getAllByText("Write tests").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("OPEN").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Unassigned").length).toBeGreaterThanOrEqual(1);
  });

  it("shows empty state when no tasks", () => {
    render(<TaskList tasks={[]} />);

    expect(screen.getByText("No tasks yet.")).toBeInTheDocument();
  });

  it("renders status badges with task variant", () => {
    render(
      <TaskList
        tasks={[
          {
            id: "t-3",
            name: "Done task",
            status: "DONE",
            assigneeName: "Bob",
            sortOrder: 1,
          },
        ]}
      />,
    );

    expect(screen.getAllByText("DONE").length).toBeGreaterThanOrEqual(1);
  });
});
