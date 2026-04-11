"use client";

import { cn } from "@/lib/utils";
import type { CourtDate, CourtDateStatus } from "@/lib/types";

interface CourtCalendarViewProps {
  courtDates: CourtDate[];
  year: number;
  month: number; // 1-indexed
  onDayClick?: (date: string, courtDates: CourtDate[]) => void;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const STATUS_DOT: Record<CourtDateStatus, string> = {
  SCHEDULED: "bg-blue-500",
  POSTPONED: "bg-amber-400",
  HEARD: "bg-teal-500",
  CANCELLED: "bg-slate-400",
};

export function CourtCalendarView({ courtDates, year, month, onDayClick }: CourtCalendarViewProps) {
  // Group court dates by day
  const byDay = new Map<number, CourtDate[]>();
  for (const cd of courtDates) {
    const [dYear, dMonth, dDayStr] = cd.scheduledDate.split("-").map(Number);
    if (dYear !== year || dMonth !== month) continue;
    const day = dDayStr;
    const current = byDay.get(day) ?? [];
    current.push(cd);
    byDay.set(day, current);
  }

  const firstDayOfMonth = new Date(year, month - 1, 1).getDay(); // 0=Sun
  const daysInMonth = new Date(year, month, 0).getDate();
  const today = new Date();
  const isCurrentMonth = today.getFullYear() === year && today.getMonth() + 1 === month;
  const todayDate = today.getDate();

  return (
    <div data-testid="court-calendar-view">
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
          const dayCases = byDay.get(day);
          const isToday = isCurrentMonth && day === todayDate;
          const total = dayCases?.length ?? 0;
          const isoDate = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

          // Count by status
          const statusCounts: Partial<Record<CourtDateStatus, number>> = {};
          if (dayCases) {
            for (const cd of dayCases) {
              statusCounts[cd.status] = (statusCounts[cd.status] ?? 0) + 1;
            }
          }

          return (
            <button
              key={day}
              type="button"
              onClick={() => total > 0 && onDayClick?.(isoDate, dayCases ?? [])}
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
              {dayCases && (
                <div className="mt-1 space-y-0.5">
                  {(Object.entries(statusCounts) as [CourtDateStatus, number][]).map(
                    ([status, count]) => (
                      <div key={status} className="flex items-center gap-1">
                        <span className={cn("size-2 shrink-0 rounded-full", STATUS_DOT[status])} />
                        <span className="text-[10px] leading-none text-slate-600 dark:text-slate-400">
                          {count}
                        </span>
                      </div>
                    )
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
