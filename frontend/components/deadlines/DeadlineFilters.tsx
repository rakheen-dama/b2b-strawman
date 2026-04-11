"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { DeadlineFiltersType } from "@/lib/types";

interface DeadlineFiltersProps {
  initialYear: number;
  initialMonth: number; // 1-indexed
  onFilterChange: (filters: Partial<DeadlineFiltersType>, year: number, month: number) => void;
  isPending?: boolean;
}

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

const CATEGORY_OPTIONS = [
  { value: "", label: "All Categories" },
  { value: "tax", label: "Tax" },
  { value: "corporate", label: "Corporate" },
  { value: "vat", label: "VAT" },
  { value: "payroll", label: "Payroll" },
];

const STATUS_OPTIONS = [
  { value: "", label: "All Statuses" },
  { value: "pending", label: "Pending" },
  { value: "filed", label: "Filed" },
  { value: "overdue", label: "Overdue" },
  { value: "not_applicable", label: "N/A" },
];

export function DeadlineFilters({
  initialYear,
  initialMonth,
  onFilterChange,
  isPending = false,
}: DeadlineFiltersProps) {
  const [year, setYear] = useState(initialYear);
  const [month, setMonth] = useState(initialMonth);
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState("");

  function getMonthRange(y: number, m: number) {
    const from = `${y}-${String(m).padStart(2, "0")}-01`;
    const lastDay = new Date(y, m, 0).getDate();
    const to = `${y}-${String(m).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}`;
    return { from, to };
  }

  function handlePrev() {
    const newMonth = month === 1 ? 12 : month - 1;
    const newYear = month === 1 ? year - 1 : year;
    setMonth(newMonth);
    setYear(newYear);
    const { from, to } = getMonthRange(newYear, newMonth);
    onFilterChange(
      {
        category: category || undefined,
        status: status || undefined,
        from,
        to,
      },
      newYear,
      newMonth
    );
  }

  function handleNext() {
    const newMonth = month === 12 ? 1 : month + 1;
    const newYear = month === 12 ? year + 1 : year;
    setMonth(newMonth);
    setYear(newYear);
    const { from, to } = getMonthRange(newYear, newMonth);
    onFilterChange(
      {
        category: category || undefined,
        status: status || undefined,
        from,
        to,
      },
      newYear,
      newMonth
    );
  }

  function handleCategoryChange(value: string) {
    setCategory(value);
    const { from, to } = getMonthRange(year, month);
    onFilterChange(
      {
        category: value || undefined,
        status: status || undefined,
        from,
        to,
      },
      year,
      month
    );
  }

  function handleStatusChange(value: string) {
    setStatus(value);
    const { from, to } = getMonthRange(year, month);
    onFilterChange(
      {
        category: category || undefined,
        status: value || undefined,
        from,
        to,
      },
      year,
      month
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {/* Month navigation */}
      <div className="flex items-center gap-1">
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
        <span className="font-display min-w-[140px] text-center text-base font-semibold text-slate-900 dark:text-slate-100">
          {MONTH_NAMES[month - 1]} {year}
        </span>
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

      {/* Category filter */}
      <Select
        value={category || "all"}
        onValueChange={(v) => handleCategoryChange(v === "all" ? "" : v)}
      >
        <SelectTrigger className="w-[160px]">
          <SelectValue placeholder="All Categories" />
        </SelectTrigger>
        <SelectContent>
          {CATEGORY_OPTIONS.map((opt) => (
            <SelectItem key={opt.value || "all"} value={opt.value || "all"}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Status filter */}
      <Select
        value={status || "all"}
        onValueChange={(v) => handleStatusChange(v === "all" ? "" : v)}
      >
        <SelectTrigger className="w-[160px]">
          <SelectValue placeholder="All Statuses" />
        </SelectTrigger>
        <SelectContent>
          {STATUS_OPTIONS.map((opt) => (
            <SelectItem key={opt.value || "all"} value={opt.value || "all"}>
              {opt.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
