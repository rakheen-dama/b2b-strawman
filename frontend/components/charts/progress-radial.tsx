// Chart kit micro preset (ADR-370, arch §15.2.4): a radial progress ring in dependency-free SVG.
// Server-safe (no "use client"); the caller supplies the label. role="img" + aria-label on the
// <svg>; the sr-only summary is a sibling (children of role="img" are presentational).
import { useId } from "react";
import { CHART_THEME } from "@/lib/chart-theme";
import { cn } from "@/lib/utils";

export type ProgressRadialTone = "success" | "warning" | "danger" | "info";

interface ProgressRadialProps {
  /** Percent, clamped to 0–100 (non-finite → 0). */
  value: number;
  /** Accessible name, e.g. "Task completion". */
  label: string;
  /**
   * Status colour — only when the ring shows a state (always beside an icon + label). Without it
   * the ring is the accent on a neutral track: progress is not a status.
   */
  tone?: ProgressRadialTone;
  /** Diameter in px (default 32); the centre percentage shows at ≥ 40. */
  size?: number;
  className?: string;
}

function clampPercent(value: number): number {
  return Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
}

export function ProgressRadial({ value, label, tone, size = 32, className }: ProgressRadialProps) {
  const summaryId = useId();
  const pct = clampPercent(value);
  const rounded = Math.round(pct);
  const strokeWidth = size >= 40 ? 4 : 3;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const center = size / 2;
  const fill = tone ? CHART_THEME.status[tone] : CHART_THEME.categorical[0];
  const track = tone ? CHART_THEME.statusSoft[tone] : CHART_THEME.track;

  return (
    <span data-slot="progress-radial" className={cn("inline-flex", className)}>
      <svg
        role="img"
        aria-label={label}
        aria-describedby={summaryId}
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        data-empty={pct === 0 ? "true" : undefined}
      >
        <circle
          data-slot="progress-radial-track"
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke={track}
          strokeWidth={strokeWidth}
        />
        {pct > 0 ? (
          <circle
            data-slot="progress-radial-arc"
            cx={center}
            cy={center}
            r={radius}
            fill="none"
            stroke={fill}
            strokeWidth={strokeWidth}
            strokeDasharray={circumference}
            strokeDashoffset={circumference - (pct / 100) * circumference}
            strokeLinecap="round"
            transform={`rotate(-90 ${center} ${center})`}
          />
        ) : null}
        {size >= 40 ? (
          <text
            x={center}
            y={center}
            textAnchor="middle"
            dominantBaseline="central"
            fontSize={size * 0.28}
            fill="currentColor"
            fontWeight={600}
          >
            {rounded}%
          </text>
        ) : null}
      </svg>
      <span id={summaryId} data-slot="progress-radial-summary" className="sr-only">
        {`${label}: ${rounded}%`}
      </span>
    </span>
  );
}
