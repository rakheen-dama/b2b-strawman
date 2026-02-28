import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { ComplianceSettings } from "@/components/compliance/compliance-settings";
import { RetentionPolicies } from "@/components/compliance/retention-policies";
import { DataRequests } from "@/components/compliance/data-requests";
import type { OrgSettings, RetentionPolicy } from "@/lib/types";

export default async function CompliancePage({
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
        <PageHeader
          title="Compliance"
          description="Manage retention policies, dormancy thresholds, and data requests."
        />
        <p className="text-sm text-slate-500">
          You do not have permission to manage compliance settings. Only admins
          and owners can access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let policies: RetentionPolicy[] = [];

  const [settingsResult, policiesResult] = await Promise.allSettled([
    api.get<OrgSettings>("/api/settings"),
    api.get<RetentionPolicy[]>("/api/retention-policies"),
  ]);

  if (settingsResult.status === "fulfilled") {
    settings = settingsResult.value;
  }
  if (policiesResult.status === "fulfilled") {
    policies = policiesResult.value ?? [];
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Compliance"
        description="Manage retention policies, dormancy thresholds, and data requests."
      />

      <ComplianceSettings
        slug={slug}
        dormancyThresholdDays={settings.dormancyThresholdDays ?? 365}
        dataRequestDeadlineDays={settings.dataRequestDeadlineDays ?? 30}
      />

      <RetentionPolicies slug={slug} policies={policies} />

      <DataRequests slug={slug} />
    </div>
  );
}
