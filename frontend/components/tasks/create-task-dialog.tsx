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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { createTask } from "@/app/(app)/org/[slug]/projects/[id]/task-actions";
import { AssigneeSelector } from "@/components/tasks/assignee-selector";
import { formatRecurrenceRule } from "@/lib/recurrence";
import type { RecurrenceFrequency } from "@/lib/recurrence";

interface CreateTaskDialogProps {
  slug: string;
  projectId: string;
  children: ReactNode;
  members?: { id: string; name: string; email: string }[];
  canManage?: boolean;
}

export function CreateTaskDialog({
  slug,
  projectId,
  children,
  members = [],
  canManage = false,
}: CreateTaskDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedAssigneeId, setSelectedAssigneeId] = useState<string | null>(null);
  const [priority, setPriority] = useState("MEDIUM");
  const [recurrenceFrequency, setRecurrenceFrequency] = useState<RecurrenceFrequency | "NONE">("NONE");
  const [recurrenceInterval, setRecurrenceInterval] = useState(1);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);
    setIsSubmitting(true);

    // Add controlled fields to FormData
    formData.set("priority", priority);
    if (recurrenceFrequency !== "NONE") {
      const rule = formatRecurrenceRule(recurrenceFrequency, recurrenceInterval);
      if (rule) formData.set("recurrenceRule", rule);
    }

    try {
      const result = await createTask(slug, projectId, formData, selectedAssigneeId);
      if (result.success) {
        formRef.current?.reset();
        setSelectedAssigneeId(null);
        setPriority("MEDIUM");
        setRecurrenceFrequency("NONE");
        setRecurrenceInterval(1);
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
      setSelectedAssigneeId(null);
      setPriority("MEDIUM");
      setRecurrenceFrequency("NONE");
      setRecurrenceInterval(1);
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
              <Label>Priority</Label>
              <Select value={priority} onValueChange={setPriority}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LOW">Low</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HIGH">High</SelectItem>
                </SelectContent>
              </Select>
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
          {/* Recurrence section */}
          <div className="space-y-2">
            <Label htmlFor="task-recurrence-frequency">
              Recurrence <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Select value={recurrenceFrequency} onValueChange={(v) => setRecurrenceFrequency(v as RecurrenceFrequency | "NONE")}>
              <SelectTrigger>
                <SelectValue placeholder="None" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NONE">None</SelectItem>
                <SelectItem value="DAILY">Daily</SelectItem>
                <SelectItem value="WEEKLY">Weekly</SelectItem>
                <SelectItem value="MONTHLY">Monthly</SelectItem>
                <SelectItem value="YEARLY">Yearly</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {recurrenceFrequency !== "NONE" && (
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="task-recurrence-interval">Interval</Label>
                <Input
                  id="task-recurrence-interval"
                  type="number"
                  min={1}
                  value={recurrenceInterval}
                  onChange={(e) => setRecurrenceInterval(Math.max(1, parseInt(e.target.value, 10) || 1))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="task-recurrence-end-date">
                  End Date <span className="font-normal text-muted-foreground">(optional)</span>
                </Label>
                <Input id="task-recurrence-end-date" name="recurrenceEndDate" type="date" />
              </div>
            </div>
          )}
          {canManage && (
            <div className="space-y-2">
              <Label>
                Assign to <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <AssigneeSelector
                members={members}
                currentAssigneeId={selectedAssigneeId}
                onAssigneeChange={setSelectedAssigneeId}
                disabled={isSubmitting}
              />
            </div>
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
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Creating..." : "Create Task"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
