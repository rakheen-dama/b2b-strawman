"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
} from "recharts";
import { CHART_THEME } from "@/lib/chart-theme";

interface HorizontalBarChartProps {
  data: Array<{
    label: string;
    segments: Array<{
      label: string;
      value: number;
      color: string;
    }>;
  }>;
  maxValue?: number;
  showLegend?: boolean;
  referenceLine?: number;
}

const THEME_COLORS = [
  CHART_THEME.colors.primary,
  CHART_THEME.colors.secondary,
  CHART_THEME.colors.tertiary,
  CHART_THEME.colors.quaternary,
  CHART_THEME.colors.quinary,
];

export function HorizontalBarChart({
  data,
  maxValue,
  showLegend = false,
  referenceLine,
}: HorizontalBarChartProps) {
  if (!data || data.length === 0) {
    return (
      <div className="text-muted-foreground flex h-48 items-center justify-center text-sm">
        No data available
      </div>
    );
  }

  // Get unique segment labels for creating stacked bars
  const allSegmentLabels = Array.from(new Set(data.flatMap((d) => d.segments.map((s) => s.label))));

  // Build color map from first occurrence, falling back to theme colors
  const colorMap: Record<string, string> = {};
  let colorIdx = 0;
  for (const item of data) {
    for (const seg of item.segments) {
      if (!colorMap[seg.label]) {
        colorMap[seg.label] = seg.color || THEME_COLORS[colorIdx % THEME_COLORS.length];
        colorIdx++;
      }
    }
  }

  // Transform to Recharts flat data format
  const chartData = data.map((item) => {
    const row: Record<string, string | number> = { label: item.label };
    for (const seg of item.segments) {
      row[seg.label] = seg.value;
    }
    return row;
  });

  // Calculate domain max
  const computedMax =
    maxValue ??
    Math.max(...data.map((item) => item.segments.reduce((sum, seg) => sum + seg.value, 0)));

  const barHeight = 28;
  const chartHeight = Math.max(data.length * (barHeight + 16) + 40, 120);

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        data={chartData}
        layout="vertical"
        margin={{ top: 4, right: 24, bottom: 4, left: 80 }}
      >
        <CartesianGrid
          strokeDasharray={CHART_THEME.grid.strokeDasharray}
          stroke={CHART_THEME.grid.stroke}
          horizontal={false}
        />
        <XAxis
          type="number"
          domain={[0, computedMax]}
          tick={{ fontSize: 12 }}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          type="category"
          dataKey="label"
          tick={{ fontSize: 12 }}
          tickLine={false}
          axisLine={false}
          width={76}
        />
        <Tooltip
          cursor={{ fill: "var(--color-muted, #f5f5f5)", opacity: 0.5 }}
          contentStyle={{
            fontSize: 12,
            backgroundColor: CHART_THEME.tooltip.background,
            color: CHART_THEME.tooltip.text,
            border: CHART_THEME.tooltip.border,
            borderRadius: CHART_THEME.tooltip.borderRadius,
            boxShadow: CHART_THEME.tooltip.boxShadow,
          }}
        />
        {showLegend && (
          <Legend
            verticalAlign="bottom"
            iconType="square"
            iconSize={10}
            wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
          />
        )}
        {referenceLine != null && (
          <ReferenceLine
            x={referenceLine}
            stroke={CHART_THEME.slate.muted}
            strokeDasharray="4 4"
            label={{
              value: String(referenceLine),
              position: "top",
              fontSize: 11,
              fill: CHART_THEME.slate.muted,
            }}
          />
        )}
        {allSegmentLabels.map((segLabel) => (
          <Bar
            key={segLabel}
            dataKey={segLabel}
            stackId="stack"
            fill={colorMap[segLabel]}
            radius={CHART_THEME.bar.radius}
            barSize={barHeight}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
