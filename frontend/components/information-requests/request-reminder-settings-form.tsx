"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Loader2 } from "lucide-react";
import { SettingsSection } from "@/components/settings/settings-section";
import { updateRequestReminderSettings } from "@/app/(app)/org/[slug]/settings/matters/_tabs/request-settings-actions";

interface RequestReminderSettingsFormProps {
  slug: string;
  defaultRequestReminderDays: number;
}

export function RequestReminderSettingsForm({
  slug,
  defaultRequestReminderDays,
}: RequestReminderSettingsFormProps) {
  const [reminderDays, setReminderDays] = useState(defaultRequestReminderDays);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    const result = await updateRequestReminderSettings(slug, reminderDays);

    if (result.success) {
      setMessage("Request reminder settings updated.");
      setIsError(false);
    } else {
      setMessage(result.error ?? "Failed to update settings.");
      setIsError(true);
    }
    setSaving(false);
  }

  return (
    <SettingsSection
      title="Request Reminders"
      description="Configure default reminder settings for information requests."
      status={
        message && (
          <p className={isError ? "text-destructive" : "text-success-foreground"}>{message}</p>
        )
      }
      footer={
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="reminder-interval-days"
            className="text-foreground block text-sm font-medium"
          >
            Default Reminder Interval (days)
          </label>
          <Input
            id="reminder-interval-days"
            type="number"
            min={1}
            max={90}
            placeholder="7"
            value={reminderDays}
            onChange={(e) =>
              setReminderDays(Math.max(1, Math.min(90, parseInt(e.target.value) || 1)))
            }
            className="mt-1 w-full"
          />
          <p className="text-muted-foreground mt-1 text-xs">
            Automated reminders will be sent to clients every N days while items are outstanding.
          </p>
        </div>
      </div>
    </SettingsSection>
  );
}
