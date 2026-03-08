"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatCurrency } from "@/lib/format";
import { loadPreviewAction } from "@/app/(app)/org/[slug]/invoices/billing-runs/new/actions";
import type { BillingRunItem } from "@/lib/api/billing-runs";

interface CustomerSelectionStepProps {
  slug: string;
  billingRunId: string;
  currency: string;
  onBack: () => void;
  onNext: () => void;
}

export function CustomerSelectionStep({
  slug,
  billingRunId,
  currency,
  onBack,
  onNext,
}: CustomerSelectionStepProps) {
  const [items, setItems] = useState<BillingRunItem[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setError(null);
      try {
        const result = await loadPreviewAction(slug, billingRunId);
        if (cancelled) return;
        if (result.success && result.preview) {
          setItems(result.preview.items);
          // Select all customers by default
          setSelectedIds(
            new Set(result.preview.items.map((item) => item.id)),
          );
        } else {
          setError(result.error ?? "Failed to load customer data.");
        }
      } catch {
        if (!cancelled) {
          setError("An unexpected error occurred.");
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [slug, billingRunId]);

  function toggleCustomer(itemId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  }

  function toggleAll() {
    if (selectedIds.size === items.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(items.map((item) => item.id)));
    }
  }

  const selectedTotal = items
    .filter((item) => selectedIds.has(item.id))
    .reduce((sum, item) => sum + item.totalUnbilledAmount, 0);

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <div className="space-y-4">
          <div className="h-6 w-48 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-10 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-10 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-10 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
        <div className="mt-4">
          <Button variant="outline" onClick={onBack}>
            Back
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="p-6">
        <h2 className="mb-4 text-lg font-semibold text-slate-950 dark:text-slate-50">
          Select Customers
        </h2>

        {items.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No customers with unbilled work found for this period.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left dark:border-slate-700">
                  <th className="pb-3 pr-4">
                    <Checkbox
                      checked={selectedIds.size === items.length}
                      onCheckedChange={toggleAll}
                      aria-label="Select all customers"
                    />
                  </th>
                  <th className="pb-3 pr-4 font-medium text-slate-500 dark:text-slate-400">
                    Customer
                  </th>
                  <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                    Unbilled Time
                  </th>
                  <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                    Unbilled Expenses
                  </th>
                  <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                    Total
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr
                    key={item.id}
                    className="border-b border-slate-100 dark:border-slate-800"
                  >
                    <td className="py-3 pr-4">
                      <Checkbox
                        checked={selectedIds.has(item.id)}
                        onCheckedChange={() => toggleCustomer(item.id)}
                        aria-label={`Select ${item.customerName}`}
                      />
                    </td>
                    <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">
                      {item.customerName}
                    </td>
                    <td className="py-3 pr-4 text-right text-slate-600 dark:text-slate-400">
                      {formatCurrency(item.unbilledTimeAmount, currency)}
                    </td>
                    <td className="py-3 pr-4 text-right text-slate-600 dark:text-slate-400">
                      {formatCurrency(item.unbilledExpenseAmount, currency)}
                    </td>
                    <td className="py-3 text-right font-medium text-slate-950 dark:text-slate-50">
                      {formatCurrency(item.totalUnbilledAmount, currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Summary Bar */}
      <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-4 dark:border-slate-700 dark:bg-slate-900">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          <span className="font-medium text-slate-950 dark:text-slate-50">
            {selectedIds.size}
          </span>{" "}
          {selectedIds.size === 1 ? "customer" : "customers"} selected,{" "}
          <span className="font-medium text-slate-950 dark:text-slate-50">
            {formatCurrency(selectedTotal, currency)}
          </span>{" "}
          total
        </p>
        <div className="flex gap-3">
          <Button variant="outline" onClick={onBack}>
            Back
          </Button>
          <Button
            variant="default"
            onClick={onNext}
            disabled={selectedIds.size === 0}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
