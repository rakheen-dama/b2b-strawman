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
import { setEntityFieldGroupsAction } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";
import type { EntityType, FieldGroupResponse } from "@/lib/types";

interface FieldGroupSelectorProps {
  entityType: EntityType;
  entityId: string;
  appliedFieldGroups: string[];
  slug: string;
  canManage: boolean;
  allGroups: FieldGroupResponse[];
}

export function FieldGroupSelector({
  entityType,
  entityId,
  appliedFieldGroups,
  slug,
  canManage,
  allGroups,
}: FieldGroupSelectorProps) {
  const [open, setOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const appliedGroupObjects = allGroups
    .filter((g) => appliedFieldGroups.includes(g.id) && g.active)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  const unappliedGroups = allGroups
    .filter((g) => !appliedFieldGroups.includes(g.id) && g.active)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  const handleAddGroup = useCallback(
    async (groupId: string) => {
      setIsUpdating(true);
      setError(null);
      setOpen(false);

      try {
        const result = await setEntityFieldGroupsAction(
          slug,
          entityType,
          entityId,
          [...appliedFieldGroups, groupId],
        );
        if (!result.success) {
          setError(result.error ?? "Failed to add field group.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setIsUpdating(false);
      }
    },
    [slug, entityType, entityId, appliedFieldGroups],
  );

  const handleRemoveGroup = useCallback(
    async (groupId: string) => {
      setIsUpdating(true);
      setError(null);

      try {
        const result = await setEntityFieldGroupsAction(
          slug,
          entityType,
          entityId,
          appliedFieldGroups.filter((id) => id !== groupId),
        );
        if (!result.success) {
          setError(result.error ?? "Failed to remove field group.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setIsUpdating(false);
      }
    },
    [slug, entityType, entityId, appliedFieldGroups],
  );

  // Show nothing if no groups available at all and not managing
  if (allGroups.filter((g) => g.active).length === 0) {
    return null;
  }

  return (
    <div className="space-y-2" data-testid="field-group-selector">
      <p className="text-xs font-medium uppercase tracking-wide text-olive-500 dark:text-olive-400">
        Field Groups
      </p>
      <div className="flex flex-wrap items-center gap-2">
        {appliedGroupObjects.map((group) => (
          <Badge key={group.id} variant="outline" className="gap-1">
            {group.name}
            {canManage && (
              <button
                type="button"
                onClick={() => handleRemoveGroup(group.id)}
                disabled={isUpdating}
                className="ml-0.5 rounded-full p-0.5 hover:bg-olive-200 dark:hover:bg-olive-700"
                aria-label={`Remove ${group.name}`}
              >
                <X className="size-3" />
              </button>
            )}
          </Badge>
        ))}

        {appliedGroupObjects.length === 0 && !canManage && (
          <span className="text-sm text-olive-400 dark:text-olive-600">
            No field groups applied
          </span>
        )}

        {canManage && unappliedGroups.length > 0 && (
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
                Add Group
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-56 p-0" align="start">
              <Command>
                <CommandInput placeholder="Search groups..." />
                <CommandList>
                  <CommandEmpty>No groups found.</CommandEmpty>
                  {unappliedGroups.map((group) => (
                    <CommandItem
                      key={group.id}
                      value={`${group.name} ${group.slug}`}
                      onSelect={() => handleAddGroup(group.id)}
                    >
                      {group.name}
                      {group.description && (
                        <span className="ml-1 text-xs text-olive-400 dark:text-olive-600">
                          {group.description}
                        </span>
                      )}
                    </CommandItem>
                  ))}
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        )}
      </div>
      {error && (
        <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}
