import type { ProposalStats } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { KpiStrip } from "@/components/layout/kpi-strip";

interface ProposalPipelineStatsProps {
  stats: ProposalStats;
}

export function ProposalPipelineStats({ stats }: ProposalPipelineStatsProps) {
  const items = [
    { label: "Total Open", value: stats.totalSent },
    { label: "Accepted", value: stats.totalAccepted },
    { label: "Declined", value: stats.totalDeclined },
    {
      label: "Conversion Rate",
      value: `${Math.round(stats.conversionRate)}%`,
    },
    {
      label: "Avg Days to Accept",
      value:
        stats.averageDaysToAccept > 0
          ? `${Math.round(stats.averageDaysToAccept)}d`
          : "\u2014",
    },
  ];

  return <KpiStrip items={items} />;
}
