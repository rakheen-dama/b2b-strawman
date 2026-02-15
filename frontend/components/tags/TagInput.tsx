"use client";

import { useState, useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Plus, X, Loader2 } from "lucide-react";
import {
  createTagAction,
  setEntityTagsAction,
} from "@/app/(app)/org/[slug]/settings/tags/actions";
import type { EntityType, TagResponse } from "@/lib/types";

function getContrastColor(hexColor: string): string {
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? "#000000" : "#FFFFFF";
}

interface TagInputProps {
  entityType: EntityType;
  entityId: string;
  tags: TagResponse[];
  allTags: TagResponse[];
  editable: boolean;
  canInlineCreate: boolean;
  slug: string;
}

export function TagInput({
  entityType,
  entityId,
  tags,
  allTags,
  editable,
  canInlineCreate,
  slug,
}: TagInputProps) {
  const [localTags, setLocalTags] = useState<TagResponse[]>(tags);
  const [open, setOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchValue, setSearchValue] = useState("");

  const appliedTagIds = new Set(localTags.map((t) => t.id));

  const unappliedTags = allTags.filter((t) => !appliedTagIds.has(t.id));

  const saveEntityTags = useCallback(
    async (newTags: TagResponse[]) => {
      setIsUpdating(true);
      setError(null);

      try {
        const result = await setEntityTagsAction(
          slug,
          entityType,
          entityId,
          newTags.map((t) => t.id),
        );
        if (!result.success) {
          setError(result.error ?? "Failed to update tags.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setIsUpdating(false);
      }
    },
    [slug, entityType, entityId],
  );

  const handleAddTag = useCallback(
    async (tag: TagResponse) => {
      const newTags = [...localTags, tag];
      setLocalTags(newTags);
      setOpen(false);
      setSearchValue("");
      await saveEntityTags(newTags);
    },
    [localTags, saveEntityTags],
  );

  const handleRemoveTag = useCallback(
    async (tagId: string) => {
      const newTags = localTags.filter((t) => t.id !== tagId);
      setLocalTags(newTags);
      await saveEntityTags(newTags);
    },
    [localTags, saveEntityTags],
  );

  const handleInlineCreate = useCallback(
    async (name: string) => {
      if (!canInlineCreate) return;

      setIsUpdating(true);
      setError(null);

      try {
        const result = await createTagAction(slug, {
          name: name.trim(),
          color: null,
        });
        if (result.success && result.tag) {
          const newTags = [...localTags, result.tag];
          setLocalTags(newTags);
          setOpen(false);
          setSearchValue("");
          await saveEntityTags(newTags);
        } else {
          setError(result.error ?? "Failed to create tag.");
          setIsUpdating(false);
        }
      } catch {
        setError("An unexpected error occurred.");
        setIsUpdating(false);
      }
    },
    [canInlineCreate, slug, localTags, saveEntityTags],
  );

  // Read-only: just render badges
  if (!editable) {
    if (localTags.length === 0) {
      return (
        <div data-testid="tag-input">
          <span className="text-sm text-olive-400 dark:text-olive-600">
            No tags
          </span>
        </div>
      );
    }

    return (
      <div className="flex flex-wrap gap-1.5" data-testid="tag-input">
        {localTags.map((tag) => (
          <Badge
            key={tag.id}
            variant="outline"
            style={
              tag.color
                ? {
                    backgroundColor: tag.color,
                    color: getContrastColor(tag.color),
                    borderColor: tag.color,
                  }
                : undefined
            }
          >
            {tag.name}
          </Badge>
        ))}
      </div>
    );
  }

  const searchTrimmed = searchValue.trim().toLowerCase();
  const hasExactMatch = allTags.some(
    (t) => t.name.toLowerCase() === searchTrimmed,
  );
  const showCreateOption =
    canInlineCreate && searchTrimmed.length > 0 && !hasExactMatch;

  return (
    <div className="space-y-2" data-testid="tag-input">
      <div className="flex flex-wrap items-center gap-1.5">
        {localTags.map((tag) => (
          <Badge
            key={tag.id}
            variant="outline"
            className="gap-1"
            style={
              tag.color
                ? {
                    backgroundColor: tag.color,
                    color: getContrastColor(tag.color),
                    borderColor: tag.color,
                  }
                : undefined
            }
          >
            {tag.name}
            <button
              type="button"
              onClick={() => handleRemoveTag(tag.id)}
              disabled={isUpdating}
              className="ml-0.5 rounded-full p-0.5 hover:bg-olive-200 dark:hover:bg-olive-700"
              aria-label={`Remove ${tag.name}`}
            >
              <X className="size-3" />
            </button>
          </Badge>
        ))}

        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              className="h-7 gap-1 text-xs"
              disabled={isUpdating}
            >
              {isUpdating ? (
                <Loader2 className="size-3 animate-spin" />
              ) : (
                <Plus className="size-3" />
              )}
              Add Tag
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-56 p-0" align="start">
            <Command>
              <CommandInput
                placeholder="Search tags..."
                value={searchValue}
                onValueChange={setSearchValue}
              />
              <CommandList>
                <CommandEmpty>
                  {showCreateOption ? null : "No tags found."}
                </CommandEmpty>
                {unappliedTags.map((tag) => (
                  <CommandItem
                    key={tag.id}
                    value={`${tag.name} ${tag.slug}`}
                    onSelect={() => handleAddTag(tag)}
                  >
                    {tag.color && (
                      <span
                        className="mr-2 inline-block size-3 rounded-full"
                        style={{ backgroundColor: tag.color }}
                      />
                    )}
                    {tag.name}
                  </CommandItem>
                ))}
                {showCreateOption && (
                  <CommandItem
                    value={`create-new-${searchValue.trim()}`}
                    onSelect={() => handleInlineCreate(searchValue)}
                  >
                    <Plus className="mr-2 size-3" />
                    Create &quot;{searchValue.trim()}&quot;
                  </CommandItem>
                )}
              </CommandList>
            </Command>
          </PopoverContent>
        </Popover>
      </div>
      {error && (
        <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}
