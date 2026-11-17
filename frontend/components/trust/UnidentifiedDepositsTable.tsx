"use client";

import { useMemo, type ReactNode } from "react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import {
  UNIDENTIFIED_DEPOSIT_STATUS_LABELS,
  type UnidentifiedDeposit,
  type UnidentifiedDepositStatus,
} from "@/lib/types";

interface UnidentifiedDepositsTableProps {
  /** One page of the account's unidentified deposits, oldest first, passed through untouched. */
  items: UnidentifiedDeposit[];
  currency: string;
  /** Pre-rendered by the server page. */
  empty: ReactNode;
}

const getItemId = (item: UnidentifiedDeposit) => item.id;

const STATUS_VARIANTS: Record<
  UnidentifiedDepositStatus,
  "warning" | "success" | "neutral" | "outline"
> = {
  HELD: "warning",
  ALLOCATED: "success",
  PAID_TO_FUND: "neutral",
  REVERSED: "outline",
};

function ageText(days: number) {
  return days === 1 ? "1 day" : `${days} days`;
}

/**
 * The trust account's unidentified deposits (Phase 92 647B, ADR-376, §16.4.2) on `DataTable`: bank
 * credits whose owner is not yet known, oldest first — received date, the bank's description and
 * reference, amount, age and status. The age is shown only while an item is held, with an "Over
 * one year" flag from the server's `olderThanOneYear`. The status prints its label, never the
 * code. No actions yet: allocating an item or paying it to the Fund arrives in 647D. Paged by the
 * server (`pageSize={null}`).
 */
export function UnidentifiedDepositsTable({
  items,
  currency,
  empty,
}: UnidentifiedDepositsTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<UnidentifiedDeposit>([
        {
          id: "receivedDate",
          meta: { label: "Received" },
          cell: ({ row }) => <DateCell value={row.original.receivedDate} kind="local" />,
        },
        {
          id: "description",
          meta: {
            label: "Description",
            cellClassName: "whitespace-normal [overflow-wrap:anywhere]",
          },
          cell: ({ row }) => (
            <span className="block max-w-xs">
              <span className="text-foreground block font-medium">
                {row.original.bankDescription}
              </span>
              {row.original.bankReference && (
                <span className="text-muted-foreground block text-xs">
                  {row.original.bankReference}
                </span>
              )}
            </span>
          ),
        },
        {
          id: "amount",
          meta: { label: "Amount", align: "end" },
          cell: ({ row }) => <MoneyCell amount={row.original.amount} currency={currency} />,
        },
        {
          id: "age",
          meta: { label: "Age", cellClassName: "whitespace-normal" },
          cell: ({ row }) =>
            row.original.status === "HELD" ? (
              <span
                className="inline-flex flex-wrap items-center gap-1"
                data-testid="unidentified-deposit-age"
              >
                <span className="tabular-nums">{ageText(row.original.ageDays)}</span>
                {row.original.olderThanOneYear && (
                  <Badge
                    variant="warning"
                    className="shrink whitespace-normal"
                    data-testid="unidentified-over-one-year"
                  >
                    Over one year
                  </Badge>
                )}
              </span>
            ) : (
              <EmptyCell />
            ),
        },
        {
          id: "status",
          meta: { label: "Status", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Badge
              variant={STATUS_VARIANTS[row.original.status] ?? "neutral"}
              className="shrink whitespace-normal"
              data-testid="unidentified-deposit-status"
            >
              {UNIDENTIFIED_DEPOSIT_STATUS_LABELS[row.original.status] ?? "—"}
            </Badge>
          ),
        },
      ]),
    [currency]
  );

  return (
    <DataTable
      columns={columns}
      data={items}
      getRowId={getItemId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Unidentified deposits"
      tableTestId="unidentified-deposits-table"
      rowTestId="unidentified-deposit-row"
    />
  );
}
