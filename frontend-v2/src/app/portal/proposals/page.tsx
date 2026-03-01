"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileCheck, Loader2 } from "lucide-react";
import { PortalProposalStatusBadge } from "@/components/portal/proposal-status-badge";
import { formatDate, formatCurrencySafe } from "@/lib/format";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  listPortalProposals,
  type PortalProposalSummary,
} from "./proposal-actions";

export default function PortalProposalsPage() {
  const [proposals, setProposals] = useState<PortalProposalSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProposals() {
      try {
        const data = await listPortalProposals();
        setProposals(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load proposals.",
        );
      } finally {
        setLoading(false);
      }
    }

    fetchProposals();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="size-6 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Proposals
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Proposals sent to you by your service provider.
        </p>
      </div>

      {proposals.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 py-16 dark:border-slate-800">
          <FileCheck className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
            No proposals yet.
          </p>
          <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
            Proposals sent to you will appear here.
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-200 dark:border-slate-800">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead className="hidden sm:table-cell">Number</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="hidden md:table-cell">Fee</TableHead>
                <TableHead className="hidden md:table-cell">
                  Sent Date
                </TableHead>
                <TableHead className="w-[120px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {proposals.map((proposal) => (
                <TableRow key={proposal.id}>
                  <TableCell>
                    <span className="font-medium text-slate-900 dark:text-slate-100">
                      {proposal.title}
                    </span>
                  </TableCell>
                  <TableCell className="hidden text-sm text-slate-500 sm:table-cell">
                    {proposal.proposalNumber}
                  </TableCell>
                  <TableCell>
                    <PortalProposalStatusBadge status={proposal.status} />
                  </TableCell>
                  <TableCell className="hidden text-sm text-slate-500 md:table-cell">
                    {proposal.feeAmount != null && proposal.feeCurrency
                      ? formatCurrencySafe(
                          proposal.feeAmount,
                          proposal.feeCurrency,
                        )
                      : "\u2014"}
                  </TableCell>
                  <TableCell className="hidden text-sm text-slate-500 md:table-cell">
                    {proposal.sentAt ? formatDate(proposal.sentAt) : "\u2014"}
                  </TableCell>
                  <TableCell>
                    <Link
                      href={`/portal/proposals/${proposal.id}`}
                      className="text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                    >
                      {proposal.status === "SENT"
                        ? "View & Respond"
                        : "View"}
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
