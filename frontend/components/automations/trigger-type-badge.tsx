import { Badge } from "@/components/ui/badge";
import type { TriggerType } from "@/lib/api/automations";

const TRIGGER_TYPE_CONFIG: Record<
  TriggerType,
  { label: string; variant: "lead" | "warning" | "success" | "neutral" | "outline" }
> = {
  TASK_STATUS_CHANGED: { label: "Task Status", variant: "lead" },
  PROJECT_STATUS_CHANGED: { label: "Project Status", variant: "success" },
  CUSTOMER_STATUS_CHANGED: { label: "Customer Status", variant: "neutral" },
  INVOICE_STATUS_CHANGED: { label: "Invoice Status", variant: "warning" },
  TIME_ENTRY_CREATED: { label: "Time Entry", variant: "outline" },
  BUDGET_THRESHOLD_REACHED: { label: "Budget Threshold", variant: "warning" },
  DOCUMENT_ACCEPTED: { label: "Document Accepted", variant: "success" },
  INFORMATION_REQUEST_COMPLETED: { label: "Request Completed", variant: "lead" },
  PROPOSAL_SENT: { label: "Proposal Sent", variant: "success" },
  FIELD_DATE_APPROACHING: { label: "Date Approaching", variant: "warning" },
};

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
