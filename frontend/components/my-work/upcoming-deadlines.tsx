import { Clock, AlertTriangle } from "lucide-react";

import { cn } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import type { PersonalDeadline } from "@/lib/dashboard-types";

interface UpcomingDeadlinesProps {
  deadlines: PersonalDeadline[] | null;
}

function getDaysRemaining(dueDate: string): number {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const [year, month, day] = dueDate.split("-").map(Number);
  const due = new Date(year, month - 1, day);
  const diffMs = due.getTime() - today.getTime();
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
}

function DaysRemainingLabel({ daysRemaining }: { daysRemaining: number }) {
  if (daysRemaining < 0) {
    return (
      <span className="inline-flex items-center gap-1 text-xs font-medium text-red-600 dark:text-red-400">
        <AlertTriangle className="size-3" />
        Overdue
      </span>
    );
  }

  if (daysRemaining === 0) {
    return (
      <span className="text-xs font-medium text-amber-600 dark:text-amber-400">
        Due today
      </span>
    );
  }

  if (daysRemaining <= 2) {
    return (
      <span className="text-xs font-medium text-amber-600 dark:text-amber-400">
        {daysRemaining} {daysRemaining === 1 ? "day" : "days"}
      </span>
    );
  }

  return (
    <span className="text-xs text-slate-600 dark:text-slate-400">
      {daysRemaining} days
    </span>
  );
}

export function UpcomingDeadlines({ deadlines }: UpcomingDeadlinesProps) {
  if (!deadlines) {
    return (
      <Card>
        <div className="px-4 py-3">
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            Upcoming Deadlines
          </h3>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Unable to load deadlines. Please try again.
          </p>
        </div>
      </Card>
    );
  }

  if (deadlines.length === 0) {
    return (
      <Card>
        <div className="px-4 py-3">
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            Upcoming Deadlines
          </h3>
          <div className="mt-4 flex flex-col items-center gap-2 py-4">
            <Clock className="size-8 text-slate-300 dark:text-slate-600" />
            <p className="text-sm text-muted-foreground">
              No upcoming deadlines
            </p>
          </div>
        </div>
      </Card>
    );
  }

  // Show at most 5 deadlines, already sorted by backend
  const visible = deadlines.slice(0, 5);

  return (
    <Card>
      <div className="px-4 py-3">
        <h3 className="font-semibold text-slate-900 dark:text-slate-100">
          Upcoming Deadlines
        </h3>
        <ul className="mt-3 divide-y divide-slate-100 dark:divide-slate-800">
          {visible.map((deadline) => {
            const daysRemaining = getDaysRemaining(deadline.dueDate);

            return (
              <li
                key={deadline.taskId}
                className={cn(
                  "flex items-center justify-between gap-3 py-2.5",
                  daysRemaining < 0 &&
                    "bg-red-50/50 -mx-4 px-4 dark:bg-red-950/20"
                )}
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                    {deadline.taskName}
                  </p>
                  <div className="mt-0.5 flex items-center gap-2">
                    <Badge variant="member">{deadline.projectName}</Badge>
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {formatDate(deadline.dueDate)}
                    </span>
                  </div>
                </div>
                <DaysRemainingLabel daysRemaining={daysRemaining} />
              </li>
            );
          })}
        </ul>
      </div>
    </Card>
  );
}
