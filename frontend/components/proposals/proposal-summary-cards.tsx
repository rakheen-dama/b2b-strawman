import { FileText, Clock, CheckCircle, TrendingUp } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TerminologyText } from "@/components/terminology-text";
import { cn } from "@/lib/utils";
import type { ProposalSummaryDto } from "@/lib/types/proposal";

interface ProposalSummaryCardsProps {
  summary: ProposalSummaryDto;
}

export function ProposalSummaryCards({ summary }: ProposalSummaryCardsProps) {
  const pendingCount = summary.byStatus.SENT ?? 0;
  const acceptedCount = summary.byStatus.ACCEPTED ?? 0;
  const conversionDisplay =
    summary.total > 0 ? `${(summary.conversionRate * 100).toFixed(1)}%` : "\u2014";

  return (
    <div className="grid gap-4 sm:grid-cols-4">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            <TerminologyText template="Total {Proposals}" />
          </CardTitle>
          <FileText className="size-4 text-slate-400 dark:text-slate-600" />
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums dark:text-slate-100">
            {summary.total}
          </p>
        </CardContent>
      </Card>

      <Card
        className={cn(
          pendingCount > 0 && "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950"
        )}
      >
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Pending
          </CardTitle>
          <Clock
            className={cn(
              "size-4",
              pendingCount > 0 ? "text-amber-500" : "text-slate-400 dark:text-slate-600"
            )}
          />
        </CardHeader>
        <CardContent>
          <p
            className={cn(
              "font-mono text-2xl font-semibold tabular-nums",
              pendingCount > 0
                ? "text-amber-600 dark:text-amber-400"
                : "text-slate-900 dark:text-slate-100"
            )}
          >
            {pendingCount}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Accepted
          </CardTitle>
          <CheckCircle className="size-4 text-slate-400 dark:text-slate-600" />
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums dark:text-slate-100">
            {acceptedCount}
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Conversion Rate
          </CardTitle>
          <TrendingUp className="size-4 text-slate-400 dark:text-slate-600" />
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums dark:text-slate-100">
            {conversionDisplay}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
