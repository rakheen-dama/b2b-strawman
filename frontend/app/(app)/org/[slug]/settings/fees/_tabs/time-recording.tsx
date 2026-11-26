import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { TimeTrackingSettingsForm } from "@/components/settings/time-tracking-settings-form";
import type { OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Fees & Trust › Time recording tab (Phase 87 S9, ADR-341) — the former time-tracking settings page's loader. */
export async function renderTimeRecordingTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Time recording" />
        <p className="text-muted-foreground">
          You do not have permission to manage time tracking settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <PageHeader
        title="Time recording"
        description="Configure time reminders and default expense markup for your organization."
      />

      <TimeTrackingSettingsForm
        slug={slug}
        timeReminderEnabled={settings.timeReminderEnabled ?? false}
        timeReminderDays={settings.timeReminderDays ?? "MON,TUE,WED,THU,FRI"}
        timeReminderTime={settings.timeReminderTime ?? "17:00"}
        timeReminderMinHours={settings.timeReminderMinHours ?? 4.0}
        defaultExpenseMarkupPercent={settings.defaultExpenseMarkupPercent ?? null}
      />
    </div>
  );
}
