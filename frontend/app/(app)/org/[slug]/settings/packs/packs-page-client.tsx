"use client";

import { useState, useTransition, useCallback, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Package } from "lucide-react";
import { toast } from "sonner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { EmptyState } from "@/components/empty-state";
import { PackCard } from "@/components/settings/pack-card";
import type { PackCatalogEntry, UninstallCheck } from "@/lib/api/packs";
import {
  fetchCatalogAction,
  fetchUninstallCheckAction,
  installPackAction,
  uninstallPackAction,
} from "./actions";

interface PacksPageClientProps {
  initialCatalog: PackCatalogEntry[];
  initialInstalled: PackCatalogEntry[];
  slug: string;
  canManage: boolean;
}

export function PacksPageClient({
  initialCatalog,
  initialInstalled,
  slug,
  canManage,
}: PacksPageClientProps) {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState("available");
  const [showAll, setShowAll] = useState(false);
  const [catalog, setCatalog] = useState<PackCatalogEntry[]>(initialCatalog);
  const [installed, setInstalled] =
    useState<PackCatalogEntry[]>(initialInstalled);
  const [uninstallChecks, setUninstallChecks] = useState<
    Record<string, UninstallCheck>
  >({});
  const [installingPackId, setInstallingPackId] = useState<string | null>(null);
  const [uninstallingPackId, setUninstallingPackId] = useState<string | null>(
    null
  );
  const [isPending, startTransition] = useTransition();

  // Re-sync from server when props change (e.g. after revalidatePath)
  useEffect(() => {
    setCatalog(initialCatalog);
  }, [initialCatalog]);

  useEffect(() => {
    setInstalled(initialInstalled);
  }, [initialInstalled]);

  // Fetch catalog when "show all" toggle changes
  useEffect(() => {
    startTransition(async () => {
      try {
        const data = await fetchCatalogAction(showAll);
        setCatalog(data);
      } catch {
        toast.error("Failed to load pack catalog.");
      }
    });
  }, [showAll]);

  // Fetch uninstall checks when Installed tab becomes active
  useEffect(() => {
    if (activeTab !== "installed" || installed.length === 0) return;

    for (const pack of installed) {
      if (uninstallChecks[pack.packId]) continue;
      startTransition(async () => {
        try {
          const check = await fetchUninstallCheckAction(pack.packId);
          setUninstallChecks((prev) => ({
            ...prev,
            [pack.packId]: check,
          }));
        } catch {
          // Silently fail -- button stays disabled by default
        }
      });
    }
  }, [activeTab, installed, uninstallChecks]);

  const handleInstall = useCallback(
    (packId: string) => {
      if (!canManage) return;
      setInstallingPackId(packId);
      startTransition(async () => {
        const result = await installPackAction(slug, packId);
        setInstallingPackId(null);
        if (result.success) {
          toast.success("Pack installed successfully.");
          router.refresh();
        } else {
          toast.error(result.error ?? "Failed to install pack.");
        }
      });
    },
    [canManage, slug, router]
  );

  const handleUninstall = useCallback(
    (packId: string) => {
      if (!canManage) return;
      setUninstallingPackId(packId);
      startTransition(async () => {
        const result = await uninstallPackAction(slug, packId);
        setUninstallingPackId(null);
        if (result.success) {
          toast.success("Pack uninstalled successfully.");
          // Clear cached uninstall check
          setUninstallChecks((prev) => {
            const next = { ...prev };
            delete next[packId];
            return next;
          });
          router.refresh();
        } else {
          toast.error(result.error ?? "Failed to uninstall pack.");
        }
      });
    },
    [canManage, slug, router]
  );

  return (
    <Tabs value={activeTab} onValueChange={setActiveTab}>
      <div className="flex items-center gap-2">
        <TabsList variant="line">
          <TabsTrigger value="available">Available</TabsTrigger>
          <TabsTrigger value="installed">
            Installed{installed.length > 0 && ` (${installed.length})`}
          </TabsTrigger>
        </TabsList>
      </div>

      <TabsContent value="available" className="space-y-6">
        <div className="flex items-center justify-between">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Kazi Packs &mdash; extend your workspace with pre-built content
          </p>
          <div className="flex items-center gap-2">
            <Switch
              id="show-all-packs"
              checked={showAll}
              onCheckedChange={setShowAll}
            />
            <Label
              htmlFor="show-all-packs"
              className="text-sm text-slate-600 dark:text-slate-400"
            >
              Show all packs
            </Label>
          </div>
        </div>

        {catalog.length === 0 ? (
          <EmptyState
            icon={Package}
            title="No packs available"
            description="No Kazi Packs available for your current profile. Toggle 'Show all packs' to browse everything."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {catalog.map((pack) => (
              <PackCard
                key={pack.packId}
                pack={pack}
                variant="available"
                onInstall={canManage ? handleInstall : undefined}
                isInstalling={installingPackId === pack.packId}
                slug={slug}
              />
            ))}
          </div>
        )}
      </TabsContent>

      <TabsContent value="installed" className="space-y-6">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Installed Packs
        </p>

        {installed.length === 0 ? (
          <EmptyState
            icon={Package}
            title="No packs installed"
            description="No packs installed yet. Browse the Available tab to add templates and workflow automations to your workspace."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {installed.map((pack) => (
              <PackCard
                key={pack.packId}
                pack={pack}
                variant="installed"
                uninstallCheck={uninstallChecks[pack.packId] ?? null}
                onUninstall={canManage ? handleUninstall : undefined}
                isUninstalling={uninstallingPackId === pack.packId}
                slug={slug}
              />
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  );
}
