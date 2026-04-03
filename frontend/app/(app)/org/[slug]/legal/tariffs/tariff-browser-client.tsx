"use client";

import { useState, useTransition, useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Copy, ChevronRight, ChevronDown } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { TariffItemBrowser } from "@/components/legal/tariff-item-browser";
import {
  fetchTariffSchedules,
  cloneSchedule,
} from "./actions";
import type { TariffSchedule } from "@/lib/types";

interface TariffBrowserClientProps {
  initialSchedules: TariffSchedule[];
  initialTotal: number;
  slug: string;
}

export function TariffBrowserClient({
  initialSchedules,
  initialTotal,
  slug,
}: TariffBrowserClientProps) {
  const [schedules, setSchedules] = useState(initialSchedules);
  const [total, setTotal] = useState(initialTotal);
  const [isPending, startTransition] = useTransition();
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const refetch = useCallback(() => {
    startTransition(async () => {
      try {
        const result = await fetchTariffSchedules();
        setSchedules(result ?? []);
        setTotal(result?.length ?? 0);
      } catch (err) {
        console.error("Failed to refetch tariff schedules:", err);
      }
    });
  }, []);

  async function handleClone(scheduleId: string) {
    const result = await cloneSchedule(slug, scheduleId);
    if (result.success) {
      toast.success("Schedule cloned successfully");
      refetch();
    } else {
      toast.error(result.error ?? "Failed to clone schedule");
    }
  }

  function toggleExpand(id: string) {
    setExpandedId((prev) => (prev === id ? null : id));
  }

  return (
    <div data-testid="tariff-browser" className="space-y-4">
      {/* Count */}
      <div className="text-sm text-slate-500 dark:text-slate-400">
        {total} schedule{total !== 1 ? "s" : ""}
      </div>

      {/* Schedule list */}
      <div
        className={cn(
          "overflow-x-auto",
          isPending && "opacity-50 transition-opacity",
        )}
      >
        {schedules.length === 0 ? (
          <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No tariff schedules found.
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {schedules.map((schedule) => (
              <div
                key={schedule.id}
                className="rounded-lg border border-slate-200 dark:border-slate-800"
              >
                {/* Schedule row */}
                <div
                  className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900/50"
                  onClick={() => toggleExpand(schedule.id)}
                  data-testid={`schedule-row-${schedule.id}`}
                >
                  <span className="text-slate-400">
                    {expandedId === schedule.id ? (
                      <ChevronDown className="size-4" />
                    ) : (
                      <ChevronRight className="size-4" />
                    )}
                  </span>

                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-900 dark:text-slate-100">
                        {schedule.name}
                      </span>
                      <Badge variant="neutral" className="text-xs">
                        {schedule.code}
                      </Badge>
                      {schedule.active && (
                        <Badge className="bg-teal-100 text-teal-700 text-xs dark:bg-teal-900 dark:text-teal-300">
                          Active
                        </Badge>
                      )}
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                      Effective from {schedule.effectiveFrom}
                      {schedule.effectiveTo && ` to ${schedule.effectiveTo}`}
                      {" \u00b7 "}
                      {schedule.itemCount} item{schedule.itemCount !== 1 ? "s" : ""}
                    </div>
                  </div>

                  <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleClone(schedule.id)}
                      title="Clone schedule"
                      data-testid={`clone-btn-${schedule.id}`}
                    >
                      <Copy className="mr-1 size-3.5" />
                      Clone
                    </Button>
                  </div>
                </div>

                {/* Expanded item browser */}
                {expandedId === schedule.id && (
                  <div className="border-t border-slate-200 px-4 py-3 dark:border-slate-800">
                    <TariffItemBrowser scheduleId={schedule.id} />
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
