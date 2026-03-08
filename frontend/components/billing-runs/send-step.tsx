"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { formatCurrency } from "@/lib/format";
import {
  getItemsAction,
  getBillingRunAction,
  batchSendAction,
} from "@/app/(app)/org/[slug]/invoices/billing-runs/new/actions";
import type {
  BillingRunItem,
  BatchOperationResult,
} from "@/lib/api/billing-runs";

interface SendStepProps {
  slug: string;
  billingRunId: string;
  currency: string;
  onBack: () => void;
}

export function SendStep({
  slug,
  billingRunId,
  currency,
  onBack,
}: SendStepProps) {
  const router = useRouter();
  const [items, setItems] = useState<BillingRunItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [sendResult, setSendResult] = useState<BatchOperationResult | null>(
    null,
  );

  useEffect(() => {
    let cancelled = false;

    async function loadApprovedItems() {
      setIsLoading(true);
      setError(null);
      try {
        const result = await getItemsAction(billingRunId);
        if (cancelled) return;
        if (result.success && result.items) {
          // Show only approved items — exclude FAILED and EXCLUDED statuses
          setItems(
            result.items.filter(
              (item) =>
                item.status !== "FAILED" &&
                item.status !== "EXCLUDED" &&
                item.status !== "CANCELLED",
            ),
          );
        } else {
          setError(result.error ?? "Failed to load approved invoices.");
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

    loadApprovedItems();
    return () => {
      cancelled = true;
    };
  }, [billingRunId]);

  const totalAmount = items.reduce(
    (sum, item) => sum + item.totalUnbilledAmount,
    0,
  );

  async function handleSendAll() {
    setIsSending(true);
    setError(null);
    try {
      // Let the backend use each invoice's own due date and payment terms set during review
      const result = await batchSendAction(billingRunId, {});
      if (result.success && result.result) {
        setSendResult(result.result);
        // Also refresh the billing run to get final stats
        await getBillingRunAction(billingRunId);
      } else {
        setError(result.error ?? "Failed to send invoices.");
      }
    } catch {
      setError("An unexpected error occurred while sending.");
    } finally {
      setIsSending(false);
    }
  }

  function handleDone() {
    router.push(`/org/${slug}/invoices/billing-runs`);
  }

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-800 dark:bg-slate-950">
        <p className="text-slate-500 dark:text-slate-400">
          Loading approved invoices...
        </p>
      </div>
    );
  }

  // Show send result / summary
  if (sendResult) {
    const totalSent = sendResult.successCount;
    const totalFailed = sendResult.failureCount;

    return (
      <div className="space-y-6">
        <div className="rounded-lg border border-slate-200 bg-white p-8 dark:border-slate-800 dark:bg-slate-950">
          <div className="text-center">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              Billing Run Complete
            </h3>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Invoices have been processed.
            </p>
          </div>

          <div className="mt-6 flex justify-center gap-8">
            <div className="text-center">
              <p className="text-2xl font-bold text-teal-600">{totalSent}</p>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Sent
              </p>
            </div>
            {totalFailed > 0 && (
              <div className="text-center">
                <p className="text-2xl font-bold text-red-600">
                  {totalFailed}
                </p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Failed
                </p>
              </div>
            )}
            <div className="text-center">
              <p className="text-2xl font-bold text-slate-900 dark:text-slate-100">
                {formatCurrency(totalAmount, currency)}
              </p>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Total Amount
              </p>
            </div>
          </div>

          {sendResult.failures.length > 0 && (
            <div className="mt-6 rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-900 dark:bg-red-950/20">
              <p className="mb-2 text-sm font-medium text-red-700 dark:text-red-400">
                Failed to Send
              </p>
              <ul className="space-y-1 text-sm text-red-600 dark:text-red-400">
                {sendResult.failures.map((failure) => (
                  <li key={failure.invoiceId}>
                    Invoice {failure.invoiceId.slice(0, 8)}: {failure.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <div className="flex justify-center">
          <Button onClick={handleDone}>Done</Button>
        </div>
      </div>
    );
  }

  // Sending in progress
  if (isSending) {
    return (
      <div className="space-y-6">
        <div className="rounded-lg border border-slate-200 bg-white p-8 dark:border-slate-800 dark:bg-slate-950">
          <div className="text-center">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              Sending Invoices...
            </h3>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Please wait while invoices are being sent.
            </p>
          </div>
          <div className="mt-6">
            <Progress value={undefined} className="h-2" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      )}

      {/* Approved Invoices Table */}
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
                Total
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Status
              </th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.id}
                className="border-b border-slate-100 last:border-b-0 dark:border-slate-800"
              >
                <td className="px-4 py-3">{item.customerName}</td>
                <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                  {item.invoiceId
                    ? `INV-${item.invoiceId.slice(0, 8)}`
                    : "—"}
                </td>
                <td className="px-4 py-3 text-right font-medium">
                  {formatCurrency(item.totalUnbilledAmount, currency)}
                </td>
                <td className="px-4 py-3">
                  <Badge variant="success">Approved</Badge>
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td
                  colSpan={4}
                  className="px-4 py-8 text-center text-slate-500 dark:text-slate-400"
                >
                  No approved invoices to send.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Summary Bar */}
      <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
        <span className="text-sm text-slate-600 dark:text-slate-400">
          Ready to send:{" "}
          <span className="font-medium text-slate-900 dark:text-slate-100">
            {items.length} invoices
          </span>{" "}
          totaling{" "}
          <span className="font-medium text-slate-900 dark:text-slate-100">
            {formatCurrency(totalAmount, currency)}
          </span>
        </span>
      </div>

      {/* Navigation */}
      <div className="flex justify-between">
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button disabled={items.length === 0}>Send All</Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Confirm Send</AlertDialogTitle>
              <AlertDialogDescription>
                Send {items.length} invoices totaling{" "}
                {formatCurrency(totalAmount, currency)}? This action cannot be
                undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={handleSendAll}>
                Send {items.length} Invoices
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </div>
  );
}
