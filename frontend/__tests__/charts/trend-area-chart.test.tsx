import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TrendAreaChart } from "@/components/charts/trend-area-chart";
import { formatChartValue } from "@/lib/chart-format";

// `mode.real` switches every mocked export to the real recharts for the un-mocked smoke renders
// (same technique as the donut test).
const mode = vi.hoisted(() => ({ real: false }));
// Props the mocked Tooltip hands its `content` (recharts' activeIndex/label/payload), or none.
const tooltip = vi.hoisted(() => ({ props: null as Record<string, unknown> | null }));
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
    AreaChart: pick(
      "AreaChart",
      ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
        <div data-testid="area-chart" data-rows={JSON.stringify(data)}>
          {children}
        </div>
      )
    ),
    Area: pick(
      "Area",
      ({
        dataKey,
        stroke,
        strokeDasharray,
        isAnimationActive,
      }: {
        dataKey: string;
        stroke?: string;
        strokeDasharray?: string;
        isAnimationActive?: boolean;
      }) => (
        <div
          data-testid={`area-${dataKey}`}
          data-stroke={stroke}
          data-dash={strokeDasharray}
          data-animate={String(isAnimationActive)}
        />
      )
    ),
    CartesianGrid: pick("CartesianGrid", () => null),
    XAxis: pick("XAxis", () => null),
    YAxis: pick("YAxis", () => null),
    LabelList: pick("LabelList", () => null),
    Tooltip: pick("Tooltip", ({ content }: { content?: unknown }) => {
      if (!tooltip.props || typeof content !== "function") return null;
      const Content = content as React.ComponentType<Record<string, unknown>>;
      return (
        <div data-testid="tooltip">
          <Content {...tooltip.props} />
        </div>
      );
    }),
    Legend: pick("Legend", () => <div data-testid="legend" />),
  };
});

