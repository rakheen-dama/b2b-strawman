"use client";

import { useMemo } from "react";
import dynamic from "next/dynamic";
import { Skeleton } from "@b2mash/ui/skeleton";
import type { ChartRow, ChartSeries } from "@/components/charts/chart-summary";
import type { MemberUtilizationRecord } from "@/lib/types";

// recharts is split out of the page bundle and mounted on the client only, behind a skeleton
// that fills the loaded block's box, so nothing shifts when the chart arrives (640B pattern).
const BarChartPreset = dynamic(
  () => import("@/components/charts/bar-chart").then((mod) => mod.BarChartPreset),
  {
    ssr: false,
    loading: () => <Skeleton data-slot="utilization-chart-skeleton" className="h-full w-full" />,
  }
);

/** The card's title — the figure's accessible name (the card above shows it). */
const TITLE = "Team Utilization";

/** Shown instead of a one-bar chart (OQ #1: a FINANCIAL_VISIBILITY role sees only its own row). */
export const UTILIZATION_NOTE =
  "The chart appears when two or more fee earners have time in this period.";

/** The existing "Utilization %" column header is the series label (no new label). */
const SERIES: ChartSeries[] = [{ key: "utilization", label: "Utilization %", slot: 1 }];

/**
 * One bar band in px: recharts' 10% category gap on each side leaves a 24px bar. Mirrors
 * bar-chart.tsx `<BarChart>` (no `barCategoryGap`/`barSize` set → recharts' default "10%").
 */
const BAND_PX = 30;
/**
 * The preset's 16px top margin + the 30px value axis. Mirrors bar-chart.tsx
 * `margin={{ top: 16, … bottom: 0 }}` and its horizontal `<XAxis type="number">` (no `height` set →
 * recharts' default 30).
 */
const AXIS_PX = 46;
/**
 * ChartTableToggle: mt-3 + the 32px (size="sm") "View as table" button. Mirrors
 * chart-table-toggle.tsx's root `mt-3 flex flex-col gap-2` and `<Button size="sm">`.
 */
const TOGGLE_ROW_PX = 44;
/**
 * Percent is not additive: the preset draws the first 10 and states "+N more"; the "View as
 * table" keeps every fee earner.
 */
const MAX_BARS = 10;

/**
 * Inside the existing card the preset draws no frame and no header of its own (no card in a card;
 * the figure keeps the card title as its aria-label). Same class as the 640B Unbilled WIP chart.
 */
const FRAMELESS_CLASS =
  "gap-0 rounded-none border-0 bg-transparent py-0 shadow-none *:data-[slot=card-content]:px-0";

interface UtilizationChartProps {
  /** The card's live rows — follow its From/To. */
  members: MemberUtilizationRecord[];
}

/**
 * Team Utilization (Phase 90 643B): one horizontal bar per fee earner, highest first. Fewer than
 * two fee earners → the note instead of a chart; none → nothing (the card states the empty period).
 */
export function UtilizationChart({ members }: UtilizationChartProps) {
  const rows = useMemo<ChartRow[]>(
    () =>
      [...members]
        .sort((a, b) => b.utilizationPercent - a.utilizationPercent)
        .map((member) => ({
          feeEarner: member.memberName,
          utilization: member.utilizationPercent,
        })),
    [members]
  );

  if (members.length === 0) return null;
  if (members.length < 2) {
    return <p className="text-muted-foreground mb-4 text-sm">{UTILIZATION_NOTE}</p>;
  }

  const height = Math.min(rows.length, MAX_BARS) * BAND_PX + AXIS_PX;
  return (
    <div
      data-slot="utilization-chart"
      style={{ minHeight: height + TOGGLE_ROW_PX }}
      className="mb-6 flex flex-col *:flex-1"
    >
      <BarChartPreset
        title={TITLE}
        hideHeader
        data={rows}
        categoryKey="feeEarner"
        categoryLabel="Fee earner"
        series={SERIES}
        orientation="horizontal"
        layout="single"
        format="percent"
        maxBars={MAX_BARS}
        height={height}
        className={FRAMELESS_CLASS}
      />
    </div>
  );
}
