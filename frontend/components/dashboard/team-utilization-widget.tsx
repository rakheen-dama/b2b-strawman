"use client";

import useSWR from "swr";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { KpiCard } from "@/components/dashboard/kpi-card";
import { useProfile } from "@/lib/hooks/useProfile";
import { fetchTeamUtilizationTrend } from "@/lib/actions/utilization";

interface TeamUtilizationWidgetProps {
  slug: string;
}

const REFRESH_INTERVAL_MS = 300_000; // 5 minutes

export function TeamUtilizationWidget({ slug }: TeamUtilizationWidgetProps) {
  const profile = useProfile();

  const shouldFetch = profile === "consulting-za";

  const { data, error, isLoading } = useSWR(
    shouldFetch ? `team-utilization-trend-${slug}` : null,
    () => fetchTeamUtilizationTrend(4),
    {
      refreshInterval: REFRESH_INTERVAL_MS,
      dedupingInterval: 60_000,
      revalidateOnFocus: false,
    }
  );

  // Self-gate: render nothing unless profile is consulting-za (Section 66.8, ADR-246).
  if (profile !== "consulting-za") {
    return null;
  }

  if (isLoading) {
    return (
      <Card data-testid="team-utilization-widget">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">Team Billable Utilization</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-slate-500 italic">Loading&hellip;</p>
        </CardContent>
      </Card>
    );
  }

  if (error || !data || data.length === 0) {
    return (
      <Card data-testid="team-utilization-widget">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">Team Billable Utilization</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-slate-500 italic">Unable to load utilization data.</p>
        </CardContent>
      </Card>
    );
  }

  // Data order: oldest first → newest last (set by the server action).
  const trend = data.map((w) => Math.round(w.teamAverages.avgBillableUtilizationPct));
  const current = trend[trend.length - 1] ?? 0;
  const prior = trend.length >= 2 ? (trend[trend.length - 2] ?? current) : current;
  const delta = current - prior; // percentage points
  const direction: "positive" | "negative" | "neutral" =
    delta > 0 ? "positive" : delta < 0 ? "negative" : "neutral";

  return (
    <div data-testid="team-utilization-widget">
      <KpiCard
        label="Team Billable Utilization"
        value={`${current}%`}
        changePercent={delta}
        changeDirection={direction}
        trend={trend}
        href={`/org/${slug}/resources/utilization`}
      />
    </div>
  );
}
