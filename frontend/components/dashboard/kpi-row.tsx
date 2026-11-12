import type { ComponentProps, ReactNode } from "react";
import { Skeleton } from "@b2mash/ui/skeleton";
import { StatCard, type StatCardDelta } from "@b2mash/ui/stat-card";
import { SparklineTrend } from "@/components/charts/sparkline-trend";
import { formatChartValue } from "@/lib/chart-format";
import type { WidgetResult } from "@/lib/dashboard-home";
import type { KpiResponse } from "@/lib/dashboard-types";
import { computeDelta, zeroFillTrend } from "@/lib/kpi";

// Home KPI row (Phase 90 640A, arch §15.3.4; B1–B3, R1). Server Component — no "use client", no
// recharts, no next/dynamic: four StatCards, the hours tile with a server-rendered SparklineTrend
// (ADR-370). Firm-wide figures, so only the firm and owner audiences get the row (B2).
// KpiRowSkeleton (640C) is Home's loading.tsx stand-in: same frame, labels and tile anatomy.

const PERIOD_LABEL = "Last 30 days";
const COMPARISON_LABEL = "previous 30 days";
const REDACTED_HINT = "Visible to admins and owners";
const TREND_WEEKS = 6;
const SPARKLINE_LABEL = `Hours logged per week, last ${TREND_WEEKS} weeks`;
/** SparklineTrend's fixed height, which the skeleton holds. */
const SPARKLINE_HEIGHT = 40;

/** Four tiles, in row order; no margin tile (R1). */
const TILE = {
  activeMatters: "Active matters",
  hours: "Hours logged",
  billable: "Billable %",
  overdue: "Overdue tasks",
} as const;
export const KPI_TILE_LABELS: ReadonlyArray<(typeof TILE)[keyof typeof TILE]> = Object.values(TILE);

interface KpiRowProps {
  result: WidgetResult<KpiResponse>;
  /** The window's last day (yyyy-MM-dd, SA) — the sparkline's six weeks end at its ISO week. */
  to: string;
  /**
   * Caller is admin or owner by role — the backend's redaction rule (not FINANCIAL_VISIBILITY;
   * tasks OQ #1). Distinguishes a redacted Billable % from "no hours in the window".
   */
  seesFinancials: boolean;
}

/** The sparkline's sr sentence: first, latest and peak week (ADR-370 — states what it draws). */
export function hoursTrendSummary(values: readonly number[]): string {
  if (values.length === 0 || values.every((v) => v === 0)) {
    return `No hours logged in the last ${TREND_WEEKS} weeks.`;
  }
  const hours = (v: number) => formatChartValue(v, "hours");
  const peak = Math.max(...values);
  return `First week ${hours(values[0])}, latest week ${hours(values[values.length - 1])}, peak ${hours(peak)} in week ${values.indexOf(peak) + 1} of ${values.length}.`;
}

function delta(change: number | null, unit: StatCardDelta["unit"]): StatCardDelta {
  return { change, unit, polarity: "up-is-good", comparisonLabel: COMPARISON_LABEL };
}

export function KpiRow({ result, to, seesFinancials }: KpiRowProps) {
  if (result.state === "absent") return null; // never rendered: withoutAbsent drops the cell

  let tiles: ReactNode;
  if (result.state === "error") {
    tiles = KPI_TILE_LABELS.map((label) => (
      <StatCard key={label} label={label} value={null} state="error" className="h-full" />
    ));
  } else {
    const k = result.data;
    const prev = k.previousPeriod;
    const weeks = zeroFillTrend(k.trend ?? [], to, TREND_WEEKS).map((p) => p.value);
    const billableRedacted = k.billablePercent === null && !seesFinancials;
    tiles = (
      <>
        <StatCard
          label={TILE.activeMatters}
          value={formatChartValue(k.activeMatterCount, "count")}
          className="h-full"
        />
        <StatCard
          label={TILE.hours}
          value={formatChartValue(k.totalHoursLogged, "hours")}
          delta={delta(
            computeDelta(k.totalHoursLogged, prev?.totalHoursLogged, "percent"),
            "percent"
          )}
          sparkline={
            <SparklineTrend
              values={weeks}
              label={SPARKLINE_LABEL}
              summary={hoursTrendSummary(weeks)}
              height={SPARKLINE_HEIGHT}
            />
          }
          className="h-full"
        />
        <StatCard
          label={TILE.billable}
          value={k.billablePercent === null ? null : formatChartValue(k.billablePercent, "percent")}
          state={billableRedacted ? "redacted" : "ready"}
          hint={billableRedacted ? REDACTED_HINT : undefined}
          delta={
            billableRedacted
              ? undefined
              : delta(computeDelta(k.billablePercent, prev?.billablePercent, "points"), "points")
          }
          className="h-full"
        />
        <StatCard
          label={TILE.overdue}
          value={formatChartValue(k.overdueTaskCount, "count")}
          className="h-full"
        />
      </>
    );
  }

  return <KpiRowFrame data-slot="kpi-row">{tiles}</KpiRowFrame>;
}

/**
 * Home's loading state for the row (640C, spec 640.13): four loading StatCards in the steady-state
 * anatomy — any non-undefined `delta` draws StatCard's delta skeleton, and the hours tile holds the
 * sparkline's height (StatCard draws no sparkline slot while loading) — so nothing shifts on load.
 */
export function KpiRowSkeleton() {
  return (
    <KpiRowFrame>
      <StatCard label={TILE.activeMatters} value={null} state="loading" className="h-full" />
      <StatCard
        label={TILE.hours}
        value={null}
        state="loading"
        delta={delta(0, "percent")}
        footer={<Skeleton className="w-full" style={{ height: SPARKLINE_HEIGHT }} />}
        className="h-full"
      />
      <StatCard
        label={TILE.billable}
        value={null}
        state="loading"
        delta={delta(0, "points")}
        className="h-full"
      />
      <StatCard label={TILE.overdue} value={null} state="loading" className="h-full" />
    </KpiRowFrame>
  );
}

function KpiRowFrame({ children, ...props }: ComponentProps<"div">) {
  return (
    <div {...props} className="@container flex flex-col gap-2">
      <p className="text-muted-foreground text-xs font-medium">{PERIOD_LABEL}</p>
      {/* 2×2 below @3xl (48rem), 4×1 at @3xl — same container-query idiom as DetailHero. */}
      <div className="grid grid-cols-2 gap-4 @3xl:grid-cols-4">{children}</div>
    </div>
  );
}
