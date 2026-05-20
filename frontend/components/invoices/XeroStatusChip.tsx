"use client";

import { useEffect, useState, useTransition } from "react";
import { ExternalLink, RefreshCw, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  getInvoiceSyncStatusAction,
  retrySyncEntryAction,
  reconcileSyncEntryAction,
  forceResyncInvoiceAction,
} from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";
import type { SyncEntryResponse, SyncState } from "@/lib/types";

interface XeroStatusChipProps {
  invoiceId: string;
  slug: string;
  canReconcile?: boolean;
}

export function XeroStatusChip({ invoiceId, slug, canReconcile = false }: XeroStatusChipProps) {
  const [entry, setEntry] = useState<SyncEntryResponse | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const result = await getInvoiceSyncStatusAction(slug, invoiceId, "INVOICE");
        if (!cancelled && result.success && result.data) {
          setEntry(result.data);
        }
      } catch {
        // Not synced — show nothing
      } finally {
        if (!cancelled) setLoaded(true);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [invoiceId, slug]);

  function handleRetry() {
    if (!entry) return;
    startTransition(async () => {
      const result = await retrySyncEntryAction(slug, entry.id);
      if (result.success) {
        toast.success("Sync retry queued.");
        setEntry((prev) => (prev ? { ...prev, state: "PENDING" as SyncState } : prev));
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
        setEntry((prev) => (prev ? { ...prev, state: "COMPLETED" as SyncState } : prev));
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
        setEntry((prev) => (prev ? { ...prev, state: "PENDING" as SyncState } : prev));
      } else {
        toast.error(result.error ?? "Failed to resync.");
      }
    });
  }

  if (!loaded) return null;
  if (!entry) return null;

  const state = entry.state;

  if (state === "COMPLETED" && entry.externalId) {
    return (
      <div className="flex items-center gap-2">
        <Badge variant="success">
          <ExternalLink className="mr-1 size-3" />
          Synced to Xero
        </Badge>
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
