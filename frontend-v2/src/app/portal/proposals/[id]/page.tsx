"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, CheckCircle2, XCircle, Clock } from "lucide-react";
import { PortalProposalStatusBadge } from "@/components/portal/proposal-status-badge";
import { formatDate, formatCurrency, formatCurrencySafe } from "@/lib/format";
import { toast } from "@/lib/toast";
import { Button } from "@/components/ui/button";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  getPortalProposal,
  acceptProposal,
  declineProposal,
  type PortalProposalDetail,
} from "../proposal-actions";

interface PortalMilestone {
  description: string;
  percentage: number;
  relativeDueDays: number;
  sortOrder: number;
}

const FEE_MODEL_LABELS: Record<string, string> = {
  FIXED: "Fixed Fee",
  HOURLY: "Hourly",
  RETAINER: "Retainer",
};

function addDays(date: string, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export default function PortalProposalDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const [proposal, setProposal] = useState<PortalProposalDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [acceptOpen, setAcceptOpen] = useState(false);
  const [declineOpen, setDeclineOpen] = useState(false);
  const [declineReason, setDeclineReason] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const fetchProposal = useCallback(async () => {
    try {
      const data = await getPortalProposal(id);
      setProposal(data);
      setError(null);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load proposal.",
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchProposal();
  }, [fetchProposal]);

  async function handleAccept() {
    setSubmitting(true);
    try {
      const result = await acceptProposal(id);
      toast.success(result.message ?? "Proposal accepted successfully.");
      setAcceptOpen(false);
      await fetchProposal();
    } catch (err) {
      toast.error(
        err instanceof Error ? err.message : "Failed to accept proposal.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDecline() {
    setSubmitting(true);
    try {
      await declineProposal(id, declineReason || undefined);
      toast.success("Proposal declined.");
      setDeclineOpen(false);
      setDeclineReason("");
      await fetchProposal();
    } catch (err) {
      toast.error(
        err instanceof Error ? err.message : "Failed to decline proposal.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="size-6 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error || !proposal) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-sm text-red-600 dark:text-red-400">
          {error ?? "Proposal not found."}
        </p>
        <Link
          href="/portal/proposals"
          className="mt-4 text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400"
        >
          Back to proposals
        </Link>
      </div>
    );
  }

  const brandColor = proposal.orgBrandColor ?? "#0d9488";
  const milestones: PortalMilestone[] = (() => {
    try {
      return proposal.milestonesJson
        ? JSON.parse(proposal.milestonesJson)
        : [];
    } catch {
      return [];
    }
  })();
  const isSent = proposal.status === "SENT";

  return (
    <div className="space-y-6">
      {/* Back link */}
      <Link
        href="/portal/proposals"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
      >
        <ArrowLeft className="size-4" />
        Back to proposals
      </Link>

      {/* Status banners for terminal states */}
      {proposal.status === "ACCEPTED" && (
        <div className="flex items-center gap-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 dark:border-emerald-800 dark:bg-emerald-950/30">
          <CheckCircle2 className="size-5 shrink-0 text-emerald-600 dark:text-emerald-400" />
          <p className="text-sm font-medium text-emerald-800 dark:text-emerald-300">
            You have accepted this proposal.
          </p>
        </div>
      )}

      {proposal.status === "DECLINED" && (
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900">
          <XCircle className="size-5 shrink-0 text-slate-500 dark:text-slate-400" />
          <p className="text-sm font-medium text-slate-600 dark:text-slate-300">
            You have declined this proposal.
          </p>
        </div>
      )}

      {proposal.status === "EXPIRED" && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-800 dark:bg-amber-950/30">
          <Clock className="size-5 shrink-0 text-amber-600 dark:text-amber-400" />
          <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
            This proposal has expired. Contact{" "}
            {proposal.orgName ?? "your service provider"} if you&apos;d like to
            discuss further.
          </p>
        </div>
      )}

      {/* Header with branding */}
      <div
        className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
        style={{ borderLeftColor: brandColor, borderLeftWidth: "4px" }}
      >
        <div className="p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              {proposal.orgLogoUrl ? (
                <img
                  src={proposal.orgLogoUrl}
                  alt={proposal.orgName ?? "Organization logo"}
                  className="mb-3 h-8 object-contain"
                />
              ) : proposal.orgName ? (
                <p
                  className="mb-3 text-sm font-semibold"
                  style={{ color: brandColor }}
                >
                  {proposal.orgName}
                </p>
              ) : null}
              <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
                {proposal.title}
              </h1>
              <div className="flex items-center gap-3 text-sm text-slate-500 dark:text-slate-400">
                <span className="font-mono tabular-nums">
                  {proposal.proposalNumber}
                </span>
                {proposal.sentAt && (
                  <>
                    <span>&middot;</span>
                    <span>Sent {formatDate(proposal.sentAt)}</span>
                  </>
                )}
              </div>
            </div>
            <PortalProposalStatusBadge status={proposal.status} />
          </div>
        </div>
      </div>

      {/* Content body — contentHtml is pre-sanitized server-side (architecture Section 32.6.3) */}
      {proposal.contentHtml && (
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <div
            className="prose prose-slate max-w-none dark:prose-invert"
            dangerouslySetInnerHTML={{ __html: proposal.contentHtml }}
          />
        </div>
      )}

      {/* Fee summary card */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="font-display text-lg font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Fee Summary
        </h2>
        <div className="mt-4 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Fee Model
            </span>
            <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
              {FEE_MODEL_LABELS[proposal.feeModel] ?? proposal.feeModel}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Total Amount
            </span>
            <span className="font-mono tabular-nums text-lg font-semibold text-slate-900 dark:text-slate-100">
              {formatCurrencySafe(proposal.feeAmount, proposal.feeCurrency)}
            </span>
          </div>

          {/* HOURLY note */}
          {proposal.feeModel === "HOURLY" && (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Billing based on time tracked at standard rates.
            </p>
          )}

          {/* RETAINER details */}
          {proposal.feeModel === "RETAINER" && (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Monthly retainer billed at{" "}
              {formatCurrencySafe(proposal.feeAmount, proposal.feeCurrency)} per
              month.
            </p>
          )}

          {/* FIXED with milestones */}
          {proposal.feeModel === "FIXED" && milestones.length > 0 && (
            <div className="mt-2">
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Payment Milestones
              </h3>
              <div className="rounded-lg border border-slate-200 dark:border-slate-700">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Description</TableHead>
                      <TableHead className="text-right">%</TableHead>
                      <TableHead className="text-right">Amount</TableHead>
                      <TableHead className="text-right">Due Date</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {milestones
                      .sort((a, b) => a.sortOrder - b.sortOrder)
                      .map((milestone, index) => {
                        const amount =
                          proposal.feeAmount != null
                            ? (milestone.percentage / 100) * proposal.feeAmount
                            : null;
                        const dueDate =
                          proposal.sentAt
                            ? addDays(
                                proposal.sentAt,
                                milestone.relativeDueDays,
                              )
                            : null;

                        return (
                          <TableRow key={`${milestone.description}-${milestone.sortOrder}`}>
                            <TableCell className="text-sm text-slate-700 dark:text-slate-300">
                              {milestone.description}
                            </TableCell>
                            <TableCell className="text-right font-mono tabular-nums text-sm text-slate-500">
                              {milestone.percentage}%
                            </TableCell>
                            <TableCell className="text-right font-mono tabular-nums text-sm font-medium text-slate-900 dark:text-slate-100">
                              {amount != null && proposal.feeCurrency
                                ? formatCurrency(amount, proposal.feeCurrency)
                                : "\u2014"}
                            </TableCell>
                            <TableCell className="text-right text-sm text-slate-500">
                              {dueDate ? formatDate(dueDate) : "\u2014"}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Action buttons for SENT status only */}
      {isSent && (
        <div className="flex items-center gap-3">
          <Button
            onClick={() => setAcceptOpen(true)}
            style={{ backgroundColor: brandColor }}
            className="text-white hover:opacity-90"
          >
            <CheckCircle2 className="mr-2 size-4" />
            Accept Proposal
          </Button>
          <Button variant="outline" onClick={() => setDeclineOpen(true)}>
            Decline
          </Button>
        </div>
      )}

      {/* Accept confirmation dialog */}
      <AlertDialog open={acceptOpen} onOpenChange={setAcceptOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Accept Proposal</AlertDialogTitle>
            <AlertDialogDescription>
              By accepting this proposal, you agree to the scope of work and fee
              structure described above. A project will be set up for you
              automatically.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={submitting}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleAccept} disabled={submitting}>
              {submitting ? "Accepting..." : "Accept Proposal"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Decline dialog with reason */}
      <Dialog open={declineOpen} onOpenChange={setDeclineOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Decline Proposal</DialogTitle>
            <DialogDescription>
              Let us know why you&apos;re declining this proposal. This is
              optional.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="Reason for declining (optional)"
            value={declineReason}
            onChange={(e) => setDeclineReason(e.target.value)}
            rows={3}
          />
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeclineOpen(false)}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDecline}
              disabled={submitting}
            >
              {submitting ? "Declining..." : "Decline Proposal"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
