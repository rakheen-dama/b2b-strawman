"use client";

import { cn } from "@/lib/utils";

const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

interface WeeklyRhythmStripProps {
  dailyHours: number[]; // length 7, Mon=0..Sun=6
  dailyCapacity: number; // hours per day (default 8)
  selectedDayIndex: number | null;
  onDaySelect: (index: number | null) => void;
}

export function WeeklyRhythmStrip({
  dailyHours,
  dailyCapacity,
  selectedDayIndex,
  onDaySelect,
}: WeeklyRhythmStripProps) {
  // Current day index (0=Mon, 6=Sun)
  const todayDayOfWeek = new Date().getDay(); // 0=Sun, 1=Mon..6=Sat
  const todayIndex = todayDayOfWeek === 0 ? 6 : todayDayOfWeek - 1;

  const totalHours = dailyHours.reduce((sum, h) => sum + h, 0);

  return (
    <div
      data-testid="weekly-rhythm-strip"
      className="flex items-center gap-2 rounded-lg border bg-card p-3"
    >
      <div className="flex h-12 flex-1 items-end gap-1">
        {DAY_LABELS.map((label, i) => {
          const hours = dailyHours[i] ?? 0;
          const filledPct =
            dailyCapacity > 0 ? Math.min(1, hours / dailyCapacity) : 0;
          const isToday = i === todayIndex;
          const isSelected = selectedDayIndex === i;

          return (
            <button
              key={i}
              data-testid={`rhythm-day-${i}`}
              aria-label={`${label}: ${hours.toFixed(1)} hours logged`}
              aria-pressed={isSelected}
              onClick={() => onDaySelect(isSelected ? null : i)}
              className={cn(
                "flex flex-1 cursor-pointer flex-col items-center gap-0.5 rounded pb-1 pt-1 hover:bg-slate-50",
                isSelected && "bg-teal-50",
                isToday && "rounded ring-1 ring-teal-500",
              )}
            >
              <div
                className="relative flex w-full flex-1 items-end justify-center"
                style={{ height: 32 }}
              >
                {/* Background (remaining capacity) */}
                <div
                  className="absolute inset-x-1 bottom-0 rounded-sm bg-slate-200"
                  style={{ height: 32 }}
                />
                {/* Filled (logged hours) */}
                {filledPct > 0 && (
                  <div
                    className="absolute inset-x-1 bottom-0 rounded-sm bg-teal-500"
                    style={{ height: `${filledPct * 32}px` }}
                  />
                )}
              </div>
              <span
                className={cn(
                  "text-[9px] font-medium uppercase",
                  isToday ? "text-teal-600" : "text-slate-400",
                )}
              >
                {label}
              </span>
            </button>
          );
        })}
      </div>
      {/* Weekly total */}
      <div className="shrink-0 border-l border-slate-200 pl-2 text-right">
        <p className="font-mono text-sm font-bold tabular-nums text-slate-900">
          {totalHours.toFixed(1)}h
        </p>
        <p className="text-[10px] text-slate-400">this week</p>
      </div>
    </div>
  );
}
