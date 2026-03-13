"use client";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { UnbilledTimeEntry, UnbilledExpense } from "@/lib/api/billing-runs";

interface CustomerDetailData {
  timeEntries: UnbilledTimeEntry[];
  expenses: UnbilledExpense[];
  includedTimeIds: Set<string>;
  includedExpenseIds: Set<string>;
  isLoading: boolean;
  isLoaded: boolean;
  error: string | null;
}

interface CherryPickCustomerDetailProps {
  itemId: string;
  isExcluded: boolean;
  data: CustomerDetailData | undefined;
  currency: string;
  subtotal: number;
  onExclude: (itemId: string) => void;
  onInclude: (itemId: string) => void;
  onToggleTimeEntry: (itemId: string, entryId: string) => void;
  onToggleExpenseEntry: (itemId: string, entryId: string) => void;
}

export function CherryPickCustomerDetail({
  itemId,
  isExcluded,
  data,
  currency,
  subtotal,
  onExclude,
  onInclude,
  onToggleTimeEntry,
  onToggleExpenseEntry,
}: CherryPickCustomerDetailProps) {
  return (
    <div className="border-t border-slate-200 p-4 dark:border-slate-700">
      {/* Exclude/Include Button */}
      <div className="mb-4 flex justify-end">
        {isExcluded ? (
          <Button variant="outline" size="sm" onClick={() => onInclude(itemId)}>
            Include Customer
          </Button>
        ) : (
          <Button variant="outline" size="sm" onClick={() => onExclude(itemId)}>
            Exclude Customer
          </Button>
        )}
      </div>

      {data?.isLoading && (
        <div className="space-y-2">
          <div className="h-8 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-8 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
        </div>
      )}

      {data?.error && (
        <p role="alert" className="text-sm text-destructive">{data.error}</p>
      )}

      {data?.isLoaded && !isExcluded && (
        <>
          {/* Time Entries Table */}
          {data.timeEntries.length > 0 && (
            <div className="mb-4">
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Time Entries
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left dark:border-slate-700">
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Include</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Date</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Member</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Task</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Hours</th>
                      <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">Rate</th>
                      <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.timeEntries.map((entry) => (
                      <tr key={entry.id} className="border-b border-slate-100 dark:border-slate-800">
                        <td className="py-2 pr-3">
                          <Checkbox
                            checked={data.includedTimeIds.has(entry.id)}
                            onCheckedChange={() => onToggleTimeEntry(itemId, entry.id)}
                            aria-label={`Include time entry ${entry.description ?? entry.id}`}
                          />
                        </td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{formatLocalDate(entry.date)}</td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">Member</td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{entry.description ?? "Task"}</td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{(entry.durationMinutes / 60).toFixed(1)}</td>
                        <td className="py-2 pr-3 text-right text-slate-600 dark:text-slate-400">{formatCurrency(entry.billingRateSnapshot, currency)}</td>
                        <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">{formatCurrency(entry.billableValue, currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Expenses Table */}
          {data.expenses.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                Expenses
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left dark:border-slate-700">
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Include</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Date</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Description</th>
                      <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Category</th>
                      <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.expenses.map((expense) => (
                      <tr key={expense.id} className="border-b border-slate-100 dark:border-slate-800">
                        <td className="py-2 pr-3">
                          <Checkbox
                            checked={data.includedExpenseIds.has(expense.id)}
                            onCheckedChange={() => onToggleExpenseEntry(itemId, expense.id)}
                            aria-label={`Include expense ${expense.description ?? expense.id}`}
                          />
                        </td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{formatLocalDate(expense.date)}</td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{expense.description ?? "\u2014"}</td>
                        <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">{expense.category}</td>
                        <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">{formatCurrency(expense.billableAmount, currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {data.timeEntries.length === 0 && data.expenses.length === 0 && (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No unbilled entries for this customer.
            </p>
          )}

          {/* Subtotal */}
          <div className="mt-3 flex justify-end border-t border-slate-200 pt-3 dark:border-slate-700">
            <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
              Subtotal: {formatCurrency(subtotal, currency)}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
