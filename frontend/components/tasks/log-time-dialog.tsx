"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
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
import {
  createTimeEntry,
  resolveRate,
} from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
import { formatCurrency, formatDuration } from "@/lib/format";
import type { ResolvedRate } from "@/lib/types";
import type { RetainerSummaryResponse } from "@/lib/types";
import { RetainerIndicator } from "@/components/time-entries/retainer-indicator";

/** Maps backend source enum to human-readable label */
function formatRateSource(source: string): string {
  switch (source) {
    case "MEMBER_DEFAULT":
      return "member default";
    case "PROJECT_OVERRIDE":
      return "project override";
    case "CUSTOMER_OVERRIDE":
      return "customer override";
    default:
      return source.toLowerCase().replace(/_/g, " ");
  }
}

interface LogTimeDialogProps {
  slug: string;
  projectId: string;
  taskId: string;
  memberId?: string | null;
  retainerSummary?: RetainerSummaryResponse | null;
  children: ReactNode;
}

export function LogTimeDialog({
  slug,
  projectId,
  taskId,
  memberId,
  retainerSummary,
  children,
}: LogTimeDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  // Rate preview state
  const [billable, setBillable] = useState(true);
  const [hours, setHours] = useState(0);
  const [minutes, setMinutes] = useState(0);
  const [date, setDate] = useState(() => new Date().toLocaleDateString("en-CA"));
  const [resolvedRate, setResolvedRate] = useState<ResolvedRate | null>(null);
  const [rateLoading, setRateLoading] = useState(false);
  const [rateChecked, setRateChecked] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Resolve rate with debounce
  const doResolveRate = useCallback(
    (currentDate: string) => {
      if (!memberId || !billable) {
        setResolvedRate(null);
        setRateChecked(false);
        return;
      }

      if (debounceRef.current) clearTimeout(debounceRef.current);

      debounceRef.current = setTimeout(async () => {
        setRateLoading(true);
        try {
          const rate = await resolveRate(memberId, projectId, currentDate);
          setResolvedRate(rate);
          setRateChecked(true);
        } catch {
          setResolvedRate(null);
          setRateChecked(true);
        } finally {
          setRateLoading(false);
        }
      }, 300);
    },
    [memberId, projectId, billable],
  );

  // Trigger rate resolution when billable, date, or dialog open changes
  useEffect(() => {
    if (open && billable && memberId) {
      doResolveRate(date);
    } else {
      setResolvedRate(null);
      setRateChecked(false);
    }

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [open, billable, date, memberId, doResolveRate]);

  async function handleSubmit(formData: FormData) {
    setError(null);

    // Inline validation: hours + minutes must sum > 0
    const h = parseInt(formData.get("hours")?.toString() ?? "0", 10) || 0;
    const m = parseInt(formData.get("minutes")?.toString() ?? "0", 10) || 0;
    if (h * 60 + m <= 0) {
      setError("Duration must be greater than 0.");
      return;
    }

    setIsSubmitting(true);

    try {
      const result = await createTimeEntry(slug, projectId, taskId, formData);
      if (result.success) {
        formRef.current?.reset();
        setBillable(true);
        setHours(0);
        setMinutes(0);
        setDate(new Date().toLocaleDateString("en-CA"));
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
      setBillable(true);
      setHours(0);
      setMinutes(0);
      setDate(new Date().toLocaleDateString("en-CA"));
      setResolvedRate(null);
      setRateChecked(false);
    }
    setOpen(newOpen);
  }

  // Default date to today in YYYY-MM-DD format
  const today = new Date().toLocaleDateString("en-CA");

  // Compute rate preview
  const totalMinutes = hours * 60 + minutes;
  const durationHours = totalMinutes / 60;
  const computedValue =
    resolvedRate && totalMinutes > 0
      ? durationHours * resolvedRate.hourlyRate
      : null;

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
                  onChange={(e) =>
                    setHours(parseInt(e.target.value, 10) || 0)
                  }
                />
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  h
                </span>
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
                  onChange={(e) =>
                    setMinutes(parseInt(e.target.value, 10) || 0)
                  }
                />
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  m
                </span>
              </div>
            </div>
          </div>

          {/* Retainer Indicator */}
          <RetainerIndicator
            summary={retainerSummary ?? null}
            selectedDate={date}
          />

          {/* Date */}
          <div className="space-y-2">
            <Label htmlFor="time-date">Date</Label>
            <Input
              id="time-date"
              name="date"
              type="date"
              defaultValue={today}
              required
              onChange={(e) => setDate(e.target.value)}
            />
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="time-description">
              Description{" "}
              <span className="font-normal text-muted-foreground">
                (optional)
              </span>
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
              checked={billable}
              onChange={(e) => setBillable(e.target.checked)}
              className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 dark:border-slate-700"
            />
            <Label htmlFor="time-billable" className="font-normal">
              Billable
            </Label>
          </div>

          {/* Rate Preview */}
          {billable && memberId && (
            <div
              className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/50"
              data-testid="rate-preview"
            >
              {rateLoading ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Resolving rate...
                </p>
              ) : rateChecked && resolvedRate ? (
                <div className="space-y-1">
                  <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Billing rate:{" "}
                    {formatCurrency(
                      resolvedRate.hourlyRate,
                      resolvedRate.currency,
                    )}
                    /hr
                    <span className="ml-1 font-normal text-slate-500 dark:text-slate-400">
                      ({formatRateSource(resolvedRate.source)})
                    </span>
                  </p>
                  {computedValue !== null && (
                    <p className="text-sm text-slate-600 dark:text-slate-400">
                      {formatDuration(totalMinutes)} x{" "}
                      {formatCurrency(
                        resolvedRate.hourlyRate,
                        resolvedRate.currency,
                      )}{" "}
                      ={" "}
                      <span className="font-medium">
                        {formatCurrency(computedValue, resolvedRate.currency)}
                      </span>
                    </p>
                  )}
                </div>
              ) : rateChecked ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  No billing rate configured
                </p>
              ) : null}
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
              {isSubmitting ? "Logging..." : "Log Time"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
