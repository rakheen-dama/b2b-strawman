import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { DefaultCapacitySettings } from "@/components/capacity/default-capacity-settings";
import type { OrgSettings } from "@/lib/types";

export default async function CapacitySettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("RESOURCE_PLANNING")) {
    notFound();
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };

  const settingsResult = await api.get<OrgSettings>("/api/settings").catch(() => null);
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
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Capacity</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure default weekly capacity hours for your organization.
        </p>
      </div>

      <DefaultCapacitySettings
        slug={slug}
        defaultWeeklyCapacityHours={settings.defaultWeeklyCapacityHours ?? 40}
      />
    </div>
  );
}
