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
import { CurrencySelector } from "@/components/rates/currency-selector";
import { createProjectBillingRate } from "@/app/(app)/org/[slug]/projects/[id]/rate-actions";
import type { ProjectMember } from "@/lib/types";

interface AddProjectRateDialogProps {
  slug: string;
  projectId: string;
  members: ProjectMember[];
  defaultCurrency: string;
  children: React.ReactNode;
}

export function AddProjectRateDialog({
  slug,
  projectId,
  members,
  defaultCurrency,
  children,
}: AddProjectRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memberId, setMemberId] = useState("");
  const [hourlyRate, setHourlyRate] = useState("");
  const [currency, setCurrency] = useState(defaultCurrency);
  const [effectiveFrom, setEffectiveFrom] = useState(
    new Date().toLocaleDateString("en-CA"),
  );
  const [effectiveTo, setEffectiveTo] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!memberId) {
      setError("Please select a member.");
      return;
    }

    const rate = parseFloat(hourlyRate);
    if (isNaN(rate) || rate <= 0) {
      setError("Hourly rate must be greater than 0.");
      return;
    }
    if (!currency) {
      setError("Currency is required.");
      return;
    }
    if (!effectiveFrom) {
      setError("Effective from date is required.");
      return;
    }

    setIsSubmitting(true);

    try {
      const result = await createProjectBillingRate(slug, projectId, {
        memberId,
        currency,
        hourlyRate: rate,
        effectiveFrom,
        effectiveTo: effectiveTo || undefined,
      });

      if (result.success) {
        resetForm();
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create rate override.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setMemberId("");
    setHourlyRate("");
    setCurrency(defaultCurrency);
    setEffectiveFrom(new Date().toLocaleDateString("en-CA"));
    setEffectiveTo("");
    setError(null);
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      resetForm();
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Project Rate Override</DialogTitle>
          <DialogDescription>
            Create a project-specific billing rate override for a team member.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="project-rate-member">Member</Label>
            <select
              id="project-rate-member"
              value={memberId}
              onChange={(e) => setMemberId(e.target.value)}
              className="flex h-9 w-full rounded-md border border-olive-200 bg-white px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-olive-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-olive-800 dark:bg-olive-950 dark:placeholder:text-olive-400"
              required
            >
              <option value="">Select a member...</option>
              {members.map((member) => (
                <option key={member.memberId} value={member.memberId}>
                  {member.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="project-rate-hourly">Hourly Rate</Label>
            <Input
              id="project-rate-hourly"
              type="number"
              min="0.01"
              step="0.01"
              value={hourlyRate}
              onChange={(e) => setHourlyRate(e.target.value)}
              placeholder="0.00"
              required
            />
          </div>

          <div className="space-y-2">
            <Label>Currency</Label>
            <CurrencySelector
              value={currency}
              onChange={setCurrency}
              className="w-full"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="project-rate-from">Effective From</Label>
            <Input
              id="project-rate-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="project-rate-to">
              Effective To{" "}
              <span className="font-normal text-olive-500">(optional)</span>
            </Label>
            <Input
              id="project-rate-to"
              type="date"
              value={effectiveTo}
              onChange={(e) => setEffectiveTo(e.target.value)}
            />
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
              {isSubmitting ? "Creating..." : "Create Override"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
