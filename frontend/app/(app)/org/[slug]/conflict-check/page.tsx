import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchConflictChecks, fetchCustomers, fetchProjects } from "./actions";
import { ConflictCheckClient } from "./conflict-check-client";
import type { ConflictCheck } from "@/lib/types";
import type { PerformConflictCheckFormData } from "@/lib/schemas/legal";

export default async function ConflictCheckPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams?: Promise<{
    customerId?: string;
    checkedName?: string;
    checkedIdNumber?: string;
  }>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = (await searchParams) ?? {};

  const initialFormDefaults: Partial<PerformConflictCheckFormData> = {};
  if (resolvedSearchParams.customerId) {
    initialFormDefaults.customerId = resolvedSearchParams.customerId;
  }
  if (resolvedSearchParams.checkedName) {
    initialFormDefaults.checkedName = resolvedSearchParams.checkedName;
  }
  if (resolvedSearchParams.checkedIdNumber) {
    initialFormDefaults.checkedIdNumber = resolvedSearchParams.checkedIdNumber;
  }

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

  // Pre-fetch customers + projects for the form selectors so the dropdowns
  // hydrate on first render instead of relying on a client-side useEffect
  // that silently eats errors (GAP-L-29).
  const [customersResult, projectsResult] = await Promise.allSettled([
    fetchCustomers(),
    fetchProjects(),
  ]);
  const initialCustomers = customersResult.status === "fulfilled" ? customersResult.value : [];
  const initialProjects = projectsResult.status === "fulfilled" ? projectsResult.value : [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Conflict Check</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Run conflict of interest checks and review history
        </p>
      </div>

      <ConflictCheckClient
        initialChecks={initialChecks}
        initialTotal={initialTotal}
        initialCustomers={initialCustomers}
        initialProjects={initialProjects}
        initialFormDefaults={initialFormDefaults}
        slug={slug}
      />
    </div>
  );
}
