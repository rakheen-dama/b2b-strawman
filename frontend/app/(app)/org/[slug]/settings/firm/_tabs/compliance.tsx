import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { RetentionPolicyTable } from "@/components/compliance/RetentionPolicyTable";
import { ComplianceSettingsForm } from "@/components/compliance/ComplianceSettingsForm";
import { CompliancePackList } from "@/components/compliance/CompliancePackList";
import type { OrgSettings, RetentionPolicy } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Firm › Compliance tab (Phase 87 S9, ADR-341) — the former settings/compliance page's loader. */
export async function renderComplianceTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage compliance settings. Only admins and owners can
          access this page.
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
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Compliance</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage retention policies, dormancy thresholds, and data request deadlines.
        </p>
      </div>

      {/* Section 1: General Settings */}
      <ComplianceSettingsForm
        slug={slug}
        dormancyThresholdDays={settings.dormancyThresholdDays ?? 365}
        dataRequestDeadlineDays={settings.dataRequestDeadlineDays ?? 30}
      />

      {/* Section 2: Retention Policies */}
      <RetentionPolicyTable key={JSON.stringify(policies)} policies={policies} slug={slug} />

      {/* Section 3: Compliance Packs */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Compliance Packs
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Applied compliance packs and their versions. Click a pack to view its full contents.
        </p>
        <CompliancePackList packs={settings.compliancePackStatus ?? []} />
      </div>
    </div>
  );
}
