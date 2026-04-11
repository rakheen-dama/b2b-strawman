"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { updateModuleSettings, type ModuleStatus } from "@/lib/actions/module-settings";

interface FeaturesSettingsFormProps {
  initialModules: ModuleStatus[];
  canManage: boolean;
}

export function FeaturesSettingsForm({ initialModules, canManage }: FeaturesSettingsFormProps) {
  const router = useRouter();
  const [modules, setModules] = useState<ModuleStatus[]>(initialModules);
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  function handleToggle(moduleId: string, checked: boolean) {
    setError(null);
    const previous = modules;
    const next = modules.map((m) => (m.id === moduleId ? { ...m, enabled: checked } : m));
    setModules(next);
    const enabledIds = next.filter((m) => m.enabled).map((m) => m.id);

    startTransition(async () => {
      const result = await updateModuleSettings(enabledIds);
      if (!result.success) {
        // Revert on failure
        setModules(previous);
        setError(result.error ?? "Failed to update features.");
        return;
      }
      if (result.data) {
        setModules(result.data.modules);
      }
      router.refresh();
    });
  }

  if (modules.length === 0) {
    return <p className="text-sm text-slate-600 dark:text-slate-400">No features available.</p>;
  }

  return (
    <div className="space-y-4">
      {modules.map((mod) => (
        <Card key={mod.id} data-testid={`feature-card-${mod.id}`}>
          <CardContent className="flex items-start justify-between gap-4 p-4">
            <div className="flex-1 space-y-1">
              <Label
                htmlFor={`feature-switch-${mod.id}`}
                className="font-semibold text-slate-950 dark:text-slate-50"
              >
                {mod.name}
              </Label>
              <p className="text-sm text-slate-600 dark:text-slate-400">{mod.description}</p>
            </div>
            {canManage ? (
              <Switch
                id={`feature-switch-${mod.id}`}
                checked={mod.enabled}
                onCheckedChange={(checked) => handleToggle(mod.id, checked)}
                disabled={isPending}
                aria-label={`Toggle ${mod.name}`}
              />
            ) : (
              <span
                className="text-sm text-slate-500 dark:text-slate-400"
                data-testid={`feature-status-${mod.id}`}
              >
                {mod.enabled ? "Enabled" : "Disabled"}
              </span>
            )}
          </CardContent>
        </Card>
      ))}
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
