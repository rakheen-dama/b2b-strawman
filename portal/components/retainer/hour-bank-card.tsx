"use client";

import Link from "next/link";
import { AlertTriangle, Calendar, FileText } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  formatHours,
  type PortalRetainerSummary,
} from "@/lib/api/retainer";

interface HourBankCardProps {
  summary: PortalRetainerSummary;
  /** When present, the retainer title wraps in a link to the detail page. */
  href?: string;
}

/**
 * Renders the remaining-hours summary for a single retainer. The remaining
 * figure is displayed in `font-mono tabular-nums` so digits align across
 * cards; a consumed-percentage progress bar tints teal → amber → red as
 * consumption rises. When less than 20% of the allotted hours remain an
 * urgency banner appears and the remaining-hours figure turns red.
 */
export function HourBankCard({ summary, href }: HourBankCardProps) {
  const {
    name,
    periodType,
    hoursAllotted,
    hoursConsumed,
    hoursRemaining,
    periodStart,
    periodEnd,
    nextRenewalDate,
  } = summary;

  const consumedPct =
    hoursAllotted > 0 ? (hoursConsumed / hoursAllotted) * 100 : 0;
  const remainingPct = 100 - consumedPct;
  const isUrgent = hoursAllotted > 0 && remainingPct < 20;
  const barColor =
    consumedPct >= 80
      ? "bg-red-500"
      : consumedPct >= 60
        ? "bg-amber-500"
        : "bg-teal-500";

  const title = (
    <span className="flex items-center gap-2 text-sm font-medium text-slate-600">
      <FileText className="size-4" aria-hidden="true" />
      {name}
    </span>
  );

  return (
    <Card className="w-full" data-testid={`hour-bank-card-${summary.id}`}>
      <CardHeader>
        <CardTitle>
          {href ? (
            <Link
              href={href}
              className="text-teal-600 hover:text-teal-700 hover:underline"
            >
              {title}
            </Link>
          ) : (
            title
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <p
            className={cn(
              "font-mono text-3xl font-semibold tabular-nums sm:text-4xl",
              isUrgent ? "text-red-600" : "text-slate-900",
            )}
            aria-label="Hours remaining"
          >
            {formatHours(hoursRemaining)}
          </p>
          <p className="text-xs text-slate-500">
            of {formatHours(hoursAllotted)} allotted (
            {periodType.toLowerCase()})
          </p>
        </div>

        <div
          className="h-2 w-full overflow-hidden rounded-full bg-slate-100"
          role="progressbar"
          aria-valuenow={Math.round(consumedPct)}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Consumed percentage"
          data-testid="hour-bank-progress"
        >
          <div
            className={cn("h-full transition-all", barColor)}
            style={{ width: `${Math.min(100, Math.max(0, consumedPct))}%` }}
            data-testid="hour-bank-progress-fill"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
          <span>
            Period: {formatDate(periodStart)} – {formatDate(periodEnd)}
          </span>
          <span className="inline-flex items-center gap-1">
            <Calendar className="size-3.5" aria-hidden="true" />
            Renews {formatDate(nextRenewalDate)}
          </span>
        </div>

        {isUrgent && (
          <div
            className="flex items-center gap-1.5 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700"
            data-testid="hour-bank-urgency-banner"
          >
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            Less than 20% of retainer hours remaining
          </div>
        )}
      </CardContent>
    </Card>
  );
}
