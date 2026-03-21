"use client";

import { TrendingUp, TrendingDown } from "lucide-react";
import { Sparkline } from "@/components/dashboard/sparkline";
import { MicroStackedBar } from "@/components/dashboard/micro-stacked-bar";
import { RadialGauge } from "@/components/dashboard/radial-gauge";
import type { KpiResponse, ProjectHealth } from "@/lib/dashboard-types";
import type { TeamCapacityGrid } from "@/lib/api/capacity";

interface MetricsStripProps {
  kpis: KpiResponse | null;
  capacityData: TeamCapacityGrid | null;
  projectHealth: ProjectHealth[] | null;
}

function formatHours(hours: number): string {
  if (hours === 0) return "0";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  if (hours >= 1000) return `${(hours / 1000).toFixed(1)}k`;
  return `${hours.toFixed(1)}h`;
}

function computeTrendDirection(
  current: number | null | undefined,
  previous: number | null | undefined,
): "up" | "down" | null {
  if (current == null || previous == null) return null;
  if (current > previous) return "up";
  if (current < previous) return "down";
  return null;
}

export function MetricsStrip({
  kpis,
  capacityData,
  projectHealth,
}: MetricsStripProps) {
  const trendValues = kpis?.trend?.map((t) => t.value) ?? [];
  const billableHours = kpis
    ? (kpis.totalHoursLogged * (kpis.billablePercent ?? 0)) / 100
    : 0;
  const nonBillableHours = kpis
    ? kpis.totalHoursLogged - billableHours
    : 0;

  const utilization =
    capacityData?.weekSummaries?.[0]?.teamUtilizationPct ?? 0;

  const onTrack =
    projectHealth?.filter((p) => p.healthStatus === "HEALTHY").length ?? 0;
  const atRisk =
    projectHealth?.filter((p) => p.healthStatus === "AT_RISK").length ?? 0;
  const over =
    projectHealth?.filter((p) => p.healthStatus === "CRITICAL").length ?? 0;

  const marginDirection = computeTrendDirection(
    kpis?.averageMarginPercent,
    kpis?.previousPeriod?.averageMarginPercent,
  );

  const overdueCount = kpis?.overdueTaskCount ?? 0;

  return (
    <div
      data-testid="metrics-strip"
      className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6"
    >
      {/* Active Projects */}
      <div
        data-testid="metric-active-projects"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Active Projects
        </p>
        <div className="mt-1 flex items-center justify-between">
          <span className="font-mono text-xl font-bold tabular-nums">
            {kpis?.activeProjectCount ?? 0}
          </span>
          <Sparkline
            data={trendValues}
            width={64}
            height={20}
            color="var(--color-teal-500, #14b8a6)"
            className="text-teal-500"
          />
        </div>
      </div>

      {/* Hours This Month */}
      <div
        data-testid="metric-hours-month"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Hours This Month
        </p>
        <div className="mt-1 flex items-center justify-between">
          <span className="font-mono text-xl font-bold tabular-nums">
            {formatHours(kpis?.totalHoursLogged ?? 0)}
          </span>
          <MicroStackedBar
            segments={[
              {
                value: billableHours,
                color: "var(--color-teal-500, #14b8a6)",
                label: "Billable",
              },
              {
                value: nonBillableHours,
                color: "var(--color-slate-300, #cbd5e1)",
                label: "Non-billable",
              },
            ]}
            width={64}
            height={6}
          />
        </div>
      </div>

      {/* Avg. Margin */}
      <div
        data-testid="metric-revenue"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Avg. Margin
        </p>
        <div className="mt-1 flex items-center gap-2">
          <span className="font-mono text-xl font-bold tabular-nums">
            {kpis?.averageMarginPercent != null
              ? `${Math.round(kpis.averageMarginPercent)}%`
              : "--"}
          </span>
          {marginDirection === "up" && (
            <TrendingUp className="size-4 text-teal-500" />
          )}
          {marginDirection === "down" && (
            <TrendingDown className="size-4 text-red-500" />
          )}
        </div>
      </div>

      {/* Overdue Tasks */}
      <div
        data-testid="metric-overdue-tasks"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Overdue Tasks
        </p>
        <div className="mt-1">
          <span
            className={`font-mono text-xl font-bold tabular-nums ${
              overdueCount >= 5
                ? "text-red-600 dark:text-red-400"
                : overdueCount > 0
                  ? "text-amber-600 dark:text-amber-400"
                  : ""
            }`}
          >
            {overdueCount}
          </span>
        </div>
      </div>

      {/* Team Utilization */}
      <div
        data-testid="metric-team-utilization"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Team Utilization
        </p>
        <div className="mt-1 flex items-center justify-between">
          <span className="font-mono text-xl font-bold tabular-nums">
            {Math.round(utilization)}%
          </span>
          <RadialGauge value={utilization} size={36} strokeWidth={4} />
        </div>
      </div>

      {/* Budget Health */}
      <div
        data-testid="metric-budget-health"
        className="rounded-lg border border-slate-200/60 bg-card p-3"
      >
        <p className="text-[11px] font-medium uppercase tracking-wider text-slate-500">
          Budget Health
        </p>
        <div className="mt-1 flex items-center gap-2">
          <div className="flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-full bg-green-500" />
            <span className="font-mono text-sm font-bold tabular-nums">
              {onTrack}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-full bg-amber-500" />
            <span className="font-mono text-sm font-bold tabular-nums">
              {atRisk}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-full bg-red-500" />
            <span className="font-mono text-sm font-bold tabular-nums">
              {over}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
