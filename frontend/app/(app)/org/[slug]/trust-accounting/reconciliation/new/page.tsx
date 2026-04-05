import { notFound } from "next/navigation";
import Link from "next/link";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { FileCheck } from "lucide-react";
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

  // Capability check — this is a write-operation page (create reconciliation,
  // upload statements, auto-match, complete) so require MANAGE_TRUST
  const capData = await fetchMyCapabilities();
  const canManageTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("MANAGE_TRUST");
  if (!canManageTrust) {
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

  if (accounts.length === 0) {
    return (
      <div data-testid="new-reconciliation-page">
        <Card>
          <CardContent className="py-10 text-center">
            <FileCheck className="mx-auto mb-3 size-10 text-slate-300 dark:text-slate-600" />
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust accounts found. Create a trust account first.
            </p>
            <div className="mt-4">
              <Button asChild variant="outline">
                <Link href={`/org/${slug}/trust-accounting`}>
                  Back to Trust Accounting
                </Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

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
