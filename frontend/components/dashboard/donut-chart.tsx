"use client";

import {
  PieChart,
  Pie,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
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

function CenterContent({
  cx,
  cy,
  centerValue,
  centerLabel,
}: {
  cx: number;
  cy: number;
  centerValue?: string;
  centerLabel?: string;
}) {
  if (!centerValue && !centerLabel) return null;

  return (
    <g>
      {centerValue && (
        <text
          x={cx}
          y={centerLabel ? cy - 6 : cy}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-current font-mono text-lg font-bold tabular-nums"
        >
          {centerValue}
        </text>
      )}
      {centerLabel && (
        <text
          x={cx}
          y={centerValue ? cy + 14 : cy}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-current text-xs opacity-60"
        >
          {centerLabel}
        </text>
      )}
    </g>
  );
}

export function DonutChart({
  data,
  centerValue,
  centerLabel,
  height = 200,
  className,
}: DonutChartProps) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-muted-foreground">
        No data available
      </div>
    );
  }

  const { donut } = CHART_THEME;

  return (
    <div data-testid="donut-chart" className={cn(className)}>
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
              <Cell
                key={i}
                fill={entry.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length]}
              />
            ))}
            {(centerValue || centerLabel) && (
              <CenterContent
                cx={0}
                cy={0}
                centerValue={centerValue}
                centerLabel={centerLabel}
              />
            )}
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
    </div>
  );
}
