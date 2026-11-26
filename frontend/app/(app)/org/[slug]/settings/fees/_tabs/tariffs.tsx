import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { MemberRatesTable } from "@/components/rates/member-rates-table";
import { HelpTip } from "@/components/help-tip";
import type { OrgSettings, OrgMember, BillingRate, CostRate } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/**
 * Fees & Trust › Rates tab (id `tariffs`, Phase 87 S9, ADR-341; labelled "Rates" by Phase 88 628B,
 * D11) — the former rates settings page's loader.
 */
export async function renderTariffsTab({ slug }: SettingsTabProps) {
  const capData = await fetchMyCapabilities();

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("FINANCIAL_VISIBILITY")
  ) {
    return (
      <div className="space-y-8">
        <PageHeader title="Rates" />
        <p className="text-muted-foreground">
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
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Rates
            <HelpTip code="rates.hierarchy" />
          </span>
        }
        description="Manage billing rates, cost rates, and the default currency for your organization."
      />

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
