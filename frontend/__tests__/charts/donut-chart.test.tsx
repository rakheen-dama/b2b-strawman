import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DonutChart } from "@/components/charts/donut-chart";
import { formatChartValue } from "@/lib/chart-format";

// Only the recharts exports the kit donut and ui/chart touch (same surface as the My Work mocks).
// `mode.real` switches every export to the real recharts for the un-mocked smoke render.
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
    PieChart: pick("PieChart", ({ children }: { children: React.ReactNode }) => (
      <div data-testid="pie-chart">{children}</div>
    )),
    Pie: pick(
      "Pie",
      ({
        data,
        isAnimationActive,
        label,
      }: {
        data: unknown[];
        isAnimationActive?: boolean;
        label?: unknown;
      }) => (
        <div
          data-testid="pie"
          data-rows={JSON.stringify(data)}
          data-animate={String(isAnimationActive)}
          data-direct-labels={String(Boolean(label))}
        />
      )
    ),
    Tooltip: pick("Tooltip", () => <div />),
  };
});

function mockReducedMotion(reduce: boolean) {
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query: string) =>
      ({
        matches: reduce && query === "(prefers-reduced-motion: reduce)",
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList
  );
}

beforeEach(() => {
  mode.real = false;
  mockReducedMotion(true);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

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
    // Legend row (≥ 2 segments) plus segment-name direct labels (≤ 4 segments).
    const legend = document.querySelector('[data-slot="chart-legend"]');
    expect(legend).toHaveTextContent("Conveyancing");
    expect(legend).toHaveTextContent("Litigation");
    expect(screen.getByTestId("pie")).toHaveAttribute("data-direct-labels", "true");
    // Reduced motion → no entry animation.
    expect(screen.getByTestId("pie")).toHaveAttribute("data-animate", "false");
    expect(pieRows().map((r) => r.fill)).toEqual(["var(--chart-1)", "var(--chart-2)"]);
  });

  it("animates when reduced motion is not requested and pins colour slots", () => {
    mockReducedMotion(false);
    render(
      <DonutChart
        title="Pinned"
        segments={[
          { key: "a", label: "Conveyancing", value: 30, slot: 1 },
          { key: "c", label: "Estates", value: 10, slot: 3 },
        ]}
        format="hours"
      />
    );

    expect(screen.getByTestId("pie")).toHaveAttribute("data-animate", "true");
    // "Litigation" (slot 2) was filtered out; Estates keeps slot 3.
    expect(pieRows().map((r) => r.fill)).toEqual(["var(--chart-1)", "var(--chart-3)"]);
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
    // Five segments: legend only, no direct labels.
    expect(screen.getByTestId("pie")).toHaveAttribute("data-direct-labels", "false");
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
      <DonutChart
        title="Empty donut"
        segments={segments}
        format="hours"
        showLegend={false}
        directLabels={false}
      />
    );
    expect(screen.getByTestId("pie-chart")).toBeInTheDocument();
    expect(document.querySelector('[data-slot="chart-legend"]')).toBeNull();
    expect(screen.getByTestId("pie")).toHaveAttribute("data-direct-labels", "false");
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

  it("renders with the real recharts: sectors, in-token segment labels and the centre", async () => {
    mode.real = true;
    // happy-dom has no layout: give ResponsiveContainer a fixed 320×200 box and a silent observer.
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
      width: 320,
      height: 200,
      top: 0,
      left: 0,
      right: 320,
      bottom: 200,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
    );
    render(
      <DonutChart
        title="Real donut"
        segments={segments}
        format="hours"
        centerValue="40.0h"
        centerLabel="total"
      />
    );

    const figure = screen.getByRole("figure", { name: "Real donut" });
    await waitFor(() => expect(figure.querySelectorAll(".recharts-pie-sector")).toHaveLength(2));
    // recharts 3 portals labels into a z-index layer, so read every <text> in the surface.
    const labels = Array.from(figure.querySelectorAll("svg.recharts-surface text"));
    expect(labels.map((t) => t.textContent)).toEqual(["Conveyancing", "Litigation"]);
    expect(labels[0]).toHaveAttribute("fill", "var(--muted-foreground)");
    expect(screen.getByText("40.0h")).toBeInTheDocument();
  });
});
