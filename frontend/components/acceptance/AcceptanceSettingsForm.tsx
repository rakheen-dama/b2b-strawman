"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Loader2 } from "lucide-react";
import { SettingsSection } from "@/components/settings/settings-section";
import { updateAcceptanceSettings } from "@/app/(app)/org/[slug]/settings/documents/_tabs/acceptance-actions";

interface AcceptanceSettingsFormProps {
  slug: string;
  acceptanceExpiryDays: number;
}

export function AcceptanceSettingsForm({
  slug,
  acceptanceExpiryDays,
}: AcceptanceSettingsFormProps) {
  const [expiryDays, setExpiryDays] = useState(acceptanceExpiryDays);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    const result = await updateAcceptanceSettings(slug, expiryDays);

    if (result.success) {
      setMessage("Acceptance settings updated.");
      setIsError(false);
    } else {
      setMessage(result.error ?? "Failed to update settings.");
      setIsError(true);
    }
    setSaving(false);
  }

  return (
    <SettingsSection
      title="Document Acceptance"
      description="Configure default settings for document acceptance requests."
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
            htmlFor="acceptance-expiry-days"
            className="text-foreground block text-sm font-medium"
          >
            Default Expiry Period (days)
          </label>
          <Input
            id="acceptance-expiry-days"
            type="number"
            min={1}
            max={365}
            placeholder="30"
            value={expiryDays}
            onChange={(e) =>
              setExpiryDays(Math.max(1, Math.min(365, parseInt(e.target.value) || 1)))
            }
            className="mt-1 w-full"
          />
          <p className="text-muted-foreground mt-1 text-xs">
            Number of days before an acceptance request expires. Applies to all new requests.
          </p>
        </div>
      </div>
    </SettingsSection>
  );
}
