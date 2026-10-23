"use client";

import { useMemo } from "react";
import useSWR, { useSWRConfig } from "swr";
import { fetchUnbilledDisbursementsAction } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { formatCurrency } from "@/lib/format";
import type { UnbilledDisbursementsResponse } from "@/lib/api/legal-disbursements";

/**
 * SWR key of a matter's unbilled-disbursement totals. Distinct from the invoice picker's
 * `unbilled-disbursements-${id}` key on purpose: that fetcher has its own null→error semantics.
 * Callers that move the total use `useUnbilledDisbursementsSummaryCache()` rather than this key.
 */
export function unbilledDisbursementsSummaryKey(matterId: string): string {
  return `unbilled-disbursements-summary-${matterId}`;
}

/**
 * Keeps the strip in step after approve / write-off / restore / create / edit move a matter's
 * unbilled total. Null / empty matter ids are ignored.
 * - `refresh` — the strip is mounted on this page: revalidate it in place.
 * - `evict` — the strip is NOT mounted here (disbursement detail): SWR's `mutate(key)` only
 *   revalidates mounted hooks and would leave the old total cached, so the strip would paint it
 *   when the user navigates back. Drop the cached data instead; the strip refetches on mount.
 */
export function useUnbilledDisbursementsSummaryCache() {
  const { mutate } = useSWRConfig();
  return useMemo(
    () => ({
      refresh(matterId: string | null | undefined) {
        if (matterId) void mutate(unbilledDisbursementsSummaryKey(matterId));
      },
      evict(matterId: string | null | undefined) {
        if (matterId) {
          void mutate(unbilledDisbursementsSummaryKey(matterId), undefined, { revalidate: false });
        }
      },
    }),
    [mutate]
  );
}

interface UnbilledDisbursementsSummaryProps {
  matterId: string;
}

/**
 * "Unbilled Disbursements R 2 000,00 · VAT R 300,00" — approved + UNBILLED disbursements of the
 * matter, excl. VAT first (G-B12). The label is "Disbursements" with a capital D and nothing but
 * whitespace before the amount (the Day 5 depth-ii assertion), and the strip holds no heading so
 * the tab's "Disbursements" heading stays unique. Non-critical: errors / 403 render nothing.
 */
export function UnbilledDisbursementsSummary({ matterId }: UnbilledDisbursementsSummaryProps) {
  const { data, error, isLoading } = useSWR<UnbilledDisbursementsResponse>(
    matterId ? unbilledDisbursementsSummaryKey(matterId) : null,
    async () => {
      // The action swallows errors and returns null — surface that as an SWR error.
      const result = await fetchUnbilledDisbursementsAction(matterId);
      if (!result) throw new Error("Failed to fetch unbilled disbursements");
      return result;
    },
    { revalidateOnFocus: false }
  );

  if (isLoading || error || !data) return null;

  return (
    <div
      data-testid="unbilled-disbursements-summary"
      className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm dark:border-slate-800 dark:bg-slate-900"
    >
      {data.items.length === 0 ? (
        <span className="text-slate-500 dark:text-slate-400">No unbilled disbursements</span>
      ) : (
        <>
          <span className="text-slate-600 dark:text-slate-400">Unbilled Disbursements</span>{" "}
          <span
            data-testid="unbilled-disbursements-total"
            className="font-mono font-medium text-slate-900 tabular-nums dark:text-slate-100"
          >
            {formatCurrency(data.totalAmount, data.currency)}
          </span>
          <span className="text-slate-400 dark:text-slate-600"> &middot; </span>
          <span className="text-slate-600 dark:text-slate-400">VAT</span>{" "}
          <span
            data-testid="unbilled-disbursements-vat"
            className="font-mono text-slate-700 tabular-nums dark:text-slate-300"
          >
            {formatCurrency(data.totalVat, data.currency)}
          </span>
        </>
      )}
    </div>
  );
}
