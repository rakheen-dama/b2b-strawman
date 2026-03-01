"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { updateTimeTrackingSettings } from "@/app/(app)/org/[slug]/settings/time-tracking/actions";

const DAYS_OF_WEEK = [
  { key: "MON", label: "Mon" },
  { key: "TUE", label: "Tue" },
  { key: "WED", label: "Wed" },
  { key: "THU", label: "Thu" },
  { key: "FRI", label: "Fri" },
  { key: "SAT", label: "Sat" },
  { key: "SUN", label: "Sun" },
] as const;

const DEFAULT_WORKING_DAYS = "MON,TUE,WED,THU,FRI";

interface TimeTrackingSettingsFormProps {
  slug: string;
  timeReminderEnabled: boolean;
  timeReminderDays: string;
  timeReminderTime: string;
  timeReminderMinHours: number;
  defaultExpenseMarkupPercent: number | null;
}

export function TimeTrackingSettingsForm({
  slug,
  timeReminderEnabled,
  timeReminderDays,
  timeReminderTime,
  timeReminderMinHours,
  defaultExpenseMarkupPercent,
}: TimeTrackingSettingsFormProps) {
  const [enabled, setEnabled] = useState(timeReminderEnabled);
  const [selectedDays, setSelectedDays] = useState<Set<string>>(
    new Set(timeReminderDays ? timeReminderDays.split(",") : DEFAULT_WORKING_DAYS.split(","))
  );
  const [reminderTime, setReminderTime] = useState(timeReminderTime);
  const [minHours, setMinHours] = useState(timeReminderMinHours);
  const [markupPercent, setMarkupPercent] = useState<string>(
    defaultExpenseMarkupPercent != null ? String(defaultExpenseMarkupPercent) : ""
  );
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  function toggleDay(day: string) {
    setSelectedDays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) {
        next.delete(day);
      } else {
        next.add(day);
      }
      return next;
    });
  }

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    // Validate minHours range
    if (minHours < 0 || minHours > 24) {
      setMessage("Minimum hours must be between 0 and 24.");
      setIsError(true);
      setSaving(false);
      return;
    }

    // Validate markup percent
    let parsedMarkup: number | null = null;
    if (markupPercent.trim() !== "") {
      parsedMarkup = parseFloat(markupPercent);
      if (isNaN(parsedMarkup)) {
        setMessage("Markup percent must be a valid number.");
        setIsError(true);
        setSaving(false);
        return;
      }
      if (parsedMarkup < 0 || parsedMarkup > 999) {
        setMessage("Markup percent must be between 0 and 999.");
        setIsError(true);
        setSaving(false);
        return;
      }
    }

    const daysCSV = DAYS_OF_WEEK
      .filter((d) => selectedDays.has(d.key))
      .map((d) => d.key)
      .join(",");

    try {
      const result = await updateTimeTrackingSettings(slug, {
        timeReminderEnabled: enabled,
        timeReminderDays: daysCSV,
        timeReminderTime: reminderTime,
        timeReminderMinHours: minHours,
        defaultExpenseMarkupPercent: parsedMarkup,
      });

      if (result.success) {
        setMessage("Time tracking settings updated.");
        setIsError(false);
      } else {
        setMessage(result.error ?? "Failed to update settings.");
        setIsError(true);
      }
    } catch {
      setMessage("An unexpected error occurred. Please try again.");
      setIsError(true);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Time Reminders Section */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Time Reminders
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure automatic reminders for team members to log their time.
        </p>

        <div className="mt-4 space-y-4">
          {/* Enable toggle */}
          <div className="flex items-center gap-3">
            <Switch
              id="time-reminder-toggle"
              checked={enabled}
              onCheckedChange={(checked: boolean) => setEnabled(checked)}
            />
            <Label
              htmlFor="time-reminder-toggle"
              className="text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Enable time reminders
            </Label>
          </div>

          {/* Working days */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
              Working Days
            </label>
            <div className="mt-2 flex flex-wrap gap-4">
              {DAYS_OF_WEEK.map((day) => (
                <div key={day.key} className="flex items-center gap-2">
                  <Checkbox
                    id={`day-${day.key}`}
                    checked={selectedDays.has(day.key)}
                    onCheckedChange={() => toggleDay(day.key)}
                  />
                  <Label
                    htmlFor={`day-${day.key}`}
                    className="text-sm text-slate-700 dark:text-slate-300"
                  >
                    {day.label}
                  </Label>
                </div>
              ))}
            </div>
          </div>

          {/* Reminder time */}
          <div>
            <label
              htmlFor="reminder-time"
              className="block text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Reminder Time (UTC)
            </label>
            <Input
              id="reminder-time"
              type="time"
              value={reminderTime}
              onChange={(e) => setReminderTime(e.target.value)}
              className="mt-1 w-40"
            />
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Note: Time is interpreted as UTC. For SAST (UTC+2), enter 2 hours
              earlier than your local time.
            </p>
          </div>

          {/* Minimum hours */}
          <div>
            <label
              htmlFor="min-hours"
              className="block text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              Minimum Hours
            </label>
            <Input
              id="min-hours"
              type="number"
              min={0}
              max={24}
              step={0.5}
              value={minHours}
              onChange={(e) =>
                setMinHours(Math.max(0, Math.min(24, parseFloat(e.target.value) || 0)))
              }
              className="mt-1 w-40"
            />
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Members who log fewer than this many hours on a working day will
              receive a reminder.
            </p>
          </div>
        </div>
      </div>

      {/* Default Expense Markup Section */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Default Expense Markup
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Set a default markup percentage applied to all expenses unless
          overridden per-expense.
        </p>

        <div className="mt-4">
          <label
            htmlFor="expense-markup"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Default Expense Markup (%)
          </label>
          <Input
            id="expense-markup"
            type="number"
            min={0}
            max={999}
            step={0.01}
            value={markupPercent}
            onChange={(e) => setMarkupPercent(e.target.value)}
            placeholder="No markup"
            className="mt-1 w-40"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Applied to all expenses unless overridden per-expense. Leave blank
            for no markup.
          </p>
        </div>
      </div>

      {/* Save button */}
      <div className="flex items-center gap-3">
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
