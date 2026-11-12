"use client";

import { useState, useTransition, useCallback } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card } from "@b2mash/ui/card";
import { StackedMeter } from "@/components/charts/stacked-meter";
import { WIDGET_ELEVATION_CLASS } from "@/components/dashboard/widget-parts";
import { formatDuration } from "@/lib/format";
import { cn } from "@/lib/utils";
import { fetchMyTimeSummary } from "@/app/(app)/org/[slug]/my-work/actions";
import type { MyWorkTimeSummary } from "@/lib/types";

// --- Date helpers ---

/** Returns Sunday of the week starting at the given Monday. */
function getSunday(monday: Date): Date {
  const d = new Date(monday);
  d.setDate(monday.getDate() + 6);
  return d;
}

/** Format a date as YYYY-MM-DD for API calls. */
function toIsoDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

/** Format the week range label, e.g. "Feb 10 - Feb 16, 2026". */
function formatWeekLabel(monday: Date, sunday: Date): string {
  const from = monday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
  const to = sunday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  return `${from} \u2013 ${to}`;
}

// --- Component ---

interface WeeklyTimeSummaryProps {
  initialSummary: MyWorkTimeSummary | null;
  initialFrom: string;
}

export function WeeklyTimeSummary({ initialSummary, initialFrom }: WeeklyTimeSummaryProps) {
  const [summary, setSummary] = useState(initialSummary);
  const [weekStart, setWeekStart] = useState(() => {
    const [y, m, d] = initialFrom.split("-").map(Number);
    return new Date(y, m - 1, d);
  });
  const [isPending, startTransition] = useTransition();

  const weekEnd = getSunday(weekStart);
  const weekLabel = formatWeekLabel(weekStart, weekEnd);

  const navigateWeek = useCallback(
    (direction: -1 | 1) => {
      const newMonday = new Date(weekStart);
      newMonday.setDate(weekStart.getDate() + direction * 7);
      setWeekStart(newMonday);

      const newSunday = getSunday(newMonday);
      const from = toIsoDate(newMonday);
      const to = toIsoDate(newSunday);

      startTransition(async () => {
        const result = await fetchMyTimeSummary(from, to);
        setSummary(result);
      });
    },
    [weekStart]
  );

  const isEmpty = !summary || summary.totalMinutes === 0;

  return (
    <Card className={cn("gap-0 p-5", WIDGET_ELEVATION_CLASS)}>
      {/* Week Navigation Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-foreground font-semibold">This Week</h2>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            onClick={() => navigateWeek(-1)}
            disabled={isPending}
            aria-label="Previous week"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="text-muted-foreground min-w-[140px] text-center text-xs">
            {isPending ? "Loading..." : weekLabel}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            onClick={() => navigateWeek(1)}
            disabled={isPending}
            aria-label="Next week"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      {isEmpty ? (
        <p className="text-muted-foreground mt-4 text-sm">No time tracked this week</p>
      ) : (
        <WeekTotals summary={summary} />
      )}
    </Card>
  );
}

/**
 * The meter's parts in draw order. StackedMeter's categorical palette gives part i the colour
 * --chart-(i+1), so each part carries that slot's swatch class — the legend reads it from here.
 */
const WEEK_PARTS = {
  billable: { label: "Billable", swatch: "bg-chart-1" },
  nonBillable: { label: "Non-billable", swatch: "bg-chart-2" },
} as const;

/**
 * The week's figures. Billable vs non-billable is one whole split into two named parts, so it is
 * a categorical StackedMeter (ADR-370): Billable = slot 1, Non-billable = slot 2 in a fixed order
 * so a zero part keeps its colour. The two figure tiles are its legend (swatch + label, never
 * colour alone); the meter's sr summary states the same formatDuration text the tiles show.
 */
function WeekTotals({ summary }: { summary: MyWorkTimeSummary }) {
  const minutes: Record<keyof typeof WEEK_PARTS, number> = {
    billable: summary.billableMinutes,
    nonBillable: summary.nonBillableMinutes,
  };
  return (
    <div className="mt-4 space-y-4">
      {/* Stat Cards: Total, Billable, Non-billable */}
      <div className="space-y-3">
        <div className="bg-muted/50 rounded-md px-4 py-3">
          <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Total</p>
          <p className="font-display text-foreground text-2xl">
            {formatDuration(summary.totalMinutes)}
          </p>
        </div>

        <StackedMeter
          palette="categorical"
          label="Billable vs non-billable time"
          segments={(Object.keys(WEEK_PARTS) as Array<keyof typeof WEEK_PARTS>).map((key) => ({
            key,
            label: WEEK_PARTS[key].label,
            value: minutes[key],
            valueText: formatDuration(minutes[key]),
          }))}
        />

        <div className="grid grid-cols-2 gap-3">
          <div className="bg-muted/50 rounded-md px-4 py-3">
            <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium tracking-wide uppercase">
              <span
                aria-hidden="true"
                className={cn(WEEK_PARTS.billable.swatch, "inline-block size-2 rounded-full")}
              />
              {WEEK_PARTS.billable.label}
            </p>
            <p className="font-display text-foreground text-xl">
              {formatDuration(summary.billableMinutes)}
            </p>
          </div>
          <div className="bg-muted/50 rounded-md px-4 py-3">
            <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium tracking-wide uppercase">
              <span
                aria-hidden="true"
                className={cn(WEEK_PARTS.nonBillable.swatch, "inline-block size-2 rounded-full")}
              />
              {WEEK_PARTS.nonBillable.label}
            </p>
            <p className="font-display text-muted-foreground text-xl">
              {formatDuration(summary.nonBillableMinutes)}
            </p>
          </div>
        </div>
      </div>

      {/* By Matter Breakdown */}
      {summary.byMatter.length > 0 && (
        <div className="border-border border-t pt-4">
          <h3 className="text-muted-foreground mb-3 text-sm font-medium">By Matter</h3>
          <div className="space-y-2">
            {summary.byMatter.map((project) => (
              <div key={project.matterId} className="flex items-center justify-between text-sm">
                <span className="text-foreground truncate">{project.matterName}</span>
                <div className="flex shrink-0 items-center gap-3">
                  {/* Was colour-only (green); now the Billable swatch + an sr label. */}
                  <span className="text-muted-foreground flex items-center gap-1.5 text-xs">
                    <span
                      aria-hidden="true"
                      className={cn(
                        WEEK_PARTS.billable.swatch,
                        "inline-block size-1.5 rounded-full"
                      )}
                    />
                    <span className="sr-only">Billable: </span>
                    {formatDuration(project.billableMinutes)}
                  </span>
                  <span className="text-foreground font-medium">
                    {formatDuration(project.totalMinutes)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
