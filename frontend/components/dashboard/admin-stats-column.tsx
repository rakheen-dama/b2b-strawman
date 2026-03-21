"use client";

import Link from "next/link";
import { Users, ClipboardList, Zap } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import type { AggregatedCompletenessResponse } from "@/lib/types";
import type { InformationRequestSummary } from "@/lib/api/information-requests";
import type { AutomationSummary } from "@/lib/api/automations";

interface AdminStatsColumnProps {
  aggregatedCompleteness: AggregatedCompletenessResponse | null;
  requestSummary: InformationRequestSummary | null;
  automationSummary: AutomationSummary | null;
  orgSlug: string;
}

export function AdminStatsColumn({
  aggregatedCompleteness,
  requestSummary,
  automationSummary,
  orgSlug,
}: AdminStatsColumnProps) {
  const incompleteProfiles = aggregatedCompleteness?.incompleteCount ?? 0;
  const pendingRequests =
    requestSummary?.itemsPendingReview ??
    ((requestSummary?.sentCount ?? 0) + (requestSummary?.inProgressCount ?? 0));
  const automationRuns = automationSummary?.todayTotal ?? 0;

  return (
    <Card data-testid="admin-stats-column">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Admin</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1 pt-0">
        <Link
          href={`/org/${orgSlug}/customers?showIncomplete=true`}
          className="flex items-center gap-3 rounded-md px-2 py-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
        >
          <Users className="size-4 shrink-0 text-slate-400" />
          <span className="font-mono text-sm font-bold tabular-nums">
            {incompleteProfiles}
          </span>
          <span className="text-xs text-slate-500">Incomplete profiles</span>
        </Link>

        <Link
          href={`/org/${orgSlug}/customers`}
          className="flex items-center gap-3 rounded-md px-2 py-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
        >
          <ClipboardList className="size-4 shrink-0 text-slate-400" />
          <span className="font-mono text-sm font-bold tabular-nums">
            {pendingRequests}
          </span>
          <span className="text-xs text-slate-500">Pending requests</span>
        </Link>

        <Link
          href={`/org/${orgSlug}/settings/automations`}
          className="flex items-center gap-3 rounded-md px-2 py-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
        >
          <Zap className="size-4 shrink-0 text-slate-400" />
          <span className="font-mono text-sm font-bold tabular-nums">
            {automationRuns}
          </span>
          <span className="text-xs text-slate-500">Automation runs today</span>
        </Link>
      </CardContent>
    </Card>
  );
}
