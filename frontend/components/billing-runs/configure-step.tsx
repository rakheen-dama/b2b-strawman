"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { createBillingRunAction } from "@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-run-actions";

interface ConfigureStepProps {
  slug: string;
  onNext: (billingRunId: string, currency: string, includeRetainers?: boolean) => void;
}

export function ConfigureStep({ slug, onNext }: ConfigureStepProps) {
  const [periodFrom, setPeriodFrom] = useState("");
  const [periodTo, setPeriodTo] = useState("");
  const [cutOffDate, setCutOffDate] = useState("");
  const [includeRetainers, setIncludeRetainers] = useState(false);
  const [notes, setNotes] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);

  function validate(): boolean {
    const errors: string[] = [];
    if (!periodFrom) errors.push("Period From is required.");
    if (!periodTo) errors.push("Period To is required.");
    if (periodFrom && periodTo && periodFrom >= periodTo) {
      errors.push("Period From must be before Period To.");
    }
    setValidationErrors(errors);
    return errors.length === 0;
  }

  async function handleSubmit() {
    if (!validate()) return;

    setError(null);
    setIsSubmitting(true);
    try {
      // TODO: Read org's default currency from org settings when available
      const result = await createBillingRunAction(slug, {
        periodFrom,
        periodTo,
        currency: "ZAR",
        includeExpenses: true,
        includeRetainers,
        cutOffDate: cutOffDate || undefined,
        notes: notes || undefined,
      });
      if (result.success && result.billingRun) {
        onNext(
          result.billingRun.id,
          result.billingRun.currency,
          result.billingRun.includeRetainers
        );
      } else {
        setError(result.error ?? "Failed to create billing run.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="mb-6 text-lg font-semibold text-slate-950 dark:text-slate-50">
        Configure Billing Run
      </h2>

      <div className="space-y-5">
        {/* Billing Period */}
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="periodFrom">Period From</Label>
            <Input
              id="periodFrom"
              type="date"
              value={periodFrom}
              onChange={(e) => setPeriodFrom(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="periodTo">Period To</Label>
            <Input
              id="periodTo"
              type="date"
              value={periodTo}
              onChange={(e) => setPeriodTo(e.target.value)}
            />
          </div>
        </div>

        {/* Cut-off Date */}
        <div className="space-y-2">
          <Label htmlFor="cutOffDate">Cut-off Date (optional)</Label>
          <Input
            id="cutOffDate"
            type="date"
            value={cutOffDate}
            onChange={(e) => setCutOffDate(e.target.value)}
            className="max-w-xs"
          />
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Only include time entries logged up to this date.
          </p>
        </div>

        {/* Include Retainers */}
        <div className="flex items-center gap-2">
          <Checkbox
            id="includeRetainers"
            checked={includeRetainers}
            onCheckedChange={(checked) => setIncludeRetainers(checked === true)}
          />
          <Label htmlFor="includeRetainers" className="cursor-pointer">
            Include retainers
          </Label>
        </div>

        {/* Notes */}
        <div className="space-y-2">
          <Label htmlFor="notes">Notes (optional)</Label>
          <Textarea
            id="notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Internal notes about this billing run..."
            rows={3}
          />
        </div>
      </div>

      {/* Validation Errors */}
      {validationErrors.length > 0 && (
        <div className="mt-4 rounded-md bg-red-50 p-3 dark:bg-red-950">
          <ul className="list-inside list-disc text-sm text-red-600 dark:text-red-400">
            {validationErrors.map((err) => (
              <li key={err}>{err}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Server Error */}
      {error && (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {error}
        </p>
      )}

      {/* Actions */}
      <div className="mt-6 flex justify-end">
        <Button variant="default" onClick={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? "Creating..." : "Next"}
        </Button>
      </div>
    </div>
  );
}
