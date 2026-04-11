"use client";

import { cn } from "@/lib/utils";

interface RadialGaugeProps {
  value: number;
  size?: number;
  strokeWidth?: number;
  thresholds?: { low: number; high: number };
  className?: string;
}

function gaugeColor(value: number, thresholds: { low: number; high: number }): string {
  if (value < thresholds.low) return "var(--color-slate-400)";
  if (value > thresholds.high) return "var(--color-amber-500, #f59e0b)";
  return "var(--color-teal-500)";
}

/**
 * Compute SVG arc path for a 270-degree gauge.
 * The arc starts at bottom-left (135 degrees from 12-o'clock) and sweeps clockwise.
 */
function describeArc(
  cx: number,
  cy: number,
  radius: number,
  startAngle: number,
  endAngle: number
): string {
  const toRad = (deg: number) => ((deg - 90) * Math.PI) / 180;

  const start = {
    x: cx + radius * Math.cos(toRad(endAngle)),
    y: cy + radius * Math.sin(toRad(endAngle)),
  };
  const end = {
    x: cx + radius * Math.cos(toRad(startAngle)),
    y: cy + radius * Math.sin(toRad(startAngle)),
  };

  const largeArcFlag = endAngle - startAngle <= 180 ? 0 : 1;

  return `M ${start.x} ${start.y} A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`;
}

export function RadialGauge({
  value,
  size = 48,
  strokeWidth = 6,
  thresholds = { low: 60, high: 90 },
  className,
}: RadialGaugeProps) {
  const clampedValue = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;

  const center = size / 2;
  const radius = (size - strokeWidth) / 2;

  // 270-degree arc: starts at 135 degrees (bottom-left), sweeps to 45 degrees (bottom-right)
  const startAngle = 135;
  const totalSweep = 270;
  const endAngle = startAngle + totalSweep;

  // Background track — full 270-degree arc
  const bgPath = describeArc(center, center, radius, startAngle, endAngle);

  // Foreground arc — proportional to value
  const valueSweep = (clampedValue / 100) * totalSweep;
  const fgPath =
    valueSweep > 0 ? describeArc(center, center, radius, startAngle, startAngle + valueSweep) : "";

  const color = gaugeColor(clampedValue, thresholds);

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-label={`${Math.round(clampedValue)}%`}
      role="img"
      data-testid="radial-gauge"
      className={cn("inline-block", className)}
    >
      {/* Background track */}
      <path
        d={bgPath}
        fill="none"
        stroke="var(--color-slate-200)"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
      />
      {/* Foreground arc */}
      {fgPath && (
        <path
          d={fgPath}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
        />
      )}
      {/* Center text */}
      <text
        x={center}
        y={center}
        textAnchor="middle"
        dominantBaseline="central"
        fontSize={size * 0.28}
        fontWeight={700}
        fill="currentColor"
        style={{ fontFamily: "var(--font-mono)" }}
      >
        {Math.round(clampedValue)}%
      </text>
    </svg>
  );
}
