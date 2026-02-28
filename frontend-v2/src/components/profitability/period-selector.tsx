"use client";

import { useMemo } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export type PeriodPreset =
  | "this-month"
  | "last-month"
  | "this-quarter"
  | "ytd"
  | "custom";

interface PeriodSelectorProps {
  from: string;
  to: string;
  onRangeChange: (from: string, to: string) => void;
}

function formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function getPresetRange(preset: PeriodPreset): { from: string; to: string } {
  const now = new Date();
  switch (preset) {
    case "this-month": {
      const first = new Date(now.getFullYear(), now.getMonth(), 1);
      const last = new Date(now.getFullYear(), now.getMonth() + 1, 0);
      return { from: formatDate(first), to: formatDate(last) };
    }
    case "last-month": {
      const first = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const last = new Date(now.getFullYear(), now.getMonth(), 0);
      return { from: formatDate(first), to: formatDate(last) };
    }
    case "this-quarter": {
      const quarterStart = Math.floor(now.getMonth() / 3) * 3;
      const first = new Date(now.getFullYear(), quarterStart, 1);
      const last = new Date(now.getFullYear(), quarterStart + 3, 0);
      return { from: formatDate(first), to: formatDate(last) };
    }
    case "ytd": {
      const first = new Date(now.getFullYear(), 0, 1);
      return { from: formatDate(first), to: formatDate(now) };
    }
    case "custom":
      return { from: "", to: "" };
  }
}

function detectPreset(from: string, to: string): PeriodPreset {
  for (const preset of [
    "this-month",
    "last-month",
    "this-quarter",
    "ytd",
  ] as PeriodPreset[]) {
    const range = getPresetRange(preset);
    if (range.from === from && range.to === to) return preset;
  }
  return "custom";
}

export function PeriodSelector({
  from,
  to,
  onRangeChange,
}: PeriodSelectorProps) {
  const currentPreset = useMemo(() => detectPreset(from, to), [from, to]);

  function handlePresetChange(value: string) {
    const preset = value as PeriodPreset;
    if (preset === "custom") return;
    const range = getPresetRange(preset);
    onRangeChange(range.from, range.to);
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="space-y-1.5">
        <Label className="text-xs text-slate-500">Period</Label>
        <Select value={currentPreset} onValueChange={handlePresetChange}>
          <SelectTrigger className="w-[160px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="this-month">This Month</SelectItem>
            <SelectItem value="last-month">Last Month</SelectItem>
            <SelectItem value="this-quarter">This Quarter</SelectItem>
            <SelectItem value="ytd">Year to Date</SelectItem>
            <SelectItem value="custom">Custom</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="period-from" className="text-xs text-slate-500">
          From
        </Label>
        <Input
          id="period-from"
          type="date"
          value={from}
          onChange={(e) => onRangeChange(e.target.value, to)}
          className="w-[150px]"
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="period-to" className="text-xs text-slate-500">
          To
        </Label>
        <Input
          id="period-to"
          type="date"
          value={to}
          onChange={(e) => onRangeChange(from, e.target.value)}
          className="w-[150px]"
        />
      </div>
    </div>
  );
}
