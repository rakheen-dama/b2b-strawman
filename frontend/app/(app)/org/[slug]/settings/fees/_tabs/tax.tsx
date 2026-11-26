import { PageHeader } from "@b2mash/ui/page-header";
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
        <PageHeader title="Tax" />
        <p className="text-muted-foreground">You do not have permission to manage tax settings.</p>
      </div>
    );
  }

  const settings = await api.get<OrgSettings>("/api/settings");

  const taxRates = await api
    .get<TaxRateResponse[]>("/api/tax-rates?includeInactive=true")
    .catch(() => [] as TaxRateResponse[]);

  return (
    <div className="space-y-8">
      <PageHeader
        title="Tax"
        description="Configure tax registration details, labels, and inclusive pricing."
      />
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
