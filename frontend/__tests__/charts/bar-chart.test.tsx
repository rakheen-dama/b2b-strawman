import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BarChartPreset } from "@/components/charts/bar-chart";
import { formatChartValue } from "@/lib/chart-format";

// `mode.real` switches every mocked export to the real recharts for the un-mocked smoke renders
// (same technique as the donut test).
const mode = vi.hoisted(() => ({ real: false }));
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  const pick = <P extends object>(
    name: keyof typeof actual,
    fake: (props: P) => React.ReactNode
  ) => {
    const Real = actual[name] as unknown as React.ComponentType<P>;
    function Switch(props: P) {
      return mode.real ? <Real {...props} /> : fake(props);
    }
    Switch.displayName = `Mock${String(name)}`;
    return Switch;
  };
  return {
    ...actual,
    ResponsiveContainer: pick(
      "ResponsiveContainer",
      ({ children }: { children: React.ReactNode }) => (
        <div data-testid="responsive-container">{children}</div>
      )
    ),
    BarChart: pick(
      "BarChart",
      ({
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
      )
    ),
    Bar: pick(
      "Bar",
      ({
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
      )
    ),
    CartesianGrid: pick("CartesianGrid", () => null),
    XAxis: pick("XAxis", () => null),
    YAxis: pick("YAxis", () => null),
    LabelList: pick(
      "LabelList",
      ({ content }: { content?: React.ReactElement<{ text?: string; targetIndex?: number }> }) =>
        content ? (
          <div
            data-testid="name-label"
            data-text={content.props.text}
            data-target={content.props.targetIndex}
          />
        ) : (
          <div data-testid="label-list" />
        )
    ),
    ReferenceLine: pick("ReferenceLine", () => <div data-testid="reference-line" />),
    Tooltip: pick("Tooltip", () => null),
    Legend: pick("Legend", () => <div data-testid="legend" />),
  };
});

/**
 * The real recharts: happy-dom has no layout, so a fixed 480×240 box (a 24px legend row) and a
 * silent observer; and
 * reduced motion, so bars draw at their final size (recharts hides labels while animating).
 */
function withRealRecharts() {
  mode.real = true;
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query: string) =>
      ({
        matches: query === "(prefers-reduced-motion: reduce)",
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList
  );
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (
    this: HTMLElement
  ) {
    // The legend row is measured too: give it a row's height, not the whole box.
    const height = this.closest(".recharts-legend-wrapper") ? 24 : 240;
    return {
      width: 480,
      height,
      top: 0,
      left: 0,
      right: 480,
      bottom: height,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    };
  });
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  );
}

