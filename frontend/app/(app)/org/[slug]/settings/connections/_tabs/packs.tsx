import { PageHeader } from "@b2mash/ui/page-header";
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
      <div className="space-y-2">
        <PageHeader
          title="Packs"
          description="Extend your workspace with pre-built content from Kazi Packs."
        />
        {!canManage && (
          <p className="text-muted-foreground text-sm">
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
