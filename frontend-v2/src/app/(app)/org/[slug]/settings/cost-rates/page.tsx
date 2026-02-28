import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { RatesTable } from "@/components/rates/rates-table";
import type { OrgSettings, OrgMember, CostRate } from "@/lib/types";

export default async function CostRatesSettingsPage({
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
            Cost Rates
          </h1>
          <p className="text-sm text-slate-500">
            You do not have permission to manage cost rates.
          </p>
        </div>
      </SettingsSidebar>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let members: OrgMember[] = [];
  let costRates: CostRate[] = [];

  try {
    const [settingsRes, membersRes, ratesRes] = await Promise.all([
      api.get<OrgSettings>("/api/settings"),
      api.get<OrgMember[]>("/api/members"),
      api.get<{ content: CostRate[] }>("/api/cost-rates"),
    ]);
    settings = settingsRes;
    members = membersRes;
    costRates = ratesRes?.content ?? [];
  } catch {
    // Non-fatal
  }

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Cost Rates
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage internal cost rates for profitability tracking. These rates
            represent the actual cost of team members&apos; time.
          </p>
        </div>

        <RatesTable
          slug={slug}
          members={members}
          rates={costRates}
          defaultCurrency={settings.defaultCurrency}
          type="cost"
        />
      </div>
    </SettingsSidebar>
  );
}
