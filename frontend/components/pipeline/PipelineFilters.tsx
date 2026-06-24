"use client";

import { useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import { KanbanSquare, List } from "lucide-react";
import { TagFilter } from "@/components/views/TagFilter";
import type { TagResponse } from "@/lib/types";

export interface PipelineFiltersProps {
  allTags: TagResponse[];
  display: "board" | "list";
}

export function PipelineFilters({ allTags, display }: PipelineFiltersProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  const selectedTags = searchParams.get("tags")?.split(",").filter(Boolean) ?? [];

  const setParam = useCallback(
    (key: string, value: string | null) => {
      const params = new URLSearchParams(searchParams.toString());
      if (value) params.set(key, value);
      else params.delete(key);
      const qs = params.toString();
      router.push(qs ? `?${qs}` : "?");
    },
    [router, searchParams]
  );

  const handleTagsChange = useCallback(
    (slugs: string[]) => {
      setParam("tags", slugs.length > 0 ? slugs.join(",") : null);
    },
    [setParam]
  );

  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <TagFilter value={selectedTags} onChange={handleTagsChange} allTags={allTags} />

      <div className="flex items-center gap-1 rounded-md border border-slate-200 p-0.5 dark:border-slate-800">
        <Button
          type="button"
          size="sm"
          variant={display === "board" ? "default" : "plain"}
          className="h-7 gap-1 text-xs"
          onClick={() => setParam("display", "board")}
        >
          <KanbanSquare className="size-3.5" /> Board
        </Button>
        <Button
          type="button"
          size="sm"
          variant={display === "list" ? "default" : "plain"}
          className="h-7 gap-1 text-xs"
          onClick={() => setParam("display", "list")}
        >
          <List className="size-3.5" /> List
        </Button>
      </div>
    </div>
  );
}
