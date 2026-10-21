import { cache, type ReactNode } from "react";
import type { Metadata } from "next";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { SettingsFamilyTabs } from "@/components/settings/settings-family-tabs";
import {
  getSettingsFamily,
  resolveSettingsTab,
  settingsAccessFrom,
  visibleSettingsTabs,
  type SettingsAccess,
  type SettingsFamilyId,
  type SettingsTabId,
} from "@/lib/settings-nav";

/**
 * Shared server shell of a settings family page (Phase 87 S9, ADR-341 §12.7.4) — Firm (616A),
 * Matters, Documents, Fees & Trust and Connections (616B–616D). A family's page.tsx only declares
 * its loader map: tab gating and `?tab=` resolution happen here, on the server, and only the
 * active tab's loader runs.
 */

export type SettingsSearchParams = Record<string, string | string[] | undefined>;

/**
 * Props every tab loader receives; `searchParams` is the page's full query (e.g. the audit trail
 * reads its filters from it).
 */
export interface SettingsTabProps {
  slug: string;
  searchParams: SettingsSearchParams;
}

export type SettingsTabLoader = (props: SettingsTabProps) => Promise<ReactNode>;

/** One loader per tab id of family F — a missing or extra tab id is a compile error. */
export type SettingsTabLoaders<F extends SettingsFamilyId> = Record<
  SettingsTabId<F>,
  SettingsTabLoader
>;

/** The route props Next.js hands a family page and its generateMetadata. */
export interface SettingsFamilyRouteProps {
  params: Promise<{ slug: string }>;
  searchParams: Promise<SettingsSearchParams>;
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * Request-scoped (React `cache`): generateMetadata and the page share one resolution. Org settings
 * are fetched only when the family has a module-gated tab; a failure there must not mask a tab's
 * own GET /api/settings failure, which still propagates from that tab's loader.
 */
const loadSettingsAccess = cache(async (familyId: SettingsFamilyId): Promise<SettingsAccess> => {
  const needsModules = getSettingsFamily(familyId).tabs.some((tab) => tab.requiredModule);
  const [caps, settings] = await Promise.all([
    fetchMyCapabilities(),
    needsModules ? getOrgSettings().catch(() => null) : null,
  ]);
  return settingsAccessFrom(caps, settings?.enabledModules ?? []);
});

/** `{Family} — {Tab} · Settings`, using the tab the page will actually render. */
export async function generateSettingsFamilyMetadata(
  familyId: SettingsFamilyId,
  { searchParams }: Pick<SettingsFamilyRouteProps, "searchParams">
): Promise<Metadata> {
  const family = getSettingsFamily(familyId);
  const sp = await searchParams;
  const { tab } = resolveSettingsTab(
    family,
    firstValue(sp.tab),
    await loadSettingsAccess(familyId)
  );
  return { title: `${family.label} — ${tab.label} · Settings` };
}

/**
 * Render a family page: the permitted tabs only (absent, never disabled), the resolved tab's panel,
 * and a one-line notice when a known-but-forbidden `?tab=` fell back to the first permitted tab.
 */
export async function renderSettingsFamilyPage<F extends SettingsFamilyId>(
  familyId: F,
  loaders: SettingsTabLoaders<F>,
  { params, searchParams }: SettingsFamilyRouteProps
): Promise<ReactNode> {
  const family = getSettingsFamily(familyId);
  const { slug } = await params;
  const sp = await searchParams;
  const access = await loadSettingsAccess(familyId);
  const { tab, forbidden } = resolveSettingsTab(family, firstValue(sp.tab), access);
  // resolveSettingsTab returns one of the family's tabs, so tab.id is a SettingsTabId<F>.
  const panel = await loaders[tab.id as SettingsTabId<F>]({ slug, searchParams: sp });

  return (
    <SettingsFamilyTabs
      label={`${family.label} settings`}
      tabs={visibleSettingsTabs(family, access).map(({ id, label }) => ({ id, label }))}
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
