"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Loader2 } from "lucide-react";
import { updateMatterRetentionYears } from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";
import { matterRetentionYearsSchema } from "@/lib/schemas";

interface MatterRetentionSettingsFormProps {
  slug: string;
  legalMatterRetentionYears: number;
  /** The setting is owner-only on the backend; admins see it read-only. */
  canEdit: boolean;
}

/**
 * Firm › Compliance "Matter retention period (years)" (Phase 88 U17, ADR-354). A closed matter's
 * scheduled deletion date is stamped at close as close date + this period, so a change applies to
 * matters closed from then on. Saved on its own because it goes through the owner-only
 * data-protection endpoint, not the compliance settings endpoint admins may write.
 */
export function MatterRetentionSettingsForm({
  slug,
  legalMatterRetentionYears,
  canEdit,
}: MatterRetentionSettingsFormProps) {
  // Kept as the raw string so out-of-range or cleared input is reported on save instead of
  // silently corrected.
  const [years, setYears] = useState(String(legalMatterRetentionYears));
  const [yearsError, setYearsError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setMessage(null);
    setIsError(false);
    setYearsError(null);

    const parsed = matterRetentionYearsSchema.safeParse(
      years.trim() === "" ? Number.NaN : Number(years)
    );
    if (!parsed.success) {
      setYearsError(
        parsed.error.issues[0]?.message ?? "Retention period must be between 1 and 30 years."
      );
      return;
    }

    setSaving(true);
    try {
      const result = await updateMatterRetentionYears(slug, parsed.data);
      if (result.success) {
        setMessage("Matter retention period updated.");
      } else {
        setMessage(result.error ?? "Failed to update the matter retention period.");
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
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Matter retention</h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        How long closed matters are kept before they are permanently deleted.
      </p>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="matter-retention-years"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Matter retention period (years)
          </label>
          <Input
            id="matter-retention-years"
            type="number"
            inputMode="numeric"
            min={1}
            max={30}
            step={1}
            value={years}
            disabled={!canEdit}
            onChange={(e) => setYears(e.target.value)}
            aria-invalid={yearsError ? true : undefined}
            aria-describedby={yearsError ? "matter-retention-years-error" : undefined}
            className="mt-1 w-full"
          />
          {yearsError && (
            <p
              id="matter-retention-years-error"
              role="alert"
              className="mt-1 text-sm text-red-600 dark:text-red-400"
            >
              {yearsError}
            </p>
          )}
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Closed matters are permanently deleted this many years after the close date. Default 5
            years. A change applies to matters closed from now on — matters already closed keep
            their scheduled deletion date.
          </p>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <Button size="sm" disabled={!canEdit || saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save retention period
        </Button>
        {!canEdit && (
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Only the firm owner can change the matter retention period.
          </p>
        )}
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
