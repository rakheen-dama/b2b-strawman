"use client";

import { useState, useEffect } from "react";
import { Check, ChevronsUpDown, X, AlertTriangle, Copy } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetClose,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { DocumentEditor } from "@/components/editor/DocumentEditor";
import {
  createClause,
  updateClause,
  cloneClause,
} from "@/lib/actions/clause-actions";
import type { Clause } from "@/lib/actions/clause-actions";
import { cn } from "@/lib/utils";

function generateSlug(title: string): string {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 100);
}

interface ClauseEditorSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  clause: Clause | null;
  categories: string[];
  onSuccess: (message: string) => void;
  onError: (message: string) => void;
}

export function ClauseEditorSheet({
  open,
  onOpenChange,
  slug,
  clause,
  categories,
  onSuccess,
  onError,
}: ClauseEditorSheetProps) {
  const isEditing = clause !== null;
  const isSystem = clause?.source === "SYSTEM";

  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("");
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [description, setDescription] = useState("");
  const [editorBody, setEditorBody] = useState<Record<string, unknown>>({
    type: "doc",
    content: [],
  });
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Reset form when sheet opens or clause changes
  useEffect(() => {
    if (open) {
      if (clause) {
        setTitle(clause.title);
        setCategory(clause.category);
        setDescription(clause.description ?? "");
        setEditorBody(clause.body);
      } else {
        setTitle("");
        setCategory("");
        setDescription("");
        setEditorBody({ type: "doc", content: [] });
      }
      setError(null);
    }
  }, [open, clause]);

  async function handleSave() {
    if (!title.trim()) {
      setError("Title is required.");
      return;
    }
    if (!category.trim()) {
      setError("Category is required.");
      return;
    }
    const bodyContent = (editorBody as { content?: unknown[] })?.content;
    if (!bodyContent || bodyContent.length === 0) {
      setError("Body content is required.");
      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      const data = {
        title: title.trim(),
        description: description.trim() || undefined,
        body: editorBody,
        category: category.trim(),
      };

      const result = isEditing
        ? await updateClause(slug, clause.id, data)
        : await createClause(slug, data);

      if (result.success) {
        onOpenChange(false);
        onSuccess(
          isEditing
            ? `Clause "${title.trim()}" updated successfully.`
            : `Clause "${title.trim()}" created successfully.`,
        );
      } else {
        setError(result.error ?? "An unexpected error occurred.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleClone() {
    if (!clause) return;
    setIsSubmitting(true);

    try {
      const result = await cloneClause(slug, clause.id);
      if (result.success) {
        onOpenChange(false);
        onSuccess(`Clause "${clause.title}" cloned successfully.`);
      } else {
        setError(result.error ?? "Failed to clone clause.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const sheetTitle = isSystem
    ? "System Clause"
    : isEditing
      ? "Edit Clause"
      : "New Clause";

  const bodyContent = (editorBody as { content?: unknown[] })?.content;
  const hasBody = Array.isArray(bodyContent) && bodyContent.length > 0;
  const canSubmit =
    title.trim().length > 0 &&
    category.trim().length > 0 &&
    hasBody &&
    !isSystem;

  return (
    <Sheet
      open={open}
      onOpenChange={(o) => {
        if (!o) onOpenChange(false);
      }}
    >
      <SheetContent
        side="right"
        className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-xl"
        showCloseButton={false}
        onPointerDownOutside={(e) => {
          const target = e.target as HTMLElement | null;
          if (!target?.closest("[data-slot='sheet-overlay']")) {
            e.preventDefault();
          }
        }}
      >
        <SheetTitle className="sr-only">{sheetTitle}</SheetTitle>
        <SheetDescription className="sr-only">
          {isSystem
            ? "View system clause details"
            : isEditing
              ? "Edit clause details"
              : "Create a new clause"}
        </SheetDescription>

        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 dark:border-slate-800">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            {sheetTitle}
          </h2>
          <SheetClose asChild>
            <Button
              variant="ghost"
              size="icon"
              className="shrink-0"
              aria-label="Close"
            >
              <X className="size-4" />
            </Button>
          </SheetClose>
        </div>

        {/* System clause banner */}
        {isSystem && (
          <div className="mx-6 mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-950">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-400" />
              <div className="flex-1">
                <p className="text-sm text-amber-700 dark:text-amber-300">
                  This is a system clause. Clone to customize.
                </p>
              </div>
              <Button
                size="sm"
                variant="plain"
                onClick={handleClone}
                disabled={isSubmitting}
                className="shrink-0"
              >
                <Copy className="mr-1 size-4" />
                {isSubmitting ? "Cloning..." : "Clone"}
              </Button>
            </div>
          </div>
        )}

        {/* Edit warning banner */}
        {isEditing && !isSystem && (
          <div className="mx-6 mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-950">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-400" />
              <p className="text-sm text-amber-700 dark:text-amber-300">
                Editing this clause will affect all templates that use it.
              </p>
            </div>
          </div>
        )}

        {/* Form fields */}
        <div className="flex-1 space-y-4 px-6 py-4">
          {/* Title */}
          <div className="space-y-2">
            <Label htmlFor="clause-editor-title">
              Title <span className="text-red-500">*</span>
            </Label>
            <Input
              id="clause-editor-title"
              placeholder="e.g. Standard NDA Clause"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={200}
              disabled={isSystem}
            />
          </div>

          {/* Slug (read-only, auto-generated) */}
          <div className="space-y-2">
            <Label htmlFor="clause-editor-slug">Slug</Label>
            <Input
              id="clause-editor-slug"
              value={isEditing && clause ? clause.slug : generateSlug(title)}
              readOnly
              disabled
              className="bg-slate-50 dark:bg-slate-900"
            />
          </div>

          {/* Category Combobox */}
          <div className="space-y-2">
            <Label htmlFor="clause-editor-category">
              Category <span className="text-red-500">*</span>
            </Label>
            <Popover open={categoryOpen} onOpenChange={setCategoryOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="plain"
                  role="combobox"
                  aria-expanded={categoryOpen}
                  id="clause-editor-category"
                  className="w-full justify-between border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
                  disabled={isSystem}
                >
                  {category || "Select or type a category"}
                  <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[300px] p-0" align="start">
                <Command>
                  <CommandInput
                    placeholder="Search or type new category..."
                    value={category}
                    onValueChange={setCategory}
                    maxLength={100}
                  />
                  <CommandList>
                    <CommandEmpty>
                      <button
                        type="button"
                        className="w-full px-2 py-1.5 text-left text-sm"
                        onClick={() => {
                          setCategoryOpen(false);
                        }}
                      >
                        Use &quot;{category}&quot;
                      </button>
                    </CommandEmpty>
                    <CommandGroup>
                      {categories.map((cat) => (
                        <CommandItem
                          key={cat}
                          value={cat}
                          onSelect={() => {
                            setCategory(cat);
                            setCategoryOpen(false);
                          }}
                        >
                          <Check
                            className={cn(
                              "mr-2 size-4",
                              category === cat ? "opacity-100" : "opacity-0",
                            )}
                          />
                          {cat}
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="clause-editor-description">Description</Label>
            <Textarea
              id="clause-editor-description"
              placeholder="Optional description of this clause"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={500}
              className="min-h-16"
              disabled={isSystem}
            />
          </div>

          {/* DocumentEditor */}
          <div className="space-y-2">
            <Label>
              Body {!isSystem && <span className="text-red-500">*</span>}
            </Label>
            <DocumentEditor
              content={clause?.body ?? null}
              onUpdate={(json) => setEditorBody(json)}
              scope="clause"
              editable={!isSystem}
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        {/* Footer */}
        {!isSystem && (
          <div className="border-t border-slate-200 px-6 py-4 dark:border-slate-800">
            <div className="flex items-center justify-end gap-3">
              <SheetClose asChild>
                <Button variant="plain" disabled={isSubmitting}>
                  Cancel
                </Button>
              </SheetClose>
              <Button
                onClick={handleSave}
                disabled={isSubmitting || !canSubmit}
              >
                {isSubmitting
                  ? isEditing
                    ? "Saving..."
                    : "Creating..."
                  : isEditing
                    ? "Save Changes"
                    : "Create Clause"}
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
