import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { RequestReminderSettingsForm } from "@/components/information-requests/request-reminder-settings-form";
import type { OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Request settings tab (Phase 87 S9, ADR-341) — the former request-settings settings page's loader. */
export async function renderRequestSettingsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Request Settings" />
        <p className="text-muted-foreground">
          You do not have permission to manage request settings. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <PageHeader
        title="Request Settings"
        description="Configure default reminder interval for information requests."
      />

      <RequestReminderSettingsForm
        slug={slug}
        defaultRequestReminderDays={settings.defaultRequestReminderDays ?? 7}
      />
    </div>
  );
}