beforeEach(() => {
  mode.real = false;
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
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

  it("shows its title as the header by default; hideHeader keeps only the figure's name (640B)", () => {
    const props = {
      title: "WIP by fee earner",
      data: wip,
      categoryKey: "earner",
      series: [{ key: "wip", label: "WIP" }],
      format: "currency" as const,
    };
    render(<BarChartPreset {...props} />);
    expect(screen.getByText("WIP by fee earner")).toBeInTheDocument();
    expect(document.querySelector('[data-slot="card-header"]')).not.toBeNull();
    cleanup();

    render(<BarChartPreset {...props} hideHeader />);
    const figure = screen.getByRole("figure", { name: "WIP by fee earner" });
    expect(figure.querySelector('[data-slot="card-header"]')).toBeNull();
    expect(screen.queryByText("WIP by fee earner")).toBeNull();
    expect(figure.querySelector('[data-slot="chart-summary"]')?.textContent).toMatch(
      /^WIP by fee earner: 3 bars;/
    );
    expect(within(figure).getByRole("button", { name: "View as table" })).toBeInTheDocument();
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

  it("states the series and categories a non-additive truncation leaves off the chart", async () => {
    const user = userEvent.setup();
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Earner ${i}` }));
    const data = Array.from({ length: 3 }, (_, i) => ({
      month: `M${i + 1}`,
      s0: null,
      s1: null,
      s2: null,
      s3: null,
      s4: null,
      s5: 30 + i,
    }));
    render(
      <BarChartPreset
        title="Sparse utilization"
        data={data}
        categoryKey="month"
        series={series}
        layout="grouped"
        format="percent"
        maxBars={2}
      />
    );

    expect(screen.queryByTestId("bar-s5")).toBeNull();
    expect(document.querySelector('[data-slot="chart-more"]')?.textContent).toBe(
      "+1 more in the table, +1 more series in the table"
    );
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toMatch(
      /; \+1 more in the table, \+1 more series in the table\.$/
    );
    await user.click(screen.getByRole("button", { name: "View as table" }));
    expect(
      within(screen.getByRole("table")).getByRole("columnheader", { name: "Earner 5" })
    ).toBeInTheDocument();
  });

  it("stays ready with the table reachable when truncation leaves only empty bars", async () => {
    const user = userEvent.setup();
    const data = [
      ...Array.from({ length: 4 }, (_, i) => ({ earner: `E${i + 1}`, util: null })),
      { earner: "E5", util: 30 },
    ];
    render(
      <BarChartPreset
        title="Utilization"
        data={data}
        categoryKey="earner"
        series={[{ key: "util", label: "Utilization" }]}
        format="percent"
        maxBars={4}
      />
    );

    expect(screen.getByRole("figure", { name: "Utilization" })).toHaveAttribute(
      "data-state",
      "ready"
    );
    expect(document.querySelector('[data-slot="empty-state"]')).toBeNull();
    expect(screen.getByText("+1 more in the table")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "View as table" }));
    const table = screen.getByRole("table");
    expect(within(table).getByRole("rowheader", { name: "E5" })).toBeInTheDocument();
    expect(
      within(table).getByRole("cell", { name: formatChartValue(30, "percent") })
    ).toBeInTheDocument();
  });

  it("targets each series' own last valued bar for its name label", () => {
    render(
      <BarChartPreset
        title="Gaps"
        data={[
          { month: "Sep", billed: 10, collected: null },
          { month: "Oct", billed: 12, collected: 11 },
          { month: "Nov", billed: 9, collected: null },
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

    // Grouped bars: recharts indexes labels over the drawn bars, so Collected's only bar is 0.
    expect(screen.getAllByTestId("name-label").map((l) => l.getAttribute("data-target"))).toEqual([
      "2",
      "0",
    ]);
  });

  it("renders series-name labels with the real recharts, despite a missing earlier value", async () => {
    withRealRecharts();
    render(
      <BarChartPreset
        title="Real columns"
        data={[
          { month: "Sep", billed: 10, collected: null },
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

    const figure = screen.getByRole("figure", { name: "Real columns" });
    // Collected draws two bars (Sep has no value): five rectangles in all.
    await waitFor(() => expect(figure.querySelectorAll(".recharts-bar-rectangle")).toHaveLength(5));
    await waitFor(() => {
      const texts = Array.from(figure.querySelectorAll("svg.recharts-surface text")).map(
        (t) => t.textContent
      );
      expect(texts).toContain("Billed");
      expect(texts).toContain("Collected");
    });
  });

  it("places a negative horizontal grouped bar's label beyond its negative end", async () => {
    withRealRecharts();
    render(
      <BarChartPreset
        title="Real ranking"
        data={[
          { matter: "A", fees: 10, writeoff: 4 },
          { matter: "B", fees: 12, writeoff: -6 },
        ]}
        categoryKey="matter"
        series={[
          { key: "fees", label: "Fees" },
          { key: "writeoff", label: "Write-off" },
        ]}
        orientation="horizontal"
        layout="grouped"
        format="count"
      />
    );

    const figure = screen.getByRole("figure", { name: "Real ranking" });
    const label = (name: string) =>
      Array.from(figure.querySelectorAll("svg.recharts-surface text")).find(
        (t) => t.textContent === name
      );
    await waitFor(() => expect(label("Write-off")).toBeDefined());
    expect(label("Write-off")).toHaveAttribute("text-anchor", "end");
    expect(label("Fees")).toHaveAttribute("text-anchor", "start");
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
