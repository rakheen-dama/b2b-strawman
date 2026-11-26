"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Loader2 } from "lucide-react";
import { SettingsSection } from "@/components/settings/settings-section";
import { updateProjectNamingPattern } from "@/app/(app)/org/[slug]/settings/matters/_tabs/naming-actions";

interface ProjectNamingSettingsProps {
  slug: string;
  projectNamingPattern: string;
}

export function ProjectNamingSettings({
  slug,
  projectNamingPattern: initialPattern,
}: ProjectNamingSettingsProps) {
  const [pattern, setPattern] = useState(initialPattern);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  async function handleSave() {
    setSaving(true);
    setMessage(null);
    setIsError(false);

    if (pattern.length > 500) {
      setMessage("Pattern must be at most 500 characters.");
      setIsError(true);
      setSaving(false);
      return;
    }

    try {
      const result = await updateProjectNamingPattern(slug, pattern || null);

      if (result.success) {
        setMessage("Matter naming pattern updated.");
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
      title="Naming Pattern"
      description="When set, new matters will be automatically named using this pattern. Leave empty to use freeform matter names."
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
      <div className="max-w-lg space-y-2">
        <Label htmlFor="project-naming-pattern">Pattern</Label>
        <Input
          id="project-naming-pattern"
          type="text"
          maxLength={500}
          placeholder="{reference_number} - {customer.name} - {name}"
          value={pattern}
          onChange={(e) => setPattern(e.target.value)}
        />
      </div>
      <div className="border-border bg-muted mt-4 rounded-md border p-4">
        <p className="text-foreground text-sm font-medium">Available placeholders</p>
        <ul className="text-muted-foreground mt-2 space-y-1 text-sm">
          <li>
            <code className="bg-background rounded px-1 py-0.5 font-mono text-xs">{"{name}"}</code>{" "}
            — The matter name entered at creation
          </li>
          <li>
            <code className="bg-background rounded px-1 py-0.5 font-mono text-xs">
              {"{customer.name}"}
            </code>{" "}
            — The linked client&apos;s name
          </li>
          <li>
            <code className="bg-background rounded px-1 py-0.5 font-mono text-xs">
              {"{field_slug}"}
            </code>{" "}
            — Any custom field slug (e.g.,{" "}
            <code className="bg-background rounded px-1 py-0.5 font-mono text-xs">
              {"{reference_number}"}
            </code>
            )
          </li>
        </ul>
      </div>
    </SettingsSection>
  );
}
