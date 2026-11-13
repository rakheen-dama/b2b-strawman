"use client";

import { useMemo } from "react";
import dynamic from "next/dynamic";
import { ChartArea } from "lucide-react";
import { ChartCard } from "@/components/charts/chart-card";
import type { ChartRow, ChartSeries } from "@/components/charts/chart-summary";
import { monthLabel } from "@/components/trust/trust-balance-trend-chart";
import type { BillingTrendResponse } from "@/lib/types";

/** Visible card title and the figure's accessible name (spec §15.2.7). */
export const BILLING_TREND_TITLE = "Fees Billed vs Collected";
/**
 * The card's scope: always the last 12 SA months, whatever the From/To of the cards below. The
 * ready card prefixes the currency ("ZAR · VAT inclusive · …").
 */
export const BILLING_TREND_SCOPE =
  "VAT inclusive · last 12 months, independent of the date ranges below";

/** The chart area (TrendAreaChart's default). */
const CHART_HEIGHT = 240;
/**
 * ChartTableToggle: mt-3 + the 32px (size="sm") "View as table" button, which the loading card
 * does not draw. Mirrors chart-table-toggle.tsx's root `mt-3` and `<Button size="sm">` (`h-8`).
 */
const TOGGLE_ROW_PX = 44;

// recharts is split out of the page bundle and mounted on the client only, behind a loading card
// of the ready card's own box (same header, chart + toggle-row height), so nothing shifts when the
// chart arrives (640B / 643A pattern).
const TrendAreaChart = dynamic(
  () => import("@/components/charts/trend-area-chart").then((mod) => mod.TrendAreaChart),
  {
    ssr: false,
    loading: () => (
      <ChartCard
        title={BILLING_TREND_TITLE}
        description={BILLING_TREND_SCOPE}
        state="loading"
        height={CHART_HEIGHT + TOGGLE_ROW_PX}
        emptyIcon={ChartArea}
      >
        {null}
      </ChartCard>
    ),
  }
);

/** Slot 1 billed, slot 3 collected (ADR-370: colour follows the entity; all slots pinned). */
const SERIES: ChartSeries[] = [
  { key: "billed", label: "Billed", slot: 1 },
  { key: "collected", label: "Collected", slot: 3 },
];

interface BillingTrendChartProps {
  /** The billing-trend read; `null` = absent (403/404) or failed (see `failed`). Plain data only. */
  trend: BillingTrendResponse | null;
  /** The read failed (not 403/404): the card shows an error line instead of the chart. */
  failed?: boolean;
}

/**
 * Profitability page: fees billed (issue month, VAT inclusive) vs collected (SA payment month) for
 * the last 12 SA months in the org default currency (Phase 90 643B, ADR-372). The current month is
 * labelled "(to date)" and drawn dashed. Absent and error states render through ChartCard without
 * loading recharts. All-zero months are drawn as flat lines: nothing billed is still a value.
 */
export function BillingTrendChart({ trend, failed = false }: BillingTrendChartProps) {
  const rows = useMemo<ChartRow[]>(
    () =>
      trend?.months.map((point) => ({
        month: monthLabel(point.month),
        billed: point.billed,
        collected: point.collected,
      })) ?? [],
    [trend]
  );

  if (failed || !trend) {
    return (
      <ChartCard
        title={BILLING_TREND_TITLE}
        description={BILLING_TREND_SCOPE}
        state={failed ? "error" : "empty"}
        height={CHART_HEIGHT}
        emptyIcon={ChartArea}
        emptyTitle="Billing trend not available."
        emptyDescription="Fees billed and collected could not be shown."
        errorMessage="Couldn't load the billing trend."
        data-testid="billing-trend"
      >
        {null}
      </ChartCard>
    );
  }

  return (
    <TrendAreaChart
      title={BILLING_TREND_TITLE}
      description={`${trend.currency} · ${BILLING_TREND_SCOPE}`}
      data={rows}
      xKey="month"
      categoryLabel="Month"
      series={SERIES}
      format="currency"
      currency={trend.currency}
      partialLastPoint={trend.months.at(-1)?.partial ?? false}
      height={CHART_HEIGHT}
      data-testid="billing-trend"
    />
  );
}
