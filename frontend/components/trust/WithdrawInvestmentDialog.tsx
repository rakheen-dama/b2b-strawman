"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatCurrency } from "@/lib/format";
import { withdrawInvestment } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";

interface WithdrawInvestmentDialogProps {
  investmentId: string;
  principal: number;
  interestEarned: number;
  currency: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function WithdrawInvestmentDialog({
  investmentId,
  principal,
  interestEarned,
  currency,
  open,
  onOpenChange,
  onSuccess,
}: WithdrawInvestmentDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      setError(null);
    }
  }

  async function handleWithdraw() {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await withdrawInvestment(investmentId);
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

  const totalWithdrawal = principal + interestEarned;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-md"
        data-testid="withdraw-investment-dialog"
      >
        <DialogHeader>
          <DialogTitle>Withdraw Investment</DialogTitle>
          <DialogDescription>
            Confirm withdrawal of this investment back to the trust account.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-700">
          <div className="flex justify-between text-sm">
            <span className="text-slate-600 dark:text-slate-400">
              Principal
            </span>
            <span className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
              {formatCurrency(principal, currency)}
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-slate-600 dark:text-slate-400">
              Interest Earned
            </span>
            <span className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
              {formatCurrency(interestEarned, currency)}
            </span>
          </div>
          <div className="border-t border-slate-200 pt-2 dark:border-slate-700">
            <div className="flex justify-between text-sm font-semibold">
              <span className="text-slate-950 dark:text-slate-50">
                Total Withdrawal
              </span>
              <span className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                {formatCurrency(totalWithdrawal, currency)}
              </span>
            </div>
          </div>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button onClick={handleWithdraw} disabled={isSubmitting}>
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
      </DialogContent>
    </Dialog>
  );
}
