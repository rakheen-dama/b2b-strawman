"use client";

import { useRef, useState, type ReactNode } from "react";
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
import { createTask } from "@/app/(app)/org/[slug]/projects/[id]/task-actions";

interface CreateTaskDialogProps {
  slug: string;
  projectId: string;
  children: ReactNode;
}

export function CreateTaskDialog({ slug, projectId, children }: CreateTaskDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createTask(slug, projectId, formData);
      if (result.success) {
        formRef.current?.reset();
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create task.");
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
          <DialogTitle>Create Task</DialogTitle>
          <DialogDescription>Add a new task to this project.</DialogDescription>
        </DialogHeader>
        <form ref={formRef} action={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="task-title">Title</Label>
            <Input
              id="task-title"
              name="title"
              placeholder="Task title"
              required
              maxLength={500}
              autoFocus
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="task-description">
              Description <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Textarea
              id="task-description"
              name="description"
              placeholder="Describe what needs to be done..."
              maxLength={5000}
              rows={3}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="task-priority">Priority</Label>
              <select
                id="task-priority"
                name="priority"
                defaultValue="MEDIUM"
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="task-type">
                Type <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <Input
                id="task-type"
                name="type"
                placeholder="e.g. Bug, Feature"
                maxLength={100}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="task-due-date">
              Due Date <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input id="task-due-date" name="dueDate" type="date" />
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
              {isSubmitting ? "Creating..." : "Create Task"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
