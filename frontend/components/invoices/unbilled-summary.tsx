import { formatCurrency, formatDate } from "@/lib/format";
import { ExpenseCategoryBadge } from "@/components/expenses/expense-category-badge";
import type {
  UnbilledTimeResponse,
  UnbilledExpenseEntry,
} from "@/lib/types";

/** Wraps formatCurrency in a try-catch to handle invalid currency codes gracefully. */
function safeFormatCurrency(amount: number, curr: string): string {
  try {
    return formatCurrency(amount, curr);
  } catch {
    return `${curr} ${amount.toFixed(2)}`;
  }
}

interface UnbilledSummaryProps {
  data: UnbilledTimeResponse;
}

export function UnbilledSummary({ data }: UnbilledSummaryProps) {
  // Group expenses by project
  const expensesByProject = data.unbilledExpenses.reduce<
    Record<string, { projectName: string; expenses: UnbilledExpenseEntry[] }>
  >((acc, expense) => {
    if (!acc[expense.projectId]) {
      acc[expense.projectId] = {
        projectName: expense.projectName,
        expenses: [],
      };
    }
    acc[expense.projectId].expenses.push(expense);
    return acc;
  }, {});

  // Calculate combined grand totals (time + expenses)
  const combinedTotals: Record<string, number> = {};
  for (const [curr, total] of Object.entries(data.grandTotals)) {
    combinedTotals[curr] = (combinedTotals[curr] ?? 0) + total.amount;
  }
  for (const [curr, amount] of Object.entries(data.unbilledExpenseTotals)) {
    combinedTotals[curr] = (combinedTotals[curr] ?? 0) + amount;
  }

  const hasTimeEntries = data.projects.some((p) => p.entries.length > 0);
  const hasExpenses = data.unbilledExpenses.length > 0;

  if (!hasTimeEntries && !hasExpenses) {
    return (
      <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
        No unbilled items found.
      </p>
    );
  }

  return (
    <div className="space-y-6" data-testid="unbilled-summary">
      {/* Time Entries Section */}
      {hasTimeEntries && (
        <div>
          <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
            Time Entries
          </h3>
          <div className="space-y-3">
            {data.projects
              .filter((p) => p.entries.length > 0)
              .map((project) => (
                <div
                  key={project.projectId}
                  className="rounded-lg border border-slate-200 dark:border-slate-800"
                >
                  <div className="border-b border-slate-200 px-4 py-2.5 dark:border-slate-800">
                    <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                      {project.projectName}
                    </span>
                    <span className="ml-2 text-xs text-slate-500">
                      {project.entries.length}{" "}
                      {project.entries.length === 1 ? "entry" : "entries"}
                    </span>
                  </div>
                  <div className="divide-y divide-slate-100 dark:divide-slate-800/50">
                    {project.entries.map((entry) => (
                      <div
                        key={entry.id}
                        className="flex items-center justify-between px-4 py-2 text-sm"
                      >
                        <div className="min-w-0 flex-1">
                          <span className="font-medium text-slate-900 dark:text-slate-100">
                            {entry.taskTitle}
                          </span>
                          <div className="text-xs text-slate-500">
                            {entry.memberName} &middot; {formatDate(entry.date)}{" "}
                            &middot; {entry.durationMinutes} min
                          </div>
                        </div>
                        <span className="shrink-0 font-medium text-slate-700 dark:text-slate-300">
                          {formatCurrency(
                            entry.billableValue,
                            entry.billingRateCurrency,
                          )}
                        </span>
                      </div>
                    ))}
                  </div>
                  {/* Project subtotals */}
                  <div className="border-t border-slate-200 px-4 py-2 dark:border-slate-800">
                    <div className="flex flex-wrap gap-4">
                      {Object.entries(project.totals).map(([curr, total]) => (
                        <span
                          key={curr}
                          className="text-sm font-medium text-slate-700 dark:text-slate-300"
                        >
                          {safeFormatCurrency(total.amount, curr)} (
                          {total.hours}h)
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
          </div>
          {/* Time totals */}
          <div className="mt-3 flex items-center justify-between">
            <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Time Subtotal
            </span>
            <div className="flex gap-3">
              {Object.entries(data.grandTotals).map(([curr, total]) => (
                <span
                  key={curr}
                  className="text-sm font-semibold text-slate-900 dark:text-slate-100"
                >
                  {safeFormatCurrency(total.amount, curr)}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Expenses Section */}
      {hasExpenses && (
        <div>
          <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-400">
            Expenses
          </h3>
          <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
                  <th className="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Date
                  </th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Project
                  </th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Description
                  </th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Category
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Amount
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Markup %
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                    Billable
                  </th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(expensesByProject).map(
                  ([_projectId, { projectName, expenses }]) =>
                    expenses.map((expense, idx) => (
                      <tr
                        key={expense.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                      >
                        <td className="px-4 py-2.5 text-sm text-slate-900 dark:text-slate-100">
                          {formatDate(expense.date)}
                        </td>
                        <td className="px-4 py-2.5 text-sm text-slate-600 dark:text-slate-400">
                          {idx === 0 ? projectName : ""}
                        </td>
                        <td className="px-4 py-2.5 text-sm text-slate-900 dark:text-slate-100">
                          {expense.description}
                        </td>
                        <td className="px-4 py-2.5">
                          <ExpenseCategoryBadge category={expense.category} />
                        </td>
                        <td className="px-4 py-2.5 text-right text-sm text-slate-900 dark:text-slate-100">
                          {safeFormatCurrency(expense.amount, expense.currency)}
                        </td>
                        <td className="px-4 py-2.5 text-right text-sm text-slate-600 dark:text-slate-400">
                          {expense.markupPercent != null
                            ? `${expense.markupPercent}%`
                            : "\u2014"}
                        </td>
                        <td className="px-4 py-2.5 text-right text-sm font-medium text-slate-900 dark:text-slate-100">
                          {safeFormatCurrency(
                            expense.billableAmount,
                            expense.currency,
                          )}
                        </td>
                      </tr>
                    )),
                )}
              </tbody>
            </table>
          </div>
          {/* Expense totals */}
          <div className="mt-3 flex items-center justify-between">
            <span className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Expense Subtotal
            </span>
            <div className="flex gap-3">
              {Object.entries(data.unbilledExpenseTotals).map(
                ([curr, amount]) => (
                  <span
                    key={curr}
                    className="text-sm font-semibold text-slate-900 dark:text-slate-100"
                  >
                    {safeFormatCurrency(amount, curr)}
                  </span>
                ),
              )}
            </div>
          </div>
        </div>
      )}

      {/* Grand Total */}
      {(hasTimeEntries || hasExpenses) && (
        <div className="flex items-center justify-between border-t border-slate-300 pt-3 dark:border-slate-700">
          <span className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Grand Total
          </span>
          <div className="flex gap-3">
            {Object.entries(combinedTotals).map(([curr, amount]) => (
              <span
                key={curr}
                className="text-base font-bold text-slate-900 dark:text-slate-100"
              >
                {safeFormatCurrency(amount, curr)}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
