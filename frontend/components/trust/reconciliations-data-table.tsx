"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { AlertCircle, CheckCircle2, Lock } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { MoneyCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { DiscardStatementDialog } from "@/components/trust/DiscardStatementDialog";
import { buildPageHref } from "@/lib/page-href";
import { formatLocalDate } from "@/lib/format";
import type { BankStatementResponse, TrustReconciliationResponse } from "@/lib/types";

/** The placeholder these two tables have always printed (not the kit's "—"). */
const NONE = "---";

// ── Reconciliation history (server-paged) ─────────────────────────

interface ReconciliationsDataTableProps {
  /** The page the server sent (`reconciliationPage.content`), passed through untouched. */
  rows: TrustReconciliationResponse[];
  currency: string;
  /** Zero-based (`reconciliationPage.pageNumber`). */
  pageIndex: number;
  /** `reconciliationPage.totalPages`; the pager shows from 2 pages. */
  pageCount: number;
  /**
   * `/org/<slug>/trust-accounting/reconciliation`: the pager links to `<basePath>?page=N` (no other
   * param, as the page's `pageUrl` always wrote) and a locked list to `<basePath>/lists/<id>`.
   */
  basePath: string;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const getReconciliationRowId = (row: TrustReconciliationResponse) => row.id;
const reconciliationRowTestId = (row: TrustReconciliationResponse) =>
  `reconciliation-row-${row.id}`;

/** A right-aligned money column whose (multi-word) header may wrap. */
function moneyColumn(
  id: string,
  label: string,
  amount: (row: TrustReconciliationResponse) => number,
  currency: string
): DataTableColumn<TrustReconciliationResponse> {
  return {
    id,
    meta: { label, align: "end", headerClassName: "whitespace-normal" },
    cell: ({ row }) => <MoneyCell amount={amount(row.original)} currency={currency} />,
  };
}

/**
 * Trust › Reconciliation history on `DataTable` (Phase 91 667B, ADR-395 server paging). The server
 * page fetches and pre-renders the empty state; this client wrapper receives only serializable
 * props and builds the columns, pager hrefs and row testids itself.
 *
 * Fit at 1024 (F-667B-1): seven columns of which three are money cannot fit a 662 px card, so the
 * Period wraps ("06 Nov" / "2026") and Completed is hidden below a 67rem frame (D-73) — the Monthly
 * list link stays in view; at 1440 every column shows. F-667B-A3: with Completed shown, the
 * seven columns' min-content is 758 px at 1024 (the money amounts, the "Unbalanced" chip, the
 * "COMPLETED" header and the "List locked" chip don't wrap), so it stays hidden there.
 */
export function ReconciliationsDataTable({
  rows,
  currency,
  pageIndex,
  pageCount,
  basePath,
  empty,
}: ReconciliationsDataTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<TrustReconciliationResponse>([
        {
          id: "periodEnd",
          enableHiding: false,
          meta: { label: "Period", cellClassName: "text-foreground font-medium whitespace-normal" },
          cell: ({ row }) => formatLocalDate(row.original.periodEnd),
        },
        moneyColumn("bankBalance", "Bank Balance", (r) => r.bankBalance, currency),
        moneyColumn("cashbookBalance", "Cashbook Balance", (r) => r.cashbookBalance, currency),
        moneyColumn(
          "clientLedgerTotal",
          "Client Ledger Total",
          (r) => r.clientLedgerTotal,
          currency
        ),
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) =>
            row.original.isBalanced ? (
              <Badge variant="success">
                <CheckCircle2 className="mr-1 size-3" />
                Balanced
              </Badge>
            ) : (
              <Badge variant="warning">
                <AlertCircle className="mr-1 size-3" />
                Unbalanced
              </Badge>
            ),
        },
        {
          id: "completedAt",
          meta: { label: "Completed", priority: "low" },
          cell: ({ row }) =>
            row.original.completedAt
              ? formatLocalDate(row.original.completedAt.split("T")[0])
              : NONE,
        },
        {
          id: "monthlyList",
          meta: { label: "Monthly list", headerClassName: "whitespace-normal" },
          cell: ({ row }) =>
            row.original.monthlyListId ? (
              <Link
                href={`${basePath}/lists/${row.original.monthlyListId}`}
                data-testid={`list-locked-${row.original.id}`}
              >
                <Badge variant="neutral">
                  <Lock className="mr-1 size-3" />
                  List locked
                </Badge>
              </Link>
            ) : (
              NONE
            ),
        },
      ]),
    [basePath, currency]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getReconciliationRowId}
      rowTestId={reconciliationRowTestId}
      pagination={{
        pageIndex,
        pageCount,
        hrefFor: (index) => buildPageHref(basePath, {}, index),
      }}
      columnVisibility={false}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Reconciliation History"
      tableTestId="reconciliations-table"
    />
  );
}

// ── Statements not yet reconciled (client mode) ───────────────────

export interface OpenStatementRow {
  statement: BankStatementResponse;
  /** The trust account the statement was imported into. */
  accountName: string;
}

interface OpenStatementsDataTableProps {
  /** Every open statement of every trust account, as the server listed them. */
  rows: OpenStatementRow[];
  /** MANAGE_TRUST (or admin/owner): the Discard action shows. */
  canManageTrust: boolean;
}

const getStatementRowId = (row: OpenStatementRow) => row.statement.id;
const statementRowTestId = (row: OpenStatementRow) => `open-statement-row-${row.statement.id}`;

/**
 * FU-6b's open statements on `DataTable` (Phase 91 667B, client mode, no pager): an imported
 * statement no reconciliation has completed, with `DiscardStatementDialog` (unchanged) for
 * MANAGE_TRUST. The page renders this table only when there are rows.
 */
export function OpenStatementsDataTable({ rows, canManageTrust }: OpenStatementsDataTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<OpenStatementRow>([
        {
          id: "accountName",
          meta: { label: "Trust Account", cellClassName: "whitespace-normal" },
          cell: ({ row }) => row.original.accountName,
        },
        {
          id: "fileName",
          enableHiding: false,
          meta: {
            label: "File",
            cellClassName: "text-foreground font-medium whitespace-normal [overflow-wrap:anywhere]",
            minWidth: "8rem",
          },
          cell: ({ row }) => row.original.statement.fileName,
        },
        {
          id: "period",
          // F-667B-A1: the range wraps between its two dates (never inside one), so the table
          // fits at 1024 with Discard in view
          meta: { label: "Period", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <>
              <span className="whitespace-nowrap">
                {formatLocalDate(row.original.statement.periodStart)} –
              </span>{" "}
              <span className="whitespace-nowrap">
                {formatLocalDate(row.original.statement.periodEnd)}
              </span>
            </>
          ),
        },
        {
          id: "lineCount",
          meta: { label: "Lines", align: "end", cellClassName: "font-mono tabular-nums" },
          cell: ({ row }) => row.original.statement.lineCount,
        },
        {
          id: "matchedCount",
          meta: { label: "Matched", align: "end", cellClassName: "font-mono tabular-nums" },
          cell: ({ row }) => row.original.statement.matchedCount,
        },
        {
          id: "actions",
          enableHiding: false,
          header: () => <span className="sr-only">Actions</span>,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) =>
            canManageTrust ? (
              <DiscardStatementDialog
                statementId={row.original.statement.id}
                fileName={row.original.statement.fileName}
              />
            ) : null,
        },
      ]),
    [canManageTrust]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getStatementRowId}
      rowTestId={statementRowTestId}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
      frame={false}
      empty={null}
      caption="Statements Not Yet Reconciled"
      tableTestId="open-statements-table"
    />
  );
}
