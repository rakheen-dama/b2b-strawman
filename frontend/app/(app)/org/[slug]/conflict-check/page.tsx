import { notFound, unstable_rethrow } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { getClient } from "@/lib/api/clients";
import { isUuid } from "@/components/audit/filter-params";
import { fetchConflictChecks, fetchCustomers, fetchProjects } from "./actions";
import { ConflictCheckClient } from "./conflict-check-client";
import {
  buildConflictCheckFormDefaults,
  type ConflictCheckClientDetails,
  type ConflictCheckSearchParams,
} from "./form-defaults";
import type { ConflictCheck } from "@/lib/types";

/**
 * The `customerId` client's name + structural ID number for the form prefill. The ID number is
 * resolved here rather than carried in the URL (POPIA, Phase 88 ruling R-17). A failed lookup
 * (404/403/network) leaves the page on its other defaults; Next control flow (re-login redirect)
 * still propagates.
 */
async function resolveClientDetails(
  customerId: string | undefined
): Promise<ConflictCheckClientDetails | null> {
  if (!customerId || !isUuid(customerId)) return null;
  try {
    const client = await getClient(customerId);
    return { name: client.name, idNumber: client.idNumber };
  } catch (error) {
    unstable_rethrow(error);
    console.error("Failed to resolve conflict-check client:", error);
    return null;
  }
}

export default async function ConflictCheckPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams?: Promise<ConflictCheckSearchParams>;
}) {
  const { slug } = await params;
  const query = (await searchParams) ?? {};

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

  const initialFormDefaults = buildConflictCheckFormDefaults(
    query,
    await resolveClientDetails(query.customerId)
  );

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
