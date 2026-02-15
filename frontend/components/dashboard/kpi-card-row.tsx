import { KpiCard } from "@/components/dashboard/kpi-card";
import type { KpiResponse } from "@/lib/dashboard-types";

interface KpiCardRowProps {
  kpis: KpiResponse | null;
  isAdmin: boolean;
  orgSlug: string;
}

function computeChangePercent(
  current: number,
  previous: number
): number | null {
  if (previous === 0) return current > 0 ? 100 : null;
  return Math.round(((current - previous) / previous) * 100);
}

function changeDirection(
  changePercent: number | null,
  invertPositive: boolean = false
): "positive" | "negative" | "neutral" | undefined {
  if (changePercent == null) return undefined;
  if (changePercent === 0) return "neutral";

  if (invertPositive) {
    // For metrics where lower is better (e.g. overdue tasks)
    return changePercent < 0 ? "positive" : "negative";
  }
  // For metrics where higher is better
  return changePercent > 0 ? "positive" : "negative";
}

export function KpiCardRow({ kpis, isAdmin, orgSlug }: KpiCardRowProps) {
  if (!kpis) {
    return (
      <div className="grid auto-rows-fr grid-cols-2 gap-4 lg:grid-cols-3">
        <KpiCard
          label="Active Projects"
          value={0}
          emptyState="No data"
          href={`/org/${orgSlug}/projects`}
        />
        <KpiCard label="Hours Logged" value={0} emptyState="No data" />
        <KpiCard label="Overdue Tasks" value={0} emptyState="No data" />
      </div>
    );
  }

  const prev = kpis.previousPeriod;
  const trendValues = kpis.trend.map((t) => t.value);

  const activeProjectsChange = computeChangePercent(
    kpis.activeProjectCount,
    prev.activeProjectCount
  );
  const hoursChange = computeChangePercent(
    kpis.totalHoursLogged,
    prev.totalHoursLogged
  );
  const overdueChange = computeChangePercent(
    kpis.overdueTaskCount,
    prev.overdueTaskCount
  );

  const cards = [
    {
      label: "Active Projects",
      value: kpis.activeProjectCount,
      changePercent: activeProjectsChange,
      changeDirection: changeDirection(activeProjectsChange),
      trend: trendValues,
      href: `/org/${orgSlug}/projects`,
      emptyState: "No projects yet",
      adminOnly: false,
    },
    {
      label: "Hours Logged",
      value: formatHours(kpis.totalHoursLogged),
      changePercent: hoursChange,
      changeDirection: changeDirection(hoursChange),
      trend: trendValues,
      href: null,
      emptyState: "No hours logged",
      adminOnly: false,
    },
    {
      label: "Billable %",
      value:
        kpis.billablePercent != null
          ? `${Math.round(kpis.billablePercent)}%`
          : 0,
      changePercent:
        kpis.billablePercent != null && prev.billablePercent != null
          ? computeChangePercent(kpis.billablePercent, prev.billablePercent)
          : null,
      changeDirection:
        kpis.billablePercent != null && prev.billablePercent != null
          ? changeDirection(
              computeChangePercent(kpis.billablePercent, prev.billablePercent)
            )
          : undefined,
      trend: null,
      href: `/org/${orgSlug}/profitability`,
      emptyState: "No data",
      adminOnly: true,
    },
    {
      label: "Overdue Tasks",
      value: kpis.overdueTaskCount,
      changePercent: overdueChange,
      changeDirection: changeDirection(overdueChange, true),
      trend: null,
      href: null,
      emptyState: null,
      adminOnly: false,
    },
    {
      label: "Avg. Margin",
      value:
        kpis.averageMarginPercent != null
          ? `${Math.round(kpis.averageMarginPercent)}%`
          : 0,
      changePercent:
        kpis.averageMarginPercent != null &&
        prev.averageMarginPercent != null
          ? computeChangePercent(
              kpis.averageMarginPercent,
              prev.averageMarginPercent
            )
          : null,
      changeDirection:
        kpis.averageMarginPercent != null &&
        prev.averageMarginPercent != null
          ? changeDirection(
              computeChangePercent(
                kpis.averageMarginPercent,
                prev.averageMarginPercent
              )
            )
          : undefined,
      trend: null,
      href: `/org/${orgSlug}/profitability`,
      emptyState: "No data",
      adminOnly: true,
    },
  ];

  const visibleCards = cards.filter((card) => !card.adminOnly || isAdmin);

  const gridCols =
    visibleCards.length === 5
      ? "grid-cols-2 lg:grid-cols-5"
      : "grid-cols-1 sm:grid-cols-3";

  return (
    <div className={`grid auto-rows-fr gap-4 ${gridCols}`}>
      {visibleCards.map((card) => (
        <KpiCard
          key={card.label}
          label={card.label}
          value={card.value}
          changePercent={card.changePercent}
          changeDirection={card.changeDirection}
          trend={card.trend}
          href={card.href}
          emptyState={card.emptyState}
        />
      ))}
    </div>
  );
}

function formatHours(hours: number): string {
  if (hours === 0) return "0";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}
