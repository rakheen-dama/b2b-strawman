"use client";

import { useMemo, type ReactNode } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TrustTransaction } from "@/lib/types";

interface FundLedgerTableProps {
  /** One page of the Fund ledger's rows, newest first, passed through untouched. */
  rows: TrustTransaction[];
  currency: string;
  /** Pre-rendered by the server page. */
  empty: ReactNode;
}

const getFundRowId = (row: TrustTransaction) => row.id;

/**
 * The Legal Practitioners' Fidelity Fund ledger's rows (Phase 92 647C, §16.4.2) on `DataTable`:
 * interest received, transfers to the Fund, bank charges and refunds, and their reversals — newest
 * first, each with the server-computed running balance (null on an unposted row → "—"). A bank
 * charge shows whether it is recoverable and its VAT (ADR-381). Types print the server's label,
 * never the code. Paged by the server (`pageSize={null}`).
 */
export function FundLedgerTable({ rows, currency, empty }: FundLedgerTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<TrustTransaction>([
        {
          id: "transactionDate",
          meta: { label: "Date" },
          cell: ({ row }) => <DateCell value={row.original.transactionDate} kind="local" />,
        },
        {
          id: "reference",
          meta: { label: "Reference", cellClassName: "whitespace-normal [overflow-wrap:anywhere]" },
          cell: ({ row }) => (
            <span className="text-foreground block max-w-xs font-medium">
              {row.original.reference}
            </span>
          ),
        },
        {
          id: "description",
          meta: { label: "Description", cellClassName: "whitespace-normal" },
          cell: ({ row }) =>
            row.original.description ? (
              <span className="text-muted-foreground block max-w-xs">
                {row.original.description}
              </span>
            ) : (
              <EmptyCell />
            ),
        },
        {
          id: "type",
          meta: { label: "Type", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <span className="inline-flex flex-wrap items-center gap-1">
              <Badge variant="neutral">{row.original.typeLabel}</Badge>
              {row.original.status === "REVERSED" && <Badge variant="outline">Reversed</Badge>}
            </span>
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
          id: "charge",
          meta: { label: "Bank charge" },
          cell: ({ row }) => {
            const { chargeRecoverable, chargeVatAmount } = row.original;
            return (
              <span data-testid="fund-ledger-charge">
                {typeof chargeRecoverable !== "boolean" ? (
                  <EmptyCell />
                ) : (
                  <>
                    {chargeRecoverable ? "Recoverable" : "Not recoverable"}
                    {typeof chargeVatAmount === "number" && (
                      <> · VAT {formatCurrency(chargeVatAmount, currency)}</>
                    )}
                  </>
                )}
              </span>
            );
          },
        },
        {
          id: "runningBalance",
          meta: { label: "Running balance", align: "end" },
          cell: ({ row }) => (
            <span data-testid="fund-ledger-balance">
              <MoneyCell amount={row.original.runningBalance} currency={currency} />
            </span>
          ),
        },
      ]),
    [currency]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getFundRowId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Legal Practitioners' Fidelity Fund ledger"
      tableTestId="fund-ledger-table"
      rowTestId="fund-ledger-row"
    />
  );
}
