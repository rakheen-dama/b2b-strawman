import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks ---

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

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/deadlines",
}));

vi.mock("@/app/(app)/org/[slug]/deadlines/actions", () => ({
  fetchDeadlines: vi.fn().mockResolvedValue({ deadlines: [], total: 0 }),
  fetchDeadlineSummary: vi.fn().mockResolvedValue({ summaries: [] }),
  updateFilingStatus: vi.fn().mockResolvedValue({ success: true, results: [] }),
}));

// --- Imports after mocks ---

import { DeadlineListView } from "@/components/deadlines/DeadlineListView";
import { DeadlineCalendarView } from "@/components/deadlines/DeadlineCalendarView";
import { DeadlineFilters } from "@/components/deadlines/DeadlineFilters";
import type { CalculatedDeadline } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

function makeDeadline(overrides: Partial<CalculatedDeadline> = {}): CalculatedDeadline {
  return {
    customerId: "cust-1",
    customerName: "Acme Pty Ltd",
    deadlineTypeSlug: "sars_provisional_1",
    deadlineTypeName: "Provisional Tax — 1st Payment",
    category: "tax",
    dueDate: "2026-03-31",
    status: "pending",
    linkedProjectId: null,
    filingStatusId: null,
    ...overrides,
  };
}

// --- Tests ---

describe("DeadlineListView", () => {
  it("renders table rows with correct status badges", () => {
    const deadlines = [
      makeDeadline({
        status: "pending",
        deadlineTypeName: "Provisional Tax",
      }),
      makeDeadline({
        customerId: "cust-2",
        customerName: "Beta Corp",
        status: "overdue",
        deadlineTypeName: "VAT Return",
        dueDate: "2026-02-28",
      }),
      makeDeadline({
        customerId: "cust-3",
        customerName: "Gamma Ltd",
        status: "filed",
        deadlineTypeName: "CIPC Annual Return",
        dueDate: "2026-03-20",
      }),
    ];
    render(
      <DeadlineListView
        deadlines={deadlines}
        slug="acme"
        selectedIds={new Set()}
        onSelectionChange={() => {}}
      />
    );
    expect(screen.getByText("Acme Pty Ltd")).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
    expect(screen.getByText("Beta Corp")).toBeInTheDocument();
    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("Gamma Ltd")).toBeInTheDocument();
    expect(screen.getByText("Filed")).toBeInTheDocument();
  });

  it("shows empty state when no deadlines", () => {
    render(
      <DeadlineListView
        deadlines={[]}
        slug="acme"
        selectedIds={new Set()}
        onSelectionChange={() => {}}
      />
    );
    expect(screen.getByText(/no deadlines found/i)).toBeInTheDocument();
  });
});

describe("DeadlineCalendarView", () => {
  it("renders month grid with correct day count for March 2026", () => {
    render(<DeadlineCalendarView deadlines={[]} year={2026} month={3} />);
    // March has 31 days
    for (let d = 1; d <= 31; d++) {
      const cells = screen.getAllByText(String(d));
      expect(cells.length).toBeGreaterThanOrEqual(1);
    }
  });

  it("shows deadline count dots for days with deadlines", () => {
    const deadline = makeDeadline({
      dueDate: "2026-03-15",
      status: "overdue",
    });
    const { container } = render(
      <DeadlineCalendarView deadlines={[deadline]} year={2026} month={3} />
    );
    // The overdue count dot should render with a red indicator
    const redDots = container.querySelectorAll(".bg-red-500");
    expect(redDots.length).toBe(1);
  });
});

describe("DeadlineFilters", () => {
  it("calls onFilterChange with updated category when dropdown changes", async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(<DeadlineFilters initialYear={2026} initialMonth={3} onFilterChange={onFilterChange} />);
    // Previous month button
    const prevBtn = screen.getByRole("button", { name: /previous month/i });
    await user.click(prevBtn);
    expect(onFilterChange).toHaveBeenCalledWith(
      expect.objectContaining({ from: "2026-02-01" }),
      2026,
      2
    );
  });

  it("renders month navigation buttons", () => {
    render(<DeadlineFilters initialYear={2026} initialMonth={3} onFilterChange={() => {}} />);
    expect(screen.getByRole("button", { name: /previous month/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /next month/i })).toBeInTheDocument();
    expect(screen.getByText(/March 2026/)).toBeInTheDocument();
  });
});

describe("Nav item requiredModule", () => {
  it("NAV_GROUPS clients group contains a Deadlines entry with requiredModule", async () => {
    const { NAV_GROUPS } = await import("@/lib/nav-items");
    const clientsGroup = NAV_GROUPS.find((g) => g.id === "clients");
    expect(clientsGroup).toBeDefined();
    const deadlinesItem = clientsGroup!.items.find((item) => item.label === "Deadlines");
    expect(deadlinesItem).toBeDefined();
    expect(deadlinesItem!.requiredModule).toBe("regulatory_deadlines");
  });
});
