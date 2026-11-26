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
import { buildPageHref } from "@/lib/page-href";
import { AllocateUnidentifiedDialog } from "./AllocateUnidentifiedDialog";
import { PayToFundDialog } from "./PayToFundDialog";
import type { TrustPickerCustomer } from "./TrustEntityPickers";

interface UnidentifiedDepositsTableProps {
  /** One page of the account's unidentified deposits, oldest first, passed through untouched. */
  items: UnidentifiedDeposit[];
  currency: string;
  /** Pre-rendered by the server page. */
  empty: ReactNode;
  /** MANAGE_TRUST: the Allocate and Pay to the Fund actions on held items (647D). */
  canManage?: boolean;
  /** The active client roster for the allocation picker (server-fetched; only when canManage). */
  customers?: TrustPickerCustomer[];
  /** The server page (`page.number`, `page.totalPages`), zero-based. */
  pagination: { pageIndex: number; pageCount: number };
  /** The page's path (`/org/{slug}/trust-accounting/other-creditors`). */
  basePath: string;
  /**
   * The page's list state as a plain object; each pager link sets `itemsPage` in it. `itemsPage`
   * is present only past the first items page, as in the page's own links.
   */
  query: Record<string, string>;
}

/**
 * The href of items page `pageIndex`. The first items page drops `itemsPage` instead of writing
 * `itemsPage=0`, as the page's own links do (other-creditors' `listUrl`), so Previous back to it
 * keeps today's URL (`?page=N`).
 */
function itemsPageHref(basePath: string, query: Record<string, string>, pageIndex: number) {
  if (pageIndex > 0) return buildPageHref(basePath, query, pageIndex, "itemsPage");
  const listQuery = { ...query };
  delete listQuery.itemsPage;
  // Re-setting `page` to its own value keeps it in place: the URL is the list URL itself.
  return buildPageHref(basePath, listQuery, Number(listQuery.page ?? 0), "page");
}

const getItemId = (item: UnidentifiedDeposit) => item.id;

const EMPTY_CUSTOMERS: TrustPickerCustomer[] = [];

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
 * code. Server-paged through DataTable `pagination` (`?itemsPage=`, every other param kept).
 *
 * <p>647D: with MANAGE_TRUST, a held item with no pending resolution offers Allocate and Pay to the
 * Fund (each dialog owns its trigger); a held item awaiting approval says so, with no actions; an
 * allocated item names its client and matter under the status.
 */
export function UnidentifiedDepositsTable({
  items,
  currency,
  empty,
  canManage = false,
  customers = EMPTY_CUSTOMERS,
  pagination,
  basePath,
  query,
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
            <span className="inline-flex flex-col items-start gap-1">
              <Badge
                variant={STATUS_VARIANTS[row.original.status] ?? "neutral"}
                className="shrink whitespace-normal"
                data-testid="unidentified-deposit-status"
              >
                {UNIDENTIFIED_DEPOSIT_STATUS_LABELS[row.original.status] ?? "—"}
              </Badge>
              {row.original.status === "ALLOCATED" && row.original.allocatedClientName && (
                <span
                  className="text-muted-foreground text-xs"
                  data-testid="unidentified-deposit-allocated-to"
                >
                  {row.original.allocatedClientName}
                  {row.original.allocatedMatterName && ` — ${row.original.allocatedMatterName}`}
                </span>
              )}
            </span>
          ),
        },
        ...(canManage
          ? defineColumns<UnidentifiedDeposit>([
              {
                id: "actions",
                meta: { label: "Actions", align: "end", cellClassName: "whitespace-normal" },
                cell: ({ row }) => (
                  <ItemActions item={row.original} customers={customers} currency={currency} />
                ),
              },
            ])
          : []),
      ]),
    [currency, canManage, customers]
  );

  return (
    <DataTable
      columns={columns}
      data={items}
      getRowId={getItemId}
      columnVisibility={false}
      pagination={{
        ...pagination,
        hrefFor: (pageIndex) => itemsPageHref(basePath, query, pageIndex),
      }}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Unidentified deposits"
      tableTestId="unidentified-deposits-table"
      rowTestId="unidentified-deposit-row"
    />
  );
}

/** A held item's actions (647D): Allocate and Pay to the Fund, or the pending resolution's text. */
function ItemActions({
  item,
  customers,
  currency,
}: {
  item: UnidentifiedDeposit;
  customers: TrustPickerCustomer[];
  currency: string;
}) {
  if (item.status !== "HELD") {
    return <EmptyCell />;
  }
  if (item.resolutionTransactionId !== null) {
    return (
      <span className="text-muted-foreground text-xs" data-testid="unidentified-deposit-pending">
        {item.allocatedClientId !== null
          ? `Allocation to ${item.allocatedClientName ?? "a client"} awaiting approval`
          : "Payment to the Fund awaiting approval"}
      </span>
    );
  }
  return (
    <span className="inline-flex flex-wrap justify-end gap-2">
      <AllocateUnidentifiedDialog item={item} customers={customers} currency={currency} />
      <PayToFundDialog item={item} currency={currency} />
    </span>
  );
}
