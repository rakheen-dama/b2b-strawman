"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
} from "@/components/ui/sheet";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { formatCurrency } from "@/lib/format";
import {
  generateAction,
  getItemsAction,
  batchApproveAction,
} from "@/app/(app)/org/[slug]/invoices/billing-runs/new/actions";
import { updateInvoice } from "@/app/(app)/org/[slug]/invoices/actions";
import type { BillingRunItem } from "@/lib/api/billing-runs";

const PAYMENT_TERMS_OPTIONS = [
  { value: "DUE_ON_RECEIPT", label: "Due on Receipt" },
  { value: "NET_7", label: "Net 7" },
  { value: "NET_14", label: "Net 14" },
  { value: "NET_30", label: "Net 30" },
  { value: "NET_60", label: "Net 60" },
];

interface ReviewDraftsStepProps {
  slug: string;
  billingRunId: string;
  currency: string;
  onBack: () => void;
  onNext: () => void;
}

export function ReviewDraftsStep({
  slug,
  billingRunId,
  currency,
  onBack,
  onNext,
}: ReviewDraftsStepProps) {
  const [items, setItems] = useState<BillingRunItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isApproving, setIsApproving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedItem, setSelectedItem] = useState<BillingRunItem | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  // Inline editor state
  const [editDueDate, setEditDueDate] = useState("");
  const [editPaymentTerms, setEditPaymentTerms] = useState("");
  const [editNotes, setEditNotes] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  // Batch set state
  const [batchDueDate, setBatchDueDate] = useState("");
  const [batchPaymentTerms, setBatchPaymentTerms] = useState("");
  const [isBatchUpdating, setIsBatchUpdating] = useState(false);

  // Guard against duplicate generation when navigating back to this step
  const hasGenerated = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function generateAndLoad() {
      setError(null);

      // If already generated, just reload items without re-generating
      if (hasGenerated.current) {
        setIsLoading(true);
        try {
          const itemsResult = await getItemsAction(billingRunId);
          if (cancelled) return;
          if (itemsResult.success && itemsResult.items) {
            setItems(itemsResult.items);
          } else {
            setError(itemsResult.error ?? "Failed to load items.");
          }
        } catch {
          if (!cancelled) {
            setError("An unexpected error occurred.");
          }
        } finally {
          if (!cancelled) setIsLoading(false);
        }
        return;
      }

      setIsGenerating(true);
      try {
        const genResult = await generateAction(billingRunId);
        if (cancelled) return;
        if (!genResult.success) {
          setError(genResult.error ?? "Failed to generate invoices.");
          setIsGenerating(false);
          return;
        }
        hasGenerated.current = true;
        setIsGenerating(false);

        setIsLoading(true);
        const itemsResult = await getItemsAction(billingRunId);
        if (cancelled) return;
        if (itemsResult.success && itemsResult.items) {
          setItems(itemsResult.items);
        } else {
          setError(itemsResult.error ?? "Failed to load items.");
        }
      } catch {
        if (!cancelled) {
          setError("An unexpected error occurred.");
        }
      } finally {
        if (!cancelled) {
          setIsGenerating(false);
          setIsLoading(false);
        }
      }
    }

    generateAndLoad();
    return () => {
      cancelled = true;
    };
  }, [billingRunId]);

  const generatedItems = items.filter(
    (item) => item.status === "GENERATED" || item.status === "FAILED",
  );
  const draftItems = items.filter((item) => item.status === "GENERATED");
  const failedItems = items.filter((item) => item.status === "FAILED");
  const totalAmount = draftItems.reduce(
    (sum, item) => sum + item.totalUnbilledAmount,
    0,
  );

  function handleRowClick(item: BillingRunItem) {
    if (item.status !== "GENERATED" || !item.invoiceId) return;
    setSelectedItem(item);
    setEditDueDate("");
    setEditPaymentTerms("");
    setEditNotes("");
    setSheetOpen(true);
  }

  async function refreshItems() {
    const itemsResult = await getItemsAction(billingRunId);
    if (itemsResult.success && itemsResult.items) {
      setItems(itemsResult.items);
    }
  }

  async function handleSaveInvoice() {
    if (!selectedItem?.invoiceId) return;
    setIsSaving(true);
    try {
      const request: Record<string, string> = {};
      if (editDueDate) request.dueDate = editDueDate;
      if (editPaymentTerms) request.paymentTerms = editPaymentTerms;
      if (editNotes) request.notes = editNotes;

      const result = await updateInvoice(
        slug,
        selectedItem.invoiceId,
        selectedItem.customerId,
        request,
      );
      if (!result.success) {
        setError(result.error ?? "Failed to update invoice.");
      } else {
        setSheetOpen(false);
        await refreshItems();
      }
    } catch {
      setError("Failed to save invoice changes.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleBatchSetDueDate() {
    if (!batchDueDate) return;
    setIsBatchUpdating(true);
    try {
      const results = await Promise.allSettled(
        draftItems
          .filter((item) => item.invoiceId)
          .map((item) =>
            updateInvoice(slug, item.invoiceId!, item.customerId, {
              dueDate: batchDueDate,
            }),
          ),
      );
      const failures = results.filter((r) => r.status === "rejected");
      if (failures.length > 0) {
        setError(
          `Failed to set due date on ${failures.length} of ${results.length} invoices.`,
        );
      }
      await refreshItems();
    } catch {
      setError("Failed to batch set due date.");
    } finally {
      setIsBatchUpdating(false);
    }
  }

  async function handleBatchSetPaymentTerms() {
    if (!batchPaymentTerms) return;
    setIsBatchUpdating(true);
    try {
      const results = await Promise.allSettled(
        draftItems
          .filter((item) => item.invoiceId)
          .map((item) =>
            updateInvoice(slug, item.invoiceId!, item.customerId, {
              paymentTerms: batchPaymentTerms,
            }),
          ),
      );
      const failures = results.filter((r) => r.status === "rejected");
      if (failures.length > 0) {
        setError(
          `Failed to set payment terms on ${failures.length} of ${results.length} invoices.`,
        );
      }
      await refreshItems();
    } catch {
      setError("Failed to batch set payment terms.");
    } finally {
      setIsBatchUpdating(false);
    }
  }

  async function handleApproveAll() {
    setIsApproving(true);
    setError(null);
    try {
      const result = await batchApproveAction(billingRunId);
      if (result.success) {
        onNext();
      } else {
        setError(result.error ?? "Failed to approve invoices.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsApproving(false);
    }
  }

  if (isGenerating) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-800 dark:bg-slate-950">
        <p className="text-slate-500 dark:text-slate-400">
          Generating invoices...
        </p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-800 dark:bg-slate-950">
        <p className="text-slate-500 dark:text-slate-400">
          Loading draft invoices...
        </p>
      </div>
    );
  }

  if (error && items.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 dark:border-slate-800 dark:bg-slate-950">
        <p className="text-center text-red-600 dark:text-red-400">{error}</p>
        <div className="mt-6 flex justify-center">
          <Button variant="outline" onClick={onBack}>
            Back
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      )}

      {/* Batch Actions */}
      <div className="flex items-center gap-3">
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="outline" size="sm" disabled={isBatchUpdating}>
              Set Due Date for All
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-64">
            <div className="space-y-3">
              <label className="text-sm font-medium">Due Date</label>
              <input
                type="date"
                className="w-full rounded-md border border-slate-200 bg-transparent px-3 py-2 text-sm dark:border-slate-700"
                value={batchDueDate}
                onChange={(e) => setBatchDueDate(e.target.value)}
              />
              <Button
                size="sm"
                className="w-full"
                disabled={!batchDueDate || isBatchUpdating}
                onClick={handleBatchSetDueDate}
              >
                Apply
              </Button>
            </div>
          </PopoverContent>
        </Popover>

        <Popover>
          <PopoverTrigger asChild>
            <Button variant="outline" size="sm" disabled={isBatchUpdating}>
              Set Payment Terms for All
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-64">
            <div className="space-y-3">
              <label className="text-sm font-medium">Payment Terms</label>
              <Select
                value={batchPaymentTerms}
                onValueChange={setBatchPaymentTerms}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select terms" />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_TERMS_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                size="sm"
                className="w-full"
                disabled={!batchPaymentTerms || isBatchUpdating}
                onClick={handleBatchSetPaymentTerms}
              >
                Apply
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {/* Invoice Table */}
      <div className="rounded-lg border border-slate-200 dark:border-slate-800">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Customer
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Invoice #
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">
                Items
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">
                Total
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Status
              </th>
            </tr>
          </thead>
          <tbody>
            {generatedItems.map((item) => (
              <tr
                key={item.id}
                className={`border-b border-slate-100 last:border-b-0 dark:border-slate-800 ${
                  item.status === "GENERATED"
                    ? "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900"
                    : "bg-red-50 dark:bg-red-950/20"
                }`}
                onClick={() => handleRowClick(item)}
              >
                <td className="px-4 py-3">
                  <span
                    className={
                      item.status === "FAILED"
                        ? "text-red-600 dark:text-red-400"
                        : ""
                    }
                  >
                    {item.customerName}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                  {/* BillingRunItem has no invoiceNumber field — use truncated ID as placeholder */}
                  {item.invoiceId ? `INV-${item.invoiceId.slice(0, 8)}` : "—"}
                </td>
                <td className="px-4 py-3 text-right text-slate-500 dark:text-slate-400">
                  {item.unbilledTimeCount + item.unbilledExpenseCount}
                </td>
                <td className="px-4 py-3 text-right font-medium">
                  {formatCurrency(item.totalUnbilledAmount, currency)}
                </td>
                <td className="px-4 py-3">
                  {item.status === "GENERATED" ? (
                    <Badge variant="neutral">Draft</Badge>
                  ) : (
                    <Badge variant="destructive">Failed</Badge>
                  )}
                </td>
              </tr>
            ))}
            {generatedItems.length === 0 && (
              <tr>
                <td
                  colSpan={5}
                  className="px-4 py-8 text-center text-slate-500 dark:text-slate-400"
                >
                  No invoices generated.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Summary Bar */}
      <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
        <div className="flex gap-6 text-sm">
          <span className="text-slate-600 dark:text-slate-400">
            Drafts:{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {draftItems.length}
            </span>
          </span>
          {failedItems.length > 0 && (
            <span className="text-red-600 dark:text-red-400">
              Failed:{" "}
              <span className="font-medium">{failedItems.length}</span>
            </span>
          )}
          <span className="text-slate-600 dark:text-slate-400">
            Total:{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {formatCurrency(totalAmount, currency)}
            </span>
          </span>
        </div>
      </div>

      {/* Failed Items Detail */}
      {failedItems.length > 0 && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-900 dark:bg-red-950/20">
          <p className="mb-2 text-sm font-medium text-red-700 dark:text-red-400">
            Failed Invoices
          </p>
          <ul className="space-y-1 text-sm text-red-600 dark:text-red-400">
            {failedItems.map((item) => (
              <li key={item.id}>
                {item.customerName}:{" "}
                {item.failureReason ?? "Unknown error"}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Navigation */}
      <div className="flex justify-between">
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
        <Button
          onClick={handleApproveAll}
          disabled={isApproving || draftItems.length === 0}
        >
          {isApproving ? "Approving..." : "Approve All & Continue"}
        </Button>
      </div>

      {/* Inline Editor Sheet */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit Invoice</SheetTitle>
            <SheetDescription>
              {selectedItem?.customerName ?? "Invoice"}
            </SheetDescription>
          </SheetHeader>
          <div className="space-y-4 p-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Due Date</label>
              <input
                type="date"
                className="w-full rounded-md border border-slate-200 bg-transparent px-3 py-2 text-sm dark:border-slate-700"
                value={editDueDate}
                onChange={(e) => setEditDueDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Payment Terms</label>
              <Select
                value={editPaymentTerms}
                onValueChange={setEditPaymentTerms}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select terms" />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_TERMS_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Notes</label>
              <textarea
                className="w-full rounded-md border border-slate-200 bg-transparent px-3 py-2 text-sm dark:border-slate-700"
                rows={4}
                placeholder="Add notes..."
                value={editNotes}
                onChange={(e) => setEditNotes(e.target.value)}
              />
            </div>
          </div>
          <SheetFooter>
            <Button
              onClick={handleSaveInvoice}
              disabled={isSaving}
              className="w-full"
            >
              {isSaving ? "Saving..." : "Save"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </div>
  );
}
