"use client";

import useSWR from "swr";
import Link from "next/link";
import { RefreshCw, Activity } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchSyncSummaryAction } from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";
import { formatDate } from "@/lib/format";
import { defaultSWROptions } from "@/lib/swr/fetcher";
import { cn } from "@/lib/utils";
import type { SyncSummaryResponse } from "@/lib/types";

interface XeroSyncSummaryProps {
  slug: string;
}

interface SummaryItem {
  label: string;
  count: number;
  stateFilter: string;
  colorClass: string;
}

export function XeroSyncSummary({ slug }: XeroSyncSummaryProps) {
  const { data: summary, isLoading } = useSWR<SyncSummaryResponse | null>(
    `xero-sync-summary-${slug}`,
    async () => {
      const result = await fetchSyncSummaryAction(slug);
      return result.success && result.data ? result.data : null;
    },
    defaultSWROptions
  );

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <Activity className="size-5 text-slate-400" />
            <CardTitle className="font-display text-lg">Sync Summary</CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <RefreshCw className="size-4 animate-spin" />
            Loading sync data...
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!summary) return null;

  const basePath = `/org/${slug}/settings/integrations/xero/sync-log`;

  const items: SummaryItem[] = [
    {
      label: "Pending",
      count: summary.pending,
      stateFilter: "PENDING",
      colorClass: "text-blue-600 dark:text-blue-400",
    },
    {
      label: "In Flight",
      count: summary.inFlight,
      stateFilter: "IN_FLIGHT",
      colorClass: "text-amber-600 dark:text-amber-400",
    },
    {
      label: "Completed (24h)",
      count: summary.completedLast24h,
      stateFilter: "COMPLETED",
      colorClass: "text-green-600 dark:text-green-400",
    },
    {
      label: "Retrying",
      count: summary.failedRetrying,
      stateFilter: "FAILED_RETRYING",
      colorClass: "text-orange-600 dark:text-orange-400",
    },
    {
      label: "Dead Letter",
      count: summary.deadLetter,
      stateFilter: "DEAD_LETTER",
      colorClass: "text-red-600 dark:text-red-400",
    },
    {
      label: "Blocked (Trust)",
      count: summary.blockedTrustBoundary,
      stateFilter: "BLOCKED_TRUST_BOUNDARY",
      colorClass: "text-purple-600 dark:text-purple-400",
    },
    {
      label: "Reconcile Drift",
      count: summary.reconcileDrift,
      stateFilter: "RECONCILE_DRIFT",
      colorClass: "text-yellow-600 dark:text-yellow-400",
    },
  ];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Activity className="size-5 text-slate-400" />
            <CardTitle className="font-display text-lg">Sync Summary</CardTitle>
          </div>
          <Link
            href={basePath}
            className="text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            View Sync Log
          </Link>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          {items.map((item) => {
            const content = (
              <div className="flex flex-col gap-0.5">
                <span className="text-muted-foreground text-xs tracking-wider uppercase">
                  {item.label}
                </span>
                <span className={cn("font-mono text-xl font-bold tabular-nums", item.colorClass)}>
                  {item.count}
                </span>
              </div>
            );

            if (item.count > 0) {
              return (
                <Link
                  key={item.stateFilter}
                  href={`${basePath}?state=${item.stateFilter}`}
                  className="rounded-md p-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/50"
                >
                  {content}
                </Link>
              );
            }

            return (
              <div key={item.stateFilter} className="p-2 opacity-60">
                {content}
              </div>
            );
          })}
        </div>

        <div className="flex gap-6 border-t border-slate-200 pt-3 text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
          {summary.oldestPendingAt && (
            <span>
              Oldest pending:{" "}
              <span className="font-medium">{formatDate(summary.oldestPendingAt)}</span>
            </span>
          )}
          {summary.lastCompletedAt && (
            <span>
              Last completed:{" "}
              <span className="font-medium">{formatDate(summary.lastCompletedAt)}</span>
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
