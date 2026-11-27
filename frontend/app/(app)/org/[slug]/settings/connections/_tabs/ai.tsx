import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiProfile, getAiCostSummary } from "@/lib/api/ai";
import { AiProfileForm } from "@/components/ai/ai-profile-form";
import { AiCostSummary } from "@/components/ai/ai-cost-summary";
import { SettingsSection } from "@/components/settings/settings-section";
import type { AiProfileResponse, AiCostSummaryResponse } from "@/lib/api/ai";
import { settingsHref } from "@/lib/settings-nav";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

const DEFAULT_PROFILE = {
  practiceAreas: [],
  jurisdiction: "",
  riskCalibration: "MODERATE",
  houseStyleNotes: null,
  ficaRequirements: null,
  feeEstimationNotes: null,
  preferredModel: "claude-sonnet-4-6",
  monthlyBudgetCents: null,
  coldStartCompleted: false,
};

/** Connections › AI tab (Phase 87 S9, ADR-341) — the former AI settings page's loader. */
export async function renderAiTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("AI_MANAGE")) {
    return (
      <div className="space-y-8">
        <PageHeader title="AI Configuration" />
        <p className="text-muted-foreground text-sm">
          You do not have permission to manage AI settings. Contact your administrator.
        </p>
      </div>
    );
  }

  let profile: AiProfileResponse | null = null;
  let costSummary: AiCostSummaryResponse | null = null;

  const [profileResult, costResult] = await Promise.allSettled([
    getAiProfile(),
    getAiCostSummary(),
  ]);

  if (profileResult.status === "fulfilled") {
    profile = profileResult.value;
  }
  if (costResult.status === "fulfilled") {
    costSummary = costResult.value;
  }

  const formData = profile
    ? {
        practiceAreas: profile.practiceAreas,
        jurisdiction: profile.jurisdiction,
        riskCalibration: profile.riskCalibration,
        houseStyleNotes: profile.houseStyleNotes,
        ficaRequirements: profile.ficaRequirements,
        feeEstimationNotes: profile.feeEstimationNotes,
        preferredModel: profile.preferredModel,
        monthlyBudgetCents: profile.monthlyBudgetCents,
        coldStartCompleted: profile.coldStartCompleted,
      }
    : DEFAULT_PROFILE;

  return (
    <div className="space-y-8">
      {/* DA-13: the h1 text is unchanged (K-124 does not list the AI tab). */}
      <PageHeader
        title={profile?.coldStartCompleted ? "AI Configuration" : "Set Up AI"}
        description={
          <>Configure your firm&apos;s AI profile to get tailored results across all AI skills.</>
        }
      />

      {/* K-100: the form/aside split follows the panel width (48rem), not the viewport. */}
      <div className="@container">
        <div className="grid grid-cols-1 gap-6 @3xl:grid-cols-3">
          <div className="@3xl:col-span-2">
            <AiProfileForm slug={slug} initialData={formData} />
          </div>
          <div className="space-y-6">
            <AiCostSummary costSummary={costSummary} />

            <SettingsSection
              title="Execution History"
              description="View all AI skill executions, costs, and outcomes."
            >
              <Link
                href={`/org/${slug}/settings/connections/ai/history`}
                className="inline-block text-sm font-medium text-(--button-accent) hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
              >
                View execution history →
              </Link>
            </SettingsSection>

            <SettingsSection
              title="AI Integration"
              description="API keys and provider settings are managed in the Integrations page."
            >
              <Link
                href={settingsHref(slug, "connections", "xero")}
                className="inline-block text-sm font-medium text-(--button-accent) hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
              >
                Manage integrations →
              </Link>
            </SettingsSection>
          </div>
        </div>
      </div>
    </div>
  );
}
