"use client";

import { useState } from "react";
import { DollarSign, Pencil, Plus, Trash2 } from "lucide-react";
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
import {
  createCustomerBillingRate,
  updateCustomerBillingRate,
  deleteCustomerBillingRate,
} from "@/app/(app)/org/[slug]/customers/[id]/rate-actions";
import { formatDate } from "@/lib/format";
import { AlertTriangle } from "lucide-react";
import type { BillingRate, OrgMember } from "@/lib/types";

function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

interface CustomerRatesTabProps {
  billingRates: BillingRate[];
  members: OrgMember[];
  customerId: string;
  slug: string;
  defaultCurrency: string;
}

export function CustomerRatesTab({
  billingRates,
  members,
  customerId,
  slug,
  defaultCurrency,
}: CustomerRatesTabProps) {
  const customerRates = billingRates.filter(
    (r) => r.scope === "CUSTOMER_OVERRIDE",
  );

  if (customerRates.length === 0) {
    return (
      <div className="space-y-4">
        <EmptyState
          icon={DollarSign}
          title="No customer rate overrides"
          description="Add billing rate overrides for team members when working for this customer."
          action={
            <AddCustomerRateDialog
              slug={slug}
              customerId={customerId}
              members={members}
              defaultCurrency={defaultCurrency}
            >
              <Button>
                <Plus className="mr-1.5 size-4" />
                Add Override
              </Button>
            </AddCustomerRateDialog>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-medium text-olive-900 dark:text-olive-100">
            Customer Rate Overrides
          </h3>
          <p className="mt-1 text-xs text-olive-500 dark:text-olive-400">
            These rates override member defaults when working for this customer.
          </p>
        </div>
        <AddCustomerRateDialog
          slug={slug}
          customerId={customerId}
          members={members}
          defaultCurrency={defaultCurrency}
        >
          <Button size="sm">
            <Plus className="mr-1.5 size-4" />
            Add Override
          </Button>
        </AddCustomerRateDialog>
      </div>

      <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
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
            {customerRates.map((rate) => (
              <TableRow key={rate.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <AvatarCircle name={rate.memberName} size={32} />
                    <span className="font-medium text-olive-900 dark:text-olive-100">
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
                    <span className="text-olive-400 dark:text-olive-600">
                      Ongoing
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    <EditCustomerRateDialog
                      slug={slug}
                      customerId={customerId}
                      rate={rate}
                    >
                      <Button
                        variant="plain"
                        size="sm"
                        aria-label={`Edit customer rate for ${rate.memberName}`}
                      >
                        <Pencil className="size-4" />
                      </Button>
                    </EditCustomerRateDialog>
                    <DeleteCustomerRateDialog
                      slug={slug}
                      customerId={customerId}
                      rateId={rate.id}
                      memberName={rate.memberName}
                    >
                      <Button
                        variant="plain"
                        size="sm"
                        aria-label={`Delete customer rate for ${rate.memberName}`}
                      >
                        <Trash2 className="size-4 text-red-500" />
                      </Button>
                    </DeleteCustomerRateDialog>
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

// --- Add Customer Rate Dialog ---

interface AddCustomerRateDialogProps {
  slug: string;
  customerId: string;
  members: OrgMember[];
  defaultCurrency: string;
  children: React.ReactNode;
}

function AddCustomerRateDialog({
  slug,
  customerId,
  members,
  defaultCurrency,
  children,
}: AddCustomerRateDialogProps) {
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
      const result = await createCustomerBillingRate(slug, customerId, {
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
          <DialogTitle>Add Customer Rate Override</DialogTitle>
          <DialogDescription>
            Create a customer-specific billing rate override for a team member.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="customer-rate-member">Member</Label>
            <select
              id="customer-rate-member"
              value={memberId}
              onChange={(e) => setMemberId(e.target.value)}
              className="flex h-9 w-full rounded-md border border-olive-200 bg-white px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-olive-500 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-olive-800 dark:bg-olive-950 dark:placeholder:text-olive-400"
              required
            >
              <option value="">Select a member...</option>
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="customer-rate-hourly">Hourly Rate</Label>
            <Input
              id="customer-rate-hourly"
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
            <Label htmlFor="customer-rate-from">Effective From</Label>
            <Input
              id="customer-rate-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="customer-rate-to">
              Effective To{" "}
              <span className="font-normal text-olive-500">(optional)</span>
            </Label>
            <Input
              id="customer-rate-to"
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

// --- Edit Customer Rate Dialog ---

interface EditCustomerRateDialogProps {
  slug: string;
  customerId: string;
  rate: BillingRate;
  children: React.ReactNode;
}

function EditCustomerRateDialog({
  slug,
  customerId,
  rate,
  children,
}: EditCustomerRateDialogProps) {
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
      const result = await updateCustomerBillingRate(
        slug,
        customerId,
        rate.id,
        {
          currency,
          hourlyRate: parsedRate,
          effectiveFrom,
          effectiveTo: effectiveTo || undefined,
        },
      );

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
          <DialogTitle>Edit Customer Rate Override</DialogTitle>
          <DialogDescription>
            Update the customer billing rate for {rate.memberName}.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-customer-rate-hourly">Hourly Rate</Label>
            <Input
              id="edit-customer-rate-hourly"
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
            <Label htmlFor="edit-customer-rate-from">Effective From</Label>
            <Input
              id="edit-customer-rate-from"
              type="date"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-customer-rate-to">
              Effective To{" "}
              <span className="font-normal text-olive-500">(optional)</span>
            </Label>
            <Input
              id="edit-customer-rate-to"
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

// --- Delete Customer Rate Dialog ---

interface DeleteCustomerRateDialogProps {
  slug: string;
  customerId: string;
  rateId: string;
  memberName: string;
  children: React.ReactNode;
}

function DeleteCustomerRateDialog({
  slug,
  customerId,
  rateId,
  memberName,
  children,
}: DeleteCustomerRateDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);

    try {
      const result = await deleteCustomerBillingRate(slug, customerId, rateId);

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
            Delete Customer Rate Override
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Delete the customer billing rate override for {memberName}? This
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
