"use client";

import { useEffect, useState } from "react";
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
import { updateProject, fetchActiveCustomers } from "@/app/(app)/org/[slug]/projects/actions";
import type { Project, Customer } from "@/lib/types";

interface EditProjectDialogProps {
  project: Project;
  slug: string;
  children: React.ReactNode;
}

export function EditProjectDialog({ project, slug, children }: EditProjectDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);

  useEffect(() => {
    if (open) {
      fetchActiveCustomers()
        .then((all) => setCustomers(all.filter((c) => c.status === "ACTIVE")))
        .catch(() => setCustomers([]));
    }
  }, [open]);

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateProject(slug, project.id, formData);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update project.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Project</DialogTitle>
          <DialogDescription>Update your project&apos;s details.</DialogDescription>
        </DialogHeader>
        <form action={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-project-name">Name</Label>
            <Input
              id="edit-project-name"
              name="name"
              defaultValue={project.name}
              required
              maxLength={255}
              autoFocus
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-project-description">
              Description <span className="text-muted-foreground font-normal">(optional)</span>
            </Label>
            <Textarea
              id="edit-project-description"
              name="description"
              defaultValue={project.description ?? ""}
              maxLength={2000}
              rows={3}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-project-due-date">
              Due Date <span className="text-muted-foreground font-normal">(optional)</span>
            </Label>
            <Input
              id="edit-project-due-date"
              name="dueDate"
              type="date"
              defaultValue={project.dueDate ?? ""}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-project-customer">
              Customer <span className="text-muted-foreground font-normal">(optional)</span>
            </Label>
            <select
              id="edit-project-customer"
              name="customerId"
              defaultValue={project.customerId ?? ""}
              className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
            >
              <option value="">-- None --</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          {error && <p className="text-destructive text-sm">{error}</p>}
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
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
