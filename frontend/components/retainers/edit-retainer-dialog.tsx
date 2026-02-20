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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { updateRetainerAction } from "@/app/(app)/org/[slug]/retainers/[id]/actions";
import {
  FREQUENCY_LABELS,
  TYPE_LABELS,
  ROLLOVER_LABELS,
} from "@/lib/retainer-constants";
import { formatLocalDate } from "@/lib/format";
import type { RetainerResponse, UpdateRetainerRequest, RolloverPolicy } from "@/lib/api/retainers";

interface EditRetainerDialogProps {
  slug: string;
  retainer: RetainerResponse;
  children: React.ReactNode;
}

export function EditRetainerDialog({
  slug,
  retainer,
  children,
}: EditRetainerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState(retainer.name);
  const [allocatedHours, setAllocatedHours] = useState<number | string>(
    retainer.allocatedHours ?? "",
  );
  const [periodFee, setPeriodFee] = useState<number | string>(
    retainer.periodFee ?? "",
  );
  const [rolloverPolicy, setRolloverPolicy] = useState<RolloverPolicy | "">(
    retainer.rolloverPolicy ?? "",
  );
  const [rolloverCapHours, setRolloverCapHours] = useState<number | string>(
    retainer.rolloverCapHours ?? "",
  );
  const [endDate, setEndDate] = useState(retainer.endDate ?? "");
  const [notes, setNotes] = useState(retainer.notes ?? "");

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      setName(retainer.name);
      setAllocatedHours(retainer.allocatedHours ?? "");
      setPeriodFee(retainer.periodFee ?? "");
      setRolloverPolicy(retainer.rolloverPolicy ?? "");
      setRolloverCapHours(retainer.rolloverCapHours ?? "");
      setEndDate(retainer.endDate ?? "");
      setNotes(retainer.notes ?? "");
      setError(null);
    }
    setOpen(newOpen);
  }

  async function handleSubmit() {
    setError(null);
    setIsSubmitting(true);

    try {
      const data: UpdateRetainerRequest = {
        name: name.trim(),
        allocatedHours:
          retainer.type === "HOUR_BANK" && allocatedHours !== ""
            ? Number(allocatedHours)
            : undefined,
        periodFee: periodFee !== "" ? Number(periodFee) : undefined,
        rolloverPolicy:
          retainer.type === "HOUR_BANK" && rolloverPolicy
            ? (rolloverPolicy as RolloverPolicy)
            : undefined,
        rolloverCapHours:
          rolloverPolicy === "CARRY_CAPPED" && rolloverCapHours !== ""
            ? Number(rolloverCapHours)
            : undefined,
        endDate: endDate || undefined,
        notes: notes.trim() || undefined,
      };
      const result = await updateRetainerAction(slug, retainer.id, data);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update retainer.");
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
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit Retainer</DialogTitle>
          <DialogDescription>
            Changes take effect on the next period.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          {/* Read-only fields */}
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
            <dl className="space-y-1 text-sm">
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">Type:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {TYPE_LABELS[retainer.type]}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">
                  Frequency:
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {FREQUENCY_LABELS[retainer.frequency]}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">
                  Start Date:
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {formatLocalDate(retainer.startDate)}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">
                  Customer:
                </dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {retainer.customerName}
                </dd>
              </div>
            </dl>
          </div>

          {/* Editable fields */}
          <div className="space-y-1.5">
            <Label htmlFor="edit-name">Name</Label>
            <Input
              id="edit-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          {retainer.type === "HOUR_BANK" && (
            <div className="space-y-1.5">
              <Label htmlFor="edit-allocated-hours">Allocated Hours</Label>
              <Input
                id="edit-allocated-hours"
                type="number"
                min="0"
                step="0.5"
                value={allocatedHours}
                onChange={(e) => setAllocatedHours(e.target.value)}
              />
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="edit-period-fee">Period Fee</Label>
            <Input
              id="edit-period-fee"
              type="number"
              min="0"
              step="0.01"
              value={periodFee}
              onChange={(e) => setPeriodFee(e.target.value)}
            />
          </div>

          {retainer.type === "HOUR_BANK" && (
            <div className="space-y-1.5">
              <Label htmlFor="edit-rollover-policy">Rollover Policy</Label>
              <Select
                value={rolloverPolicy}
                onValueChange={(v) => setRolloverPolicy(v as RolloverPolicy)}
              >
                <SelectTrigger id="edit-rollover-policy">
                  <SelectValue placeholder="Select policy" />
                </SelectTrigger>
                <SelectContent>
                  {(
                    Object.entries(ROLLOVER_LABELS) as [RolloverPolicy, string][]
                  ).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          {rolloverPolicy === "CARRY_CAPPED" && (
            <div className="space-y-1.5">
              <Label htmlFor="edit-rollover-cap">Rollover Cap (hours)</Label>
              <Input
                id="edit-rollover-cap"
                type="number"
                min="0"
                step="0.5"
                value={rolloverCapHours}
                onChange={(e) => setRolloverCapHours(e.target.value)}
              />
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="edit-end-date">End Date</Label>
            <Input
              id="edit-end-date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="edit-notes">Notes</Label>
            <Textarea
              id="edit-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => setOpen(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? "Saving..." : "Save Changes"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
