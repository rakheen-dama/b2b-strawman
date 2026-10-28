"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Loader2 } from "lucide-react";
import { updateBatchBillingSettings } from "@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs-actions";
import { paymentTermsDaysSchema } from "@/lib/schemas";

interface BatchBillingSettingsProps {
  slug: string;
  billingBatchAsyncThreshold: number;
  billingEmailRateLimit: number;
  defaultBillingRunCurrency: string | null;
  defaultPaymentTermsDays: number;
}

// Saved through the batch-billing endpoint, which also carries the firm-wide fee-note payment-terms
// default (620A contract) — hence the "Fee note defaults" card here.
export function BatchBillingSettings({
  slug,
  billingBatchAsyncThreshold,
  billingEmailRateLimit,
  defaultBillingRunCurrency,
  defaultPaymentTermsDays,
}: BatchBillingSettingsProps) {
  const [asyncThreshold, setAsyncThreshold] = useState(billingBatchAsyncThreshold);
  const [emailRateLimit, setEmailRateLimit] = useState(billingEmailRateLimit);
  const [currency, setCurrency] = useState(defaultBillingRunCurrency ?? "");
  // Kept as the raw string (not clamped like the two fields below) so out-of-range or cleared input
  // is reported on save instead of silently corrected.
  const [termsDays, setTermsDays] = useState(String(defaultPaymentTermsDays));
  // The field's own error, rendered under it; `message` stays the shared save result.
  const [termsError, setTermsError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);
    setTermsError(null);

    if (!Number.isFinite(asyncThreshold) || asyncThreshold < 1 || asyncThreshold > 1000) {
      setMessage("Async threshold must be between 1 and 1000.");
      setIsError(true);
      setSaving(false);
      return;
    }

    if (!Number.isFinite(emailRateLimit) || emailRateLimit < 1 || emailRateLimit > 100) {
      setMessage("Email rate limit must be between 1 and 100.");
      setIsError(true);
      setSaving(false);
      return;
    }

    const trimmedCurrency = currency.trim();
    if (trimmedCurrency.length > 0 && trimmedCurrency.length !== 3) {
      setMessage("Currency must be exactly 3 characters or empty.");
      setIsError(true);
      setSaving(false);
      return;
    }

    const terms = paymentTermsDaysSchema.safeParse(
      termsDays.trim() === "" ? Number.NaN : Number(termsDays)
    );
    if (!terms.success) {
      setTermsError(
        terms.error.issues[0]?.message ?? "Payment terms must be between 0 and 365 days."
      );
      setSaving(false);
      return;
    }

    try {
      const result = await updateBatchBillingSettings(slug, {
        billingBatchAsyncThreshold: asyncThreshold,
        billingEmailRateLimit: emailRateLimit,
        defaultBillingRunCurrency:
          trimmedCurrency.length === 3 ? trimmedCurrency.toUpperCase() : null,
        defaultPaymentTermsDays: terms.data,
      });

      if (result.success) {
        setMessage("Batch billing settings updated.");
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
          Fee note defaults
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Applies to every fee note you approve, not only batch billing runs.
        </p>

        <div className="mt-4 space-y-4">
          <div className="max-w-xs space-y-2">
            <Label htmlFor="default-payment-terms-days">Default payment terms (days)</Label>
            <Input
              id="default-payment-terms-days"
              type="number"
              inputMode="numeric"
              min={0}
              max={365}
              step={1}
              value={termsDays}
              onChange={(e) => setTermsDays(e.target.value)}
              aria-invalid={termsError ? true : undefined}
              aria-describedby={termsError ? "default-payment-terms-days-error" : undefined}
            />
            {termsError && (
              <p
                id="default-payment-terms-days-error"
                role="alert"
                className="text-sm text-red-600 dark:text-red-400"
              >
                {termsError}
              </p>
            )}
            <p className="text-xs text-slate-500 dark:text-slate-400">
              A fee note approved without a due date is due this many days after its issue date. A
              due date typed on the draft is kept.
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Batch Billing Configuration
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure thresholds and defaults for batch billing runs.
        </p>

        <div className="mt-4 space-y-4">
          <div className="max-w-xs space-y-2">
            <Label htmlFor="async-threshold">Async Threshold</Label>
            <Input
              id="async-threshold"
              type="number"
              min={1}
              max={1000}
              step={1}
              value={asyncThreshold}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                setAsyncThreshold(Number.isFinite(val) ? Math.max(1, Math.min(1000, val)) : 1);
              }}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Client count above which batch generation runs asynchronously
            </p>
          </div>

          <div className="max-w-xs space-y-2">
            <Label htmlFor="email-rate-limit">Email Rate Limit</Label>
            <Input
              id="email-rate-limit"
              type="number"
              min={1}
              max={100}
              step={1}
              value={emailRateLimit}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                setEmailRateLimit(Number.isFinite(val) ? Math.max(1, Math.min(100, val)) : 1);
              }}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Maximum emails per second during batch send
            </p>
          </div>

          <div className="max-w-xs space-y-2">
            <Label htmlFor="default-billing-currency">Default Billing Run Currency</Label>
            <Input
              id="default-billing-currency"
              type="text"
              maxLength={3}
              placeholder="e.g. USD"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Pre-fill currency on new billing runs. Falls back to org default currency if empty.
            </p>
          </div>
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
