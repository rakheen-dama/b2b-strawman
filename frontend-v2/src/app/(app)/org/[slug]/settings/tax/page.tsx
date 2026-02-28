import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { TaxSettingsForm } from "@/components/settings/tax-settings-form";
import { TaxRateTable } from "@/components/settings/tax-rate-table";
import type { OrgSettings, TaxRateResponse } from "@/lib/types";

export default async function TaxSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <SettingsSidebar slug={slug}>
        <div className="space-y-6">
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Tax Settings
          </h1>
          <p className="text-sm text-slate-500">
            You do not have permission to manage tax settings.
          </p>
        </div>
      </SettingsSidebar>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  const settingsResult = await api
    .get<OrgSettings>("/api/settings")
    .catch(() => null);
  if (settingsResult) settings = settingsResult;

  const taxRates = await api
    .get<TaxRateResponse[]>("/api/tax-rates?includeInactive=true")
    .catch(() => [] as TaxRateResponse[]);

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Tax Settings
          </h1>
          <p className="mt-1 text-sm text-slate-500">
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
    </SettingsSidebar>
  );
}
