"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/format";
import {
  getUnbilledTimeAction,
  getUnbilledExpensesAction,
  updateSelectionsAction,
  excludeCustomerAction,
  includeCustomerAction,
  getRetainerPreviewAction,
} from "@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-run-actions";
import { CherryPickCustomerDetail } from "./cherry-pick-customer-detail";
import { CherryPickRetainerSection } from "./cherry-pick-retainer-section";
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
  const [customerData, setCustomerData] = useState<Record<string, CustomerData>>({});
  const [itemStates, setItemStates] = useState<Record<string, BillingRunItem>>(() => {
    const map: Record<string, BillingRunItem> = {};
    for (const item of items) map[item.id] = item;
    return map;
  });
  const [retainers, setRetainers] = useState<RetainerPeriodPreview[]>([]);
  const [includedRetainerIds, setIncludedRetainerIds] = useState<Set<string>>(new Set());
  const [retainersLoaded, setRetainersLoaded] = useState(false);

  const debounceTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const pendingSelections = useRef<Record<string, EntrySelectionDto[]>>({});

  useEffect(() => {
    if (!includeRetainers || retainersLoaded) return;
    async function loadRetainers() {
      const result = await getRetainerPreviewAction(billingRunId);
      if (result.success && result.retainers) {
        setRetainers(result.retainers);
        setIncludedRetainerIds(new Set(result.retainers.map((r) => r.agreementId)));
      }
      setRetainersLoaded(true);
    }
    loadRetainers();
  }, [billingRunId, includeRetainers, retainersLoaded]);

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
              error: timeResult.error || expenseResult.error || "Failed to load data.",
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
          [itemId]: { ...prev[itemId], isLoading: false, error: "An unexpected error occurred." },
        }));
      }
    },
    [billingRunId]
  );

  function toggleSection(itemId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
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
    updateSelectionsAction(billingRunId, itemId, { selections: selectionsToSend })
      .then((result) => {
        if (!result.success) loadCustomerData(itemId);
        else if (result.item) setItemStates((prev) => ({ ...prev, [itemId]: result.item! }));
      })
      .catch(() => loadCustomerData(itemId));
  }

  function scheduleFlush(itemId: string) {
    if (debounceTimers.current[itemId]) clearTimeout(debounceTimers.current[itemId]);
    debounceTimers.current[itemId] = setTimeout(() => flushSelections(itemId), 500);
  }

  function toggleTimeEntry(itemId: string, entryId: string) {
    const data = customerData[itemId];
    if (!data) return;
    const newIncluded = !data.includedTimeIds.has(entryId);
    setCustomerData((prev) => {
      const current = prev[itemId];
      const nextIds = new Set(current.includedTimeIds);
      if (newIncluded) nextIds.add(entryId);
      else nextIds.delete(entryId);
      return { ...prev, [itemId]: { ...current, includedTimeIds: nextIds } };
    });
    if (!pendingSelections.current[itemId]) pendingSelections.current[itemId] = [];
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
    const newIncluded = !data.includedExpenseIds.has(entryId);
    setCustomerData((prev) => {
      const current = prev[itemId];
      const nextIds = new Set(current.includedExpenseIds);
      if (newIncluded) nextIds.add(entryId);
      else nextIds.delete(entryId);
      return { ...prev, [itemId]: { ...current, includedExpenseIds: nextIds } };
    });
    if (!pendingSelections.current[itemId]) pendingSelections.current[itemId] = [];
    pendingSelections.current[itemId].push({
      entryType: "EXPENSE",
      entryId,
      included: newIncluded,
    });
    scheduleFlush(itemId);
  }

  async function handleExcludeCustomer(itemId: string) {
    const result = await excludeCustomerAction(billingRunId, itemId);
    if (result.success && result.item)
      setItemStates((prev) => ({ ...prev, [itemId]: result.item! }));
  }

  async function handleIncludeCustomer(itemId: string) {
    const result = await includeCustomerAction(billingRunId, itemId);
    if (result.success && result.item)
      setItemStates((prev) => ({ ...prev, [itemId]: result.item! }));
  }

  function getSubtotal(itemId: string): number {
    const data = customerData[itemId];
    if (!data?.isLoaded) return itemStates[itemId]?.totalUnbilledAmount ?? 0;
    const timeTotal = data.timeEntries
      .filter((e) => data.includedTimeIds.has(e.id))
      .reduce((sum, e) => sum + (e.billableValue ?? 0), 0);
    const expenseTotal = data.expenses
      .filter((e) => data.includedExpenseIds.has(e.id))
      .reduce((sum, e) => sum + e.billableAmount, 0);
    return timeTotal + expenseTotal;
  }

  function toggleRetainer(agreementId: string) {
    setIncludedRetainerIds((prev) => {
      const next = new Set(prev);
      if (next.has(agreementId)) next.delete(agreementId);
      else next.add(agreementId);
      return next;
    });
  }

  const activeItems = Object.values(itemStates).filter(
    (item) => item.status !== "EXCLUDED" && item.status !== "CANCELLED"
  );

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <div className="p-6">
          <h2 className="mb-4 text-lg font-semibold text-slate-950 dark:text-slate-50">
            Review &amp; Cherry-Pick
          </h2>

          {items.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">No customers to review.</p>
          ) : (
            <div className="space-y-2">
              {items.map((originalItem) => {
                const item = itemStates[originalItem.id] ?? originalItem;
                const isExpanded = expandedIds.has(item.id);
                const isExcluded = item.status === "EXCLUDED";
                const subtotal = getSubtotal(item.id);

                return (
                  <div
                    key={item.id}
                    className="rounded-md border border-slate-200 dark:border-slate-700"
                  >
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

                    {isExpanded && (
                      <CherryPickCustomerDetail
                        itemId={item.id}
                        isExcluded={isExcluded}
                        data={customerData[item.id]}
                        currency={currency}
                        subtotal={subtotal}
                        onExclude={handleExcludeCustomer}
                        onInclude={handleIncludeCustomer}
                        onToggleTimeEntry={toggleTimeEntry}
                        onToggleExpenseEntry={toggleExpenseEntry}
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Retainer Section */}
        {includeRetainers && retainersLoaded && retainers.length > 0 && (
          <CherryPickRetainerSection
            retainers={retainers}
            includedRetainerIds={includedRetainerIds}
            currency={currency}
            onToggleRetainer={toggleRetainer}
          />
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
            <Button variant="default" onClick={onNext} disabled={activeItems.length === 0}>
              Next
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
