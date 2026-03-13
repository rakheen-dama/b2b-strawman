"use client";

import { Check, Circle, Loader2, XCircle } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TaskStatus } from "@/lib/types";

const STATUS_OPTIONS: { value: TaskStatus; label: string; icon: typeof Circle }[] = [
  { value: "OPEN", label: "Open", icon: Circle },
  { value: "IN_PROGRESS", label: "In Progress", icon: Loader2 },
  { value: "DONE", label: "Done", icon: Check },
  { value: "CANCELLED", label: "Cancelled", icon: XCircle },
];

interface TaskStatusSelectProps {
  value: TaskStatus;
  onChange: (status: TaskStatus) => void;
}

export function TaskStatusSelect({ value, onChange }: TaskStatusSelectProps) {
  const current = STATUS_OPTIONS.find((o) => o.value === value);
  const CurrentIcon = current?.icon ?? Circle;

  return (
    <Select value={value} onValueChange={(v) => onChange(v as TaskStatus)}>
      <SelectTrigger className="h-7 w-auto gap-1.5 rounded-full border-slate-200 px-2.5 text-xs font-medium dark:border-slate-700">
        <CurrentIcon className="size-3" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent position="popper" sideOffset={4}>
        {STATUS_OPTIONS.map((opt) => {
          const Icon = opt.icon;
          return (
            <SelectItem key={opt.value} value={opt.value}>
              <span className="flex items-center gap-1.5">
                <Icon className="size-3" />
                {opt.label}
              </span>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}
