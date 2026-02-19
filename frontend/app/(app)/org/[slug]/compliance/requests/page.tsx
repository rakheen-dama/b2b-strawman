import { auth } from "@clerk/nextjs/server";
import { getDataRequests } from "@/lib/compliance-api";
import { EmptyState } from "@/components/empty-state";
import { DataRequestTable } from "@/components/compliance/DataRequestTable";
import { CreateDataRequestDialog } from "@/components/compliance/CreateDataRequestDialog";
import { ShieldCheck } from "lucide-react";
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
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Compliance
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view compliance data. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  let requests: DataRequestResponse[] = [];
  try {
    requests = await getDataRequests(search.status);
  } catch {
    // Non-fatal: show empty state
  }

  const statusOptions: { value: DataRequestStatus; label: string }[] = [
    { value: "RECEIVED", label: "Received" },
    { value: "IN_PROGRESS", label: "In Progress" },
    { value: "COMPLETED", label: "Completed" },
    { value: "REJECTED", label: "Rejected" },
  ];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Data Requests
          </h1>
          {requests.length > 0 && (
            <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {requests.length}
            </span>
          )}
        </div>
        <CreateDataRequestDialog slug={slug} />
      </div>

      {/* Filter Bar — pill-style links */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">Filter:</span>
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

      {/* Table or Empty State */}
      {requests.length === 0 ? (
        <EmptyState
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
