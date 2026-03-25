"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TriggerType } from "@/lib/api/automations";

interface TriggerConfigFormProps {
  triggerType: TriggerType | "";
  triggerConfig: Record<string, unknown>;
  onConfigChange: (config: Record<string, unknown>) => void;
}

const STATUS_OPTIONS: Record<string, { value: string; label: string }[]> = {
  TASK_STATUS_CHANGED: [
    { value: "OPEN", label: "Open" },
    { value: "IN_PROGRESS", label: "In Progress" },
    { value: "DONE", label: "Done" },
    { value: "CANCELLED", label: "Cancelled" },
  ],
  PROJECT_STATUS_CHANGED: [
    { value: "ACTIVE", label: "Active" },
    { value: "COMPLETED", label: "Completed" },
    { value: "ARCHIVED", label: "Archived" },
  ],
  CUSTOMER_STATUS_CHANGED: [
    { value: "ACTIVE", label: "Active" },
    { value: "ARCHIVED", label: "Archived" },
  ],
  INVOICE_STATUS_CHANGED: [
    { value: "DRAFT", label: "Draft" },
    { value: "APPROVED", label: "Approved" },
    { value: "SENT", label: "Sent" },
    { value: "PAID", label: "Paid" },
    { value: "VOID", label: "Void" },
  ],
};

const STATUS_CHANGE_TRIGGERS = new Set([
  "TASK_STATUS_CHANGED",
  "PROJECT_STATUS_CHANGED",
  "CUSTOMER_STATUS_CHANGED",
  "INVOICE_STATUS_CHANGED",
]);

const SIMPLE_TRIGGERS = new Set([
  "TIME_ENTRY_CREATED",
  "DOCUMENT_ACCEPTED",
  "INFORMATION_REQUEST_COMPLETED",
  "PROPOSAL_SENT",
  "FIELD_DATE_APPROACHING",
]);

export function TriggerConfigForm({
  triggerType,
  triggerConfig,
  onConfigChange,
}: TriggerConfigFormProps) {
  if (!triggerType) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">
        Select a trigger type to configure additional settings.
      </p>
    );
  }

  if (STATUS_CHANGE_TRIGGERS.has(triggerType)) {
    const options = STATUS_OPTIONS[triggerType] ?? [];
    const fromStatus = triggerConfig.fromStatus == null ? "ANY" : (triggerConfig.fromStatus as string);
    const toStatus = triggerConfig.toStatus == null ? "ANY" : (triggerConfig.toStatus as string);

    return (
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="from-status">From Status</Label>
          <Select
            value={fromStatus}
            onValueChange={(val) =>
              onConfigChange({
                ...triggerConfig,
                fromStatus: val === "ANY" ? null : val,
              })
            }
          >
            <SelectTrigger id="from-status">
              <SelectValue placeholder="Any status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ANY">Any</SelectItem>
              {options.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="to-status">To Status</Label>
          <Select
            value={toStatus}
            onValueChange={(val) =>
              onConfigChange({
                ...triggerConfig,
                toStatus: val === "ANY" ? null : val,
              })
            }
          >
            <SelectTrigger id="to-status">
              <SelectValue placeholder="Any status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ANY">Any</SelectItem>
              {options.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
    );
  }

  if (triggerType === "BUDGET_THRESHOLD_REACHED") {
    const thresholdPercent =
      (triggerConfig.thresholdPercent as number) ?? 80;

    return (
      <div className="space-y-2">
        <Label htmlFor="threshold-percent">Budget Threshold</Label>
        <div className="flex items-center gap-2">
          <Input
            id="threshold-percent"
            type="number"
            min={1}
            max={200}
            value={thresholdPercent}
            onChange={(e) => {
              const val = Number(e.target.value);
              if (!Number.isNaN(val)) {
                onConfigChange({
                  ...triggerConfig,
                  thresholdPercent: val,
                });
              }
            }}
            className="w-24"
          />
          <span className="text-sm text-slate-600 dark:text-slate-400">
            %
          </span>
        </div>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          Trigger when budget consumption reaches this percentage.
        </p>
      </div>
    );
  }

  if (SIMPLE_TRIGGERS.has(triggerType)) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">
        This trigger has no additional configuration.
      </p>
    );
  }

  return null;
}