/**
 * The real recharts: happy-dom has no layout, so a fixed 480×240 box (a 24px legend row) and a
 * silent observer; and reduced motion, so areas draw at their final shape (recharts hides labels
 * while animating).
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
  tooltip.props = null;
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
afterEach(() => cleanup());

const balance = [
  { month: "Sep 2026", trust: 120000 },
  { month: "Oct 2026", trust: 210000 },
  { month: "Nov 2026", trust: 184500 },
];

describe("TrendAreaChart", () => {
  it("names the figure by its title and summarises first, last and peak", () => {
    render(
      <TrendAreaChart
        title="Trust balance"
        data={balance}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
      />
    );

    expect(screen.getByRole("figure", { name: "Trust balance" })).toBeInTheDocument();
    const summary = document.querySelector('[data-slot="chart-summary"]');
    expect(summary?.textContent).toBe(
      `Trust balance: from ${formatChartValue(120000, "currency")} in Sep 2026 to ` +
        `${formatChartValue(184500, "currency")} in Nov 2026, peak ` +
        `${formatChartValue(210000, "currency")} in Oct 2026.`
    );
    // A single series needs no legend box (the title names it).
    expect(screen.queryByTestId("legend")).toBeNull();
    expect(screen.getByTestId("area-trust")).toHaveAttribute("data-stroke", "var(--chart-1)");
  });

  it("draws the incomplete last period dashed and labels it (to date)", async () => {
    const user = userEvent.setup();
    render(
      <TrendAreaChart
        title="Trust balance to date"
        data={balance}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
        partialLastPoint
      />
    );

    expect(screen.getByTestId("area-trust__partial")).toHaveAttribute("data-dash", "4 4");
    expect(screen.getByTestId("area-trust")).not.toHaveAttribute("data-dash");
    const chartRows = JSON.parse(
      screen.getByTestId("area-chart").getAttribute("data-rows") ?? "[]"
    );
    expect(chartRows[2].trust).toBeNull();
    expect(chartRows.map((r: Record<string, unknown>) => r.trust__partial)).toEqual([
      null,
      210000,
      184500,
    ]);
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toContain(
      "in Nov 2026 (to date)"
    );

    await user.click(screen.getByRole("button", { name: "View as table" }));
    expect(
      within(screen.getByRole("table")).getByRole("rowheader", { name: "Nov 2026 (to date)" })
    ).toBeInTheDocument();
  });

  it("folds a sixth series into Other and shows a legend", async () => {
    const user = userEvent.setup();
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Series ${i}` }));
    const data = [
      { month: "Oct", s0: 1, s1: 2, s2: 3, s3: 4, s4: 5, s5: 6 },
      { month: "Nov", s0: 1, s1: 2, s2: 3, s3: 4, s4: 5, s5: 6 },
    ];
    render(
      <TrendAreaChart title="Six series" data={data} xKey="month" series={series} format="count" />
    );

    expect(screen.getByTestId("legend")).toBeInTheDocument();
    expect(screen.getByTestId("area-__other")).toHaveAttribute(
      "data-stroke",
      "var(--muted-foreground)"
    );
    expect(screen.queryByTestId("area-s4")).toBeNull();

    await user.click(screen.getByRole("button", { name: "View as table" }));
    const headers = within(screen.getByRole("table"))
      .getAllByRole("columnheader")
      .map((h) => h.textContent);
    expect(headers).toEqual(["Period", "Series 0", "Series 1", "Series 2", "Series 3", "Other"]);
    // Other = s4 + s5.
    expect(within(screen.getByRole("table")).getAllByRole("cell", { name: "11" })).toHaveLength(2);
  });

  it("never sums non-additive series: six percent series draw five, the table keeps six", async () => {
    const user = userEvent.setup();
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Earner ${i}` }));
    const data = [{ month: "Nov", s0: 80, s1: 70, s2: 60, s3: 50, s4: 40, s5: 30 }];
    render(
      <TrendAreaChart
        title="Utilization"
        data={data}
        xKey="month"
        categoryLabel="Month"
        series={series}
        format="percent"
      />
    );

    expect(screen.queryByTestId("area-__other")).toBeNull();
    expect(screen.getByTestId("area-s4")).toHaveAttribute("data-stroke", "var(--chart-5)");
    expect(screen.queryByTestId("area-s5")).toBeNull();

    await user.click(screen.getByRole("button", { name: "View as table" }));
    const headers = within(screen.getByRole("table"))
      .getAllByRole("columnheader")
      .map((h) => h.textContent);
    expect(headers).toEqual(["Month", ...series.map((s) => s.label)]);
  });

  it("stays ready with the table reachable when truncation leaves only empty series", async () => {
    const user = userEvent.setup();
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Earner ${i}` }));
    const data = [{ month: "Nov", s0: null, s1: null, s2: null, s3: null, s4: null, s5: 30 }];
    render(
      <TrendAreaChart
        title="Sparse utilization"
        data={data}
        xKey="month"
        series={series}
        format="percent"
      />
    );

    expect(screen.getByRole("figure", { name: "Sparse utilization" })).toHaveAttribute(
      "data-state",
      "ready"
    );
    await user.click(screen.getByRole("button", { name: "View as table" }));
    expect(
      within(screen.getByRole("table")).getByRole("cell", {
        name: formatChartValue(30, "percent"),
      })
    ).toBeInTheDocument();
  });

  it("states the series a non-additive truncation leaves off the chart", () => {
    const series = Array.from({ length: 6 }, (_, i) => ({ key: `s${i}`, label: `Earner ${i}` }));
    const data = [{ month: "Nov", s0: null, s1: null, s2: null, s3: null, s4: null, s5: 30 }];
    render(
      <TrendAreaChart
        title="Sparse utilization"
        data={data}
        xKey="month"
        series={series}
        format="percent"
      />
    );

    expect(document.querySelector('[data-slot="chart-more"]')?.textContent).toBe(
      "+1 more series in the table"
    );
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toMatch(
      /; \+1 more series in the table\.$/
    );
  });

  it("adds no truncation note when every series is drawn", () => {
    render(
      <TrendAreaChart
        title="Trust balance"
        data={balance}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="percent"
      />
    );

    expect(document.querySelector('[data-slot="chart-more"]')).toBeNull();
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).not.toContain(
      "more"
    );
  });

  it("labels the tooltip (to date) by the hovered index, not by a repeated x label", () => {
    // An earlier period shares the last period's label.
    const data = [
      { week: "Wk 1", trust: 10 },
      { week: "Wk 2", trust: 20 },
      { week: "Wk 1", trust: 30 },
    ];
    const hover = (activeIndex: string, dataKey: string, value: number) => {
      tooltip.props = {
        active: true,
        activeIndex,
        label: "Wk 1",
        payload: [{ dataKey, name: "Trust", value, payload: {} }],
      };
    };
    const chart = () => (
      <TrendAreaChart
        title="Weekly trust"
        data={data}
        xKey="week"
        series={[{ key: "trust", label: "Trust" }]}
        format="count"
        partialLastPoint
      />
    );

    hover("0", "trust", 10);
    const { rerender } = render(chart());
    expect(within(screen.getByTestId("tooltip")).getByText("Wk 1")).toBeInTheDocument();
    expect(within(screen.getByTestId("tooltip")).queryByText("Wk 1 (to date)")).toBeNull();

    hover("2", "trust__partial", 30);
    rerender(chart());
    expect(within(screen.getByTestId("tooltip")).getByText("Wk 1 (to date)")).toBeInTheDocument();
  });

  it("treats NaN and Infinity as no value", () => {
    render(
      <TrendAreaChart
        title="Not finite"
        data={[
          { month: "Oct", trust: Number.NaN },
          { month: "Nov", trust: Number.POSITIVE_INFINITY },
        ]}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
      />
    );

    expect(screen.getByRole("figure", { name: "Not finite" })).toHaveAttribute(
      "data-state",
      "empty"
    );
  });

  it("labels a single incomplete period (to date) though it cannot be dashed", async () => {
    const user = userEvent.setup();
    render(
      <TrendAreaChart
        title="First month"
        data={[{ month: "Nov 2026", trust: 5000 }]}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
        partialLastPoint
      />
    );

    expect(screen.queryByTestId("area-trust__partial")).toBeNull();
    expect(document.querySelector('[data-slot="chart-summary"]')?.textContent).toContain(
      "in Nov 2026 (to date)"
    );
    await user.click(screen.getByRole("button", { name: "View as table" }));
    expect(
      within(screen.getByRole("table")).getByRole("rowheader", { name: "Nov 2026 (to date)" })
    ).toBeInTheDocument();
  });

  it("dots a lone point and an isolated point between gaps (real recharts)", async () => {
    withRealRecharts();
    const { rerender } = render(
      <TrendAreaChart
        title="Lone point"
        data={[{ month: "Nov", trust: 5 }]}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="count"
      />
    );
    const dots = () => document.querySelectorAll(".recharts-area-dots circle");
    await waitFor(() => expect(dots()).toHaveLength(1));

    rerender(
      <TrendAreaChart
        title="Lone point"
        data={[
          { month: "Aug", trust: 5 },
          { month: "Sep", trust: null },
          { month: "Oct", trust: 7 },
          { month: "Nov", trust: 8 },
        ]}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="count"
      />
    );
    // Aug has no valued neighbour; Oct–Nov draw a line.
    await waitFor(() => expect(dots()).toHaveLength(1));
  });

  it("puts a line-end label at the series' last valued point, never at y = 0 (real recharts)", async () => {
    withRealRecharts();
    render(
      <TrendAreaChart
        title="Two lines"
        data={[
          { month: "Sep", fees: 10, disb: 4 },
          { month: "Oct", fees: 12, disb: 6 },
          { month: "Nov", fees: 11, disb: null },
        ]}
        xKey="month"
        series={[
          { key: "fees", label: "Fees" },
          { key: "disb", label: "Disbursements" },
        ]}
        format="count"
      />
    );

    const label = (name: string) =>
      Array.from(document.querySelectorAll("svg.recharts-surface text")).find(
        (t) => t.textContent === name
      );
    await waitFor(() => expect(label("Disbursements")).toBeDefined());
    expect(label("Fees")).toBeDefined();
    expect(Number(label("Disbursements")?.getAttribute("y"))).toBeGreaterThan(0);
    // Oct, not Nov: left of the Fees label at the Nov end.
    expect(Number(label("Disbursements")?.getAttribute("x"))).toBeLessThan(
      Number(label("Fees")?.getAttribute("x"))
    );
  });

  // 643A OBS D3: trust's cashbook and client-ledger lines end at (almost) the same value; their
  // end labels drew on top of each other. Converging ends fall back to the legend, and the right
  // margin the labels needed goes with them.
  it("drops overlapping end labels and their margin when the series converge (real recharts)", async () => {
    withRealRecharts();
    const converging = [
      { month: "Sep 2026", cashbook: 120000, ledgers: 119000 },
      { month: "Oct 2026", cashbook: 210000, ledgers: 208000 },
      { month: "Nov 2026", cashbook: 290249.98, ledgers: 290000 },
    ];
    const series = [
      { key: "cashbook", label: "Cashbook" },
      { key: "ledgers", label: "Client ledgers" },
    ];
    const svgText = () =>
      Array.from(document.querySelectorAll("svg.recharts-surface text")).map((t) => t.textContent);
    // The plot clip spans the drawable width: 480 − 64 (y axis) − the left/right margins.
    const plotWidth = () =>
      Number(document.querySelector("svg.recharts-surface clipPath rect")?.getAttribute("width"));

    const { rerender } = render(
      <TrendAreaChart
        title="Converging"
        data={converging}
        xKey="month"
        series={series}
        format="currency"
      />
    );
    const curves = () => document.querySelectorAll("path.recharts-area-curve").length;
    // Drawn (both curves) with neither end label, and the legend still names both series.
    await waitFor(() => expect(curves()).toBe(2));
    await waitFor(() => expect(plotWidth()).toBe(480 - 64 - 12));
    expect(svgText()).not.toContain("Cashbook");
    expect(svgText()).not.toContain("Client ledgers");
    const legend = document.querySelector(".recharts-legend-wrapper") as HTMLElement;
    expect(legend).toHaveTextContent("Cashbook");
    expect(legend).toHaveTextContent("Client ledgers");

    // Same with the dashed partial last period (the trust dashboard's shape).
    rerender(
      <TrendAreaChart
        title="Converging"
        data={converging}
        xKey="month"
        series={series}
        format="currency"
        partialLastPoint
      />
    );
    await waitFor(() => expect(plotWidth()).toBe(480 - 64 - 12));
    expect(svgText()).not.toContain("Cashbook");

    // Separated ends keep their direct labels and the room for them.
    rerender(
      <TrendAreaChart
        title="Converging"
        data={converging.map((row) => ({ ...row, ledgers: row.ledgers / 2 }))}
        xKey="month"
        series={series}
        format="currency"
      />
    );
    await waitFor(() => expect(svgText()).toContain("Client ledgers"));
    expect(svgText()).toContain("Cashbook");
    expect(plotWidth()).toBe(480 - 64 - 88);
  });

  it("shows a skeleton at chart height while loading", () => {
    render(
      <TrendAreaChart
        title="Loading trend"
        data={balance}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
        loading
        height={200}
      />
    );

    const figure = screen.getByRole("figure", { name: "Loading trend" });
    expect(figure).toHaveAttribute("aria-busy", "true");
    expect(figure.querySelector('[data-slot="skeleton"]')).toHaveStyle({ height: "200px" });
    expect(screen.queryByTestId("responsive-container")).toBeNull();
    expect(screen.queryByRole("button", { name: "View as table" })).toBeNull();
  });

  it("renders the compact empty state when there is no data", () => {
    render(
      <TrendAreaChart
        title="Empty trend"
        data={[]}
        xKey="month"
        series={[{ key: "trust", label: "Trust" }]}
        format="currency"
        emptyTitle="No postings yet."
      />
    );

    expect(document.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "compact"
    );
    expect(screen.getByText("No postings yet.")).toBeInTheDocument();
    expect(screen.queryByTestId("area-chart")).toBeNull();
  });
});
