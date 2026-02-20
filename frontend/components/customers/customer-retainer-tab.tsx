"use client";

import Link from "next/link";
import { FileText } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import { RetainerStatusBadge } from "@/components/retainers/retainer-status-badge";
import { RetainerProgress } from "@/components/retainers/retainer-progress";
import { PeriodHistoryTable } from "@/components/retainers/period-history-table";
import { FREQUENCY_LABELS, TYPE_LABELS } from "@/lib/retainer-constants";
import { formatLocalDate } from "@/lib/format";
import type { RetainerResponse, PeriodSummary } from "@/lib/api/retainers";

interface CustomerRetainerTabProps {
  retainer: RetainerResponse | null;
  allRetainers: RetainerResponse[];
  periods: PeriodSummary[];
  slug: string;
  customerId: string;
  canManage: boolean;
}

export function CustomerRetainerTab({
  retainer,
  allRetainers,
  periods,
  slug,
}: CustomerRetainerTabProps) {
  // State 1: No retainer at all
  if (allRetainers.length === 0) {
    return (
      <EmptyState
        icon={FileText}
        title="No retainer agreement"
        description="Set up a recurring engagement with this customer."
        actionLabel="Set Up Retainer"
        actionHref={`/org/${slug}/retainers`}
      />
    );
  }

  // State 3: Only terminated retainers
  const allTerminated = allRetainers.every((r) => r.status === "TERMINATED");
  if (allTerminated) {
    return (
      <div className="space-y-6">
        <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
          Historical retainer data
        </div>
        <PeriodHistoryTable periods={periods} slug={slug} />
        <div className="flex justify-center">
          <Button asChild variant="outline" size="sm">
            <Link href={`/org/${slug}/retainers`}>Create New Retainer</Link>
          </Button>
        </div>
      </div>
    );
  }

  // State 2: Active or paused retainer
  return (
    <div className="space-y-6">
      {retainer && (
        <Card className="shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0">
            <CardTitle className="font-display text-slate-900 dark:text-slate-100">
              {retainer.name}
            </CardTitle>
            <RetainerStatusBadge status={retainer.status} />
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-sm text-slate-600 dark:text-slate-400">
              <span>
                {TYPE_LABELS[retainer.type]} &middot;{" "}
                {FREQUENCY_LABELS[retainer.frequency]}
              </span>
              <span className="mx-2">&middot;</span>
              <span>Start: {formatLocalDate(retainer.startDate)}</span>
              <span className="mx-2">&middot;</span>
              <span>
                End:{" "}
                {retainer.endDate
                  ? formatLocalDate(retainer.endDate)
                  : "Ongoing"}
              </span>
            </div>

            {retainer.currentPeriod && (
              <div className="space-y-2">
                <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Current Period:{" "}
                  {formatLocalDate(retainer.currentPeriod.periodStart)} &ndash;{" "}
                  {formatLocalDate(retainer.currentPeriod.periodEnd)}
                </p>
                <RetainerProgress
                  type={retainer.type}
                  consumedHours={retainer.currentPeriod.consumedHours}
                  allocatedHours={retainer.allocatedHours}
                />
              </div>
            )}

            {retainer.notes && (
              <p className="text-sm text-slate-500">{retainer.notes}</p>
            )}
          </CardContent>
        </Card>
      )}

      <PeriodHistoryTable periods={periods} slug={slug} />
    </div>
  );
}
