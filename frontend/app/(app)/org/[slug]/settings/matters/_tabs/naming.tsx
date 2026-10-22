import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { ProjectNamingSettings } from "@/components/project-naming/project-naming-settings";
import type { OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Naming tab (Phase 87 S9, ADR-341) — the former project-naming settings page's loader. */
export async function renderNamingTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Matter Naming</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage matter naming settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Matter Naming</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure an auto-naming pattern for new matters.
        </p>
      </div>

      <ProjectNamingSettings
        slug={slug}
        projectNamingPattern={settings.matterNamingPattern ?? ""}
      />
    </div>
  );
}
