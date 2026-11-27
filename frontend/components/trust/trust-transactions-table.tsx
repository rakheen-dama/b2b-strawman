"use client";

import { useMemo, type ReactNode } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { DateCell } from "@/components/data-table/cells";
import {
  defineColumns,
  type DataTableColumn,
  type DataTableLinkPagination,
} from "@/components/data-table/define-columns";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import { type TrustTransaction, type TrustTransactionStatus } from "@/lib/types";

interface TrustTransactionsTableProps {
  /** The rows the server page sent, passed through untouched (data identity = navigation). */
  rows: TrustTransaction[];
  currency: string;
  /** Pre-rendered by the server page (the dashboard: "No transactions recorded yet"). */
  empty: ReactNode;
  // ── Phase 91 667A, additive. Function-valued props come only from a "use client" wrapper
  //    (`TransactionsDataTable`); the dashboard passes none of them and renders as before. ──
  /** Appends the "Actions" column (last, sticky right). Absent → no column (the dashboard). */
  actions?: (row: TrustTransaction) => ReactNode;
  /** Server paging (ADR-395). Absent → every row, no pager (the dashboard). */
  pagination?: DataTableLinkPagination;
  /** Per-row `<tr>` testid (e.g. `transaction-row-<id>`). Absent → none (the dashboard). */
  rowTestId?: (row: TrustTransaction) => string;
  /** Pre-rendered toolbar slots (the transactions page's filters). */
  toolbar?: DataTableToolbarSlots;
  /** Visually hidden caption; default "Recent trust transactions" (the dashboard). */
  caption?: string;
  /**
   * `"register"` = the Trust › Transactions layout: the Type badge wraps (`rounded-md`,
   * `transaction-type-<id>`) over a 10rem column floor with the "where the money goes" line for
   * `DESCRIBED_TYPES`; Status adds "1 of 2 approvals"; Reference gets a 9rem floor (K-10, K-90).
   * Default `"recent"` = the dashboard, unchanged.
   */
  variant?: "recent" | "register";
}

export function statusBadgeVariant(
  status: TrustTransactionStatus
): "success" | "warning" | "destructive" | "neutral" {
  switch (status) {
    case "APPROVED":
      return "success";
    case "AWAITING_APPROVAL":
      return "warning";
    case "REJECTED":
      return "destructive";
    case "RECORDED":
    case "REVERSED":
    default:
      return "neutral";
  }
}

/**
 * Rows whose description names where the money goes, so the approver can judge them: the
 * unidentified-deposit resolutions (647D) and the investment placements (648A — "Placed in section
 * 86(4) investment at … (account)"). Shown in the `"register"` variant only.
 */
const DESCRIBED_TYPES: ReadonlySet<string> = new Set([
  "UNIDENT_ALLOC_OUT",
  "UNIDENT_FUND_PAY",
  "INV_PLACE",
  "FIRM_INV_PLACE",
]);

/**
 * The Actions column sticks to the right edge of the table's scroll frame (K-90), so Approve /
 * Reject / Reverse stay in view while the wider columns scroll under it. Opaque card background,
 * above the scrolled cells, with a left rule.
 */
const STICKY_ACTIONS_CLASS = "sticky right-0 z-[1] bg-card shadow-[inset_1px_0_0_var(--border)]";

const getTransactionRowId = (row: TrustTransaction) => row.id;

/**
 * Trust transactions on `DataTable`: the dashboard's "Recent Transactions" (Phase 90 643A) and,
 * through `TransactionsDataTable` with `variant="register"`, the Trust › Transactions list (Phase
 * 91 667A). Column order Date, Reference, Type, Amount, Status (+ Actions) is unchanged. Type and
 * direction arrive on the row (Phase 92 646E): money in = success token + down-left arrow, money
 * out (REFUND included, G88-F23) = foreground + up-right arrow, NEUTRAL = foreground, no arrow;
 * `data-direction` states it for tests.
 */
