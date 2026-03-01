import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { CalendarMonthView } from "../calendar-month-view";
import { CalendarListView } from "../calendar-list-view";
import type { CalendarItem } from "../calendar-types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

function makeItem(overrides: Partial<CalendarItem> = {}): CalendarItem {
  return {
    id: "item-1",
    name: "Test Task",
    itemType: "TASK",
    dueDate: "2026-03-15",
    status: "OPEN",
    priority: "MEDIUM",
    assigneeId: null,
    projectId: "proj-1",
    projectName: "Test Project",
    ...overrides,
  };
}

// --- Month View Tests ---

describe("CalendarMonthView", () => {
  const mockOnNavigate = vi.fn();
  const baseProps = {
    year: 2026,
    month: 3, // March (31 days)
    onNavigate: mockOnNavigate,
    isPending: false,
    slug: "acme",
  };

  it("renders_month_grid_with_correct_day_count", () => {
    render(<CalendarMonthView {...baseProps} items={[]} />);
    // March 2026 has 31 days — each day cell has the day number
    for (let d = 1; d <= 31; d++) {
      const cells = screen.getAllByText(String(d));
      expect(cells.length).toBeGreaterThanOrEqual(1);
    }
  });

  it("shows_task_dot_on_due_date", () => {
    const item = makeItem({ dueDate: "2026-03-15", name: "Important Task" });
    render(<CalendarMonthView {...baseProps} items={[item]} />);
    expect(screen.getByTitle("Important Task")).toBeInTheDocument();
  });

  it("shows_plus_n_more_when_multiple_items_on_same_day", () => {
    const items = Array.from({ length: 4 }, (_, i) =>
      makeItem({ id: `item-${i}`, name: `Task ${i}`, dueDate: "2026-03-10" })
    );
    render(<CalendarMonthView {...baseProps} items={items} />);
    expect(screen.getByText("+1")).toBeInTheDocument();
  });

  it("navigates_to_next_month_on_button_click", async () => {
    const user = userEvent.setup();
    render(<CalendarMonthView {...baseProps} items={[]} />);
    await user.click(screen.getByRole("button", { name: "Next month" }));
    expect(mockOnNavigate).toHaveBeenCalledWith(2026, 4);
  });
});

// --- List View Tests ---

describe("CalendarListView", () => {
  it("renders_items_grouped_by_week", () => {
    const items = [
      makeItem({ id: "a", dueDate: "2026-03-02", name: "Early March" }),
      makeItem({ id: "b", dueDate: "2026-03-16", name: "Mid March" }),
    ];
    render(<CalendarListView items={items} slug="acme" />);
    const weekHeaders = screen.getAllByText(/Week of/);
    expect(weekHeaders).toHaveLength(2);
    expect(screen.getByText("Early March")).toBeInTheDocument();
    expect(screen.getByText("Mid March")).toBeInTheDocument();
  });

  it("shows_overdue_section_when_enabled", () => {
    const overdueItems = [
      makeItem({
        id: "ov1",
        dueDate: "2026-02-01",
        name: "Very Overdue Task",
        status: "OPEN",
      }),
    ];
    render(
      <CalendarListView items={[]} overdueItems={overdueItems} slug="acme" />
    );
    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("Very Overdue Task")).toBeInTheDocument();
  });

  it("applies_red_color_to_overdue_items", () => {
    const overdueItems = [
      makeItem({
        id: "ov2",
        dueDate: "2026-01-15",
        name: "Old Task",
        status: "OPEN",
      }),
    ];
    const { container } = render(
      <CalendarListView items={[]} overdueItems={overdueItems} slug="acme" />
    );
    const overdueSection = container.querySelector(".border-red-200");
    expect(overdueSection).toBeInTheDocument();
  });
});
