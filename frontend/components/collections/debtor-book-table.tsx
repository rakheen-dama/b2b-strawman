"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { TriageBadges } from "@/components/collections/triage-badges";
import { activityBadgeVariant, formatSnakeCase } from "@/components/collections/activity-format";
import { formatDateTime, formatDaysOverdue } from "@/lib/format";
import type { DebtorResponse } from "@/lib/api/collections";

interface DebtorBookTableProps {
  slug: string;
  /** The first page of debtors (50) the server read; no pager, as before. */
  debtors: DebtorResponse[];
  /** Pre-rendered empty state (server), shown instead of the table. */
  empty: ReactNode;
}

const getDebtorRowId = (debtor: DebtorResponse) => `${debtor.clientId}-${debtor.currency}`;

/** Oldest from a 36rem frame (was the `sm:` viewport). */
const OLDEST_CLASS = "hidden @xl:table-cell";
/** K-71: the aging buckets from a 56rem frame (was the `lg:` viewport, which overflowed at 1024). */
const BUCKET_CLASS = "hidden @4xl:table-cell";

/** An aging-bucket money column (Current, 30d, 60d, 90d+). */
function bucketColumn(
  bucket: keyof DebtorResponse["buckets"],
  label: string
): DataTableColumn<DebtorResponse> {
  return {
    id: bucket,
    meta: { label, align: "end", headerClassName: BUCKET_CLASS, cellClassName: BUCKET_CLASS },
    cell: ({ row }) => (
      <MoneyCell
        amount={row.original.buckets[bucket]}
        currency={row.original.currency}
        className="font-sans"
      />
    ),
  };
}

/**
 * The Debtor book on `DataTable` (Phase 91 666A, client mode). COLUMN ORDER IS FROZEN: the take-on
 * spec reads cells by index (1 = Outstanding, 3–6 = the buckets). Money is `font-sans` tabular
 * figures (K-71); hidden columns stay in the DOM (CSS only).
 */
export function DebtorBookTable({ slug, debtors, empty }: DebtorBookTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<DebtorResponse>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "whitespace-normal" },
          cell: ({ row }) => {
            const debtor = row.original;
            return (
              <>
                <Link
                  href={`/org/${slug}/clients/${debtor.clientId}`}
                  className="text-foreground font-medium hover:underline"
                >
                  {debtor.clientName}
                </Link>
                <p className="text-muted-foreground text-xs">
                  {debtor.invoiceCount} fee note{debtor.invoiceCount === 1 ? "" : "s"} ·{" "}
                  {debtor.currency}
                </p>
              </>
            );
          },
        },
        {
          id: "outstanding",
          meta: { label: "Outstanding", align: "end" },
          cell: ({ row }) => (
            <MoneyCell
              amount={row.original.outstandingTotal}
              currency={row.original.currency}
              className="font-sans font-medium"
            />
          ),
        },
        {
          id: "oldest",
          meta: {
            label: "Oldest",
            align: "end",
            headerClassName: OLDEST_CLASS,
            cellClassName: OLDEST_CLASS,
          },
          cell: ({ row }) => formatDaysOverdue(row.original.oldestDaysOverdue),
        },
        bucketColumn("current", "Current"),
        bucketColumn("d30", "30d"),
        bucketColumn("d60", "60d"),
        bucketColumn("d90plus", "90d+"),
        {
          id: "signals",
          meta: { label: "Signals" },
          cell: ({ row }) => {
            const debtor = row.original;
            return (
              <div className="flex flex-wrap items-center gap-1">
                {debtor.collectionsExempt && <Badge variant="neutral">Exempt</Badge>}
                <TriageBadges signals={debtor.signals} signalDetails={debtor.signalDetails} />
                {!debtor.collectionsExempt && debtor.signals.length === 0 && <EmptyCell />}
              </div>
            );
          },
        },
        {
          id: "lastActivity",
          meta: { label: "Last activity", priority: "low" },
          cell: ({ row }) => {
            const activity = row.original.lastActivity;
            return activity ? (
              <div className="flex flex-col gap-0.5">
                <Badge variant={activityBadgeVariant(activity.status)}>
                  {formatSnakeCase(activity.status)}
                </Badge>
                <span className="text-muted-foreground font-mono text-xs tabular-nums">
                  {formatDateTime(activity.at)}
                </span>
              </div>
            ) : (
              <span className="text-muted-foreground text-xs">No chase history</span>
            );
          },
        },
      ]),
    [slug]
  );

  return (
    <DataTable
      columns={columns}
      data={debtors}
      getRowId={getDebtorRowId}
      empty={empty}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
    />
  );
}