export function TrustTransactionsTable({
  rows,
  currency,
  empty,
  actions,
  pagination,
  rowTestId,
  toolbar,
  caption = "Recent trust transactions",
  variant = "recent",
}: TrustTransactionsTableProps) {
  const register = variant === "register";

  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(() => {
    const base: DataTableColumn<TrustTransaction>[] = defineColumns<TrustTransaction>([
      {
        id: "transactionDate",
        meta: { label: "Date" },
        cell: ({ row }) => <DateCell value={row.original.transactionDate} kind="local" />,
      },
      {
        id: "reference",
        // Long system references (INT-CREDIT-<uuid>-<uuid>) wrap at any character instead of
        // widening the table past the card (the kit cell is nowrap); capped so a short-column
        // layout keeps room for Type, Amount and Status. The register's floor (K-10) keeps the
        // column readable when the frame is over-full, so only tokens wider than it break.
        meta: {
          label: "Reference",
          cellClassName: "whitespace-normal [overflow-wrap:anywhere]",
          ...(register ? { minWidth: "9rem" } : {}),
        },
        cell: ({ row }) => (
          <span className="text-foreground block max-w-md font-medium">
            {row.original.reference}
          </span>
        ),
      },
      {
        id: "type",
        meta: register
          ? { label: "Type", cellClassName: "whitespace-normal", minWidth: "10rem" }
          : { label: "Type" },
        cell: ({ row }) =>
          register ? (
            <>
              {/* 647D OBS h / K-90: long registry labels ("Unidentified deposit paid to the
                  Fund") wrap inside the badge instead of clipping or widening the table */}
              <Badge
                variant="neutral"
                className="shrink rounded-md whitespace-normal"
                data-testid={`transaction-type-${row.original.id}`}
              >
                {row.original.typeLabel}
              </Badge>
              {/* 647D / 648A: an unidentified deposit's allocation or payment to the Fund, and an
                  investment placement, say where the money goes so the approver can judge it (no
                  other row's description is shown here) */}
              {DESCRIBED_TYPES.has(row.original.transactionType) && row.original.description && (
                <span
                  className="text-muted-foreground mt-1 block max-w-xs text-xs whitespace-normal"
                  data-testid={`transaction-description-${row.original.id}`}
                >
                  {row.original.description}
                </span>
              )}
            </>
          ) : (
            <Badge variant="neutral">{row.original.typeLabel}</Badge>
          ),
      },
      {
        id: "amount",
        meta: { label: "Amount", align: "end" },
        cell: ({ row }) => {
          const direction = row.original.direction;
          return (
            <span
              data-direction={direction.toLowerCase()}
              className={cn(
                "inline-flex items-center gap-1 font-mono tabular-nums",
                direction === "IN" ? "text-success-foreground" : "text-foreground"
              )}
            >
              {direction === "IN" && <ArrowDownLeft aria-hidden="true" className="size-3" />}
              {direction === "OUT" && <ArrowUpRight aria-hidden="true" className="size-3" />}
              {formatCurrency(row.original.amount, currency)}
            </span>
          );
        },
      },
      {
        id: "status",
        meta: { label: "Status" },
        cell: ({ row }) => {
          const badge = (
            <Badge variant={statusBadgeVariant(row.original.status)}>
              {row.original.status.replace(/_/g, " ")}
            </Badge>
          );
          if (!register) return badge;
          return (
            <>
              {badge}
              {/* LZKC-016: a dual-mode first approval keeps status AWAITING_APPROVAL — surface
                  the progress. */}
              {row.original.status === "AWAITING_APPROVAL" && row.original.approvedBy && (
                <span
                  className="text-muted-foreground mt-1 block text-xs"
                  data-testid={`approval-progress-${row.original.id}`}
                >
                  1 of 2 approvals
                </span>
              )}
            </>
          );
        },
      },
    ]);
    if (!actions) return base;
    return [
      ...base,
      {
        id: "actions",
        enableHiding: false,
        meta: {
          label: "Actions",
          headerClassName: STICKY_ACTIONS_CLASS,
          cellClassName: cn(STICKY_ACTIONS_CLASS, "whitespace-normal"),
        },
        cell: ({ row }) => actions(row.original),
      },
    ] satisfies DataTableColumn<TrustTransaction>[];
  }, [currency, register, actions]);

  const shared = {
    columns,
    data: rows,
    getRowId: getTransactionRowId,
    rowTestId,
    toolbar,
    columnVisibility: false,
    stickyHeader: false,
    frame: false,
    empty,
    caption,
    tableTestId: "transactions-table",
  };

  return pagination ? (
    <DataTable {...shared} pagination={pagination} />
  ) : (
    <DataTable {...shared} pageSize={null} />
  );
}
