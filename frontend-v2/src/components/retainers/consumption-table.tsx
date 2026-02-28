"use client";

import type { PeriodSummary } from "@/lib/api/retainers";
import { formatLocalDate } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface ConsumptionTableProps {
  periods: PeriodSummary[];
  className?: string;
}

export function ConsumptionTable({
  periods,
  className,
}: ConsumptionTableProps) {
  if (periods.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <p className="text-sm text-slate-500">
          No period data available yet.
        </p>
      </div>
    );
  }

  return (
    <div className={cn("rounded-lg border border-slate-200 overflow-hidden", className)}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Period</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Allocated</TableHead>
            <TableHead className="text-right">Consumed</TableHead>
            <TableHead className="text-right">Remaining</TableHead>
            <TableHead className="text-right">Rollover In</TableHead>
            <TableHead className="text-right">Rollover Out</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {periods.map((period) => {
            const pct =
              period.allocatedHours != null && period.allocatedHours > 0
                ? Math.round(
                    (period.consumedHours / period.allocatedHours) * 100,
                  )
                : 0;
            const isOverage = pct > 100;

            return (
              <TableRow key={period.id}>
                <TableCell>
                  <span className="text-sm text-slate-900">
                    {formatLocalDate(period.periodStart)} &mdash;{" "}
                    {formatLocalDate(period.periodEnd)}
                  </span>
                </TableCell>
                <TableCell>
                  <StatusBadge status={period.status} />
                </TableCell>
                <TableCell className="text-right font-mono text-sm tabular-nums">
                  {period.allocatedHours ?? "\u2014"}h
                </TableCell>
                <TableCell
                  className={cn(
                    "text-right font-mono text-sm tabular-nums",
                    isOverage && "text-red-600 font-semibold",
                  )}
                >
                  {period.consumedHours}h
                </TableCell>
                <TableCell className="text-right font-mono text-sm tabular-nums">
                  {period.remainingHours != null
                    ? `${period.remainingHours}h`
                    : "\u2014"}
                </TableCell>
                <TableCell className="text-right font-mono text-sm tabular-nums text-slate-500">
                  {period.rolloverHoursIn > 0
                    ? `+${period.rolloverHoursIn}h`
                    : "\u2014"}
                </TableCell>
                <TableCell className="text-right font-mono text-sm tabular-nums text-slate-500">
                  {period.rolloverHoursOut > 0
                    ? `${period.rolloverHoursOut}h`
                    : "\u2014"}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
