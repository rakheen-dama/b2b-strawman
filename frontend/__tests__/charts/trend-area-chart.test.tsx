import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TrendAreaChart } from "@/components/charts/trend-area-chart";
import { formatChartValue } from "@/lib/chart-format";

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  AreaChart: ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
    <div data-testid="area-chart" data-rows={JSON.stringify(data)}>
      {children}
    </div>
  ),
  Area: ({
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
  ),
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
  LabelList: () => null,
  Tooltip: () => null,
  Legend: () => <div data-testid="legend" />,
}));

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
