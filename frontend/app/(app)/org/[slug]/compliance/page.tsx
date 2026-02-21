import { getAuthContext } from "@/lib/auth";
import { getComplianceDashboardData } from "./actions";
import { LifecycleDistributionSection } from "@/components/compliance/LifecycleDistributionSection";
import { OnboardingPipelineSection } from "@/components/compliance/OnboardingPipelineSection";
import { DataRequestsSection } from "@/components/compliance/DataRequestsSection";
import { DormancyCheckSection } from "@/components/compliance/DormancyCheckSection";

export default async function ComplianceDashboardPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view compliance data. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  const result = await getComplianceDashboardData(slug);

  if (!result.success || !result.data) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>
        <p className="text-sm text-red-600 dark:text-red-400">
          {result.error ?? "Failed to load compliance dashboard."}
        </p>
      </div>
    );
  }

  const { lifecycleCounts, onboardingCustomers, openDataRequests } = result.data;

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>

      <LifecycleDistributionSection counts={lifecycleCounts} orgSlug={slug} />

      <OnboardingPipelineSection customers={onboardingCustomers} orgSlug={slug} />

      <DataRequestsSection
        openCount={openDataRequests.total}
        urgentRequests={openDataRequests.urgent}
        orgSlug={slug}
      />

      <DormancyCheckSection orgSlug={slug} />
    </div>
  );
}
