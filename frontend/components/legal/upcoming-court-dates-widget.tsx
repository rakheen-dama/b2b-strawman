"use client";

import Link from "next/link";
import useSWR from "swr";
import { Gavel } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { fetchUpcoming } from "@/app/(app)/org/[slug]/court-calendar/actions";
import type { CourtDate } from "@/lib/types";

interface UpcomingCourtDatesWidgetProps {
  orgSlug: string;
}

const REFRESH_INTERVAL_MS = 300_000; // 5 minutes

function daysUntil(dateStr: string): number {
  const now = new Date();
  const target = new Date(dateStr + "T00:00:00");
  const diffMs = target.getTime() - now.getTime();
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
}

function urgencyClass(daysRemaining: number): string {
  if (daysRemaining < 3) return "text-red-600 dark:text-red-400";
  if (daysRemaining < 7) return "text-amber-600 dark:text-amber-400";
  return "text-slate-600 dark:text-slate-400";
}

function dateTypeLabel(type: string): string {
  return type
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join("-");
}

export function UpcomingCourtDatesWidget({ orgSlug }: UpcomingCourtDatesWidgetProps) {
  const { data, error, isLoading } = useSWR(
    `upcoming-court-dates-${orgSlug}`,
    () => fetchUpcoming(),
    {
      refreshInterval: REFRESH_INTERVAL_MS,
      dedupingInterval: 2000,
      revalidateOnFocus: true,
    }
  );

  if (isLoading) {
    return (
      <Card data-testid="upcoming-court-dates-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <Gavel className="size-4" />
            Upcoming Court Dates
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-slate-500 italic">Loading&hellip;</p>
        </CardContent>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card data-testid="upcoming-court-dates-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <Gavel className="size-4" />
            Upcoming Court Dates
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-slate-500 italic">Unable to load court dates.</p>
        </CardContent>
      </Card>
    );
  }

  const courtDates = data.courtDates.slice(0, 5);

  if (courtDates.length === 0) {
    return (
      <Card data-testid="upcoming-court-dates-widget">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <Gavel className="size-4" />
            Upcoming Court Dates
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-slate-500 italic">No upcoming court dates.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card data-testid="upcoming-court-dates-widget">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Gavel className="size-4" />
          Upcoming Court Dates
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-1 pt-0">
        {courtDates.map((cd: CourtDate) => {
          const days = daysUntil(cd.scheduledDate);
          return (
            <div
              key={cd.id}
              className={cn(
                "flex items-center justify-between rounded-md px-2 py-1.5",
                days < 3 && "bg-red-50/50 dark:bg-red-950/20"
              )}
            >
              <div className="flex items-center gap-2 overflow-hidden">
                <span className={cn("shrink-0 font-mono text-xs tabular-nums", urgencyClass(days))}>
                  {cd.scheduledDate}
                </span>
                <Badge variant="outline" className="shrink-0 text-[10px]">
                  {dateTypeLabel(cd.dateType)}
                </Badge>
                <span className="truncate text-xs text-slate-700 dark:text-slate-300">
                  {cd.projectName}
                </span>
              </div>
              <span className="ml-2 shrink-0 text-[10px] text-slate-500 dark:text-slate-400">
                {cd.courtName}
              </span>
            </div>
          );
        })}
      </CardContent>
      <CardFooter className="pt-0">
        <Button variant="ghost" size="sm" className="h-7 text-xs text-slate-500" asChild>
          <Link href={`/org/${orgSlug}/court-calendar`}>View All &rarr;</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
