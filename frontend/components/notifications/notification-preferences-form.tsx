"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Switch } from "@/components/ui/switch";
import { Label } from "@b2mash/ui/label";
import { SettingsActionBar, SettingsSection } from "@/components/settings/settings-section";
import { updateNotificationPreferences } from "@/lib/actions/notifications";
import type { NotificationPreference } from "@/lib/actions/notifications";

const NOTIFICATION_TYPE_LABELS: Record<string, { label: string; category: string }> = {
  // Tasks
  TASK_ASSIGNED: { label: "Task Assigned", category: "Tasks" },
  TASK_CLAIMED: { label: "Task Claimed", category: "Tasks" },
  TASK_UPDATED: { label: "Task Updated", category: "Tasks" },
  TASK_CANCELLED: { label: "Task Cancelled", category: "Tasks" },
  TASK_RECURRENCE_CREATED: { label: "Task Recurrence Created", category: "Tasks" },
  // Matters
  PROJECT_COMPLETED: { label: "Matter Completed", category: "Matters" },
  PROJECT_ARCHIVED: { label: "Matter Archived", category: "Matters" },
  PREREQUISITE_BLOCKED_ACTIVATION: { label: "Prerequisite Blocked", category: "Matters" },
  MATTER_CLOSED: { label: "Matter closed", category: "Matters" },
  MATTER_REOPENED: { label: "Matter reopened", category: "Matters" },
  // Collaboration
  COMMENT_ADDED: { label: "Comment Added", category: "Collaboration" },
  DOCUMENT_SHARED: { label: "Document Shared", category: "Collaboration" },
  MEMBER_INVITED: { label: "Member Invited", category: "Collaboration" },
  DOCUMENT_GENERATED: { label: "Document Generated", category: "Collaboration" },
  ACCEPTANCE_COMPLETED: { label: "Acceptance Completed", category: "Collaboration" },
  ACCEPTANCE_DECLINED: { label: "Acceptance Declined", category: "Collaboration" },
  // Billing
  BUDGET_ALERT: { label: "Fee estimate alert", category: "Billing" },
  INVOICE_APPROVED: { label: "Fee note approved", category: "Billing" },
  INVOICE_SENT: { label: "Fee note sent", category: "Billing" },
  INVOICE_PAID: { label: "Fee note paid", category: "Billing" },
  INVOICE_VOIDED: { label: "Fee note voided", category: "Billing" },
  PAYMENT_FAILED: { label: "Payment Failed", category: "Billing" },
  PAYMENT_LINK_EXPIRED: { label: "Payment Link Expired", category: "Billing" },
  BILLING_RUN_COMPLETED: { label: "Billing Run Completed", category: "Billing" },
  BILLING_RUN_SENT: { label: "Billing Run Sent", category: "Billing" },
  BILLING_RUN_FAILURES: { label: "Billing Run Failures", category: "Billing" },
  COLLECTION_ESCALATED: { label: "Collection escalated", category: "Billing" },
  CASH_DIGEST: { label: "Daily cash summary", category: "Billing" },
  // Client Requests
  INFORMATION_REQUEST_ITEM_SUBMITTED: {
    label: "Request Item Submitted",
    category: "Client Requests",
  },
  INFORMATION_REQUEST_COMPLETED: { label: "Request Completed", category: "Client Requests" },
  INFORMATION_REQUEST_DRAFT_CREATED: {
    label: "Request Draft Created",
    category: "Client Requests",
  },
  // Time Tracking
  TIME_REMINDER: { label: "Time Reminders", category: "Time Tracking" },
  // Security & Compliance
  ROLE_PERMISSIONS_CHANGED: { label: "Role Permissions Changed", category: "Security" },
  RETENTION_PURGE_WARNING: { label: "Retention Purge Warning", category: "Security" },
  DSAR_DEADLINE_WARNING: { label: "DSAR Deadline Warning", category: "Security" },
  // K-115 (founder-approved names, 2026-11-26): the types that rendered as raw enum names
  // Diary
  COURT_DATE_REMINDER: { label: "Court date reminder", category: "Diary" },
  PRESCRIPTION_WARNING: { label: "Prescription warning", category: "Diary" },
  // Trust
  TRUST_PAYMENT_AWAITING_APPROVAL: { label: "Trust payment awaiting approval", category: "Trust" },
  TRUST_PAYMENT_APPROVED: { label: "Trust payment approved", category: "Trust" },
  TRUST_PAYMENT_REJECTED: { label: "Trust payment rejected", category: "Trust" },
  TRUST_RECONCILIATION_OVERDUE: { label: "Trust reconciliation overdue", category: "Trust" },
  TRUST_INVESTMENT_MATURING: { label: "Trust investment maturing", category: "Trust" },
  TRUST_APPROVAL_AGING: { label: "Trust approval waiting too long", category: "Trust" },
  TRUST_RECONCILIATION_COMPLETED: { label: "Trust reconciliation completed", category: "Trust" },
};

