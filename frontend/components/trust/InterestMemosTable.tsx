"use client";

import { useMemo, type ReactNode } from "react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import type { InterestMemo } from "@/lib/types";
import { InterestMemoDialog, monthLabel } from "./InterestMemoDialog";

interface InterestMemosTableProps {
  /** The year's memos of the account, oldest month first, passed through untouched. */
  memos: InterestMemo[];
  currency: string;
  /** Pre-rendered by the server page. */
  empty: ReactNode;
  /** MANAGE_TRUST: a "Correct" action per memo. */
  canManage?: boolean;
  trustAccountId: string;
  /** Months (yyyy-MM) whose Rule 54.15 list is locked (650A) — marked "List locked". */
  lockedMonths: string[];
}

const getMemoId = (memo: InterestMemo) => memo.id;

/** The long money headers wrap so they do not squeeze the free-text Source column (F-OBS-1). */
const MONEY_HEADER_CLASS = "whitespace-normal";

/**
 * The trust account's at-source memo records of one year (Phase 92 650B, ADR-384) on `DataTable`:
 * per month the Rule 54.28 gross interest, gross charges and the VAT in them that never reached the
 * account, where the figures came from and who saved them last. A month whose list is locked is
 * marked — a memo is not part of the list and may still be corrected. With MANAGE_TRUST each row
 * offers "Correct" (the dialog owns its trigger).
 */
export function InterestMemosTable({
  memos,
  currency,
  empty,
  canManage = false,
  trustAccountId,
  lockedMonths,
}: InterestMemosTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<InterestMemo>([
        {
          id: "month",
          meta: { label: "Month", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <span className="inline-flex flex-wrap items-center gap-1">
              <span className="text-foreground font-medium">
                {monthLabel(row.original.periodMonth)}
              </span>
              {lockedMonths.includes(row.original.periodMonth.slice(0, 7)) && (
                <Badge
                  variant="neutral"
                  className="shrink whitespace-normal"
                  data-testid="interest-memo-locked"
                >
                  List locked
                </Badge>
              )}
            </span>
          ),
        },
        {
          id: "grossInterest",
          meta: {
            label: "Gross interest (at source)",
            align: "end",
            headerClassName: MONEY_HEADER_CLASS,
          },
          cell: ({ row }) => <MoneyCell amount={row.original.grossInterest} currency={currency} />,
        },
        {
          id: "grossCharges",
          meta: {
            label: "Gross charges (at source)",
            align: "end",
            headerClassName: MONEY_HEADER_CLASS,
          },
          cell: ({ row }) => <MoneyCell amount={row.original.grossCharges} currency={currency} />,
        },
        {
          id: "chargesVat",
          meta: { label: "VAT in the charges", align: "end", headerClassName: MONEY_HEADER_CLASS },
          cell: ({ row }) => <MoneyCell amount={row.original.chargesVat} currency={currency} />,
        },
        {
          id: "source",
          // Wraps at word boundaries only: the min width keeps a column of ordinary words; a single
          // token longer than max-w-xs still breaks (break-words) rather than widening the table.
          meta: { label: "Source", cellClassName: "whitespace-normal break-words" },
          cell: ({ row }) => (
            <span className="block max-w-xs min-w-40" data-testid="interest-memo-source-note">
              {row.original.sourceNote}
            </span>
          ),
        },
        {
          id: "recorded",
          meta: { label: "Saved by", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <span className="inline-flex flex-col items-start">
              <span data-testid="interest-memo-recorded-by">
                {row.original.recordedByName ?? "—"}
              </span>
              <span className="text-muted-foreground text-xs">
                <DateCell value={row.original.updatedAt} />
              </span>
            </span>
          ),
        },
        ...(canManage
          ? defineColumns<InterestMemo>([
              {
                id: "actions",
                meta: { label: "Actions", align: "end", cellClassName: "whitespace-normal" },
                cell: ({ row }) => (
                  <InterestMemoDialog
                    trustAccountId={trustAccountId}
                    memo={row.original}
                    existing={memos}
                    lockedMonths={lockedMonths}
                    triggerLabel="Correct"
                    triggerTestId={`correct-interest-memo-btn-${row.original.id}`}
                  />
                ),
              },
            ])
          : []),
      ]),
    [currency, canManage, trustAccountId, memos, lockedMonths]
  );

  return (
    <DataTable
      columns={columns}
      data={memos}
      getRowId={getMemoId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Interest and charges at source"
      tableTestId="interest-memos-table"
      rowTestId="interest-memo-row"
    />
  );
}
