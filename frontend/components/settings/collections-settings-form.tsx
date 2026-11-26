"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Switch } from "@/components/ui/switch";
import { Loader2 } from "lucide-react";
import { SettingsSection } from "@/components/settings/settings-section";
import { updateCollectionsSettings } from "@/app/(app)/org/[slug]/settings/fees/_tabs/collections-actions";

interface CollectionsSettingsFormProps {
  slug: string;
  collectionsEnabled: boolean;
  stage1DaysOverdue: number;
  stage2DaysOverdue: number;
  stage3DaysOverdue: number;
  escalateDaysOverdue: number;
}

function parseThreshold(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === "" || !/^\d+$/.test(trimmed)) {
    return null;
  }
  return Number.parseInt(trimmed, 10);
}

export function CollectionsSettingsForm({
  slug,
  collectionsEnabled,
  stage1DaysOverdue,
  stage2DaysOverdue,
  stage3DaysOverdue,
  escalateDaysOverdue,
}: CollectionsSettingsFormProps) {
  const [enabled, setEnabled] = useState(collectionsEnabled);
  const [stage1, setStage1] = useState(String(stage1DaysOverdue));
  const [stage2, setStage2] = useState(String(stage2DaysOverdue));
  const [stage3, setStage3] = useState(String(stage3DaysOverdue));
  const [escalate, setEscalate] = useState(String(escalateDaysOverdue));
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    const parsed1 = parseThreshold(stage1);
    const parsed2 = parseThreshold(stage2);
    const parsed3 = parseThreshold(stage3);
    const parsedEscalate = parseThreshold(escalate);

    // Mirror the server rule: each threshold must be a whole number ≥ 1.
    if (
      parsed1 === null ||
      parsed2 === null ||
      parsed3 === null ||
      parsedEscalate === null ||
      parsed1 < 1 ||
      parsed2 < 1 ||
      parsed3 < 1 ||
      parsedEscalate < 1
    ) {
      setMessage("Each threshold must be a whole number of at least 1 day.");
      setIsError(true);
      setSaving(false);
      return;
    }

    // Mirror the server rule: strictly increasing stage1 < stage2 < stage3 < escalate.
    if (!(parsed1 < parsed2 && parsed2 < parsed3 && parsed3 < parsedEscalate)) {
      setMessage("Thresholds must strictly increase: stage 1 < stage 2 < stage 3 < escalation.");
      setIsError(true);
      setSaving(false);
      return;
    }

    try {
      const result = await updateCollectionsSettings(slug, {
        collectionsEnabled: enabled,
        stage1DaysOverdue: parsed1,
        stage2DaysOverdue: parsed2,
        stage3DaysOverdue: parsed3,
        escalateDaysOverdue: parsedEscalate,
      });

      if (result.success) {
        setMessage("Collections settings updated.");
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
    <SettingsSection
      title="Overdue fee-note reminders"
      description="Configure when overdue fee notes move through reminder stages and escalate for collections."
      status={
        message && (
          <p
            role="alert"
            aria-live="polite"
            className={isError ? "text-destructive" : "text-success-foreground"}
          >
            {message}
          </p>
        )
      }
      footer={
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
      }
    >
      <div className="space-y-4">
        {/* Enable toggle */}
        <div className="flex items-center gap-3">
          <Switch
            id="collections-toggle"
            checked={enabled}
            onCheckedChange={(checked: boolean) => setEnabled(checked)}
          />
          <Label htmlFor="collections-toggle" className="text-foreground text-sm font-medium">
            Enable collections reminders
          </Label>
        </div>

        {/* Stage 1 */}
        <div>
          <Label htmlFor="stage1-days" className="text-foreground block text-sm font-medium">
            Stage 1 (days overdue)
          </Label>
          <Input
            id="stage1-days"
            type="number"
            min={1}
            step={1}
            value={stage1}
            onChange={(e) => setStage1(e.target.value)}
            className="mt-1 w-40"
          />
        </div>

        {/* Stage 2 */}
        <div>
          <Label htmlFor="stage2-days" className="text-foreground block text-sm font-medium">
            Stage 2 (days overdue)
          </Label>
          <Input
            id="stage2-days"
            type="number"
            min={1}
            step={1}
            value={stage2}
            onChange={(e) => setStage2(e.target.value)}
            className="mt-1 w-40"
          />
        </div>

        {/* Stage 3 */}
        <div>
          <Label htmlFor="stage3-days" className="text-foreground block text-sm font-medium">
            Stage 3 (days overdue)
          </Label>
          <Input
            id="stage3-days"
            type="number"
            min={1}
            step={1}
            value={stage3}
            onChange={(e) => setStage3(e.target.value)}
            className="mt-1 w-40"
          />
        </div>

        {/* Escalation */}
        <div>
          <Label htmlFor="escalate-days" className="text-foreground block text-sm font-medium">
            Escalation (days overdue)
          </Label>
          <Input
            id="escalate-days"
            type="number"
            min={1}
            step={1}
            value={escalate}
            onChange={(e) => setEscalate(e.target.value)}
            className="mt-1 w-40"
          />
          <p className="text-muted-foreground mt-1 text-xs">
            Thresholds must strictly increase from stage 1 through escalation.
          </p>
        </div>
      </div>
    </SettingsSection>
  );
}
