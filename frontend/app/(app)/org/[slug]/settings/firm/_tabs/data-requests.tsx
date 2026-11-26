import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchDsarRequests } from "./data-requests-actions";
import { DsarRequestsTable } from "@/components/data-protection/dsar-requests-table";
import { LogDsarRequestDialog } from "@/components/data-protection/log-dsar-dialog";
import { SettingsSection } from "@/components/settings/settings-section";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/**
 * Firm › Data requests tab (Phase 87 S9, ADR-341) — the former settings/data-protection/requests
 * page's loader.
 */
export async function renderDataRequestsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="DSAR Requests" />
        <p className="text-muted-foreground">
          You do not have permission to manage DSAR requests. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  let requests: Awaited<ReturnType<typeof fetchDsarRequests>> = [];
  let fetchError: string | null = null;
  try {
    requests = await fetchDsarRequests(slug);
  } catch (error) {
    console.error("Failed to fetch DSAR requests:", error);
    fetchError = error instanceof Error ? error.message : "Failed to load DSAR requests.";
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="DSAR Requests"
        description="Track and manage data subject access requests."
        actions={<LogDsarRequestDialog slug={slug} />}
      />

      {fetchError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
          <p className="text-destructive text-sm">{fetchError}</p>
        </div>
      )}

      <SettingsSection>
        <DsarRequestsTable requests={requests} slug={slug} />
      </SettingsSection>
    </div>
  );
}
