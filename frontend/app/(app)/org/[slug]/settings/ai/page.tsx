import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiProfile, getAiCostSummary } from "@/lib/api/ai";
import { AiProfileForm } from "@/components/ai/ai-profile-form";
import { AiCostSummary } from "@/components/ai/ai-cost-summary";
import type { AiProfileResponse, AiCostSummaryResponse } from "@/lib/api/ai";

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

export default async function AiSettingsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin && !caps.capabilities.includes("AI_MANAGE")) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            AI Configuration
          </h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to manage AI settings. Contact your administrator.
          </p>
        </div>
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
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          {profile?.coldStartCompleted ? "AI Configuration" : "Set Up AI"}
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure your firm&apos;s AI profile to get tailored results across all AI skills.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <AiProfileForm slug={slug} initialData={formData} />
        </div>
        <div className="space-y-6">
          <AiCostSummary costSummary={costSummary} />

          {/* API Key Status Info Card */}
          <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
              AI Integration
            </h2>
            <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
              API keys and provider settings are managed in the Integrations page.
            </p>
            <Link
              href={`/org/${slug}/settings/integrations`}
              className="mt-3 inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            >
              Manage integrations →
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
