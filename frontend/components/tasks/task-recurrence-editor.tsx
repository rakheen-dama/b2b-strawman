"use client";

import { useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { updateTask } from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { parseRecurrenceRule, formatRecurrenceRule } from "@/lib/recurrence";
import type { RecurrenceFrequency } from "@/lib/recurrence";
import type { Task } from "@/lib/types";

interface RecurrenceEditorProps {
  task: Task;
  slug: string;
  projectId: string;
  onUpdate: (task: Task) => void;
}

export function RecurrenceEditor({ task, slug, projectId, onUpdate }: RecurrenceEditorProps) {
  const parsed = parseRecurrenceRule(task.recurrenceRule);
  const [frequency, setFrequency] = useState<RecurrenceFrequency | "NONE">(
    parsed?.frequency ?? "NONE"
  );
  const [recurrenceInterval, setRecurrenceInterval] = useState(parsed?.interval ?? 1);
  const [endDate, setEndDate] = useState(task.recurrenceEndDate ?? "");
  const [, startTransition] = useTransition();

  function handleSave() {
    const effectiveFrequency = frequency === "NONE" ? null : frequency;
    const rule = effectiveFrequency
      ? formatRecurrenceRule(effectiveFrequency, recurrenceInterval)
      : null;
    const newEndDate = effectiveFrequency && endDate ? endDate : null;

    // Optimistic update
    onUpdate({
      ...task,
      recurrenceRule: rule,
      recurrenceEndDate: newEndDate,
      isRecurring: !!rule,
    });

    startTransition(async () => {
      try {
        const result = await updateTask(slug, task.id, projectId, {
          title: task.title,
          description: task.description ?? undefined,
          priority: task.priority,
          status: task.status,
          type: task.type ?? undefined,
          dueDate: task.dueDate ?? undefined,
          assigneeId: task.assigneeId ?? undefined,
          recurrenceRule: rule ?? undefined,
          recurrenceEndDate: newEndDate ?? undefined,
        });

        if (!result.success) {
          onUpdate(task);
        }
      } catch {
        onUpdate(task);
      }
    });
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-xs text-slate-500 dark:text-slate-400">Frequency</label>
          <Select
            value={frequency}
            onValueChange={(v) => setFrequency(v as RecurrenceFrequency | "NONE")}
          >
            <SelectTrigger className="mt-1 h-8 w-full text-xs">
              <SelectValue placeholder="None" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="NONE">None</SelectItem>
              <SelectItem value="DAILY">Daily</SelectItem>
              <SelectItem value="WEEKLY">Weekly</SelectItem>
              <SelectItem value="MONTHLY">Monthly</SelectItem>
              <SelectItem value="YEARLY">Yearly</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {frequency !== "NONE" && (
          <div>
            <label
              htmlFor="detail-recurrence-interval"
              className="text-xs text-slate-500 dark:text-slate-400"
            >
              Interval
            </label>
            <Input
              id="detail-recurrence-interval"
              type="number"
              min={1}
              value={recurrenceInterval}
              onChange={(e) =>
                setRecurrenceInterval(Math.max(1, parseInt(e.target.value, 10) || 1))
              }
              className="mt-1 h-8 text-xs"
            />
          </div>
        )}
      </div>
      {frequency && (
        <div>
          <label
            htmlFor="detail-recurrence-end"
            className="text-xs text-slate-500 dark:text-slate-400"
          >
            End Date (optional)
          </label>
          <Input
            id="detail-recurrence-end"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="mt-1 h-8 text-xs"
          />
        </div>
      )}
      <Button size="sm" variant="outline" onClick={handleSave}>
        Save Recurrence
      </Button>
    </div>
  );
}
