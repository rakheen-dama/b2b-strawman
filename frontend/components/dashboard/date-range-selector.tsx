"use client";

import { useRouter, usePathname } from "next/navigation";
import { RefreshCw } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface DateRangeSelectorProps {
  value: { from: Date; to: Date };
  onChange: (range: { from: Date; to: Date }) => void;
  presets?: Array<{ label: string; from: Date; to: Date }>;
}

function formatDateParam(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function getThisWeekRange(): { from: Date; to: Date } {
  const now = new Date();
  const day = now.getDay(); // 0=Sun, 1=Mon, ...
  const diff = day === 0 ? -6 : 1 - day; // Monday
  const monday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() + diff
  );
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return { from: monday, to: sunday };
}

function getThisMonthRange(): { from: Date; to: Date } {
  const now = new Date();
  const from = new Date(now.getFullYear(), now.getMonth(), 1);
  const to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return { from, to };
}

function getLast30DaysRange(): { from: Date; to: Date } {
  const now = new Date();
  const to = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const from = new Date(to);
  from.setDate(to.getDate() - 30);
  return { from, to };
}

function getThisQuarterRange(): { from: Date; to: Date } {
  const now = new Date();
  const quarter = Math.floor(now.getMonth() / 3);
  const from = new Date(now.getFullYear(), quarter * 3, 1);
  const to = new Date(now.getFullYear(), quarter * 3 + 3, 0);
  return { from, to };
}

const DEFAULT_PRESETS: Array<{
  label: string;
  getRangeOrNull: (() => { from: Date; to: Date }) | null;
}> = [
  { label: "This Week", getRangeOrNull: getThisWeekRange },
  { label: "This Month", getRangeOrNull: getThisMonthRange },
  { label: "Last 30 Days", getRangeOrNull: getLast30DaysRange },
  { label: "This Quarter", getRangeOrNull: getThisQuarterRange },
  { label: "Custom", getRangeOrNull: null },
];

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

export function DateRangeSelector({
  value,
  onChange,
  presets,
}: DateRangeSelectorProps) {
  const router = useRouter();
  const pathname = usePathname();

  function handlePresetClick(range: { from: Date; to: Date }) {
    onChange(range);
    const params = new URLSearchParams();
    params.set("from", formatDateParam(range.from));
    params.set("to", formatDateParam(range.to));
    router.push(`${pathname}?${params.toString()}`);
  }

  function handleRefresh() {
    router.refresh();
  }

  // Determine active preset
  function isActivePreset(from: Date, to: Date): boolean {
    return isSameDay(value.from, from) && isSameDay(value.to, to);
  }

  // Use custom presets if provided, otherwise use defaults
  const presetItems = presets
    ? presets.map((p) => ({
        label: p.label,
        getRangeOrNull: (() => ({ from: p.from, to: p.to })) as
          | (() => { from: Date; to: Date })
          | null,
      }))
    : DEFAULT_PRESETS;

  return (
    <div className="flex items-center gap-1.5">
      {presetItems.map((preset) => {
        if (!preset.getRangeOrNull) {
          // "Custom" placeholder - no-op
          return (
            <Button
              key={preset.label}
              variant="ghost"
              size="sm"
              disabled
              className="text-muted-foreground"
            >
              {preset.label}
            </Button>
          );
        }

        const range = preset.getRangeOrNull();
        const isActive = isActivePreset(range.from, range.to);

        return (
          <Button
            key={preset.label}
            variant={isActive ? "outline" : "ghost"}
            size="sm"
            onClick={() => handlePresetClick(range)}
            className={cn(isActive && "border-slate-400 dark:border-slate-600")}
          >
            {preset.label}
          </Button>
        );
      })}

      <Button
        variant="ghost"
        size="icon-sm"
        onClick={handleRefresh}
        aria-label="Refresh data"
      >
        <RefreshCw className="size-4" />
      </Button>
    </div>
  );
}
