import { auth } from "@clerk/nextjs/server";
import { api } from "@/lib/api";
import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";
import { StatusBadge } from "@/components/invoices/status-badge";
import { EmptyState } from "@/components/empty-state";
import { formatCurrency, formatDate } from "@/lib/format";
import { Receipt } from "lucide-react";
import Link from "next/link";

function computeSummary(invoices: InvoiceResponse[]) {
  const now = new Date();
  const todayStr = now.toLocaleDateString("en-CA"); // YYYY-MM-DD
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

  let outstanding = 0;
  let overdue = 0;
  let paidThisMonth = 0;
  let outstandingCurrency = "USD";
  let overdueCurrency = "USD";
  let paidCurrency = "USD";

  for (const inv of invoices) {
    if (inv.status === "APPROVED" || inv.status === "SENT") {
      outstanding += inv.total;
      outstandingCurrency = inv.currency;
      if (inv.dueDate && inv.dueDate < todayStr) {
        overdue += inv.total;
        overdueCurrency = inv.currency;
      }
    }
    if (inv.status === "PAID" && inv.paidAt) {
      const paidDate = new Date(inv.paidAt);
      if (paidDate >= monthStart) {
        paidThisMonth += inv.total;
        paidCurrency = inv.currency;
      }
    }
  }

  return {
    outstanding,
    outstandingCurrency,
    overdue,
    overdueCurrency,
    paidThisMonth,
    paidCurrency,
  };
}

export default async function InvoicesPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string; customerId?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          Invoices
        </h1>
        <p className="text-olive-600 dark:text-olive-400">
          You do not have permission to view invoices. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let invoices: InvoiceResponse[] = [];
  try {
    const queryParams = new URLSearchParams();
    if (search.status) queryParams.set("status", search.status);
    if (search.customerId) queryParams.set("customerId", search.customerId);
    const qs = queryParams.toString();
    invoices = await api.get<InvoiceResponse[]>(
      `/api/invoices${qs ? `?${qs}` : ""}`,
    );
  } catch {
    // Non-fatal: show empty state
  }

  // Compute summary from all invoices (when no filter applied, or from filtered set)
  const summary = computeSummary(invoices);

  const statusOptions: InvoiceStatus[] = [
    "DRAFT",
    "APPROVED",
    "SENT",
    "PAID",
    "VOID",
  ];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
            Invoices
          </h1>
          {invoices.length > 0 && (
            <span className="rounded-full bg-olive-200 px-2.5 py-0.5 text-sm text-olive-700 dark:bg-olive-800 dark:text-olive-300">
              {invoices.length}
            </span>
          )}
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-lg border border-olive-200 bg-white p-4 dark:border-olive-800 dark:bg-olive-950">
          <p className="text-sm font-medium text-olive-600 dark:text-olive-400">
            Total Outstanding
          </p>
          <p className="mt-1 text-2xl font-semibold text-olive-900 dark:text-olive-100">
            {formatCurrency(summary.outstanding, summary.outstandingCurrency)}
          </p>
        </div>
        <div className="rounded-lg border border-olive-200 bg-white p-4 dark:border-olive-800 dark:bg-olive-950">
          <p className="text-sm font-medium text-olive-600 dark:text-olive-400">
            Total Overdue
          </p>
          <p className="mt-1 text-2xl font-semibold text-red-600 dark:text-red-400">
            {formatCurrency(summary.overdue, summary.overdueCurrency)}
          </p>
        </div>
        <div className="rounded-lg border border-olive-200 bg-white p-4 dark:border-olive-800 dark:bg-olive-950">
          <p className="text-sm font-medium text-olive-600 dark:text-olive-400">
            Paid This Month
          </p>
          <p className="mt-1 text-2xl font-semibold text-green-600 dark:text-green-400">
            {formatCurrency(summary.paidThisMonth, summary.paidCurrency)}
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-olive-600 dark:text-olive-400">
          Filter:
        </span>
        <Link
          href={`/org/${slug}/invoices`}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !search.status
              ? "bg-olive-900 text-white dark:bg-olive-100 dark:text-olive-900"
              : "bg-olive-100 text-olive-700 hover:bg-olive-200 dark:bg-olive-800 dark:text-olive-300 dark:hover:bg-olive-700"
          }`}
        >
          All
        </Link>
        {statusOptions.map((status) => (
          <Link
            key={status}
            href={`/org/${slug}/invoices?status=${status}`}
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              search.status === status
                ? "bg-olive-900 text-white dark:bg-olive-100 dark:text-olive-900"
                : "bg-olive-100 text-olive-700 hover:bg-olive-200 dark:bg-olive-800 dark:text-olive-300 dark:hover:bg-olive-700"
            }`}
          >
            {status.charAt(0) + status.slice(1).toLowerCase()}
          </Link>
        ))}
      </div>

      {/* Invoice Table or Empty State */}
      {invoices.length === 0 ? (
        <EmptyState
          icon={Receipt}
          title="No invoices found"
          description={
            search.status
              ? `No ${search.status.toLowerCase()} invoices found.`
              : "Create invoices from customer unbilled time."
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-olive-200 dark:border-olive-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Invoice
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Issue Date
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 lg:table-cell dark:text-olive-400">
                  Due Date
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Total
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 xl:table-cell dark:text-olive-400">
                  Currency
                </th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr
                  key={invoice.id}
                  className="group border-b border-olive-100 transition-colors last:border-0 hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/invoices/${invoice.id}`}
                      className="font-medium text-olive-950 hover:underline dark:text-olive-50"
                    >
                      {invoice.invoiceNumber ?? "Draft"}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-olive-600 dark:text-olive-400">
                    {invoice.customerName}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={invoice.status} />
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                    {invoice.issueDate
                      ? formatDate(invoice.issueDate)
                      : "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 lg:table-cell dark:text-olive-400">
                    {invoice.dueDate ? formatDate(invoice.dueDate) : "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium text-olive-900 dark:text-olive-100">
                    {formatCurrency(invoice.total, invoice.currency)}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 xl:table-cell dark:text-olive-400">
                    {invoice.currency}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
