"use client";

import { useState } from "react";
import { WeeklyRhythmStrip } from "@/components/dashboard/weekly-rhythm-strip";
import type { MyWorkTimeEntryItem } from "@/lib/types";

function computeDailyHours(entries: MyWorkTimeEntryItem[], from: string): number[] {
  const daily = new Array<number>(7).fill(0);
  const [y, m, d] = from.split("-").map(Number);
  const monday = new Date(y, m - 1, d);
  entries.forEach((e) => {
    const [ey, em, ed] = e.date.split("-").map(Number);
    const entryDate = new Date(ey, em - 1, ed);
    const diffDays = Math.round((entryDate.getTime() - monday.getTime()) / (1000 * 60 * 60 * 24));
    if (diffDays >= 0 && diffDays < 7) {
      daily[diffDays] += e.durationMinutes / 60;
    }
  });
  return daily;
}

interface WeeklyRhythmStripClientProps {
  weekEntries: MyWorkTimeEntryItem[];
  from: string;
  dailyCapacity?: number;
}

export function WeeklyRhythmStripClient({
  weekEntries,
  from,
  dailyCapacity = 8,
}: WeeklyRhythmStripClientProps) {
  const [selectedDayIndex, setSelectedDayIndex] = useState<number | null>(null);
  const dailyHours = computeDailyHours(weekEntries, from);

  return (
    <WeeklyRhythmStrip
      dailyHours={dailyHours}
      dailyCapacity={dailyCapacity}
      selectedDayIndex={selectedDayIndex}
      onDaySelect={(i) => setSelectedDayIndex((prev) => (prev === i ? null : i))}
    />
  );
}
