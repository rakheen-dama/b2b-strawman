"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { usePortalContext } from "@/hooks/use-portal-context";
import { DeadlineList } from "@/components/deadlines/deadline-list";
import { DeadlineDetailPanel } from "@/components/deadlines/deadline-detail-panel";
import {
  listDeadlines,
  type PortalDeadline,
  type PortalDeadlineStatus,
  type PortalDeadlineType,
} from "@/lib/api/deadlines";

const DEADLINES_MODULE = "deadlines";

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

/**
 * Default window: today..today+60d. The backend defaults to the same range
 * when no query params are sent, but we set them explicitly so the UI can
 * show the window to the user if we ever add a date selector.
 */
function defaultWindow(): { from: string; to: string } {
  const now = new Date();
  const from = toIsoDate(now);
  const plus60 = new Date(now);
  plus60.setDate(plus60.getDate() + 60);
  const to = toIsoDate(plus60);
  return { from, to };
}

export default function DeadlinesPage() {
  const ctx = usePortalContext();
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

  const window = useMemo(() => defaultWindow(), []);

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
    setIsLoading(true);
    setError(null);
    (async () => {
      try {
        const data = await listDeadlines({
          from: window.from,
          to: window.to,
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
  }, [ctx, statusFilter, window]);

  const selectedDeadline = useMemo(() => {
    if (!selectedKey || !deadlines) return null;
    return (
      deadlines.find((d) => `${d.sourceEntity}-${d.id}` === selectedKey) ??
      null
    );
  }, [selectedKey, deadlines]);

  function handleSelect(d: PortalDeadline) {
    setSelectedKey(`${d.sourceEntity}-${d.id}`);
    setPanelOpen(true);
  }

  function handleClosePanel() {
    setPanelOpen(false);
  }

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Deadlines
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
