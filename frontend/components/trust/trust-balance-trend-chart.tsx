"use client";

import { useMemo } from "react";
import dynamic from "next/dynamic";
import { ChartArea } from "lucide-react";
import { ChartCard } from "@/components/charts/chart-card";
import type { ChartRow, ChartSeries } from "@/components/charts/chart-summary";
import type { TrustBalanceTrendResponse } from "@/lib/dashboard-types";

/** Visible card title and the figure's name — not "Trust Balance", which names the StatCard. */
export const TREND_TITLE = "Trust balance trend";
/** OQ #6: the series replays postings as they stand today, not historical snapshots. */
export const TREND_CAPTION = "Month-end balances as currently recorded";

/** The chart area (TrendAreaChart's default). */
const CHART_HEIGHT = 240;
/**
 * ChartTableToggle: mt-3 + the 32px (size="sm") "View as table" button, which the loading card
 * does not draw. Mirrors chart-table-toggle.tsx's root `mt-3` and `<Button size="sm">` (`h-8`).
 */
const TOGGLE_ROW_PX = 44;

// Below the fold (D5): recharts is split out of the page bundle and mounted on the client only,
// behind a loading card of the ready card's own box (same header, chart + toggle-row height), so
// nothing shifts when the chart arrives (640B pattern).
const TrendAreaChart = dynamic(
  () => import("@/components/charts/trend-area-chart").then((mod) => mod.TrendAreaChart),
  {
    ssr: false,
    loading: () => (
      <ChartCard
        title={TREND_TITLE}
        description={TREND_CAPTION}
        state="loading"
        height={CHART_HEIGHT + TOGGLE_ROW_PX}
        emptyIcon={ChartArea}
      >
        {null}
      </ChartCard>
    ),
  }
);

/** Slot 1 cashbook, slot 3 client ledgers (ADR-370: colour follows the entity; all slots pinned). */
const SERIES: ChartSeries[] = [
  { key: "cashbook", label: "Cashbook", slot: 1 },
  { key: "ledger", label: "Client ledgers", slot: 3 },
];

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** Deterministic "2026-06" → "Jun 2026" (no Intl: identical on server and client). */
export function monthLabel(yearMonth: string): string {
  const [year, month] = yearMonth.split("-");
  const name = MONTHS[Number(month) - 1];
  return name && year ? `${name} ${year}` : yearMonth;
}

interface TrustBalanceTrendChartProps {
  /** The trend read; `null` = absent (403/404) or failed (see `failed`). Plain data only. */
  trend: TrustBalanceTrendResponse | null;
  /** The read failed (not 403/404): the card shows an error line instead of the chart. */
  failed?: boolean;
}

/**
 * Trust dashboard balance trend (Phase 90 643A, ADR-372): month-end cashbook vs client-ledger
 * balances for the last 12 SA months, the current month labelled "(to date)" and drawn dashed.
 * Absent and error states render through ChartCard without loading recharts. An all-zero series
 * is drawn as a flat line: zero balances do not mean "no postings" (a month can net to zero).
 */
export function TrustBalanceTrendChart({ trend, failed = false }: TrustBalanceTrendChartProps) {
  const rows = useMemo<ChartRow[]>(
    () =>
      trend?.months.map((point) => ({
        month: monthLabel(point.month),
        cashbook: point.cashbookBalance,
        ledger: point.clientLedgerTotal,
      })) ?? [],
    [trend]
  );

  if (failed || !trend) {
    return (
      <ChartCard
        title={TREND_TITLE}
        description={TREND_CAPTION}
        state={failed ? "error" : "empty"}
        height={CHART_HEIGHT}
        emptyIcon={ChartArea}
        emptyTitle="Balance trend not available."
        emptyDescription="The balance trend could not be shown for this account."
        errorMessage="Couldn't load the balance trend."
        data-testid="trust-balance-trend"
      >
        {null}
      </ChartCard>
    );
  }

  return (
    <TrendAreaChart
      title={TREND_TITLE}
      description={`${TREND_CAPTION} (${trend.currency})`}
      data={rows}
      xKey="month"
      categoryLabel="Month"
      series={SERIES}
      format="currency"
      currency={trend.currency}
      partialLastPoint={trend.months.at(-1)?.partial ?? false}
      height={CHART_HEIGHT}
      data-testid="trust-balance-trend"
    />
  );
}
