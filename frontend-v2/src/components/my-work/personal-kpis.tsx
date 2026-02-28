import { KpiStrip } from "@/components/layout/kpi-strip";
import type { PersonalDashboardResponse } from "@/lib/dashboard-types";

interface PersonalKpisProps {
  data: PersonalDashboardResponse | null;
  periodLabel?: string;
}

function formatHours(hours: number): string {
  if (hours === 0) return "0";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}

export function PersonalKpis({ data, periodLabel }: PersonalKpisProps) {
  const hoursLabel = periodLabel ? `Hours ${periodLabel}` : "Hours";

  if (!data) {
    return (
      <KpiStrip
        items={[
          { label: hoursLabel, value: "--" },
          { label: "Billable %", value: "--" },
          { label: "Overdue Tasks", value: "--" },
        ]}
      />
    );
  }

  const { utilization, overdueTaskCount } = data;

  return (
    <KpiStrip
      items={[
        {
          label: hoursLabel,
          value: formatHours(utilization.totalHours),
        },
        {
          label: "Billable %",
          value:
            utilization.billablePercent != null
              ? `${Math.round(utilization.billablePercent)}%`
              : "--",
        },
        {
          label: "Overdue Tasks",
          value: overdueTaskCount,
        },
      ]}
    />
  );
}
