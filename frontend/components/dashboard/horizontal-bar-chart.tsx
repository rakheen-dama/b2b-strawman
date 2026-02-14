"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";

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
}

const DEFAULT_COLORS = [
  "var(--color-chart-1, #2563eb)",
  "var(--color-chart-2, #e11d48)",
  "var(--color-chart-3, #e77e23)",
  "var(--color-chart-4, #8b5cf6)",
  "var(--color-chart-5, #06b6d4)",
];

export function HorizontalBarChart({
  data,
  maxValue,
  showLegend = false,
}: HorizontalBarChartProps) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-muted-foreground">
        No data available
      </div>
    );
  }

  // Get unique segment labels for creating stacked bars
  const allSegmentLabels = Array.from(
    new Set(data.flatMap((d) => d.segments.map((s) => s.label)))
  );

  // Build color map from first occurrence, falling back to defaults
  const colorMap: Record<string, string> = {};
  let colorIdx = 0;
  for (const item of data) {
    for (const seg of item.segments) {
      if (!colorMap[seg.label]) {
        colorMap[seg.label] =
          seg.color || DEFAULT_COLORS[colorIdx % DEFAULT_COLORS.length];
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
    Math.max(
      ...data.map((item) =>
        item.segments.reduce((sum, seg) => sum + seg.value, 0)
      )
    );

  const barHeight = 28;
  const chartHeight = Math.max(data.length * (barHeight + 16) + 40, 120);

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        data={chartData}
        layout="vertical"
        margin={{ top: 4, right: 24, bottom: 4, left: 80 }}
      >
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
            borderRadius: 6,
            border: "1px solid var(--color-border, #e5e5e5)",
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
        {allSegmentLabels.map((segLabel) => (
          <Bar
            key={segLabel}
            dataKey={segLabel}
            stackId="stack"
            fill={colorMap[segLabel]}
            radius={[0, 0, 0, 0]}
            barSize={barHeight}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
