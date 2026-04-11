import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";
import type { Customer } from "@/lib/types/customer";
import { StatusBadge } from "@/components/invoices/status-badge";
import { CreateInvoiceButton } from "@/components/invoices/create-invoice-button";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { formatCurrency, formatDate } from "@/lib/format";
import { Receipt, CreditCard } from "lucide-react";
import Link from "next/link";
import { HelpTip } from "@/components/help-tip";
import { TerminologyHeading } from "@/components/terminology-heading";
import { docsLink } from "@/lib/docs";

function computeSummary(invoices: InvoiceResponse[]) {
  const now = new Date();
  const todayStr = now.toLocaleDateString("en-CA"); // YYYY-MM-DD
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

  // Use the first invoice's currency as the default, falling back to USD
  const defaultCurrency = invoices.length > 0 ? invoices[0].currency : "USD";

  let outstanding = 0;
  let overdue = 0;
  let paidThisMonth = 0;
  let outstandingCurrency = defaultCurrency;
  let overdueCurrency = defaultCurrency;
  let paidCurrency = defaultCurrency;

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
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("INVOICING")) {
    notFound();
  }

  let invoices: InvoiceResponse[] = [];
  try {
    const queryParams = new URLSearchParams();
    if (search.status) queryParams.set("status", search.status);
    if (search.customerId) queryParams.set("customerId", search.customerId);
    const qs = queryParams.toString();
    invoices = await api.get<InvoiceResponse[]>(`/api/invoices${qs ? `?${qs}` : ""}`);
  } catch {
    // Non-fatal: show empty state
  }

  // Fetch active customers for "New Invoice" button
  let customers: { id: string; name: string }[] = [];
  try {
    const raw = await api.get<Customer[]>("/api/customers");
    customers = (Array.isArray(raw) ? raw : [])
      .filter((c) => c.status === "ACTIVE")
      .map((c) => ({ id: c.id, name: c.name }));
  } catch {
    // Non-fatal — button will be hidden if no customers
  }

  // Compute summary from all invoices (when no filter applied, or from filtered set)
  const summary = computeSummary(invoices);

  const { t } = createMessages("empty-states");

  const statusOptions: InvoiceStatus[] = ["DRAFT", "APPROVED", "SENT", "PAID", "VOID"];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
            <TerminologyHeading term="Invoices" />
            <HelpTip code="invoices.lifecycle" />
          </h1>
          {invoices.length > 0 && (
            <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {invoices.length}
            </span>
          )}
        </div>
        <CreateInvoiceButton customers={customers} slug={slug} />
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 border-b border-slate-200 dark:border-slate-800">
        <Link
          href={`/org/${slug}/invoices`}
          className="border-b-2 border-slate-900 px-4 py-2 text-sm font-medium text-slate-900 dark:border-slate-100 dark:text-slate-100"
        >
          <TerminologyHeading term="Invoices" />
        </Link>
        <Link
          href={`/org/${slug}/invoices/billing-runs`}
          className="border-b-2 border-transparent px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
        >
          Billing Runs
        </Link>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
          <p className="text-sm font-medium text-slate-600 dark:text-slate-400">
            Total Outstanding
          </p>
          <p className="mt-1 text-2xl font-semibold text-slate-900 dark:text-slate-100">
            {formatCurrency(summary.outstanding, summary.outstandingCurrency)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
          <p className="text-sm font-medium text-slate-600 dark:text-slate-400">Total Overdue</p>
          <p className="mt-1 text-2xl font-semibold text-red-600 dark:text-red-400">
            {formatCurrency(summary.overdue, summary.overdueCurrency)}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
          <p className="text-sm font-medium text-slate-600 dark:text-slate-400">Paid This Month</p>
          <p className="mt-1 text-2xl font-semibold text-green-600 dark:text-green-400">
            {formatCurrency(summary.paidThisMonth, summary.paidCurrency)}
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">Filter:</span>
        <Link
          href={`/org/${slug}/invoices`}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !search.status
              ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
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
                ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
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
          title={search.status ? "No fee notes found" : t("invoices.list.heading")}
          description={
            search.status
              ? `No ${search.status.toLowerCase()} fee notes found.`
              : t("invoices.list.description")
          }
          secondaryLink={{ label: "Read the guide", href: docsLink("/features/invoicing") }}
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Invoice
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
                  Issue Date
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                  Due Date
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Total
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase xl:table-cell dark:text-slate-400">
                  Currency
                </th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr
                  key={invoice.id}
                  className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/invoices/${invoice.id}`}
                      className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                    >
                      {invoice.invoiceNumber ?? "Draft"}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {invoice.customerName}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <StatusBadge status={invoice.status} />
                      {invoice.paymentUrl && (
                        <span
                          title="Online payment enabled"
                          className="text-teal-600 dark:text-teal-400"
                        >
                          <CreditCard className="size-3.5" />
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                    {invoice.issueDate ? formatDate(invoice.issueDate) : "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 lg:table-cell dark:text-slate-400">
                    {invoice.dueDate ? formatDate(invoice.dueDate) : "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrency(invoice.total, invoice.currency)}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 xl:table-cell dark:text-slate-400">
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
