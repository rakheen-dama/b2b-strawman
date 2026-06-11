"use client";

import { useTransition } from "react";
import useSWR from "swr";
import { ExternalLink, RefreshCw, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  getInvoiceSyncStatusAction,
  retrySyncEntryAction,
  reconcileSyncEntryAction,
  forceResyncInvoiceAction,
} from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";
import { defaultSWROptions } from "@/lib/swr/fetcher";
import type { SyncEntryResponse } from "@/lib/types";

interface XeroStatusChipProps {
  invoiceId: string;
  slug: string;
  canReconcile?: boolean;
}

export function XeroStatusChip({ invoiceId, slug, canReconcile = false }: XeroStatusChipProps) {
  const [isPending, startTransition] = useTransition();

  const { data: entry, mutate } = useSWR<SyncEntryResponse | null>(
    `xero-invoice-status-${slug}-${invoiceId}`,
    async () => {
      const result = await getInvoiceSyncStatusAction(slug, invoiceId, "INVOICE");
      return result.success && result.data ? result.data : null;
    },
    defaultSWROptions
  );

  function handleRetry() {
    if (!entry) return;
    startTransition(async () => {
      const result = await retrySyncEntryAction(slug, entry.id);
      if (result.success) {
        toast.success("Sync retry queued.");
        mutate();
      } else {
        toast.error(result.error ?? "Failed to retry.");
      }
    });
  }

  function handleReconcile() {
    if (!entry) return;
    startTransition(async () => {
      const result = await reconcileSyncEntryAction(slug, entry.id);
      if (result.success) {
        toast.success("Reconciliation resolved.");
        mutate();
      } else {
        toast.error(result.error ?? "Failed to reconcile.");
      }
    });
  }

  function handleResync() {
    startTransition(async () => {
      const result = await forceResyncInvoiceAction(slug, invoiceId);
      if (result.success) {
        toast.success("Resync queued.");
        mutate();
      } else {
        toast.error(result.error ?? "Failed to resync.");
      }
    });
  }

  if (!entry) return null;

  const state = entry.state;

  if (state === "COMPLETED" && entry.externalId) {
    return (
      <div className="flex items-center gap-2">
        <a
          href={`https://go.xero.com/AccountsReceivable/View.aspx?InvoiceID=${entry.externalId}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          <Badge variant="success">
            <ExternalLink className="mr-1 size-3" />
            Synced to Xero
          </Badge>
        </a>
      </div>
    );
  }

  if (state === "PENDING" || state === "IN_FLIGHT") {
    return (
      <Badge
        variant="neutral"
        className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300"
      >
        <RefreshCw className="mr-1 size-3 animate-spin" />
        {state === "PENDING" ? "Sync Pending" : "Syncing..."}
      </Badge>
    );
  }

  if (state === "DEAD_LETTER" || state === "FAILED_RETRYING") {
    return (
      <div className="flex items-center gap-2">
        <Badge variant="destructive">
          <AlertTriangle className="mr-1 size-3" />
          Sync Failed
        </Badge>
        {state === "DEAD_LETTER" && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleRetry}
            disabled={isPending}
            className="h-6 px-2 text-xs"
          >
            {isPending ? "Retrying..." : "Retry"}
          </Button>
        )}
      </div>
    );
  }

  if (state === "BLOCKED_TRUST_BOUNDARY") {
    return (
      <Badge
        variant="neutral"
        className="bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300"
      >
        Blocked (Trust Boundary)
      </Badge>
    );
  }

  if (state === "RECONCILE_DRIFT") {
    return (
      <div className="flex items-center gap-2">
        <Badge
          variant="neutral"
          className="bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300"
        >
          Reconcile Drift
        </Badge>
        {canReconcile && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleReconcile}
            disabled={isPending}
            className="h-6 px-2 text-xs"
          >
            {isPending ? "Resolving..." : "Reconcile"}
          </Button>
        )}
      </div>
    );
  }

  // Fallback — not synced
  return null;
}
