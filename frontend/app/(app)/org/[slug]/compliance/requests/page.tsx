import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getDataRequests } from "@/lib/compliance-api";
import { EmptyState } from "@/components/empty-state";
import { DataRequestTable } from "@/components/compliance/DataRequestTable";
import { CreateDataRequestDialog } from "@/components/compliance/CreateDataRequestDialog";
import { ShieldCheck } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import Link from "next/link";
import type { DataRequestStatus, DataRequestResponse } from "@/lib/types";

export default async function DataRequestsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Compliance" />
        <p className="text-muted-foreground">
          You do not have permission to view compliance data. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  let requests: DataRequestResponse[] = [];
  let loadError = false;
  try {
    requests = await getDataRequests(search.status as DataRequestStatus | undefined); // cast: URL param is untyped string
  } catch {
    loadError = true;
  }

  const statusOptions: { value: DataRequestStatus; label: string }[] = [
    { value: "RECEIVED", label: "Received" },
    { value: "IN_PROGRESS", label: "In Progress" },
    { value: "COMPLETED", label: "Completed" },
    { value: "REJECTED", label: "Rejected" },
  ];

  return (
    <div className="space-y-8">
      {/* Page Header — the count pill sits in `actions`, outside the h1 (DA-2) */}
      <PageHeader
        title="Data Requests"
        actions={
          <>
            {requests.length > 0 && (
              <span className="bg-muted text-muted-foreground rounded-full px-2.5 py-0.5 text-sm">
                {requests.length}
              </span>
            )}
            <CreateDataRequestDialog slug={slug} />
          </>
        }
      />

      {/* Filter Bar — pill-style links */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-sm font-medium">Filter:</span>
        <Link
          href={`/org/${slug}/compliance/requests`}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !search.status
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
          }`}
        >
          All
        </Link>
        {statusOptions.map((option) => (
          <Link
            key={option.value}
            href={`/org/${slug}/compliance/requests?status=${option.value}`}
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              search.status === option.value
                ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            }`}
          >
            {option.label}
          </Link>
        ))}
      </div>

      {/* Table, Error, or Empty State */}
      {loadError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900 dark:bg-red-950/30">
          <p className="text-sm text-red-700 dark:text-red-400">
            Failed to load data requests. Please try again.
          </p>
        </div>
      ) : requests.length === 0 ? (
        <EmptyState
          variant="default"
          icon={ShieldCheck}
          title="No data requests found"
          description={
            search.status
              ? `No ${search.status.toLowerCase().replace("_", " ")} data requests.`
              : "Create a new data subject request to get started."
          }
        />
      ) : (
        <DataRequestTable requests={requests} slug={slug} />
      )}
    </div>
  );
}
