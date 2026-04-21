"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { MessageSquare } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { Skeleton } from "@/components/ui/skeleton";

interface PortalRequest {
  id: string;
  requestNumber: string;
  status: string;
  projectId: string;
  projectName: string;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  sentAt: string | null;
  completedAt: string | null;
}

export default function RequestsPage() {
  const [requests, setRequests] = useState<PortalRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    portalGet<PortalRequest[]>("/portal/requests")
      .then((data) => {
        if (!cancelled) setRequests(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : "Failed to load");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Information requests
      </h1>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}
      {!error && requests === null && (
        <div className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      )}
      {!error && requests !== null && requests.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <MessageSquare className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">No requests yet</p>
          <p className="mt-1 text-sm text-slate-500">
            Your firm will share information requests with you here.
          </p>
        </div>
      )}
      {!error && requests !== null && requests.length > 0 && (
        <ul className="space-y-3">
          {requests.map((r) => (
            <li key={r.id}>
              <Link
                href={`/requests/${r.id}`}
                className="block rounded-lg border border-slate-200 bg-white p-4 transition hover:shadow-md"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-sm text-slate-500">
                      {r.requestNumber}
                    </p>
                    <p className="mt-1 font-medium text-slate-900">
                      {r.projectName}
                    </p>
                  </div>
                  <div className="text-right text-sm text-slate-600">
                    <p>{r.status}</p>
                    <p className="mt-1 text-xs">
                      {r.submittedItems}/{r.totalItems} submitted
                    </p>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
