"use client";

import { useRouter } from "next/navigation";
import { ClipboardList, AlertTriangle, CheckCircle2 } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { InformationRequestSummary } from "@/lib/api/information-requests";

interface InformationRequestsWidgetProps {
  data: InformationRequestSummary | null;
  orgSlug: string;
}

export function InformationRequestsWidget({
  data,
  orgSlug,
}: InformationRequestsWidgetProps) {
  const router = useRouter();

  if (data === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ClipboardList className="h-4 w-4" />
            Information Requests
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            Unable to load request data.
          </p>
        </CardContent>
      </Card>
    );
  }

  const pendingReview =
    data.itemsPendingReview ?? data.sentCount + data.inProgressCount;
  const overdueCount = data.overdueRequests ?? 0;
  const completionRate =
    data.completionRateLast30Days ??
    (data.totalRequests > 0
      ? data.completedCount / data.totalRequests
      : 0);

  const hasNoRequests = data.totalRequests === 0;

  if (hasNoRequests) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ClipboardList className="h-4 w-4" />
            Information Requests
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            No information requests yet.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ClipboardList className="h-4 w-4" />
          Information Requests
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-3 gap-3">
          <div className="text-center">
            <p className="font-mono text-2xl font-semibold tabular-nums text-slate-950 dark:text-slate-50">
              {pendingReview}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Pending Review
            </p>
          </div>
          <div className="text-center">
            <p
              className={`font-mono text-2xl font-semibold tabular-nums ${
                overdueCount > 0
                  ? "text-amber-600 dark:text-amber-400"
                  : "text-slate-950 dark:text-slate-50"
              }`}
            >
              {overdueCount}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Overdue
            </p>
          </div>
          <div className="text-center">
            <p className="font-mono text-2xl font-semibold tabular-nums text-slate-950 dark:text-slate-50">
              {Math.round(completionRate * 100)}%
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Completed
            </p>
          </div>
        </div>

        {overdueCount > 0 && (
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
            <p className="text-sm text-slate-700 dark:text-slate-300">
              <span className="font-mono font-semibold tabular-nums">
                {overdueCount}
              </span>{" "}
              {overdueCount === 1 ? "request is" : "requests are"} overdue
            </p>
          </div>
        )}

        {overdueCount === 0 && pendingReview === 0 && (
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-green-500" />
            <p className="text-sm text-green-600 dark:text-green-400">
              All requests are up to date.
            </p>
          </div>
        )}
      </CardContent>
      <CardFooter>
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => router.push(`/org/${orgSlug}/customers`)}
        >
          View customer requests &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
