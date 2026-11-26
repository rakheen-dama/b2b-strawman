import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { JurisdictionSelectorSection } from "@/components/settings/jurisdiction-selector";
import { InformationOfficerSection } from "@/components/settings/information-officer-section";
import { RetentionPoliciesTable } from "@/components/data-protection/retention-policies-table";
import { ProcessingRegisterTable } from "@/components/data-protection/processing-register-table";
import { PaiaManualSection } from "@/components/data-protection/paia-manual-section";
import { SettingsSection } from "@/components/settings/settings-section";
import { fetchRetentionPolicies, fetchProcessingActivities } from "./data-protection-actions";
import { settingsHref } from "@/lib/settings-nav";
import type { OrgSettings } from "@/lib/types";
import type { RetentionPolicyExtended, ProcessingActivity } from "@/lib/types/data-protection";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/**
 * Firm › Data protection tab (Phase 87 S9, ADR-341) — the former settings/data-protection page's
 * loader.
 */
export async function renderDataProtectionTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Data Protection" />
        <p className="text-muted-foreground">
          You do not have permission to manage data protection settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let retentionPolicies: RetentionPolicyExtended[] = [];
  let processingActivities: ProcessingActivity[] = [];

  const [settingsResult, retentionResult, processingResult] = await Promise.allSettled([
    api.get<OrgSettings>("/api/settings"),
    fetchRetentionPolicies(slug),
    fetchProcessingActivities(slug),
  ]);

  if (settingsResult.status === "fulfilled") {
    settings = settingsResult.value;
  }
  if (retentionResult.status === "fulfilled") {
    retentionPolicies = retentionResult.value;
  }
  if (processingResult.status === "fulfilled") {
    processingActivities = processingResult.value;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Data Protection"
        description="Configure data protection jurisdiction, information officer details, and manage compliance settings."
      />

      {/* Section 1: Jurisdiction Selector */}
      <JurisdictionSelectorSection
        slug={slug}
        currentJurisdiction={settings.dataProtectionJurisdiction ?? null}
      />

      {/* Section 2: Information Officer */}
      <InformationOfficerSection
        slug={slug}
        initialName={settings.informationOfficerName ?? null}
        initialEmail={settings.informationOfficerEmail ?? null}
      />

      {/* Section 3: DSAR Requests */}
      <SettingsSection
        title="Data Subject Access Requests"
        description="Track and manage data subject access requests (DSARs) including access, deletion, correction, and portability requests."
      >
        <Link
          href={settingsHref(slug, "firm", "data-requests")}
          className="text-sm font-medium text-(--button-accent) hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
        >
          View DSAR Requests →
        </Link>
      </SettingsSection>

      {/* Section 4: Retention Policies */}
      <RetentionPoliciesTable
        policies={retentionPolicies}
        slug={slug}
        financialRetentionMonths={settings.financialRetentionMonths}
      />

      {/* Section 5: Processing Register */}
      <ProcessingRegisterTable activities={processingActivities} slug={slug} />

      {/* Section 6: PAIA Manual */}
      <PaiaManualSection slug={slug} jurisdiction={settings.dataProtectionJurisdiction ?? null} />
    </div>
  );
}
