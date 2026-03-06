"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2 } from "lucide-react";
import { updateRequestReminderSettings } from "@/app/(app)/org/[slug]/settings/request-settings/actions";

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
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Request Reminders
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Configure default reminder settings for information requests.
      </p>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="reminder-interval-days"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
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
              setReminderDays(
                Math.max(1, Math.min(90, parseInt(e.target.value) || 1)),
              )
            }
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Automated reminders will be sent to clients every N days while items
            are outstanding.
          </p>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
        {message && (
          <p
            className={`text-sm ${
              isError
                ? "text-red-600 dark:text-red-400"
                : "text-green-600 dark:text-green-400"
            }`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
