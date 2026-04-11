"use client";

import { cn } from "@/lib/utils";
import type { CalculatedDeadline } from "@/lib/types";

interface DeadlineCalendarViewProps {
  deadlines: CalculatedDeadline[];
  year: number;
  month: number; // 1-indexed
  onDayClick?: (date: string) => void; // ISO date "2026-03-15"
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export function DeadlineCalendarView({
  deadlines,
  year,
  month,
  onDayClick,
}: DeadlineCalendarViewProps) {
  // Group deadlines by day (within this month)
  const byDay = new Map<number, { filed: number; overdue: number; pending: number }>();
  for (const d of deadlines) {
    const [dYear, dMonth, dDayStr] = d.dueDate.split("-").map(Number);
    if (dYear !== year || dMonth !== month) continue;
    const day = dDayStr;
    const current = byDay.get(day) ?? { filed: 0, overdue: 0, pending: 0 };
    if (d.status === "filed") current.filed++;
    else if (d.status === "overdue") current.overdue++;
    else current.pending++;
    byDay.set(day, current);
  }

  const firstDayOfMonth = new Date(year, month - 1, 1).getDay(); // 0=Sun
  const daysInMonth = new Date(year, month, 0).getDate();
  const today = new Date();
  const isCurrentMonth = today.getFullYear() === year && today.getMonth() + 1 === month;
  const todayDate = today.getDate();

  return (
    <div>
      {/* Weekday headers */}
      <div className="grid grid-cols-7 border-b border-slate-200 dark:border-slate-700">
        {WEEKDAYS.map((day) => (
          <div
            key={day}
            className="py-2 text-center text-xs font-medium text-slate-500 dark:text-slate-400"
          >
            {day}
          </div>
        ))}
      </div>

      {/* Day grid */}
      <div className="grid grid-cols-7">
        {/* Leading empty cells */}
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div
            key={`empty-${i}`}
            className="min-h-[80px] border-r border-b border-slate-100 dark:border-slate-800"
          />
        ))}

        {/* Day cells */}
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1;
          const counts = byDay.get(day);
          const isToday = isCurrentMonth && day === todayDate;
          const total = counts ? counts.filed + counts.overdue + counts.pending : 0;
          const isoDate = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

          return (
            <button
              key={day}
              type="button"
              onClick={() => total > 0 && onDayClick?.(isoDate)}
              disabled={total === 0}
              className={cn(
                "min-h-[80px] border-r border-b border-slate-100 p-1.5 text-left transition-colors dark:border-slate-800",
                total > 0 && "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/50"
              )}
            >
              <span
                className={cn(
                  "inline-flex size-6 items-center justify-center rounded-full text-xs",
                  isToday
                    ? "bg-teal-600 font-semibold text-white"
                    : "text-slate-700 dark:text-slate-300"
                )}
              >
                {day}
              </span>
              {counts && (
                <div className="mt-1 space-y-0.5">
                  {counts.overdue > 0 && (
                    <div className="flex items-center gap-1">
                      <span className="size-2 shrink-0 rounded-full bg-red-500" />
                      <span className="text-[10px] leading-none text-red-600">
                        {counts.overdue}
                      </span>
                    </div>
                  )}
                  {counts.pending > 0 && (
                    <div className="flex items-center gap-1">
                      <span className="size-2 shrink-0 rounded-full bg-amber-400" />
                      <span className="text-[10px] leading-none text-amber-600">
                        {counts.pending}
                      </span>
                    </div>
                  )}
                  {counts.filed > 0 && (
                    <div className="flex items-center gap-1">
                      <span className="size-2 shrink-0 rounded-full bg-teal-500" />
                      <span className="text-[10px] leading-none text-teal-600">{counts.filed}</span>
                    </div>
                  )}
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
