"use client";

import { Plus } from "lucide-react";

import { cn } from "@/lib/utils";
import type { WeekCell } from "@/lib/api/capacity";

interface CapacityCellProps {
  cell: WeekCell;
  onClick?: () => void;
}

// Deterministic colour palette for project bars
const PROJECT_COLOURS = [
  "bg-teal-400",
  "bg-sky-400",
  "bg-violet-400",
  "bg-amber-400",
  "bg-rose-400",
  "bg-emerald-400",
  "bg-indigo-400",
  "bg-orange-400",
];

function getProjectColour(index: number): string {
  return PROJECT_COLOURS[index % PROJECT_COLOURS.length];
}

function getUtilizationClasses(pct: number): {
  bg: string;
  border: string;
  text: string;
} {
  if (pct > 100) {
    return {
      bg: "bg-red-100 dark:bg-red-950",
      border: "border-red-300 dark:border-red-700",
      text: "text-red-700 dark:text-red-300",
    };
  }
  if (pct >= 80) {
    return {
      bg: "bg-amber-100 dark:bg-amber-950",
      border: "border-amber-300 dark:border-amber-700",
      text: "text-amber-700 dark:text-amber-300",
    };
  }
  return {
    bg: "bg-emerald-100 dark:bg-emerald-950",
    border: "border-emerald-300 dark:border-emerald-700",
    text: "text-emerald-700 dark:text-emerald-300",
  };
}

export function CapacityCell({ cell, onClick }: CapacityCellProps) {
  const isEmpty =
    cell.allocations.length === 0 && cell.effectiveCapacity === 0;
  const hasAllocations = cell.allocations.length > 0;
  const colours = getUtilizationClasses(cell.utilizationPct);

  if (isEmpty) {
    return (
      <button
        onClick={onClick}
        className="flex h-16 w-full items-center justify-center rounded border border-dashed border-slate-300 text-slate-400 transition-colors hover:border-teal-400 hover:text-teal-500 dark:border-slate-600 dark:text-slate-500 dark:hover:border-teal-500 dark:hover:text-teal-400"
        aria-label="Add allocation"
      >
        <Plus className="h-4 w-4" />
      </button>
    );
  }

  return (
    <button
      onClick={onClick}
      className={cn(
        "flex h-16 w-full flex-col justify-between rounded border p-1.5 text-left transition-colors",
        colours.bg,
        colours.border,
        cell.leaveDays > 0 && "opacity-60",
      )}
      data-testid="capacity-cell"
      data-utilization={
        cell.utilizationPct > 100
          ? "over"
          : cell.utilizationPct >= 80
            ? "warning"
            : "normal"
      }
    >
      <span
        className={cn("font-mono text-xs tabular-nums font-medium", colours.text)}
      >
        {cell.totalAllocated}/{cell.effectiveCapacity}h
      </span>

      {hasAllocations && (
        <div className="flex gap-0.5">
          {cell.allocations.map((slot, i) => {
            const widthPct =
              cell.effectiveCapacity > 0
                ? Math.min((slot.hours / cell.effectiveCapacity) * 100, 100)
                : 0;
            return (
              <div
                key={slot.projectId}
                className={cn(
                  "h-1.5 rounded-full",
                  getProjectColour(i),
                )}
                style={{ width: `${Math.max(widthPct, 8)}%` }}
                title={`${slot.projectName}: ${slot.hours}h`}
              />
            );
          })}
        </div>
      )}

      {cell.leaveDays > 0 && (
        <span className="text-[10px] text-slate-500 dark:text-slate-400">
          {cell.leaveDays}d leave
        </span>
      )}
    </button>
  );
}
