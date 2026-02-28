import { Clock } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDuration } from "@/lib/format";
import type { MyWorkTimeEntryItem } from "@/lib/types";

interface TodayTimeEntriesProps {
  entries: MyWorkTimeEntryItem[];
}

export function TodayTimeEntries({ entries }: TodayTimeEntriesProps) {
  const totalMinutes = entries.reduce(
    (sum, entry) => sum + entry.durationMinutes,
    0,
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-slate-700">
          Today
        </CardTitle>
        {totalMinutes > 0 && (
          <span className="font-mono text-sm font-medium tabular-nums text-slate-900 dark:text-slate-100">
            {formatDuration(totalMinutes)}
          </span>
        )}
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <Clock className="size-8 text-slate-300 dark:text-slate-700" />
            <p className="text-sm text-slate-500">No time logged today</p>
          </div>
        ) : (
          <div className="space-y-2">
            {entries.map((entry) => (
              <div
                key={entry.id}
                className="flex items-start justify-between gap-3 rounded-md border border-slate-100 p-3 dark:border-slate-800"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                    {entry.taskTitle}
                  </p>
                  <p className="mt-0.5 truncate text-xs text-slate-500">
                    {entry.projectName}
                    {entry.description && ` \u2014 ${entry.description}`}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={entry.billable ? "success" : "neutral"}>
                    {entry.billable ? "Billable" : "Non-billable"}
                  </Badge>
                  <span className="font-mono text-sm font-medium tabular-nums text-slate-900 dark:text-slate-100">
                    {formatDuration(entry.durationMinutes)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
