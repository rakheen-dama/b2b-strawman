"use client";

import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Landmark, Loader2, AlertTriangle } from "lucide-react";
import { todayInSA } from "@b2mash/shared/date";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { payUnidentifiedDepositToFund } from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { payToFundSchema, type PayToFundFormData } from "@/lib/schemas/trust";
import type { UnidentifiedDeposit } from "@/lib/types";
import { useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { UnsettledSubmitNotice } from "./unsettled-submit-notice";

interface PayToFundDialogProps {
  /** A held item with no pending allocation or payment. */
  item: UnidentifiedDeposit;
  currency: string;
}

function defaultValues(): PayToFundFormData {
  return { reference: "", transactionDate: todayInSA(), notes: "" };
}

function heldFor(days: number) {
  return days === 1 ? "Held for 1 day" : `Held for ${days} days`;
}

/**
 * Pay an unidentified deposit to the Legal Practitioners' Fidelity Fund (Phase 92 647D, ADR-376,
 * §16.3.6, section 87(4)). The request saves a pending payment another member must approve. Shows
 * how long the item has been held and, under one year, warns that unclaimed trust money is
 * ordinarily paid over only after the period the Act sets — a warning, never a block: Kazi does not
 * enforce when an item may be paid over (OL-4). Owns its trigger: a plain button beside "Allocate"
 * (never an adjacent `asChild` trigger — Dialog Trigger Composition).
 */
export function PayToFundDialog({ item, currency }: PayToFundDialogProps) {
  const idempotency = useIdempotencyKey(["payUnidentifiedDepositToFund", item.id]);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Radix only tracks a DialogTrigger, so the owned button restores focus itself on close.
  const triggerRef = useRef<HTMLButtonElement>(null);

  const form = useForm<PayToFundFormData>({
    resolver: zodResolver(payToFundSchema),
    defaultValues: defaultValues(),
  });

  function handleOpenChange(next: boolean) {
    // Escape / overlay / X while a request is in flight: stay open so its outcome is shown.
    if (!next && isSubmitting) return;
    setDialogOpen(next);
  }

  function setDialogOpen(next: boolean) {
    setOpen(next);
    if (next) {
      // Fresh defaults on every open (today's date as of the opening, not of the first render).
      setError(null);
      form.reset(defaultValues());
    }
  }

  async function handleSubmit(values: PayToFundFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await idempotency.run(
        ["payUnidentifiedDepositToFund", item.id, values],
        (idempotencyKey) => payUnidentifiedDepositToFund(item.id, values, idempotencyKey)
      );
      if (!result.success) {
        setError(result.error ?? "Failed to request the payment to the Fund");
        return;
      }
      setDialogOpen(false);
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        ref={triggerRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setDialogOpen(true)}
        data-testid={`pay-to-fund-btn-${item.id}`}
      >
        <Landmark className="mr-1 size-3" />
        Pay to the Fund
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent
          className="sm:max-w-lg"
          data-testid="pay-to-fund-dialog"
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            triggerRef.current?.focus();
          }}
        >
          <DialogHeader>
            <DialogTitle>Pay to the Fidelity Fund</DialogTitle>
            <DialogDescription>
              Received {formatLocalDate(item.receivedDate)} — {item.bankDescription} —{" "}
              {formatCurrency(item.amount, currency)}. Another member must approve the payment.
            </DialogDescription>
          </DialogHeader>

          <p className="text-sm font-medium" data-testid="pay-to-fund-age">
            {heldFor(item.ageDays)}
          </p>
          {!item.olderThanOneYear && (
            // A warning, not an error: role="status" (the Alert primitive defaults to "alert").
            <Alert variant="warning" role="status" data-testid="pay-to-fund-under-one-year-warning">
              <AlertTriangle />
              <AlertDescription>
                This deposit has been held for less than a year. Unclaimed trust money is ordinarily
                paid to the Legal Practitioners&apos; Fidelity Fund only after the period the Act
                sets — check before you request this payment.
              </AlertDescription>
            </Alert>
          )}

          <Form {...form}>
            <form noValidate onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="reference"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Payment reference</FormLabel>
                    <FormControl>
                      <Input
                        placeholder="e.g. the EFT reference"
                        maxLength={200}
                        data-testid="pay-to-fund-reference"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="transactionDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Payment date</FormLabel>
                    <FormControl>
                      <Input
                        type="date"
                        min={item.receivedDate}
                        max={todayInSA()}
                        data-testid="pay-to-fund-date"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="notes"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Notes (optional)</FormLabel>
                    <FormControl>
                      <Textarea maxLength={2000} data-testid="pay-to-fund-notes" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <UnsettledSubmitNotice show={idempotency.unsettled} />
              {error && (
                <p role="alert" className="text-sm text-red-600 dark:text-red-400">
                  {error}
                </p>
              )}

              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => handleOpenChange(false)}
                  disabled={isSubmitting}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={isSubmitting} data-testid="pay-to-fund-submit">
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Requesting...
                    </>
                  ) : (
                    "Request payment"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>
    </>
  );
}
