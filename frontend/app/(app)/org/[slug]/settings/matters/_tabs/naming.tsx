import { PageHeader } from "@b2mash/ui/page-header";
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
        <PageHeader title="Matter Naming" />
        <p className="text-muted-foreground">
          You do not have permission to manage matter naming settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <PageHeader
        title="Matter Naming"
        description="Configure an auto-naming pattern for new matters."
      />

      <ProjectNamingSettings
        slug={slug}
        projectNamingPattern={settings.matterNamingPattern ?? ""}
      />
    </div>
  );
}
