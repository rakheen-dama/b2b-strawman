import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getCollectionsSettings } from "@/lib/api/collections";
import { CollectionsSettingsForm } from "@/components/settings/collections-settings-form";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Fees & Trust › Collections tab (Phase 87 S9, ADR-341) — the former collections settings page's loader. */
export async function renderCollectionsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Collections</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage collections settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  // Backend supplies sensible defaults when the policy has never been set (never 404s),
  // so a failure here is a real fetch error — let it propagate to the error boundary
  // rather than silently rendering defaults an admin could save over an existing policy.
  const settings = await getCollectionsSettings();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Collections</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure overdue fee-note reminder stages and escalation for your organization.
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
