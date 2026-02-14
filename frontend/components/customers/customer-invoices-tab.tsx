"use client";

import { FileText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { InvoiceGenerationDialog } from "@/components/invoices/invoice-generation-dialog";
import { formatDate, formatCurrency } from "@/lib/format";
import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";
import Link from "next/link";

const STATUS_BADGE: Record<
  InvoiceStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  APPROVED: { label: "Approved", variant: "lead" },
  SENT: { label: "Sent", variant: "lead" },
  PAID: { label: "Paid", variant: "success" },
  VOID: { label: "Void", variant: "destructive" },
};

interface CustomerInvoicesTabProps {
  invoices: InvoiceResponse[];
  customerId: string;
  customerName: string;
  slug: string;
  canManage: boolean;
  defaultCurrency: string;
}

export function CustomerInvoicesTab({
  invoices,
  customerId,
  customerName,
  slug,
  canManage,
  defaultCurrency,
}: CustomerInvoicesTabProps) {
  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">Invoices</h2>
        {invoices.length > 0 && <Badge variant="neutral">{invoices.length}</Badge>}
      </div>
      {canManage && (
        <InvoiceGenerationDialog
          customerId={customerId}
          customerName={customerName}
          slug={slug}
          defaultCurrency={defaultCurrency}
        />
      )}
    </div>
  );

  if (invoices.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <EmptyState
          icon={FileText}
          title="No invoices yet"
          description="Generate an invoice from unbilled time entries"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-olive-200 dark:border-olive-800">
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Invoice
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
            </tr>
          </thead>
          <tbody>
            {invoices.map((invoice) => {
              const badge = STATUS_BADGE[invoice.status];

              return (
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
                  <td className="px-4 py-3">
                    <Badge variant={badge.variant}>{badge.label}</Badge>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                    {invoice.issueDate ? formatDate(invoice.issueDate) : "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-400 lg:table-cell dark:text-olive-600">
                    {invoice.dueDate ? formatDate(invoice.dueDate) : "\u2014"}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium text-olive-900 dark:text-olive-100">
                    {formatCurrency(invoice.total, invoice.currency)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
