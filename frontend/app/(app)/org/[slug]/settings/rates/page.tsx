import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { MemberRatesTable } from "@/components/rates/member-rates-table";
import { HelpTip } from "@/components/help-tip";
import type { OrgSettings, OrgMember, BillingRate, CostRate } from "@/lib/types";

export default async function RatesSettingsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("FINANCIAL_VISIBILITY")
  ) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
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
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Rates & Currency
          <HelpTip code="rates.hierarchy" docsPath="/features/rate-cards-budgets" />
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
