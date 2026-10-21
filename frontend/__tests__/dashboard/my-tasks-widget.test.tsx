import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MyTasksWidget } from "@/components/dashboard/my-tasks-widget";
import type { MyWorkTaskItem } from "@/lib/types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const TODAY = "2026-10-21";

function task(o: Partial<MyWorkTaskItem> & { id: string }): MyWorkTaskItem {
  return {
    matterId: "m1",
    matterName: "Ndlovu v RAF",
    title: `Task ${o.id}`,
    status: "OPEN",
    priority: "MEDIUM",
    dueDate: null,
    totalTimeMinutes: 0,
    ...o,
  };
}

describe("MyTasksWidget", () => {
  afterEach(() => cleanup());

  it("top 5 open assigned tasks, soonest due first, undated last; done/cancelled dropped", () => {
    const assigned = [
      task({ id: "a", dueDate: null }),
      task({ id: "b", dueDate: "2026-10-25" }),
      task({ id: "c", dueDate: "2026-10-19" }),
      task({ id: "d", dueDate: "2026-10-22", status: "DONE" }),
      task({ id: "e", dueDate: "2026-10-23", status: "CANCELLED" }),
      task({ id: "f", dueDate: "2026-10-21", status: "IN_PROGRESS" }),
      task({ id: "g", dueDate: "2026-11-01" }),
      task({ id: "h", dueDate: "2026-11-02" }),
    ];
    render(<MyTasksWidget slug="acme" today={TODAY} data={{ assigned, unassigned: [] }} />);

    const rows = within(screen.getByRole("list", { name: "My open tasks" })).getAllByRole("link");
    expect(rows.map((r) => r.textContent?.match(/Task \w/)?.[0])).toEqual([
      "Task c",
      "Task f",
      "Task b",
      "Task g",
      "Task h",
    ]);
    expect(screen.getByTestId("my-tasks-headline")).toHaveTextContent("6");
    expect(screen.getByText("open tasks, 1 overdue")).toBeInTheDocument();
    expect(rows[0]).toHaveAttribute("href", "/org/acme/matters/m1?taskId=c");
  });

  it("overdue rows carry an Overdue badge; others don't", () => {
    render(
      <MyTasksWidget
        slug="acme"
        today={TODAY}
        data={{
          assigned: [
            task({ id: "late", dueDate: "2026-10-01" }),
            task({ id: "soon", dueDate: TODAY }),
          ],
          unassigned: [],
        }}
      />
    );
    const late = screen.getByRole("link", { name: /Task late/ });
    const soon = screen.getByRole("link", { name: /Task soon/ });
    expect(within(late).getByText("Overdue")).toBeInTheDocument();
    expect(within(soon).queryByText("Overdue")).not.toBeInTheDocument();
  });

  it("links to My work; empty state says what to do next; ignores unassigned tasks", () => {
    render(
      <MyTasksWidget
        slug="acme"
        today={TODAY}
        data={{ assigned: [], unassigned: [task({ id: "u" })] }}
      />
    );
    expect(screen.getByRole("link", { name: "Open my work" })).toHaveAttribute(
      "href",
      "/org/acme/my-work"
    );
    expect(screen.getByText(/Nothing assigned to you/)).toBeInTheDocument();
    expect(screen.queryByText("Task u")).not.toBeInTheDocument();
  });

  it("error: quiet error line", () => {
    render(<MyTasksWidget slug="acme" today={TODAY} data={null} />);
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
  });
});
