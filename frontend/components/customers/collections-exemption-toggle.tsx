"use client";

import { useState } from "react";
import { Switch } from "@/components/ui/switch";
import { Label } from "@b2mash/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { setCollectionsExemptionAction } from "@/app/(app)/org/[slug]/customers/[id]/actions";

interface CollectionsExemptionToggleProps {
  slug: string;
  customerId: string;
  isAdmin: boolean;
  exempt: boolean;
}

export function CollectionsExemptionToggle({
  slug,
  customerId,
  isAdmin,
  exempt,
}: CollectionsExemptionToggleProps) {
  const [isExempt, setIsExempt] = useState(exempt);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleToggle(checked: boolean) {
    setSaving(true);
    setError(null);
    const previous = isExempt;
    setIsExempt(checked);
    const result = await setCollectionsExemptionAction(slug, customerId, checked);
    if (!result.success) {
      setIsExempt(previous); // revert on failure
      setError(result.error ?? "Failed to update collections exemption.");
    }
    setSaving(false);
  }

  return (
    <Card data-testid="collections-exemption-card">
      <CardHeader>
        <CardTitle>Collections</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-center gap-3">
          <Switch
            id="collections-exemption-toggle"
            checked={isExempt}
            disabled={!isAdmin || saving}
            onCheckedChange={handleToggle}
          />
          <Label
            htmlFor="collections-exemption-toggle"
            className="text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Exclude from collections
          </Label>
        </div>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          {isAdmin
            ? "Exempt customers never receive automated payment reminders."
            : "Only admins and owners can change this setting."}
        </p>
        {error && <p className="mt-2 text-sm text-red-600 dark:text-red-400">{error}</p>}
      </CardContent>
    </Card>
  );
}
