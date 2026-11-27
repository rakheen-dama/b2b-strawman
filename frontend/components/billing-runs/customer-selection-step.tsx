"use client";

import { useEffect, useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrency } from "@/lib/format";
import { loadPreviewAction } from "@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-run-actions";
import type { BillingRunItem } from "@/lib/api/billing-runs";

interface CustomerSelectionStepProps {
  slug: string;
  billingRunId: string;
  currency: string;
  onBack: () => void;
  onNext: (selectedItems: BillingRunItem[]) => void;
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
          setSelectedIds(new Set(result.preview.items.map((item) => item.id)));
        } else {
          setError(result.error ?? "Failed to load client data.");
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
        <p role="alert" className="text-destructive text-sm">
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
          Select Clients
        </h2>

        {items.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No clients with unbilled work found for this period.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>
                  <Checkbox
                    checked={selectedIds.size === items.length}
                    onCheckedChange={toggleAll}
                    aria-label="Select all clients"
                  />
                </TableHead>
                <TableHead>Client</TableHead>
                <TableHead className="text-right">Unbilled Time</TableHead>
                <TableHead className="text-right">Unbilled Disbursements</TableHead>
                <TableHead className="text-right">Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <Checkbox
                      checked={selectedIds.has(item.id)}
                      onCheckedChange={() => toggleCustomer(item.id)}
                      aria-label={`Select ${item.clientName}`}
                    />
                  </TableCell>
                  <TableCell className="font-medium whitespace-normal">{item.clientName}</TableCell>
                  <TableCell className="text-muted-foreground text-right">
                    {formatCurrency(item.unbilledTimeAmount, currency)}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-right">
                    {formatCurrency(item.unbilledExpenseAmount, currency)}
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    {formatCurrency(item.totalUnbilledAmount, currency)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      {/* Summary Bar */}
      <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-4 dark:border-slate-700 dark:bg-slate-900">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          <span className="font-medium text-slate-950 dark:text-slate-50">{selectedIds.size}</span>{" "}
          {selectedIds.size === 1 ? "client" : "clients"} selected,{" "}
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
            onClick={() => onNext(items.filter((item) => selectedIds.has(item.id)))}
            disabled={selectedIds.size === 0}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
