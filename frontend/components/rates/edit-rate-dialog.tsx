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
import {
  updateBillingRate,
  updateCostRate,
} from "@/app/(app)/org/[slug]/settings/rates/actions";
import type { BillingRate, CostRate } from "@/lib/types";

interface EditBillingRateDialogProps {
  slug: string;
  rate: BillingRate;
  children: React.ReactNode;
}

interface EditCostRateDialogProps {
  slug: string;
  rate: CostRate;
  children: React.ReactNode;
}

type EditRateDialogProps =
  | (EditBillingRateDialogProps & { rateType: "billing" })
  | (EditCostRateDialogProps & { rateType: "cost" });

export function EditRateDialog(props: EditRateDialogProps) {
  const { slug, rateType, children } = props;
  const rate = props.rate;

  const initialRate =
    rateType === "billing"
      ? (rate as BillingRate).hourlyRate
      : (rate as CostRate).hourlyCost;

  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hourlyRate, setHourlyRate] = useState(initialRate.toString());
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
      let result;
      if (rateType === "billing") {
        result = await updateBillingRate(slug, rate.id, {
          currency,
          hourlyRate: parsedRate,
          effectiveFrom,
          effectiveTo: effectiveTo || undefined,
        });
      } else {
        result = await updateCostRate(slug, rate.id, {
          currency,
          hourlyCost: parsedRate,
          effectiveFrom,
          effectiveTo: effectiveTo || undefined,
        });
      }

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
      setHourlyRate(initialRate.toString());
      setCurrency(rate.currency);
      setEffectiveFrom(rate.effectiveFrom);
      setEffectiveTo(rate.effectiveTo ?? "");
      setError(null);
    }
    setOpen(newOpen);
  }

  const memberName =
    rateType === "billing"
      ? (rate as BillingRate).memberName
      : (rate as CostRate).memberName;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            Edit {rateType === "billing" ? "Billing" : "Cost"} Rate
          </DialogTitle>
          <DialogDescription>
            Update the {rateType === "billing" ? "billing" : "cost"} rate for{" "}
            {memberName}.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-hourly-rate">
              {rateType === "billing" ? "Hourly Rate" : "Hourly Cost"}
            </Label>
            <Input
              id="edit-hourly-rate"
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
            <Label htmlFor="edit-effective-from">Effective From</Label>
            <Input
              id="edit-effective-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-effective-to">
              Effective To{" "}
              <span className="font-normal text-olive-500">(optional)</span>
            </Label>
            <Input
              id="edit-effective-to"
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
