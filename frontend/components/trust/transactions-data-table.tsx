"use client";

import { useCallback, type ReactNode } from "react";
import type { DataTableToolbarSlots } from "@/components/data-table/data-table";
import { ApprovalBadge } from "@/components/trust/approval-badge";
import { ReversalButton } from "@/components/trust/reversal-button";
import { TrustTransactionsTable } from "@/components/trust/trust-transactions-table";
import { buildPageHref } from "@/lib/page-href";
import {
  canOfferReversal,
  reversalRuleFor,
  type TrustTransaction,
  type TrustTransactionTypeInfo,
} from "@/lib/types";

interface TransactionsDataTableProps {
  /** The page the server sent (`transactionPage.content`), passed through untouched. */
  rows: TrustTransaction[];
  currency: string;
  /** Zero-based (`transactionPage.pageNumber`). */
  pageIndex: number;
  /** `transactionPage.totalPages`; the pager shows from 2 pages. */
  pageCount: number;
  /** `/org/<slug>/trust-accounting/transactions`. */
  basePath: string;
  /** Every string search param of the page; the pager keeps them all and sets `page`. */
  query: Record<string, string>;
  /** The FULL type registry: reversal rules need the take-on types too (657A). */
  types: TrustTransactionTypeInfo[];
  canApproveTrust: boolean;
  canManageTrust: boolean;
  /** The viewer's member id (LZKC-016 dual-approval guard); "" when unresolved. */
  currentMemberId: string;
  /** Pre-rendered toolbar slots (status pills, type pills, `TransactionFilters`). */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const transactionRowTestId = (row: TrustTransaction) => `transaction-row-${row.id}`;

/**
 * Trust › Transactions on `DataTable` (Phase 91 667A, ADR-395 server paging). The server page
 * fetches, gates and pre-renders the toolbar and empty state; this client wrapper receives only
 * serializable props and builds the pager hrefs (`buildPageHref`, every param kept), the row testid
 * and the Actions cell itself (§18.3.7). The Actions cell is today's: `ApprovalBadge` (with the
 * dual-approval guard) only for an AWAITING_APPROVAL row and an approver, `ReversalButton` per the
 * row type's reversal rule for a trust manager.
 */
export function TransactionsDataTable({
  rows,
  currency,
  pageIndex,
  pageCount,
  basePath,
  query,
  types,
  canApproveTrust,
  canManageTrust,
  currentMemberId,
  toolbar,
  empty,
}: TransactionsDataTableProps) {
  // Stable across re-renders so the table's memoised columns are not rebuilt (cells would remount).
  const actions = useCallback(
    (tx: TrustTransaction) => (
      // Wraps (and caps) so the approver error line stays inside the sticky column.
      <div className="flex max-w-xs flex-wrap items-center gap-2">
        {tx.status === "AWAITING_APPROVAL" && canApproveTrust && (
          <span data-testid={`approval-actions-${tx.id}`}>
            <ApprovalBadge
              transactionId={tx.id}
              status={tx.status}
              firstApprovedBy={tx.approvedBy}
              currentMemberId={currentMemberId}
            />
          </span>
        )}
        {/* Phase 92 649B: any posted row, per its type's reversal rule */}
        {canManageTrust && canOfferReversal(tx, types) && (
          <span data-testid={`reversal-action-${tx.id}`}>
            <ReversalButton
              transactionId={tx.id}
              transactionDate={tx.transactionDate}
              needsApproval={reversalRuleFor(tx.transactionType, types) === "NEEDS_APPROVAL"}
            />
          </span>
        )}
      </div>
    ),
    [canApproveTrust, canManageTrust, currentMemberId, types]
  );

  return (
    <TrustTransactionsTable
      variant="register"
      rows={rows}
      currency={currency}
      actions={actions}
      pagination={{
        pageIndex,
        pageCount,
        hrefFor: (index) => buildPageHref(basePath, query, index),
      }}
      rowTestId={transactionRowTestId}
      toolbar={toolbar}
      caption="Transaction History"
      empty={empty}
    />
  );
}
