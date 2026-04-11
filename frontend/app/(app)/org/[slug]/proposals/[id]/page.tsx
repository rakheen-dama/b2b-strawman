import { notFound } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, handleApiError } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ProposalDetailActions } from "@/components/proposals/proposal-detail-actions";
import { formatDate, formatCurrencySafe } from "@/lib/format";
import type { ProposalResponse, ProposalStatus } from "@/lib/types/proposal";

const STATUS_BADGE: Record<
  ProposalStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" | "warning" }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  SENT: { label: "Sent", variant: "lead" },
  ACCEPTED: { label: "Accepted", variant: "success" },
  DECLINED: { label: "Declined", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "warning" },
};

const FEE_MODEL_LABELS: Record<string, string> = {
  FIXED: "Fixed Fee",
  HOURLY: "Hourly",
  RETAINER: "Retainer",
  CONTINGENCY: "Contingency",
};

export default async function ProposalDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();

  if (
    !caps.isAdmin &&
    !caps.isOwner &&
    !caps.capabilities.includes("INVOICING")
  ) {
    notFound();
  }

  let proposal: ProposalResponse;
  try {
    proposal = await api.get<ProposalResponse>(`/api/proposals/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  const badge = STATUS_BADGE[proposal.status];

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/proposals`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Proposals
        </Link>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {proposal.title}
            </h1>
            <Badge variant={badge.variant}>{badge.label}</Badge>
          </div>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {proposal.proposalNumber}
          </p>
        </div>

        <div className="flex shrink-0 gap-2">
          <ProposalDetailActions
            proposalId={proposal.id}
            customerId={proposal.customerId}
            status={proposal.status}
            slug={slug}
          />
        </div>
      </div>

      {/* Details Card */}
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle className="font-display text-slate-900 dark:text-slate-100">
            Proposal Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-slate-500 dark:text-slate-400">Fee Model</dt>
              <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                {FEE_MODEL_LABELS[proposal.feeModel] ?? proposal.feeModel}
              </dd>
            </div>

            {proposal.feeModel === "FIXED" &&
              proposal.fixedFeeAmount != null && (
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">
                    Fixed Fee
                  </dt>
                  <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrencySafe(
                      proposal.fixedFeeAmount,
                      proposal.fixedFeeCurrency,
                    )}
                  </dd>
                </div>
              )}

            {proposal.feeModel === "HOURLY" && proposal.hourlyRateNote && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Hourly Rate
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {proposal.hourlyRateNote}
                </dd>
              </div>
            )}

            {proposal.feeModel === "RETAINER" && (
              <>
                {proposal.retainerAmount != null && (
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Retainer Amount
                    </dt>
                    <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                      {formatCurrencySafe(
                        proposal.retainerAmount,
                        proposal.retainerCurrency,
                      )}
                    </dd>
                  </div>
                )}
                {proposal.retainerHoursIncluded != null && (
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Hours Included
                    </dt>
                    <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                      {proposal.retainerHoursIncluded}h
                    </dd>
                  </div>
                )}
              </>
            )}

            {proposal.feeModel === "CONTINGENCY" && (
              <>
                {proposal.contingencyPercent != null && (
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Contingency Percent
                    </dt>
                    <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                      {proposal.contingencyPercent}%
                    </dd>
                  </div>
                )}
                {proposal.contingencyCapPercent != null && (
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">
                      Contingency Cap
                    </dt>
                    <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                      {proposal.contingencyCapPercent}%
                    </dd>
                  </div>
                )}
                {proposal.contingencyDescription && (
                  <div className="col-span-2 sm:col-span-3">
                    <dt className="text-slate-500 dark:text-slate-400">
                      Description
                    </dt>
                    <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                      {proposal.contingencyDescription}
                    </dd>
                  </div>
                )}
              </>
            )}

            <div>
              <dt className="text-slate-500 dark:text-slate-400">Created</dt>
              <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                {formatDate(proposal.createdAt)}
              </dd>
            </div>

            {proposal.expiresAt && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Expires
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(proposal.expiresAt)}
                </dd>
              </div>
            )}

            {proposal.sentAt && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">Sent</dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(proposal.sentAt)}
                </dd>
              </div>
            )}

            {proposal.acceptedAt && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Accepted
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(proposal.acceptedAt)}
                </dd>
              </div>
            )}

            {proposal.declinedAt && (
              <div>
                <dt className="text-slate-500 dark:text-slate-400">
                  Declined
                </dt>
                <dd className="mt-0.5 font-medium text-slate-900 dark:text-slate-100">
                  {formatDate(proposal.declinedAt)}
                </dd>
              </div>
            )}
          </dl>

          {proposal.declineReason && (
            <div className="mt-4">
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Decline Reason
              </p>
              <p className="mt-0.5 text-sm text-slate-900 dark:text-slate-100">
                {proposal.declineReason}
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
