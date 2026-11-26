import { PageHeader } from "@b2mash/ui/page-header";
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
        <PageHeader title="Collections" />
        <p className="text-muted-foreground">
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
      <PageHeader
        title="Collections"
        description="Configure overdue fee-note reminder stages and escalation for your organization."
      />

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
