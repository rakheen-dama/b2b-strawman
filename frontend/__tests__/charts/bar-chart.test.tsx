import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BarChartPreset } from "@/components/charts/bar-chart";
import { formatChartValue } from "@/lib/chart-format";

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  BarChart: ({
    children,
    data,
    layout,
  }: {
    children: React.ReactNode;
    data: unknown[];
    layout?: string;
  }) => (
    <div data-testid="bar-chart" data-rows={JSON.stringify(data)} data-layout={layout}>
      {children}
    </div>
  ),
  Bar: ({
    dataKey,
    fill,
    stackId,
    children,
  }: {
    dataKey: string;
    fill?: string;
    stackId?: string;
    children?: React.ReactNode;
  }) => (
    <div data-testid={`bar-${dataKey}`} data-fill={fill} data-stack={stackId}>
      {children}
    </div>
  ),
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
  LabelList: ({
    content,
  }: {
    content?: React.ReactElement<{ text?: string; targetIndex?: number }>;
  }) =>
    content ? (
      <div
        data-testid="name-label"
        data-text={content.props.text}
        data-target={content.props.targetIndex}
      />
    ) : (
      <div data-testid="label-list" />
    ),
  ReferenceLine: () => <div data-testid="reference-line" />,
  Tooltip: () => null,
  Legend: () => <div data-testid="legend" />,
}));

afterEach(() => cleanup());

function chartRows(): Array<Record<string, unknown>> {
  return JSON.parse(screen.getByTestId("bar-chart").getAttribute("data-rows") ?? "[]");
}

const wip = [
  { earner: "Alice", wip: 84000 },
  { earner: "Bob", wip: 41000 },
  { earner: "Carol", wip: 3200 },
];

