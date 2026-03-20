"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  Check,
  X,
  Calendar,
  Clock,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import {
  getPortalProposal,
  acceptProposal,
  declineProposal,
} from "@/lib/api/portal-proposals";
import { formatDate, formatCurrencySafe } from "@/lib/format";
import type { PortalProposalDetail } from "@/lib/types/document";

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

interface Milestone {
  title: string;
  amount: number | null;
  description: string | null;
}

function parseMilestones(json: string | null): Milestone[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function isExpiringSoon(expiresAt: string | null): boolean {
  if (!expiresAt) return false;
  const diff = new Date(expiresAt).getTime() - Date.now();
  return diff > 0 && diff < 48 * 60 * 60 * 1000;
}

export default function PortalProposalDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [proposal, setProposal] = useState<PortalProposalDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isAccepting, setIsAccepting] = useState(false);
  const [isDeclining, setIsDeclining] = useState(false);
  const [showDeclineDialog, setShowDeclineDialog] = useState(false);
  const [declineReason, setDeclineReason] = useState("");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const proposalId = params.id;

  const fetchProposal = useCallback(async () => {
    try {
      const data = await getPortalProposal(proposalId);
      setProposal(data);
      setError(null);
    } catch (err) {
      if (err instanceof PortalApiError && err.status === 401) {
        clearPortalAuth();
        router.replace("/portal");
        return;
      }
      setError(
        err instanceof Error ? err.message : "Failed to load proposal",
      );
    } finally {
      setIsLoading(false);
    }
  }, [proposalId, router]);

  useEffect(() => {
    fetchProposal();
  }, [fetchProposal]);

  async function handleAccept() {
    setIsAccepting(true);
    setActionError(null);
    setSuccessMessage(null);

    try {
      const result = await acceptProposal(proposalId);
      setSuccessMessage(result.message);
      await fetchProposal();
    } catch (err) {
      if (err instanceof PortalApiError && err.status === 401) {
        clearPortalAuth();
        router.replace("/portal");
        return;
      }
      setActionError(
        err instanceof Error ? err.message : "Failed to accept proposal",
      );
    } finally {
      setIsAccepting(false);
    }
  }

  async function handleDecline() {
    setIsDeclining(true);
    setActionError(null);
    setSuccessMessage(null);

    try {
      await declineProposal(proposalId, declineReason.trim() || undefined);
      setShowDeclineDialog(false);
      setDeclineReason("");
      await fetchProposal();
    } catch (err) {
      if (err instanceof PortalApiError && err.status === 401) {
        clearPortalAuth();
        router.replace("/portal");
        return;
      }
      setActionError(
        err instanceof Error ? err.message : "Failed to decline proposal",
      );
    } finally {
      setIsDeclining(false);
    }
  }

  // --- Loading state ---

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  // --- Error state ---

  if (error || !proposal) {
    return (
      <div className="space-y-4">
        <Link
          href="/portal/proposals"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Proposals
        </Link>
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {error || "Proposal not found"}
        </div>
      </div>
    );
  }

  const isActionable = proposal.status === "SENT";
  const milestones = parseMilestones(proposal.milestonesJson);

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          href="/portal/proposals"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Proposals
        </Link>
      </div>

      {/* Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            {proposal.title}
          </h1>
          <Badge variant={statusBadgeVariant(proposal.status)}>
            {statusLabel(proposal.status)}
          </Badge>
        </div>
        <p className="font-mono text-sm text-slate-500 dark:text-slate-400">
          {proposal.proposalNumber}
        </p>
      </div>

      {/* Success message */}
      {successMessage && (
        <div className="flex items-center gap-2 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-700 dark:bg-green-950 dark:text-green-300">
          <Check className="size-4 shrink-0" />
          {successMessage}
        </div>
      )}

      {/* Action error */}
      {actionError && (
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {actionError}
        </div>
      )}

      {/* Details card */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="grid gap-6 sm:grid-cols-2">
          {/* Fee details */}
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
              Fee
            </p>
            <p className="font-mono tabular-nums text-2xl font-semibold text-slate-900 dark:text-slate-100">
              {formatCurrencySafe(proposal.feeAmount, proposal.feeCurrency)}
            </p>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              {feeModelLabel(proposal.feeModel)}
            </p>
          </div>

          {/* Dates */}
          <div className="space-y-3">
            {proposal.sentAt && (
              <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                <Calendar className="size-4 shrink-0" />
                <span>Sent {formatDate(proposal.sentAt)}</span>
              </div>
            )}
            {proposal.expiresAt && (
              <div className="flex items-center gap-2 text-sm">
                <Clock className="size-4 shrink-0" />
                <span
                  className={
                    isExpiringSoon(proposal.expiresAt)
                      ? "font-medium text-amber-600 dark:text-amber-400"
                      : "text-slate-600 dark:text-slate-400"
                  }
                >
                  Expires {formatDate(proposal.expiresAt)}
                </span>
              </div>
            )}
            {proposal.orgName && (
              <p className="text-sm text-slate-500 dark:text-slate-400">
                From {proposal.orgName}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Content HTML - server-generated from org's proposal content */}
      {proposal.contentHtml && (
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <h2 className="font-display mb-4 text-lg text-slate-900 dark:text-slate-100">
            Proposal Details
          </h2>
          <div
            className="prose prose-slate dark:prose-invert max-w-none text-sm"
            dangerouslySetInnerHTML={{ __html: proposal.contentHtml }}
          />
        </div>
      )}

      {/* Milestones */}
      {milestones.length > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <h2 className="font-display mb-4 text-lg text-slate-900 dark:text-slate-100">
            Milestones
          </h2>
          <div className="space-y-3">
            {milestones.map((milestone, index) => (
              <div
                key={index}
                className="flex items-start justify-between gap-4 rounded-md border border-slate-100 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-800"
              >
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    {milestone.title}
                  </p>
                  {milestone.description && (
                    <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                      {milestone.description}
                    </p>
                  )}
                </div>
                {milestone.amount != null && (
                  <span className="font-mono tabular-nums text-sm font-medium text-slate-700 dark:text-slate-300">
                    {formatCurrencySafe(milestone.amount, proposal.feeCurrency)}
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Action buttons */}
      {isActionable && (
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <Button
            onClick={handleAccept}
            disabled={isAccepting || isDeclining}
            className="bg-green-600 text-white hover:bg-green-700"
          >
            {isAccepting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Accepting...
              </>
            ) : (
              <>
                <Check className="mr-1.5 size-4" />
                Accept Proposal
              </>
            )}
          </Button>
          <Button
            variant="destructive"
            onClick={() => setShowDeclineDialog(true)}
            disabled={isAccepting || isDeclining}
          >
            <X className="mr-1.5 size-4" />
            Decline
          </Button>
        </div>
      )}

      {/* Decline dialog */}
      <AlertDialog open={showDeclineDialog} onOpenChange={setShowDeclineDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Decline Proposal</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to decline this proposal? You can optionally
              provide a reason.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="py-2">
            <Textarea
              placeholder="Reason for declining (optional)"
              value={declineReason}
              onChange={(e) => setDeclineReason(e.target.value)}
              rows={3}
              disabled={isDeclining}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel
              disabled={isDeclining}
              onClick={() => {
                setDeclineReason("");
              }}
            >
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={isDeclining}
              onClick={(e) => {
                e.preventDefault();
                handleDecline();
              }}
            >
              {isDeclining ? (
                <>
                  <Loader2 className="mr-1.5 size-4 animate-spin" />
                  Declining...
                </>
              ) : (
                "Decline Proposal"
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
