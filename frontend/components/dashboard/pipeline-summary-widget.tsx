"use client";

import useSWR from "swr";
import Link from "next/link";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { useCapabilities } from "@/lib/capabilities";
import { formatCurrency } from "@/lib/format";
import { fetchPipelineSummary } from "@/lib/actions/pipeline-summary";

interface PipelineSummaryWidgetProps {
  slug: string;
}

const REFRESH_INTERVAL_MS = 300_000; // 5 minutes

export function PipelineSummaryWidget({ slug }: PipelineSummaryWidgetProps) {
  const { isAdmin, isOwner, isLoading: capsLoading } = useCapabilities();
  const canView = isAdmin || isOwner;

  const { data, error, isLoading } = useSWR(
    canView ? `pipeline-summary-${slug}` : null,
    () => fetchPipelineSummary(),
    {
      refreshInterval: REFRESH_INTERVAL_MS,
      dedupingInterval: 60_000,
      revalidateOnFocus: false,
    }
  );

  // Self-gate: only admins/owners see pipeline aggregates (Section 11.6 dashboard scope).
  if (capsLoading || !canView) {
    return null;
  }

  const cardHeader = (
    <CardHeader className="pb-2">
      <CardTitle className="text-sm font-medium">Sales Pipeline</CardTitle>
    </CardHeader>
  );

  if (isLoading) {
    return (
      <Card data-testid="pipeline-summary-widget">
        {cardHeader}
        <CardContent>
          <p className="text-xs text-slate-500 italic">Loading&hellip;</p>
        </CardContent>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card data-testid="pipeline-summary-widget">
        {cardHeader}
        <CardContent>
          <p className="text-xs text-slate-500 italic">Unable to load pipeline data.</p>
        </CardContent>
      </Card>
    );
  }

  const currency = data.currency || "ZAR";
  const winRatePct = Math.round((data.winRate ?? 0) * 100);
  const stages = data.stages ?? [];
  const maxWeighted = stages.reduce((max, s) => Math.max(max, s.weightedValue ?? 0), 0);

  return (
    <Card data-testid="pipeline-summary-widget">
      {cardHeader}
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Open weighted value
            </p>
            <p className="font-mono text-2xl font-bold tracking-tight text-slate-950 tabular-nums dark:text-slate-50">
              {formatCurrency(data.openWeightedValue, currency)}
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Win rate
            </p>
            <p className="font-mono text-lg font-semibold text-slate-950 tabular-nums dark:text-slate-50">
              {winRatePct}%
            </p>
          </div>
        </div>

        {stages.length > 0 ? (
          <div className="space-y-2">
            {stages.map((stage) => {
              const pct =
                maxWeighted > 0 ? Math.round(((stage.weightedValue ?? 0) / maxWeighted) * 100) : 0;
              return (
                <div key={stage.stageId} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-600 dark:text-slate-300">
                      {stage.stageName}{" "}
                      <span className="text-slate-400 dark:text-slate-500">
                        ({stage.dealCount})
                      </span>
                    </span>
                    <span className="font-mono text-slate-500 tabular-nums dark:text-slate-400">
                      {formatCurrency(stage.weightedValue ?? 0, currency)}
                    </span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                    <div className="h-full rounded-full bg-teal-500" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-xs text-slate-500 italic">No open deals.</p>
        )}

        <Link
          href={`/org/${slug}/pipeline`}
          className="inline-block text-xs font-medium text-teal-600 hover:underline dark:text-teal-400"
        >
          View pipeline →
        </Link>
      </CardContent>
    </Card>
  );
}
