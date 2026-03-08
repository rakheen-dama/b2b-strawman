import { getAuthContext } from "@/lib/auth";
import { listBillingRuns } from "@/lib/api/billing-runs";
import type { BillingRun } from "@/lib/api/billing-runs";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { EmptyState } from "@/components/empty-state";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { Layers, Plus, AlertCircle } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export default async function BillingRunsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Billing Runs
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view billing runs. Only admins and
          owners can access this page.
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
    (run) => run.status === "PREVIEW" || run.status === "IN_PROGRESS",
  );

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Invoices
          </h1>
        </div>
        <Button asChild size="sm">
          <Link href={`/org/${slug}/invoices/billing-runs/new`}>
            <Plus className="mr-1.5 size-4" />
            New Billing Run
          </Link>
        </Button>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 border-b border-slate-200 dark:border-slate-800">
        <Link
          href={`/org/${slug}/invoices`}
          className="border-b-2 border-transparent px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
        >
          Invoices
        </Link>
        <Link
          href={`/org/${slug}/invoices/billing-runs`}
          className="border-b-2 border-slate-900 px-4 py-2 text-sm font-medium text-slate-900 dark:border-slate-100 dark:text-slate-100"
        >
          Billing Runs
        </Link>
      </div>

      {/* Active Run Banner */}
      {activeRun && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/50">
          <AlertCircle className="size-5 text-amber-600 dark:text-amber-400" />
          <div className="flex-1">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
              Active billing run in progress
            </p>
            <p className="text-sm text-amber-700 dark:text-amber-300">
              &ldquo;{activeRun.name}&rdquo; is currently{" "}
              {activeRun.status === "PREVIEW" ? "in preview" : "in progress"}.
            </p>
          </div>
          <Button asChild size="sm" variant="outline">
            <Link href={`/org/${slug}/invoices/billing-runs/${activeRun.id}`}>
              View
            </Link>
          </Button>
        </div>
      )}

      {/* Billing Runs Table or Empty State */}
      {billingRuns.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="No billing runs"
          description="Create a billing run to generate invoices for multiple customers at once."
          actionLabel="New Billing Run"
          actionHref={`/org/${slug}/invoices/billing-runs/new`}
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Period
                </th>
                <th className="hidden px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 md:table-cell dark:text-slate-400">
                  Customers
                </th>
                <th className="hidden px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 md:table-cell dark:text-slate-400">
                  Invoices
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Total
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 lg:table-cell dark:text-slate-400">
                  Created
                </th>
              </tr>
            </thead>
            <tbody>
              {billingRuns.map((run) => (
                <tr
                  key={run.id}
                  className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/invoices/billing-runs/${run.id}`}
                      className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                    >
                      {run.name}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <BillingRunStatusBadge status={run.status} />
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                    {formatLocalDate(run.periodFrom)} &ndash;{" "}
                    {formatLocalDate(run.periodTo)}
                  </td>
                  <td className="hidden px-4 py-3 text-right text-sm text-slate-600 md:table-cell dark:text-slate-400">
                    {run.totalCustomers ?? "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-right text-sm text-slate-600 md:table-cell dark:text-slate-400">
                    {run.totalInvoices ?? "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium text-slate-900 dark:text-slate-100">
                    {run.totalAmount != null
                      ? formatCurrency(run.totalAmount, run.currency)
                      : "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 lg:table-cell dark:text-slate-400">
                    {formatLocalDate(run.createdAt.substring(0, 10))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
