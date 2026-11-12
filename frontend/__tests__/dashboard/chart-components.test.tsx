import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MicroStackedBar } from "@/components/dashboard/micro-stacked-bar";
import { ChartTooltip } from "@/components/dashboard/chart-tooltip";
import { DonutChart } from "@/components/dashboard/donut-chart";

// Mock Recharts for DonutChart tests — SVG rendering doesn't work in happy-dom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: ({
    children,
    data,
  }: {
    children: React.ReactNode;
    data: Array<{ name: string; value: number }>;
  }) => (
    <div data-testid="pie">
      {data?.map((entry, i) => (
        <span key={i} data-testid={`pie-entry-${i}`}>
          {entry.name}: {entry.value}
        </span>
      ))}
      {children}
    </div>
  ),
  Cell: ({ fill }: { fill: string }) => <div data-testid="cell" data-fill={fill} />,
  Legend: () => <div data-testid="legend" />,
  Tooltip: ({ content }: { content: React.ReactElement }) => (
    <div data-testid="tooltip">{content}</div>
  ),
}));

describe("MicroStackedBar", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders segments proportionally", () => {
    render(
      <MicroStackedBar
        segments={[
          { value: 30, color: "red", label: "A" },
          { value: 70, color: "blue", label: "B" },
        ]}
      />
    );

    const bar = screen.getByTestId("micro-stacked-bar");
    expect(bar).toBeInTheDocument();

    const segments = screen.getAllByTestId("segment");
    expect(segments).toHaveLength(2);
    expect(segments[0]).toHaveAttribute("title", "A: 30");
    expect(segments[1]).toHaveAttribute("title", "B: 70");
  });

  it("handles single segment", () => {
    render(<MicroStackedBar segments={[{ value: 100, color: "green", label: "Only" }]} />);

    const segments = screen.getAllByTestId("segment");
    expect(segments).toHaveLength(1);
    expect(segments[0]).toHaveAttribute("title", "Only: 100");
  });
});

describe("DonutChart", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders with center content", () => {
    render(
      <DonutChart
        data={[
          { name: "A", value: 40 },
          { name: "B", value: 60 },
        ]}
        centerValue="$1.2k"
        centerLabel="Revenue"
      />
    );

    expect(screen.getByTestId("donut-chart")).toBeInTheDocument();
    expect(screen.getByTestId("pie-chart")).toBeInTheDocument();
    expect(screen.getByText("A: 40")).toBeInTheDocument();
    expect(screen.getByText("B: 60")).toBeInTheDocument();

    // Center content renders as HTML overlay
    expect(screen.getByText("$1.2k")).toBeInTheDocument();
    expect(screen.getByText("Revenue")).toBeInTheDocument();
  });
});

describe("ChartTooltip", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders with theme styling", () => {
    render(
      <ChartTooltip
        active={true}
        payload={[
          { name: "Revenue", value: 1200, color: "#ff0000" },
          { name: "Cost", value: 800, color: "#0000ff" },
        ]}
        label="January"
      />
    );

    const tooltip = screen.getByTestId("chart-tooltip");
    expect(tooltip).toBeInTheDocument();
    expect(screen.getByText("January")).toBeInTheDocument();
    expect(screen.getByText("Revenue")).toBeInTheDocument();
    expect(screen.getByText("1200")).toBeInTheDocument();
    expect(screen.getByText("Cost")).toBeInTheDocument();
    expect(screen.getByText("800")).toBeInTheDocument();
  });
});
