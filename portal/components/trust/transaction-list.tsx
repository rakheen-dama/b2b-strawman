"use client";

import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, Receipt } from "lucide-react";
import { formatCurrency, formatDate } from "@/lib/format";
import { Skeleton } from "@/components/ui/skeleton";
import {
  getMatterTransactions,
  type PortalTrustTransactionResponse,
} from "@/lib/api/trust";

interface TransactionListProps {
  matterId: string;
  /** Currency code (ISO 4217). Defaults to ZAR. */
  currency?: string;
  /** Page size — defaults to 20 (also the backend default). */
  pageSize?: number;
}

/**
 * Renders a stable, human-friendly description for a transaction.
 * When the backend supplies a description, use it verbatim; otherwise
 * synthesise one from the type + reference so rows are never blank
 * (sanitisation-fallback safety).
 */
function describeTransaction(txn: PortalTrustTransactionResponse): string {
  if (txn.description && txn.description.trim().length > 0) {
    return txn.description;
  }
  const humanType = txn.transactionType
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
  return txn.reference ? `${humanType} — ${txn.reference}` : humanType;
}

export function TransactionList({
  matterId,
  currency = "ZAR",
  pageSize = 20,
}: TransactionListProps) {
  const [page, setPage] = useState(0);
  const [transactions, setTransactions] = useState<
    PortalTrustTransactionResponse[]
  >([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Reset pagination when switching matters so we don't request page N+1
  // of a matter that may only have one page.
  useEffect(() => {
    setPage(0);
  }, [matterId]);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    (async () => {
      try {
        const data = await getMatterTransactions(matterId, {
          page,
          size: pageSize,
        });
        if (cancelled) return;
        setTransactions(data.content);
        setTotalPages(data.page.totalPages);
        setTotalElements(data.page.totalElements);
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load transactions",
          );
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [matterId, page, pageSize]);

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        {error}
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-12 text-center">
        <Receipt className="mb-3 size-10 text-slate-300" aria-hidden="true" />
        <p className="text-sm font-medium text-slate-600">
          No transactions yet
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Mobile cards */}
      <ul className="space-y-3 md:hidden" aria-label="Trust transactions">
        {transactions.map((txn) => (
          <li
            key={txn.id}
            className="rounded-lg border border-slate-200 bg-white p-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900">
                  {describeTransaction(txn)}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {formatDate(txn.occurredAt)} · {txn.transactionType}
                </p>
              </div>
              <div className="text-right">
                <p className="font-mono text-sm font-semibold text-slate-900 tabular-nums">
                  {formatCurrency(txn.amount, currency)}
                </p>
                <p className="mt-1 font-mono text-xs text-slate-500 tabular-nums">
                  Bal {formatCurrency(txn.runningBalance, currency)}
                </p>
              </div>
            </div>
          </li>
        ))}
      </ul>

      {/* Desktop table */}
      <div className="hidden overflow-x-auto rounded-lg border border-slate-200 md:block">
        <table className="w-full text-sm" aria-label="Trust transactions">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-4 py-3 text-left font-medium text-slate-600">
                Date
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600">
                Type
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600">
                Description
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600">
                Amount
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600">
                Running balance
              </th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((txn) => (
              <tr
                key={txn.id}
                className="border-b border-slate-100 last:border-b-0"
              >
                <td className="px-4 py-3 text-slate-700">
                  {formatDate(txn.occurredAt)}
                </td>
                <td className="px-4 py-3 text-slate-700">
                  {txn.transactionType}
                </td>
                <td className="px-4 py-3 text-slate-900">
                  {describeTransaction(txn)}
                </td>
                <td className="px-4 py-3 text-right font-mono text-slate-900 tabular-nums">
                  {formatCurrency(txn.amount, currency)}
                </td>
                <td className="px-4 py-3 text-right font-mono text-slate-700 tabular-nums">
                  {formatCurrency(txn.runningBalance, currency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-4 pt-2">
          <p className="text-xs text-slate-500">
            Page {page + 1} of {totalPages} · {totalElements} total
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="inline-flex min-h-11 items-center gap-1 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
              aria-label="Previous page"
            >
              <ChevronLeft className="size-4" />
              Previous
            </button>
            <button
              type="button"
              onClick={() =>
                setPage((p) => Math.min(totalPages - 1, p + 1))
              }
              disabled={page >= totalPages - 1}
              className="inline-flex min-h-11 items-center gap-1 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
              aria-label="Next page"
            >
              Next
              <ChevronRight className="size-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
