"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { updateCapacitySettings } from "@/app/(app)/org/[slug]/settings/capacity/actions";

interface DefaultCapacitySettingsProps {
  slug: string;
  defaultWeeklyCapacityHours: number;
}

export function DefaultCapacitySettings({
  slug,
  defaultWeeklyCapacityHours,
}: DefaultCapacitySettingsProps) {
  const [hours, setHours] = useState(defaultWeeklyCapacityHours);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    try {
      const result = await updateCapacitySettings(slug, {
        defaultWeeklyCapacityHours: hours,
      });

      if (result.success) {
        setMessage("Capacity settings updated.");
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
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Default Weekly Capacity
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Set the default number of hours per week for team members. Individual
          members can override this in their capacity settings.
        </p>
        <div className="mt-4 max-w-xs space-y-2">
          <Label htmlFor="weekly-capacity-hours">Hours per week</Label>
          <Input
            id="weekly-capacity-hours"
            type="number"
            min={0}
            max={168}
            step={0.5}
            value={hours}
            onChange={(e) => setHours(parseFloat(e.target.value) || 0)}
          />
        </div>
      </div>

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
