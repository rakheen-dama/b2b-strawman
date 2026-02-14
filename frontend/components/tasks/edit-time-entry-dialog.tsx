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
import { Textarea } from "@/components/ui/textarea";
import { updateTimeEntry } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
import { formatCurrencySafe } from "@/lib/format";
import type { TimeEntry } from "@/lib/types";

interface EditTimeEntryDialogProps {
  entry: TimeEntry;
  slug: string;
  projectId: string;
  children: React.ReactNode;
}

export function EditTimeEntryDialog({
  entry,
  slug,
  projectId,
  children,
}: EditTimeEntryDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Pre-populate hours and minutes from durationMinutes
  const initialHours = Math.floor(entry.durationMinutes / 60);
  const initialMinutes = entry.durationMinutes % 60;

  async function handleSubmit(formData: FormData) {
    setError(null);

    // Inline validation: hours + minutes must sum > 0
    const hours = parseInt(formData.get("hours")?.toString() ?? "0", 10) || 0;
    const minutes =
      parseInt(formData.get("minutes")?.toString() ?? "0", 10) || 0;
    const durationMinutes = hours * 60 + minutes;
    if (durationMinutes <= 0) {
      setError("Duration must be greater than 0.");
      return;
    }

    const date = formData.get("date")?.toString().trim() ?? "";
    if (!date) {
      setError("Date is required.");
      return;
    }

    const billableStr = formData.get("billable")?.toString();
    const billable = billableStr === "on" || billableStr === "true";
    const description =
      formData.get("description")?.toString().trim() || undefined;

    setIsSubmitting(true);

    try {
      const result = await updateTimeEntry(slug, projectId, entry.id, {
        date,
        durationMinutes,
        billable,
        description,
      });
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update time entry.");
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

  const hasBillingRate =
    entry.billingRateSnapshot != null && entry.billingRateCurrency != null;
  const hasCostRate =
    entry.costRateSnapshot != null && entry.costRateCurrency != null;
  const hasRateSnapshot = hasBillingRate || hasCostRate;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Time Entry</DialogTitle>
          <DialogDescription>Update this time entry.</DialogDescription>
        </DialogHeader>
        <form action={handleSubmit} className="space-y-4">
          {/* Duration: hours + minutes */}
          <div className="space-y-2">
            <Label>Duration</Label>
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1.5">
                <Input
                  id="edit-time-hours"
                  name="hours"
                  type="number"
                  min={0}
                  max={23}
                  defaultValue={initialHours}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-sm text-olive-600 dark:text-olive-400">
                  h
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <Input
                  id="edit-time-minutes"
                  name="minutes"
                  type="number"
                  min={0}
                  max={59}
                  defaultValue={initialMinutes}
                  className="w-20"
                  placeholder="0"
                />
                <span className="text-sm text-olive-600 dark:text-olive-400">
                  m
                </span>
              </div>
            </div>
          </div>

          {/* Date */}
          <div className="space-y-2">
            <Label htmlFor="edit-time-date">Date</Label>
            <Input
              id="edit-time-date"
              name="date"
              type="date"
              defaultValue={entry.date}
              required
            />
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="edit-time-description">
              Description{" "}
              <span className="font-normal text-muted-foreground">
                (optional)
              </span>
            </Label>
            <Textarea
              id="edit-time-description"
              name="description"
              defaultValue={entry.description ?? ""}
              placeholder="What did you work on?"
              maxLength={2000}
              rows={2}
            />
          </div>

          {/* Billable */}
          <div className="flex items-center gap-2">
            <input
              id="edit-time-billable"
              name="billable"
              type="checkbox"
              defaultChecked={entry.billable}
              className="size-4 rounded border-olive-300 text-indigo-600 focus:ring-indigo-500 dark:border-olive-700"
            />
            <Label htmlFor="edit-time-billable" className="font-normal">
              Billable
            </Label>
          </div>

          {/* Rate snapshot (read-only) */}
          {hasRateSnapshot && (
            <div
              className="rounded-md border border-olive-200 bg-olive-50 px-3 py-2 dark:border-olive-800 dark:bg-olive-900/50"
              data-testid="rate-snapshot"
            >
              {hasBillingRate && (
                <p className="text-sm text-olive-600 dark:text-olive-400">
                  Billing rate:{" "}
                  <span className="font-medium text-olive-700 dark:text-olive-300">
                    {formatCurrencySafe(
                      entry.billingRateSnapshot,
                      entry.billingRateCurrency,
                    )}
                    /hr
                  </span>
                  {entry.billableValue != null && (
                    <span className="ml-2">
                      Value:{" "}
                      <span className="font-medium">
                        {formatCurrencySafe(
                          entry.billableValue,
                          entry.billingRateCurrency,
                        )}
                      </span>
                    </span>
                  )}
                </p>
              )}
              {hasCostRate && (
                <p className="text-sm text-olive-600 dark:text-olive-400">
                  Cost rate:{" "}
                  <span className="font-medium text-olive-700 dark:text-olive-300">
                    {formatCurrencySafe(
                      entry.costRateSnapshot,
                      entry.costRateCurrency,
                    )}
                    /hr
                  </span>
                  {entry.costValue != null && (
                    <span className="ml-2">
                      Cost:{" "}
                      <span className="font-medium">
                        {formatCurrencySafe(
                          entry.costValue,
                          entry.costRateCurrency,
                        )}
                      </span>
                    </span>
                  )}
                </p>
              )}
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
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
