import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchConflictChecks } from "./actions";
import { ConflictCheckClient } from "./conflict-check-client";
import type { ConflictCheck } from "@/lib/types";

export default async function ConflictCheckPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("conflict_check")) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <h2 className="font-display text-xl font-semibold text-slate-950 dark:text-slate-50">
          Module Not Available
        </h2>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The Conflict Check module is not enabled for your organization.
        </p>
      </div>
    );
  }

  let initialChecks: ConflictCheck[] = [];
  let initialTotal = 0;

  try {
    const result = await fetchConflictChecks();
    initialChecks = result?.content ?? [];
    initialTotal = result?.page?.totalElements ?? 0;
  } catch (error) {
    console.error("Failed to fetch conflict checks:", error);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Conflict Check</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Run conflict of interest checks and review history
        </p>
      </div>

      <ConflictCheckClient initialChecks={initialChecks} initialTotal={initialTotal} slug={slug} />
    </div>
  );
}
