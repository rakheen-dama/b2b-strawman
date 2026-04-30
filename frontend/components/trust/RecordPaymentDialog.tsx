"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Loader2 } from "lucide-react";
import { recordPayment } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { recordPaymentSchema, type RecordPaymentFormData } from "@/lib/schemas/trust";
import {
  TrustCustomerPicker,
  TrustMatterPicker,
  type TrustPickerCustomer,
} from "./TrustEntityPickers";

interface RecordPaymentDialogProps {
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
  /** Pre-fetched customer roster for the picker. */
  customers: TrustPickerCustomer[];
  /** When set, locks the customer picker to this id. */
  defaultCustomerId?: string;
  /** When set, locks the matter picker to this id. */
  defaultProjectId?: string;
}

function getDefaultValues(defaultCustomerId?: string, defaultProjectId?: string) {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return {
    customerId: defaultCustomerId ?? "",
    projectId: defaultProjectId ?? "",
    amount: 0,
    reference: "",
    description: "",
    transactionDate: local.toISOString().slice(0, 10),
  };
}

export function RecordPaymentDialog({
  accountId,
  open,
  onOpenChange,
  onSuccess,
  customers,
  defaultCustomerId,
  defaultProjectId,
}: RecordPaymentDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<RecordPaymentFormData>({
    resolver: zodResolver(recordPaymentSchema),
    defaultValues: getDefaultValues(defaultCustomerId, defaultProjectId),
  });

  const customerId = form.watch("customerId");

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset(getDefaultValues(defaultCustomerId, defaultProjectId));
      setError(null);
    }
  }

  async function handleSubmit(data: RecordPaymentFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await recordPayment(accountId, data);
      if (result.success) {
        onSuccess?.();
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to record payment");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Record Payment</DialogTitle>
          <DialogDescription>
            Record a trust payment on behalf of a client. Payments above the approval threshold will
            require approval.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="customerId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Client</FormLabel>
                  <TrustCustomerPicker
                    field={field}
                    customers={customers}
                    defaultCustomerId={defaultCustomerId}
                    triggerTestId="trust-payment-customer-trigger"
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
                  <FormLabel>Matter (Optional)</FormLabel>
                  <TrustMatterPicker
                    field={field}
                    customerId={customerId}
                    defaultProjectId={defaultProjectId}
                    triggerTestId="trust-payment-matter-trigger"
                  />
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Amount</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="0.00"
                      {...field}
                      onChange={(e) => field.onChange(parseFloat(e.target.value) || 0)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reference</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. PAY/2026/001" maxLength={200} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description (Optional)</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Payee details and payment purpose"
                      maxLength={2000}
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
                  <FormLabel>Transaction Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Recording...
                  </>
                ) : (
                  "Record Payment"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
