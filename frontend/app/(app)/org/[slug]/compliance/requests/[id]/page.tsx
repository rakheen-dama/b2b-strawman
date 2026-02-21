import { getAuthContext } from "@/lib/auth";
import { getDataRequest } from "@/lib/compliance-api";
import { handleApiError } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { DataRequestTimeline } from "@/components/compliance/DataRequestTimeline";
import { DataRequestActions } from "@/components/compliance/DataRequestActions";
import {
  STATUS_CONFIG,
} from "@/components/compliance/DataRequestTable";
import { isOverdue, formatLocalDate, formatComplianceDateWithTime } from "@/lib/format";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import type { DataRequestType } from "@/lib/types";

const TYPE_LABELS: Record<DataRequestType, string> = {
  ACCESS: "Access Request",
  DELETION: "Deletion Request",
  CORRECTION: "Correction Request",
  OBJECTION: "Objection Request",
};

export default async function DataRequestDetailPage({
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
          Data Request
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view data requests. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  let request;
  try {
    request = await getDataRequest(id);
  } catch (error) {
    handleApiError(error);
    return null;
  }

  const statusConfig = STATUS_CONFIG[request.status] ?? {
    label: request.status,
    variant: "neutral" as const,
  };
  const overdue = isOverdue(request.deadline) && request.status !== "COMPLETED" && request.status !== "REJECTED";

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/compliance/requests`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Data Requests
        </Link>
      </div>

      {/* Header */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-display text-3xl font-bold text-slate-950 dark:text-slate-50">
            {request.customerName}
          </h1>
          <Badge
            variant={statusConfig.variant}
            className={statusConfig.className}
          >
            {statusConfig.label}
          </Badge>
          <Badge variant="neutral">
            {TYPE_LABELS[request.requestType] ?? request.requestType}
          </Badge>
        </div>

        <div className="flex flex-wrap items-center gap-4 text-sm text-slate-600 dark:text-slate-400">
          <span>
            Requested: {formatComplianceDateWithTime(request.requestedAt)}
          </span>
          <span
            className={cn(
              overdue ? "text-red-600 dark:text-red-400 font-medium" : "",
            )}
          >
            Deadline: {formatLocalDate(request.deadline)}
            {overdue && " (Overdue)"}
          </span>
          {(request.status === "COMPLETED" || request.status === "REJECTED") && request.completedAt && (
            <span>
              {request.status === "COMPLETED" ? "Completed" : "Rejected"}:{" "}
              {formatComplianceDateWithTime(request.completedAt)}
            </span>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Main content */}
        <div className="space-y-6 lg:col-span-2">
          {/* Description */}
          <div className="rounded-lg border border-slate-200 p-6 dark:border-slate-800">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Description
            </h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">{request.description}</p>
          </div>

          {/* Rejection reason */}
          {request.status === "REJECTED" && request.rejectionReason && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900 dark:bg-red-950/30">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-red-700 dark:text-red-400">
                Rejection Reason
              </h2>
              <p className="text-sm text-red-800 dark:text-red-300">{request.rejectionReason}</p>
            </div>
          )}

          {/* Notes */}
          {request.notes && (
            <div className="rounded-lg border border-slate-200 p-6 dark:border-slate-800">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Notes
              </h2>
              <p className="text-sm text-slate-700 dark:text-slate-300">{request.notes}</p>
            </div>
          )}

          {/* Action buttons */}
          {isAdmin && (
            <div className="rounded-lg border border-slate-200 p-6 dark:border-slate-800">
              <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Actions
              </h2>
              <DataRequestActions request={request} slug={slug} />
            </div>
          )}
        </div>

        {/* Sidebar: timeline */}
        <div>
          <div className="rounded-lg border border-slate-200 p-6 dark:border-slate-800">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Status Timeline
            </h2>
            <DataRequestTimeline request={request} />
          </div>
        </div>
      </div>
    </div>
  );
}
