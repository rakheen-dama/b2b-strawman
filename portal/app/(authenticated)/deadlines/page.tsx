"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  usePortalContext,
  usePortalContextIsSettled,
} from "@/hooks/use-portal-context";
import { DeadlineList } from "@/components/deadlines/deadline-list";
import { DeadlineDetailPanel } from "@/components/deadlines/deadline-detail-panel";
import { Skeleton } from "@b2mash/ui/skeleton";
import {
  listDeadlines,
  type PortalDeadline,
  type PortalDeadlineStatus,
  type PortalDeadlineType,
} from "@/lib/api/deadlines";

const DEADLINES_MODULE = "deadlines";

export default function DeadlinesPage() {
  const ctx = usePortalContext();
  const isSettled = usePortalContextIsSettled();
  const router = useRouter();

  const [statusFilter, setStatusFilter] = useState<
    "ALL" | PortalDeadlineStatus
  >("ALL");
  const [typeFilter, setTypeFilter] = useState<"ALL" | PortalDeadlineType>(
    "ALL",
  );

  const [deadlines, setDeadlines] = useState<PortalDeadline[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);

  // Module gate: redirect once the portal context has loaded and the
  // `deadlines` module is not enabled. Backend also 404s (source of truth).
  useEffect(() => {
    if (ctx && !ctx.enabledModules.includes(DEADLINES_MODULE)) {
      router.replace("/home");
    }
  }, [ctx, router]);

  // Fetch deadlines once the entitlement is confirmed (and on filter change).
  useEffect(() => {
    if (!ctx?.enabledModules.includes(DEADLINES_MODULE)) return;
    let cancelled = false;
    (async () => {
      setIsLoading(true);
      setError(null);
      if (cancelled) return;
      try {
        // No from/to: the backend defaults the window to today..today+60 on the
        // South African calendar, evaluated per request, with no lower bound for
        // the Overdue, Completed and Cancelled filters (D-127). A window built
        // from the browser's local date started on SA-tomorrow east of SA late
        // in the evening (Dubai from 22:00 SAST), dropping today's deadlines.
        const data = await listDeadlines({
          // Never send "ALL" — omit the param so the backend returns all statuses.
          status: statusFilter === "ALL" ? undefined : statusFilter,
        });
        if (cancelled) return;
        setDeadlines(data);
        setError(null);
      } catch (err) {
        if (!cancelled) {
          console.error("Failed to load deadlines", err);
          setError("Failed to load deadlines");
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [ctx, statusFilter]);

  const selectedDeadline = useMemo(() => {
    if (!selectedKey || !deadlines) return null;
    return (
      deadlines.find((d) => `${d.sourceEntity}-${d.id}` === selectedKey) ?? null
    );
  }, [selectedKey, deadlines]);

  function handleSelect(d: PortalDeadline) {
    setSelectedKey(`${d.sourceEntity}-${d.id}`);
    setPanelOpen(true);
  }

  function handleClosePanel() {
    setPanelOpen(false);
  }

  // Avoid flash-of-gated-content: don't render the list until the portal
  // session context has resolved. If the module is disabled, the redirect
  // effect above will fire as soon as the context arrives.
  if (!isSettled || !ctx?.enabledModules.includes(DEADLINES_MODULE)) {
    return (
      <div>
        <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
          Diary dates
        </h1>
        <div className="space-y-3" data-testid="deadlines-context-loading">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Diary dates
      </h1>
      <DeadlineList
        deadlines={deadlines}
        isLoading={isLoading}
        error={error}
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
        typeFilter={typeFilter}
        onTypeFilterChange={setTypeFilter}
        onSelect={handleSelect}
        selectedKey={selectedKey}
      />
      <DeadlineDetailPanel
        open={panelOpen}
        deadline={selectedDeadline}
        onClose={handleClosePanel}
      />
    </div>
  );
}
