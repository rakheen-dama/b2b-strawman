"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { todayInSA } from "@b2mash/shared/date";
import { formatCurrency } from "@/lib/format";
import { withdrawInvestmentSchema, type WithdrawInvestmentFormData } from "@/lib/schemas/trust";
import { withdrawInvestment } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";

interface WithdrawInvestmentDialogProps {
  investmentId: string;
  /** What the withdrawal returns: the investment account's balance for this investment. */
  balance: number;
  currency: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function getDefaultValues(): WithdrawInvestmentFormData {
  return { withdrawalDate: todayInSA() };
}

/**
 * 648A: the whole investment returns to the trust banking account it was placed from. The amount
 * is derived (capital plus the interest recorded on it); only the date is entered.
 */
export function WithdrawInvestmentDialog({
  investmentId,
  balance,
  currency,
  open,
  onOpenChange,
  onSuccess,
}: WithdrawInvestmentDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<WithdrawInvestmentFormData>({
    resolver: zodResolver(withdrawInvestmentSchema),
    defaultValues: getDefaultValues(),
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset(getDefaultValues());
      setError(null);
    }
  }

  async function handleSubmit(data: WithdrawInvestmentFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await withdrawInvestment(investmentId, data);
      if (result.success) {
        onSuccess();
        router.refresh();
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to withdraw investment");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md" data-testid="withdraw-investment-dialog">
        <DialogHeader>
          <DialogTitle>Withdraw Investment</DialogTitle>
          <DialogDescription>
            The whole investment returns to the trust banking account it was placed from.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
              <div className="flex justify-between text-sm font-semibold">
                <span className="text-slate-950 dark:text-slate-50">Returns to trust</span>
                <span
                  className="font-mono text-slate-950 tabular-nums dark:text-slate-50"
                  data-testid="withdrawal-amount"
                >
                  {formatCurrency(balance, currency)}
                </span>
              </div>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                The capital and any section 86(4) interest posted to the books (section 86(3)
                interest belongs to the Legal Practitioners&apos; Fidelity Fund).
              </p>
            </div>

            <FormField
              control={form.control}
              name="withdrawalDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Withdrawal date</FormLabel>
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
                    Withdrawing...
                  </>
                ) : (
                  "Confirm Withdrawal"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
