import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getCollectionsSettings } from "@/lib/api/collections";
import type { CollectionsSettingsResponse } from "@/lib/api/collections";
import { CollectionsSettingsForm } from "@/components/settings/collections-settings-form";

export default async function CollectionsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Collections</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage collections settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  // Backend supplies sensible defaults when the policy has never been set.
  let settings: CollectionsSettingsResponse = {
    collectionsEnabled: false,
    stage1DaysOverdue: 7,
    stage2DaysOverdue: 21,
    stage3DaysOverdue: 45,
    escalateDaysOverdue: 60,
  };

  const settingsResult = await getCollectionsSettings().catch(() => null);
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
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Collections</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure overdue-invoice reminder stages and escalation for your organization.
        </p>
      </div>

      <CollectionsSettingsForm
        slug={slug}
        collectionsEnabled={settings.collectionsEnabled}
        stage1DaysOverdue={settings.stage1DaysOverdue}
        stage2DaysOverdue={settings.stage2DaysOverdue}
        stage3DaysOverdue={settings.stage3DaysOverdue}
        escalateDaysOverdue={settings.escalateDaysOverdue}
      />
    </div>
  );
}
