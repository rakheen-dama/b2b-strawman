"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

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
  taxInclusive: initialInclusive,
}: TaxSettingsFormProps) {
  const [regNumber, setRegNumber] = useState(initialRegNumber);
  const [regLabel, setRegLabel] = useState(initialRegLabel);
  const [taxLabel, setTaxLabel] = useState(initialTaxLabel);
  const [inclusive, setInclusive] = useState(initialInclusive);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSave() {
    setIsSaving(true);
    setMessage(null);
    try {
      const res = await fetch("/api/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          taxRegistrationNumber: regNumber,
          taxRegistrationLabel: regLabel,
          taxLabel,
          taxInclusive: inclusive,
        }),
      });
      if (res.ok) {
        setMessage("Tax settings saved.");
      } else {
        setMessage("Failed to save tax settings.");
      }
    } catch {
      setMessage("Failed to save tax settings.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">
        Tax Configuration
      </h2>
      <p className="mt-1 text-sm text-slate-500">
        Configure tax registration and labeling for invoices.
      </p>

      <div className="mt-6 grid gap-6 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="tax-reg-label">Registration Label</Label>
          <Input
            id="tax-reg-label"
            value={regLabel}
            onChange={(e) => setRegLabel(e.target.value)}
            placeholder="Tax Number"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="tax-reg-number">Registration Number</Label>
          <Input
            id="tax-reg-number"
            value={regNumber}
            onChange={(e) => setRegNumber(e.target.value)}
            placeholder="e.g., 4012345678"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="tax-label">Tax Label</Label>
          <Input
            id="tax-label"
            value={taxLabel}
            onChange={(e) => setTaxLabel(e.target.value)}
            placeholder="e.g., VAT, GST, Tax"
          />
        </div>

        <div className="flex items-center gap-3 pt-6">
          <Switch
            id="tax-inclusive"
            checked={inclusive}
            onCheckedChange={setInclusive}
          />
          <Label htmlFor="tax-inclusive">Prices include tax</Label>
        </div>
      </div>

      <div className="mt-6 flex items-center gap-4">
        <Button onClick={handleSave} disabled={isSaving} size="sm">
          {isSaving ? "Saving..." : "Save tax settings"}
        </Button>
        {message && (
          <p
            className={`text-sm ${
              message.includes("saved")
                ? "text-emerald-600"
                : "text-red-600"
            }`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
