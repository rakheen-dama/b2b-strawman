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
import { Switch } from "@/components/ui/switch";
import { updateTaxRate } from "@/app/(app)/org/[slug]/settings/tax/actions";
import type { TaxRateResponse } from "@/lib/types";

interface EditTaxRateDialogProps {
  slug: string;
  taxRate: TaxRateResponse;
  children: React.ReactNode;
}

export function EditTaxRateDialog({
  slug,
  taxRate,
  children,
}: EditTaxRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState(taxRate.name);
  const [rate, setRate] = useState(String(taxRate.rate));
  const [isDefault, setIsDefault] = useState(taxRate.isDefault);
  const [isExempt, setIsExempt] = useState(taxRate.isExempt);

  function resetForm() {
    setName(taxRate.name);
    setRate(String(taxRate.rate));
    setIsDefault(taxRate.isDefault);
    setIsExempt(taxRate.isExempt);
    setError(null);
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (nextOpen) {
      resetForm();
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("Name is required.");
      return;
    }
    if (trimmedName.length > 100) {
      setError("Name must be 100 characters or less.");
      return;
    }

    const rateNum = parseFloat(rate);
    if (isNaN(rateNum) || rateNum < 0 || rateNum > 99.99) {
      setError("Rate must be between 0.00 and 99.99.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const result = await updateTaxRate(slug, taxRate.id, {
        name: trimmedName,
        rate: rateNum,
        isDefault,
        isExempt,
      });

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "An error occurred.");
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
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Edit Tax Rate</DialogTitle>
            <DialogDescription>
              Update the tax rate details.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="edit-tax-rate-name">Name</Label>
              <Input
                id="edit-tax-rate-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. VAT, GST, Sales Tax"
                maxLength={100}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="edit-tax-rate-percent">Rate (%)</Label>
              <Input
                id="edit-tax-rate-percent"
                type="number"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                placeholder="e.g. 15.00"
                min="0"
                max="99.99"
                step="0.01"
                required
                disabled={isExempt}
              />
            </div>

            <div className="flex items-center gap-3">
              <Switch
                id="edit-tax-rate-default"
                checked={isDefault}
                onCheckedChange={setIsDefault}
              />
              <Label htmlFor="edit-tax-rate-default">
                Set as default tax rate
              </Label>
            </div>
            {isDefault && !taxRate.isDefault && (
              <p className="text-xs text-amber-600 dark:text-amber-400">
                This will replace the current default tax rate.
              </p>
            )}

            <div className="flex items-center gap-3">
              <Switch
                id="edit-tax-rate-exempt"
                checked={isExempt}
                onCheckedChange={(checked) => {
                  setIsExempt(checked);
                  if (checked) {
                    setRate("0");
                  }
                }}
              />
              <Label htmlFor="edit-tax-rate-exempt">
                Tax exempt (0% rate)
              </Label>
            </div>

            {error && (
              <p className="text-sm text-destructive">{error}</p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="outline"
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
