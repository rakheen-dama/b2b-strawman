"use client";

import { useCallback, useRef } from "react";
import { cn } from "@/lib/utils";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

interface TimeCellProps {
  taskId: string;
  dayIndex: number; // 0=Mon, 1=Tue ... 6=Sun
  value: number; // decimal hours, 0 = empty
  error?: string;
  onChange: (taskId: string, dayIndex: number, hours: number) => void;
}

export function TimeCell({
  taskId,
  dayIndex,
  value,
  error,
  onChange,
}: TimeCellProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleBlur = useCallback(
    (e: React.FocusEvent<HTMLInputElement>) => {
      const parsed = parseFloat(e.target.value);
      const hours = isNaN(parsed) || parsed < 0 ? 0 : Math.min(parsed, 24);
      onChange(taskId, dayIndex, hours);
      // Normalize the displayed value after blur
      e.target.value = hours === 0 ? "" : String(hours);
    },
    [taskId, dayIndex, onChange],
  );

  // Use defaultValue with a key from the parent to reset on week navigation.
  // The parent renders <TimeCell key={cellKey} value={...} /> so when value
  // changes due to week navigation (component remount via key), defaultValue
  // is re-applied.
  const displayValue = value === 0 ? "" : String(value);

  const input = (
    <input
      ref={inputRef}
      type="number"
      min="0"
      max="24"
      step="0.25"
      defaultValue={displayValue}
      onBlur={handleBlur}
      aria-label={`Hours for day ${dayIndex + 1}`}
      className={cn(
        "w-14 rounded border px-1 py-0.5 text-center text-sm",
        "focus:outline-none focus:ring-1 focus:ring-slate-400",
        "dark:bg-slate-900 dark:text-slate-100",
        error
          ? "border-red-500 focus:ring-red-400"
          : "border-slate-200 dark:border-slate-700",
      )}
    />
  );

  if (error) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>{input}</TooltipTrigger>
          <TooltipContent>
            <p>{error}</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return input;
}
