"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@b2mash/ui/skeleton";
import type { ChartRow } from "@/components/charts/chart-summary";

// The one recharts chart on Home (arch §15.6.2): split out of the Home bundle and mounted on the
// client only (`ssr: false` needs a client module — the widget stays a Server Component), behind
// a skeleton that fills the loaded block's box, so nothing shifts when the chart arrives.
const BarChartPreset = dynamic(
  () => import("@/components/charts/bar-chart").then((mod) => mod.BarChartPreset),
  {
    ssr: false,
    loading: () => <Skeleton data-slot="wip-chart-skeleton" className="h-full w-full" />,
  }
);

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
 * chart-table-toggle.tsx's root `mt-3 flex flex-col gap-2` and `<Button size="sm">`
 * (`h-8` in packages/ui/src/button.tsx).
 */
const TOGGLE_ROW_PX = 44;
/** The preset folds earners past this into one "Other" bar (bar-chart.tsx `maxBars = 10`). */
const MAX_BARS = 10;

/**
 * Inside the widget card the preset draws no frame of its own (no card in a card) and no header
 * (the widget's title names it; the figure keeps that name as its aria-label). Undoes
 * chart-card.tsx's `<Card className="gap-4 py-5">` + `<CardContent className="px-5">` and the
 * @b2mash/ui Card's own `rounded-lg border bg-card py-6 shadow-sm` (packages/ui/src/card.tsx).
 */
const FRAMELESS_CLASS =
  "gap-0 rounded-none border-0 bg-transparent py-0 shadow-none *:data-[slot=card-content]:px-0";

interface UnbilledWipChartProps {
  /** The widget title — the figure's accessible name and the series label. */
  title: string;
  /** `{ feeEarner, value }` per fee earner, value descending (the endpoint's order). */
  rows: ChartRow[];
  currency: string;
}

export function UnbilledWipChart({ title, rows, currency }: UnbilledWipChartProps) {
  const height = Math.min(rows.length, MAX_BARS) * BAND_PX + AXIS_PX;
  return (
    <div
      data-slot="wip-chart"
      style={{ minHeight: height + TOGGLE_ROW_PX }}
      className="flex flex-col *:flex-1"
    >
      <BarChartPreset
        title={title}
        hideHeader
        data={rows}
        categoryKey="feeEarner"
        categoryLabel="Fee earner"
        series={[{ key: "value", label: title, slot: 1 }]}
        orientation="horizontal"
        layout="single"
        format="currency"
        currency={currency}
        maxBars={MAX_BARS}
        height={height}
        className={FRAMELESS_CLASS}
      />
    </div>
  );
}
