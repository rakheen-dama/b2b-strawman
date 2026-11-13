"use client";

import { useMemo } from "react";
import dynamic from "next/dynamic";
import { Skeleton } from "@b2mash/ui/skeleton";
import type { ChartRow, ChartSeries } from "@/components/charts/chart-summary";
import {
  FRAMELESS_CHART_CLASS,
  HORIZONTAL_BAR_AXIS_PX,
  HORIZONTAL_BAR_BAND_PX,
  TOGGLE_ROW_PX,
} from "@/components/charts/layout";
import type { MatterProfitabilitySummary } from "@/lib/types";

// recharts is split out of the page bundle and mounted on the client only, behind a skeleton
// that fills the loaded block's box, so nothing shifts when the chart arrives (640B pattern).
const BarChartPreset = dynamic(
  () => import("@/components/charts/bar-chart").then((mod) => mod.BarChartPreset),
  {
    ssr: false,
    loading: () => (
      <Skeleton data-slot="profitability-ranking-skeleton" className="h-full w-full" />
    ),
  }
);

/** The card's title — the figure's accessible name (the card above shows it). */
const TITLE = "Matter Profitability";

/** The existing "Margin" column header is the series label (no new label). */
const SERIES: ChartSeries[] = [{ key: "margin", label: "Margin", slot: 1 }];

/**
 * Top matters by margin. Pre-sliced: currency is additive, so the preset would otherwise fold the
 * rest into one summed "Other" bar — meaningless on a ranking.
 */
const TOP = 10;

/** Rows in other currencies are not summed into, or ranked against, the default currency. */
export function excludedHint(count: number): string {
  return `Excludes ${count} ${count === 1 ? "matter" : "matters"} in other currencies`;
}

interface ProfitabilityRankingChartProps {
  /** The card's live rows — follow its From/To. */
  matters: MatterProfitabilitySummary[];
  /** Org default currency (from the billing-trend read); `null` → no chart. */
  defaultCurrency: string | null;
}

/**
 * Matter Profitability ranking (Phase 90 643B): the top 10 matters by margin in the org default
 * currency, a diverging bar each (losses left of the zero line). Matters without a cost rate
 * (`margin` null) are not ranked — the table shows them as N/A. Other-currency matters are counted
 * in a hint, never converted.
 */
export function ProfitabilityRankingChart({
  matters,
  defaultCurrency,
}: ProfitabilityRankingChartProps) {
  const { rows, excluded } = useMemo<{ rows: ChartRow[]; excluded: number }>(() => {
    if (!defaultCurrency) return { rows: [], excluded: 0 };
    const inCurrency = matters.filter((matter) => matter.currency === defaultCurrency);
    const ranked = inCurrency
      .filter((matter) => matter.margin != null)
      .sort((a, b) => (b.margin ?? 0) - (a.margin ?? 0))
      .slice(0, TOP)
      .map((matter) => ({ matter: matter.matterName, margin: matter.margin }));
    return { rows: ranked, excluded: matters.length - inCurrency.length };
  }, [matters, defaultCurrency]);

  if (!defaultCurrency) return null;

  const hint =
    excluded > 0 ? <p className="text-muted-foreground text-xs">{excludedHint(excluded)}</p> : null;

  if (rows.length === 0) {
    return hint ? <div className="mb-4">{hint}</div> : null;
  }

  const height = rows.length * HORIZONTAL_BAR_BAND_PX + HORIZONTAL_BAR_AXIS_PX;
  // The hint sits outside the preset (not its `footer`) so the skeleton box equals the ready box.
  return (
    <div className="mb-6 space-y-2">
      <div
        data-slot="profitability-ranking-chart"
        style={{ minHeight: height + TOGGLE_ROW_PX }}
        className="flex flex-col *:flex-1"
      >
        <BarChartPreset
          title={TITLE}
          hideHeader
          data={rows}
          categoryKey="matter"
          categoryLabel="Matter"
          series={SERIES}
          orientation="horizontal"
          layout="single"
          diverging
          format="currency"
          currency={defaultCurrency}
          maxBars={TOP}
          height={height}
          className={FRAMELESS_CHART_CLASS}
        />
      </div>
      {hint}
    </div>
  );
}
