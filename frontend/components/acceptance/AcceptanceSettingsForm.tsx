"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Loader2 } from "lucide-react";
import { updateAcceptanceSettings } from "@/app/(app)/org/[slug]/settings/acceptance/actions";

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
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Document Acceptance
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Configure default settings for document acceptance requests.
      </p>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="acceptance-expiry-days"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
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
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Number of days before an acceptance request expires. Applies to all
            new requests.
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
