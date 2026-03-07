"use client";

import { useRouter, usePathname } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { getCurrentMonday, formatDate, addWeeks } from "@/lib/date-utils";

interface WeekRangeSelectorProps {
  weekStart: string;
  weekCount: number;
}

function parseDate(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d);
}

const WEEK_OPTIONS = [4, 8, 12] as const;

export function WeekRangeSelector({
  weekStart,
  weekCount,
}: WeekRangeSelectorProps) {
  const router = useRouter();
  const pathname = usePathname();

  function navigate(newStart: string, newCount: number) {
    const params = new URLSearchParams();
    params.set("weekStart", newStart);
    params.set(
      "weekEnd",
      formatDate(addWeeks(parseDate(newStart), newCount)),
    );
    router.push(`${pathname}?${params.toString()}`);
  }

  function handlePrev() {
    const newStart = addWeeks(parseDate(weekStart), -weekCount);
    navigate(formatDate(newStart), weekCount);
  }

  function handleNext() {
    const newStart = addWeeks(parseDate(weekStart), weekCount);
    navigate(formatDate(newStart), weekCount);
  }

  function handleThisWeek() {
    const monday = getCurrentMonday();
    navigate(formatDate(monday), weekCount);
  }

  function handleWeekCountChange(count: number) {
    navigate(weekStart, count);
  }

  return (
    <div className="flex items-center gap-2">
      <div className="flex items-center gap-1 rounded-full border border-slate-200 bg-white p-0.5 dark:border-slate-700 dark:bg-slate-800">
        {WEEK_OPTIONS.map((count) => (
          <button
            key={count}
            onClick={() => handleWeekCountChange(count)}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              weekCount === count
                ? "bg-teal-500 text-white"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white",
            )}
          >
            {count}w
          </button>
        ))}
      </div>

      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="sm"
          onClick={handlePrev}
          aria-label="Previous weeks"
          className="h-8 w-8 rounded-full p-0"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={handleThisWeek}
          className="h-8 rounded-full px-3 text-sm"
        >
          This Week
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={handleNext}
          aria-label="Next weeks"
          className="h-8 w-8 rounded-full p-0"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
