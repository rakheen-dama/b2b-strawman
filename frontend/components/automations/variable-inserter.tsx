"use client";

import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Variable } from "lucide-react";
import { useState } from "react";
import type { TriggerType } from "@/lib/api/automations";

interface VariableInserterProps {
  triggerType: TriggerType | "";
  onInsert: (variablePath: string) => void;
}

const COMMON_VARIABLES = [
  { value: "actor.name", label: "Actor Name" },
  { value: "rule.name", label: "Rule Name" },
];

const TRIGGER_VARIABLES: Record<string, { value: string; label: string }[]> = {
  TASK_STATUS_CHANGED: [
    { value: "task.name", label: "Task Name" },
    { value: "task.status", label: "Task Status" },
    { value: "task.previousStatus", label: "Task Previous Status" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  PROJECT_STATUS_CHANGED: [
    { value: "project.name", label: "Project Name" },
    { value: "project.status", label: "Project Status" },
    { value: "project.previousStatus", label: "Project Previous Status" },
    { value: "customer.name", label: "Customer Name" },
  ],
  CUSTOMER_STATUS_CHANGED: [
    { value: "customer.name", label: "Customer Name" },
    { value: "customer.status", label: "Customer Status" },
    { value: "customer.previousStatus", label: "Customer Previous Status" },
  ],
  INVOICE_STATUS_CHANGED: [
    { value: "invoice.invoiceNumber", label: "Invoice Number" },
    { value: "invoice.totalAmount", label: "Invoice Total Amount" },
    { value: "invoice.status", label: "Invoice Status" },
    { value: "customer.name", label: "Customer Name" },
    { value: "project.name", label: "Project Name" },
  ],
  TIME_ENTRY_CREATED: [
    { value: "timeEntry.hours", label: "Time Entry Hours" },
    { value: "task.name", label: "Task Name" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  BUDGET_THRESHOLD_REACHED: [
    { value: "budget.thresholdPercent", label: "Budget Threshold %" },
    { value: "budget.consumedPercent", label: "Budget Consumed %" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  DOCUMENT_ACCEPTED: [
    { value: "document.name", label: "Document Name" },
    { value: "project.name", label: "Project Name" },
    { value: "customer.name", label: "Customer Name" },
  ],
  INFORMATION_REQUEST_COMPLETED: [
    { value: "customer.name", label: "Customer Name" },
  ],
};

export function VariableInserter({
  triggerType,
  onInsert,
}: VariableInserterProps) {
  const [open, setOpen] = useState(false);

  const triggerVars = triggerType ? (TRIGGER_VARIABLES[triggerType] ?? []) : [];
  const allVariables = [...COMMON_VARIABLES, ...triggerVars];

  function handleSelect(variablePath: string) {
    onInsert(variablePath);
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-7 gap-1 px-2 text-xs text-slate-500 hover:text-teal-600 dark:text-slate-400 dark:hover:text-teal-400"
        >
          <Variable className="size-3.5" />
          Insert Variable
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64 p-2">
        <div className="space-y-1">
          <p className="px-2 py-1 text-xs font-medium text-slate-500 dark:text-slate-400">
            Available Variables
          </p>
          {allVariables.map((v) => (
            <button
              key={v.value}
              type="button"
              className="flex w-full items-center rounded-md px-2 py-1.5 text-left text-sm hover:bg-slate-100 dark:hover:bg-slate-800"
              onClick={() => handleSelect(v.value)}
            >
              <code className="mr-2 text-xs text-teal-600 dark:text-teal-400">
                {`{{${v.value}}}`}
              </code>
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {v.label}
              </span>
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
