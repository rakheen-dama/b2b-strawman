import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { ReconciliationWizard } from "./wizard";

export default async function NewReconciliationPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // Module gating
  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  // Capability check
  const capData = await fetchMyCapabilities();
  const hasViewTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  // Fetch trust accounts
  let accounts;
  try {
    accounts = await fetchTrustAccounts();
  } catch {
    notFound();
  }

  const currency = settings.defaultCurrency ?? "ZAR";

  return (
    <div data-testid="new-reconciliation-page">
      <ReconciliationWizard
        accounts={accounts}
        currency={currency}
        slug={slug}
      />
    </div>
  );
}
