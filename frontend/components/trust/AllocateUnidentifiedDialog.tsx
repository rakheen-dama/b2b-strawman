"use client";

import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, UserCheck } from "lucide-react";
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
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { allocateUnidentifiedDeposit } from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { allocateUnidentifiedSchema, type AllocateUnidentifiedFormData } from "@/lib/schemas/trust";
import type { UnidentifiedDeposit } from "@/lib/types";
import {
  TrustCustomerPicker,
  TrustMatterPicker,
  type TrustPickerCustomer,
} from "./TrustEntityPickers";
import { useIdempotencyKey } from "@/hooks/use-idempotency-key";

interface AllocateUnidentifiedDialogProps {
  /** A held item with no pending allocation or payment. */
  item: UnidentifiedDeposit;
  /** The active client roster, server-fetched by the page. */
  customers: TrustPickerCustomer[];
  currency: string;
}

function defaultValues(): AllocateUnidentifiedFormData {
  return { customerId: "", projectId: "", allocationDate: todayInSA(), notes: "" };
}

/**
 * Allocate an unidentified deposit to a client and matter (Phase 92 647D, ADR-376, §16.3.6). The
 * request saves a pending entry; nothing moves to the client's ledger until another member
 * approves it on the transactions page. The allocation is dated the day the owner was identified
 * (default today, from the received date to today — the server is the authority); the client's
 * ledger line says when the money was received. Owns its trigger: a plain button beside "Pay to
 * the Fund" (never an adjacent `asChild` trigger — Dialog Trigger Composition).
 */
export function AllocateUnidentifiedDialog({
  item,
  customers,
  currency,
}: AllocateUnidentifiedDialogProps) {
  const idempotency = useIdempotencyKey();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Radix only tracks a DialogTrigger, so the owned button restores focus itself on close.
  const triggerRef = useRef<HTMLButtonElement>(null);

  const form = useForm<AllocateUnidentifiedFormData>({
    resolver: zodResolver(allocateUnidentifiedSchema),
    defaultValues: defaultValues(),
  });
  const customerId = useWatch({ control: form.control, name: "customerId" });

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

  async function handleSubmit(values: AllocateUnidentifiedFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await idempotency.run(
        ["allocateUnidentifiedDeposit", item.id, values],
        (idempotencyKey) => allocateUnidentifiedDeposit(item.id, values, idempotencyKey)
      );
      if (!result.success) {
        setError(result.error ?? "Failed to request the allocation");
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
        data-testid={`allocate-btn-${item.id}`}
      >
        <UserCheck className="mr-1 size-3" />
        Allocate
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent
          className="sm:max-w-lg"
          data-testid="allocate-unidentified-dialog"
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            triggerRef.current?.focus();
          }}
        >
          <DialogHeader>
            <DialogTitle>Allocate to a client</DialogTitle>
            <DialogDescription>
              Received {formatLocalDate(item.receivedDate)} — {item.bankDescription} —{" "}
              {formatCurrency(item.amount, currency)}. Another member must approve the allocation
              before the money moves to the client&apos;s ledger.
            </DialogDescription>
          </DialogHeader>

          <Form {...form}>
            <form noValidate onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="customerId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Client</FormLabel>
                    <TrustCustomerPicker
                      field={field}
                      customers={customers}
                      triggerTestId="allocate-customer-trigger"
                    />
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="projectId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Matter</FormLabel>
                    <TrustMatterPicker
                      field={field}
                      customerId={customerId}
                      triggerTestId="allocate-matter-trigger"
                    />
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="allocationDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Allocation date</FormLabel>
                    <FormControl>
                      <Input
                        type="date"
                        min={item.receivedDate}
                        max={todayInSA()}
                        data-testid="allocate-date"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      The day the owner was identified. The client&apos;s ledger shows the date the
                      deposit was received.
                    </FormDescription>
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
                      <Textarea
                        placeholder="How the owner was identified"
                        maxLength={2000}
                        data-testid="allocate-notes"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

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
                <Button type="submit" disabled={isSubmitting} data-testid="allocate-submit">
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Requesting...
                    </>
                  ) : (
                    "Request allocation"
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
