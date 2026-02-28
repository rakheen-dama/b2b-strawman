import type { KpiResponse } from "@/lib/dashboard-types";
import { KpiStrip } from "@/components/layout/kpi-strip";

interface DashboardKpiCardsProps {
  kpis: KpiResponse | null;
  isAdmin: boolean;
}

function computeChange(
  current: number,
  previous: number,
): { value: number; direction: "up" | "down" | "flat" } | undefined {
  if (previous === 0 && current === 0) return undefined;
  if (previous === 0) return { value: 100, direction: "up" };
  const pct = Math.round(((current - previous) / previous) * 100);
  if (pct === 0) return { value: 0, direction: "flat" };
  return { value: Math.abs(pct), direction: pct > 0 ? "up" : "down" };
}

function formatHours(hours: number): string {
  if (hours === 0) return "0";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}

export function DashboardKpiCards({ kpis, isAdmin }: DashboardKpiCardsProps) {
  if (!kpis) {
    return (
      <KpiStrip
        items={[
          { label: "Active Projects", value: "--" },
          { label: "Hours Logged", value: "--" },
          { label: "Overdue Tasks", value: "--" },
        ]}
      />
    );
  }

  const prev = kpis.previousPeriod;

  const items = [
    {
      label: "Active Projects",
      value: kpis.activeProjectCount,
      trend: computeChange(kpis.activeProjectCount, prev.activeProjectCount),
    },
    {
      label: "Hours Logged",
      value: formatHours(kpis.totalHoursLogged),
      trend: computeChange(kpis.totalHoursLogged, prev.totalHoursLogged),
    },
    {
      label: "Overdue Tasks",
      value: kpis.overdueTaskCount,
      trend: kpis.overdueTaskCount > 0
        ? computeChange(kpis.overdueTaskCount, prev.overdueTaskCount)
        : undefined,
    },
  ];

  if (isAdmin) {
    items.push({
      label: "Billable %",
      value:
        kpis.billablePercent != null
          ? `${Math.round(kpis.billablePercent)}%`
          : "--",
      trend:
        kpis.billablePercent != null && prev.billablePercent != null
          ? computeChange(kpis.billablePercent, prev.billablePercent)
          : undefined,
    });

    items.push({
      label: "Avg. Margin",
      value:
        kpis.averageMarginPercent != null
          ? `${Math.round(kpis.averageMarginPercent)}%`
          : "--",
      trend:
        kpis.averageMarginPercent != null && prev.averageMarginPercent != null
          ? computeChange(
              kpis.averageMarginPercent,
              prev.averageMarginPercent,
            )
          : undefined,
    });
  }

  return <KpiStrip items={items} />;
}
