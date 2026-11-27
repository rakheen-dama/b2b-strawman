"use client";

import { useLayoutEffect, useMemo, useRef, useTransition, type ReactNode } from "react";
import { Activity, MoreVertical, RefreshCw, ExternalLink, GitCompare } from "lucide-react";
import { toast } from "sonner";
import { useRouter } from "next/navigation";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { RelativeDate } from "@/components/ui/relative-date";
import { SyncEntryStateBadge } from "@/components/integrations/SyncEntryStateBadge";
import { buildPageHref } from "@/lib/page-href";
import {
  retrySyncEntryAction,
  reconcileSyncEntryAction,
} from "@/app/(app)/org/[slug]/settings/connections/xero/sync-log/actions";
import type { SyncEntryResponse } from "@/lib/types";

/**
 * The Sync Log's client wrapper (665F, ADR-395): the RSC page passes serializable props only — the
 * page's rows, zero-based `pageIndex`, `pageCount`, the query as a plain object and the base path —
 * plus the pre-rendered filters; the column defs and the pager's `hrefFor` are built here.
 */
interface SyncLogTableProps {
  entries: SyncEntryResponse[];
  slug: string;
  canReconcile: boolean;
  /** Zero-based (the API's `page.number`); the URL's `?page=` is ONE-based. */
  pageIndex: number;
  pageCount: number;
  basePath: string;
  /** The current filters (`state`, `entityType`, `direction`, `size`), kept by the pager links. */
  query: Record<string, string>;
  filters: ReactNode;
  emptyDescription: string;
}

/** A table row: the entry plus the render-time pending flag (the row menus disable while pending). */
interface SyncLogRow {
  entry: SyncEntryResponse;
  isPending: boolean;
}

const ENTITY_TYPE_LABELS: Record<string, { label: string; variant: "neutral" | "lead" }> = {
  INVOICE: { label: "Fee note", variant: "lead" },
  CUSTOMER: { label: "Client", variant: "neutral" },
};

