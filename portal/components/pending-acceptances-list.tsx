"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileCheck, Clock, ArrowRight } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatDate } from "@/lib/format";
import { Skeleton } from "@/components/ui/skeleton";
import type { PortalPendingAcceptance } from "@/lib/types";

function AcceptanceSkeleton() {
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-200/80 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-5 w-48" />
        <div className="flex gap-4">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-3 w-24" />
        </div>
      </div>
      <Skeleton className="h-8 w-28" />
    </div>
  );
}

export function PendingAcceptancesList() {
  const [acceptances, setAcceptances] = useState<PortalPendingAcceptance[]>(
    [],
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchAcceptances() {
      try {
        const data = await portalGet<PortalPendingAcceptance[]>(
          "/portal/acceptance-requests/pending",
        );
        if (!cancelled) {
          setAcceptances(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Failed to load pending acceptances",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchAcceptances();

    return () => {
      cancelled = true;
    };
  }, []);

  if (isLoading) {
    return (
      <div className="mb-8">
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Pending Acceptances
        </h2>
        <div className="flex flex-col gap-3">
          {Array.from({ length: 2 }).map((_, i) => (
            <AcceptanceSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mb-8">
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      </div>
    );
  }

  // Don't render anything when there are no pending acceptances
  if (acceptances.length === 0) {
    return null;
  }

  return (
    <div className="mb-8">
      <div className="mb-4 flex items-center gap-2">
        <FileCheck className="size-5 text-teal-600" />
        <h2 className="font-display text-lg font-semibold text-slate-900">
          Pending Acceptances
        </h2>
      </div>
      <div className="flex flex-col gap-3">
        {acceptances.map((acceptance) => (
          <div
            key={acceptance.id}
            className="flex flex-col gap-3 rounded-lg border border-teal-200/60 bg-teal-50/30 p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-slate-900">
                {acceptance.documentTitle}
              </p>
              <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
                <span className="flex items-center gap-1">
                  <Clock className="size-3" />
                  Sent {formatDate(acceptance.sentAt)}
                </span>
                <span>Expires {formatDate(acceptance.expiresAt)}</span>
              </div>
            </div>
            <Link
              href={`/accept/${acceptance.requestToken}`}
              className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-md bg-teal-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-teal-700 sm:w-auto"
            >
              Review & Accept
              <ArrowRight className="size-4" />
            </Link>
          </div>
        ))}
      </div>
    </div>
  );
}
