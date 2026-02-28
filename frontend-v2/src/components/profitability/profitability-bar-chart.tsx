"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Legend,
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProjectProfitabilitySummary } from "@/lib/types";

interface ProfitabilityBarChartProps {
  data: ProjectProfitabilitySummary[];
}

export function ProfitabilityBarChart({ data }: ProfitabilityBarChartProps) {
  if (data.length === 0) {
    return null;
  }

  const chartData = data
    .filter((p) => p.billableValue > 0 || (p.costValue ?? 0) > 0)
    .sort((a, b) => b.billableValue - a.billableValue)
    .slice(0, 12) // top 12 projects for readability
    .map((p) => ({
      name:
        p.projectName.length > 20
          ? p.projectName.slice(0, 18) + "..."
          : p.projectName,
      revenue: Number(p.billableValue.toFixed(2)),
      cost: Number((p.costValue ?? 0).toFixed(2)),
      margin: Number((p.margin ?? 0).toFixed(2)),
    }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          Revenue vs Cost by Project
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[350px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              margin={{ top: 0, right: 20, bottom: 0, left: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                vertical={false}
                stroke="#e2e8f0"
              />
              <XAxis
                dataKey="name"
                tick={{ fontSize: 11, fill: "#64748b" }}
                angle={-25}
                textAnchor="end"
                height={60}
              />
              <YAxis
                tick={{ fontSize: 12, fill: "#64748b" }}
                tickFormatter={(v) =>
                  v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)
                }
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
              <Legend
                wrapperStyle={{ fontSize: "13px" }}
                iconType="square"
              />
              <Bar
                dataKey="revenue"
                fill="#0d9488"
                name="Revenue"
                radius={[4, 4, 0, 0]}
              />
              <Bar
                dataKey="cost"
                fill="#94a3b8"
                name="Cost"
                radius={[4, 4, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