describe("BarChartPreset", () => {
  it("names the figure, summarises highest and lowest, and has no legend for one series", () => {
    render(
      <BarChartPreset
        title="WIP by fee earner"
        data={wip}
        categoryKey="earner"
        series={[{ key: "wip", label: "WIP" }]}
        format="currency"
      />
    );

    expect(screen.getByRole("figure", { name: "WIP by fee earner" })).toBeInTheDocument();
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toBe(
      `WIP by fee earner: 3 bars; highest Alice ${formatChartValue(84000, "currency")}, ` +
        `lowest Carol ${formatChartValue(3200, "currency")}.`
    );
    expect(screen.queryByTestId("legend")).toBeNull();
    expect(screen.getByTestId("bar-wip")).toHaveAttribute("data-fill", "var(--chart-1)");
    // Single series → direct value labels, no series-name labels.
    expect(screen.getByTestId("label-list")).toBeInTheDocument();
    expect(screen.queryByTestId("name-label")).toBeNull();
  });

  it("folds categories beyond maxBars into one Other bar", () => {
    const data = Array.from({ length: 12 }, (_, i) => ({ earner: `E${i + 1}`, hours: i + 1 }));
    render(
      <BarChartPreset
        title="Hours by earner"
        data={data}
        categoryKey="earner"
        series={[{ key: "hours", label: "Hours" }]}
        format="hours"
        orientation="horizontal"
        maxBars={5}
      />
    );

    const rows = chartRows();
    expect(rows).toHaveLength(5);
    expect(rows.map((r) => r.earner)).toEqual(["E1", "E2", "E3", "E4", "Other"]);
    // E5..E12 = 5 + 6 + … + 12.
    expect(rows[4].hours).toBe(68);
    // Horizontal bars = recharts' vertical axis layout.
    expect(screen.getByTestId("bar-chart")).toHaveAttribute("data-layout", "vertical");
  });

  it("colours negatives with --div-neg about a zero line when diverging", () => {
    render(
      <BarChartPreset
        title="Margin by matter"
        data={[
          { matter: "Estate late Mokoena", margin: 12.5 },
          { matter: "Smith v Jones", margin: -8 },
        ]}
        categoryKey="matter"
        series={[{ key: "margin", label: "Margin" }]}
        format="percent"
        orientation="horizontal"
        diverging
      />
    );

    const rows = chartRows();
    expect(rows[0].fill).toBe("var(--div-pos)");
    expect(rows[1].fill).toBe("var(--div-neg)");
    expect(screen.getByTestId("bar-margin")).not.toHaveAttribute("data-fill");
    expect(screen.getByTestId("reference-line")).toBeInTheDocument();
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toContain(
      "1 below zero"
    );
  });

  it("stacks up to five series with a legend and folds a sixth into Other", () => {
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Series ${i}` }));
    const data = [{ month: "Nov", s0: 1, s1: 2, s2: 3, s3: 4, s4: 5, s5: 6 }];
    render(
      <BarChartPreset
        title="Stacked six"
        data={data}
        categoryKey="month"
        series={series}
        layout="stacked"
        format="count"
      />
    );

    expect(screen.getByTestId("legend")).toBeInTheDocument();
    expect(screen.queryByTestId("bar-s4")).toBeNull();
    const other = screen.getByTestId("bar-__other");
    expect(other).toHaveAttribute("data-fill", "var(--muted-foreground)");
    expect(other).toHaveAttribute("data-stack", "stack");
    expect(chartRows()[0].__other).toBe(11);
    // Five drawn series: legend only — no value labels and no direct labels past four.
    expect(screen.queryByTestId("label-list")).toBeNull();
    expect(screen.queryByTestId("name-label")).toBeNull();
  });

  it("direct-labels 2–4 grouped series by name on the last category, beside the legend", () => {
    render(
      <BarChartPreset
        title="Billed vs collected"
        data={[
          { month: "Sep", billed: 10, collected: 8 },
          { month: "Oct", billed: 12, collected: 11 },
          { month: "Nov", billed: 9, collected: 7 },
        ]}
        categoryKey="month"
        series={[
          { key: "billed", label: "Billed" },
          { key: "collected", label: "Collected" },
        ]}
        layout="grouped"
        format="currency"
      />
    );

    expect(screen.getByTestId("legend")).toBeInTheDocument();
    const labels = screen.getAllByTestId("name-label");
    expect(labels.map((l) => l.getAttribute("data-text"))).toEqual(["Billed", "Collected"]);
    // The endpoint (last category) carries the label, never every bar.
    expect(labels.map((l) => l.getAttribute("data-target"))).toEqual(["2", "2"]);
    expect(screen.queryByTestId("label-list")).toBeNull();
  });

  it("keeps a series' colour slot when a filter removes another series", () => {
    const data = [{ month: "Nov", fees: 5, disb: 2, trust: 9 }];
    const all = [
      { key: "fees", label: "Fees", slot: 1 as const },
      { key: "disb", label: "Disbursements", slot: 2 as const },
      { key: "trust", label: "Trust", slot: 3 as const },
    ];
    const { rerender } = render(
      <BarChartPreset
        title="Slots"
        data={data}
        categoryKey="month"
        series={all}
        layout="grouped"
        format="currency"
      />
    );
    expect(screen.getByTestId("bar-trust")).toHaveAttribute("data-fill", "var(--chart-3)");

    rerender(
      <BarChartPreset
        title="Slots"
        data={data}
        categoryKey="month"
        series={all.filter((s) => s.key !== "disb")}
        layout="grouped"
        format="currency"
      />
    );
    expect(screen.getByTestId("bar-fees")).toHaveAttribute("data-fill", "var(--chart-1)");
    expect(screen.getByTestId("bar-trust")).toHaveAttribute("data-fill", "var(--chart-3)");
  });

  it("never sums a non-additive tail: percent bars truncate to maxBars and state +N more", async () => {
    const user = userEvent.setup();
    const data = Array.from({ length: 7 }, (_, i) => ({ earner: `E${i + 1}`, util: 90 - i * 5 }));
    render(
      <BarChartPreset
        title="Utilization"
        data={data}
        categoryKey="earner"
        series={[{ key: "util", label: "Utilization" }]}
        format="percent"
        orientation="horizontal"
        maxBars={4}
      />
    );

    const rows = chartRows();
    expect(rows.map((r) => r.earner)).toEqual(["E1", "E2", "E3", "E4"]);
    expect(rows.some((r) => r.earner === "Other")).toBe(false);
    expect(screen.getByText("+3 more in the table")).toBeInTheDocument();
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toMatch(
      /^Utilization: 4 bars; .*; \+3 more in the table\.$/
    );

    // The table keeps every row the chart leaves off.
    await user.click(screen.getByRole("button", { name: "View as table" }));
    const table = screen.getByRole("table");
    expect(within(table).getAllByRole("rowheader")).toHaveLength(7);
    expect(within(table).getByRole("rowheader", { name: "E7" })).toBeInTheDocument();
  });

  it("renders loading, empty and error states without the chart", () => {
    const props = {
      categoryKey: "earner",
      series: [{ key: "wip", label: "WIP" }],
      format: "currency" as const,
    };
    const { rerender } = render(
      <BarChartPreset title="WIP states" data={wip} loading {...props} />
    );
    expect(document.querySelector('[data-slot="skeleton"]')).toBeInTheDocument();

    rerender(<BarChartPreset title="WIP states" data={[]} {...props} />);
    expect(document.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "compact"
    );

    rerender(
      <BarChartPreset title="WIP states" data={wip} error="Could not load WIP." {...props} />
    );
    expect(screen.getByRole("status")).toHaveTextContent("Could not load WIP.");
    expect(screen.queryByTestId("bar-chart")).toBeNull();
  });
});
