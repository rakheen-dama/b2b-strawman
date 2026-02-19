"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2 } from "lucide-react";
import { updateComplianceSettings } from "@/app/(app)/org/[slug]/settings/compliance/actions";

interface ComplianceSettingsFormProps {
  slug: string;
  dormancyThresholdDays: number;
  dataRequestDeadlineDays: number;
}

export function ComplianceSettingsForm({
  slug,
  dormancyThresholdDays,
  dataRequestDeadlineDays,
}: ComplianceSettingsFormProps) {
  const [dormancy, setDormancy] = useState(dormancyThresholdDays);
  const [deadline, setDeadline] = useState(dataRequestDeadlineDays);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    const result = await updateComplianceSettings(slug, {
      dormancyThresholdDays: dormancy,
      dataRequestDeadlineDays: deadline,
    });

    if (result.success) {
      setMessage("Compliance settings updated.");
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
        General Settings
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Configure dormancy detection and data request processing deadlines.
      </p>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="dormancy-threshold"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Dormancy Threshold (days)
          </label>
          <Input
            id="dormancy-threshold"
            type="number"
            min={1}
            value={dormancy}
            onChange={(e) => setDormancy(parseInt(e.target.value) || 0)}
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Customers inactive for this many days will be flagged as dormant.
          </p>
        </div>
        <div>
          <label
            htmlFor="data-request-deadline"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Data Request Deadline (days)
          </label>
          <Input
            id="data-request-deadline"
            type="number"
            min={1}
            value={deadline}
            onChange={(e) => setDeadline(parseInt(e.target.value) || 0)}
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Number of days allowed to complete a data request before it is overdue.
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
