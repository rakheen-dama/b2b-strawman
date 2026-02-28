import { api } from "@/lib/api";
import { SettingsSidebar } from "@/components/shell/settings-sidebar";
import { BillingPageClient } from "@/components/billing/billing-page-client";
import type { BillingResponse } from "@/lib/internal-api";

export default async function BillingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let billing: BillingResponse | null = null;
  try {
    billing = await api.get<BillingResponse>("/api/billing/subscription");
  } catch {
    // Non-fatal
  }

  return (
    <SettingsSidebar slug={slug}>
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            Plan & Billing
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage your subscription and view usage.
          </p>
        </div>

        <BillingPageClient slug={slug} billing={billing} />
      </div>
    </SettingsSidebar>
  );
}
