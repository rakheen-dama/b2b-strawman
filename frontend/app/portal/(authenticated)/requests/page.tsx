"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Inbox, Loader2, AlertCircle, FolderOpen } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { PortalRequestStatusBadge } from "@/components/portal/portal-request-status-badge";
import { PortalRequestProgressBar } from "@/components/portal/portal-request-progress-bar";
import { listPortalRequests } from "@/lib/api/portal-requests";
import type { PortalRequestListItem } from "@/lib/api/portal-requests";
import { PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { formatDate } from "@/lib/format";
import { useRouter } from "next/navigation";
import { cn } from "@/lib/utils";

type TabFilter = "open" | "completed";

export default function PortalRequestListPage() {
  const router = useRouter();
  const [requests, setRequests] = useState<PortalRequestListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabFilter>("open");

  useEffect(() => {
    async function fetchRequests() {
      try {
        const data = await listPortalRequests();
        setRequests(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load requests");
      } finally {
        setIsLoading(false);
      }
    }
    fetchRequests();
  }, [router]);

  const openRequests = requests.filter((r) => r.status === "SENT" || r.status === "IN_PROGRESS");
  const completedRequests = requests.filter(
    (r) => r.status === "COMPLETED" || r.status === "CANCELLED"
  );

  const filteredRequests = activeTab === "open" ? openRequests : completedRequests;

  // Sort by most recent sentAt descending
  const sortedRequests = [...filteredRequests].sort((a, b) => {
    if (a.sentAt && b.sentAt) return b.sentAt.localeCompare(a.sentAt);
    if (a.sentAt) return -1;
    if (b.sentAt) return 1;
    return 0;
  });

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
        role="alert"
      >
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">Requests</h1>
          <Badge variant="neutral">{requests.length}</Badge>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200 dark:border-slate-800">
        <button
          onClick={() => setActiveTab("open")}
          className={cn(
            "px-4 py-2 text-sm font-medium transition-colors",
            activeTab === "open"
              ? "border-b-2 border-slate-900 text-slate-900 dark:border-slate-100 dark:text-slate-100"
              : "text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
          )}
        >
          Open ({openRequests.length})
        </button>
        <button
          onClick={() => setActiveTab("completed")}
          className={cn(
            "px-4 py-2 text-sm font-medium transition-colors",
            activeTab === "completed"
              ? "border-b-2 border-slate-900 text-slate-900 dark:border-slate-100 dark:text-slate-100"
              : "text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
          )}
        >
          Completed ({completedRequests.length})
        </button>
      </div>

      {/* Content */}
      {sortedRequests.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title={activeTab === "open" ? "No open requests" : "No completed requests"}
          description={
            activeTab === "open"
              ? "You don't have any pending requests at the moment"
              : "No requests have been completed yet"
          }
        />
      ) : (
        <div className="grid gap-4">
          {sortedRequests.map((request) => (
            <Link
              key={request.id}
              href={`/portal/requests/${request.id}`}
              className="group rounded-lg border border-slate-200 bg-white p-5 transition-all hover:border-slate-300 hover:shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="font-semibold text-slate-900 group-hover:text-slate-950 dark:text-slate-100 dark:group-hover:text-slate-50">
                      {request.requestNumber}
                    </h3>
                    <PortalRequestStatusBadge status={request.status} />
                  </div>
                  {request.projectName && (
                    <div className="mt-1 flex items-center gap-1.5 text-sm text-slate-600 dark:text-slate-400">
                      <FolderOpen className="size-3.5" />
                      <span>{request.projectName}</span>
                    </div>
                  )}
                  <div className="mt-3 flex items-center gap-4">
                    <PortalRequestProgressBar
                      totalItems={request.totalItems}
                      acceptedItems={request.acceptedItems}
                    />
                    {request.sentAt && (
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        Sent {formatDate(request.sentAt)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
