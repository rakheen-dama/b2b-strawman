"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  Send,
  Pencil,
  Trash2,
  Copy,
  Undo2,
  ExternalLink,
  Users,
  Calendar,
  User,
  Loader2,
} from "lucide-react";

import type {
  ProposalDetailResponse,
  CreateProposalData,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import {
  deleteProposal,
  withdrawProposal,
  createProposal,
  replaceMilestones,
  replaceTeamMembers,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { formatDate, formatCurrencySafe } from "@/lib/format";
import { toast } from "@/lib/toast";
import { StatusBadge } from "@/components/ui/status-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { ProposalPreview } from "./proposal-preview";
import { SendProposalDialog } from "./send-proposal-dialog";

interface ProposalDetailClientProps {
  proposal: ProposalDetailResponse;
  orgSlug: string;
}

function formatFeeModelLabel(model: string): string {
  const map: Record<string, string> = {
    FIXED: "Fixed Fee",
    HOURLY: "Hourly Rate",
    RETAINER: "Retainer",
  };
  return map[model] ?? model;
}

export function ProposalDetailClient({
  proposal,
  orgSlug,
}: ProposalDetailClientProps) {
  const router = useRouter();
  const [loadingAction, setLoadingAction] = React.useState<string | null>(null);
  const [sendDialogOpen, setSendDialogOpen] = React.useState(false);

  const executeAction = async (name: string, fn: () => Promise<void>) => {
    setLoadingAction(name);
    try {
      await fn();
    } finally {
      setLoadingAction(null);
    }
  };

  async function handleDelete() {
    await executeAction("delete", async () => {
      try {
        await deleteProposal(proposal.id);
        toast.success("Proposal deleted");
        router.push(`/org/${orgSlug}/proposals`);
      } catch {
        toast.error("Failed to delete proposal");
      }
    });
  }

  async function handleWithdraw() {
    await executeAction("withdraw", async () => {
      try {
        await withdrawProposal(proposal.id);
        toast.success("Proposal withdrawn");
        router.refresh();
      } catch {
        toast.error("Failed to withdraw proposal");
      }
    });
  }

  async function handleCopy() {
    await executeAction("copy", async () => {
      try {
        const createData: CreateProposalData = {
          title: `Copy of ${proposal.title}`,
          customerId: proposal.customerId,
          feeModel: proposal.feeModel,
          ...(proposal.fixedFeeAmount != null && {
            fixedFeeAmount: proposal.fixedFeeAmount,
          }),
          ...(proposal.fixedFeeCurrency && {
            fixedFeeCurrency: proposal.fixedFeeCurrency,
          }),
          ...(proposal.hourlyRateNote && {
            hourlyRateNote: proposal.hourlyRateNote,
          }),
          ...(proposal.retainerAmount != null && {
            retainerAmount: proposal.retainerAmount,
          }),
          ...(proposal.retainerCurrency && {
            retainerCurrency: proposal.retainerCurrency,
          }),
          ...(proposal.retainerHoursIncluded != null && {
            retainerHoursIncluded: proposal.retainerHoursIncluded,
          }),
          ...(proposal.contentJson && { contentJson: proposal.contentJson }),
          ...(proposal.projectTemplateId && {
            projectTemplateId: proposal.projectTemplateId,
          }),
        };

        const newProposal = await createProposal(createData);

        if (proposal.milestones.length > 0) {
          await replaceMilestones(
            newProposal.id,
            proposal.milestones.map((m) => ({
              description: m.description,
              percentage: m.percentage,
              relativeDueDays: m.relativeDueDays,
            })),
          );
        }

        if (proposal.teamMembers.length > 0) {
          await replaceTeamMembers(
            newProposal.id,
            proposal.teamMembers.map((t) => ({
              memberId: t.memberId,
              role: t.role ?? "",
            })),
          );
        }

        toast.success("Proposal copied");
        router.push(`/org/${orgSlug}/proposals/${newProposal.id}/edit`);
      } catch {
        toast.error("Failed to copy proposal");
      }
    });
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Back link */}
      <Link
        href={`/org/${orgSlug}/proposals`}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 transition-colors hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Proposals
      </Link>

      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm text-slate-400">
              {proposal.proposalNumber}
            </span>
            <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
              {proposal.title}
            </h1>
            <StatusBadge status={proposal.status} />
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
            {proposal.customerName && (
              <Link
                href={`/org/${orgSlug}/customers/${proposal.customerId}`}
                className="flex items-center gap-1 transition-colors hover:text-teal-600"
              >
                <User className="size-3.5" />
                {proposal.customerName}
              </Link>
            )}
            {proposal.createdByName && (
              <span className="flex items-center gap-1">
                <Users className="size-3.5" />
                Created by {proposal.createdByName}
              </span>
            )}
            <span className="flex items-center gap-1">
              <Calendar className="size-3.5" />
              Created {formatDate(proposal.createdAt)}
            </span>
            {proposal.sentAt && (
              <span>Sent {formatDate(proposal.sentAt)}</span>
            )}
            {proposal.expiresAt && (
              <span>Expires {formatDate(proposal.expiresAt)}</span>
            )}
            {proposal.acceptedAt && (
              <span className="text-emerald-600">
                Accepted {formatDate(proposal.acceptedAt)}
              </span>
            )}
            {proposal.declinedAt && (
              <span className="text-red-600">
                Declined {formatDate(proposal.declinedAt)}
              </span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          {proposal.status === "DRAFT" && (
            <>
              <Button size="sm" asChild>
                <Link
                  href={`/org/${orgSlug}/proposals/${proposal.id}/edit`}
                >
                  <Pencil className="mr-1.5 size-4" />
                  Edit
                </Link>
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => setSendDialogOpen(true)}
              >
                <Send className="mr-1.5 size-4" />
                Send
              </Button>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={!!loadingAction}
                  >
                    <Trash2 className="mr-1.5 size-4" />
                    Delete
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Delete Proposal?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This action cannot be undone. The proposal will be
                      permanently deleted.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                      variant="destructive"
                      onClick={handleDelete}
                    >
                      {loadingAction === "delete" ? (
                        <Loader2 className="mr-1.5 size-4 animate-spin" />
                      ) : null}
                      Delete
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </>
          )}

          {proposal.status === "SENT" && (
            <>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!!loadingAction}
                  >
                    <Undo2 className="mr-1.5 size-4" />
                    Withdraw
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Withdraw Proposal?</AlertDialogTitle>
                    <AlertDialogDescription>
                      This will move the proposal back to DRAFT status. The
                      recipient will no longer be able to accept it.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction onClick={handleWithdraw}>
                      {loadingAction === "withdraw" ? (
                        <Loader2 className="mr-1.5 size-4 animate-spin" />
                      ) : null}
                      Withdraw
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
              <Button
                size="sm"
                variant="outline"
                onClick={handleCopy}
                disabled={!!loadingAction}
              >
                {loadingAction === "copy" ? (
                  <Loader2 className="mr-1.5 size-4 animate-spin" />
                ) : (
                  <Copy className="mr-1.5 size-4" />
                )}
                Copy
              </Button>
            </>
          )}

          {proposal.status === "ACCEPTED" && (
            <>
              {proposal.createdProjectId && (
                <Button size="sm" variant="outline" asChild>
                  <Link
                    href={`/org/${orgSlug}/projects/${proposal.createdProjectId}`}
                  >
                    <ExternalLink className="mr-1.5 size-4" />
                    View Project
                  </Link>
                </Button>
              )}
              {proposal.createdRetainerId && (
                <Button size="sm" variant="outline" asChild>
                  <Link
                    href={`/org/${orgSlug}/retainers/${proposal.createdRetainerId}`}
                  >
                    <ExternalLink className="mr-1.5 size-4" />
                    View Retainer
                  </Link>
                </Button>
              )}
              {proposal.milestones.some((m) => m.invoiceId) && (
                <Button size="sm" variant="outline" asChild>
                  <Link
                    href={`/org/${orgSlug}/invoices/${proposal.milestones.find((m) => m.invoiceId)?.invoiceId}`}
                  >
                    <ExternalLink className="mr-1.5 size-4" />
                    View Invoice
                  </Link>
                </Button>
              )}
            </>
          )}

          {(proposal.status === "DECLINED" ||
            proposal.status === "EXPIRED") && (
            <Button
              size="sm"
              variant="outline"
              onClick={handleCopy}
              disabled={!!loadingAction}
            >
              {loadingAction === "copy" ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <Copy className="mr-1.5 size-4" />
              )}
              Copy
            </Button>
          )}
        </div>
      </div>

      {/* Decline reason */}
      {proposal.status === "DECLINED" && proposal.declineReason && (
        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-4">
            <p className="text-sm font-medium text-red-800">Decline Reason</p>
            <p className="mt-1 text-sm text-red-700">
              {proposal.declineReason}
            </p>
          </CardContent>
        </Card>
      )}

      {/* Fee Summary */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Fee Summary</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-500">Fee Model</span>
              <span className="text-sm font-medium text-slate-900">
                {formatFeeModelLabel(proposal.feeModel)}
              </span>
            </div>

            {proposal.feeModel === "FIXED" && (
              <>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500">Amount</span>
                  <span className="font-mono text-sm font-semibold tabular-nums text-slate-900">
                    {formatCurrencySafe(
                      proposal.fixedFeeAmount,
                      proposal.fixedFeeCurrency,
                    )}
                  </span>
                </div>
                {proposal.milestones.length > 0 && (
                  <div className="mt-4 space-y-2">
                    <p className="text-sm font-medium text-slate-700">
                      Milestones
                    </p>
                    <div className="space-y-1">
                      {proposal.milestones.map((m) => (
                        <div
                          key={m.id}
                          className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm"
                        >
                          <span className="text-slate-700">
                            {m.description}
                          </span>
                          <div className="flex items-center gap-3 text-slate-500">
                            <span className="font-mono tabular-nums">
                              {m.percentage}%
                            </span>
                            <span>{m.relativeDueDays}d</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            {proposal.feeModel === "HOURLY" && proposal.hourlyRateNote && (
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-500">Rate Note</span>
                <span className="text-sm text-slate-900">
                  {proposal.hourlyRateNote}
                </span>
              </div>
            )}

            {proposal.feeModel === "RETAINER" && (
              <>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500">Monthly Amount</span>
                  <span className="font-mono text-sm font-semibold tabular-nums text-slate-900">
                    {formatCurrencySafe(
                      proposal.retainerAmount,
                      proposal.retainerCurrency,
                    )}
                  </span>
                </div>
                {proposal.retainerHoursIncluded != null && (
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">
                      Hours Included
                    </span>
                    <span className="text-sm font-medium text-slate-900">
                      {proposal.retainerHoursIncluded}h / month
                    </span>
                  </div>
                )}
              </>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Proposal Body */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Proposal Content</CardTitle>
        </CardHeader>
        <CardContent>
          <ProposalPreview contentJson={proposal.contentJson} />
        </CardContent>
      </Card>

      {/* Team Members */}
      {proposal.teamMembers.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Team Members</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {proposal.teamMembers.map((member) => (
                <div
                  key={member.id}
                  className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm"
                >
                  <span className="font-medium text-slate-700">
                    {member.memberName ?? member.memberId}
                  </span>
                  {member.role && (
                    <span className="text-slate-500">{member.role}</span>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Project Template */}
      {proposal.projectTemplateName && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Project Template</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-slate-700">
              {proposal.projectTemplateName}
            </p>
          </CardContent>
        </Card>
      )}

      {/* Send Dialog */}
      <SendProposalDialog
        open={sendDialogOpen}
        onOpenChange={setSendDialogOpen}
        proposalId={proposal.id}
        customerId={proposal.customerId}
        existingPortalContactId={proposal.portalContactId}
      />
    </div>
  );
}
