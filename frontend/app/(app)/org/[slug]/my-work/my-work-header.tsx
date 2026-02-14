"use client";

import { useState } from "react";
import { DateRangeSelector } from "@/components/dashboard/date-range-selector";

interface MyWorkHeaderProps {
  from: string;
  to: string;
}

function parseDateString(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function MyWorkHeader({ from, to }: MyWorkHeaderProps) {
  const [dateRange, setDateRange] = useState({
    from: parseDateString(from),
    to: parseDateString(to),
  });

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          My Work
        </h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
          Your tasks and time tracking across all projects
        </p>
      </div>
      <DateRangeSelector value={dateRange} onChange={setDateRange} />
    </div>
  );
}
