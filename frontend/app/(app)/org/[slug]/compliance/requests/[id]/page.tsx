import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getDataRequest } from "@/lib/compliance-api";
import { handleApiError } from "@/lib/api";
import { Badge } from "@b2mash/ui/badge";
import { DataRequestTimeline } from "@/components/compliance/DataRequestTimeline";
import { DataRequestActions } from "@/components/compliance/DataRequestActions";
import { STATUS_CONFIG } from "@/lib/compliance/data-request-status";
import { isOverdue, formatLocalDate, formatComplianceDateWithTime } from "@/lib/format";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import { PageHeader } from "@b2mash/ui/page-header";
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
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Data Request" />
        <p className="text-muted-foreground">
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
  const overdue =
    isOverdue(request.deadline) && request.status !== "COMPLETED" && request.status !== "REJECTED";

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/compliance/requests`}
          className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm transition-colors"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Data Requests
        </Link>
      </div>

      {/* Header */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          {/* DA-1 exception: badges sit beside the h1, which PageHeader cannot hold; same title recipe. */}
          <h1 className="font-display text-foreground text-2xl font-semibold tracking-tight">
            {request.clientName}
          </h1>
          <Badge variant={statusConfig.variant} className={statusConfig.className}>
            {statusConfig.label}
          </Badge>
          <Badge variant="neutral">{TYPE_LABELS[request.requestType] ?? request.requestType}</Badge>
        </div>

        <div className="text-muted-foreground flex flex-wrap items-center gap-4 text-sm">
          <span>Requested: {formatComplianceDateWithTime(request.requestedAt)}</span>
          <span className={cn(overdue ? "font-medium text-red-600 dark:text-red-400" : "")}>
            Deadline: {formatLocalDate(request.deadline)}
            {overdue && " (Overdue)"}
          </span>
          {(request.status === "COMPLETED" || request.status === "REJECTED") &&
            request.completedAt && (
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
          <div className="border-border rounded-lg border p-6">
            <h2 className="text-muted-foreground mb-3 text-sm font-semibold tracking-wide uppercase">
              Description
            </h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">{request.description}</p>
          </div>

          {/* Rejection reason */}
          {request.status === "REJECTED" && request.rejectionReason && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900 dark:bg-red-950/30">
              <h2 className="mb-3 text-sm font-semibold tracking-wide text-red-700 uppercase dark:text-red-400">
                Rejection Reason
              </h2>
              <p className="text-sm text-red-800 dark:text-red-300">{request.rejectionReason}</p>
            </div>
          )}

          {/* Notes */}
          {request.notes && (
            <div className="border-border rounded-lg border p-6">
              <h2 className="text-muted-foreground mb-3 text-sm font-semibold tracking-wide uppercase">
                Notes
              </h2>
              <p className="text-sm text-slate-700 dark:text-slate-300">{request.notes}</p>
            </div>
          )}

          {/* Action buttons */}
          {isAdmin && (
            <div className="border-border rounded-lg border p-6">
              <h2 className="text-muted-foreground mb-4 text-sm font-semibold tracking-wide uppercase">
                Actions
              </h2>
              <DataRequestActions request={request} slug={slug} />
            </div>
          )}
        </div>

        {/* Sidebar: timeline */}
        <div>
          <div className="border-border rounded-lg border p-6">
            <h2 className="text-muted-foreground mb-4 text-sm font-semibold tracking-wide uppercase">
              Status Timeline
            </h2>
            <DataRequestTimeline request={request} />
          </div>
        </div>
      </div>
    </div>
  );
}
