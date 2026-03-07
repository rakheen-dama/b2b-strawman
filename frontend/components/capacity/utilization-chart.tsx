"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ReferenceLine,
  ResponsiveContainer,
} from "recharts";
import type { TeamUtilizationResponse } from "@/lib/api/capacity";

interface UtilizationChartProps {
  data: TeamUtilizationResponse;
}

export function UtilizationChart({ data }: UtilizationChartProps) {
  if (data.members.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-sm text-muted-foreground">
        No utilization data available
      </div>
    );
  }

  const chartData = data.members.map((m) => ({
    label: m.memberName,
    planned: m.avgPlannedUtilizationPct,
    actual: m.avgActualUtilizationPct,
  }));

  const teamAvg = data.teamAverages.avgActualUtilizationPct;

  const maxVal = Math.max(
    ...chartData.map((d) => Math.max(d.planned, d.actual)),
    teamAvg,
    100,
  );

  const barHeight = 28;
  const chartHeight = Math.max(
    chartData.length * (barHeight + 16) + 60,
    160,
  );

  return (
    <div>
      <h3 className="mb-3 text-sm font-medium text-slate-700 dark:text-slate-300">
        Planned vs Actual Utilization
      </h3>
      <ResponsiveContainer width="100%" height={chartHeight}>
        <BarChart
          data={chartData}
          layout="vertical"
          margin={{ top: 4, right: 24, bottom: 4, left: 100 }}
        >
          <XAxis
            type="number"
            domain={[0, Math.ceil(maxVal / 10) * 10]}
            tick={{ fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            unit="%"
          />
          <YAxis
            type="category"
            dataKey="label"
            tick={{ fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={96}
          />
          <Tooltip
            cursor={{ fill: "var(--color-muted, #f5f5f5)", opacity: 0.5 }}
            contentStyle={{
              fontSize: 12,
              borderRadius: 6,
              border: "1px solid var(--color-border, #e5e5e5)",
            }}
            formatter={(value: number | undefined) => value != null ? `${value}%` : ""}
          />
          <Legend
            verticalAlign="bottom"
            iconType="square"
            iconSize={10}
            wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
          />
          <ReferenceLine
            x={teamAvg}
            stroke="var(--color-chart-4, #8b5cf6)"
            strokeDasharray="4 4"
            label={{
              value: `Avg ${teamAvg}%`,
              position: "top",
              fontSize: 11,
              fill: "var(--color-chart-4, #8b5cf6)",
            }}
          />
          <Bar
            dataKey="planned"
            name="Planned"
            fill="var(--color-chart-1, #2563eb)"
            barSize={barHeight}
            radius={[0, 2, 2, 0]}
          />
          <Bar
            dataKey="actual"
            name="Actual"
            fill="var(--color-chart-2, #14b8a6)"
            barSize={barHeight}
            radius={[0, 2, 2, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
