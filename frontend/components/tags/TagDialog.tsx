"use client";

import { useState } from "react";
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
import {
  createTagAction,
  updateTagAction,
} from "@/app/(app)/org/[slug]/settings/tags/actions";
import type { TagResponse } from "@/lib/types";

interface TagDialogProps {
  slug: string;
  tag?: TagResponse;
  children: React.ReactNode;
}

export function TagDialog({ slug, tag, children }: TagDialogProps) {
  const isEditing = !!tag;
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState(tag?.name ?? "");
  const [color, setColor] = useState(tag?.color ?? "");

  function resetForm() {
    setName(tag?.name ?? "");
    setColor(tag?.color ?? "");
    setError(null);
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (nextOpen) {
      resetForm();
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("Name is required.");
      return;
    }
    if (trimmedName.length > 50) {
      setError("Name must be 50 characters or less.");
      return;
    }

    const trimmedColor = color.trim() || null;
    if (trimmedColor && !/^#[0-9A-Fa-f]{6}$/.test(trimmedColor)) {
      setError("Color must be a valid hex color (e.g. #FF5733).");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const result = isEditing
        ? await updateTagAction(slug, tag.id, {
            name: trimmedName,
            color: trimmedColor,
          })
        : await createTagAction(slug, {
            name: trimmedName,
            color: trimmedColor,
          });

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "An error occurred.");
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
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{isEditing ? "Edit Tag" : "Create Tag"}</DialogTitle>
            <DialogDescription>
              {isEditing
                ? "Update the tag name and color."
                : "Create a new tag for your organization."}
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="tag-name">Name</Label>
              <Input
                id="tag-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Urgent, VIP, Internal"
                maxLength={50}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="tag-color">Color (optional)</Label>
              <div className="flex items-center gap-2">
                <Input
                  id="tag-color"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  placeholder="#FF5733"
                  maxLength={7}
                />
                {color && /^#[0-9A-Fa-f]{6}$/.test(color.trim()) && (
                  <div
                    className="size-8 shrink-0 rounded border border-olive-200 dark:border-olive-700"
                    style={{ backgroundColor: color.trim() }}
                  />
                )}
              </div>
              <p className="text-xs text-olive-500 dark:text-olive-400">
                Hex format, e.g. #FF5733
              </p>
            </div>

            {error && (
              <p className="text-sm text-destructive">{error}</p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="outline"
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
                  ? "Save"
                  : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
