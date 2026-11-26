import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { AcceptanceSettingsForm } from "@/components/acceptance/AcceptanceSettingsForm";
import type { OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Documents › Acceptance tab (Phase 87 S9, ADR-341) — the former acceptance settings page's loader. */
export async function renderAcceptanceTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Acceptance" />
        <p className="text-muted-foreground">
          You do not have permission to manage acceptance settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <PageHeader
        title="Acceptance"
        description="Configure default expiry period for document acceptance requests."
      />

      <AcceptanceSettingsForm
        slug={slug}
        acceptanceExpiryDays={settings.acceptanceExpiryDays ?? 30}
      />
    </div>
  );
}
