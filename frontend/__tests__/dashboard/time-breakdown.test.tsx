import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import type { PersonalMatterBreakdown } from "@/lib/dashboard-types";

// Mock recharts to avoid rendering issues in happy-dom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: () => <div />,
  Cell: () => <div />,
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="bar-chart">{children}</div>
  ),
  Bar: () => <div data-testid="bar" />,
  XAxis: () => <div />,
  YAxis: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div data-testid="legend" />,
  CartesianGrid: () => <div data-testid="cartesian-grid" />,
  ReferenceLine: () => <div data-testid="reference-line" />,
}));

const mockBreakdown: PersonalMatterBreakdown[] = [
  {
    matterId: "p1",
    matterName: "Website Redesign",
    hours: 20.0,
    percent: 51.9,
  },
  {
    matterId: "p2",
    matterName: "Mobile App",
    hours: 12.5,
    percent: 32.5,
  },
  {
    matterId: "p3",
    matterName: "API Migration",
    hours: 6.0,
    percent: 15.6,
  },
];

describe("TimeBreakdown", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders bars and labels for project breakdown", async () => {
    render(<TimeBreakdown data={mockBreakdown} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
    expect(screen.getByText("20.0h (52%)")).toBeInTheDocument();
    expect(screen.getByText("12.5h (33%)")).toBeInTheDocument();
    expect(screen.getByText("6.0h (16%)")).toBeInTheDocument();
    // The kit DonutChart: a figure named by the card title, centre total overlay.
    const figure = screen.getByRole("figure", { name: "Time Breakdown" });
    expect(figure).toHaveAttribute("data-testid", "time-activity-panel");
    expect(screen.getByTestId("pie-chart")).toBeInTheDocument();
    expect(screen.getByText("38.5h")).toBeInTheDocument();
    expect(screen.getByText("total")).toBeInTheDocument();
    // The card keeps its own matter list, so the chart draws no second legend.
    expect(screen.queryByTestId("legend")).toBeNull();
  });

  it("states the breakdown in an sr-only summary and a table on demand", async () => {
    const user = userEvent.setup();
    render(<TimeBreakdown data={mockBreakdown} />);

    const summary = document.querySelector('[data-slot="chart-summary"]');
    expect(summary?.textContent).toMatch(/^Time Breakdown: .* total — Website Redesign /);

    expect(screen.queryByRole("table")).toBeNull();
    await user.click(screen.getByRole("button", { name: "View as table" }));
    const table = screen.getByRole("table", { name: "Time Breakdown" });
    expect(within(table).getByRole("columnheader", { name: "Matter" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Hours" })).toBeInTheDocument();
    for (const name of ["Website Redesign", "Mobile App", "API Migration"]) {
      expect(within(table).getByRole("rowheader", { name })).toBeInTheDocument();
    }
  });

  it("renders empty state when data is empty", () => {
    render(<TimeBreakdown data={[]} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(screen.getByText("No time logged this period.")).toBeInTheDocument();
  });

  it("renders error state when data is null", () => {
    render(<TimeBreakdown data={null} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load time breakdown. Please try again.")
    ).toBeInTheDocument();
  });
});
