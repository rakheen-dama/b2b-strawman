"use client";

import { useParams, useRouter } from "next/navigation";
import { Receipt } from "lucide-react";

import type { InvoiceResponse } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";

interface CustomerInvoicesTabProps {
  invoices: InvoiceResponse[];
}

export function CustomerInvoicesTab({ invoices }: CustomerInvoicesTabProps) {
  const router = useRouter();
  const params = useParams<{ slug: string }>();

  if (invoices.length === 0) {
    return (
      <div className="flex min-h-[200px] flex-col items-center justify-center rounded-lg bg-slate-50/50 px-6 py-12 text-center">
        <Receipt className="mb-3 size-10 text-slate-400" />
        <h3 className="text-base font-semibold text-slate-700">
          No invoices yet
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Invoices for this customer will appear here.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50/50 text-left">
            <th className="px-4 py-2.5 font-medium text-slate-600">
              Invoice #
            </th>
            <th className="px-4 py-2.5 font-medium text-slate-600">Status</th>
            <th className="px-4 py-2.5 font-medium text-slate-600 text-right">
              Amount
            </th>
            <th className="px-4 py-2.5 font-medium text-slate-600">
              Issue Date
            </th>
            <th className="px-4 py-2.5 font-medium text-slate-600">
              Due Date
            </th>
          </tr>
        </thead>
        <tbody>
          {invoices.map((invoice) => (
            <tr
              key={invoice.id}
              className="border-b border-slate-100 last:border-0 cursor-pointer transition-colors hover:bg-slate-50"
              onClick={() =>
                router.push(`/org/${params.slug}/invoices/${invoice.id}`)
              }
            >
              <td className="px-4 py-2.5 font-mono text-xs text-slate-700">
                {invoice.invoiceNumber ?? "--"}
              </td>
              <td className="px-4 py-2.5">
                <StatusBadge status={invoice.status} />
              </td>
              <td className="px-4 py-2.5 text-right font-mono tabular-nums text-slate-900">
                {invoice.currency}{" "}
                {Number(invoice.total).toLocaleString("en-ZA", {
                  minimumFractionDigits: 2,
                })}
              </td>
              <td className="px-4 py-2.5 text-slate-500">
                {invoice.issueDate ? formatDate(invoice.issueDate) : "--"}
              </td>
              <td className="px-4 py-2.5 text-slate-500">
                {invoice.dueDate ? formatDate(invoice.dueDate) : "--"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
