"use client";

import { useState } from "react";
import useSWR from "swr";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { fetchApprovedTrustDisbursementPayments } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import type { TrustTransaction } from "@/lib/types";

interface TrustTransactionLinkDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
  onSelect: (transaction: TrustTransaction) => void;
}

function formatZAR(amount: number): string {
  return `R ${amount.toFixed(2)}`;
}

export function TrustTransactionLinkDialog({
  open,
  onOpenChange,
  projectId,
  onSelect,
}: TrustTransactionLinkDialogProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const swrKey = open && projectId ? `approved-trust-disb-${projectId}` : null;

  const { data, error, isLoading } = useSWR<TrustTransaction[]>(
    swrKey,
    () => fetchApprovedTrustDisbursementPayments(projectId),
    { revalidateOnFocus: false }
  );

  const transactions = data ?? [];
  const selected = transactions.find((t) => t.id === selectedId) ?? null;

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      setSelectedId(null);
    }
  }

  function handleConfirm() {
    if (!selected) return;
    onSelect(selected);
    setSelectedId(null);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-2xl"
        data-testid="trust-transaction-link-dialog"
      >
        <DialogHeader>
          <DialogTitle>Link Trust Transaction</DialogTitle>
          <DialogDescription>
            Select an approved trust disbursement payment from this matter.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[50vh] overflow-y-auto">
          {isLoading && (
            <div
              className="flex items-center gap-2 py-8 text-sm text-slate-500"
              data-testid="trust-tx-loading"
            >
              <Loader2 className="size-4 animate-spin" />
              Loading trust transactions&hellip;
            </div>
          )}

          {!isLoading && error && (
            <p className="py-6 text-sm text-red-600" data-testid="trust-tx-error">
              Failed to load trust transactions.
            </p>
          )}

          {!isLoading && !error && transactions.length === 0 && (
            <p
              className="py-6 text-center text-sm text-slate-500"
              data-testid="trust-tx-empty"
            >
              No approved disbursement-payment trust transactions found for this matter.
            </p>
          )}

          {!isLoading && !error && transactions.length > 0 && (
            <table className="w-full text-sm" data-testid="trust-tx-table">
              <thead className="border-b border-slate-200 text-left text-xs uppercase text-slate-500 dark:border-slate-800">
                <tr>
                  <th className="w-8 py-2"></th>
                  <th className="py-2">Date</th>
                  <th className="py-2">Amount</th>
                  <th className="py-2">Supplier / Reference</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx) => {
                  const isSelected = tx.id === selectedId;
                  return (
                    <tr
                      key={tx.id}
                      className="cursor-pointer border-b border-slate-100 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                      onClick={() => setSelectedId(tx.id)}
                      data-testid={`trust-tx-row-${tx.id}`}
                    >
                      <td className="py-2">
                        <input
                          type="radio"
                          name="trust-tx"
                          checked={isSelected}
                          onChange={() => setSelectedId(tx.id)}
                          aria-label={`Select trust transaction ${tx.reference}`}
                        />
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {tx.transactionDate}
                      </td>
                      <td className="py-2 font-mono tabular-nums text-slate-900 dark:text-slate-100">
                        {formatZAR(tx.amount)}
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        <span className="font-medium">{tx.reference}</span>
                        {tx.description && (
                          <span className="ml-2 text-xs text-slate-500">
                            &middot; {tx.description}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            disabled={!selected}
            data-testid="trust-tx-link-confirm"
          >
            Link Transaction
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
