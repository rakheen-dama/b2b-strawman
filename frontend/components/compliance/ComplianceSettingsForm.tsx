"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Loader2 } from "lucide-react";
import { updateComplianceSettings } from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";

interface ComplianceSettingsFormProps {
  slug: string;
  dormancyThresholdDays: number;
  dataRequestDeadlineDays: number;
  /** FICA review intervals in months by risk rating (Phase 93 655B, FD-7). */
  ficaReviewMonthsLow: number;
  ficaReviewMonthsMedium: number;
  ficaReviewMonthsHigh: number;
}

/** The backend's 1–120 month bound (`chk_org_settings_fica_review_months`). */
const MIN_REVIEW_MONTHS = 1;
const MAX_REVIEW_MONTHS = 120;

function clampMonths(raw: string): number {
  const n = parseInt(raw) || MIN_REVIEW_MONTHS;
  return Math.min(MAX_REVIEW_MONTHS, Math.max(MIN_REVIEW_MONTHS, n));
}

const REVIEW_INTERVAL_FIELDS = [
  { key: "low", label: "Low risk (months)" },
  { key: "medium", label: "Medium risk (months)" },
  { key: "high", label: "High risk (months)" },
] as const;

type ReviewIntervalKey = (typeof REVIEW_INTERVAL_FIELDS)[number]["key"];

export function ComplianceSettingsForm({
  slug,
  dormancyThresholdDays,
  dataRequestDeadlineDays,
  ficaReviewMonthsLow,
  ficaReviewMonthsMedium,
  ficaReviewMonthsHigh,
}: ComplianceSettingsFormProps) {
  const [dormancy, setDormancy] = useState(dormancyThresholdDays);
  const [deadline, setDeadline] = useState(dataRequestDeadlineDays);
  const [reviewMonths, setReviewMonths] = useState<Record<ReviewIntervalKey, number>>({
    low: ficaReviewMonthsLow,
    medium: ficaReviewMonthsMedium,
    high: ficaReviewMonthsHigh,
  });
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
      ficaReviewMonthsLow: reviewMonths.low,
      ficaReviewMonthsMedium: reviewMonths.medium,
      ficaReviewMonthsHigh: reviewMonths.high,
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
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">General Settings</h2>
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
            onChange={(e) => setDormancy(Math.max(1, parseInt(e.target.value) || 1))}
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Clients inactive for this many days will be flagged as dormant.
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
            onChange={(e) => setDeadline(Math.max(1, parseInt(e.target.value) || 1))}
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Number of days allowed to complete a data request before it is overdue.
          </p>
        </div>
      </div>

      <fieldset className="mt-6" data-testid="fica-review-intervals">
        <legend className="text-sm font-semibold text-slate-950 dark:text-slate-50">
          FICA review intervals
        </legend>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Your firm&apos;s choice in its Risk Management and Compliance Programme (RMCP). Changes
          apply to clients taken on after you save.
        </p>
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {REVIEW_INTERVAL_FIELDS.map(({ key, label }) => (
            <div key={key}>
              <label
                htmlFor={`fica-review-months-${key}`}
                className="block text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                {label}
              </label>
              <Input
                id={`fica-review-months-${key}`}
                data-testid={`fica-review-months-${key}`}
                type="number"
                min={MIN_REVIEW_MONTHS}
                max={MAX_REVIEW_MONTHS}
                value={reviewMonths[key]}
                onChange={(e) =>
                  setReviewMonths((prev) => ({ ...prev, [key]: clampMonths(e.target.value) }))
                }
                className="mt-1 w-full"
              />
            </div>
          ))}
        </div>
      </fieldset>

      <div className="mt-4 flex items-center gap-3">
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
        {message && (
          <p
            className={`text-sm ${
              isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"
            }`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
