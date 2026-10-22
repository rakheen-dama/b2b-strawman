import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { MemberRatesTable } from "@/components/rates/member-rates-table";
import { HelpTip } from "@/components/help-tip";
import type { OrgSettings, OrgMember, BillingRate, CostRate } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Fees & Trust › Tariffs tab (Phase 87 S9, ADR-341) — the former rates settings page's loader. */
export async function renderTariffsTab({ slug }: SettingsTabProps) {
  const capData = await fetchMyCapabilities();

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("FINANCIAL_VISIBILITY")
  ) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Rates & Currency
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage rates and currency settings. Only admins and owners
          can access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let members: OrgMember[] = [];
  let billingRates: BillingRate[] = [];
  let costRates: CostRate[] = [];

  const [settingsRes, membersRes, billingRatesRes, costRatesRes] = await Promise.allSettled([
    api.get<OrgSettings>("/api/settings"),
    api.get<OrgMember[]>("/api/members"),
    api.get<{ content: BillingRate[] }>("/api/billing-rates"),
    api.get<{ content: CostRate[] }>("/api/cost-rates"),
  ]);
  if (settingsRes.status === "fulfilled" && settingsRes.value) settings = settingsRes.value;
  if (membersRes.status === "fulfilled" && Array.isArray(membersRes.value))
    members = membersRes.value;
  if (billingRatesRes.status === "fulfilled") billingRates = billingRatesRes.value?.content ?? [];
  if (costRatesRes.status === "fulfilled") costRates = costRatesRes.value?.content ?? [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Rates & Currency
          <HelpTip code="rates.hierarchy" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage billing rates, cost rates, and the default currency for your organization.
        </p>
      </div>

      <MemberRatesTable
        slug={slug}
        members={members}
        billingRates={billingRates}
        costRates={costRates}
        defaultCurrency={settings?.defaultCurrency ?? "USD"}
      />
    </div>
  );
}
