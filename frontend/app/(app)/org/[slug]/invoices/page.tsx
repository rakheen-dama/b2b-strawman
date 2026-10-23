import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { listClients } from "@/lib/api/clients";
import { getOrgSettings } from "@/lib/api/settings";
import type {
  InvoiceResponse,
  InvoiceStatus,
  OutstandingSummary,
  OutstandingSummaryResult,
} from "@/lib/types";
import { StatusBadge } from "@/components/invoices/status-badge";
import { CreateInvoiceButton } from "@/components/invoices/create-invoice-button";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { formatCurrency, formatDate } from "@/lib/format";
import { Receipt, CreditCard } from "lucide-react";
import Link from "next/link";
import { HelpTip } from "@/components/help-tip";
import { fetchOutstandingSummaryAction } from "./invoice-crud-actions";

/**
 * "Paid This Month" — the one tile still computed from the list. The debtor-book tiles read
 * GET /api/invoices/outstanding-summary (Phase 88 U3, ADR-347), never a client-side sum.
 */
function computePaidThisMonth(invoices: InvoiceResponse[], fallbackCurrency: string) {
  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

  // Use the first invoice's currency as the default, falling back to the org's
  // configured default currency (ZAR for legal-za tenants) instead of USD.
  let paidCurrency = invoices.length > 0 ? invoices[0].currency : fallbackCurrency;
  let paidThisMonth = 0;

  for (const inv of invoices) {
    if (inv.status === "PAID" && inv.paidAt) {
      const paidDate = new Date(inv.paidAt);
      if (paidDate >= monthStart) {
        paidThisMonth += inv.total;
        paidCurrency = inv.currency;
      }
    }
  }

  return { paidThisMonth, paidCurrency };
}

const DASH = "\u2014";

function SummaryTile({
  label,
  value,
  valueClassName,
  testId,
}: {
  label: string;
  value: string;
  valueClassName: string;
  testId: string;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <p className="text-sm font-medium text-slate-600 dark:text-slate-400">{label}</p>
      <p data-testid={testId} className={`mt-1 text-2xl font-semibold ${valueClassName}`}>
        {value}
      </p>
    </div>
  );
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

  // The debtor-book summary needs FINANCIAL_VISIBILITY (admin/owner inherit it); the page only
  // needs INVOICING. Without it the tiles are absent — no request, no guaranteed 403.
  const canViewFinancials =
    capData.isAdmin || capData.isOwner || capData.capabilities.includes("FINANCIAL_VISIBILITY");
  const summaryResult: OutstandingSummaryResult = canViewFinancials
    ? await fetchOutstandingSummaryAction()
    : { state: "absent" };

  let invoices: InvoiceResponse[] = [];
  try {
    const queryParams = new URLSearchParams();
    if (search.status) queryParams.set("status", search.status);
    if (search.customerId) queryParams.set("clientId", search.customerId);
    const qs = queryParams.toString();
    invoices = await api.get<InvoiceResponse[]>(`/api/invoices${qs ? `?${qs}` : ""}`);
  } catch {
    // Non-fatal: show empty state
  }

  // Fetch active customers for "New Invoice" button
  let customers: { id: string; name: string }[] = [];
  try {
    const raw = await listClients();
    customers = (Array.isArray(raw) ? raw : [])
      .filter((c) => c.status === "ACTIVE")
      .map((c) => ({ id: c.id, name: c.name }));
  } catch {
    // Non-fatal — button will be hidden if no customers
  }

  // Resolve the org's configured default currency so empty-state KPIs display
  // using the tenant's currency (e.g. ZAR) rather than a hardcoded USD fallback.
  let fallbackCurrency = "USD";
  try {
    const orgSettings = await getOrgSettings();
    fallbackCurrency = orgSettings.defaultCurrency ?? "USD";
  } catch {
    // Non-fatal — fall through to USD.
  }

  // "Paid This Month" from the listed fee notes (when no filter applied, or from filtered set)
  const paid = computePaidThisMonth(invoices, fallbackCurrency);
  const summaryMoney = (pick: (summary: OutstandingSummary) => number) =>
    summaryResult.state === "ok"
      ? formatCurrency(pick(summaryResult.summary), summaryResult.summary.currency)
      : DASH;

  const { t } = createMessages("empty-states");

  const statusOptions: InvoiceStatus[] = ["DRAFT", "APPROVED", "SENT", "PAID", "VOID"];

  // Validate search.status against the whitelist — reject unknown values
  const validatedStatus = statusOptions.includes(search.status as InvoiceStatus)
    ? (search.status as InvoiceStatus)
    : undefined;

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
            Fee notes
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
          Fee notes
        </Link>
        <Link
          href={`/org/${slug}/invoices/billing-runs`}
          className="border-b-2 border-transparent px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
        >
          Billing Runs
        </Link>
      </div>

      {/* Summary Cards — debtor-book tiles from GET /api/invoices/outstanding-summary (ADR-347) */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {summaryResult.state !== "absent" && (
          <>
            <SummaryTile
              label="Total outstanding"
              testId="fee-notes-total-outstanding"
              value={summaryMoney((summary) => summary.outstandingTotal)}
              valueClassName="text-slate-900 dark:text-slate-100"
            />
            <SummaryTile
              label="Overdue"
              testId="fee-notes-overdue"
              value={summaryMoney((summary) => summary.overdueTotal)}
              valueClassName="text-red-600 dark:text-red-400"
            />
            <SummaryTile
              label="Approved, not sent"
              testId="fee-notes-approved-unsent"
              value={summaryMoney((summary) => summary.approvedUnsentTotal)}
              valueClassName="text-slate-900 dark:text-slate-100"
            />
          </>
        )}
        <SummaryTile
          label="Paid This Month"
          testId="fee-notes-paid-this-month"
          value={formatCurrency(paid.paidThisMonth, paid.paidCurrency)}
          valueClassName="text-green-600 dark:text-green-400"
        />
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-medium text-slate-600 dark:text-slate-400">Filter:</span>
        <Link
          href={`/org/${slug}/invoices`}
          className={`rounded-full px-3 py-1 text-sm transition-colors ${
            !validatedStatus
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
              validatedStatus === status
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
          title={
            validatedStatus
              ? `No ${validatedStatus.toLowerCase()} fee notes found`
              : t("invoices.list.heading")
          }
          description={
            validatedStatus
              ? `No ${validatedStatus.toLowerCase()} fee notes found.`
              : t("invoices.list.description")
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Fee note
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Client
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
                    {invoice.clientName}
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
