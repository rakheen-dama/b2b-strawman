import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { RatesTable } from "@/components/rates/rates-table";
import type { OrgSettings, OrgMember, BillingRate } from "@/lib/types";

export default async function RatesSettingsPage({
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
            Billing Rates
          </h1>
          <p className="text-sm text-slate-500">
            You do not have permission to manage billing rates.
          </p>
        </div>
      </SettingsSidebar>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let members: OrgMember[] = [];
  let billingRates: BillingRate[] = [];

  try {
    const [settingsRes, membersRes, ratesRes] = await Promise.all([
      api.get<OrgSettings>("/api/settings"),
      api.get<OrgMember[]>("/api/members"),
      api.get<{ content: BillingRate[] }>("/api/billing-rates"),
    ]);
    settings = settingsRes;
    members = membersRes;
    billingRates = ratesRes?.content ?? [];
  } catch {
    // Non-fatal
  }

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Billing Rates
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage billing rates for your team members. Rates are used for
            invoicing and profitability calculations.
          </p>
        </div>

        <RatesTable
          slug={slug}
          members={members}
          rates={billingRates}
          defaultCurrency={settings.defaultCurrency}
          type="billing"
        />
      </div>
    </SettingsSidebar>
  );
}
