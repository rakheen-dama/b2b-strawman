import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchTariffSchedules } from "./actions";
import { TariffBrowserClient } from "./tariff-browser-client";
import type { TariffSchedule } from "@/lib/types";
import { PageHeader } from "@b2mash/ui/page-header";

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
      <div className="space-y-6">
        <PageHeader title="Tariff Schedules" />
        <div className="flex flex-col items-center justify-center py-20">
          <h2 className="font-display text-foreground text-xl font-semibold">
            Module Not Available
          </h2>
          <p className="text-muted-foreground mt-2 text-sm">
            The Tariff Schedules module is not enabled for your organization.
          </p>
        </div>
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
      <PageHeader
        title="Tariff Schedules"
        description="Browse and manage LSSA tariff schedules and items"
      />

      <TariffBrowserClient
        initialSchedules={initialSchedules}
        initialTotal={initialTotal}
        slug={slug}
      />
    </div>
  );
}
