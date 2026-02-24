"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Download, FileText } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatCurrency, formatDate } from "@/lib/format";
import { InvoiceStatusBadge } from "@/components/invoice-status-badge";
import { Skeleton } from "@/components/ui/skeleton";
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
  const [invoices, setInvoices] = useState<PortalInvoice[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchInvoices() {
      try {
        const data = await portalGet<PortalInvoice[]>("/portal/invoices");
        if (!cancelled) {
          setInvoices(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load invoices",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchInvoices();

    return () => {
      cancelled = true;
    };
  }, []);

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
        Invoices
      </h1>

      {isLoading && <TableSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!isLoading && !error && invoices.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <FileText className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">
            No invoices yet.
          </p>
        </div>
      )}

      {!isLoading && !error && invoices.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full text-sm" aria-label="Invoice list">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-4 py-3 text-left font-medium text-slate-600">
                  Invoice #
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
                      className="font-medium text-teal-600 hover:text-teal-700 hover:underline"
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
                        className="text-sm text-slate-500 hover:text-slate-700"
                      >
                        View
                      </Link>
                      <button
                        onClick={() => handleDownload(invoice.id)}
                        className="inline-flex items-center gap-1 text-sm text-teal-600 hover:text-teal-700"
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
      )}
    </div>
  );
}
