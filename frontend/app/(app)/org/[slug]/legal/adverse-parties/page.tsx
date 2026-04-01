import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchAdverseParties } from "./actions";
import { AdversePartyRegistryClient } from "./adverse-party-registry-client";
import type { AdverseParty } from "@/lib/types";

export default async function AdversePartiesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("conflict_check")) {
    notFound();
  }

  let initialParties: AdverseParty[] = [];
  let initialTotal = 0;

  try {
    const result = await fetchAdverseParties();
    initialParties = result.content;
    initialTotal = result.page.totalElements;
  } catch (error) {
    console.error("Failed to fetch adverse parties:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Adverse Parties
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage the adverse party registry for conflict checking
        </p>
      </div>

      <AdversePartyRegistryClient
        initialParties={initialParties}
        initialTotal={initialTotal}
        slug={slug}
      />
    </div>
  );
}