interface NotificationPreferencesFormProps {
  initialPreferences: NotificationPreference[];
}

export function NotificationPreferencesForm({
  initialPreferences,
}: NotificationPreferencesFormProps) {
  const [preferences, setPreferences] = useState<NotificationPreference[]>(initialPreferences);
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  function handleToggle(
    notificationType: string,
    field: "inAppEnabled" | "emailEnabled",
    checked: boolean
  ) {
    setPreferences((prev) =>
      prev.map((p) => (p.notificationType === notificationType ? { ...p, [field]: checked } : p))
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
  const grouped = preferences.reduce<Record<string, NotificationPreference[]>>((acc, pref) => {
    const meta = NOTIFICATION_TYPE_LABELS[pref.notificationType];
    const category = meta?.category ?? "Other";
    if (!acc[category]) acc[category] = [];
    acc[category].push(pref);
    return acc;
  }, {});

  // Maintain stable category order
  const categoryOrder = [
    "Tasks",
    "Matters",
    "Collaboration",
    "Billing",
    "Client Requests",
    "Time Tracking",
    "Security",
    "Diary",
    "Trust",
    "Other",
  ];
  const sortedCategories = categoryOrder.filter((c) => grouped[c]);

  return (
    <div className="space-y-6">
      {/* One untitled section (no heading today); `py-0` + `-mx-6` keep the full-bleed rows. */}
      <SettingsSection className="overflow-hidden py-0">
        <div className="-mx-6">
          {/* Table header — K-115: `minmax(0,1fr)` lets long labels wrap instead of widening the grid */}
          <div className="grid grid-cols-[minmax(0,1fr)_80px_80px] items-center gap-4 border-b px-4 py-3">
            <p className="text-foreground text-sm font-semibold break-words">Notification Type</p>
            <p className="text-foreground text-center text-sm font-semibold">In-App</p>
            <p className="text-foreground text-center text-sm font-semibold">Email</p>
          </div>

          {/* Grouped preference rows */}
          {sortedCategories.map((category) => (
            <div key={category}>
              {/* Category header */}
              <div className="border-b border-slate-100 bg-slate-50 px-4 py-2 dark:border-slate-800 dark:bg-slate-900">
                {/* K-85: slate-600 on the slate-50 band (was slate-500, 4.48:1); dark unchanged */}
                <p className="text-muted-foreground text-xs font-semibold tracking-wider uppercase">
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
                    className="grid grid-cols-[minmax(0,1fr)_80px_80px] items-center gap-4 border-b border-slate-100 px-4 py-3 last:border-b-0 dark:border-slate-800"
                  >
                    <Label
                      htmlFor={inAppId}
                      className="text-sm font-medium break-words text-slate-800 dark:text-slate-200"
                    >
                      {label}
                    </Label>
                    <div className="flex justify-center">
                      <Switch
                        id={inAppId}
                        checked={pref.inAppEnabled}
                        onCheckedChange={(checked: boolean) =>
                          handleToggle(pref.notificationType, "inAppEnabled", checked)
                        }
                      />
                    </div>
                    <div className="flex justify-center">
                      <Switch
                        id={emailId}
                        checked={pref.emailEnabled}
                        onCheckedChange={(checked: boolean) =>
                          handleToggle(pref.notificationType, "emailEnabled", checked)
                        }
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </SettingsSection>

      {/* Page-level save (one Save for the whole list); the message is the bar's status. */}
      <SettingsActionBar
        status={
          saveMessage && (
            <p
              className={
                saveMessage.includes("success") ? "text-success-foreground" : "text-destructive"
              }
            >
              {saveMessage}
            </p>
          )
        }
      >
        <Button onClick={handleSave} disabled={isSaving}>
          {isSaving ? "Saving..." : "Save preferences"}
        </Button>
      </SettingsActionBar>
    </div>
  );
}
