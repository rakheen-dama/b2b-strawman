import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

import { StatCard, type StatCardDelta } from "@b2mash/ui/stat-card";
import { formatChartValue } from "@/lib/chart-format";

afterEach(() => {
  cleanup();
});

const upPercent: StatCardDelta = {
  change: 12.5,
  unit: "percent",
  polarity: "up-is-good",
  comparisonLabel: "previous 30 days",
};

function deltaRow(container: HTMLElement) {
  return container.querySelector('[data-slot="stat-card-delta"]');
}

describe("StatCard", () => {
  it("ready: the group is named by its label and shows the caller-formatted value", () => {
    render(<StatCard label="Billable hours" value="42,5h" hint="Last 30 days" data-testid="kpi" />);

    const group = screen.getByRole("group", { name: "Billable hours" });
    expect(group).toHaveAttribute("data-state", "ready");
    expect(group).toHaveAttribute("data-testid", "kpi");
    expect(within(group).getByText("42,5h")).toHaveClass("font-mono", "tabular-nums");
    expect(within(group).getByText("Last 30 days")).toBeInTheDocument();
  });

  it("loading: renders skeletons for value and delta, no dash, aria-busy", () => {
    const { container } = render(
      <StatCard label="Revenue" value="R 1" state="loading" delta={upPercent} />
    );

    const group = screen.getByRole("group", { name: "Revenue" });
    expect(group).toHaveAttribute("aria-busy", "true");
    expect(container.querySelector('[data-slot="stat-card-value-skeleton"]')).not.toBeNull();
    expect(container.querySelector('[data-slot="stat-card-delta-skeleton"]')).not.toBeNull();
    expect(screen.queryByText("—")).toBeNull();
    expect(screen.queryByText("R 1")).toBeNull();
    expect(deltaRow(container)).toBeNull();
  });

  it("redacted: dash + sr 'Not available' + caller hint, and no delta even when passed", () => {
    const { container } = render(
      <StatCard
        label="Fees billed"
        value="R 99"
        state="redacted"
        hint="Visible to admins and owners"
        delta={upPercent}
        sparkline={<svg data-testid="spark" />}
      />
    );

    expect(screen.getByText("—")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText("Not available")).toHaveClass("sr-only");
    expect(screen.getByText("Visible to admins and owners")).toBeInTheDocument();
    expect(screen.queryByText("R 99")).toBeNull();
    expect(deltaRow(container)).toBeNull();
    expect(screen.queryByTestId("spark")).toBeNull();
  });

  it("error: dash + role=status 'Couldn't load', no delta", () => {
    const { container } = render(
      <StatCard label="Trust balance" value="R 5" state="error" delta={upPercent} />
    );

    expect(screen.getByRole("status")).toHaveTextContent("Couldn't load");
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.queryByText("R 5")).toBeNull();
    expect(deltaRow(container)).toBeNull();
  });

  it("delta up (percent): signed en-ZA figure, sr sentence and positive tone", () => {
    const { container } = render(<StatCard label="Hours" value="10h" delta={upPercent} />);

    expect(screen.getByText("+12,5%")).toBeInTheDocument();
    expect(screen.getByText("Up 12,5% vs previous 30 days")).toHaveClass("sr-only");
    expect(screen.getByText("vs previous 30 days")).toHaveAttribute("aria-hidden", "true");
    const row = deltaRow(container);
    expect(row).toHaveAttribute("data-tone", "positive");
    expect(row?.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("delta down (percent): minus figure, 'Down' sr text, negative tone", () => {
    const { container } = render(
      <StatCard label="Hours" value="8h" delta={{ ...upPercent, change: -3.24 }} />
    );

    expect(screen.getByText("−3,2%")).toBeInTheDocument();
    expect(screen.getByText("Down 3,2% vs previous 30 days")).toBeInTheDocument();
    expect(deltaRow(container)).toHaveAttribute("data-tone", "negative");
  });

  it("change: null renders no delta row", () => {
    const { container } = render(
      <StatCard label="Hours" value="8h" delta={{ ...upPercent, change: null }} />
    );
    expect(deltaRow(container)).toBeNull();
    expect(screen.queryByText(/vs previous 30 days/)).toBeNull();
  });

  it("absent delta renders no delta row (point-in-time tile)", () => {
    const { container } = render(<StatCard label="Open matters" value="12" />);
    expect(deltaRow(container)).toBeNull();
  });

  it("points: visible figure ends 'pp' and the sr text says 'percentage points'", () => {
    render(
      <StatCard
        label="Billable %"
        value="62,5%"
        delta={{
          change: 4.5,
          unit: "points",
          polarity: "up-is-good",
          comparisonLabel: "last month",
        }}
      />
    );

    expect(screen.getByText("+4,5 pp")).toBeInTheDocument();
    expect(screen.getByText("Up 4,5 percentage points vs last month")).toHaveClass("sr-only");
  });

  it("down-is-good inverts the tone", () => {
    const { container, rerender } = render(
      <StatCard label="Overdue" value="3" delta={{ ...upPercent, polarity: "down-is-good" }} />
    );
    // An increase on a down-is-good metric is bad.
    expect(screen.getByText("+12,5%")).toBeInTheDocument();
    expect(deltaRow(container)).toHaveAttribute("data-tone", "negative");

    rerender(
      <StatCard
        label="Overdue"
        value="3"
        delta={{ ...upPercent, change: -10, polarity: "down-is-good" }}
      />
    );
    // A decrease on a down-is-good metric is good.
    expect(screen.getByText("−10,0%")).toBeInTheDocument();
    expect(deltaRow(container)).toHaveAttribute("data-tone", "positive");
  });

  it("neutral polarity and a flat change are muted; flat says 'No change'", () => {
    const { container, rerender } = render(
      <StatCard label="Matters" value="4" delta={{ ...upPercent, polarity: "neutral" }} />
    );
    expect(deltaRow(container)).toHaveAttribute("data-tone", "neutral");
    expect(screen.getByText("Up 12,5% vs previous 30 days")).toBeInTheDocument();

    rerender(<StatCard label="Matters" value="4" delta={{ ...upPercent, change: 0.02 }} />);
    const row = deltaRow(container);
    expect(row).toHaveAttribute("data-tone", "neutral");
    expect(row?.querySelector("svg")).toBeNull();
    expect(screen.getByText("No change vs previous 30 days")).toBeInTheDocument();
  });

  it("default shows the sparkline in an aria-hidden wrapper; compact hides it", () => {
    const { container, rerender } = render(
      <StatCard label="Hours" value="10h" sparkline={<svg data-testid="spark" />} />
    );
    const wrapper = container.querySelector('[data-slot="stat-card-sparkline"]');
    expect(wrapper).toHaveAttribute("aria-hidden", "true");
    expect(within(wrapper as HTMLElement).getByTestId("spark")).toBeInTheDocument();

    rerender(
      <StatCard
        label="Hours"
        value="10h"
        variant="compact"
        delta={upPercent}
        sparkline={<svg data-testid="spark" />}
      />
    );
    const group = screen.getByRole("group", { name: "Hours" });
    expect(group).toHaveAttribute("data-variant", "compact");
    expect(screen.queryByTestId("spark")).toBeNull();
    // Compact keeps the figure and the full sr sentence, drops the visible comparison label.
    expect(screen.getByText("+12,5%")).toBeInTheDocument();
    expect(screen.getByText("Up 12,5% vs previous 30 days")).toBeInTheDocument();
    expect(screen.queryByText("vs previous 30 days")).toBeNull();
  });

  it("ready with value null renders a dash and sr 'Not available'", () => {
    render(<StatCard label="Next court date" value={null} hint="None scheduled" />);

    const group = screen.getByRole("group", { name: "Next court date" });
    expect(within(group).getByText("—")).toHaveAttribute("aria-hidden", "true");
    expect(within(group).getByText("Not available")).toHaveClass("sr-only");
    expect(within(group).getByText("None scheduled")).toBeInTheDocument();
  });

  it("icon is decorative and the footer slot renders", () => {
    const { container } = render(
      <StatCard
        label="Hours"
        value="10h"
        icon={<svg data-testid="icon" />}
        footer={<a href="/time">View time</a>}
      />
    );
    expect(container.querySelector('[data-slot="stat-card-icon"]')).toHaveAttribute(
      "aria-hidden",
      "true"
    );
    expect(screen.getByRole("link", { name: "View time" })).toHaveAttribute("href", "/time");
  });
});

// StatCard formats its delta with toFixed(1) (no Intl in the package); chart-format uses the
// polyfilled en-ZA Intl. Pin that both render the same figure for the same signed change.
describe("StatCard delta figure ↔ chart-format parity", () => {
  const values = [12.5, 2.25, 0.05, -3.14, -0.04, 99.95];

  function visibleFigure(change: number, unit: StatCardDelta["unit"]): string {
    const { container, unmount } = render(
      <StatCard label="Parity" value="1" delta={{ ...upPercent, change, unit }} />
    );
    const figure = container.querySelector('[data-slot="stat-card-delta-figure"]')?.textContent;
    unmount();
    return figure ?? "";
  }

  it.each(values)("points %s: the full signed figure matches formatChartValue", (change) => {
    expect(visibleFigure(change, "points")).toBe(formatChartValue(change, "points"));
  });

  // chart-format's "percent" kind is unsigned (Intl hyphen-minus) while StatCard signs with
  // "+" / U+2212, so only the absolute numeric portion is comparable here.
  it.each(values)("percent %s: the absolute figure matches formatChartValue", (change) => {
    const unsigned = visibleFigure(change, "percent").replace(/^[+−]/, "");
    expect(unsigned).toBe(formatChartValue(Math.abs(change), "percent"));
  });
});
