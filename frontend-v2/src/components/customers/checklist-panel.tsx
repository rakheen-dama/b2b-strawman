"use client";

import * as React from "react";
import { Check, Circle, AlertCircle, Ban } from "lucide-react";

import type {
  ChecklistInstanceResponse,
  ChecklistInstanceItemResponse,
  ChecklistItemStatus,
} from "@/lib/types";
import { cn } from "@/lib/utils";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { StatusBadge } from "@/components/ui/status-badge";

interface ChecklistPanelProps {
  checklist: ChecklistInstanceResponse | null;
  className?: string;
}

const STATUS_ICON: Record<ChecklistItemStatus, React.ReactNode> = {
  PENDING: <Circle className="size-4 text-slate-400" />,
  COMPLETED: <Check className="size-4 text-emerald-600" />,
  SKIPPED: <Ban className="size-4 text-slate-400" />,
  BLOCKED: <AlertCircle className="size-4 text-amber-500" />,
  CANCELLED: <Ban className="size-4 text-red-400" />,
};

export function ChecklistPanel({ checklist, className }: ChecklistPanelProps) {
  if (!checklist) {
    return (
      <Card className={className}>
        <CardContent className="py-8 text-center">
          <p className="text-sm text-slate-500">
            No checklist assigned for this lifecycle stage.
          </p>
        </CardContent>
      </Card>
    );
  }

  const completed = checklist.items.filter(
    (i) => i.status === "COMPLETED"
  ).length;
  const total = checklist.items.length;
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle className="text-base">Onboarding Checklist</CardTitle>
          <p className="mt-0.5 text-xs text-slate-500">
            {completed} of {total} items completed
          </p>
        </div>
        <StatusBadge status={checklist.status} />
      </CardHeader>
      <CardContent className="space-y-4">
        <Progress value={percent} className="h-2" />

        <ul className="space-y-2">
          {checklist.items
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((item) => (
              <ChecklistItem key={item.id} item={item} />
            ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function ChecklistItem({ item }: { item: ChecklistInstanceItemResponse }) {
  return (
    <li
      className={cn(
        "flex items-start gap-3 rounded-md px-3 py-2 text-sm transition-colors",
        item.status === "COMPLETED" && "bg-emerald-50/50",
        item.status === "BLOCKED" && "bg-amber-50/50"
      )}
    >
      <div className="mt-0.5 shrink-0">{STATUS_ICON[item.status]}</div>
      <div className="flex-1">
        <p
          className={cn(
            "font-medium",
            item.status === "COMPLETED"
              ? "text-slate-500 line-through"
              : "text-slate-800"
          )}
        >
          {item.name}
          {item.required && (
            <span className="ml-1 text-red-400 text-xs">*</span>
          )}
        </p>
        {item.description && (
          <p className="mt-0.5 text-xs text-slate-500">{item.description}</p>
        )}
        {item.completedAt && item.completedByName && (
          <p className="mt-0.5 text-[10px] text-slate-400">
            Completed by {item.completedByName}
          </p>
        )}
      </div>
    </li>
  );
}
