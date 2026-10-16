"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@b2mash/ui/label";
import { updatePortalDigestCadence } from "@/app/(app)/org/[slug]/settings/general/portal-actions";
import type { PortalDigestCadence } from "@/lib/types/settings";

const CADENCE_OPTIONS: { value: PortalDigestCadence; label: string }[] = [
  { value: "WEEKLY", label: "Weekly" },
  { value: "BIWEEKLY", label: "Bi-weekly" },
  { value: "OFF", label: "Off" },
];

interface PortalSettingsSectionProps {
  slug: string;
  currentCadence: PortalDigestCadence;
}

export function PortalSettingsSection({ slug, currentCadence }: PortalSettingsSectionProps) {
  const router = useRouter();
  const [cadence, setCadence] = useState<PortalDigestCadence>(currentCadence);
  const [isSavingCadence, setIsSavingCadence] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Auto-dismiss the success banner 3s after it renders so repeated edits
  // don't stack stale "updated" messages against unrelated interactions.
  useEffect(() => {
    if (!statusMessage) return;
    const timer = setTimeout(() => setStatusMessage(null), 3000);
    return () => clearTimeout(timer);
  }, [statusMessage]);

  async function handleCadenceChange(value: string) {
    const next = value as PortalDigestCadence;
    const previous = cadence;
    setCadence(next);
    setIsSavingCadence(true);
    setError(null);
    setStatusMessage(null);
    try {
      const result = await updatePortalDigestCadence(slug, next);
      if (result.success) {
        setStatusMessage("Portal digest cadence updated.");
        router.refresh();
      } else {
        setError(result.error ?? "Failed to update portal digest cadence.");
        setCadence(previous);
      }
    } catch {
      setError("Failed to update portal digest cadence.");
      setCadence(previous);
    } finally {
      setIsSavingCadence(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Client Portal</h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Control how often your clients receive portal digest emails.
      </p>

      <div className="mt-6 max-w-xs space-y-2">
        <Label htmlFor="portal-digest-cadence">Portal digest cadence</Label>
        <Select value={cadence} disabled={isSavingCadence} onValueChange={handleCadenceChange}>
          <SelectTrigger id="portal-digest-cadence" className="w-full">
            <SelectValue placeholder="Select cadence" />
          </SelectTrigger>
          <SelectContent>
            {CADENCE_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          How often clients receive the weekly activity digest email.
        </p>
      </div>

      {statusMessage && (
        <p className="mt-4 text-xs text-teal-600 dark:text-teal-400">{statusMessage}</p>
      )}
      {error && (
        <p className="text-destructive mt-4 text-xs" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
