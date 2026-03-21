"use client";

import Link from "next/link";
import useSWR from "swr";
import { CalendarClock } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { fetchDeadlineSummary } from "@/app/(app)/org/[slug]/deadlines/actions";
import type { DeadlineSummary } from "@/lib/types";

interface DeadlineWidgetProps {
  orgSlug: string;
}

const REFRESH_INTERVAL_MS = 300_000; // 5 minutes

function getCurrentMonthRange(): { from: string; to: string } {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth(); // 0-indexed
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0); // day 0 of next month = last day of current
  const pad = (n: number) => String(n).padStart(2, "0");
  return {
    from: `${firstDay.getFullYear()}-${pad(firstDay.getMonth() + 1)}-${pad(firstDay.getDate())}`,
    to: `${lastDay.getFullYear()}-${pad(lastDay.getMonth() + 1)}-${pad(lastDay.getDate())}`,
  };
}

function aggregateSummaries(summaries: DeadlineSummary[]) {
  return summaries.reduce(
    (acc, s) => ({
      total: acc.total + s.total,
      filed: acc.filed + s.filed,
      pending: acc.pending + s.pending,
      overdue: acc.overdue + s.overdue,
    }),
    { total: 0, filed: 0, pending: 0, overdue: 0 },
  );
}

export function DeadlineWidget({ orgSlug }: DeadlineWidgetProps) {
  const { from, to } = getCurrentMonthRange();

  const { data, error, isLoading } = useSWR(
    `deadline-summary-${orgSlug}`,
    () => fetchDeadlineSummary(from, to),
    {
      refreshInterval: REFRESH_INTERVAL_MS,
      dedupingInterval: 2000,
      revalidateOnFocus: true,
    },
  );

  if (isLoading) {
    return (
      <Card data-testid="deadline-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <CalendarClock className="size-4" />
            Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs italic text-slate-500">
            Loading&hellip;
          </p>
        </CardContent>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card data-testid="deadline-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <CalendarClock className="size-4" />
            Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs italic text-slate-500">
            Unable to load deadline data.
          </p>
        </CardContent>
      </Card>
    );
  }

  const totals = aggregateSummaries(data.summaries);

  if (totals.total === 0) {
    return (
      <Card data-testid="deadline-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <CalendarClock className="size-4" />
            Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs italic text-slate-500">
            No deadlines this month.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card data-testid="deadline-widget">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <CalendarClock className="size-4" />
          Deadlines
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-1 pt-0">
        {/* Compact list rows */}
        <div className="flex items-center justify-between rounded-md px-2 py-1.5">
          <span className="text-xs text-slate-600 dark:text-slate-400">
            Total
          </span>
          <span className="font-mono text-sm font-bold tabular-nums">
            {totals.total}
          </span>
        </div>
        <div className="flex items-center justify-between rounded-md px-2 py-1.5">
          <span className="text-xs text-teal-600 dark:text-teal-400">
            Filed
          </span>
          <span className="font-mono text-sm font-bold tabular-nums text-teal-600 dark:text-teal-400">
            {totals.filed}
          </span>
        </div>
        <div className="flex items-center justify-between rounded-md px-2 py-1.5">
          <span className="text-xs text-amber-600 dark:text-amber-400">
            Pending
          </span>
          <span className="font-mono text-sm font-bold tabular-nums text-amber-600 dark:text-amber-400">
            {totals.pending}
          </span>
        </div>
        {totals.overdue > 0 && (
          <div className="flex items-center justify-between rounded-md bg-red-50/50 px-2 py-1.5 dark:bg-red-950/20">
            <span className="text-xs font-medium text-red-600 dark:text-red-400">
              Overdue
            </span>
            <span className="font-mono text-sm font-bold tabular-nums text-red-600 dark:text-red-400">
              {totals.overdue}
            </span>
          </div>
        )}
        {totals.overdue === 0 && (
          <div className="flex items-center justify-between rounded-md px-2 py-1.5">
            <span className="text-xs text-slate-400 dark:text-slate-500">
              Overdue
            </span>
            <span className="font-mono text-sm font-bold tabular-nums text-slate-300 dark:text-slate-600">
              0
            </span>
          </div>
        )}
      </CardContent>
      <CardFooter className="pt-0">
        <Button variant="ghost" size="sm" className="h-7 text-xs text-slate-500" asChild>
          <Link href={`/org/${orgSlug}/deadlines`}>
            View All &rarr;
          </Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
