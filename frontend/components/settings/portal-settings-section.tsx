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
import { Label } from "@/components/ui/label";
import {
  updatePortalDigestCadence,
  updatePortalRetainerMemberDisplay,
} from "@/app/(app)/org/[slug]/settings/general/portal-actions";
import type {
  PortalDigestCadence,
  PortalRetainerMemberDisplay,
} from "@/lib/types/settings";

const CADENCE_OPTIONS: { value: PortalDigestCadence; label: string }[] = [
  { value: "WEEKLY", label: "Weekly" },
  { value: "BIWEEKLY", label: "Bi-weekly" },
  { value: "OFF", label: "Off" },
];

const MEMBER_DISPLAY_OPTIONS: {
  value: PortalRetainerMemberDisplay;
  label: string;
  description: string;
}[] = [
  {
    value: "FULL_NAME",
    label: "Full name",
    description: "Show each member's full name (e.g. Alice Ndlovu).",
  },
  {
    value: "FIRST_NAME_ROLE",
    label: "First name + role",
    description: "Show first name with role (e.g. Alice (Attorney)).",
  },
  {
    value: "ROLE_ONLY",
    label: "Role only",
    description: "Show role only (e.g. Attorney).",
  },
  {
    value: "ANONYMISED",
    label: "Anonymised",
    description: "Show a generic label (e.g. Team member).",
  },
];

interface PortalSettingsSectionProps {
  slug: string;
  currentCadence: PortalDigestCadence;
  currentMemberDisplay: PortalRetainerMemberDisplay;
}

export function PortalSettingsSection({
  slug,
  currentCadence,
  currentMemberDisplay,
}: PortalSettingsSectionProps) {
  const router = useRouter();
  const [cadence, setCadence] = useState<PortalDigestCadence>(currentCadence);
  const [memberDisplay, setMemberDisplay] =
    useState<PortalRetainerMemberDisplay>(currentMemberDisplay);
  const [isSavingCadence, setIsSavingCadence] = useState(false);
  const [isSavingMemberDisplay, setIsSavingMemberDisplay] = useState(false);
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

  async function handleMemberDisplayChange(value: string) {
    const next = value as PortalRetainerMemberDisplay;
    const previous = memberDisplay;
    setMemberDisplay(next);
    setIsSavingMemberDisplay(true);
    setError(null);
    setStatusMessage(null);
    try {
      const result = await updatePortalRetainerMemberDisplay(slug, next);
      if (result.success) {
        setStatusMessage("Portal retainer member display updated.");
        router.refresh();
      } else {
        setError(
          result.error ?? "Failed to update portal retainer member display.",
        );
        setMemberDisplay(previous);
      }
    } catch {
      setError("Failed to update portal retainer member display.");
      setMemberDisplay(previous);
    } finally {
      setIsSavingMemberDisplay(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Client Portal
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Control how often your clients receive portal digest emails and how
        team members are displayed on client retainer pages.
      </p>

      <div className="mt-6 grid gap-6 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="portal-digest-cadence">Portal digest cadence</Label>
          <Select
            value={cadence}
            disabled={isSavingCadence}
            onValueChange={handleCadenceChange}
          >
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

        <div className="space-y-2">
          <Label htmlFor="portal-retainer-member-display">
            Portal retainer member display
          </Label>
          <Select
            value={memberDisplay}
            disabled={isSavingMemberDisplay}
            onValueChange={handleMemberDisplayChange}
          >
            <SelectTrigger
              id="portal-retainer-member-display"
              className="w-full"
            >
              <SelectValue placeholder="Select display mode" />
            </SelectTrigger>
            <SelectContent>
              {MEMBER_DISPLAY_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            How team-member names appear on client retainer usage pages.
          </p>
        </div>
      </div>

      {statusMessage && (
        <p className="mt-4 text-xs text-teal-600 dark:text-teal-400">
          {statusMessage}
        </p>
      )}
      {error && (
        <p className="text-destructive mt-4 text-xs" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
