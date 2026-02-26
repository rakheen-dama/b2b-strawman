"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { updateNotificationPreferences } from "@/lib/actions/notifications";
import type { NotificationPreference } from "@/lib/actions/notifications";

const NOTIFICATION_TYPE_LABELS: Record<
  string,
  { label: string; category: string }
> = {
  // Tasks
  TASK_ASSIGNED: { label: "Task Assigned", category: "Tasks" },
  TASK_CLAIMED: { label: "Task Claimed", category: "Tasks" },
  TASK_UPDATED: { label: "Task Updated", category: "Tasks" },
  // Collaboration
  COMMENT_ADDED: { label: "Comment Added", category: "Collaboration" },
  DOCUMENT_SHARED: { label: "Document Shared", category: "Collaboration" },
  MEMBER_INVITED: { label: "Member Invited", category: "Collaboration" },
  DOCUMENT_GENERATED: {
    label: "Document Generated",
    category: "Collaboration",
  },
  // Billing & Invoicing
  BUDGET_ALERT: { label: "Budget Alert", category: "Billing & Invoicing" },
  INVOICE_APPROVED: {
    label: "Invoice Approved",
    category: "Billing & Invoicing",
  },
  INVOICE_SENT: { label: "Invoice Sent", category: "Billing & Invoicing" },
  INVOICE_PAID: { label: "Invoice Paid", category: "Billing & Invoicing" },
  INVOICE_VOIDED: {
    label: "Invoice Voided",
    category: "Billing & Invoicing",
  },
  // Scheduling
  RECURRING_PROJECT_CREATED: {
    label: "Recurring Project Created",
    category: "Scheduling",
  },
  SCHEDULE_SKIPPED: { label: "Schedule Skipped", category: "Scheduling" },
  SCHEDULE_COMPLETED: { label: "Schedule Completed", category: "Scheduling" },
  // Retainers
  RETAINER_PERIOD_READY_TO_CLOSE: {
    label: "Retainer Period Ready to Close",
    category: "Retainers",
  },
  RETAINER_PERIOD_CLOSED: {
    label: "Retainer Period Closed",
    category: "Retainers",
  },
  RETAINER_APPROACHING_CAPACITY: {
    label: "Retainer Approaching Capacity",
    category: "Retainers",
  },
  RETAINER_FULLY_CONSUMED: {
    label: "Retainer Fully Consumed",
    category: "Retainers",
  },
  RETAINER_TERMINATED: {
    label: "Retainer Terminated",
    category: "Retainers",
  },
};

interface NotificationPreferencesFormProps {
  initialPreferences: NotificationPreference[];
}

export function NotificationPreferencesForm({
  initialPreferences,
}: NotificationPreferencesFormProps) {
  const [preferences, setPreferences] =
    useState<NotificationPreference[]>(initialPreferences);
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  function handleToggle(
    notificationType: string,
    field: "inAppEnabled" | "emailEnabled",
    checked: boolean
  ) {
    setPreferences((prev) =>
      prev.map((p) =>
        p.notificationType === notificationType
          ? { ...p, [field]: checked }
          : p
      )
    );
    setSaveMessage(null);
  }

  async function handleSave() {
    setIsSaving(true);
    setSaveMessage(null);
    try {
      const result = await updateNotificationPreferences(preferences);
      setPreferences(result.preferences);
      setSaveMessage("Preferences saved successfully.");
    } catch {
      setSaveMessage("Failed to save preferences. Please try again.");
    } finally {
      setIsSaving(false);
    }
  }

  // Group preferences by category
  const grouped = preferences.reduce<
    Record<string, NotificationPreference[]>
  >((acc, pref) => {
    const meta = NOTIFICATION_TYPE_LABELS[pref.notificationType];
    const category = meta?.category ?? "Other";
    if (!acc[category]) acc[category] = [];
    acc[category].push(pref);
    return acc;
  }, {});

  // Maintain stable category order
  const categoryOrder = [
    "Tasks",
    "Collaboration",
    "Billing & Invoicing",
    "Scheduling",
    "Retainers",
    "Other",
  ];
  const sortedCategories = categoryOrder.filter((c) => grouped[c]);

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        {/* Table header */}
        <div className="grid grid-cols-[1fr_80px_80px] items-center gap-4 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Notification Type
          </p>
          <p className="text-center text-sm font-semibold text-slate-900 dark:text-slate-100">
            In-App
          </p>
          <p className="text-center text-sm font-semibold text-slate-900 dark:text-slate-100">
            Email
          </p>
        </div>

        {/* Grouped preference rows */}
        {sortedCategories.map((category) => (
          <div key={category}>
            {/* Category header */}
            <div className="border-b border-slate-100 bg-slate-50 px-4 py-2 dark:border-slate-800 dark:bg-slate-900">
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                {category}
              </p>
            </div>

            {/* Preferences in this category */}
            {grouped[category].map((pref) => {
              const meta = NOTIFICATION_TYPE_LABELS[pref.notificationType];
              const label = meta?.label ?? pref.notificationType;
              const inAppId = `inapp-${pref.notificationType}`;
              const emailId = `email-${pref.notificationType}`;

              return (
                <div
                  key={pref.notificationType}
                  className="grid grid-cols-[1fr_80px_80px] items-center gap-4 border-b border-slate-100 px-4 py-3 last:border-b-0 dark:border-slate-800"
                >
                  <Label
                    htmlFor={inAppId}
                    className="text-sm font-medium text-slate-800 dark:text-slate-200"
                  >
                    {label}
                  </Label>
                  <div className="flex justify-center">
                    <Switch
                      id={inAppId}
                      checked={pref.inAppEnabled}
                      onCheckedChange={(checked: boolean) =>
                        handleToggle(
                          pref.notificationType,
                          "inAppEnabled",
                          checked
                        )
                      }
                    />
                  </div>
                  <div className="flex justify-center">
                    <Switch
                      id={emailId}
                      checked={pref.emailEnabled}
                      onCheckedChange={(checked: boolean) =>
                        handleToggle(
                          pref.notificationType,
                          "emailEnabled",
                          checked
                        )
                      }
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      {/* Save button and feedback */}
      <div className="flex items-center gap-4">
        <Button onClick={handleSave} disabled={isSaving}>
          {isSaving ? "Saving..." : "Save preferences"}
        </Button>
        {saveMessage && (
          <p
            className={`text-sm ${
              saveMessage.includes("success")
                ? "text-green-600 dark:text-green-400"
                : "text-red-600 dark:text-red-400"
            }`}
          >
            {saveMessage}
          </p>
        )}
      </div>
    </div>
  );
}
