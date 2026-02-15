import { KpiCard } from "@/components/dashboard/kpi-card";
import type {
  PersonalDashboardResponse,
  TrendPoint,
} from "@/lib/dashboard-types";

interface PersonalKpisProps {
  data: PersonalDashboardResponse | null;
  periodLabel?: string;
}

function formatHours(hours: number): string {
  if (hours === 0) return "0";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}

function trendValues(trend: TrendPoint[]): number[] {
  return trend.map((t) => t.value);
}

export function PersonalKpis({ data, periodLabel }: PersonalKpisProps) {
  const hoursLabel = periodLabel ? `Hours ${periodLabel}` : "Hours";

  if (!data) {
    return (
      <div className="grid auto-rows-fr grid-cols-1 gap-4 sm:grid-cols-3">
        <KpiCard label={hoursLabel} value={0} emptyState="No data" />
        <KpiCard label="Billable %" value={0} emptyState="No data" />
        <KpiCard label="Overdue Tasks" value={0} emptyState="No data" />
      </div>
    );
  }

  const { utilization, overdueTaskCount, trend } = data;
  const sparkline = trendValues(trend);

  const billableValue =
    utilization.billablePercent != null
      ? `${Math.round(utilization.billablePercent)}%`
      : 0;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <KpiCard
        label={hoursLabel}
        value={formatHours(utilization.totalHours)}
        trend={sparkline}
        emptyState="No hours logged"
      />
      <KpiCard
        label="Billable %"
        value={billableValue}
        emptyState="No billable data"
      />
      <KpiCard
        label="Overdue Tasks"
        value={overdueTaskCount}
        emptyState={null}
      />
    </div>
  );
}
