"use client";

import { useState } from "react";
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
import { Plus, X } from "lucide-react";
import { Label } from "@/components/ui/label";
import type { TagResponse } from "@/lib/types";

interface TagFilterProps {
  value: string[];
  onChange: (slugs: string[]) => void;
  allTags: TagResponse[];
}

export function TagFilter({ value, onChange, allTags }: TagFilterProps) {
  const [open, setOpen] = useState(false);

  const selectedSlugs = new Set(value);
  const selectedTags = allTags.filter((t) => selectedSlugs.has(t.slug));
  const availableTags = allTags.filter((t) => !selectedSlugs.has(t.slug));

  function handleAdd(tag: TagResponse) {
    onChange([...value, tag.slug]);
    setOpen(false);
  }

  function handleRemove(slug: string) {
    onChange(value.filter((s) => s !== slug));
  }

  return (
    <div className="space-y-2">
      <Label className="text-sm font-medium text-olive-700 dark:text-olive-300">
        Tags
      </Label>
      <div className="flex flex-wrap items-center gap-1.5">
        {selectedTags.map((tag) => (
          <Badge
            key={tag.slug}
            variant="outline"
            className="gap-1"
            style={
              tag.color
                ? { borderColor: tag.color, color: tag.color }
                : undefined
            }
          >
            {tag.name}
            <button
              type="button"
              onClick={() => handleRemove(tag.slug)}
              className="ml-0.5 rounded-full p-0.5 hover:bg-olive-200 dark:hover:bg-olive-700"
              aria-label={`Remove ${tag.name}`}
            >
              <X className="size-3" />
            </button>
          </Badge>
        ))}

        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" size="sm" className="h-7 gap-1 text-xs">
              <Plus className="size-3" />
              Add Tag
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-56 p-0" align="start">
            <Command>
              <CommandInput placeholder="Search tags..." />
              <CommandList>
                <CommandEmpty>No tags found.</CommandEmpty>
                {availableTags.map((tag) => (
                  <CommandItem
                    key={tag.id}
                    value={`${tag.name} ${tag.slug}`}
                    onSelect={() => handleAdd(tag)}
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
              </CommandList>
            </Command>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}
