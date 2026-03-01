import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { TimeTrackingSettingsForm } from "@/components/settings/time-tracking-settings-form";
import type { OrgSettings } from "@/lib/types";

export default async function TimeTrackingSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Time Tracking
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage time tracking settings. Only
          admins and owners can access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await api
    .get<OrgSettings>("/api/settings")
    .catch(() => null);
  if (settingsResult) {
    settings = settingsResult;
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Time Tracking
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure time reminders and default expense markup for your
          organization.
        </p>
      </div>

      <TimeTrackingSettingsForm
        slug={slug}
        timeReminderEnabled={settings.timeReminderEnabled ?? false}
        timeReminderDays={settings.timeReminderDays ?? "MON,TUE,WED,THU,FRI"}
        timeReminderTime={settings.timeReminderTime ?? "17:00"}
        timeReminderMinHours={settings.timeReminderMinHours ?? 4.0}
        defaultExpenseMarkupPercent={
          settings.defaultExpenseMarkupPercent ?? null
        }
      />
    </div>
  );
}
