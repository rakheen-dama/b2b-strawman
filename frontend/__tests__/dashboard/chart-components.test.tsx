import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { Sparkline } from "@/components/dashboard/sparkline";
import { RadialGauge } from "@/components/dashboard/radial-gauge";
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

describe("Sparkline", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SVG polyline with data points", () => {
    const { container } = render(<Sparkline data={[10, 20, 15, 30, 25]} />);

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute("width", "80");
    expect(svg).toHaveAttribute("height", "24");

    const polyline = container.querySelector("polyline");
    expect(polyline).toBeInTheDocument();
    expect(polyline).toHaveAttribute("points");
    expect(polyline!.getAttribute("points")!.split(" ").length).toBe(5);

    // Gradient fill polygon should also be present
    const polygon = container.querySelector("polygon");
    expect(polygon).toBeInTheDocument();
  });

  it("handles empty data array gracefully", () => {
    const { container } = render(<Sparkline data={[]} />);

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();

    // No polyline or polygon when data is empty
    const polyline = container.querySelector("polyline");
    expect(polyline).not.toBeInTheDocument();
  });

  it("respects custom width and height", () => {
    const { container } = render(<Sparkline data={[5, 10, 15]} width={120} height={40} />);

    const svg = container.querySelector("svg");
    expect(svg).toHaveAttribute("width", "120");
    expect(svg).toHaveAttribute("height", "40");
  });
});

describe("RadialGauge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders value text", () => {
    render(<RadialGauge value={75} />);

    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByTestId("radial-gauge")).toBeInTheDocument();
  });

  it("applies teal for value in optimal range", () => {
    const { container } = render(<RadialGauge value={75} />);

    // Foreground arc path should use teal color
    const paths = container.querySelectorAll("path");
    const fgPath = paths[1]; // second path is foreground
    expect(fgPath).toHaveAttribute("stroke", "var(--color-teal-500)");
  });

  it("applies slate for under-threshold value", () => {
    const { container } = render(<RadialGauge value={30} />);

    const paths = container.querySelectorAll("path");
    const fgPath = paths[1];
    expect(fgPath).toHaveAttribute("stroke", "var(--color-slate-400)");
  });

  it("applies amber for over-threshold value", () => {
    const { container } = render(<RadialGauge value={95} />);

    const paths = container.querySelectorAll("path");
    const fgPath = paths[1];
    expect(fgPath).toHaveAttribute("stroke", "var(--color-amber-500, #f59e0b)");
  });

  it("handles edge cases 0 and 100", () => {
    const { rerender } = render(<RadialGauge value={0} />);
    expect(screen.getByText("0%")).toBeInTheDocument();

    rerender(<RadialGauge value={100} />);
    expect(screen.getByText("100%")).toBeInTheDocument();
  });
});

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
