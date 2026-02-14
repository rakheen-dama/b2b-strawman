import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { HorizontalBarChart } from "@/components/dashboard/horizontal-bar-chart";

// Recharts SVG rendering doesn't work in happy-dom — mock all components
// to render testable DOM elements instead
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  BarChart: ({
    children,
    data,
  }: {
    children: React.ReactNode;
    data: Array<Record<string, string | number>>;
  }) => (
    <div data-testid="bar-chart">
      {data?.map((item, i) => (
        <span key={i} data-testid={`bar-label-${i}`}>
          {item.label}
        </span>
      ))}
      {children}
    </div>
  ),
  Bar: ({ dataKey, fill }: { dataKey: string; fill: string }) => (
    <div data-testid={`bar-${dataKey}`} data-fill={fill} />
  ),
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: ({
    children,
    ...props
  }: {
    children?: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <div data-testid="legend" {...(props as Record<string, unknown>)}>
      {children}
    </div>
  ),
}));

const SAMPLE_DATA = [
  {
    label: "Alice",
    segments: [
      { label: "Website Redesign", value: 20, color: "#2563eb" },
      { label: "Mobile App", value: 12.5, color: "#e11d48" },
    ],
  },
  {
    label: "Bob",
    segments: [
      { label: "Website Redesign", value: 15, color: "#2563eb" },
      { label: "API Integration", value: 8, color: "#8b5cf6" },
    ],
  },
];

describe("HorizontalBarChart", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders bars for given data", () => {
    render(<HorizontalBarChart data={SAMPLE_DATA} />);

    // Y-axis labels rendered via BarChart mock
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();

    // Should have Bar components for each unique segment
    expect(screen.getByTestId("bar-Website Redesign")).toBeInTheDocument();
    expect(screen.getByTestId("bar-Mobile App")).toBeInTheDocument();
    expect(screen.getByTestId("bar-API Integration")).toBeInTheDocument();
  });

  it("renders legend when showLegend is true", () => {
    render(<HorizontalBarChart data={SAMPLE_DATA} showLegend />);

    expect(screen.getByTestId("legend")).toBeInTheDocument();
  });

  it("handles empty data", () => {
    render(<HorizontalBarChart data={[]} />);

    expect(screen.getByText("No data available")).toBeInTheDocument();
    expect(screen.queryByTestId("bar-chart")).not.toBeInTheDocument();
  });
});
