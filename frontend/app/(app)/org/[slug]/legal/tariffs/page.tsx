import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchTariffSchedules } from "./actions";
import { TariffBrowserClient } from "./tariff-browser-client";
import type { TariffSchedule } from "@/lib/types";

export default async function TariffsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("lssa_tariff")) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <h2 className="font-display text-xl font-semibold text-slate-950 dark:text-slate-50">
          Module Not Available
        </h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The Tariff Schedules module is not enabled for your organization.
        </p>
      </div>
    );
  }

  let initialSchedules: TariffSchedule[] = [];
  let initialTotal = 0;

  try {
    const result = await fetchTariffSchedules();
    initialSchedules = result ?? [];
    initialTotal = initialSchedules.length;
  } catch (error) {
    console.error("Failed to fetch tariff schedules:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Tariff Schedules
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Browse and manage LSSA tariff schedules and items
        </p>
      </div>

      <TariffBrowserClient
        initialSchedules={initialSchedules}
        initialTotal={initialTotal}
        slug={slug}
      />
    </div>
  );
}
