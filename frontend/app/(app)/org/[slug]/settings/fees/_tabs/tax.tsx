import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { TaxSettingsForm } from "@/components/settings/TaxSettingsForm";
import { TaxRateTable } from "./tax-rate-table";
import type { OrgSettings, TaxRateResponse } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Fees & Trust › Tax tab (Phase 87 S9, ADR-341) — the former tax settings page's loader. */
export async function renderTaxTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Tax Settings</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage tax settings.
        </p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  const taxRates = await api
    .get<TaxRateResponse[]>("/api/tax-rates?includeInactive=true")
    .catch(() => [] as TaxRateResponse[]);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Tax Settings</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure tax registration details, labels, and inclusive pricing.
        </p>
      </div>
      <TaxSettingsForm
        slug={slug}
        taxRegistrationNumber={settings.taxRegistrationNumber ?? ""}
        taxRegistrationLabel={settings.taxRegistrationLabel ?? "Tax Number"}
        taxLabel={settings.taxLabel ?? "Tax"}
        taxInclusive={settings.taxInclusive ?? false}
      />
      <TaxRateTable slug={slug} taxRates={taxRates} />
    </div>
  );
}
