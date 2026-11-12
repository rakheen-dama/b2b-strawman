// Chart kit micro preset (ADR-370, arch §15.2.4): a thin segmented meter (one whole, split into
// parts) in dependency-free SVG. Server-safe (no "use client"); callers format every valueText.
// Ordinal buckets take the one-hue --aging-* ramp (B10 — never status colours); "categorical"
// takes --chart-1..5 in slot order. role="img" + aria-label on the <svg>; the sr-only summary is a
// sibling (children of role="img" are presentational).
import { useId } from "react";
import { CHART_THEME } from "@/lib/chart-theme";
import { cn } from "@/lib/utils";

export interface StackedMeterSegment {
  key: string;
  label: string;
  /** Drawn width share; ≤ 0 or non-finite draws nothing (still stated in the summary). */
  value: number;
  /** Caller-formatted, e.g. formatCurrency(value, currency). */
  valueText: string;
}

export type StackedMeterPalette = "ordinal-aging" | "categorical";

interface StackedMeterProps {
  /** Drawn in the given order; a segment keeps its colour slot when another is zero. */
  segments: StackedMeterSegment[];
  /** "ordinal-aging": ≤ 4 ordered buckets (current → 90+); "categorical": ≤ 5 named parts. */
  palette: StackedMeterPalette;
  /** Accessible name of the meter. */
  label: string;
  /** Bar thickness in px (default 8; 6 in dense rows). */
  height?: number;
  className?: string;
}

const AGING: readonly string[] = [
  CHART_THEME.aging.current,
  CHART_THEME.aging.d30,
  CHART_THEME.aging.d60,
  CHART_THEME.aging.d90plus,
];

function colourAt(palette: StackedMeterPalette, index: number): string {
  const ramp: readonly string[] = palette === "ordinal-aging" ? AGING : CHART_THEME.categorical;
  return ramp[Math.min(index, ramp.length - 1)];
}

export function StackedMeter({
  segments,
  palette,
  label,
  height = 8,
  className,
}: StackedMeterProps) {
  const summaryId = useId();
  const values = segments.map((s) => (Number.isFinite(s.value) && s.value > 0 ? s.value : 0));
  const total = values.reduce((sum, v) => sum + v, 0);

  // Each drawn part starts where the previous one ended; a zero part draws nothing.
  const drawn: Array<{ segment: StackedMeterSegment; x: number; width: number; colour: string }> =
    [];
  let start = 0;
  for (let index = 0; index < segments.length; index++) {
    const v = values[index];
    if (v === 0) continue;
    drawn.push({
      segment: segments[index],
      x: (start / total) * 100,
      width: (v / total) * 100,
      colour: colourAt(palette, index),
    });
    start += v;
  }

  return (
    <span data-slot="stacked-meter" className={cn("block", className)}>
      {/* The rounded, clipped wrapper gives fully rounded ends without an SVG clipPath id. */}
      <span className="block overflow-hidden rounded-full" style={{ height }}>
        <svg
          role="img"
          aria-label={label}
          aria-describedby={summaryId}
          width="100%"
          height={height}
          className="block"
          data-empty={drawn.length === 0 ? "true" : undefined}
        >
          <rect data-slot="stacked-meter-track" width="100%" height={height} fill="var(--muted)" />
          {drawn.map(({ segment, x, width, colour }) => (
            <rect
              key={segment.key}
              data-segment={segment.key}
              x={`${x}%`}
              width={`${width}%`}
              height={height}
              fill={colour}
            >
              <title>{`${segment.label}: ${segment.valueText}`}</title>
            </rect>
          ))}
          {/* 2px surface gap between touching parts (dataviz spacer), centred on each boundary. */}
          {drawn.slice(1).map(({ segment, x }) => (
            <line
              key={`gap-${segment.key}`}
              data-slot="stacked-meter-gap"
              x1={`${x}%`}
              x2={`${x}%`}
              y1={0}
              y2={height}
              stroke="var(--card)"
              strokeWidth={2}
            />
          ))}
        </svg>
      </span>
      <span id={summaryId} data-slot="stacked-meter-summary" className="sr-only">
        {segments.map((s) => `${s.label} ${s.valueText}`).join(", ")}
      </span>
    </span>
  );
}
