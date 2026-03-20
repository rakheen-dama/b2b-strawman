"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ClipboardCheck,
  Loader2,
  AlertCircle,
  ExternalLink,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { formatDate } from "@/lib/format";
import type { PendingAcceptance } from "@/lib/types/document";

function statusBadgeVariant(
  status: string,
): "warning" | "success" | "destructive" | "neutral" {
  switch (status) {
    case "PENDING":
      return "warning";
    case "ACCEPTED":
      return "success";
    case "EXPIRED":
      return "destructive";
    default:
      return "neutral";
  }
}

function isExpiringSoon(expiresAt: string | null): boolean {
  if (!expiresAt) return false;
  const diff = new Date(expiresAt).getTime() - Date.now();
  // Within 48 hours
  return diff > 0 && diff < 48 * 60 * 60 * 1000;
}

export default function PortalAcceptancesPage() {
  const router = useRouter();
  const [acceptances, setAcceptances] = useState<PendingAcceptance[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchAcceptances() {
      try {
        const data = await portalApi.get<PendingAcceptance[]>(
          "/portal/acceptance-requests/pending",
        );
        setAcceptances(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(
          err instanceof Error ? err.message : "Failed to load acceptances",
        );
      } finally {
        setIsLoading(false);
      }
    }
    fetchAcceptances();
  }, [router]);

  // Sort by sentAt descending (most recent first)
  const sortedAcceptances = [...acceptances].sort((a, b) => {
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
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            Acceptances
          </h1>
          <Badge variant="neutral">{acceptances.length}</Badge>
        </div>
      </div>

      {/* Content */}
      {sortedAcceptances.length === 0 ? (
        <EmptyState
          icon={ClipboardCheck}
          title="No pending acceptances"
          description="You don't have any documents waiting for acceptance at the moment."
        />
      ) : (
        <div className="grid gap-4">
          {sortedAcceptances.map((acceptance) => (
            <div
              key={acceptance.id}
              className="rounded-lg border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="font-semibold text-slate-900 dark:text-slate-100">
                      {acceptance.documentTitle || "Untitled Document"}
                    </h3>
                    <Badge variant={statusBadgeVariant(acceptance.status)}>
                      {acceptance.status}
                    </Badge>
                  </div>

                  <div className="mt-2 flex flex-wrap items-center gap-4 text-sm text-slate-500 dark:text-slate-400">
                    {acceptance.sentAt && (
                      <span>Sent {formatDate(acceptance.sentAt)}</span>
                    )}
                    {acceptance.expiresAt && (
                      <span
                        className={
                          isExpiringSoon(acceptance.expiresAt)
                            ? "font-medium text-amber-600 dark:text-amber-400"
                            : ""
                        }
                      >
                        Expires {formatDate(acceptance.expiresAt)}
                      </span>
                    )}
                  </div>
                </div>

                <Button asChild size="sm" variant="outline">
                  <Link href={`/accept/${acceptance.requestToken}`}>
                    Review & Accept
                    <ExternalLink className="ml-1.5 size-3.5" />
                  </Link>
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
