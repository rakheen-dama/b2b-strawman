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
    return <ModuleDisabledFallback moduleName="Bulk Billing Runs" slug={slug} />;
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Billing Run
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to create billing runs. Only admins and
          owners can access this page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
        New Billing Run
      </h1>
      <BillingRunWizard
        slug={slug}
        billingRunId={search.billingRunId}
      />
    </div>
  );
}
