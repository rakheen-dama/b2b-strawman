import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { RetentionPolicyTable } from "@/components/compliance/RetentionPolicyTable";
import { ComplianceSettingsForm } from "@/components/compliance/ComplianceSettingsForm";
import { MatterRetentionSettingsForm } from "@/components/compliance/MatterRetentionSettingsForm";
import { CompliancePackList } from "@/components/compliance/CompliancePackList";
import { DEFAULT_MATTER_RETENTION_YEARS } from "@/lib/schemas";
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
  const settingsLoaded = settingsResult.status === "fulfilled";
  if (settingsLoaded) {
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
          Manage the matter retention period, retention policies, dormancy thresholds, and data
          request deadlines.
        </p>
      </div>

      {/* Section 1: Matter retention period — owner-only on the backend, read-only for admins.
          Without the stored value there is nothing safe to show or save: a fallback number would
          let an owner overwrite the firm's real period without seeing it. */}
      {settingsLoaded ? (
        <MatterRetentionSettingsForm
          slug={slug}
          legalMatterRetentionYears={
            settings.legalMatterRetentionYears ?? DEFAULT_MATTER_RETENTION_YEARS
          }
          canEdit={caps.isOwner}
        />
      ) : (
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Matter retention
          </h2>
          <p role="alert" className="mt-1 text-sm text-red-600 dark:text-red-400">
            {"Couldn't load the current matter retention period. Reload the page to change it."}
          </p>
        </div>
      )}

      {/* Section 2: General Settings */}
      <ComplianceSettingsForm
        slug={slug}
        dormancyThresholdDays={settings.dormancyThresholdDays ?? 365}
        dataRequestDeadlineDays={settings.dataRequestDeadlineDays ?? 30}
      />

      {/* Section 3: Retention Policies */}
      <RetentionPolicyTable key={JSON.stringify(policies)} policies={policies} slug={slug} />

      {/* Section 4: Compliance Packs */}
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
