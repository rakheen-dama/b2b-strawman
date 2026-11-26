import { Badge } from "@b2mash/ui/badge";
import type { TriggerType } from "@/lib/api/automations";

const TRIGGER_TYPE_CONFIG: Record<
  TriggerType,
  { label: string; variant: "lead" | "warning" | "success" | "neutral" | "outline" }
> = {
  TASK_STATUS_CHANGED: { label: "Task Status", variant: "lead" },
  PROJECT_STATUS_CHANGED: { label: "Matter Status", variant: "success" },
  CUSTOMER_STATUS_CHANGED: { label: "Client Status", variant: "neutral" },
  INVOICE_STATUS_CHANGED: { label: "Fee note status", variant: "warning" },
  TIME_ENTRY_CREATED: { label: "Time Entry", variant: "outline" },
  BUDGET_THRESHOLD_REACHED: { label: "Fee estimate threshold", variant: "warning" },
  DOCUMENT_ACCEPTED: { label: "Document uploaded", variant: "success" },
  INFORMATION_REQUEST_COMPLETED: { label: "Request Completed", variant: "lead" },
  FIELD_DATE_APPROACHING: { label: "Date Approaching", variant: "warning" },
  SCHEDULED: { label: "Scheduled", variant: "outline" },
};

/**
 * K-122: executions record the Java event class (`AutomationEventListener`
 * `event.getClass().getSimpleName()`; scheduled rules record `"ScheduledTrigger"`), not the rule's
 * trigger type. Mirrors `TriggerTypeMapping.java`, so the execution log shows the same badge label
 * as the rule list.
 */
const EVENT_CLASS_TRIGGER: Record<string, TriggerType> = {
  TaskStatusChangedEvent: "TASK_STATUS_CHANGED",
  TaskCompletedEvent: "TASK_STATUS_CHANGED",
  ProjectCompletedEvent: "PROJECT_STATUS_CHANGED",
  ProjectArchivedEvent: "PROJECT_STATUS_CHANGED",
  ProjectReopenedEvent: "PROJECT_STATUS_CHANGED",
  BudgetThresholdEvent: "BUDGET_THRESHOLD_REACHED",
  TimeEntryChangedEvent: "TIME_ENTRY_CREATED",
  DocumentUploadedEvent: "DOCUMENT_ACCEPTED",
  InvoiceSentEvent: "INVOICE_STATUS_CHANGED",
  InvoicePaidEvent: "INVOICE_STATUS_CHANGED",
  InvoiceVoidedEvent: "INVOICE_STATUS_CHANGED",
  InformationRequestCompletedEvent: "INFORMATION_REQUEST_COMPLETED",
  CustomerStatusChangedEvent: "CUSTOMER_STATUS_CHANGED",
  FieldDateApproachingEvent: "FIELD_DATE_APPROACHING",
  ScheduledTrigger: "SCHEDULED",
};

/** The trigger type for a recorded event class; anything else (already a type, unknown) as is. */
export function triggerTypeFromEvent(eventType: string): string {
  return EVENT_CLASS_TRIGGER[eventType] ?? eventType;
}

interface TriggerTypeBadgeProps {
  triggerType: TriggerType;
}

export function TriggerTypeBadge({ triggerType }: TriggerTypeBadgeProps) {
  const config = TRIGGER_TYPE_CONFIG[triggerType] ?? {
    label: triggerType,
    variant: "neutral" as const,
  };

  return <Badge variant={config.variant}>{config.label}</Badge>;
}
