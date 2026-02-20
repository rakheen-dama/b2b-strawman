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
import { TemplatePicker } from "@/components/templates/TemplatePicker";
import { createScheduleAction } from "@/app/(app)/org/[slug]/schedules/actions";
import { resolveNameTokens } from "@/lib/name-token-resolver";
import { FREQUENCY_OPTIONS } from "@/lib/schedule-constants";
import type { RecurrenceFrequency } from "@/lib/schedule-constants";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

interface ScheduleCreateDialogProps {
  slug: string;
  templates: ProjectTemplateResponse[];
  customers: Customer[];
  orgMembers: OrgMember[];
  children: React.ReactNode;
}

const selectClasses =
  "flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800";

export function ScheduleCreateDialog({
  slug,
  templates,
  customers,
  orgMembers,
  children,
}: ScheduleCreateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [customerId, setCustomerId] = useState("");
  const [frequency, setFrequency] = useState<RecurrenceFrequency>("MONTHLY");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [leadTimeDays, setLeadTimeDays] = useState(0);
  const [projectLeadMemberId, setProjectLeadMemberId] = useState("");
  const [nameOverride, setNameOverride] = useState("");

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId) ?? null;
  const selectedCustomerName = customers.find((c) => c.id === customerId)?.name;

  const namePreview =
    selectedTemplate && selectedCustomerName
      ? resolveNameTokens(selectedTemplate.namePattern, selectedCustomerName, new Date())
      : selectedTemplate
        ? resolveNameTokens(selectedTemplate.namePattern, undefined, new Date())
        : "";

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      setSelectedTemplateId(null);
      setCustomerId("");
      setFrequency("MONTHLY");
      setStartDate("");
      setEndDate("");
      setLeadTimeDays(0);
      setProjectLeadMemberId("");
      setNameOverride("");
      setError(null);
    }
    setOpen(newOpen);
  }

  async function handleSubmit() {
    if (!selectedTemplateId || !customerId || !startDate) return;

    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createScheduleAction(slug, {
        templateId: selectedTemplateId,
        customerId,
        frequency,
        startDate,
        endDate: endDate || undefined,
        leadTimeDays,
        projectLeadMemberId: projectLeadMemberId || undefined,
        nameOverride: nameOverride.trim() || undefined,
      });

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create schedule.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const canSubmit = !!selectedTemplateId && !!customerId && !!startDate && !isSubmitting;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New Recurring Schedule</DialogTitle>
          <DialogDescription>
            Set up automatic project creation from a template on a recurring basis.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          {/* Template */}
          <div className="space-y-2">
            <Label>Template</Label>
            <TemplatePicker
              templates={templates}
              selectedId={selectedTemplateId}
              onSelect={setSelectedTemplateId}
            />
          </div>

          {/* Customer */}
          <div className="space-y-2">
            <Label htmlFor="schedule-customer">Customer</Label>
            <select
              id="schedule-customer"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              className={selectClasses}
              required
            >
              <option value="">Select a customer...</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          {/* Frequency */}
          <div className="space-y-2">
            <Label htmlFor="schedule-frequency">Frequency</Label>
            <select
              id="schedule-frequency"
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as RecurrenceFrequency)}
              className={selectClasses}
            >
              {FREQUENCY_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* Start Date */}
          <div className="space-y-2">
            <Label htmlFor="schedule-start-date">Start Date</Label>
            <Input
              id="schedule-start-date"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              required
            />
          </div>

          {/* End Date */}
          <div className="space-y-2">
            <Label htmlFor="schedule-end-date">
              End Date{" "}
              <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="schedule-end-date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>

          {/* Lead Time Days */}
          <div className="space-y-2">
            <Label htmlFor="schedule-lead-time">Lead Time (days)</Label>
            <Input
              id="schedule-lead-time"
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
              <Label htmlFor="schedule-lead-member">
                Project Lead{" "}
                <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <select
                id="schedule-lead-member"
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

          {/* Name Override */}
          <div className="space-y-2">
            <Label htmlFor="schedule-name-override">
              Name Override{" "}
              <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Input
              id="schedule-name-override"
              value={nameOverride}
              onChange={(e) => setNameOverride(e.target.value)}
              placeholder="Custom project name pattern..."
              maxLength={255}
            />
            {namePreview && (
              <p className="text-xs text-slate-400 dark:text-slate-500">
                Name preview:{" "}
                <span className="font-medium text-slate-600 dark:text-slate-300">
                  {namePreview}
                </span>
              </p>
            )}
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
          <Button type="button" onClick={handleSubmit} disabled={!canSubmit}>
            {isSubmitting ? "Creating..." : "Create Schedule"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
