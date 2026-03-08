"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import {
  getUnbilledTimeAction,
  getUnbilledExpensesAction,
  updateSelectionsAction,
  excludeCustomerAction,
  includeCustomerAction,
  getRetainerPreviewAction,
} from "@/app/(app)/org/[slug]/invoices/billing-runs/new/actions";
import type {
  BillingRunItem,
  UnbilledTimeEntry,
  UnbilledExpense,
  RetainerPeriodPreview,
  EntrySelectionDto,
} from "@/lib/api/billing-runs";

interface CherryPickStepProps {
  billingRunId: string;
  currency: string;
  includeRetainers: boolean;
  items: BillingRunItem[];
  onBack: () => void;
  onNext: () => void;
}

interface CustomerData {
  timeEntries: UnbilledTimeEntry[];
  expenses: UnbilledExpense[];
  includedTimeIds: Set<string>;
  includedExpenseIds: Set<string>;
  isLoading: boolean;
  isLoaded: boolean;
  error: string | null;
}

export function CherryPickStep({
  billingRunId,
  currency,
  includeRetainers,
  items,
  onBack,
  onNext,
}: CherryPickStepProps) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [customerData, setCustomerData] = useState<
    Record<string, CustomerData>
  >({});
  const [itemStates, setItemStates] = useState<Record<string, BillingRunItem>>(
    () => {
      const map: Record<string, BillingRunItem> = {};
      for (const item of items) {
        map[item.id] = item;
      }
      return map;
    },
  );
  const [retainers, setRetainers] = useState<RetainerPeriodPreview[]>([]);
  const [includedRetainerIds, setIncludedRetainerIds] = useState<Set<string>>(
    new Set(),
  );
  const [retainersLoaded, setRetainersLoaded] = useState(false);

  // Debounce timer ref for selection updates
  const debounceTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>(
    {},
  );
  const pendingSelections = useRef<Record<string, EntrySelectionDto[]>>({});

  // Load retainers on mount if enabled
  useEffect(() => {
    if (!includeRetainers || retainersLoaded) return;

    async function loadRetainers() {
      const result = await getRetainerPreviewAction(billingRunId);
      if (result.success && result.retainers) {
        setRetainers(result.retainers);
        setIncludedRetainerIds(
          new Set(result.retainers.map((r) => r.agreementId)),
        );
      }
      setRetainersLoaded(true);
    }

    loadRetainers();
  }, [billingRunId, includeRetainers, retainersLoaded]);

  // Clean up debounce timers on unmount
  useEffect(() => {
    return () => {
      Object.values(debounceTimers.current).forEach(clearTimeout);
    };
  }, []);

  const loadCustomerData = useCallback(
    async (itemId: string) => {
      setCustomerData((prev) => ({
        ...prev,
        [itemId]: {
          timeEntries: [],
          expenses: [],
          includedTimeIds: new Set(),
          includedExpenseIds: new Set(),
          isLoading: true,
          isLoaded: false,
          error: null,
        },
      }));

      try {
        const [timeResult, expenseResult] = await Promise.all([
          getUnbilledTimeAction(billingRunId, itemId),
          getUnbilledExpensesAction(billingRunId, itemId),
        ]);

        if (!timeResult.success || !expenseResult.success) {
          setCustomerData((prev) => ({
            ...prev,
            [itemId]: {
              ...prev[itemId],
              isLoading: false,
              error:
                timeResult.error ||
                expenseResult.error ||
                "Failed to load data.",
            },
          }));
          return;
        }

        const timeEntries = timeResult.entries ?? [];
        const expenses = expenseResult.entries ?? [];

        setCustomerData((prev) => ({
          ...prev,
          [itemId]: {
            timeEntries,
            expenses,
            includedTimeIds: new Set(timeEntries.map((e) => e.id)),
            includedExpenseIds: new Set(expenses.map((e) => e.id)),
            isLoading: false,
            isLoaded: true,
            error: null,
          },
        }));
      } catch {
        setCustomerData((prev) => ({
          ...prev,
          [itemId]: {
            ...prev[itemId],
            isLoading: false,
            error: "An unexpected error occurred.",
          },
        }));
      }
    },
    [billingRunId],
  );

  function toggleSection(itemId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
        // Lazy-load data on first expand
        if (!customerData[itemId]?.isLoaded && !customerData[itemId]?.isLoading) {
          loadCustomerData(itemId);
        }
      }
      return next;
    });
  }

  function flushSelections(itemId: string) {
    const selections = pendingSelections.current[itemId];
    if (!selections || selections.length === 0) return;

    const selectionsToSend = [...selections];
    pendingSelections.current[itemId] = [];

    updateSelectionsAction(billingRunId, itemId, {
      selections: selectionsToSend,
    })
      .then((result) => {
        if (!result.success) {
          // Revert optimistic updates on failure
          loadCustomerData(itemId);
        } else if (result.item) {
          setItemStates((prev) => ({
            ...prev,
            [itemId]: result.item!,
          }));
        }
      })
      .catch(() => {
        // Revert optimistic updates on network/unexpected error
        loadCustomerData(itemId);
      });
  }

  function scheduleFlush(itemId: string) {
    if (debounceTimers.current[itemId]) {
      clearTimeout(debounceTimers.current[itemId]);
    }
    debounceTimers.current[itemId] = setTimeout(() => {
      flushSelections(itemId);
    }, 500);
  }

  function toggleTimeEntry(itemId: string, entryId: string) {
    const data = customerData[itemId];
    if (!data) return;

    const isCurrentlyIncluded = data.includedTimeIds.has(entryId);
    const newIncluded = !isCurrentlyIncluded;

    // Optimistic update
    setCustomerData((prev) => {
      const current = prev[itemId];
      const nextIds = new Set(current.includedTimeIds);
      if (newIncluded) {
        nextIds.add(entryId);
      } else {
        nextIds.delete(entryId);
      }
      return {
        ...prev,
        [itemId]: { ...current, includedTimeIds: nextIds },
      };
    });

    // Queue selection update
    if (!pendingSelections.current[itemId]) {
      pendingSelections.current[itemId] = [];
    }
    pendingSelections.current[itemId].push({
      entryType: "TIME_ENTRY",
      entryId,
      included: newIncluded,
    });
    scheduleFlush(itemId);
  }

  function toggleExpenseEntry(itemId: string, entryId: string) {
    const data = customerData[itemId];
    if (!data) return;

    const isCurrentlyIncluded = data.includedExpenseIds.has(entryId);
    const newIncluded = !isCurrentlyIncluded;

    // Optimistic update
    setCustomerData((prev) => {
      const current = prev[itemId];
      const nextIds = new Set(current.includedExpenseIds);
      if (newIncluded) {
        nextIds.add(entryId);
      } else {
        nextIds.delete(entryId);
      }
      return {
        ...prev,
        [itemId]: { ...current, includedExpenseIds: nextIds },
      };
    });

    // Queue selection update
    if (!pendingSelections.current[itemId]) {
      pendingSelections.current[itemId] = [];
    }
    pendingSelections.current[itemId].push({
      entryType: "EXPENSE",
      entryId,
      included: newIncluded,
    });
    scheduleFlush(itemId);
  }

  async function handleExcludeCustomer(itemId: string) {
    const result = await excludeCustomerAction(billingRunId, itemId);
    if (result.success && result.item) {
      setItemStates((prev) => ({
        ...prev,
        [itemId]: result.item!,
      }));
    }
  }

  async function handleIncludeCustomer(itemId: string) {
    const result = await includeCustomerAction(billingRunId, itemId);
    if (result.success && result.item) {
      setItemStates((prev) => ({
        ...prev,
        [itemId]: result.item!,
      }));
    }
  }

  function getSubtotal(itemId: string): number {
    const data = customerData[itemId];
    if (!data?.isLoaded) {
      return itemStates[itemId]?.totalUnbilledAmount ?? 0;
    }

    const timeTotal = data.timeEntries
      .filter((e) => data.includedTimeIds.has(e.id))
      .reduce((sum, e) => sum + e.billableValue, 0);

    const expenseTotal = data.expenses
      .filter((e) => data.includedExpenseIds.has(e.id))
      .reduce((sum, e) => sum + e.billableAmount, 0);

    return timeTotal + expenseTotal;
  }

  function toggleRetainer(agreementId: string) {
    setIncludedRetainerIds((prev) => {
      const next = new Set(prev);
      if (next.has(agreementId)) {
        next.delete(agreementId);
      } else {
        next.add(agreementId);
      }
      return next;
    });
  }

  const activeItems = Object.values(itemStates).filter(
    (item) => item.status !== "EXCLUDED" && item.status !== "CANCELLED",
  );

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <div className="p-6">
          <h2 className="mb-4 text-lg font-semibold text-slate-950 dark:text-slate-50">
            Review &amp; Cherry-Pick
          </h2>

          {items.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No customers to review.
            </p>
          ) : (
            <div className="space-y-2">
              {items.map((originalItem) => {
                const item = itemStates[originalItem.id] ?? originalItem;
                const isExpanded = expandedIds.has(item.id);
                const data = customerData[item.id];
                const isExcluded = item.status === "EXCLUDED";
                const subtotal = getSubtotal(item.id);

                return (
                  <div
                    key={item.id}
                    className="rounded-md border border-slate-200 dark:border-slate-700"
                  >
                    {/* Customer Header */}
                    <button
                      type="button"
                      className="flex w-full items-center justify-between p-4 text-left"
                      onClick={() => toggleSection(item.id)}
                      aria-expanded={isExpanded}
                    >
                      <div className="flex items-center gap-3">
                        <ChevronRight
                          className={`size-4 text-slate-500 transition-transform ${isExpanded ? "rotate-90" : ""}`}
                        />
                        <span
                          className={`font-medium ${isExcluded ? "text-slate-400 line-through dark:text-slate-500" : "text-slate-950 dark:text-slate-50"}`}
                        >
                          {item.customerName}
                        </span>
                        {isExcluded && (
                          <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
                            Excluded
                          </span>
                        )}
                      </div>
                      <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
                        {formatCurrency(subtotal, currency)}
                      </span>
                    </button>

                    {/* Expanded Content */}
                    {isExpanded && (
                      <div className="border-t border-slate-200 p-4 dark:border-slate-700">
                        {/* Exclude/Include Button */}
                        <div className="mb-4 flex justify-end">
                          {isExcluded ? (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleIncludeCustomer(item.id)}
                            >
                              Include Customer
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleExcludeCustomer(item.id)}
                            >
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
                          <p
                            role="alert"
                            className="text-sm text-destructive"
                          >
                            {data.error}
                          </p>
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
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Include
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Date
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Member
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Task
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Hours
                                        </th>
                                        <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">
                                          Rate
                                        </th>
                                        <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                                          Amount
                                        </th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {data.timeEntries.map((entry) => (
                                        <tr
                                          key={entry.id}
                                          className="border-b border-slate-100 dark:border-slate-800"
                                        >
                                          <td className="py-2 pr-3">
                                            <Checkbox
                                              checked={data.includedTimeIds.has(
                                                entry.id,
                                              )}
                                              onCheckedChange={() =>
                                                toggleTimeEntry(
                                                  item.id,
                                                  entry.id,
                                                )
                                              }
                                              aria-label={`Include time entry ${entry.description ?? entry.id}`}
                                            />
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {formatLocalDate(entry.date)}
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            Member
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {entry.description ?? "Task"}
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {(
                                              entry.durationMinutes / 60
                                            ).toFixed(1)}
                                          </td>
                                          <td className="py-2 pr-3 text-right text-slate-600 dark:text-slate-400">
                                            {formatCurrency(
                                              entry.billingRateSnapshot,
                                              currency,
                                            )}
                                          </td>
                                          <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">
                                            {formatCurrency(
                                              entry.billableValue,
                                              currency,
                                            )}
                                          </td>
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
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Include
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Date
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Description
                                        </th>
                                        <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                                          Category
                                        </th>
                                        <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                                          Amount
                                        </th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {data.expenses.map((expense) => (
                                        <tr
                                          key={expense.id}
                                          className="border-b border-slate-100 dark:border-slate-800"
                                        >
                                          <td className="py-2 pr-3">
                                            <Checkbox
                                              checked={data.includedExpenseIds.has(
                                                expense.id,
                                              )}
                                              onCheckedChange={() =>
                                                toggleExpenseEntry(
                                                  item.id,
                                                  expense.id,
                                                )
                                              }
                                              aria-label={`Include expense ${expense.description ?? expense.id}`}
                                            />
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {formatLocalDate(expense.date)}
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {expense.description ?? "—"}
                                          </td>
                                          <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                                            {expense.category}
                                          </td>
                                          <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">
                                            {formatCurrency(
                                              expense.billableAmount,
                                              currency,
                                            )}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </div>
                              </div>
                            )}

                            {data.timeEntries.length === 0 &&
                              data.expenses.length === 0 && (
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
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Retainer Section */}
        {includeRetainers && retainersLoaded && retainers.length > 0 && (
          <div className="border-t border-slate-200 p-6 dark:border-slate-700">
            <h3 className="mb-3 text-sm font-semibold text-slate-950 dark:text-slate-50">
              Retainer Agreements
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left dark:border-slate-700">
                    <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                      Include
                    </th>
                    <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                      Customer
                    </th>
                    <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">
                      Period
                    </th>
                    <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">
                      Consumed Hours
                    </th>
                    <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                      Estimated Amount
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {retainers.map((retainer) => (
                    <tr
                      key={retainer.agreementId}
                      className="border-b border-slate-100 dark:border-slate-800"
                    >
                      <td className="py-2 pr-3">
                        <Checkbox
                          checked={includedRetainerIds.has(
                            retainer.agreementId,
                          )}
                          onCheckedChange={() =>
                            toggleRetainer(retainer.agreementId)
                          }
                          aria-label={`Include retainer for ${retainer.customerName}`}
                        />
                      </td>
                      <td className="py-2 pr-3 font-medium text-slate-950 dark:text-slate-50">
                        {retainer.customerName}
                      </td>
                      <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                        {formatLocalDate(retainer.periodStart)} &mdash;{" "}
                        {formatLocalDate(retainer.periodEnd)}
                      </td>
                      <td className="py-2 pr-3 text-right text-slate-600 dark:text-slate-400">
                        {retainer.consumedHours.toFixed(1)}
                      </td>
                      <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">
                        {formatCurrency(retainer.estimatedAmount, currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Summary Bar */}
        <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-4 dark:border-slate-700 dark:bg-slate-900">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            <span className="font-medium text-slate-950 dark:text-slate-50">
              {activeItems.length}
            </span>{" "}
            {activeItems.length === 1 ? "customer" : "customers"} included
          </p>
          <div className="flex gap-3">
            <Button variant="outline" onClick={onBack}>
              Back
            </Button>
            <Button
              variant="default"
              onClick={onNext}
              disabled={activeItems.length === 0}
            >
              Next
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
