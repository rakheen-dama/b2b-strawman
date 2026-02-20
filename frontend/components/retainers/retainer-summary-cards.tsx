import { FileText, Clock, AlertTriangle } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface RetainerSummaryCardsProps {
  activeCount: number;
  readyToCloseCount: number;
  totalOverageHours: number;
}

export function RetainerSummaryCards({
  activeCount,
  readyToCloseCount,
  totalOverageHours,
}: RetainerSummaryCardsProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Active Retainers
          </CardTitle>
          <FileText className="size-4 text-slate-400 dark:text-slate-600" />
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold tabular-nums text-slate-900 dark:text-slate-100">
            {activeCount}
          </p>
        </CardContent>
      </Card>

      <Card
        className={cn(
          readyToCloseCount > 0 &&
            "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950",
        )}
      >
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Periods Ready to Close
          </CardTitle>
          <Clock
            className={cn(
              "size-4",
              readyToCloseCount > 0
                ? "text-amber-500"
                : "text-slate-400 dark:text-slate-600",
            )}
          />
        </CardHeader>
        <CardContent>
          <p
            className={cn(
              "font-mono text-2xl font-semibold tabular-nums",
              readyToCloseCount > 0
                ? "text-amber-600 dark:text-amber-400"
                : "text-slate-900 dark:text-slate-100",
            )}
          >
            {readyToCloseCount}
          </p>
        </CardContent>
      </Card>

      <Card
        className={cn(
          totalOverageHours > 0 &&
            "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950",
        )}
      >
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Total Overage Hours
          </CardTitle>
          <AlertTriangle
            className={cn(
              "size-4",
              totalOverageHours > 0
                ? "text-amber-500"
                : "text-slate-400 dark:text-slate-600",
            )}
          />
        </CardHeader>
        <CardContent>
          <p
            className={cn(
              "font-mono text-2xl font-semibold tabular-nums",
              totalOverageHours > 0
                ? "text-amber-600 dark:text-amber-400"
                : "text-slate-900 dark:text-slate-100",
            )}
          >
            {totalOverageHours.toFixed(1)} hrs
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
