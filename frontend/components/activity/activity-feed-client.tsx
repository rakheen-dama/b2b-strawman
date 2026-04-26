"use client";

import { useCallback, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ActivityFilter } from "@/components/activity/activity-filter";
import { ActivityItem } from "@/components/activity/activity-item";
import { loadMoreActivity } from "@/app/(app)/org/[slug]/projects/[id]/activity-actions";
import type { ActivityItem as ActivityItemType } from "@/lib/actions/activity";

const ALL_ACTORS_VALUE = "__all__";

interface ActivityFeedClientProps {
  projectId: string;
  initialItems: ActivityItemType[];
  initialTotalPages: number;
}

export function ActivityFeedClient({
  projectId,
  initialItems,
  initialTotalPages,
}: ActivityFeedClientProps) {
  const [items, setItems] = useState<ActivityItemType[]>(initialItems);
  const [filter, setFilter] = useState<string | null>(null);
  const [actorFilter, setActorFilter] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(initialTotalPages);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // Client-side actor extraction: derive distinct actors from events already loaded.
  // The activity DTO currently exposes `actorName` but not `actorId`, so we key on
  // actorName (which functions as the identifier in the current API shape).
  // TODO(Phase 69): replace this client-side derivation with the backend facet
  // endpoint `GET /api/audit-events/facets/actors?from=&to=` once it ships, which
  // will return the full set of actors that ever touched the matter (not just the
  // ones present in the current event window).
  const distinctActors = useMemo(() => {
    const seen = new Map<string, string>();
    for (const item of items) {
      if (item.actorName && !seen.has(item.actorName)) {
        seen.set(item.actorName, item.actorName);
      }
    }
    return Array.from(seen.entries())
      .map(([key, name]) => ({ key, name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [items]);

  const visibleItems = useMemo(() => {
    if (!actorFilter) return items;
    return items.filter((item) => item.actorName === actorFilter);
  }, [items, actorFilter]);

  const reload = useCallback(
    async (entityType: string | null) => {
      try {
        const data = await loadMoreActivity(projectId, entityType ?? undefined, 0);
        setItems(data.content);
        setTotalPages(data.page.totalPages);
        setPage(0);
      } catch {
        // Keep existing state on error
      }
    },
    [projectId]
  );

  async function handleFilterChange(entityType: string | null) {
    setFilter(entityType);
    await reload(entityType);
  }

  function handleActorChange(value: string) {
    setActorFilter(value === ALL_ACTORS_VALUE ? null : value);
  }

  async function handleLoadMore() {
    setIsLoadingMore(true);
    try {
      const nextPage = page + 1;
      const data = await loadMoreActivity(projectId, filter ?? undefined, nextPage);
      setItems((prev) => [...prev, ...data.content]);
      setTotalPages(data.page.totalPages);
      setPage(nextPage);
    } catch {
      // Keep existing state on error
    } finally {
      setIsLoadingMore(false);
    }
  }

  const hasMore = page + 1 < totalPages;

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        <ActivityFilter onFilterChange={handleFilterChange} currentFilter={filter} />
        <Select
          value={actorFilter ?? ALL_ACTORS_VALUE}
          onValueChange={handleActorChange}
        >
          <SelectTrigger className="h-8 w-48" aria-label="Filter by actor">
            <SelectValue placeholder="Filter by actor" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_ACTORS_VALUE}>All actors</SelectItem>
            {distinctActors.map((actor) => (
              <SelectItem key={actor.key} value={actor.key}>
                {actor.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Activity list */}
      <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-950">
        {visibleItems.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500 dark:text-slate-400">
            {actorFilter && items.length > 0
              ? `No activity from ${actorFilter}`
              : "No activity yet"}
          </p>
        )}

        {visibleItems.map((item) => (
          <ActivityItem key={item.id} item={item} />
        ))}
      </div>

      {/* Load more */}
      {hasMore && (
        <div className="flex justify-center">
          <Button variant="ghost" onClick={handleLoadMore} disabled={isLoadingMore}>
            {isLoadingMore ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
