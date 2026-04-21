"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle, XCircle, Clock } from "lucide-react";
import { portalGet, portalPost } from "@/lib/api-client";
import { formatCurrency, formatDate } from "@/lib/format";
import { ProposalStatusBadge } from "@/components/proposal-status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import type {
  PortalProposalDetail,
  PortalAcceptResponse,
  PortalDeclineResponse,
} from "@/lib/types";

function PageSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-48" />
      <Skeleton className="h-24" />
    </div>
  );
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
      return "Milestone-Based";
    default:
      return feeModel.replace(/_/g, " ");
  }
}

export default function ProposalDetailPage() {
  const params = useParams();
  const proposalId = Array.isArray(params.id)
    ? params.id[0]
    : (params.id ?? "");

  const [proposal, setProposal] = useState<PortalProposalDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [isAccepting, setIsAccepting] = useState(false);
  const [isDeclining, setIsDeclining] = useState(false);
  const [showDeclineForm, setShowDeclineForm] = useState(false);
  const [declineReason, setDeclineReason] = useState("");
  const [actionMessage, setActionMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const fetchProposal = useCallback(async () => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalProposalDetail>(
        `/portal/api/proposals/${proposalId}`,
      );
      setProposal(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load proposal");
    } finally {
      setIsLoading(false);
    }
  }, [proposalId]);

  useEffect(() => {
    fetchProposal();
  }, [fetchProposal]);

  async function handleAccept() {
    setIsAccepting(true);
    setActionMessage(null);
    try {
      const result = await portalPost<PortalAcceptResponse>(
        `/portal/api/proposals/${proposalId}/accept`,
        {},
      );
      setActionMessage({ type: "success", text: result.message });
      setProposal((prev) => (prev ? { ...prev, status: "ACCEPTED" } : prev));
    } catch (err) {
      setActionMessage({
        type: "error",
        text:
          err instanceof Error ? err.message : "Failed to accept proposal",
      });
    } finally {
      setIsAccepting(false);
    }
  }

  async function handleDecline() {
    setIsDeclining(true);
    setActionMessage(null);
    try {
      await portalPost<PortalDeclineResponse>(
        `/portal/api/proposals/${proposalId}/decline`,
        { reason: declineReason || null },
      );
      setActionMessage({
        type: "success",
        text: "Proposal has been declined.",
      });
      setProposal((prev) => (prev ? { ...prev, status: "DECLINED" } : prev));
      setShowDeclineForm(false);
    } catch (err) {
      setActionMessage({
        type: "error",
        text:
          err instanceof Error ? err.message : "Failed to decline proposal",
      });
    } finally {
      setIsDeclining(false);
    }
  }

  if (isLoading) {
    return <PageSkeleton />;
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        <p className="mb-2">{error}</p>
        <button
          type="button"
          onClick={() => fetchProposal()}
          className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
        >
          Try again
        </button>
      </div>
    );
  }

  if (!proposal) return null;

  const isSent = proposal.status === "SENT";
  const showStickyBar = isSent && !showDeclineForm;

  return (
    <>
      <div className={showStickyBar ? "space-y-8 pb-24 md:pb-0" : "space-y-8"}>
      {/* Back link */}
      <Link
        href="/proposals"
        className="inline-flex min-h-11 items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to proposals
      </Link>

      {/* Header */}
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-display text-2xl font-semibold text-slate-900">
              {proposal.title}
            </h1>
            <ProposalStatusBadge status={proposal.status} />
          </div>
          <div className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
            <span>{proposal.proposalNumber}</span>
            {proposal.sentAt && (
              <span>Sent: {formatDate(proposal.sentAt)}</span>
            )}
            {proposal.expiresAt && (
              <span>Expires: {formatDate(proposal.expiresAt)}</span>
            )}
          </div>
        </div>
      </div>

      {/* Action message */}
      {actionMessage && (
        <div
          className={`flex items-center gap-3 rounded-lg border p-4 ${
            actionMessage.type === "success"
              ? "border-green-200 bg-green-50"
              : "border-red-200 bg-red-50"
          }`}
        >
          {actionMessage.type === "success" ? (
            <CheckCircle className="size-5 text-green-600" />
          ) : (
            <XCircle className="size-5 text-red-600" />
          )}
          <p
            className={`text-sm font-medium ${
              actionMessage.type === "success"
                ? "text-green-700"
                : "text-red-700"
            }`}
          >
            {actionMessage.text}
          </p>
        </div>
      )}

      {/* Status banners */}
      {proposal.status === "ACCEPTED" && !actionMessage && (
        <div className="flex items-center gap-3 rounded-lg border border-green-200 bg-green-50 p-4">
          <CheckCircle className="size-5 text-green-600" />
          <p className="text-sm font-medium text-green-700">
            This proposal has been accepted.
          </p>
        </div>
      )}
      {proposal.status === "DECLINED" && !actionMessage && (
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <XCircle className="size-5 text-slate-500" />
          <p className="text-sm font-medium text-slate-600">
            This proposal was declined.
          </p>
        </div>
      )}
      {proposal.status === "EXPIRED" && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <Clock className="size-5 text-amber-600" />
          <p className="text-sm font-medium text-amber-700">
            This proposal has expired.
          </p>
        </div>
      )}

      {/* Fee details */}
      <section>
        <h2 className="font-display mb-3 text-lg font-semibold text-slate-900">
          Fee Details
        </h2>
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex flex-col gap-4 text-sm sm:flex-row sm:flex-wrap sm:gap-8">
            <div>
              <span className="font-medium text-slate-500">Fee Model</span>
              <p className="mt-1 text-slate-900">
                {feeModelLabel(proposal.feeModel)}
              </p>
            </div>
            {proposal.feeAmount != null && proposal.feeCurrency && (
              <div>
                <span className="font-medium text-slate-500">Amount</span>
                <p className="mt-1 text-lg font-semibold text-slate-900">
                  {formatCurrency(proposal.feeAmount, proposal.feeCurrency)}
                </p>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Content HTML — server-generated from Tiptap rendering pipeline, not user input */}
      {proposal.contentHtml && (
        <section>
          <h2 className="font-display mb-3 text-lg font-semibold text-slate-900">
            Proposal Details
          </h2>
          <div
            className="prose prose-sm prose-slate max-w-none rounded-lg border border-slate-200 bg-white p-6"
            dangerouslySetInnerHTML={{ __html: proposal.contentHtml }}
          />
        </section>
      )}

      {/* Accept / Decline actions */}
      {isSent && (
        <section className="rounded-lg border border-teal-200 bg-teal-50 p-6">
          <h2 className="font-display mb-2 text-lg font-semibold text-slate-900">
            Your Response
          </h2>
          <p className="mb-4 text-sm text-slate-600">
            Please review the proposal above and accept or decline.
          </p>

          {!showDeclineForm ? (
            <div className="hidden flex-wrap gap-3 md:flex">
              <Button
                onClick={handleAccept}
                disabled={isAccepting}
                className="min-h-11 bg-teal-600 text-white hover:bg-teal-700 md:min-h-9"
              >
                {isAccepting ? "Accepting..." : "Accept Proposal"}
              </Button>
              <Button
                variant="outline"
                onClick={() => setShowDeclineForm(true)}
                disabled={isAccepting}
                className="min-h-11 md:min-h-9"
              >
                Decline
              </Button>
            </div>
          ) : (
            <div className="space-y-3">
              <label
                htmlFor="decline-reason"
                className="block text-sm font-medium text-slate-700"
              >
                Reason for declining (optional)
              </label>
              <textarea
                id="decline-reason"
                value={declineReason}
                onChange={(e) => setDeclineReason(e.target.value)}
                rows={3}
                className="w-full min-h-24 rounded-md border border-slate-300 px-3 py-2 text-base text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 md:text-sm"
                placeholder="Let them know why you are declining..."
              />
              <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                <Button
                  variant="destructive"
                  onClick={handleDecline}
                  disabled={isDeclining}
                  className="min-h-11 md:min-h-9"
                >
                  {isDeclining ? "Declining..." : "Confirm Decline"}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => {
                    setShowDeclineForm(false);
                    setDeclineReason("");
                  }}
                  disabled={isDeclining}
                  className="min-h-11 md:min-h-9"
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}
        </section>
      )}
      </div>

      {/* Mobile sticky action bar for Accept / Decline */}
      {showStickyBar && (
        <StickyActionBar>
          <Button
            onClick={handleAccept}
            disabled={isAccepting}
            className="min-h-11 flex-1 bg-teal-600 text-white hover:bg-teal-700"
          >
            {isAccepting ? "Accepting..." : "Accept"}
          </Button>
          <Button
            variant="outline"
            onClick={() => setShowDeclineForm(true)}
            disabled={isAccepting}
            className="min-h-11 flex-1"
          >
            Decline
          </Button>
        </StickyActionBar>
      )}
    </>
  );
}
