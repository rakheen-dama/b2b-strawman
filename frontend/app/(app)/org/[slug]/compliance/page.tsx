import { auth } from "@clerk/nextjs/server";
import {
  getLifecycleStatusCounts,
  getOnboardingPipelineData,
  getDashboardDataRequests,
} from "@/lib/compliance-api";
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
  const { orgRole } = await auth();

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

  let lifecycleCounts: Record<string, number>;
  let onboardingCustomers: Array<{
    id: string;
    name: string;
    lifecycleStatusChangedAt: string | null;
    checklistProgress: { completed: number; total: number };
  }>;
  let openDataRequests: { total: number; urgent: import("@/lib/types").DataRequestResponse[] };

  try {
    const [counts, customers, urgentRequests] = await Promise.all([
      getLifecycleStatusCounts(),
      getOnboardingPipelineData(),
      getDashboardDataRequests(),
    ]);
    lifecycleCounts = counts;
    onboardingCustomers = customers;
    openDataRequests = { total: urgentRequests.length, urgent: urgentRequests };
  } catch {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>
        <p className="text-sm text-red-600 dark:text-red-400">
          Failed to load compliance dashboard.
        </p>
      </div>
    );
  }

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
