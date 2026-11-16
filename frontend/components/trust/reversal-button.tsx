"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { RotateCcw, Loader2 } from "lucide-react";
import { reverseTransaction } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { reversalReasonSchema, type ReversalReasonFormData } from "@/lib/schemas/trust";

interface ReversalButtonProps {
  transactionId: string;
  /** The row's own date (YYYY-MM-DD): the earliest reversal date the date picker offers. */
  transactionDate?: string;
  /** A deposit's reversal takes effect only once another member approves it. */
  needsApproval?: boolean;
}

export function ReversalButton({
  transactionId,
  transactionDate,
  needsApproval = false,
}: ReversalButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<ReversalReasonFormData>({
    resolver: zodResolver(reversalReasonSchema),
    defaultValues: {
      reason: "",
      reversalDate: "",
    },
  });

  function handleOpenChange(newOpen: boolean) {
    setDialogOpen(newOpen);
    if (!newOpen) {
      form.reset();
      setError(null);
    }
  }

  async function handleSubmit(data: ReversalReasonFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await reverseTransaction(transactionId, data);
      if (result.success) {
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to reverse transaction");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onClick={() => setDialogOpen(true)}
        data-testid="reverse-button"
      >
        <RotateCcw className="mr-1 size-3" />
        Reverse
      </Button>

      <Dialog open={dialogOpen} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Reverse Transaction</DialogTitle>
            <DialogDescription>
              {needsApproval
                ? "This will create a reversal entry. A deposit reversal must be approved by another member before it takes effect."
                : "This will create a reversal entry. Please provide a reason."}
            </DialogDescription>
          </DialogHeader>

          <Form {...form}>
            <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Reason</FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder="Reason for reversal"
                        maxLength={500}
                        data-testid="reversal-reason-input"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="reversalDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Reversal date (optional)</FormLabel>
                    <FormControl>
                      <Input
                        type="date"
                        min={transactionDate}
                        data-testid="reversal-date-input"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      Leave blank for today. Use the date the bank shows the reversal.
                    </FormDescription>
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
                <Button
                  type="submit"
                  variant="destructive"
                  disabled={isSubmitting}
                  data-testid="confirm-reverse-button"
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Reversing...
                    </>
                  ) : (
                    "Reverse Transaction"
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
