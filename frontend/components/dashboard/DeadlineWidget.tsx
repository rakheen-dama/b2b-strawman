"use client";

import Link from "next/link";
import useSWR from "swr";
import { CalendarClock } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
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
    "deadline-summary-widget",
    () => fetchDeadlineSummary(from, to),
    {
      refreshInterval: REFRESH_INTERVAL_MS,
      dedupingInterval: 2000,
      revalidateOnFocus: true,
    },
  );

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="size-4" />
            Upcoming Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm italic text-muted-foreground">
            Loading deadline data&hellip;
          </p>
        </CardContent>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="size-4" />
            Upcoming Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm italic text-muted-foreground">
            Unable to load deadline data.
          </p>
        </CardContent>
      </Card>
    );
  }

  const totals = aggregateSummaries(data.summaries);

  if (totals.total === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClock className="size-4" />
            Upcoming Deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm italic text-muted-foreground">
            No deadlines this month.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <CalendarClock className="size-4" />
          Upcoming Deadlines
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-baseline gap-4">
          <div className="text-center">
            <p className="font-mono text-2xl font-semibold tabular-nums text-slate-950 dark:text-slate-50">
              {totals.total}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">Total</p>
          </div>
          <div className="text-center">
            <p className="font-mono text-2xl font-semibold tabular-nums text-teal-600 dark:text-teal-400">
              {totals.filed}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">Filed</p>
          </div>
          <div className="text-center">
            <p className="font-mono text-2xl font-semibold tabular-nums text-amber-600 dark:text-amber-400">
              {totals.pending}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Pending
            </p>
          </div>
          <div className="text-center">
            <p
              className={`font-mono text-2xl font-semibold tabular-nums ${
                totals.overdue > 0
                  ? "text-red-600 dark:text-red-400"
                  : "text-slate-300 dark:text-slate-600"
              }`}
            >
              {totals.overdue}
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Overdue
            </p>
          </div>
        </div>
      </CardContent>
      <div className="border-t border-slate-200 px-6 py-3 dark:border-slate-700">
        <Link
          href={`/org/${orgSlug}/deadlines`}
          className="text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
        >
          View All &rarr;
        </Link>
      </div>
    </Card>
  );
}
