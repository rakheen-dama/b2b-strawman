"use client";

import { useTransition } from "react";
import { MoreVertical, RefreshCw, ExternalLink, GitCompare } from "lucide-react";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RelativeDate } from "@/components/ui/relative-date";
import { SyncEntryStateBadge } from "@/components/integrations/SyncEntryStateBadge";
import {
  retrySyncEntryAction,
  reconcileSyncEntryAction,
} from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";
import type { SyncEntryResponse } from "@/lib/types";

interface SyncLogTableProps {
  entries: SyncEntryResponse[];
  slug: string;
  canReconcile: boolean;
}

const ENTITY_TYPE_LABELS: Record<string, { label: string; variant: "neutral" | "lead" }> = {
  INVOICE: { label: "Invoice", variant: "lead" },
  CUSTOMER: { label: "Customer", variant: "neutral" },
};

export function SyncLogTable({ entries, slug, canReconcile }: SyncLogTableProps) {
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

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Entity Type</TableHead>
          <TableHead>Entity</TableHead>
          <TableHead>Direction</TableHead>
          <TableHead>State</TableHead>
          <TableHead className="text-right">Attempts</TableHead>
          <TableHead>Last Error</TableHead>
          <TableHead>Created</TableHead>
          <TableHead>Completed</TableHead>
          <TableHead className="w-10" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map((entry) => {
          const entityLabel = ENTITY_TYPE_LABELS[entry.entityType] ?? {
            label: entry.entityType,
            variant: "neutral" as const,
          };

          const entityHref =
            entry.entityType === "INVOICE"
              ? `/org/${slug}/invoices/${entry.entityId}`
              : entry.entityType === "CUSTOMER"
                ? `/org/${slug}/customers/${entry.entityId}`
                : null;

          const showRetry = entry.state === "DEAD_LETTER";
          const showOpenInXeroInvoice =
            entry.state === "COMPLETED" && entry.externalId && entry.entityType === "INVOICE";
          const showOpenInXeroContact =
            entry.state === "COMPLETED" && entry.externalId && entry.entityType === "CUSTOMER";
          const showReconcile = entry.state === "RECONCILE_DRIFT" && canReconcile;
          const hasActions =
            showRetry || showOpenInXeroInvoice || showOpenInXeroContact || showReconcile;

          return (
            <TableRow key={entry.id}>
              <TableCell>
                <Badge variant={entityLabel.variant}>{entityLabel.label}</Badge>
              </TableCell>
              <TableCell>
                {entityHref ? (
                  <a
                    href={entityHref}
                    className="font-mono text-sm text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                  >
                    {entry.entityId.substring(0, 8)}...
                  </a>
                ) : (
                  <span className="font-mono text-sm text-slate-600 dark:text-slate-400">
                    {entry.entityId.substring(0, 8)}...
                  </span>
                )}
              </TableCell>
              <TableCell>
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  {entry.direction}
                </span>
              </TableCell>
              <TableCell>
                <SyncEntryStateBadge state={entry.state} />
              </TableCell>
              <TableCell className="text-right">
                <span className="font-mono text-sm tabular-nums">{entry.attemptCount}</span>
              </TableCell>
              <TableCell>
                {entry.lastErrorDetail ? (
                  <span
                    className="max-w-[200px] truncate text-sm text-red-600 dark:text-red-400"
                    title={entry.lastErrorDetail}
                  >
                    {entry.lastErrorCode ? `${entry.lastErrorCode}: ` : ""}
                    {entry.lastErrorDetail}
                  </span>
                ) : (
                  <span className="text-sm text-slate-400">-</span>
                )}
              </TableCell>
              <TableCell>
                <RelativeDate iso={entry.createdAt} />
              </TableCell>
              <TableCell>
                {entry.completedAt ? (
                  <RelativeDate iso={entry.completedAt} />
                ) : (
                  <span className="text-sm text-slate-400">-</span>
                )}
              </TableCell>
              <TableCell>
                {hasActions && (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="sm" className="size-8 p-0" disabled={isPending}>
                        <MoreVertical className="size-4" />
                        <span className="sr-only">Open menu</span>
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      {showRetry && (
                        <DropdownMenuItem onClick={() => handleRetry(entry.id)}>
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
                        <DropdownMenuItem onClick={() => handleReconcile(entry.id)}>
                          <GitCompare className="mr-2 size-4" />
                          Reconcile
                        </DropdownMenuItem>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
