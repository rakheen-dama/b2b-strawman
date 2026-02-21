import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { MemberRatesTable } from "@/components/rates/member-rates-table";
import type { OrgSettings, OrgMember, BillingRate, CostRate } from "@/lib/types";

export default async function RatesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
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
          You do not have permission to manage rates. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let settings: OrgSettings = { defaultCurrency: "USD" };
  let members: OrgMember[] = [];
  let billingRates: BillingRate[] = [];
  let costRates: CostRate[] = [];

  try {
    const [settingsRes, membersRes, billingRatesRes, costRatesRes] =
      await Promise.all([
        api.get<OrgSettings>("/api/settings"),
        api.get<OrgMember[]>("/api/members"),
        api.get<{ content: BillingRate[] }>("/api/billing-rates"),
        api.get<{ content: CostRate[] }>("/api/cost-rates"),
      ]);
    settings = settingsRes;
    members = membersRes;
    billingRates = billingRatesRes?.content ?? [];
    costRates = costRatesRes?.content ?? [];
  } catch {
    // Non-fatal: show empty state with defaults
  }

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
          Rates & Currency
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage billing rates, cost rates, and the default currency for your
          organization.
        </p>
      </div>

      <MemberRatesTable
        slug={slug}
        members={members}
        billingRates={billingRates}
        costRates={costRates}
        defaultCurrency={settings.defaultCurrency}
      />
    </div>
  );
}
