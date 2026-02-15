"use client";

import { useState } from "react";
import { AlertTriangle, DollarSign, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { CurrencySelector } from "@/components/rates/currency-selector";
import { AddProjectRateDialog } from "@/components/rates/add-project-rate-dialog";
import {
  updateProjectBillingRate,
  deleteProjectBillingRate,
} from "@/app/(app)/org/[slug]/projects/[id]/rate-actions";
import { formatCurrency, formatDate } from "@/lib/format";
import type { BillingRate, ProjectMember } from "@/lib/types";

interface ProjectRatesTabProps {
  billingRates: BillingRate[];
  members: ProjectMember[];
  projectId: string;
  slug: string;
  defaultCurrency: string;
}

export function ProjectRatesTab({
  billingRates,
  members,
  projectId,
  slug,
  defaultCurrency,
}: ProjectRatesTabProps) {
  const projectRates = billingRates.filter(
    (r) => r.scope === "PROJECT_OVERRIDE",
  );

  if (projectRates.length === 0) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={DollarSign}
          title="No project rate overrides"
          description="Add billing rate overrides for specific team members on this project."
          action={
            <AddProjectRateDialog
              slug={slug}
              projectId={projectId}
              members={members}
              defaultCurrency={defaultCurrency}
            >
              <Button>
                <Plus className="mr-1.5 size-4" />
                Add Override
              </Button>
            </AddProjectRateDialog>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
            Project Rate Overrides
          </h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            These rates override member defaults for this project.
          </p>
        </div>
        <AddProjectRateDialog
          slug={slug}
          projectId={projectId}
          members={members}
          defaultCurrency={defaultCurrency}
        >
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            Add Override
          </Button>
        </AddProjectRateDialog>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Member</TableHead>
              <TableHead>Hourly Rate</TableHead>
              <TableHead>Currency</TableHead>
              <TableHead>Effective From</TableHead>
              <TableHead>Effective To</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {projectRates.map((rate) => (
              <TableRow key={rate.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <AvatarCircle name={rate.memberName} size={32} />
                    <span className="font-medium text-slate-900 dark:text-slate-100">
                      {rate.memberName}
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <span className="font-medium">
                    {formatCurrency(rate.hourlyRate, rate.currency)}
                  </span>
                </TableCell>
                <TableCell>{rate.currency}</TableCell>
                <TableCell>{formatDate(rate.effectiveFrom)}</TableCell>
                <TableCell>
                  {rate.effectiveTo ? (
                    formatDate(rate.effectiveTo)
                  ) : (
                    <span className="text-slate-400 dark:text-slate-600">
                      Ongoing
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    <EditProjectRateDialog
                      slug={slug}
                      projectId={projectId}
                      rate={rate}
                    >
                      <Button
                        variant="plain"
                        size="sm"
                        aria-label={`Edit project rate for ${rate.memberName}`}
                      >
                        <Pencil className="size-4" />
                      </Button>
                    </EditProjectRateDialog>
                    <DeleteProjectRateDialog
                      slug={slug}
                      projectId={projectId}
                      rateId={rate.id}
                      memberName={rate.memberName}
                    >
                      <Button
                        variant="plain"
                        size="sm"
                        aria-label={`Delete project rate for ${rate.memberName}`}
                      >
                        <Trash2 className="size-4 text-red-500" />
                      </Button>
                    </DeleteProjectRateDialog>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}

// --- Edit Project Rate Dialog ---

interface EditProjectRateDialogProps {
  slug: string;
  projectId: string;
  rate: BillingRate;
  children: React.ReactNode;
}

function EditProjectRateDialog({
  slug,
  projectId,
  rate,
  children,
}: EditProjectRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hourlyRate, setHourlyRate] = useState(rate.hourlyRate.toString());
  const [currency, setCurrency] = useState(rate.currency);
  const [effectiveFrom, setEffectiveFrom] = useState(rate.effectiveFrom);
  const [effectiveTo, setEffectiveTo] = useState(rate.effectiveTo ?? "");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const parsedRate = parseFloat(hourlyRate);
    if (isNaN(parsedRate) || parsedRate <= 0) {
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
      const result = await updateProjectBillingRate(slug, projectId, rate.id, {
        currency,
        hourlyRate: parsedRate,
        effectiveFrom,
        effectiveTo: effectiveTo || undefined,
      });

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update rate.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setHourlyRate(rate.hourlyRate.toString());
      setCurrency(rate.currency);
      setEffectiveFrom(rate.effectiveFrom);
      setEffectiveTo(rate.effectiveTo ?? "");
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Project Rate Override</DialogTitle>
          <DialogDescription>
            Update the project billing rate for {rate.memberName}.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-project-rate-hourly">Hourly Rate</Label>
            <Input
              id="edit-project-rate-hourly"
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
            <Label htmlFor="edit-project-rate-from">Effective From</Label>
            <Input
              id="edit-project-rate-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-project-rate-to">
              Effective To{" "}
              <span className="font-normal text-slate-500">(optional)</span>
            </Label>
            <Input
              id="edit-project-rate-to"
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
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// --- Delete Project Rate Dialog ---

interface DeleteProjectRateDialogProps {
  slug: string;
  projectId: string;
  rateId: string;
  memberName: string;
  children: React.ReactNode;
}

function DeleteProjectRateDialog({
  slug,
  projectId,
  rateId,
  memberName,
  children,
}: DeleteProjectRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);

    try {
      const result = await deleteProjectBillingRate(slug, projectId, rateId);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete rate.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">
            Delete Project Rate Override
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Delete the project billing rate override for {memberName}? This
            action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isDeleting}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
