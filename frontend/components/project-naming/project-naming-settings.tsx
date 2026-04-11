"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { updateProjectNamingPattern } from "@/app/(app)/org/[slug]/settings/project-naming/actions";

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
        setMessage("Project naming pattern updated.");
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
    <div className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Naming Pattern</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          When set, new projects will be automatically named using this pattern. Leave empty to use
          freeform project names.
        </p>
        <div className="mt-4 max-w-lg space-y-2">
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
        <div className="mt-4 rounded-md border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Available placeholders
          </p>
          <ul className="mt-2 space-y-1 text-sm text-slate-600 dark:text-slate-400">
            <li>
              <code className="rounded bg-slate-200 px-1 py-0.5 font-mono text-xs dark:bg-slate-800">
                {"{name}"}
              </code>{" "}
              — The project name entered at creation
            </li>
            <li>
              <code className="rounded bg-slate-200 px-1 py-0.5 font-mono text-xs dark:bg-slate-800">
                {"{customer.name}"}
              </code>{" "}
              — The linked customer&apos;s name
            </li>
            <li>
              <code className="rounded bg-slate-200 px-1 py-0.5 font-mono text-xs dark:bg-slate-800">
                {"{field_slug}"}
              </code>{" "}
              — Any custom field slug (e.g.,{" "}
              <code className="rounded bg-slate-200 px-1 py-0.5 font-mono text-xs dark:bg-slate-800">
                {"{reference_number}"}
              </code>
              )
            </li>
          </ul>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <Button size="sm" disabled={saving} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
        {message && (
          <p
            className={`text-sm ${
              isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"
            }`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
