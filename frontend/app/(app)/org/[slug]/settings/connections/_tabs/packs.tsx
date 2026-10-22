import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listPackCatalog, listInstalledPacks } from "@/lib/api/packs";
import { PacksPageClient } from "./packs-page-client";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Connections › Packs tab (Phase 87 S9, ADR-341) — the former packs settings page's loader. */
export async function renderPacksTab({ slug }: SettingsTabProps) {
  const [caps, catalog, installed] = await Promise.all([
    fetchMyCapabilities(),
    listPackCatalog(),
    listInstalledPacks(),
  ]);

  const canManage = caps.isAdmin || caps.isOwner;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Packs</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Extend your workspace with pre-built content from Kazi Packs.
        </p>
        {!canManage && (
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            Only admins and owners can install or uninstall packs.
          </p>
        )}
      </div>

      <PacksPageClient
        initialCatalog={catalog}
        initialInstalled={installed}
        slug={slug}
        canManage={canManage}
      />
    </div>
  );
}
