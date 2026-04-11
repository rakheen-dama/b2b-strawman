import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { formatDuration } from "@/lib/format";
import type { MyWorkTaskItem, MyWorkTimeEntryItem } from "@/lib/types";
import type { PersonalDeadline } from "@/lib/dashboard-types";

interface TodaysAgendaProps {
  tasks: MyWorkTaskItem[];
  todayEntries: MyWorkTimeEntryItem[];
  upcomingDeadlines: PersonalDeadline[];
  weeklyCapacityHours?: number;
}

function getDaysRemaining(dueDate: string): number {
  const now = new Date();
  const todayDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const [year, month, day] = dueDate.split("-").map(Number);
  const due = new Date(year, month - 1, day);
  return Math.ceil((due.getTime() - todayDate.getTime()) / (1000 * 60 * 60 * 24));
}

export function TodaysAgenda({
  tasks,
  todayEntries,
  upcomingDeadlines,
  weeklyCapacityHours = 40,
}: TodaysAgendaProps) {
  const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD"
  const dailyTargetMinutes = (weeklyCapacityHours / 5) * 60; // e.g. 480 min = 8h

  // Filter tasks to today/overdue
  const urgentTasks = tasks
    .filter((t) => {
      if (!t.dueDate) return false;
      return t.dueDate <= today;
    })
    .slice(0, 5);

  // Today's logged minutes
  const loggedMinutes = todayEntries.reduce((sum, e) => sum + e.durationMinutes, 0);
  const progressPct = Math.min(100, (loggedMinutes / dailyTargetMinutes) * 100);

  // Next deadline — first entry (already sorted by backend)
  const nextDeadline = upcomingDeadlines[0] ?? null;

  return (
    <div className="bg-card rounded-lg border p-4">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Section 1: Today's Tasks */}
        <div data-testid="todays-tasks">
          <p className="mb-2 text-[11px] font-medium tracking-wider text-slate-500 uppercase">
            Today&apos;s Tasks
          </p>
          {urgentTasks.length === 0 ? (
            <p className="text-sm text-slate-400">No tasks due today</p>
          ) : (
            <div className="space-y-1">
              {urgentTasks.map((task) => (
                <div key={task.id} className="flex h-9 items-center gap-2 text-sm">
                  <span className="flex-1 truncate font-medium text-slate-900">{task.title}</span>
                  <Badge variant="member" className="shrink-0 text-[10px]">
                    {task.projectName}
                  </Badge>
                  {task.totalTimeMinutes > 0 && (
                    <span className="shrink-0 font-mono text-xs text-slate-500 tabular-nums">
                      {formatDuration(task.totalTimeMinutes)}
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Section 2: Time Logged Today */}
        <div data-testid="time-progress-today">
          <p className="mb-2 text-[11px] font-medium tracking-wider text-slate-500 uppercase">
            Time Today
          </p>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="font-mono font-medium text-slate-900 tabular-nums">
                {formatDuration(loggedMinutes)}
              </span>
              <span className="font-mono text-xs text-slate-400 tabular-nums">
                / {formatDuration(dailyTargetMinutes)}
              </span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200">
              <div
                className="h-full rounded-full bg-teal-500 transition-all"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          </div>
        </div>

        {/* Section 3: Next Deadline */}
        <div data-testid="next-deadline">
          <p className="mb-2 text-[11px] font-medium tracking-wider text-slate-500 uppercase">
            Next Deadline
          </p>
          {!nextDeadline ? (
            <p className="text-sm text-slate-400">No upcoming deadlines</p>
          ) : (
            <div>
              <p className="truncate text-sm font-medium text-slate-900">{nextDeadline.taskName}</p>
              <p className="truncate text-xs text-slate-500">{nextDeadline.projectName}</p>
              {(() => {
                const days = getDaysRemaining(nextDeadline.dueDate);
                const colorClass =
                  days < 0 ? "text-red-600" : days <= 2 ? "text-amber-600" : "text-slate-600";
                return (
                  <span className={cn("text-xs font-medium", colorClass)}>
                    {days < 0
                      ? `${Math.abs(days)}d overdue`
                      : days === 0
                        ? "Due today"
                        : `${days}d remaining`}
                  </span>
                );
              })()}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
