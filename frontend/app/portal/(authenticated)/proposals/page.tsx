"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FileSignature, Loader2, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { listPortalProposals } from "@/lib/api/portal-proposals";
import { formatDate, formatCurrencySafe } from "@/lib/format";
import type { PortalProposalSummary } from "@/lib/types/document";

function statusBadgeVariant(
  status: string,
): "warning" | "success" | "destructive" | "neutral" {
  switch (status) {
    case "SENT":
      return "warning";
    case "ACCEPTED":
      return "success";
    case "DECLINED":
      return "destructive";
    case "EXPIRED":
    case "DRAFT":
    default:
      return "neutral";
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "SENT":
      return "Pending";
    case "ACCEPTED":
      return "Accepted";
    case "DECLINED":
      return "Declined";
    case "EXPIRED":
      return "Expired";
    case "DRAFT":
      return "Draft";
    default:
      return status;
  }
}

function feeModelLabel(feeModel: string): string {
  switch (feeModel) {
    case "FIXED":
      return "Fixed Fee";
    case "HOURLY":
      return "Hourly Rate";
    case "RETAINER":
      return "Retainer";
    case "MILESTONE":
      return "Milestone";
    default:
      return feeModel;
  }
}

export default function PortalProposalListPage() {
  const router = useRouter();
  const [proposals, setProposals] = useState<PortalProposalSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProposals() {
      try {
        const data = await listPortalProposals();
        setProposals(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(
          err instanceof Error ? err.message : "Failed to load proposals",
        );
      } finally {
        setIsLoading(false);
      }
    }
    fetchProposals();
  }, [router]);

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
            Proposals
          </h1>
          <Badge variant="neutral">{proposals.length}</Badge>
        </div>
      </div>

      {/* Content */}
      {proposals.length === 0 ? (
        <EmptyState
          icon={FileSignature}
          title="No proposals"
          description="You don't have any proposals at the moment."
        />
      ) : (
        <div className="grid gap-4">
          {proposals.map((proposal) => (
            <Link
              key={proposal.id}
              href={`/portal/proposals/${proposal.id}`}
              className="group rounded-lg border border-slate-200 bg-white p-5 transition-all hover:border-slate-300 hover:shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="font-semibold text-slate-900 group-hover:text-slate-950 dark:text-slate-100 dark:group-hover:text-slate-50">
                      {proposal.title}
                    </h3>
                    <Badge variant={statusBadgeVariant(proposal.status)}>
                      {statusLabel(proposal.status)}
                    </Badge>
                  </div>

                  <div className="mt-2 flex flex-wrap items-center gap-4 text-sm text-slate-500 dark:text-slate-400">
                    <span className="font-mono tabular-nums">
                      {proposal.proposalNumber}
                    </span>
                    <span>{feeModelLabel(proposal.feeModel)}</span>
                    <span className="font-mono tabular-nums font-medium text-slate-700 dark:text-slate-300">
                      {formatCurrencySafe(
                        proposal.feeAmount,
                        proposal.feeCurrency,
                      )}
                    </span>
                    {proposal.sentAt && (
                      <span>Sent {formatDate(proposal.sentAt)}</span>
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
