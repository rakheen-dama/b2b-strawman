import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { BatchBillingSettings } from "@/components/settings/batch-billing-settings";
import type { OrgSettings } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Fees & Trust › Fee runs tab (Phase 87 S9, ADR-341) — the former batch-billing settings page's loader. */
export async function renderFeeRunsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Batch Billing</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage batch billing settings. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Batch Billing</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure async thresholds, email rate limits, and default billing currency for batch
          billing runs.
        </p>
      </div>

      <BatchBillingSettings
        slug={slug}
        billingBatchAsyncThreshold={settings.billingBatchAsyncThreshold ?? 50}
        billingEmailRateLimit={settings.billingEmailRateLimit ?? 5}
        defaultBillingRunCurrency={settings.defaultBillingRunCurrency ?? null}
      />
    </div>
  );
}
