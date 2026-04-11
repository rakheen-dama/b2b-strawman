"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { createRoleAction, updateRoleAction } from "@/app/(app)/org/[slug]/settings/roles/actions";
import { CAPABILITY_META } from "@/lib/capabilities";
import type { OrgRole } from "@/lib/api/org-roles";

interface RoleDialogProps {
  slug: string;
  role?: OrgRole;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function RoleDialog({ slug, role, open, onOpenChange }: RoleDialogProps) {
  const isEditing = !!role;
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [capabilities, setCapabilities] = useState<Set<string>>(new Set());

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      setName(role?.name ?? "");
      setDescription(role?.description ?? "");
      setCapabilities(new Set(role?.capabilities ?? []));
      setError(null);
    }
  }, [open, role]);

  function toggleCapability(cap: string) {
    setCapabilities((prev) => {
      const next = new Set(prev);
      if (next.has(cap)) {
        next.delete(cap);
      } else {
        next.add(cap);
      }
      return next;
    });
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

    setIsSubmitting(true);
    setError(null);

    try {
      const data = {
        name: trimmedName,
        description: description.trim() || undefined,
        capabilities: Array.from(capabilities),
      };

      const result = isEditing
        ? await updateRoleAction(slug, role.id, data)
        : await createRoleAction(slug, data);

      if (result.success) {
        onOpenChange(false);
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{isEditing ? "Edit Role" : "Create Role"}</DialogTitle>
            <DialogDescription>
              {isEditing
                ? "Update the role name, description, and capabilities."
                : "Create a new role with specific capabilities for your team."}
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="role-name">Name</Label>
              <Input
                id="role-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Bookkeeper, Project Lead"
                maxLength={50}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="role-description">Description (optional)</Label>
              <Textarea
                id="role-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe what this role is for..."
                rows={2}
              />
            </div>

            <div className="space-y-3">
              <Label>Capabilities</Label>
              <div className="space-y-3">
                {CAPABILITY_META.map((cap) => (
                  <label key={cap.value} className="flex cursor-pointer items-start gap-3">
                    <Checkbox
                      checked={capabilities.has(cap.value)}
                      onCheckedChange={() => toggleCapability(cap.value)}
                      aria-label={cap.label}
                    />
                    <div className="space-y-0.5">
                      <span className="text-sm leading-none font-medium">{cap.label}</span>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {cap.description}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {error && <p className="text-destructive text-sm">{error}</p>}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
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