export function SyncLogTable({
  entries,
  slug,
  canReconcile,
  pageIndex,
  pageCount,
  basePath,
  query,
  filters,
  emptyDescription,
}: SyncLogTableProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  function handleRetry(entryId: string) {
    startTransition(async () => {
      const result = await retrySyncEntryAction(slug, entryId);
      if (result.success) {
        toast.success("Sync retry queued.");
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to retry.");
      }
    });
  }

  function handleReconcile(entryId: string) {
    startTransition(async () => {
      const result = await reconcileSyncEntryAction(slug, entryId);
      if (result.success) {
        toast.success("Reconciliation resolved.");
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to reconcile.");
      }
    });
  }

  // Memoised columns reach today's handlers through a ref refreshed after each render (F-665A-4).
  const actions = useRef({ retry: handleRetry, reconcile: handleReconcile });
  useLayoutEffect(() => {
    actions.current = { retry: handleRetry, reconcile: handleReconcile };
  });

  const columns = useMemo(
    () =>
      defineColumns<SyncLogRow>([
        {
          id: "entityType",
          meta: { label: "Entity Type" },
          cell: ({ row }) => {
            const entityLabel = ENTITY_TYPE_LABELS[row.original.entry.entityType] ?? {
              label: row.original.entry.entityType,
              variant: "neutral" as const,
            };
            return <Badge variant={entityLabel.variant}>{entityLabel.label}</Badge>;
          },
        },
        {
          id: "entity",
          meta: { label: "Entity" },
          cell: ({ row }) => {
            const entry = row.original.entry;
            const entityHref =
              entry.entityType === "INVOICE"
                ? `/org/${slug}/invoices/${entry.entityId}`
                : entry.entityType === "CUSTOMER"
                  ? `/org/${slug}/clients/${entry.entityId}`
                  : null;
            return entityHref ? (
              <a
                href={entityHref}
                className="font-mono text-sm text-(--button-accent) hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
              >
                {entry.entityId.substring(0, 8)}...
              </a>
            ) : (
              <span className="text-muted-foreground font-mono text-sm">
                {entry.entityId.substring(0, 8)}...
              </span>
            );
          },
        },
        {
          id: "direction",
          meta: { label: "Direction" },
          cell: ({ row }) => (
            <span className="text-muted-foreground text-sm">{row.original.entry.direction}</span>
          ),
        },
        {
          id: "state",
          meta: { label: "State" },
          cell: ({ row }) => <SyncEntryStateBadge state={row.original.entry.state} />,
        },
        {
          id: "attempts",
          meta: { label: "Attempts", align: "end" },
          cell: ({ row }) => (
            <span className="font-mono text-sm tabular-nums">
              {row.original.entry.attemptCount}
            </span>
          ),
        },
        {
          id: "lastError",
          meta: { label: "Last Error" },
          cell: ({ row }) => {
            const entry = row.original.entry;
            return entry.lastErrorDetail ? (
              <span
                className="max-w-[200px] truncate text-sm text-red-600 dark:text-red-400"
                title={entry.lastErrorDetail}
              >
                {entry.lastErrorCode ? `${entry.lastErrorCode}: ` : ""}
                {entry.lastErrorDetail}
              </span>
            ) : (
              <span className="text-sm text-slate-400">-</span>
            );
          },
        },
        {
          id: "created",
          meta: { label: "Created" },
          cell: ({ row }) => <RelativeDate iso={row.original.entry.createdAt} />,
        },
        {
          id: "completed",
          meta: { label: "Completed" },
          cell: ({ row }) =>
            row.original.entry.completedAt ? (
              <RelativeDate iso={row.original.entry.completedAt} />
            ) : (
              <span className="text-sm text-slate-400">-</span>
            ),
        },
        {
          id: "actions",
          enableHiding: false,
          header: () => null,
          meta: { label: "", headerClassName: "w-10" },
          cell: ({ row }) => {
            const entry = row.original.entry;
            const showRetry = entry.state === "DEAD_LETTER";
            const showOpenInXeroInvoice =
              entry.state === "COMPLETED" && entry.externalId && entry.entityType === "INVOICE";
            const showOpenInXeroContact =
              entry.state === "COMPLETED" && entry.externalId && entry.entityType === "CUSTOMER";
            const showReconcile = entry.state === "RECONCILE_DRIFT" && canReconcile;
            const hasActions =
              showRetry || showOpenInXeroInvoice || showOpenInXeroContact || showReconcile;
            if (!hasActions) return null;
            return (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="size-8 p-0"
                    disabled={row.original.isPending}
                  >
                    <MoreVertical className="size-4" />
                    <span className="sr-only">Open menu</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {showRetry && (
                    <DropdownMenuItem onClick={() => actions.current.retry(entry.id)}>
                      <RefreshCw className="mr-2 size-4" />
                      Retry
                    </DropdownMenuItem>
                  )}
                  {showOpenInXeroInvoice && (
                    <DropdownMenuItem asChild>
                      <a
                        href={`https://go.xero.com/AccountsReceivable/View.aspx?InvoiceID=${entry.externalId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <ExternalLink className="mr-2 size-4" />
                        Open in Xero
                      </a>
                    </DropdownMenuItem>
                  )}
                  {showOpenInXeroContact && (
                    <DropdownMenuItem asChild>
                      <a
                        href={`https://go.xero.com/Contacts/View/${entry.externalId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <ExternalLink className="mr-2 size-4" />
                        Open in Xero
                      </a>
                    </DropdownMenuItem>
                  )}
                  {showReconcile && (
                    <DropdownMenuItem onClick={() => actions.current.reconcile(entry.id)}>
                      <GitCompare className="mr-2 size-4" />
                      Reconcile
                    </DropdownMenuItem>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            );
          },
        },
      ]),
    [slug, canReconcile]
  );

  const rows = useMemo(() => entries.map((entry) => ({ entry, isPending })), [entries, isPending]);

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => row.entry.id}
      toolbar={{ filters }}
      // URL-owned server paging: the kit pager links keep every filter and set the ONE-based page.
      pagination={{
        pageIndex,
        pageCount,
        hrefFor: (index) => buildPageHref(basePath, query, index + 1),
      }}
      stickyHeader={false}
      empty={
        <EmptyState
          variant="default"
          icon={Activity}
          title="No sync entries found"
          description={emptyDescription}
        />
      }
    />
  );
}
