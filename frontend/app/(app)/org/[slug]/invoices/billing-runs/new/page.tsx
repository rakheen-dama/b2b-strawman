import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { BillingRunWizard } from "@/components/billing-runs/billing-run-wizard";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function NewBillingRunPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ billingRunId?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;

  // Server-side module gate — short-circuit before any further work.
  if (!(await isModuleEnabledServer("bulk_billing"))) {
    return (
      <div className="space-y-8">
        <PageHeader title="New fee run" />
        <ModuleDisabledFallback moduleName="Bulk fee runs" slug={slug} />
      </div>
    );
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="New fee run" />
        <p className="text-muted-foreground">
          You do not have permission to create fee runs. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader title="New fee run" />
      <BillingRunWizard slug={slug} billingRunId={search.billingRunId} />
    </div>
  );
}
