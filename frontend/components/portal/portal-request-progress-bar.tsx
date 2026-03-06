"use client";

import { Progress } from "@/components/ui/progress";

interface PortalRequestProgressBarProps {
  totalItems: number;
  acceptedItems: number;
}

export function PortalRequestProgressBar({
  totalItems,
  acceptedItems,
}: PortalRequestProgressBarProps) {
  const percentage =
    totalItems > 0 ? Math.round((acceptedItems / totalItems) * 100) : 0;

  return (
    <div className="flex items-center gap-3">
      <Progress value={percentage} className="h-2 w-24" />
      <span className="text-xs text-slate-500 dark:text-slate-400">
        {acceptedItems}/{totalItems} accepted
      </span>
    </div>
  );
}
