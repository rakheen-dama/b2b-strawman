"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  CartesianGrid,
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { MemberUtilizationRecord } from "@/lib/types";

interface UtilizationChartProps {
  data: MemberUtilizationRecord[];
}

function utilizationColor(percent: number): string {
  if (percent >= 70) return "#10b981"; // emerald-500
  if (percent >= 40) return "#f59e0b"; // amber-500
  return "#94a3b8"; // slate-400
}

export function UtilizationChart({ data }: UtilizationChartProps) {
  if (data.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Team Utilization</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="py-12 text-center text-sm text-slate-500">
            No utilization data for this period.
          </p>
        </CardContent>
      </Card>
    );
  }

  const chartData = data
    .sort((a, b) => b.utilizationPercent - a.utilizationPercent)
    .map((m) => ({
      name: m.memberName,
      utilization: Number(m.utilizationPercent.toFixed(1)),
      billable: Number(m.billableHours.toFixed(1)),
      nonBillable: Number(m.nonBillableHours.toFixed(1)),
      total: Number(m.totalHours.toFixed(1)),
    }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Team Utilization</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[300px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              layout="vertical"
              margin={{ top: 0, right: 20, bottom: 0, left: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                horizontal={false}
                stroke="#e2e8f0"
              />
              <XAxis
                type="number"
                domain={[0, 100]}
                tick={{ fontSize: 12, fill: "#64748b" }}
                tickFormatter={(v) => `${v}%`}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={120}
                tick={{ fontSize: 12, fill: "#334155" }}
              />
              <Tooltip
                cursor={{ fill: "#f1f5f9" }}
                contentStyle={{
                  borderRadius: "8px",
                  border: "1px solid #e2e8f0",
                  boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
                  fontSize: "13px",
                }}
              />
              <Bar dataKey="utilization" radius={[0, 4, 4, 0]} barSize={20}>
                {chartData.map((entry, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={utilizationColor(entry.utilization)}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
