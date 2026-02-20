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
import { updateScheduleAction } from "@/app/(app)/org/[slug]/schedules/actions";
import { FREQUENCY_LABELS } from "@/lib/schedule-constants";
import type { ScheduleResponse, UpdateScheduleRequest } from "@/lib/api/schedules";
import type { OrgMember } from "@/lib/types";

interface ScheduleEditDialogProps {
  slug: string;
  schedule: ScheduleResponse;
  orgMembers: OrgMember[];
  children: React.ReactNode;
}

const selectClasses =
  "flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800";

export function ScheduleEditDialog({
  slug,
  schedule,
  orgMembers,
  children,
}: ScheduleEditDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [nameOverride, setNameOverride] = useState(schedule.nameOverride ?? "");
  const [endDate, setEndDate] = useState(schedule.endDate ?? "");
  const [leadTimeDays, setLeadTimeDays] = useState(schedule.leadTimeDays);
  const [projectLeadMemberId, setProjectLeadMemberId] = useState(
    schedule.projectLeadMemberId ?? "",
  );

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      setNameOverride(schedule.nameOverride ?? "");
      setEndDate(schedule.endDate ?? "");
      setLeadTimeDays(schedule.leadTimeDays);
      setProjectLeadMemberId(schedule.projectLeadMemberId ?? "");
      setError(null);
    }
    setOpen(newOpen);
  }

  async function handleSubmit() {
    setError(null);
    setIsSubmitting(true);

    try {
      const data: UpdateScheduleRequest = {
        leadTimeDays,
        nameOverride: nameOverride.trim() || undefined,
        endDate: endDate || undefined,
        projectLeadMemberId: projectLeadMemberId || undefined,
      };
      const result = await updateScheduleAction(slug, schedule.id, data);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update schedule.");
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
          <DialogTitle>Edit Schedule</DialogTitle>
          <DialogDescription>
            Update the editable fields for this recurring schedule.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          {/* Read-only fields */}
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
            <dl className="space-y-1 text-sm">
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">Template:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {schedule.templateName}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">Customer:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {schedule.customerName}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">Frequency:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {FREQUENCY_LABELS[schedule.frequency]}
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="text-slate-500 dark:text-slate-400">Start Date:</dt>
                <dd className="font-medium text-slate-900 dark:text-slate-100">
                  {schedule.startDate}
                </dd>
              </div>
            </dl>
          </div>

          {/* Name Override */}
          <div className="space-y-2">
            <Label htmlFor="edit-name-override">
              Name Override{" "}
              <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="edit-name-override"
              value={nameOverride}
              onChange={(e) => setNameOverride(e.target.value)}
              placeholder="Custom project name pattern..."
              maxLength={255}
            />
          </div>

          {/* End Date */}
          <div className="space-y-2">
            <Label htmlFor="edit-end-date">
              End Date{" "}
              <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="edit-end-date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>

          {/* Lead Time Days */}
          <div className="space-y-2">
            <Label htmlFor="edit-lead-time">Lead Time (days)</Label>
            <Input
              id="edit-lead-time"
              type="number"
              min={0}
              value={leadTimeDays}
              onChange={(e) => setLeadTimeDays(Math.max(0, parseInt(e.target.value) || 0))}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Number of days before the period start date to create the project.
            </p>
          </div>

          {/* Project Lead */}
          {orgMembers.length > 0 && (
            <div className="space-y-2">
              <Label htmlFor="edit-lead-member">
                Project Lead{" "}
                <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <select
                id="edit-lead-member"
                value={projectLeadMemberId}
                onChange={(e) => setProjectLeadMemberId(e.target.value)}
                className={selectClasses}
              >
                <option value="">Unassigned</option>
                {orgMembers.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </div>
          )}

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
