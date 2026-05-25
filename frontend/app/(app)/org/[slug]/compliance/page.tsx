import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiProfile } from "@/lib/api/ai";
import { getAuditReports } from "@/lib/api/compliance-audit";
import { getComplianceDashboardData } from "./actions";
import { LifecycleDistributionSection } from "@/components/compliance/LifecycleDistributionSection";
import { OnboardingPipelineSection } from "@/components/compliance/OnboardingPipelineSection";
import { DataRequestsSection } from "@/components/compliance/DataRequestsSection";
import { DormancyCheckSection } from "@/components/compliance/DormancyCheckSection";
import { ComplianceAuditTab } from "@/components/ai/compliance-audit-tab";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { PaginatedResponse, ComplianceAuditReportResponse } from "@/lib/api/compliance-audit";

export default async function ComplianceDashboardPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("CUSTOMER_MANAGEMENT")
  ) {
    notFound();
  }

  const result = await getComplianceDashboardData(slug);

  // AI configuration status
  const canExecuteAi = capData.capabilities.includes("AI_EXECUTE");
  const canReviewGates = capData.capabilities.includes("AI_REVIEW");
  let isAiConfigured = false;
  if (capData.capabilities.includes("AI_MANAGE")) {
    try {
      const aiProfile = await getAiProfile();
      isAiConfigured = aiProfile.coldStartCompleted;
    } catch {
      // Non-fatal: panel will show disabled state
    }
  } else if (canExecuteAi) {
    // MEMBER with AI_EXECUTE: they wouldn't have this capability without setup being done
    isAiConfigured = true;
  }

  // Fetch initial audit reports for the AI tab
  let initialReports: PaginatedResponse<ComplianceAuditReportResponse> = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 10, number: 0 },
  };
  if (isAiConfigured) {
    try {
      initialReports = await getAuditReports(0, 10);
    } catch {
      // Non-fatal: tab will show empty state
    }
  }

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

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="ai-audit">AI Audit</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="space-y-8 pt-4">
            <LifecycleDistributionSection counts={lifecycleCounts} orgSlug={slug} />

            <OnboardingPipelineSection customers={onboardingCustomers} orgSlug={slug} />

            <DataRequestsSection
              openCount={openDataRequests.total}
              urgentRequests={openDataRequests.urgent}
              orgSlug={slug}
            />

            <DormancyCheckSection orgSlug={slug} />
          </div>
        </TabsContent>

        <TabsContent value="ai-audit">
          <div className="pt-4">
            <ComplianceAuditTab
              slug={slug}
              isAiConfigured={isAiConfigured}
              canExecuteAi={canExecuteAi}
              canReviewGates={canReviewGates}
              initialReports={initialReports}
            />
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
