"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { ActionForm } from "@/components/automations/action-form";
import {
  Plus,
  X,
  ChevronUp,
  ChevronDown,
  ListTodo,
  Bell,
  Mail,
  RefreshCw,
  FolderPlus,
  UserPlus,
  Clock,
} from "lucide-react";
import type { ActionType, DelayUnit, TriggerType } from "@/lib/api/automations";

export interface ActionRow {
  id: string;
  actionType: ActionType;
  actionConfig: Record<string, unknown>;
  sortOrder: number;
  delayDuration: number | null;
  delayUnit: DelayUnit | null;
}

interface ActionListProps {
  actions: ActionRow[];
  onActionsChange: (actions: ActionRow[]) => void;
  triggerType: TriggerType | "";
}

const ACTION_TYPE_OPTIONS: { value: ActionType; label: string }[] = [
  { value: "CREATE_TASK", label: "Create Task" },
  { value: "SEND_NOTIFICATION", label: "Send Notification" },
  { value: "SEND_EMAIL", label: "Send Email" },
  { value: "UPDATE_STATUS", label: "Update Status" },
  { value: "CREATE_PROJECT", label: "Create Project" },
  { value: "ASSIGN_MEMBER", label: "Assign Member" },
];

const ACTION_ICONS: Record<ActionType, typeof ListTodo> = {
  CREATE_TASK: ListTodo,
  SEND_NOTIFICATION: Bell,
  SEND_EMAIL: Mail,
  UPDATE_STATUS: RefreshCw,
  CREATE_PROJECT: FolderPlus,
  ASSIGN_MEMBER: UserPlus,
};

const ACTION_LABELS: Record<ActionType, string> = {
  CREATE_TASK: "Create Task",
  SEND_NOTIFICATION: "Send Notification",
  SEND_EMAIL: "Send Email",
  UPDATE_STATUS: "Update Status",
  CREATE_PROJECT: "Create Project",
  ASSIGN_MEMBER: "Assign Member",
};

const DELAY_UNIT_OPTIONS: { value: DelayUnit; label: string }[] = [
  { value: "MINUTES", label: "Minutes" },
  { value: "HOURS", label: "Hours" },
  { value: "DAYS", label: "Days" },
];

export function ActionList({
  actions,
  onActionsChange,
  triggerType,
}: ActionListProps) {
  const [addingType, setAddingType] = useState(false);

  function addAction(type: ActionType) {
    const newAction: ActionRow = {
      id: crypto.randomUUID(),
      actionType: type,
      actionConfig: {},
      sortOrder: actions.length,
      delayDuration: null,
      delayUnit: null,
    };
    onActionsChange([...actions, newAction]);
    setAddingType(false);
  }

  function updateAction(index: number, updates: Partial<ActionRow>) {
    const updated = actions.map((a, i) =>
      i === index ? { ...a, ...updates } : a,
    );
    onActionsChange(updated);
  }

  function removeAction(index: number) {
    const updated = actions
      .filter((_, i) => i !== index)
      .map((a, i) => ({ ...a, sortOrder: i }));
    onActionsChange(updated);
  }

  function moveAction(index: number, direction: "up" | "down") {
    const targetIndex = direction === "up" ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= actions.length) return;

    const updated = [...actions];
    const temp = updated[index];
    updated[index] = updated[targetIndex];
    updated[targetIndex] = temp;
    // Reassign sort orders
    const reordered = updated.map((a, i) => ({ ...a, sortOrder: i }));
    onActionsChange(reordered);
  }

  function toggleDelay(index: number, enabled: boolean) {
    if (enabled) {
      updateAction(index, { delayDuration: 1, delayUnit: "HOURS" });
    } else {
      updateAction(index, { delayDuration: null, delayUnit: null });
    }
  }

  return (
    <div className="space-y-3">
      {actions.length === 0 && !addingType && (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No actions configured. Add an action to define what happens when
          this rule triggers.
        </p>
      )}

      {actions.map((action, index) => {
        const Icon = ACTION_ICONS[action.actionType];
        const hasDelay = action.delayDuration !== null;

        return (
          <Card key={action.id} className="border-slate-200 dark:border-slate-700">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 px-4 py-3">
              <div className="flex items-center gap-2">
                <Icon className="size-4 text-teal-600 dark:text-teal-400" />
                <CardTitle className="text-sm font-medium">
                  {ACTION_LABELS[action.actionType]}
                </CardTitle>
                {hasDelay && (
                  <span className="flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                    <Clock className="size-3" />
                    {action.delayDuration} {action.delayUnit?.toLowerCase()}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="size-7 p-0"
                  onClick={() => moveAction(index, "up")}
                  disabled={index === 0}
                  aria-label="Move action up"
                >
                  <ChevronUp className="size-4" />
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="size-7 p-0"
                  onClick={() => moveAction(index, "down")}
                  disabled={index === actions.length - 1}
                  aria-label="Move action down"
                >
                  <ChevronDown className="size-4" />
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="size-7 p-0 text-slate-400 hover:text-red-600 dark:hover:text-red-400"
                  onClick={() => removeAction(index)}
                  aria-label="Remove action"
                >
                  <X className="size-4" />
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4 px-4 pb-4 pt-0">
              <ActionForm
                actionType={action.actionType}
                actionConfig={action.actionConfig}
                onConfigChange={(config) =>
                  updateAction(index, { actionConfig: config })
                }
                triggerType={triggerType}
              />

              {/* Delay Toggle */}
              <div className="border-t border-slate-100 pt-3 dark:border-slate-800">
                <div className="flex items-center gap-2">
                  <Switch
                    id={`delay-toggle-${action.id}`}
                    checked={hasDelay}
                    onCheckedChange={(checked) =>
                      toggleDelay(index, checked as boolean)
                    }
                    size="sm"
                  />
                  <Label
                    htmlFor={`delay-toggle-${action.id}`}
                    className="text-sm font-normal"
                  >
                    Add Delay
                  </Label>
                </div>
                {hasDelay && (
                  <div className="mt-2 flex items-center gap-2">
                    <Input
                      type="number"
                      min={1}
                      value={action.delayDuration ?? 1}
                      onChange={(e) => {
                        const val = parseInt(e.target.value, 10);
                        updateAction(index, {
                          delayDuration: isNaN(val) ? 1 : val,
                        });
                      }}
                      className="w-20"
                      aria-label="Delay duration"
                    />
                    <Select
                      value={action.delayUnit ?? "HOURS"}
                      onValueChange={(v) =>
                        updateAction(index, { delayUnit: v as DelayUnit })
                      }
                    >
                      <SelectTrigger className="w-28" aria-label="Delay unit">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {DELAY_UNIT_OPTIONS.map((opt) => (
                          <SelectItem key={opt.value} value={opt.value}>
                            {opt.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        );
      })}

      {addingType ? (
        <div className="flex items-center gap-2">
          <Select onValueChange={(v) => addAction(v as ActionType)}>
            <SelectTrigger className="w-56">
              <SelectValue placeholder="Select action type" />
            </SelectTrigger>
            <SelectContent>
              {ACTION_TYPE_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setAddingType(false)}
          >
            Cancel
          </Button>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setAddingType(true)}
        >
          <Plus className="mr-1.5 size-4" />
          Add Action
        </Button>
      )}
    </div>
  );
}
