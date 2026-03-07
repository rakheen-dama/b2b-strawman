"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Plus, X } from "lucide-react";
import type { TriggerType, ConditionOperator } from "@/lib/api/automations";

export interface ConditionRow {
  field: string;
  operator: ConditionOperator;
  value: string;
}

interface ConditionBuilderProps {
  triggerType: TriggerType | "";
  conditions: ConditionRow[];
  onConditionsChange: (conditions: ConditionRow[]) => void;
}

const CONDITION_FIELDS: Record<string, { value: string; label: string }[]> = {
  TASK_STATUS_CHANGED: [
    { value: "task.id", label: "Task ID" },
    { value: "task.name", label: "Task Name" },
    { value: "task.status", label: "Task Status" },
    { value: "task.previousStatus", label: "Task Previous Status" },
    { value: "task.assigneeId", label: "Task Assignee ID" },
    { value: "task.projectId", label: "Task Project ID" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  PROJECT_STATUS_CHANGED: [
    { value: "project.id", label: "Project ID" },
    { value: "project.name", label: "Project Name" },
    { value: "project.status", label: "Project Status" },
    { value: "project.previousStatus", label: "Project Previous Status" },
    { value: "project.customerId", label: "Project Customer ID" },
    { value: "customer.name", label: "Customer Name" },
  ],
  CUSTOMER_STATUS_CHANGED: [
    { value: "customer.id", label: "Customer ID" },
    { value: "customer.name", label: "Customer Name" },
    { value: "customer.status", label: "Customer Status" },
    { value: "customer.previousStatus", label: "Customer Previous Status" },
  ],
  INVOICE_STATUS_CHANGED: [
    { value: "invoice.id", label: "Invoice ID" },
    { value: "invoice.invoiceNumber", label: "Invoice Number" },
    { value: "invoice.status", label: "Invoice Status" },
    { value: "invoice.previousStatus", label: "Invoice Previous Status" },
    { value: "invoice.totalAmount", label: "Invoice Total Amount" },
    { value: "invoice.customerId", label: "Invoice Customer ID" },
    { value: "customer.name", label: "Customer Name" },
  ],
  TIME_ENTRY_CREATED: [
    { value: "timeEntry.id", label: "Time Entry ID" },
    { value: "timeEntry.hours", label: "Time Entry Hours" },
    { value: "timeEntry.taskId", label: "Time Entry Task ID" },
    { value: "timeEntry.projectId", label: "Time Entry Project ID" },
    { value: "task.name", label: "Task Name" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  BUDGET_THRESHOLD_REACHED: [
    { value: "budget.projectId", label: "Budget Project ID" },
    { value: "budget.thresholdPercent", label: "Budget Threshold %" },
    { value: "budget.consumedPercent", label: "Budget Consumed %" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  DOCUMENT_ACCEPTED: [
    { value: "document.id", label: "Document ID" },
    { value: "document.name", label: "Document Name" },
    { value: "document.projectId", label: "Document Project ID" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  INFORMATION_REQUEST_COMPLETED: [
    { value: "request.id", label: "Request ID" },
    { value: "request.customerId", label: "Request Customer ID" },
    { value: "customer.name", label: "Customer Name" },
  ],
};

const OPERATORS: { value: ConditionOperator; label: string }[] = [
  { value: "EQUALS", label: "Equals" },
  { value: "NOT_EQUALS", label: "Not Equals" },
  { value: "IN", label: "In" },
  { value: "NOT_IN", label: "Not In" },
  { value: "GREATER_THAN", label: "Greater Than" },
  { value: "LESS_THAN", label: "Less Than" },
  { value: "CONTAINS", label: "Contains" },
  { value: "IS_NULL", label: "Is Null" },
  { value: "IS_NOT_NULL", label: "Is Not Null" },
];

const NULLARY_OPERATORS = new Set<ConditionOperator>(["IS_NULL", "IS_NOT_NULL"]);

export function ConditionBuilder({
  triggerType,
  conditions,
  onConditionsChange,
}: ConditionBuilderProps) {
  const fields = triggerType ? (CONDITION_FIELDS[triggerType] ?? []) : [];

  function addCondition() {
    onConditionsChange([
      ...conditions,
      { field: "", operator: "EQUALS", value: "" },
    ]);
  }

  function updateCondition(index: number, updates: Partial<ConditionRow>) {
    const updated = conditions.map((c, i) =>
      i === index ? { ...c, ...updates } : c,
    );
    onConditionsChange(updated);
  }

  function removeCondition(index: number) {
    onConditionsChange(conditions.filter((_, i) => i !== index));
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-slate-500 dark:text-slate-400">
        All conditions must be true (AND logic). For OR logic, create separate
        rules.
      </p>

      {conditions.map((condition, index) => (
        <div key={index} className="flex items-start gap-2">
          <Select
            value={condition.field}
            onValueChange={(val) => updateCondition(index, { field: val })}
          >
            <SelectTrigger className="w-48">
              <SelectValue placeholder="Select field" />
            </SelectTrigger>
            <SelectContent>
              {fields.map((f) => (
                <SelectItem key={f.value} value={f.value}>
                  {f.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select
            value={condition.operator}
            onValueChange={(val) =>
              updateCondition(index, {
                operator: val as ConditionOperator,
                value: NULLARY_OPERATORS.has(val as ConditionOperator)
                  ? ""
                  : condition.value,
              })
            }
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="Operator" />
            </SelectTrigger>
            <SelectContent>
              {OPERATORS.map((op) => (
                <SelectItem key={op.value} value={op.value}>
                  {op.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {!NULLARY_OPERATORS.has(condition.operator) && (
            <Input
              value={condition.value}
              onChange={(e) =>
                updateCondition(index, { value: e.target.value })
              }
              placeholder="Value"
              className="flex-1"
            />
          )}

          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="size-9 shrink-0 p-0 text-slate-400 hover:text-red-600 dark:hover:text-red-400"
            onClick={() => removeCondition(index)}
            aria-label="Remove condition"
          >
            <X className="size-4" />
          </Button>
        </div>
      ))}

      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={addCondition}
        disabled={!triggerType}
      >
        <Plus className="mr-1.5 size-4" />
        Add Condition
      </Button>
    </div>
  );
}
