import { getAuthContext } from "@/lib/auth";
import { FileText, Plus } from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { fetchRetainers } from "@/lib/api/retainers";
import type { RetainerResponse, RetainerStatus } from "@/lib/api/retainers";
import type { Customer } from "@/lib/types";
import { RetainerSummaryCards } from "@/components/retainers/retainer-summary-cards";
import { RetainerList } from "@/components/retainers/retainer-list";
import { CreateRetainerDialog } from "@/components/retainers/create-retainer-dialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";

const STATUS_FILTERS: { label: string; value: RetainerStatus | null }[] = [
  { label: "All", value: null },
  { label: "Active", value: "ACTIVE" },
  { label: "Paused", value: "PAUSED" },
  { label: "Terminated", value: "TERMINATED" },
];

function computeSummary(retainers: RetainerResponse[]) {
  let activeCount = 0;
  let readyToCloseCount = 0;
  let totalOverageHours = 0;

  for (const r of retainers) {
    if (r.status === "ACTIVE") activeCount++;
    if (r.currentPeriod?.readyToClose) readyToCloseCount++;
    if (r.currentPeriod && (r.currentPeriod.overageHours ?? 0) > 0) {
      totalOverageHours += r.currentPeriod.overageHours ?? 0;
    }
  }

  return { activeCount, readyToCloseCount, totalOverageHours };
}

export default async function RetainersPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string; customerId?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Retainers
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view retainers.
        </p>
      </div>
    );
  }

  const [retainersResult, customersResult] = await Promise.allSettled([
    fetchRetainers({
      status: search.status as RetainerStatus | undefined,
      customerId: search.customerId,
    }),
    api.get<Customer[]>("/api/customers"),
  ]);

  const retainers: RetainerResponse[] =
    retainersResult.status === "fulfilled" ? retainersResult.value : [];

  const customers: Array<{ id: string; name: string; email: string }> =
    customersResult.status === "fulfilled"
      ? customersResult.value
          .filter(
            (c) =>
              c.lifecycleStatus !== "OFFBOARDED" &&
              c.lifecycleStatus !== "PROSPECT",
          )
          .map((c) => ({ id: c.id, name: c.name, email: c.email }))
      : [];

  const summary = computeSummary(retainers);

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Retainers
          </h1>
          {retainers.length > 0 && (
            <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {retainers.length}
            </span>
          )}
        </div>
        <CreateRetainerDialog slug={slug} customers={customers}>
          <Button>
            <Plus className="mr-2 size-4" />
            New Retainer
          </Button>
        </CreateRetainerDialog>
      </div>

      {/* Summary Cards */}
      <RetainerSummaryCards
        activeCount={summary.activeCount}
        readyToCloseCount={summary.readyToCloseCount}
        totalOverageHours={summary.totalOverageHours}
      />

      {/* URL-based Status Filter Tabs */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
          Filter:
        </span>
        {STATUS_FILTERS.map((filter) => (
          <Link
            key={filter.label}
            href={
              filter.value
                ? `/org/${slug}/retainers?status=${filter.value}`
                : `/org/${slug}/retainers`
            }
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              (filter.value === null && !search.status) ||
              search.status === filter.value
                ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            }`}
          >
            {filter.label}
          </Link>
        ))}
      </div>

      {/* Retainer List */}
      {retainers.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No retainers found"
          description="Create a retainer agreement to track recurring client engagements."
        />
      ) : (
        <RetainerList slug={slug} retainers={retainers} />
      )}
    </div>
  );
}
