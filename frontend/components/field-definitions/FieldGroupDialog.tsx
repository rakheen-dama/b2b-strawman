"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Check, ChevronsUpDown, X } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  createFieldGroupAction,
  updateFieldGroupAction,
} from "@/app/(app)/org/[slug]/settings/custom-fields/actions";
import type {
  EntityType,
  FieldGroupResponse,
  FieldDefinitionResponse,
} from "@/lib/types";

const ENTITY_TYPES: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
];

interface FieldGroupDialogProps {
  slug: string;
  entityType?: EntityType;
  group?: FieldGroupResponse;
  availableFields: FieldDefinitionResponse[];
  initialFieldIds?: string[];
  children: React.ReactNode;
}

export function FieldGroupDialog({
  slug,
  entityType: initialEntityType,
  group,
  availableFields,
  initialFieldIds,
  children,
}: FieldGroupDialogProps) {
  const isEditing = !!group;
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [popoverOpen, setPopoverOpen] = useState(false);

  const [entityType, setEntityType] = useState<EntityType>(
    group?.entityType ?? initialEntityType ?? "PROJECT",
  );
  const [name, setName] = useState(group?.name ?? "");
  const [slugField, setSlugField] = useState(group?.slug ?? "");
  const [description, setDescription] = useState(group?.description ?? "");
  const [sortOrder, setSortOrder] = useState(group?.sortOrder ?? 0);
  const [selectedFieldIds, setSelectedFieldIds] = useState<string[]>(
    initialFieldIds ?? [],
  );
  const [autoApply, setAutoApply] = useState(group?.autoApply ?? false);

  // Filter available fields by entityType
  const filteredFields = availableFields.filter(
    (f) => f.entityType === entityType && f.active,
  );

  useEffect(() => {
    if (!isEditing) {
      setSelectedFieldIds([]);
    }
  }, [entityType, isEditing]);

  function resetForm() {
    setEntityType(initialEntityType ?? "PROJECT");
    setName("");
    setSlugField("");
    setDescription("");
    setSortOrder(0);
    setSelectedFieldIds([]);
    setAutoApply(false);
    setError(null);
  }

  function populateFromGroup(g: FieldGroupResponse) {
    setEntityType(g.entityType);
    setName(g.name);
    setSlugField(g.slug);
    setDescription(g.description ?? "");
    setSortOrder(g.sortOrder);
    setSelectedFieldIds(initialFieldIds ?? []);
    setAutoApply(g.autoApply ?? false);
    setError(null);
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen && isEditing && group) {
      populateFromGroup(group);
    } else if (!newOpen) {
      if (!isEditing) resetForm();
    }
    setOpen(newOpen);
  }

  function toggleField(fieldId: string) {
    setSelectedFieldIds((prev) =>
      prev.includes(fieldId)
        ? prev.filter((id) => id !== fieldId)
        : [...prev, fieldId],
    );
  }

  function removeField(fieldId: string) {
    setSelectedFieldIds((prev) => prev.filter((id) => id !== fieldId));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Name is required.");
      return;
    }

    setIsSubmitting(true);

    try {
      if (isEditing && group) {
        const result = await updateFieldGroupAction(slug, group.id, {
          name: name.trim(),
          slug: slugField.trim() || undefined,
          description: description.trim() || undefined,
          sortOrder,
          fieldDefinitionIds: selectedFieldIds,
          autoApply,
        });

        if (result.success) {
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to update field group.");
        }
      } else {
        const result = await createFieldGroupAction(slug, {
          entityType,
          name: name.trim(),
          slug: slugField.trim() || undefined,
          description: description.trim() || undefined,
          sortOrder,
          fieldDefinitionIds: selectedFieldIds,
          autoApply,
        });

        if (result.success) {
          resetForm();
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to create field group.");
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit Field Group" : "Add Field Group"}
          </DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update the field group settings."
              : "Create a new field group to organize custom fields."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Entity Type */}
          <div className="space-y-2">
            <Label htmlFor="fg-entity-type">Entity Type</Label>
            {isEditing ? (
              <Input
                id="fg-entity-type"
                value={
                  ENTITY_TYPES.find((t) => t.value === entityType)?.label ??
                  entityType
                }
                readOnly
                className="bg-slate-50 dark:bg-slate-900"
              />
            ) : (
              <select
                id="fg-entity-type"
                value={entityType}
                onChange={(e) => setEntityType(e.target.value as EntityType)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 dark:border-slate-700"
              >
                {ENTITY_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Name */}
          <div className="space-y-2">
            <Label htmlFor="fg-name">Name</Label>
            <Input
              id="fg-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Contact & Address"
              maxLength={100}
              required
            />
          </div>

          {/* Slug */}
          <div className="space-y-2">
            <Label htmlFor="fg-slug">Slug</Label>
            <Input
              id="fg-slug"
              value={slugField}
              onChange={(e) => setSlugField(e.target.value)}
              placeholder="Auto-generated from name if left blank"
              pattern="^[a-z][a-z0-9_-]*$"
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Auto-generated from name if left blank.
            </p>
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="fg-description">Description</Label>
            <Textarea
              id="fg-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional description"
              rows={2}
            />
          </div>

          {/* Sort Order */}
          <div className="space-y-2">
            <Label htmlFor="fg-sort-order">Sort Order</Label>
            <Input
              id="fg-sort-order"
              type="number"
              value={sortOrder}
              onChange={(e) => setSortOrder(parseInt(e.target.value, 10) || 0)}
              min={0}
            />
          </div>

          {/* Auto-apply */}
          <div className="flex items-start gap-3">
            <input
              id="fg-auto-apply"
              type="checkbox"
              checked={autoApply}
              onChange={(e) => setAutoApply(e.target.checked)}
              className="mt-0.5 size-4 rounded border-slate-300 accent-teal-600"
            />
            <div className="space-y-0.5">
              <Label htmlFor="fg-auto-apply" className="cursor-pointer font-medium">
                Auto-apply to new entities
              </Label>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                When enabled, this group is automatically applied when a new entity of this type is created.
              </p>
            </div>
          </div>

          {/* Field Selection */}
          <div className="space-y-2">
            <Label>Fields</Label>
            <Popover open={popoverOpen} onOpenChange={setPopoverOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  role="combobox"
                  aria-expanded={popoverOpen}
                  className="w-full justify-between font-normal"
                >
                  {selectedFieldIds.length > 0
                    ? `${selectedFieldIds.length} field${selectedFieldIds.length > 1 ? "s" : ""} selected`
                    : "Select fields..."}
                  <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-full p-0" align="start">
                <Command>
                  <CommandInput placeholder="Search fields..." />
                  <CommandList>
                    <CommandEmpty>No fields found.</CommandEmpty>
                    <CommandGroup>
                      {filteredFields.map((field) => (
                        <CommandItem
                          key={field.id}
                          value={`${field.name} ${field.fieldType}`}
                          onSelect={() => toggleField(field.id)}
                        >
                          <Check
                            className={cn(
                              "mr-2 size-4",
                              selectedFieldIds.includes(field.id)
                                ? "opacity-100"
                                : "opacity-0",
                            )}
                          />
                          <span className="flex-1">{field.name}</span>
                          <Badge variant="neutral" className="ml-2 text-xs">
                            {field.fieldType}
                          </Badge>
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>

            {/* Selected fields as badges */}
            {selectedFieldIds.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {selectedFieldIds.map((fieldId) => {
                  const field = availableFields.find(
                    (f) => f.id === fieldId,
                  );
                  if (!field) return null;
                  return (
                    <Badge
                      key={fieldId}
                      variant="secondary"
                      className="gap-1"
                    >
                      {field.name}
                      <button
                        type="button"
                        onClick={() => removeField(fieldId)}
                        className="ml-0.5 rounded-full hover:bg-slate-300 dark:hover:bg-slate-600"
                      >
                        <X className="size-3" />
                      </button>
                    </Badge>
                  );
                })}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? isEditing
                  ? "Saving..."
                  : "Creating..."
                : isEditing
                  ? "Save Changes"
                  : "Create Group"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
