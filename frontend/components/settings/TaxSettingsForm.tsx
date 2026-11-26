"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Switch } from "@/components/ui/switch";
import { Loader2 } from "lucide-react";
import { SettingsSection } from "@/components/settings/settings-section";
import { updateTaxSettings } from "@/app/(app)/org/[slug]/settings/fees/_tabs/tax-actions";

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
    <SettingsSection
      title="Tax Configuration"
      description="Configure your organization's tax registration and display settings."
      status={
        message && (
          <p className={isError ? "text-destructive" : "text-success-foreground"}>{message}</p>
        )
      }
      footer={
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="tax-registration-number"
            className="text-foreground block text-sm font-medium"
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
            className="text-foreground block text-sm font-medium"
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
          <label htmlFor="tax-label" className="text-foreground block text-sm font-medium">
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
          <p className="text-muted-foreground mt-1 text-xs">
            Label shown on fee notes and documents (e.g. &quot;VAT&quot;, &quot;GST&quot;).
          </p>
        </div>
        <div className="flex items-center gap-3 self-center">
          <Switch id="tax-inclusive" checked={taxInclusive} onCheckedChange={setTaxInclusive} />
          <label htmlFor="tax-inclusive" className="text-foreground text-sm font-medium">
            Tax-inclusive pricing
          </label>
        </div>
      </div>
    </SettingsSection>
  );
}
