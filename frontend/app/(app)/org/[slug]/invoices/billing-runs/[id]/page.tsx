import { getAuthContext } from "@/lib/auth";
import { handleApiError } from "@/lib/api";
import { getBillingRun, getItems } from "@/lib/api/billing-runs";
import type { BillingRun, BillingRunItem } from "@/lib/api/billing-runs";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { BillingRunSummaryCards } from "@/components/billing-runs/billing-run-summary-cards";
import { BillingRunItemsTable } from "@/components/billing-runs/billing-run-items-table";
import { BillingRunDetailActions } from "@/components/billing-runs/billing-run-detail-actions";
import { formatLocalDate } from "@/lib/format";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";

export default async function BillingRunDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Billing Run
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view billing runs. Only admins and
          owners can access this page.
        </p>
      </div>
    );
  }

  let billingRun: BillingRun;
  let items: BillingRunItem[];
  try {
    [billingRun, items] = await Promise.all([
      getBillingRun(id),
      getItems(id),
    ]);
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/org/${slug}/invoices/billing-runs`}
        className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ArrowLeft className="mr-1.5 size-4" />
        Back to Billing Runs
      </Link>

      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              {billingRun.name}
            </h1>
            <BillingRunStatusBadge status={billingRun.status} />
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Period: {formatLocalDate(billingRun.periodFrom)} &ndash;{" "}
            {formatLocalDate(billingRun.periodTo)}
            {billingRun.completedAt && (
              <>
                {" "}
                &middot; Completed{" "}
                {formatLocalDate(billingRun.completedAt.split("T")[0])}
              </>
            )}
          </p>
        </div>

        <BillingRunDetailActions
          slug={slug}
          billingRunId={id}
          status={billingRun.status}
        />
      </div>

      {/* Summary Cards */}
      <BillingRunSummaryCards billingRun={billingRun} />

      {/* Items Table */}
      <div className="space-y-4">
        <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
          Items
        </h2>
        <BillingRunItemsTable
          items={items}
          currency={billingRun.currency}
          slug={slug}
        />
      </div>
    </div>
  );
}
