"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export interface DateRangeValue {
  field: string;
  from?: string;
  to?: string;
}

interface DateRangeFilterProps {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
}

const DATE_FIELDS = [
  { value: "created_at", label: "Created" },
  { value: "updated_at", label: "Updated" },
];

export function DateRangeFilter({ value, onChange }: DateRangeFilterProps) {
  return (
    <div className="space-y-3">
      <Label className="text-sm font-medium text-olive-700 dark:text-olive-300">
        Date Range
      </Label>
      <div className="space-y-2">
        <select
          value={value.field}
          onChange={(e) => onChange({ ...value, field: e.target.value })}
          className="h-9 w-full rounded-md border border-olive-200 bg-transparent px-3 text-sm dark:border-olive-700"
        >
          {DATE_FIELDS.map((f) => (
            <option key={f.value} value={f.value}>
              {f.label}
            </option>
          ))}
        </select>
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <Input
              type="date"
              value={value.from ?? ""}
              onChange={(e) =>
                onChange({ ...value, from: e.target.value || undefined })
              }
              placeholder="From"
            />
          </div>
          <span className="text-sm text-olive-400">to</span>
          <div className="flex-1">
            <Input
              type="date"
              value={value.to ?? ""}
              onChange={(e) =>
                onChange({ ...value, to: e.target.value || undefined })
              }
              placeholder="To"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
