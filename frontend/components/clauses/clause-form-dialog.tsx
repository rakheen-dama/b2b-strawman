"use client";

import { useState, type FormEvent } from "react";
import { Check, ChevronsUpDown, Eye } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
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
import { ClausePreviewPanel } from "@/components/clauses/clause-preview-panel";
import { createClause, updateClause } from "@/lib/actions/clause-actions";
import type { Clause } from "@/lib/actions/clause-actions";
import { cn } from "@/lib/utils";

interface ClauseFormDialogProps {
  slug: string;
  clause?: Clause;
  categories: string[];
  children: React.ReactNode;
}

export function ClauseFormDialog({
  slug,
  clause,
  categories,
  children,
}: ClauseFormDialogProps) {
  const isEditing = !!clause;

  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("");
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [description, setDescription] = useState("");
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPreview, setShowPreview] = useState(false);

  function extractTextFromBody(body: Record<string, unknown>): string {
    const content = body?.content as Array<Record<string, unknown>> | undefined;
    if (!content || !Array.isArray(content)) return "";
    return content
      .map((node) => {
        const children = node.content as
          | Array<Record<string, unknown>>
          | undefined;
        if (!children) return "";
        return children
          .map((child) => (child.text as string) ?? "")
          .join("");
      })
      .join("\n");
  }

  function wrapTextAsBody(text: string): Record<string, unknown> {
    return {
      type: "doc",
      content: text
        .split("\n")
        .filter((line) => line.length > 0)
        .map((line) => ({
          type: "paragraph",
          content: [{ type: "text", text: line }],
        })),
    };
  }

  function resetForm() {
    if (isEditing && clause) {
      setTitle(clause.title);
      setCategory(clause.category);
      setDescription(clause.description ?? "");
      setBody(extractTextFromBody(clause.body));
    } else {
      setTitle("");
      setCategory("");
      setDescription("");
      setBody("");
    }
    setError(null);
    setShowPreview(false);
  }

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (newOpen) {
      resetForm();
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!title.trim() || !body.trim()) {
      setError("Title and body are required.");
      return;
    }

    if (!category.trim()) {
      setError("Category is required.");
      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      const data = {
        title: title.trim(),
        description: description.trim() || undefined,
        body: wrapTextAsBody(body.trim()),
        category: category.trim(),
      };

      const result = isEditing
        ? await updateClause(slug, clause.id, data)
        : await createClause(slug, data);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "An unexpected error occurred.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const canSubmit = title.trim().length > 0 && body.trim().length > 0 && category.trim().length > 0;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Clause" : "New Clause"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update the clause details."
              : "Create a new reusable clause."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Title */}
          <div className="space-y-2">
            <Label htmlFor="clause-title">
              Title <span className="text-red-500">*</span>
            </Label>
            <Input
              id="clause-title"
              placeholder="e.g. Standard NDA Clause"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={200}
              required
            />
          </div>

          {/* Category Combobox */}
          <div className="space-y-2">
            <Label htmlFor="clause-category">
              Category <span className="text-red-500">*</span>
            </Label>
            <Popover open={categoryOpen} onOpenChange={setCategoryOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="plain"
                  role="combobox"
                  aria-expanded={categoryOpen}
                  id="clause-category"
                  className="w-full justify-between border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
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
            <Label htmlFor="clause-description">Description</Label>
            <Textarea
              id="clause-description"
              placeholder="Optional description of this clause"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={500}
              className="min-h-16"
            />
          </div>

          {/* Body */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="clause-body">
                Body <span className="text-red-500">*</span>
              </Label>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setShowPreview(!showPreview)}
                disabled={!body.trim()}
              >
                <Eye className="mr-1 size-4" />
                {showPreview ? "Hide Preview" : "Preview"}
              </Button>
            </div>
            <Textarea
              id="clause-body"
              placeholder="HTML or Thymeleaf content..."
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className="min-h-32 font-mono text-sm"
              required
            />
          </div>

          {/* Client-side Preview */}
          {showPreview && (
            <ClausePreviewPanel html={body} isLoading={false} />
          )}

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
            <Button type="submit" disabled={isSubmitting || !canSubmit}>
              {isSubmitting
                ? isEditing
                  ? "Saving..."
                  : "Creating..."
                : isEditing
                  ? "Save Changes"
                  : "Create Clause"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
