"use client";

import { useMemo, type ReactNode } from "react";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatDateTime } from "@/lib/format";
import type { MonthlyListAdjustmentEntry } from "@/lib/types";

// ── Entered after the lock (DataTable, client mode) ───────────────

interface MonthlyListAfterLockTableProps {
  /** `adjustmentsSinceLock.entries`, passed through untouched. */
  entries: MonthlyListAdjustmentEntry[];
  currency: string;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
  /** Pre-rendered sentence under the table (the live creditor total), shown only with rows. */
  footer: ReactNode;
}

const getEntryRowId = (entry: MonthlyListAdjustmentEntry) => entry.transactionId;
const entryRowTestId = (entry: MonthlyListAdjustmentEntry) =>
  `after-lock-entry-${entry.transactionId}`;

/**
 * The entries dated in a locked month and recorded after its list was locked (Phase 92 650A) on
 * `DataTable` (Phase 91 667B, client mode, no pager). They are part of the books, not of the list.
 */
export function MonthlyListAfterLockTable({
  entries,
  currency,
  empty,
  footer,
}: MonthlyListAfterLockTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<MonthlyListAdjustmentEntry>([
        {
          id: "transactionDate",
          meta: { label: "Date" },
          cell: ({ row }) => <DateCell value={row.original.transactionDate} kind="local" />,
        },
        {
          id: "postedAt",
          meta: { label: "Entered" },
          cell: ({ row }) => <span>{formatDateTime(row.original.postedAt)}</span>,
        },
        {
          id: "type",
          meta: { label: "Type", cellClassName: "text-foreground whitespace-normal" },
          cell: ({ row }) => row.original.typeLabel,
        },
        {
          id: "client",
          meta: { label: "Client", cellClassName: "whitespace-normal" },
          cell: ({ row }) => row.original.clientName ?? <EmptyCell />,
        },
        {
          id: "reference",
          meta: { label: "Reference", cellClassName: "whitespace-normal [overflow-wrap:anywhere]" },
          cell: ({ row }) => row.original.reference ?? <EmptyCell />,
        },
        {
          id: "cashbookEffect",
          meta: { label: "Cashbook", align: "end" },
          cell: ({ row }) => <MoneyCell amount={row.original.cashbookEffect} currency={currency} />,
        },
      ]),
    [currency]
  );

  return (
    <DataTable
      columns={columns}
      data={entries}
      getRowId={getEntryRowId}
      rowTestId={entryRowTestId}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
      frame={false}
      empty={empty}
      footer={footer}
      caption="Entered after this list was locked"
    />
  );
}
