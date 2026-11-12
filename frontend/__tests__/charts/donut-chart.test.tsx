import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DonutChart } from "@/components/charts/donut-chart";
import { formatChartValue } from "@/lib/chart-format";

// Only the recharts exports the kit donut and ui/chart touch (same surface as the My Work mocks).
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: ({ data, isAnimationActive }: { data: unknown[]; isAnimationActive?: boolean }) => (
    <div
      data-testid="pie"
      data-rows={JSON.stringify(data)}
      data-animate={String(isAnimationActive)}
    />
  ),
  Cell: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div data-testid="legend" />,
}));

vi.mock("motion/react", () => ({ useReducedMotion: () => true }));

afterEach(() => cleanup());

const segments = [
  { key: "a", label: "Conveyancing", value: 30 },
  { key: "b", label: "Litigation", value: 10 },
];

function pieRows(): Array<{ key: string; label: string; value: number; fill: string }> {
  return JSON.parse(screen.getByTestId("pie").getAttribute("data-rows") ?? "[]");
}

describe("DonutChart (kit)", () => {
  it("names the figure by its title and renders the centre overlay", () => {
    render(
      <DonutChart
        title="Hours by practice area"
        segments={segments}
        format="hours"
        centerValue="40.0h"
        centerLabel="logged"
      />
    );

    expect(screen.getByRole("figure", { name: "Hours by practice area" })).toBeInTheDocument();
    expect(screen.getByText("40.0h")).toBeInTheDocument();
    expect(screen.getByText("logged")).toBeInTheDocument();
    expect(screen.getByTestId("legend")).toBeInTheDocument();
    // Reduced motion → no entry animation.
    expect(screen.getByTestId("pie")).toHaveAttribute("data-animate", "false");
    expect(pieRows().map((r) => r.fill)).toEqual(["var(--chart-1)", "var(--chart-2)"]);
  });

  it("writes a one-sentence sr summary and a table with the formatted values", async () => {
    const user = userEvent.setup();
    render(<DonutChart title="Hours by area" segments={segments} format="hours" />);

    const summary = document.querySelector('[data-slot="chart-summary"]');
    expect(summary).toHaveTextContent(
      `Hours by area: ${formatChartValue(40, "hours")} total — ` +
        `Conveyancing ${formatChartValue(30, "hours")} (${formatChartValue(75, "percent")}), ` +
        `Litigation ${formatChartValue(10, "hours")} (${formatChartValue(25, "percent")}).`
    );

    await user.click(screen.getByRole("button", { name: "View as table" }));
    const table = screen.getByRole("table");
    expect(within(table).getByRole("rowheader", { name: "Conveyancing" })).toBeInTheDocument();
    expect(
      within(table).getByRole("cell", { name: formatChartValue(30, "hours") })
    ).toBeInTheDocument();
  });

  it("folds more than five segments into Other with the summed value", () => {
    const many = Array.from({ length: 7 }, (_, i) => ({
      key: `m${i}`,
      label: `Matter ${i + 1}`,
      value: 70 - i * 10,
    }));
    render(<DonutChart title="Seven matters" segments={many} format="hours" />);

    const rows = pieRows();
    expect(rows).toHaveLength(5);
    expect(rows.map((r) => r.label)).toEqual([
      "Matter 1",
      "Matter 2",
      "Matter 3",
      "Matter 4",
      "Other",
    ]);
    // Matters 5–7: 30 + 20 + 10.
    expect(rows[4].value).toBe(60);
    expect(rows[4].fill).toBe("var(--muted-foreground)");
  });

  it("renders the compact empty state and hides the legend when asked", () => {
    const { rerender } = render(
      <DonutChart title="Empty donut" segments={[]} format="hours" emptyTitle="Nothing logged." />
    );
    const empty = document.querySelector('[data-slot="empty-state"]');
    expect(empty).toHaveAttribute("data-variant", "compact");
    expect(screen.getByText("Nothing logged.")).toBeInTheDocument();
    expect(screen.queryByTestId("pie-chart")).toBeNull();

    rerender(
      <DonutChart title="Empty donut" segments={segments} format="hours" showLegend={false} />
    );
    expect(screen.getByTestId("pie-chart")).toBeInTheDocument();
    expect(screen.queryByTestId("legend")).toBeNull();
  });

  it("shows a skeleton at chart height while loading", () => {
    render(
      <DonutChart title="Loading donut" segments={segments} format="hours" loading height={160} />
    );

    const figure = screen.getByRole("figure", { name: "Loading donut" });
    expect(figure).toHaveAttribute("aria-busy", "true");
    const skeleton = figure.querySelector('[data-slot="skeleton"]');
    expect(skeleton).toHaveStyle({ height: "160px" });
    expect(screen.queryByTestId("responsive-container")).toBeNull();
  });
});
