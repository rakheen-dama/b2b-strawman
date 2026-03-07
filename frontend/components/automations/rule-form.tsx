"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { TriggerConfigForm } from "@/components/automations/trigger-config-form";
import {
  ConditionBuilder,
  type ConditionRow,
} from "@/components/automations/condition-builder";
import {
  ActionList,
  type ActionRow,
} from "@/components/automations/action-list";
import type {
  AutomationRuleResponse,
  TriggerType,
  ConditionOperator,
  ActionType,
  DelayUnit,
} from "@/lib/api/automations";

interface RuleFormData {
  name: string;
  description: string;
  triggerType: TriggerType | "";
  triggerConfig: Record<string, unknown>;
  conditions: Record<string, unknown>[];
  actions: {
    actionType: ActionType;
    actionConfig: Record<string, unknown>;
    sortOrder: number;
    delayDuration: number | null;
    delayUnit: DelayUnit | null;
  }[];
}

interface RuleFormProps {
  rule?: AutomationRuleResponse;
  onSave: (data: RuleFormData) => void;
  onCancel: () => void;
  isSaving: boolean;
}

const TRIGGER_TYPE_OPTIONS: { value: TriggerType; label: string; description: string }[] = [
  { value: "TASK_STATUS_CHANGED", label: "Task Status Changed", description: "Triggered when a task status changes" },
  { value: "PROJECT_STATUS_CHANGED", label: "Project Status Changed", description: "Triggered when a project status changes" },
  { value: "CUSTOMER_STATUS_CHANGED", label: "Customer Status Changed", description: "Triggered when a customer status changes" },
  { value: "INVOICE_STATUS_CHANGED", label: "Invoice Status Changed", description: "Triggered when an invoice status changes" },
  { value: "TIME_ENTRY_CREATED", label: "Time Entry Created", description: "Triggered when a new time entry is logged" },
  { value: "BUDGET_THRESHOLD_REACHED", label: "Budget Threshold Reached", description: "Triggered when budget consumption hits a threshold" },
  { value: "DOCUMENT_ACCEPTED", label: "Document Accepted", description: "Triggered when a document is accepted" },
  { value: "INFORMATION_REQUEST_COMPLETED", label: "Information Request Completed", description: "Triggered when an information request is completed" },
];

function parseConditions(
  raw: Record<string, unknown>[],
): ConditionRow[] {
  return raw.map((c) => ({
    id: crypto.randomUUID(),
    field: (c.field as string) ?? "",
    operator: (c.operator as ConditionOperator) ?? "EQUALS",
    value: (c.value as string) ?? "",
  }));
}

function serializeConditions(
  rows: ConditionRow[],
): Record<string, unknown>[] {
  return rows
    .filter((r) => r.field)
    .map(({ field, operator, value }) => ({
      field,
      operator,
      value: value || undefined,
    }));
}

export function RuleForm({
  rule,
  onSave,
  onCancel,
  isSaving,
}: RuleFormProps) {
  const [name, setName] = useState(rule?.name ?? "");
  const [description, setDescription] = useState(rule?.description ?? "");
  const [triggerType, setTriggerType] = useState<TriggerType | "">(
    rule?.triggerType ?? "",
  );
  const [triggerConfig, setTriggerConfig] = useState<Record<string, unknown>>(
    rule?.triggerConfig ?? {},
  );
  const [conditions, setConditions] = useState<ConditionRow[]>(
    rule?.conditions ? parseConditions(rule.conditions) : [],
  );
  const [actions, setActions] = useState<ActionRow[]>(
    rule?.actions
      ? rule.actions.map((a) => ({
          id: a.id,
          actionType: a.actionType,
          actionConfig: a.actionConfig,
          sortOrder: a.sortOrder,
          delayDuration: a.delayDuration,
          delayUnit: a.delayUnit,
        }))
      : [],
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  function validate(): boolean {
    const newErrors: Record<string, string> = {};
    if (!name.trim()) {
      newErrors.name = "Name is required.";
    }
    if (!triggerType) {
      newErrors.triggerType = "Trigger type is required.";
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }

  function handleSubmit() {
    if (!validate()) return;
    onSave({
      name: name.trim(),
      description: description.trim(),
      triggerType,
      triggerConfig,
      conditions: serializeConditions(conditions),
      actions: actions.map(({ actionType, actionConfig, sortOrder, delayDuration, delayUnit }) => ({
        actionType,
        actionConfig,
        sortOrder,
        delayDuration,
        delayUnit,
      })),
    });
  }

  function handleTriggerTypeChange(value: string) {
    const newType = value as TriggerType;
    setTriggerType(newType);
    setTriggerConfig({});
    setConditions([]);
    setErrors((prev) => {
      const { triggerType: _triggerType, ...rest } = prev;
      return rest;
    });
  }

  return (
    <div className="space-y-6">
      {/* Section 1: Trigger */}
      <Card>
        <CardHeader>
          <CardTitle>Trigger</CardTitle>
          <CardDescription>
            Define when this automation rule should fire.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="rule-name">Name</Label>
            <Input
              id="rule-name"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                if (errors.name) {
                  setErrors((prev) => {
                    const { name: _name, ...rest } = prev;
                    return rest;
                  });
                }
              }}
              placeholder="e.g. Notify on task completion"
            />
            {errors.name && (
              <p className="text-sm text-red-600 dark:text-red-400">
                {errors.name}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="rule-description">Description</Label>
            <Textarea
              id="rule-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional description of what this rule does"
              rows={2}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="trigger-type">Trigger Type</Label>
            <Select value={triggerType} onValueChange={handleTriggerTypeChange}>
              <SelectTrigger id="trigger-type">
                <SelectValue placeholder="Select a trigger type" />
              </SelectTrigger>
              <SelectContent>
                {TRIGGER_TYPE_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.triggerType && (
              <p className="text-sm text-red-600 dark:text-red-400">
                {errors.triggerType}
              </p>
            )}
          </div>

          <TriggerConfigForm
            triggerType={triggerType}
            triggerConfig={triggerConfig}
            onConfigChange={setTriggerConfig}
          />
        </CardContent>
      </Card>

      {/* Section 2: Conditions */}
      <Card>
        <CardHeader>
          <CardTitle>Conditions</CardTitle>
          <CardDescription>
            Optionally add conditions to narrow when this rule executes.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ConditionBuilder
            triggerType={triggerType}
            conditions={conditions}
            onConditionsChange={setConditions}
          />
        </CardContent>
      </Card>

      {/* Section 3: Actions */}
      <Card>
        <CardHeader>
          <CardTitle>Actions</CardTitle>
          <CardDescription>
            Define what happens when this rule triggers.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ActionList
            actions={actions}
            onActionsChange={setActions}
            triggerType={triggerType}
          />
        </CardContent>
      </Card>

      {/* Save / Cancel */}
      <div className="flex items-center gap-3">
        <Button onClick={handleSubmit} disabled={isSaving}>
          {isSaving ? "Saving..." : rule ? "Save Changes" : "Create Rule"}
        </Button>
        <Button variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
