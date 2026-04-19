"use client";

import { useEffect, useState } from "react";
import useSWR from "swr";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { ModuleGate } from "@/components/module-gate";
import { fetchUnbilledDisbursementsAction } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { addDisbursementLines } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import type { UnbilledDisbursementsResponse } from "@/lib/api/legal-disbursements";

interface AddDisbursementsPickerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  invoiceId: string;
  slug: string;
  customerId: string;
  projectId: string | null;
  onSuccess: () => void;
}

function formatZAR(amount: number): string {
  return `R ${amount.toFixed(2)}`;
}

const CATEGORY_LABELS: Record<string, string> = {
  SHERIFF_FEES: "Sheriff Fees",
  COUNSEL_FEES: "Counsel Fees",
  SEARCH_FEES: "Search Fees",
  DEEDS_OFFICE_FEES: "Deeds Office Fees",
  COURT_FEES: "Court Fees",
  ADVOCATE_FEES: "Advocate Fees",
  EXPERT_WITNESS: "Expert Witness",
  TRAVEL: "Travel",
  OTHER: "Other",
};

export function AddDisbursementsPicker(props: AddDisbursementsPickerProps) {
  return (
    <ModuleGate module="disbursements">
      <AddDisbursementsPickerContent {...props} />
    </ModuleGate>
  );
}

function AddDisbursementsPickerContent({
  open,
  onOpenChange,
  invoiceId,
  slug,
  customerId,
  projectId,
  onSuccess,
}: AddDisbursementsPickerProps) {
  // In-memory selection state — survives close+reopen of the dialog while the
  // component instance stays mounted, then is pruned against the latest items
  // on re-open (see effect below). Not persisted across page navigations.
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isSubmitting, setIsSubmitting] = useState(false);

  const swrKey = open && projectId ? `unbilled-disbursements-${projectId}` : null;
  const { data, error, isLoading } = useSWR<UnbilledDisbursementsResponse | null>(
    swrKey,
    () => (projectId ? fetchUnbilledDisbursementsAction(projectId) : Promise.resolve(null)),
    { revalidateOnFocus: false }
  );

  const items = data?.items ?? [];

  // When the dialog re-opens, re-intersect selection with currently-available ids
  // (so stale ids from previously-selected items no longer present are pruned).
  useEffect(() => {
    if (!open) return;
    setSelectedIds((prev) => {
      if (prev.size === 0) return prev;
      const availableIds = new Set(items.map((i) => i.id));
      const next = new Set<string>();
      prev.forEach((id) => {
        if (availableIds.has(id)) next.add(id);
      });
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, items.length]);

  function toggleId(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    // Keep selection across reopens in same session; only clear on successful submit.
  }

  async function handleSubmit() {
    if (selectedIds.size === 0) return;
    setIsSubmitting(true);
    try {
      const result = await addDisbursementLines(
        slug,
        invoiceId,
        customerId,
        Array.from(selectedIds)
      );
      if (result.success) {
        toast.success(
          `Added ${selectedIds.size} disbursement line${selectedIds.size !== 1 ? "s" : ""} to invoice`
        );
        setSelectedIds(new Set());
        onSuccess();
      } else {
        toast.error(result.error ?? "Failed to add disbursement lines");
      }
    } catch (err) {
      console.error("Failed to add disbursement lines to invoice:", err);
      toast.error("An unexpected error occurred while adding disbursement lines");
    } finally {
      setIsSubmitting(false);
    }
  }

  const selectedTotal = items
    .filter((i) => selectedIds.has(i.id))
    .reduce((sum, i) => sum + i.amount + i.vatAmount, 0);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="flex max-h-[85vh] flex-col sm:max-w-2xl"
        data-testid="add-disbursements-picker"
      >
        <DialogHeader>
          <DialogTitle>Add Disbursements</DialogTitle>
          <DialogDescription>
            Select unbilled, approved disbursements to add as invoice lines.
          </DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {!projectId && (
            <p className="py-6 text-center text-sm text-slate-500" data-testid="picker-no-project">
              This invoice is not linked to a matter. Select a matter on the invoice to add
              disbursements.
            </p>
          )}

          {projectId && isLoading && (
            <div className="flex items-center gap-2 py-6 text-sm text-slate-500">
              <Loader2 className="size-4 animate-spin" />
              Loading unbilled disbursements&hellip;
            </div>
          )}

          {projectId && !isLoading && error && (
            <p className="py-6 text-sm text-red-600" data-testid="picker-error">
              Failed to load unbilled disbursements.
            </p>
          )}

          {projectId && !isLoading && !error && items.length === 0 && (
            <p className="py-6 text-center text-sm text-slate-500" data-testid="picker-empty">
              No unbilled approved disbursements for this matter.
            </p>
          )}

          {projectId && !isLoading && !error && items.length > 0 && (
            <table className="w-full text-sm" data-testid="picker-table">
              <thead className="border-b border-slate-200 text-left text-xs text-slate-500 uppercase dark:border-slate-800">
                <tr>
                  <th className="w-8 py-2"></th>
                  <th className="py-2">Date</th>
                  <th className="py-2">Category</th>
                  <th className="py-2">Description</th>
                  <th className="py-2 text-right">Amount (incl VAT)</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const isChecked = selectedIds.has(item.id);
                  const inclVat = item.amount + item.vatAmount;
                  return (
                    <tr
                      key={item.id}
                      className="border-b border-slate-100 dark:border-slate-800"
                      data-testid={`picker-row-${item.id}`}
                    >
                      <td className="py-2">
                        <Checkbox
                          checked={isChecked}
                          onCheckedChange={() => toggleId(item.id)}
                          aria-label={`Select ${item.description}`}
                          data-testid={`picker-checkbox-${item.id}`}
                        />
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {item.incurredDate}
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        {CATEGORY_LABELS[item.category] ?? item.category}
                      </td>
                      <td className="py-2 text-slate-700 dark:text-slate-300">
                        <span>{item.description}</span>
                        {item.supplierName && (
                          <span className="ml-2 text-xs text-slate-500">
                            &middot; {item.supplierName}
                          </span>
                        )}
                      </td>
                      <td className="py-2 text-right font-mono text-slate-900 tabular-nums dark:text-slate-100">
                        {formatZAR(inclVat)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <DialogFooter className="flex items-center justify-between gap-4 sm:justify-between">
          <div className="text-sm text-slate-600 dark:text-slate-400">
            {selectedIds.size > 0 ? (
              <>
                <span
                  className="font-medium text-slate-900 dark:text-slate-100"
                  data-testid="picker-selected-count"
                >
                  {selectedIds.size}
                </span>{" "}
                selected &middot;{" "}
                <span className="font-medium text-slate-900 dark:text-slate-100">
                  {formatZAR(selectedTotal)}
                </span>
              </>
            ) : (
              "Select disbursements to add"
            )}
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="plain"
              onClick={() => handleOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={selectedIds.size === 0 || isSubmitting}
              data-testid="picker-submit"
            >
              {isSubmitting ? "Adding..." : "Add to Invoice"}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
