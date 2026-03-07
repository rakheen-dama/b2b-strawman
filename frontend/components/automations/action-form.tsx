"use client";

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
import { VariableInserter } from "@/components/automations/variable-inserter";
import type { ActionType, TriggerType } from "@/lib/api/automations";

interface ActionFormProps {
  actionType: ActionType;
  actionConfig: Record<string, unknown>;
  onConfigChange: (config: Record<string, unknown>) => void;
  triggerType: TriggerType | "";
}

const ASSIGN_TO_OPTIONS = [
  { value: "TRIGGER_ACTOR", label: "Trigger Actor" },
  { value: "PROJECT_OWNER", label: "Project Owner" },
  { value: "SPECIFIC_MEMBER", label: "Specific Member" },
  { value: "UNASSIGNED", label: "Unassigned" },
];

const NOTIFICATION_RECIPIENT_OPTIONS = [
  { value: "TRIGGER_ACTOR", label: "Trigger Actor" },
  { value: "PROJECT_OWNER", label: "Project Owner" },
  { value: "ALL_PROJECT_MEMBERS", label: "All Project Members" },
  { value: "ALL_ADMINS", label: "All Admins" },
];

const EMAIL_RECIPIENT_OPTIONS = [
  { value: "TRIGGER_ACTOR", label: "Trigger Actor" },
  { value: "PROJECT_OWNER", label: "Project Owner" },
  { value: "CUSTOMER_CONTACT", label: "Customer Contact" },
];

const TARGET_ENTITY_OPTIONS = [
  { value: "TASK", label: "Task" },
  { value: "PROJECT", label: "Project" },
  { value: "CUSTOMER", label: "Customer" },
  { value: "INVOICE", label: "Invoice" },
];

const STATUS_OPTIONS_BY_ENTITY: Record<
  string,
  { value: string; label: string }[]
> = {
  TASK: [
    { value: "OPEN", label: "Open" },
    { value: "IN_PROGRESS", label: "In Progress" },
    { value: "DONE", label: "Done" },
    { value: "CANCELLED", label: "Cancelled" },
  ],
  PROJECT: [
    { value: "ACTIVE", label: "Active" },
    { value: "COMPLETED", label: "Completed" },
    { value: "ARCHIVED", label: "Archived" },
  ],
  CUSTOMER: [
    { value: "ACTIVE", label: "Active" },
    { value: "ARCHIVED", label: "Archived" },
  ],
  INVOICE: [
    { value: "DRAFT", label: "Draft" },
    { value: "APPROVED", label: "Approved" },
    { value: "SENT", label: "Sent" },
    { value: "PAID", label: "Paid" },
    { value: "VOID", label: "Void" },
  ],
};

const ROLE_OPTIONS = [
  { value: "LEAD", label: "Lead" },
  { value: "MEMBER", label: "Member" },
];

function insertVariable(
  currentValue: string,
  variablePath: string,
): string {
  return `${currentValue}{{${variablePath}}}`;
}

