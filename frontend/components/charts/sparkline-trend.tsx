// Chart kit micro preset (ADR-370, arch §15.2.4): a server-rendered sparkline for the KPI hours
// tile (640A). Dependency-free SVG, server-safe (no "use client"); fixed height so the KPI row
// cannot shift layout. The caller writes the sr summary (first, last and peak points, formatted) —
// the preset formats nothing. role="img" + aria-label on the <svg>; the summary is a sibling.
import { useId } from "react";
import { CHART_THEME } from "@/lib/chart-theme";
import { cn } from "@/lib/utils";

interface SparklineTrendProps {
  values: number[];
  /** Accessible name, e.g. "Hours logged, last six weeks". */
  label: string;
  /** Caller-formatted sr sentence stating the first, last and peak points. */
  summary: string;
  /** Height in px (default 40). Width fills the container. */
  height?: number;
  className?: string;
}

/** viewBox width; the SVG stretches to its container (preserveAspectRatio="none"). */
const W = 100;
/** Keeps the 2px stroke inside the box at the min and max. */
const PAD = 3;
/** Slot 1 (teal) — a single-series trend takes the first categorical slot. */
const ACCENT = CHART_THEME.categorical[0];

export function SparklineTrend({
  values,
  label,
  summary,
  height = 40,
  className,
}: SparklineTrendProps) {
  const summaryId = useId();
  const ys = values.map((v) => (Number.isFinite(v) ? v : 0));
  const empty = ys.length === 0;

  let line = "";
  if (!empty) {
    const min = Math.min(...ys);
    const span = Math.max(...ys) - min;
    const yOf = (v: number) =>
      span === 0 ? height / 2 : PAD + (1 - (v - min) / span) * (height - 2 * PAD);
    // One point draws a flat line across the box, never a lone dot.
    const points: Array<[number, number]> =
      ys.length === 1
        ? [
            [0, yOf(ys[0])],
            [W, yOf(ys[0])],
          ]
        : ys.map((v, i) => [(i / (ys.length - 1)) * W, yOf(v)]);
    line = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x},${y}`).join(" ");
  }

  return (
    <span data-slot="sparkline-trend" className={cn("block", className)}>
      <svg
        role="img"
        aria-label={label}
        aria-describedby={summaryId}
        width="100%"
        height={height}
        viewBox={`0 0 ${W} ${height}`}
        preserveAspectRatio="none"
        className="block"
        data-empty={empty ? "true" : undefined}
      >
        {empty ? (
          <line
            data-slot="sparkline-trend-track"
            x1={0}
            x2={W}
            y1={height / 2}
            y2={height / 2}
            stroke={CHART_THEME.grid.stroke}
            strokeWidth={2}
            vectorEffect="non-scaling-stroke"
          />
        ) : (
          <>
            <path
              data-slot="sparkline-trend-area"
              d={`${line} L${W},${height} L0,${height} Z`}
              fill={ACCENT}
              fillOpacity={0.1}
            />
            <path
              data-slot="sparkline-trend-line"
              d={line}
              fill="none"
              stroke={ACCENT}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
              vectorEffect="non-scaling-stroke"
            />
          </>
        )}
      </svg>
      <span id={summaryId} data-slot="sparkline-trend-summary" className="sr-only">
        {summary}
      </span>
    </span>
  );
}
