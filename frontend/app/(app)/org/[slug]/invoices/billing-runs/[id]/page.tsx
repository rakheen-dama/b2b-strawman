import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { handleApiError } from "@/lib/api";
import { getBillingRun, getItems } from "@/lib/api/billing-runs";
import type { BillingRun, BillingRunItem } from "@/lib/api/billing-runs";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { BillingRunSummaryCards } from "@/components/billing-runs/billing-run-summary-cards";
import { BillingRunItemsTable } from "@/components/billing-runs/billing-run-items-table";
import { BillingRunDetailActions } from "@/components/billing-runs/billing-run-detail-actions";
import { billingRunTitle } from "@/components/billing-runs/billing-run-title";
import { formatLocalDate } from "@/lib/format";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { BreadcrumbLabel } from "@/components/breadcrumb-label-provider";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

export default async function BillingRunDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;

  // Server-side module gate — short-circuit BEFORE invoking backend data fetches.
  if (!(await isModuleEnabledServer("bulk_billing"))) {
    return (
      <div className="space-y-8">
        <PageHeader title="Fee run" />
        <ModuleDisabledFallback moduleName="Bulk fee runs" slug={slug} />
      </div>
    );
  }

  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Fee run" />
        <p className="text-muted-foreground">
          You do not have permission to view fee runs. Only admins and owners can access this page.
        </p>
      </div>
    );
  }

  let billingRun: BillingRun;
  let items: BillingRunItem[];
  try {
    [billingRun, items] = await Promise.all([getBillingRun(id), getItems(id)]);
  } catch (error) {
    handleApiError(error);
  }

  const title = billingRunTitle(billingRun);

  return (
    <div className="space-y-8">
      <BreadcrumbLabel href={`/org/${slug}/invoices/billing-runs/${id}`} label={title} />
      {/* Back link */}
      <Link
        href={`/org/${slug}/invoices/billing-runs`}
        className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm transition-colors"
      >
        <ArrowLeft className="mr-1.5 size-4" />
        Back to Fee runs
      </Link>

      {/* The status badge sits in the toolbar row, never inside the h1 (its accessible name). */}
      <PageHeader
        title={title}
        description={
          <>
            Period: {formatLocalDate(billingRun.periodFrom)} &ndash;{" "}
            {formatLocalDate(billingRun.periodTo)}
            {billingRun.completedAt && (
              <> &middot; Completed {formatLocalDate(billingRun.completedAt.split("T")[0])}</>
            )}
          </>
        }
        actions={
          <BillingRunDetailActions slug={slug} billingRunId={id} status={billingRun.status} />
        }
      >
        <BillingRunStatusBadge status={billingRun.status} />
      </PageHeader>

      {/* Summary Cards */}
      <BillingRunSummaryCards billingRun={billingRun} />

      {/* Items Table */}
      <div className="space-y-4">
        <h2 className="font-display text-foreground text-xl">Items</h2>
        <BillingRunItemsTable items={items} currency={billingRun.currency} slug={slug} />
      </div>
    </div>
  );
}