export function ActionForm({
  actionType,
  actionConfig,
  onConfigChange,
  triggerType,
}: ActionFormProps) {
  function updateField(key: string, value: unknown) {
    onConfigChange({ ...actionConfig, [key]: value });
  }

  switch (actionType) {
    case "CREATE_TASK":
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="action-task-name">Task Name</Label>
              <VariableInserter
                triggerType={triggerType}
                onInsert={(v) =>
                  updateField(
                    "taskName",
                    insertVariable(
                      (actionConfig.taskName as string) ?? "",
                      v,
                    ),
                  )
                }
              />
            </div>
            <Input
              id="action-task-name"
              value={(actionConfig.taskName as string) ?? ""}
              onChange={(e) => updateField("taskName", e.target.value)}
              placeholder="e.g. Review: {{task.name}}"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="action-task-desc">Task Description</Label>
            <Textarea
              id="action-task-desc"
              value={(actionConfig.taskDescription as string) ?? ""}
              onChange={(e) => updateField("taskDescription", e.target.value)}
              placeholder="Optional description"
              rows={2}
            />
          </div>
          <div className="space-y-1.5">
            <Label>Assign To</Label>
            <Select
              value={(actionConfig.assignTo as string) ?? ""}
              onValueChange={(v) => updateField("assignTo", v)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select assignee" />
              </SelectTrigger>
              <SelectContent>
                {ASSIGN_TO_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {(actionConfig.assignTo as string) === "SPECIFIC_MEMBER" && (
            <div className="space-y-1.5">
              <Label htmlFor="action-member-id">Member ID</Label>
              <Input
                id="action-member-id"
                value={(actionConfig.specificMemberId as string) ?? ""}
                onChange={(e) =>
                  updateField("specificMemberId", e.target.value)
                }
                placeholder="Member UUID"
              />
            </div>
          )}
        </div>
      );

    case "SEND_NOTIFICATION":
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label>Recipient</Label>
            <Select
              value={(actionConfig.recipientType as string) ?? ""}
              onValueChange={(v) => updateField("recipientType", v)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select recipient" />
              </SelectTrigger>
              <SelectContent>
                {NOTIFICATION_RECIPIENT_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="action-notif-title">Title</Label>
              <VariableInserter
                triggerType={triggerType}
                onInsert={(v) =>
                  updateField(
                    "title",
                    insertVariable((actionConfig.title as string) ?? "", v),
                  )
                }
              />
            </div>
            <Input
              id="action-notif-title"
              value={(actionConfig.title as string) ?? ""}
              onChange={(e) => updateField("title", e.target.value)}
              placeholder="Notification title"
            />
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="action-notif-message">Message</Label>
              <VariableInserter
                triggerType={triggerType}
                onInsert={(v) =>
                  updateField(
                    "message",
                    insertVariable(
                      (actionConfig.message as string) ?? "",
                      v,
                    ),
                  )
                }
              />
            </div>
            <Textarea
              id="action-notif-message"
              value={(actionConfig.message as string) ?? ""}
              onChange={(e) => updateField("message", e.target.value)}
              placeholder="Notification message"
              rows={3}
            />
          </div>
        </div>
      );

    case "SEND_EMAIL":
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label>Recipient</Label>
            <Select
              value={(actionConfig.recipientType as string) ?? ""}
              onValueChange={(v) => updateField("recipientType", v)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select recipient" />
              </SelectTrigger>
              <SelectContent>
                {EMAIL_RECIPIENT_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="action-email-subject">Subject</Label>
              <VariableInserter
                triggerType={triggerType}
                onInsert={(v) =>
                  updateField(
                    "subject",
                    insertVariable(
                      (actionConfig.subject as string) ?? "",
                      v,
                    ),
                  )
                }
              />
            </div>
            <Input
              id="action-email-subject"
              value={(actionConfig.subject as string) ?? ""}
              onChange={(e) => updateField("subject", e.target.value)}
              placeholder="Email subject"
            />
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="action-email-body">Body</Label>
              <VariableInserter
                triggerType={triggerType}
                onInsert={(v) =>
                  updateField(
                    "body",
                    insertVariable((actionConfig.body as string) ?? "", v),
                  )
                }
              />
            </div>
            <Textarea
              id="action-email-body"
              value={(actionConfig.body as string) ?? ""}
              onChange={(e) => updateField("body", e.target.value)}
              placeholder="Email body"
              rows={4}
            />
          </div>
        </div>
      );

    case "UPDATE_STATUS": {
      const entityType = (actionConfig.targetEntityType as string) ?? "";
      const statusOptions = entityType
        ? (STATUS_OPTIONS_BY_ENTITY[entityType] ?? [])
        : [];
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label>Target Entity</Label>
            <Select
              value={entityType}
              onValueChange={(v) =>
                onConfigChange({
                  ...actionConfig,
                  targetEntityType: v,
                  newStatus: "",
                })
              }
            >
              <SelectTrigger>
                <SelectValue placeholder="Select entity type" />
              </SelectTrigger>
              <SelectContent>
                {TARGET_ENTITY_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {entityType && (
            <div className="space-y-1.5">
              <Label>New Status</Label>
              <Select
                value={(actionConfig.newStatus as string) ?? ""}
                onValueChange={(v) => updateField("newStatus", v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  {statusOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
      );
    }

    case "CREATE_PROJECT":
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="action-template-id">Project Template ID</Label>
            <Input
              id="action-template-id"
              value={(actionConfig.templateId as string) ?? ""}
              onChange={(e) => updateField("templateId", e.target.value)}
              placeholder="UUID of the project template"
            />
          </div>
        </div>
      );

    case "ASSIGN_MEMBER":
      return (
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="action-assign-member-id">Member ID</Label>
            <Input
              id="action-assign-member-id"
              value={(actionConfig.memberId as string) ?? ""}
              onChange={(e) => updateField("memberId", e.target.value)}
              placeholder="Member UUID"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Role</Label>
            <Select
              value={(actionConfig.role as string) ?? ""}
              onValueChange={(v) => updateField("role", v)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select role" />
              </SelectTrigger>
              <SelectContent>
                {ROLE_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      );

    default:
      return null;
  }
}
