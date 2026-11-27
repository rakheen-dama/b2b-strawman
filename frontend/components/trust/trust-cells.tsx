import type { ColumnMeta } from "@tanstack/react-table";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TrustTransaction, TrustTransactionStatus } from "@/lib/types";

/**
 * Cells and column metas shared by the trust transaction tables on `DataTable` — the dashboard's
 * "Recent Transactions" and the Trust › Transactions register (`TrustTransactionsTable`) and a
 * client's ledger history (`ClientHistoryDataTable`). No hooks, so both client modules import it.
 */

type TrustColumnMeta = ColumnMeta<TrustTransaction, unknown>;

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
 * Long system references (INT-CREDIT-<uuid>-<uuid>) wrap at any character instead of widening the
 * table past the card (the kit cell is nowrap). `floor` (the register and the client history,
 * K-10) keeps the column readable when the frame is over-full, so only tokens wider than it break.
 * 6rem, not wider: at 1024 the register's sticky Actions column must leave Amount in view (F-667A-A4).
 */
export function referenceMeta(floor: boolean): TrustColumnMeta {
  return {
    label: "Reference",
    cellClassName: "whitespace-normal [overflow-wrap:anywhere]",
    ...(floor ? { minWidth: "6rem" } : {}),
  };
}

/** Capped so a short-column layout keeps room for Type, Amount and Status. */
export function TrustReference({ reference }: { reference: string }) {
  return <span className="text-foreground block max-w-md font-medium">{reference}</span>;
}

/** K-90: long registry labels wrap inside the badge over a 10rem column floor. */
export const REGISTER_TYPE_META: TrustColumnMeta = {
  label: "Type",
  cellClassName: "whitespace-normal",
  minWidth: "10rem",
};

/**
 * 647D OBS h / K-90: long registry labels ("Unidentified deposit paid to the Fund") wrap inside the
 * badge instead of clipping or widening the table.
 */
export function RegisterTypeBadge({ label, testId }: { label: string; testId?: string }) {
  return (
    <Badge variant="neutral" className="shrink rounded-md whitespace-normal" data-testid={testId}>
      {label}
    </Badge>
  );
}

/**
 * The amount styled from the row's direction (Phase 92 646E F8): money in = success token +
 * down-left arrow, money out (REFUND included, G88-F23) = foreground + up-right arrow, NEUTRAL =
 * foreground, no arrow; `data-direction` states it for tests.
 */
export function TrustAmount({
  amount,
  direction,
  currency,
}: {
  amount: number;
  direction: TrustTransaction["direction"];
  currency: string;
}) {
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
      {formatCurrency(amount, currency)}
    </span>
  );
}

/** The status badge: `AWAITING_APPROVAL` → "AWAITING APPROVAL". */
export function TrustStatusBadge({ status }: { status: TrustTransactionStatus }) {
  return <Badge variant={statusBadgeVariant(status)}>{status.replace(/_/g, " ")}</Badge>;
}
