"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { CalendarItem } from "./calendar-actions";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

interface CalendarMonthViewProps {
  items: CalendarItem[];
  year: number;
  month: number; // 1-indexed
  onNavigate: (year: number, month: number) => void;
  isPending: boolean;
}

function getStatusVariant(
  status: string
): "neutral" | "warning" | "success" | "secondary" {
  switch (status) {
    case "OPEN":
      return "neutral";
    case "IN_PROGRESS":
      return "warning";
    case "DONE":
      return "success";
    case "CANCELLED":
    case "ARCHIVED":
      return "secondary";
    case "ACTIVE":
      return "success";
    default:
      return "neutral";
  }
}

export function CalendarMonthView({
  items,
  year,
  month,
  onNavigate,
  isPending,
}: CalendarMonthViewProps) {
  const [openDay, setOpenDay] = useState<number | null>(null);

  // Build a map of day -> items
  const itemsByDay = new Map<number, CalendarItem[]>();
  for (const item of items) {
    const [, , dayStr] = item.dueDate.split("-");
    const day = parseInt(dayStr, 10);
    const [itemYear, itemMonth] = item.dueDate.split("-").map(Number);
    if (itemYear === year && itemMonth === month) {
      if (!itemsByDay.has(day)) {
        itemsByDay.set(day, []);
      }
      itemsByDay.get(day)!.push(item);
    }
  }

  // Calculate grid layout
  const firstDayOfMonth = new Date(year, month - 1, 1).getDay(); // 0=Sun
  const daysInMonth = new Date(year, month, 0).getDate();

  const handlePrev = () => {
    if (month === 1) {
      onNavigate(year - 1, 12);
    } else {
      onNavigate(year, month - 1);
    }
  };

  const handleNext = () => {
    if (month === 12) {
      onNavigate(year + 1, 1);
    } else {
      onNavigate(year, month + 1);
    }
  };

  const today = new Date();
  const isCurrentMonth =
    today.getFullYear() === year && today.getMonth() + 1 === month;
  const todayDate = today.getDate();

  return (
    <div
      className={cn(
        "transition-opacity duration-200",
        isPending && "opacity-50"
      )}
    >
      {/* Month Header with Navigation */}
      <div className="mb-4 flex items-center justify-between">
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          onClick={handlePrev}
          disabled={isPending}
          aria-label="Previous month"
        >
          <ChevronLeft className="size-4" />
        </Button>
        <h2 className="font-display text-lg font-semibold text-slate-900 dark:text-slate-100">
          {MONTH_NAMES[month - 1]} {year}
        </h2>
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          onClick={handleNext}
          disabled={isPending}
          aria-label="Next month"
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {/* Weekday Headers */}
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

      {/* Day Grid */}
      <div className="grid grid-cols-7">
        {/* Leading empty cells */}
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div
            key={`empty-${i}`}
            className="min-h-[80px] border-b border-r border-slate-100 dark:border-slate-800"
          />
        ))}

        {/* Day cells */}
        {Array.from({ length: daysInMonth }).map((_, i) => {
          const day = i + 1;
          const dayItems = itemsByDay.get(day) || [];
          const isToday = isCurrentMonth && day === todayDate;
          const maxDots = 3;
          const extraCount = Math.max(0, dayItems.length - maxDots);

          return (
            <Popover
              key={day}
              open={openDay === day}
              onOpenChange={(open) => setOpenDay(open ? day : null)}
            >
              <PopoverTrigger asChild>
                <button
                  type="button"
                  className={cn(
                    "min-h-[80px] border-b border-r border-slate-100 p-1.5 text-left transition-colors hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/50",
                    dayItems.length > 0 && "cursor-pointer"
                  )}
                  disabled={dayItems.length === 0}
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
                  {dayItems.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {dayItems.slice(0, maxDots).map((item) => (
                        <span
                          key={item.id}
                          className={cn(
                            "size-2 rounded-full",
                            item.itemType === "PROJECT"
                              ? "bg-teal-500"
                              : "bg-slate-400 dark:bg-slate-500"
                          )}
                          title={item.name}
                        />
                      ))}
                      {extraCount > 0 && (
                        <span className="text-[10px] leading-none text-slate-500">
                          +{extraCount}
                        </span>
                      )}
                    </div>
                  )}
                </button>
              </PopoverTrigger>
              {dayItems.length > 0 && (
                <PopoverContent className="w-[280px] p-0" align="start">
                  <div className="border-b border-slate-100 px-3 py-2 dark:border-slate-800">
                    <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                      {MONTH_NAMES[month - 1]} {day}
                    </p>
                  </div>
                  <div className="max-h-[240px] overflow-y-auto p-1">
                    {dayItems.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-start gap-2 rounded-md px-2 py-1.5"
                      >
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                            {item.name}
                          </p>
                          <div className="mt-0.5 flex items-center gap-1.5">
                            <Badge
                              variant={
                                item.itemType === "TASK"
                                  ? "neutral"
                                  : "default"
                              }
                              className="text-[10px]"
                            >
                              {item.itemType}
                            </Badge>
                            <Badge
                              variant={getStatusVariant(item.status)}
                              className="text-[10px]"
                            >
                              {item.status}
                            </Badge>
                          </div>
                          <p className="mt-0.5 truncate text-xs text-slate-500">
                            {item.projectName}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </PopoverContent>
              )}
            </Popover>
          );
        })}
      </div>
    </div>
  );
}
