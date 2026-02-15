"use client";

import { useState, type ReactNode } from "react";
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
import { CurrencySelector } from "@/components/rates/currency-selector";
import { upsertBudget } from "@/app/(app)/org/[slug]/projects/[id]/budget-actions";
import type { BudgetStatusResponse, UpsertBudgetRequest } from "@/lib/types";

interface BudgetConfigDialogProps {
  slug: string;
  projectId: string;
  existing: BudgetStatusResponse | null;
  defaultCurrency: string;
  children: ReactNode;
}

export function BudgetConfigDialog({
  slug,
  projectId,
  existing,
  defaultCurrency,
  children,
}: BudgetConfigDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currency, setCurrency] = useState(
    existing?.budgetCurrency ?? defaultCurrency
  );
  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setError(null);
      setCurrency(existing?.budgetCurrency ?? defaultCurrency);
    }
  }

  async function handleSubmit(formData: FormData) {
    setError(null);

    const hoursStr = formData.get("budgetHours")?.toString().trim();
    const amountStr = formData.get("budgetAmount")?.toString().trim();
    const thresholdStr = formData.get("alertThresholdPct")?.toString().trim();
    const notes = formData.get("notes")?.toString().trim() || undefined;

    const budgetHours = hoursStr ? parseFloat(hoursStr) : undefined;
    const budgetAmount = amountStr ? parseFloat(amountStr) : undefined;

    if (!budgetHours && !budgetAmount) {
      setError("At least one of budget hours or budget amount is required.");
      return;
    }

    if (budgetHours !== undefined && budgetHours <= 0) {
      setError("Budget hours must be a positive number.");
      return;
    }

    if (budgetAmount !== undefined && budgetAmount <= 0) {
      setError("Budget amount must be a positive number.");
      return;
    }

    if (budgetAmount !== undefined && !currency) {
      setError("Currency is required when budget amount is set.");
      return;
    }

    const alertThresholdPct = thresholdStr ? parseInt(thresholdStr, 10) : 80;
    if (alertThresholdPct < 50 || alertThresholdPct > 100) {
      setError("Alert threshold must be between 50% and 100%.");
      return;
    }

    const data: UpsertBudgetRequest = {
      budgetHours,
      budgetAmount,
      budgetCurrency: budgetAmount !== undefined ? currency : undefined,
      alertThresholdPct,
      notes,
    };

    setIsSubmitting(true);
    try {
      const result = await upsertBudget(slug, projectId, data);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to save budget.");
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {existing ? "Edit Budget" : "Set Budget"}
          </DialogTitle>
          <DialogDescription>
            Configure budget limits for this project. Set hours, amount, or
            both.
          </DialogDescription>
        </DialogHeader>
        <form action={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="budgetHours">Budget Hours</Label>
              <Input
                id="budgetHours"
                name="budgetHours"
                type="number"
                min={0}
                step="0.5"
                placeholder="e.g. 200"
                defaultValue={existing?.budgetHours ?? ""}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="budgetAmount">Budget Amount</Label>
              <Input
                id="budgetAmount"
                name="budgetAmount"
                type="number"
                min={0}
                step="0.01"
                placeholder="e.g. 50000"
                defaultValue={existing?.budgetAmount ?? ""}
              />
            </div>
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
            <Label htmlFor="alertThresholdPct">
              Alert Threshold (%)
            </Label>
            <Input
              id="alertThresholdPct"
              name="alertThresholdPct"
              type="number"
              min={50}
              max={100}
              defaultValue={existing?.alertThresholdPct ?? 80}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              You will be alerted when consumption reaches this percentage
              (50-100%).
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="notes">Notes</Label>
            <Textarea
              id="notes"
              name="notes"
              placeholder="Optional notes about this budget..."
              rows={3}
              defaultValue={existing?.notes ?? ""}
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
              {isSubmitting ? "Saving..." : "Save Budget"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
