"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import { ActivityFilter } from "@/components/activity/activity-filter";
import { ActivityItem } from "@/components/activity/activity-item";
import { loadMoreActivity } from "@/app/(app)/org/[slug]/projects/[id]/activity-actions";
import type { ActivityItem as ActivityItemType } from "@/lib/actions/activity";

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
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(initialTotalPages);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const reload = useCallback(
    async (entityType: string | null) => {
      try {
        const data = await loadMoreActivity(
          projectId,
          entityType ?? undefined,
          0
        );
        setItems(data.content);
        setTotalPages(data.totalPages);
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

  async function handleLoadMore() {
    setIsLoadingMore(true);
    try {
      const nextPage = page + 1;
      const data = await loadMoreActivity(
        projectId,
        filter ?? undefined,
        nextPage
      );
      setItems((prev) => [...prev, ...data.content]);
      setTotalPages(data.totalPages);
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
      {/* Filter chips */}
      <ActivityFilter
        onFilterChange={handleFilterChange}
        currentFilter={filter}
      />

      {/* Activity list */}
      <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-950">
        {items.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500 dark:text-slate-400">
            No activity yet
          </p>
        )}

        {items.map((item) => (
          <ActivityItem key={item.id} item={item} />
        ))}
      </div>

      {/* Load more */}
      {hasMore && (
        <div className="flex justify-center">
          <Button
            variant="ghost"
            onClick={handleLoadMore}
            disabled={isLoadingMore}
          >
            {isLoadingMore ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
