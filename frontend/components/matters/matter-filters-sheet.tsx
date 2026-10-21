"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { SlidersHorizontal } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { TagFilter } from "@/components/views/TagFilter";
import type { TagResponse } from "@/lib/types";

interface MatterFiltersSheetProps {
  allTags: TagResponse[];
}

function parseTags(raw: string | null): string[] {
  return (raw ?? "")
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);
}

/**
 * Matters toolbar filters in a `Sheet` (R-7). 615C carries the tag filter only (F-3 ruling (C)):
 * the list endpoint's exact-match custom-field semantics would mis-filter range/contains fields,
 * so custom-field filtering stays in the Save-view dialog. Apply / Clear write `?tags=`.
 */
export function MatterFiltersSheet({ allTags }: MatterFiltersSheetProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const applied = parseTags(searchParams.get("tags"));
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<string[]>(applied);

  function writeTags(tags: string[]) {
    const params = new URLSearchParams(searchParams.toString());
    if (tags.length > 0) params.set("tags", tags.join(","));
    else params.delete("tags");
    const qs = params.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  }

  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        if (next) setDraft(applied);
        setOpen(next);
      }}
    >
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="h-8 gap-1.5"
        onClick={() => {
          setDraft(applied);
          setOpen(true);
        }}
        data-testid="matter-filters-trigger"
      >
        <SlidersHorizontal className="size-3.5" />
        Filters
        {applied.length > 0 && (
          <span className="rounded-full bg-slate-900 px-1.5 text-xs text-slate-50 dark:bg-slate-100 dark:text-slate-900">
            {applied.length}
          </span>
        )}
      </Button>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle>Filter matters</SheetTitle>
          <SheetDescription>Show only matters carrying every selected tag.</SheetDescription>
        </SheetHeader>
        <div className="px-4">
          <TagFilter value={draft} onChange={setDraft} allTags={allTags} />
        </div>
        <SheetFooter className="flex-row justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setDraft([]);
              setOpen(false);
              writeTags([]);
            }}
          >
            Clear
          </Button>
          <Button
            type="button"
            onClick={() => {
              setOpen(false);
              writeTags(draft);
            }}
          >
            Apply
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
