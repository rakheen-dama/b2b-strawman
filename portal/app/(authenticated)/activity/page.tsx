"use client";

import { useCallback, useEffect, useState } from "react";
import { Activity as ActivityIcon } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatRelativeDate } from "@/lib/format";
import { Skeleton } from "@/components/ui/skeleton";

type ActivityTab = "mine" | "firm";

interface PortalActivityEvent {
  id: string;
  eventType: string;
  actorType: string;
  actorName: string | null;
  entityId: string;
  entityType: string;
  projectId: string | null;
  summary: string;
  occurredAt: string;
}

interface ActivityPage {
  content: PortalActivityEvent[];
  page?: { totalElements: number };
}

function ListSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-14 w-full" />
      ))}
    </div>
  );
}

export default function ActivityPage() {
  const [tab, setTab] = useState<ActivityTab>("mine");
  const [events, setEvents] = useState<PortalActivityEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchEvents = useCallback(async (currentTab: ActivityTab) => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<ActivityPage>(
        `/portal/activity?tab=${currentTab.toUpperCase()}`,
      );
      setEvents(data.content ?? []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load activity");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchEvents(tab);
  }, [fetchEvents, tab]);

  return (
    <div>
      <h1 className="font-display mb-2 text-2xl font-semibold text-slate-900">
        Activity
      </h1>
      <p className="mb-6 text-sm text-slate-600">
        A timeline of actions on your matter.
      </p>

      <div
        role="tablist"
        aria-label="Activity tabs"
        className="mb-6 inline-flex rounded-md border border-slate-200 bg-white p-1"
      >
        <button
          type="button"
          role="tab"
          aria-selected={tab === "mine"}
          onClick={() => setTab("mine")}
          className={`inline-flex min-h-11 items-center rounded px-3 py-1.5 text-sm font-medium ${
            tab === "mine"
              ? "bg-teal-600 text-white"
              : "text-slate-600 hover:text-slate-900"
          }`}
        >
          Your actions
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "firm"}
          onClick={() => setTab("firm")}
          className={`inline-flex min-h-11 items-center rounded px-3 py-1.5 text-sm font-medium ${
            tab === "firm"
              ? "bg-teal-600 text-white"
              : "text-slate-600 hover:text-slate-900"
          }`}
        >
          Firm actions
        </button>
      </div>

      {isLoading && <ListSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p className="mb-2">{error}</p>
          <button
            type="button"
            onClick={() => fetchEvents(tab)}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
          >
            Try again
          </button>
        </div>
      )}

      {!isLoading && !error && events.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <ActivityIcon className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">
            No activity yet.
          </p>
        </div>
      )}

      {!isLoading && !error && events.length > 0 && (
        <ul
          data-testid="activity-list"
          className="flex flex-col gap-2 overflow-hidden rounded-lg border border-slate-200 bg-white"
        >
          {events.map((event) => (
            <li
              key={event.id}
              className="flex items-start justify-between gap-4 border-b border-slate-100 px-4 py-3 last:border-b-0"
            >
              <div className="flex flex-col">
                <span className="text-sm font-medium text-slate-900">
                  {event.summary}
                </span>
                <span className="text-xs text-slate-500">
                  {event.actorType === "PORTAL_CONTACT"
                    ? "You"
                    : event.actorName ?? "Firm"}
                </span>
              </div>
              <span className="text-xs text-slate-500" suppressHydrationWarning>
                {formatRelativeDate(event.occurredAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
