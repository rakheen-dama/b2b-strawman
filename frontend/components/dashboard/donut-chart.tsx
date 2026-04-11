"use client";

import { PieChart, Pie, Cell, Legend, ResponsiveContainer, Tooltip } from "recharts";
import { CHART_THEME } from "@/lib/chart-theme";
import { ChartTooltip } from "@/components/dashboard/chart-tooltip";
import { cn } from "@/lib/utils";

interface DonutChartProps {
  data: Array<{ name: string; value: number; color?: string }>;
  centerValue?: string;
  centerLabel?: string;
  height?: number;
  className?: string;
}

const DEFAULT_COLORS = [
  CHART_THEME.colors.primary,
  CHART_THEME.colors.secondary,
  CHART_THEME.colors.tertiary,
  CHART_THEME.colors.quaternary,
  CHART_THEME.colors.quinary,
];

export function DonutChart({
  data,
  centerValue,
  centerLabel,
  height = 200,
  className,
}: DonutChartProps) {
  if (!data || data.length === 0) {
    return (
      <div className="text-muted-foreground flex h-48 items-center justify-center text-sm">
        No data available
      </div>
    );
  }

  const { donut } = CHART_THEME;

  return (
    <div data-testid="donut-chart" className={cn("relative", className)}>
      <ResponsiveContainer width="100%" height={height}>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius={donut.innerRadius}
            outerRadius={donut.outerRadius}
            cornerRadius={donut.cornerRadius}
            paddingAngle={donut.paddingAngle}
            label={false}
            labelLine={false}
          >
            {data.map((entry, i) => (
              <Cell key={i} fill={entry.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip />} />
          <Legend
            verticalAlign="bottom"
            iconType="circle"
            iconSize={8}
            wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
          />
        </PieChart>
      </ResponsiveContainer>
      {(centerValue || centerLabel) && (
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          {centerValue && <span className="font-mono text-lg font-bold">{centerValue}</span>}
          {centerLabel && <span className="text-xs opacity-60">{centerLabel}</span>}
        </div>
      )}
    </div>
  );
}
