import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { KpiRow, hoursTrendSummary } from "@/components/dashboard/kpi-row";
import { formatChartValue } from "@/lib/chart-format";
import type { KpiResponse } from "@/lib/dashboard-types";

// Phase 90 640A — Home KPI row (arch §15.3.4): four StatCards, last 30 days vs the 30 before,
// hours sparkline of six ISO weeks, Billable % redacted by role (B3), no margin tile (R1).

afterEach(() => cleanup());

const TO = "2026-11-12";
const SPARKLINE_NAME = "Hours logged per week, last 6 weeks";
const h = (v: number) => formatChartValue(v, "hours");

const ADMIN_KPIS: KpiResponse = {
  activeMatterCount: 14,
  totalHoursLogged: 42.5,
  billablePercent: 62.5,
  overdueTaskCount: 3,
  averageMarginPercent: null,
  // Sparse, as the backend sends it: W41, W43, W44, W46 missing → zero-filled.
  trend: [
    { period: "2026-W42", value: 12 },
    { period: "2026-W45", value: 7.5 },
  ],
  previousPeriod: {
    activeMatterCount: 12,
    totalHoursLogged: 34,
    billablePercent: 58,
    overdueTaskCount: 5,
    averageMarginPercent: null,
  },
};

/** What a custom role with FINANCIAL_VISIBILITY (not admin/owner) receives after P1. */
const REDACTED_KPIS: KpiResponse = {
  ...ADMIN_KPIS,
  billablePercent: null,
  previousPeriod: { ...ADMIN_KPIS.previousPeriod!, billablePercent: null },
};

const ok = (data: KpiResponse) => ({ state: "ok" as const, data });

function tile(name: string) {
  return screen.getByRole("group", { name });
}
function deltaIn(group: HTMLElement) {
  return group.querySelector('[data-slot="stat-card-delta"]');
}

