"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { updateNotificationPreferences } from "@/lib/actions/notifications";
import type { NotificationPreference } from "@/lib/actions/notifications";

const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  TASK_ASSIGNED: "Task Assigned",
  TASK_CLAIMED: "Task Claimed",
  TASK_UPDATED: "Task Updated",
  COMMENT_ADDED: "Comment Added",
  DOCUMENT_SHARED: "Document Shared",
  MEMBER_INVITED: "Member Invited",
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

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
        {/* Table header */}
        <div className="grid grid-cols-[1fr_80px_80px] items-center gap-4 border-b border-olive-200 px-4 py-3 dark:border-olive-800">
          <p className="text-sm font-semibold text-olive-900 dark:text-olive-100">
            Notification Type
          </p>
          <p className="text-center text-sm font-semibold text-olive-900 dark:text-olive-100">
            In-App
          </p>
          <p className="text-center text-sm font-semibold text-olive-900 dark:text-olive-100">
            Email
          </p>
        </div>

        {/* Preference rows */}
        {preferences.map((pref) => {
          const label =
            NOTIFICATION_TYPE_LABELS[pref.notificationType] ??
            pref.notificationType;
          const inAppId = `inapp-${pref.notificationType}`;
          const emailId = `email-${pref.notificationType}`;

          return (
            <div
              key={pref.notificationType}
              className="grid grid-cols-[1fr_80px_80px] items-center gap-4 border-b border-olive-100 px-4 py-3 last:border-b-0 dark:border-olive-800"
            >
              <Label
                htmlFor={inAppId}
                className="text-sm font-medium text-olive-800 dark:text-olive-200"
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
              <div className="group relative flex justify-center">
                <Switch
                  id={emailId}
                  checked={pref.emailEnabled}
                  disabled
                />
                <span className="pointer-events-none absolute -top-8 left-1/2 -translate-x-1/2 whitespace-nowrap rounded bg-olive-900 px-2 py-1 text-xs text-white opacity-0 shadow-sm transition-opacity group-hover:opacity-100 dark:bg-olive-100 dark:text-olive-900">
                  Coming soon
                </span>
              </div>
            </div>
          );
        })}
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
