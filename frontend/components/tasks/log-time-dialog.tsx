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
import { createTimeEntry } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";

interface LogTimeDialogProps {
  slug: string;
  projectId: string;
  taskId: string;
  children: ReactNode;
}

export function LogTimeDialog({ slug, projectId, taskId, children }: LogTimeDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);

    // Inline validation: hours + minutes must sum > 0
    const hours = parseInt(formData.get("hours")?.toString() ?? "0", 10) || 0;
    const minutes = parseInt(formData.get("minutes")?.toString() ?? "0", 10) || 0;
    if (hours * 60 + minutes <= 0) {
      setError("Duration must be greater than 0.");
      return;
    }

    setIsSubmitting(true);

    try {
      const result = await createTimeEntry(slug, projectId, taskId, formData);
      if (result.success) {
        formRef.current?.reset();
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to log time.");
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
    } else {
      // Reset form on close
      formRef.current?.reset();
      setError(null);
    }
    setOpen(newOpen);
  }

  // Default date to today in YYYY-MM-DD format
  const today = new Date().toISOString().split("T")[0];

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Log Time</DialogTitle>
          <DialogDescription>Record time spent on this task.</DialogDescription>
        </DialogHeader>
        <form ref={formRef} action={handleSubmit} className="space-y-4">
          {/* Duration: hours + minutes */}
          <div className="space-y-2">
            <Label>Duration</Label>
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1.5">
                <Input
                  id="time-hours"
                  name="hours"
                  type="number"
                  min={0}
                  max={23}
                  defaultValue={0}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-sm text-olive-600 dark:text-olive-400">h</span>
              </div>
              <div className="flex items-center gap-1.5">
                <Input
                  id="time-minutes"
                  name="minutes"
                  type="number"
                  min={0}
                  max={59}
                  defaultValue={0}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-sm text-olive-600 dark:text-olive-400">m</span>
              </div>
            </div>
          </div>

          {/* Date */}
          <div className="space-y-2">
            <Label htmlFor="time-date">Date</Label>
            <Input id="time-date" name="date" type="date" defaultValue={today} required />
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="time-description">
              Description <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Textarea
              id="time-description"
              name="description"
              placeholder="What did you work on?"
              maxLength={2000}
              rows={2}
            />
          </div>

          {/* Billable */}
          <div className="flex items-center gap-2">
            <input
              id="time-billable"
              name="billable"
              type="checkbox"
              defaultChecked
              className="size-4 rounded border-olive-300 text-indigo-600 focus:ring-indigo-500 dark:border-olive-700"
            />
            <Label htmlFor="time-billable" className="font-normal">
              Billable
            </Label>
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
              {isSubmitting ? "Logging..." : "Log Time"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
