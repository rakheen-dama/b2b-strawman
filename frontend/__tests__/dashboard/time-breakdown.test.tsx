import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import type { PersonalProjectBreakdown } from "@/lib/dashboard-types";

// Mock recharts to avoid rendering issues in happy-dom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({
    children,
  }: {
    children: React.ReactNode;
  }) => <div data-testid="responsive-container">{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="bar-chart">{children}</div>
  ),
  Bar: () => <div data-testid="bar" />,
  XAxis: () => <div />,
  YAxis: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div data-testid="legend" />,
}));

const mockBreakdown: PersonalProjectBreakdown[] = [
  {
    projectId: "p1",
    projectName: "Website Redesign",
    hours: 20.0,
    percent: 51.9,
  },
  {
    projectId: "p2",
    projectName: "Mobile App",
    hours: 12.5,
    percent: 32.5,
  },
  {
    projectId: "p3",
    projectName: "API Migration",
    hours: 6.0,
    percent: 15.6,
  },
];

describe("TimeBreakdown", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders bars and labels for project breakdown", () => {
    render(<TimeBreakdown data={mockBreakdown} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
    expect(screen.getByText("20.0h (52%)")).toBeInTheDocument();
    expect(screen.getByText("12.5h (33%)")).toBeInTheDocument();
    expect(screen.getByText("6.0h (16%)")).toBeInTheDocument();
    expect(screen.getByTestId("responsive-container")).toBeInTheDocument();
  });

  it("renders empty state when data is empty", () => {
    render(<TimeBreakdown data={[]} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(
      screen.getByText("No time logged this period.")
    ).toBeInTheDocument();
  });

  it("renders error state when data is null", () => {
    render(<TimeBreakdown data={null} />);

    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load time breakdown. Please try again.")
    ).toBeInTheDocument();
  });
});
