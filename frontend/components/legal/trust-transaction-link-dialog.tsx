"use client";

import { useState } from "react";
import useSWR, { SWRConfig } from "swr";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@b2mash/ui/button";
import { fetchApprovedTrustDisbursementPayments } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { formatCurrency } from "@/lib/format";
import type { TrustTransaction } from "@/lib/types";

interface TrustTransactionLinkDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
  onSelect: (transaction: TrustTransaction) => void;
}

// One SWR cache per open: the picker mounts only while `open` (below), so it always
// starts from the loading state and a fresh fetch. A shared cache could still hold a payment linked
// since the last open, and SWR would paint it (revalidating cached data is deferred a frame and
// deduped for 2 s). The backend does not reject a second link, so a used payment must never be
// offered (A14).
const freshCachePerOpen = { provider: () => new Map() };

export function TrustTransactionLinkDialog({
  open,
  onOpenChange,
  projectId,
  onSelect,
}: TrustTransactionLinkDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl" data-testid="trust-transaction-link-dialog">
        <DialogHeader>
          <DialogTitle>Link Trust Transaction</DialogTitle>
          <DialogDescription>Select an approved trust payment on this matter.</DialogDescription>
        </DialogHeader>

        {/* Mounted only while open: if an exit animation keeps DialogContent mounted, an
            immediate re-open must still start a fresh cache rather than reuse the last one. */}
        {open && (
          <SWRConfig value={freshCachePerOpen}>
            <TrustTransactionPicker
              projectId={projectId}
              onSelect={onSelect}
              onCancel={() => onOpenChange(false)}
            />
          </SWRConfig>
        )}
      </DialogContent>
    </Dialog>
  );
}

interface TrustTransactionPickerProps {
  projectId: string;
  onSelect: (transaction: TrustTransaction) => void;
  onCancel: () => void;
}

function TrustTransactionPicker({ projectId, onSelect, onCancel }: TrustTransactionPickerProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data, error, isLoading } = useSWR<TrustTransaction[]>(
    projectId ? `approved-trust-disb-${projectId}` : null,
    () => fetchApprovedTrustDisbursementPayments(projectId),
    { revalidateOnFocus: false }
  );

  const transactions = data ?? [];
  const selected = transactions.find((t) => t.id === selectedId) ?? null;

  function handleConfirm() {
    if (!selected) return;
    onSelect(selected);
    setSelectedId(null);
  }

  return (
    <>
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
          <p className="py-6 text-center text-sm text-slate-500" data-testid="trust-tx-empty">
            No approved trust payments on this matter yet.
          </p>
        )}

        {!isLoading && !error && transactions.length > 0 && (
          <table className="w-full text-sm" data-testid="trust-tx-table">
            <thead className="border-b border-slate-200 text-left text-xs text-slate-500 uppercase dark:border-slate-800">
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
                    className="border-b border-slate-100 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                    data-testid={`trust-tx-row-${tx.id}`}
                  >
                    <td className="py-2">
                      <input
                        type="radio"
                        name="trust-tx"
                        checked={isSelected}
                        onChange={() => setSelectedId(tx.id)}
                        aria-label={`Select trust transaction ${tx.reference}`}
                        data-testid={`trust-tx-radio-${tx.id}`}
                      />
                    </td>
                    <td className="py-2 text-slate-700 dark:text-slate-300">
                      {tx.transactionDate}
                    </td>
                    <td className="py-2 font-mono text-slate-900 tabular-nums dark:text-slate-100">
                      {formatCurrency(tx.amount, "ZAR")}
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
        <Button type="button" variant="plain" onClick={onCancel}>
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
    </>
  );
}
