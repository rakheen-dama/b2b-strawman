"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Clock } from "lucide-react";

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
import { Switch } from "@/components/ui/switch";

interface LogTimeDialogProps {
  slug: string;
  projectId: string;
  taskId: string;
  taskTitle: string;
  onLogTime: (
    slug: string,
    projectId: string,
    taskId: string,
    formData: FormData
  ) => Promise<{ success: boolean; error?: string }>;
  children?: React.ReactNode;
}

export function LogTimeDialog({
  slug,
  projectId,
  taskId,
  taskTitle,
  onLogTime,
  children,
}: LogTimeDialogProps) {
  const [open, setOpen] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [isPending, setIsPending] = React.useState(false);
  const [billable, setBillable] = React.useState(true);
  const router = useRouter();

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setIsPending(true);

    const formData = new FormData(e.currentTarget);
    formData.set("billable", billable ? "true" : "false");
    const result = await onLogTime(slug, projectId, taskId, formData);

    setIsPending(false);

    if (result.success) {
      setOpen(false);
      router.refresh();
    } else {
      setError(result.error ?? "An error occurred.");
    }
  }

  const today = new Date().toISOString().split("T")[0];

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {children ?? (
          <Button size="sm" variant="outline">
            <Clock className="mr-1.5 size-4" />
            Log Time
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Log Time</DialogTitle>
          <DialogDescription>
            Record time spent on &ldquo;{taskTitle}&rdquo;
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="time-date">Date</Label>
            <Input
              id="time-date"
              name="date"
              type="date"
              defaultValue={today}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="time-hours">Hours</Label>
              <Input
                id="time-hours"
                name="hours"
                type="number"
                min="0"
                defaultValue="0"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="time-minutes">Minutes</Label>
              <Input
                id="time-minutes"
                name="minutes"
                type="number"
                min="0"
                max="59"
                defaultValue="0"
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="time-description">Description</Label>
            <Textarea
              id="time-description"
              name="description"
              placeholder="What did you work on? (optional)"
              rows={2}
            />
          </div>
          <div className="flex items-center gap-3">
            <Switch
              id="time-billable"
              checked={billable}
              onCheckedChange={setBillable}
            />
            <Label htmlFor="time-billable">Billable</Label>
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? "Logging..." : "Log Time"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
