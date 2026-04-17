import type { Metadata } from "next";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listPackCatalog, listInstalledPacks } from "@/lib/api/packs";
import { PacksPageClient } from "./packs-page-client";

export const metadata: Metadata = {
  title: "Packs",
};

export default async function PacksSettingsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const [caps, catalog, installed] = await Promise.all([
    fetchMyCapabilities(),
    listPackCatalog(),
    listInstalledPacks(),
  ]);

  const canManage = caps.isAdmin || caps.isOwner;

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
