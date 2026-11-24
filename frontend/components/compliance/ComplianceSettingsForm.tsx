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

const REVIEW_MONTHS_ERROR = `Enter a whole number of months from ${MIN_REVIEW_MONTHS} to ${MAX_REVIEW_MONTHS}.`;

/** A whole number of months within 1–120, else null (blank, decimals and out-of-range refused). */
function parseReviewMonths(raw: string): number | null {
  const trimmed = raw.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const n = Number(trimmed);
  return n >= MIN_REVIEW_MONTHS && n <= MAX_REVIEW_MONTHS ? n : null;
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
  // The raw text of each interval input, so it can be cleared and retyped; validated on blur and
  // on save (never snapped while typing).
  const [reviewMonths, setReviewMonths] = useState<Record<ReviewIntervalKey, string>>({
    low: String(ficaReviewMonthsLow),
    medium: String(ficaReviewMonthsMedium),
    high: String(ficaReviewMonthsHigh),
  });
  const [reviewChecked, setReviewChecked] = useState<Record<ReviewIntervalKey, boolean>>({
    low: false,
    medium: false,
    high: false,
  });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  const reviewInvalid = (key: ReviewIntervalKey) =>
    reviewChecked[key] && parseReviewMonths(reviewMonths[key]) === null;

  async function handleSave() {
    setMessage(null);
    setIsError(false);
    const low = parseReviewMonths(reviewMonths.low);
    const medium = parseReviewMonths(reviewMonths.medium);
    const high = parseReviewMonths(reviewMonths.high);
    if (low === null || medium === null || high === null) {
      // Show every invalid interval's message; nothing is sent.
      setReviewChecked({ low: true, medium: true, high: true });
      return;
    }
    setSaving(true);

    const result = await updateComplianceSettings(slug, {
      dormancyThresholdDays: dormancy,
      dataRequestDeadlineDays: deadline,
      ficaReviewMonthsLow: low,
      ficaReviewMonthsMedium: medium,
      ficaReviewMonthsHigh: high,
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
                inputMode="numeric"
                min={MIN_REVIEW_MONTHS}
                max={MAX_REVIEW_MONTHS}
                step={1}
                value={reviewMonths[key]}
                onChange={(e) => {
                  const value = e.target.value;
                  setReviewMonths((prev) => ({ ...prev, [key]: value }));
                }}
                onBlur={() => setReviewChecked((prev) => ({ ...prev, [key]: true }))}
                aria-invalid={reviewInvalid(key) || undefined}
                aria-describedby={
                  reviewInvalid(key) ? `fica-review-months-${key}-error` : undefined
                }
                className="mt-1 w-full"
              />
              {reviewInvalid(key) && (
                <p
                  id={`fica-review-months-${key}-error`}
                  className="mt-1 text-xs text-red-600 dark:text-red-400"
                  data-testid={`fica-review-months-${key}-error`}
                >
                  {REVIEW_MONTHS_ERROR}
                </p>
              )}
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
