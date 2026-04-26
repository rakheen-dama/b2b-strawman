"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Download, FileText } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatCurrency, formatDate } from "@/lib/format";
import { InvoiceStatusBadge } from "@/components/invoice-status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useTerminology } from "@/lib/terminology";
import type { PortalInvoice, PortalDownload } from "@/lib/types";

function TableSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

export default function InvoicesPage() {
  const { t } = useTerminology();
  const [invoices, setInvoices] = useState<PortalInvoice[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchInvoices = useCallback(async () => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalInvoice[]>("/portal/invoices");
      setInvoices(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load invoices",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInvoices();
  }, [fetchInvoices]);

  async function handleDownload(invoiceId: string) {
    try {
      const data = await portalGet<PortalDownload>(
        `/portal/invoices/${invoiceId}/download`,
      );
      window.open(data.downloadUrl, "_blank");
    } catch {
      alert("Download failed. Please try again.");
    }
  }

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        {t("Invoices")}
      </h1>

      {isLoading && <TableSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p className="mb-2">{error}</p>
          <button
            type="button"
            onClick={() => fetchInvoices()}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
          >
            Try again
          </button>
        </div>
      )}

      {!isLoading && !error && invoices.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <FileText className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">
            No {t("invoices")} yet.
          </p>
        </div>
      )}

      {!isLoading && !error && invoices.length > 0 && (
        <>
          {/* Mobile: Card layout */}
          <div
            data-testid="invoices-list-mobile"
            className="flex flex-col gap-3 md:hidden"
          >
            {invoices.map((invoice) => (
              <div
                key={invoice.id}
                className="flex flex-col gap-3 rounded-lg border border-slate-200/80 bg-white p-4 shadow-sm"
              >
                <div className="flex items-start justify-between gap-3">
                  <Link
                    href={`/invoices/${invoice.id}`}
                    className="inline-flex min-h-11 items-center font-medium text-teal-600 hover:text-teal-700 hover:underline"
                  >
                    {invoice.invoiceNumber}
                  </Link>
                  <InvoiceStatusBadge status={invoice.status} />
                </div>
                <div className="flex flex-col gap-1 text-xs text-slate-500">
                  <span>Issued {formatDate(invoice.issueDate)}</span>
                  <span>Due {formatDate(invoice.dueDate)}</span>
                </div>
                <div className="flex items-center justify-between border-t border-slate-100 pt-3">
                  <span className="text-base font-semibold text-slate-900">
                    {formatCurrency(invoice.total, invoice.currency)}
                  </span>
                  <div className="flex items-center gap-3">
                    <Link
                      href={`/invoices/${invoice.id}`}
                      className="inline-flex min-h-11 items-center text-sm text-slate-600 hover:text-slate-900"
                    >
                      View
                    </Link>
                    <button
                      onClick={() => handleDownload(invoice.id)}
                      className="inline-flex min-h-11 items-center gap-1 text-sm text-teal-600 hover:text-teal-700"
                      aria-label={`Download ${invoice.invoiceNumber}`}
                    >
                      <Download className="size-4" />
                      PDF
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Desktop: Table layout */}
          <div
            data-testid="invoices-list-desktop"
            className="hidden overflow-x-auto rounded-lg border border-slate-200 md:block"
          >
            <table className="w-full text-sm" aria-label={`${t("Invoice")} list`}>
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-4 py-3 text-left font-medium text-slate-600">
                    {t("Invoice")} #
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600">
                    Status
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600">
                    Issue Date
                  </th>
                  <th className="px-4 py-3 text-left font-medium text-slate-600">
                    Due Date
                  </th>
                  <th className="px-4 py-3 text-right font-medium text-slate-600">
                    Total
                  </th>
                  <th className="px-4 py-3 text-right font-medium text-slate-600">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((invoice) => (
                  <tr
                    key={invoice.id}
                    className="border-b border-slate-100 last:border-b-0"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/invoices/${invoice.id}`}
                        className="inline-flex min-h-[44px] items-center font-medium text-teal-600 hover:text-teal-700 hover:underline"
                      >
                        {invoice.invoiceNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <InvoiceStatusBadge status={invoice.status} />
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {formatDate(invoice.issueDate)}
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {formatDate(invoice.dueDate)}
                    </td>
                    <td className="px-4 py-3 text-right font-medium text-slate-900">
                      {formatCurrency(invoice.total, invoice.currency)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          href={`/invoices/${invoice.id}`}
                          className="inline-flex min-h-[44px] items-center text-sm text-slate-500 hover:text-slate-700"
                        >
                          View
                        </Link>
                        <button
                          onClick={() => handleDownload(invoice.id)}
                          className="inline-flex min-h-[44px] items-center gap-1 text-sm text-teal-600 hover:text-teal-700"
                          aria-label={`Download ${invoice.invoiceNumber}`}
                        >
                          <Download className="size-4" />
                          PDF
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
