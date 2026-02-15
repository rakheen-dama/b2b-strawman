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
  createBillingRate,
  createCostRate,
} from "@/app/(app)/org/[slug]/settings/rates/actions";
import type { OrgMember } from "@/lib/types";

interface AddRateDialogProps {
  slug: string;
  member: OrgMember;
  defaultCurrency: string;
  children: React.ReactNode;
}

export function AddRateDialog({
  slug,
  member,
  defaultCurrency,
  children,
}: AddRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rateType, setRateType] = useState<"billing" | "cost">("billing");
  const [hourlyRate, setHourlyRate] = useState("");
  const [currency, setCurrency] = useState(defaultCurrency);
  const [effectiveFrom, setEffectiveFrom] = useState(
    new Date().toLocaleDateString("en-CA"),
  );
  const [effectiveTo, setEffectiveTo] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

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
      let result;
      if (rateType === "billing") {
        result = await createBillingRate(slug, {
          memberId: member.id,
          currency,
          hourlyRate: rate,
          effectiveFrom,
          effectiveTo: effectiveTo || undefined,
        });
      } else {
        result = await createCostRate(slug, {
          memberId: member.id,
          currency,
          hourlyCost: rate,
          effectiveFrom,
          effectiveTo: effectiveTo || undefined,
        });
      }

      if (result.success) {
        resetForm();
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to create rate.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setRateType("billing");
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
          <DialogTitle>Add Rate</DialogTitle>
          <DialogDescription>
            Create a new rate for {member.name}.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label>Rate Type</Label>
            <div className="flex gap-2">
              <Button
                type="button"
                variant={rateType === "billing" ? "accent" : "plain"}
                size="sm"
                onClick={() => setRateType("billing")}
              >
                Billing Rate
              </Button>
              <Button
                type="button"
                variant={rateType === "cost" ? "accent" : "plain"}
                size="sm"
                onClick={() => setRateType("cost")}
              >
                Cost Rate
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="add-hourly-rate">
              {rateType === "billing" ? "Hourly Rate" : "Hourly Cost"}
            </Label>
            <Input
              id="add-hourly-rate"
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
            <Label htmlFor="add-effective-from">Effective From</Label>
            <Input
              id="add-effective-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="add-effective-to">
              Effective To{" "}
              <span className="font-normal text-slate-500">(optional)</span>
            </Label>
            <Input
              id="add-effective-to"
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
              {isSubmitting ? "Creating..." : "Create Rate"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
