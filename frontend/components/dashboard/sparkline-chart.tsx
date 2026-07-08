"use client";

import { useId } from "react";
import { cn } from "@/lib/utils";

interface SparklineChartProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  showGradient?: boolean;
  className?: string;
}

function buildSmoothPath(points: [number, number][]): string {
  if (points.length < 2) return "";
  const [first, ...rest] = points;
  let d = `M ${first[0]},${first[1]}`;
  for (let i = 0; i < rest.length; i++) {
    const prev = i === 0 ? first : rest[i - 1];
    const curr = rest[i];
    const cpx = (prev[0] + curr[0]) / 2;
    d += ` C ${cpx},${prev[1]} ${cpx},${curr[1]} ${curr[0]},${curr[1]}`;
  }
  return d;
}

export function SparklineChart({
  data,
  width = 80,
  height = 28,
  color = "currentColor",
  showGradient = true,
  className,
}: SparklineChartProps) {
  const gradientId = useId();

  if (!data || data.length === 0) {
    return (
      <svg
        width={width}
        height={height}
        aria-hidden="true"
        data-testid="sparkline"
        className={cn("inline-block", className)}
      />
    );
  }

  const validData = data.filter(Number.isFinite);
  if (validData.length === 0) {
    return (
      <svg
        width={width}
        height={height}
        aria-hidden="true"
        data-testid="sparkline"
        className={cn("inline-block", className)}
      />
    );
  }

  const min = Math.min(...validData);
  const max = Math.max(...validData);
  const range = max - min || 1;

  const padding = 2;
  const plotHeight = height - padding * 2;
  const stepX = (width - 4) / Math.max(validData.length - 1, 1);

  const coords: [number, number][] = validData.map((value, i) => [
    2 + i * stepX,
    padding + plotHeight - ((value - min) / range) * plotHeight,
  ]);

  const linePath = buildSmoothPath(coords);
  const firstX = coords[0][0];
  const lastX = coords[coords.length - 1][0];
  const fillPath = `${linePath} L ${lastX},${height} L ${firstX},${height} Z`;

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      aria-hidden="true"
      data-testid="sparkline"
      className={cn("inline-block", className)}
    >
      {showGradient && (
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.25} />
            <stop offset="100%" stopColor={color} stopOpacity={0.02} />
          </linearGradient>
        </defs>
      )}
      {showGradient && linePath && <path d={fillPath} fill={`url(#${gradientId})`} />}
      {linePath && (
        <path
          d={linePath}
          fill="none"
          stroke={color}
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}
      {/* End dot */}
      <circle
        cx={coords[coords.length - 1][0]}
        cy={coords[coords.length - 1][1]}
        r={2}
        fill={color}
      />
    </svg>
  );
}
