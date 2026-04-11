import { BillingRunItemStatusBadge } from "@/components/billing-runs/billing-run-item-status-badge";
import { formatCurrency } from "@/lib/format";
import type { BillingRunItem } from "@/lib/api/billing-runs";
import Link from "next/link";

interface BillingRunItemsTableProps {
  items: BillingRunItem[];
  currency: string;
  slug: string;
}

export function BillingRunItemsTable({ items, currency, slug }: BillingRunItemsTableProps) {
  if (items.length === 0) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400">No items in this billing run.</p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200/80 dark:border-slate-800/80">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200/80 bg-slate-50/50 dark:border-slate-800/80 dark:bg-slate-900/50">
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Customer
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Status
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">
              Time Amount
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">
              Expense Amount
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600 dark:text-slate-400">
              Total
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Invoice
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Failure Reason
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200/80 dark:divide-slate-800/80">
          {items.map((item) => (
            <tr key={item.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/50">
              <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                {item.customerName}
              </td>
              <td className="px-4 py-3">
                <BillingRunItemStatusBadge status={item.status} />
              </td>
              <td className="px-4 py-3 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                {formatCurrency(item.unbilledTimeAmount, currency)}
              </td>
              <td className="px-4 py-3 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                {formatCurrency(item.unbilledExpenseAmount, currency)}
              </td>
              <td className="px-4 py-3 text-right font-mono font-medium text-slate-900 tabular-nums dark:text-slate-100">
                {formatCurrency(item.totalUnbilledAmount, currency)}
              </td>
              <td className="px-4 py-3">
                {item.invoiceId ? (
                  <Link
                    href={`/org/${slug}/invoices/${item.invoiceId}`}
                    className="text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
                  >
                    View Invoice
                  </Link>
                ) : (
                  <span className="text-slate-400 dark:text-slate-500">—</span>
                )}
              </td>
              <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                {item.failureReason ?? "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