describe("KpiRow", () => {
  it("admin: four values, hours % delta, billable points delta, 'Last 30 days' label", () => {
    render(<KpiRow result={ok(ADMIN_KPIS)} to={TO} seesFinancials />);

    expect(screen.getByText("Last 30 days")).toBeInTheDocument();
    expect(within(tile("Active matters")).getByText(formatChartValue(14, "count"))).toBeVisible();
    expect(within(tile("Hours logged")).getByText(h(42.5))).toBeVisible();
    expect(within(tile("Billable %")).getByText(formatChartValue(62.5, "percent"))).toBeVisible();
    expect(within(tile("Overdue tasks")).getByText(formatChartValue(3, "count"))).toBeVisible();

    // (42.5 − 34) / 34 = +25.0 %; 62.5 − 58 = +4.5 pp.
    expect(within(tile("Hours logged")).getByText("Up 25,0% vs previous 30 days")).toHaveClass(
      "sr-only"
    );
    expect(
      within(tile("Billable %")).getByText("Up 4,5 percentage points vs previous 30 days")
    ).toHaveClass("sr-only");
    // Point-in-time tiles carry no delta (rulings §3.2).
    expect(deltaIn(tile("Active matters"))).toBeNull();
    expect(deltaIn(tile("Overdue tasks"))).toBeNull();
  });

  it("hours sparkline: in the accessibility tree with a first/latest/peak summary", () => {
    render(<KpiRow result={ok(ADMIN_KPIS)} to={TO} seesFinancials />);

    // Zero-filled W41..W46 = [0, 12, 0, 0, 7.5, 0]. getByRole excludes aria-hidden subtrees, so
    // this fails if StatCard's sparkline wrapper hides the preset (Decision (a)).
    const img = within(tile("Hours logged")).getByRole("img", { name: SPARKLINE_NAME });
    expect(img).toHaveAccessibleDescription(
      `First week ${h(0)}, latest week ${h(0)}, peak ${h(12)} in week 2 of 6.`
    );
    // The only sparkline in the row.
    expect(screen.getAllByRole("img")).toHaveLength(1);
  });

  it("trend with gaps across the 53-week ISO year is zero-filled to six weeks incl. 2026-W53", () => {
    const { container } = render(
      <KpiRow
        result={ok({
          ...ADMIN_KPIS,
          trend: [
            { period: "2026-W51", value: 4 },
            { period: "2026-W53", value: 9.5 },
          ],
        })}
        to="2027-01-05"
        seesFinancials
      />
    );

    // Weeks 2026-W49, W50, W51, W52, W53, 2027-W01 → [0, 0, 4, 0, 9.5, 0].
    expect(screen.getByRole("img", { name: SPARKLINE_NAME })).toHaveAccessibleDescription(
      `First week ${h(0)}, latest week ${h(0)}, peak ${h(9.5)} in week 5 of 6.`
    );
    const d =
      container.querySelector('[data-slot="sparkline-trend-line"]')?.getAttribute("d") ?? "";
    expect(d.split(" ")).toHaveLength(6);
  });

  it("redacted caller (FINANCIAL_VISIBILITY custom role): Billable % '—' + hint, no delta", () => {
    render(<KpiRow result={ok(REDACTED_KPIS)} to={TO} seesFinancials={false} />);

    const billable = tile("Billable %");
    expect(billable).toHaveAttribute("data-state", "redacted");
    expect(within(billable).getByText("—")).toBeInTheDocument();
    expect(within(billable).getByText("Not available")).toHaveClass("sr-only");
    expect(within(billable).getByText("Visible to admins and owners")).toBeInTheDocument();
    expect(deltaIn(billable)).toBeNull();
    // The other three tiles are unaffected (hours is firm-wide for every caller).
    expect(tile("Hours logged")).toHaveAttribute("data-state", "ready");
    expect(deltaIn(tile("Hours logged"))).not.toBeNull();
  });

  it("admin with billablePercent null (no hours in the window): '—', no hint, no delta", () => {
    render(<KpiRow result={ok({ ...ADMIN_KPIS, billablePercent: null })} to={TO} seesFinancials />);

    const billable = tile("Billable %");
    expect(billable).toHaveAttribute("data-state", "ready");
    expect(within(billable).getByText("—")).toBeInTheDocument();
    expect(screen.queryByText("Visible to admins and owners")).toBeNull();
    expect(deltaIn(billable)).toBeNull();
  });

  it("previous hours 0 → no hours delta (no '∞%')", () => {
    render(
      <KpiRow
        result={ok({
          ...ADMIN_KPIS,
          previousPeriod: { ...ADMIN_KPIS.previousPeriod!, totalHoursLogged: 0 },
        })}
        to={TO}
        seesFinancials
      />
    );

    expect(deltaIn(tile("Hours logged"))).toBeNull();
  });

  it("error → four role=status 'Couldn't load' tiles, no values, no sparkline", () => {
    render(<KpiRow result={{ state: "error" }} to={TO} seesFinancials />);

    const statuses = screen.getAllByRole("status");
    expect(statuses).toHaveLength(4);
    for (const status of statuses) expect(status).toHaveTextContent("Couldn't load");
    expect(screen.getAllByRole("group")).toHaveLength(4);
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("renders exactly the four tiles — no margin tile (R1)", () => {
    render(<KpiRow result={ok(ADMIN_KPIS)} to={TO} seesFinancials />);

    expect(screen.getAllByRole("group").map((g) => g.getAttribute("aria-labelledby"))).toHaveLength(
      4
    );
    for (const name of ["Active matters", "Hours logged", "Billable %", "Overdue tasks"]) {
      expect(tile(name)).toBeInTheDocument();
    }
    expect(screen.queryByText(/margin/i)).toBeNull();
  });

  it("hoursTrendSummary: an all-zero series says so instead of naming a peak", () => {
    expect(hoursTrendSummary([0, 0, 0, 0, 0, 0])).toBe("No hours logged in the last 6 weeks.");
  });
});
