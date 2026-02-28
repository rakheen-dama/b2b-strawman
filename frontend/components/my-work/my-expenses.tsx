import { Receipt } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { ExpenseCategoryBadge } from "@/components/expenses/expense-category-badge";
import { formatCurrencySafe, formatDate } from "@/lib/format";
import type { ExpenseResponse } from "@/lib/types";

interface MyExpensesProps {
  expenses: ExpenseResponse[];
}

export function MyExpenses({ expenses }: MyExpensesProps) {
  if (expenses.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
        <h3 className="mb-3 text-sm font-semibold text-slate-900 dark:text-slate-100">
          My Expenses
        </h3>
        <EmptyState
          icon={Receipt}
          title="No recent expenses"
          description="Your logged expenses will appear here."
        />
      </div>
    );
  }

  // Group totals by currency to avoid mixing currencies
  const totalsByCurrency = expenses.reduce<Record<string, number>>(
    (acc, e) => {
      const cur = e.currency ?? "ZAR";
      acc[cur] = (acc[cur] ?? 0) + e.amount;
      return acc;
    },
    {},
  );
  const currencyEntries = Object.entries(totalsByCurrency);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          My Expenses
        </h3>
        <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
          {currencyEntries
            .map(([cur, amt]) => formatCurrencySafe(amt, cur))
            .join(" | ")}
        </span>
      </div>
      <div className="space-y-2">
        {expenses.map((expense) => (
          <div
            key={expense.id}
            className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-900"
          >
            <div className="flex min-w-0 items-center gap-2">
              <ExpenseCategoryBadge category={expense.category} />
              <span className="truncate text-slate-700 dark:text-slate-300">
                {expense.description}
              </span>
            </div>
            <div className="ml-2 flex shrink-0 items-center gap-3">
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {formatDate(expense.date)}
              </span>
              <span className="font-medium text-slate-700 dark:text-slate-300">
                {formatCurrencySafe(expense.amount, expense.currency)}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
