import type { Metadata } from "next";
import type { ReactNode } from "react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { SettingsFamilyTabs } from "@/components/settings/settings-family-tabs";
import {
  getSettingsFamily,
  resolveSettingsTab,
  settingsAccessFrom,
  visibleSettingsTabs,
  type SettingsAccess,
} from "@/lib/settings-nav";
import type { FirmTabProps, SettingsSearchParams } from "./_tabs/types";
import { renderGeneralTab } from "./_tabs/general";
import { renderRolesTab } from "./_tabs/roles";
import { renderAuditTrailTab } from "./_tabs/audit-trail";
import { renderDataProtectionTab } from "./_tabs/data-protection";
import { renderDataRequestsTab } from "./_tabs/data-requests";
import { renderComplianceTab } from "./_tabs/compliance";
import { renderFeaturesTab } from "./_tabs/features";
import { renderSubscriptionTab } from "./_tabs/subscription";

const FIRM = getSettingsFamily("firm");

/**
 * Firm settings family (Phase 87 S9, ADR-341 §12.7): eight former pages as `?tab=` tabs. Only the
 * active tab's loader runs — each former page's loader is now a tab loader in `_tabs/`.
 */
const TAB_LOADERS: Record<string, (props: FirmTabProps) => Promise<ReactNode>> = {
  general: renderGeneralTab,
  roles: renderRolesTab,
  "audit-trail": renderAuditTrailTab,
  "data-protection": renderDataProtectionTab,
  "data-requests": renderDataRequestsTab,
  compliance: renderComplianceTab,
  features: renderFeaturesTab,
  subscription: renderSubscriptionTab,
};

interface FirmSettingsPageProps {
  params: Promise<{ slug: string }>;
  searchParams: Promise<SettingsSearchParams>;
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

async function loadAccess(): Promise<SettingsAccess> {
  const [caps, settings] = await Promise.all([
    fetchMyCapabilities(),
    // Module gates only (Firm declares none today). A failure here must not mask a tab's own
    // GET /api/settings failure, which still propagates from the General loader.
    getOrgSettings().catch(() => null),
  ]);
  return settingsAccessFrom(caps, settings?.enabledModules ?? []);
}

export async function generateMetadata({ searchParams }: FirmSettingsPageProps): Promise<Metadata> {
  const sp = await searchParams;
  const { tab } = resolveSettingsTab(FIRM, firstValue(sp.tab), await loadAccess());
  return { title: `${FIRM.label} — ${tab.label} · Settings` };
}

export default async function FirmSettingsPage({ params, searchParams }: FirmSettingsPageProps) {
  const { slug } = await params;
  const sp = await searchParams;
  const access = await loadAccess();
  const { tab, forbidden } = resolveSettingsTab(FIRM, firstValue(sp.tab), access);
  const panel = await TAB_LOADERS[tab.id]({ slug, searchParams: sp });

  return (
    <SettingsFamilyTabs
      label={`${FIRM.label} settings`}
      tabs={visibleSettingsTabs(FIRM, access).map(({ id, label }) => ({ id, label }))}
      activeTab={tab.id}
    >
      {forbidden && (
        <p
          role="status"
          className="mb-6 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
        >
          You don&apos;t have access to {forbidden.label} — showing {tab.label} instead.
        </p>
      )}
      {panel}
    </SettingsFamilyTabs>
  );
}
