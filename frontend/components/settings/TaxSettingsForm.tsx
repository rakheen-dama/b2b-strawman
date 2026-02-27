"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Loader2 } from "lucide-react";
import { updateTaxSettings } from "@/app/(app)/org/[slug]/settings/tax/actions";

interface TaxSettingsFormProps {
  slug: string;
  taxRegistrationNumber: string;
  taxRegistrationLabel: string;
  taxLabel: string;
  taxInclusive: boolean;
}

export function TaxSettingsForm({
  slug,
  taxRegistrationNumber: initialRegNumber,
  taxRegistrationLabel: initialRegLabel,
  taxLabel: initialTaxLabel,
  taxInclusive: initialTaxInclusive,
}: TaxSettingsFormProps) {
  const [regNumber, setRegNumber] = useState(initialRegNumber);
  const [regLabel, setRegLabel] = useState(initialRegLabel);
  const [taxLabel, setTaxLabel] = useState(initialTaxLabel);
  const [taxInclusive, setTaxInclusive] = useState(initialTaxInclusive);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);
    const result = await updateTaxSettings(slug, {
      taxRegistrationNumber: regNumber || undefined,
      taxRegistrationLabel: regLabel || undefined,
      taxLabel: taxLabel || undefined,
      taxInclusive,
    });
    if (result.success) {
      setMessage("Tax settings updated.");
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
        Tax Configuration
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Configure your organization&apos;s tax registration and display
        settings.
      </p>
      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="tax-registration-number"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Tax Registration Number
          </label>
          <Input
            id="tax-registration-number"
            type="text"
            maxLength={50}
            placeholder="e.g. VAT-123456789"
            value={regNumber}
            onChange={(e) => setRegNumber(e.target.value)}
            className="mt-1 w-full"
          />
        </div>
        <div>
          <label
            htmlFor="tax-registration-label"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Registration Label
          </label>
          <Input
            id="tax-registration-label"
            type="text"
            maxLength={30}
            placeholder="e.g. VAT Number"
            value={regLabel}
            onChange={(e) => setRegLabel(e.target.value)}
            className="mt-1 w-full"
          />
        </div>
        <div>
          <label
            htmlFor="tax-label"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Tax Label
          </label>
          <Input
            id="tax-label"
            type="text"
            maxLength={20}
            placeholder="e.g. VAT, GST, Tax"
            value={taxLabel}
            onChange={(e) => setTaxLabel(e.target.value)}
            className="mt-1 w-full"
          />
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Label shown on invoices and documents (e.g. &quot;VAT&quot;,
            &quot;GST&quot;).
          </p>
        </div>
        <div className="flex items-center gap-3 self-center">
          <Switch
            id="tax-inclusive"
            checked={taxInclusive}
            onCheckedChange={setTaxInclusive}
          />
          <label
            htmlFor="tax-inclusive"
            className="text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Tax-inclusive pricing
          </label>
        </div>
      </div>
      <div className="mt-4 flex items-center gap-3">
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
        {message && (
          <p
            className={`text-sm ${isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
