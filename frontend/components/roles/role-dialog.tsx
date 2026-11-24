"use client";

import { useEffect, useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  createRoleAction,
  updateRoleAction,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/roles-actions";
import { CAPABILITY_META, OWNER_ONLY_CAPABILITIES, useCanGrantOwnerOnly } from "@/lib/capabilities";
import { OwnerOnlyHint } from "@/components/roles/owner-only-hint";
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
  // FR-4: a non-owner cannot grant an owner-only capability, but may take one away (the backend
  // refuses only additions) — so its box is disabled while unchecked, enabled while checked
  const canGrantOwnerOnly = useCanGrantOwnerOnly();
  const isOwnerOnlyForCaller = (cap: string) =>
    !canGrantOwnerOnly && OWNER_ONLY_CAPABILITIES.has(cap);
  const isLocked = (cap: string) => isOwnerOnlyForCaller(cap) && !capabilities.has(cap);

  // Reset form when dialog opens
  useEffect(() => {
    if (open) {
      /* eslint-disable react-hooks/set-state-in-effect -- One-shot reset of form fields when dialog opens with a target role; no render loop. */
      setName(role?.name ?? "");
      setDescription(role?.description ?? "");
      setCapabilities(new Set(role?.capabilities ?? []));
      setError(null);
      /* eslint-enable react-hooks/set-state-in-effect */
    }
  }, [open, role]);

  function toggleCapability(cap: string) {
    if (isLocked(cap)) return;
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
                placeholder="e.g. Bookkeeper, Matter Lead"
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
                {CAPABILITY_META.map((cap) => {
                  const ownerOnly = isOwnerOnlyForCaller(cap.value);
                  const locked = isLocked(cap.value);
                  return (
                    <label
                      key={cap.value}
                      className={`flex items-start gap-3 ${locked ? "cursor-not-allowed" : "cursor-pointer"}`}
                    >
                      <Checkbox
                        checked={capabilities.has(cap.value)}
                        onCheckedChange={() => toggleCapability(cap.value)}
                        disabled={locked}
                        aria-label={cap.label}
                        aria-describedby={
                          ownerOnly ? `role-dialog-owner-only-hint-${cap.value}` : undefined
                        }
                        data-testid={`role-capability-${cap.value}`}
                      />
                      <div className="space-y-0.5">
                        <span className="text-sm leading-none font-medium">{cap.label}</span>
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {cap.description}
                        </p>
                        {ownerOnly && (
                          <OwnerOnlyHint
                            id={`role-dialog-owner-only-hint-${cap.value}`}
                            testId={`owner-only-hint-${cap.value}`}
                          />
                        )}
                      </div>
                    </label>
                  );
                })}
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
