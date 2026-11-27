import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchConflictChecks, fetchCustomers, fetchProjects } from "./actions";
import { ConflictCheckClient } from "./conflict-check-client";
import { buildConflictCheckFormDefaults, type ConflictCheckSearchParams } from "./form-defaults";
import { resolveClientDetails } from "./resolve-client";
import type { ConflictCheck } from "@/lib/types";
import { PageHeader } from "@b2mash/ui/page-header";

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
      <div className="space-y-6">
        <PageHeader title="Conflict Check" />
        <div className="flex flex-col items-center justify-center py-20">
          <h2 className="font-display text-foreground text-xl font-semibold">
            Module Not Available
          </h2>
          <p className="text-muted-foreground mt-2 text-sm">
            The Conflict Check module is not enabled for your organization.
          </p>
        </div>
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
  // that silently eats errors (GAP-L-29). The `customerId` client lookup for the form prefill
  // runs in the same batch; it resolves null on failure and rejects only for Next control flow
  // (re-login redirect), which Promise.all propagates.
  const [clientDetails, [customersResult, projectsResult]] = await Promise.all([
    resolveClientDetails(query.customerId),
    Promise.allSettled([fetchCustomers(), fetchProjects()]),
  ]);
  const initialCustomers = customersResult.status === "fulfilled" ? customersResult.value : [];
  const initialProjects = projectsResult.status === "fulfilled" ? projectsResult.value : [];

  const initialFormDefaults = buildConflictCheckFormDefaults(query, clientDetails);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Conflict Check"
        description="Run conflict of interest checks and review history"
      />

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
