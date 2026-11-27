import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { listBillingRuns } from "@/lib/api/billing-runs";
import type { BillingRun } from "@/lib/api/billing-runs";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { BillingRunsTable } from "@/components/billing-runs/billing-runs-table";
import { billingRunTitle } from "@/components/billing-runs/billing-run-title";
import { FeeNotesSubNav } from "@/components/invoices/fee-notes-sub-nav";
import { EmptyState } from "@/components/empty-state";
import { Layers, Plus, AlertCircle } from "lucide-react";
import Link from "next/link";
import { Button } from "@b2mash/ui/button";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function BillingRunsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  // Server-side module gate — short-circuit BEFORE invoking backend data fetches.
  if (!(await isModuleEnabledServer("bulk_billing"))) {
    return (
      <div className="space-y-8">
        <PageHeader title="Fee runs" />
        <ModuleDisabledFallback moduleName="Bulk fee runs" slug={slug} />
      </div>
    );
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Fee runs" />
        <p className="text-muted-foreground">
          You do not have permission to view fee runs. Only admins and owners can access this page.
        </p>
      </div>
    );
  }

  let billingRuns: BillingRun[] = [];
  try {
    const result = await listBillingRuns({
      size: 100,
      sort: "createdAt,desc",
    });
    billingRuns = result.content;
  } catch {
    // Non-fatal: show empty state
  }

  const activeRun = billingRuns.find(
    (run) => run.status === "PREVIEW" || run.status === "IN_PROGRESS"
  );

  return (
    <div className="space-y-8">
      <PageHeader
        title="Fee runs"
        actions={
          <Button asChild size="sm">
            <Link href={`/org/${slug}/invoices/billing-runs/new`}>
              <Plus className="mr-1.5 size-4" />
              New fee run
            </Link>
          </Button>
        }
      />

      <FeeNotesSubNav slug={slug} current="fee-runs" />

      {/* Active Run Banner */}
      {activeRun && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/50">
          <AlertCircle className="size-5 text-amber-600 dark:text-amber-400" />
          <div className="flex-1">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
              Active fee run in progress
            </p>
            <p className="text-sm text-amber-700 dark:text-amber-300">
              &ldquo;{billingRunTitle(activeRun)}&rdquo; is currently{" "}
              {activeRun.status === "PREVIEW" ? "in preview" : "in progress"}.
            </p>
          </div>
          <Button asChild size="sm" variant="outline">
            <Link href={`/org/${slug}/invoices/billing-runs/${activeRun.id}`}>View</Link>
          </Button>
        </div>
      )}

      <BillingRunsTable
        slug={slug}
        runs={billingRuns}
        empty={
          <EmptyState
            variant="default"
            icon={Layers}
            title="No fee runs yet"
            description="Start a fee run to generate fee notes for multiple clients at once."
            actionLabel="New fee run"
            actionHref={`/org/${slug}/invoices/billing-runs/new`}
          />
        }
      />
    </div>
  );
}
