"use client";

import { useId } from "react";
import { cn } from "@/lib/utils";

interface SparklineChartProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
}

export function SparklineChart({
  data,
  width = 80,
  height = 24,
  color = "currentColor",
}: SparklineChartProps) {
  const gradientId = useId();

  if (!data || data.length === 0) {
    return <svg width={width} height={height} aria-hidden="true" />;
  }

  // Filter out non-finite values (NaN, Infinity, -Infinity)
  const validData = data.filter(Number.isFinite);
  if (validData.length === 0) {
    return <svg width={width} height={height} aria-hidden="true" />;
  }

  const min = Math.min(...validData);
  const max = Math.max(...validData);
  const range = max - min || 1;

  const padding = 1;
  const plotHeight = height - padding * 2;
  const stepX = (width - 2) / Math.max(validData.length - 1, 1);

  const points = validData
    .map((value, i) => {
      const x = 1 + i * stepX;
      const y = padding + plotHeight - ((value - min) / range) * plotHeight;
      return `${x},${y}`;
    })
    .join(" ");

  // Build the polygon for the gradient fill area (line + bottom edge)
  const firstX = 1;
  const lastX = 1 + (validData.length - 1) * stepX;
  const fillPoints = `${firstX},${height} ${points} ${lastX},${height}`;

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      aria-hidden="true"
      className="inline-block"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.2} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <polygon points={fillPoints} fill={`url(#${gradientId})`} />
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth={1}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
