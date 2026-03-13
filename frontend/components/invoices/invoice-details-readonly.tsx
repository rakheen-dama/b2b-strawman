"use client";

import { formatDate } from "@/lib/format";
import type { InvoiceResponse } from "@/lib/types";

interface InvoiceDetailsReadonlyProps {
  invoice: InvoiceResponse;
}

export function InvoiceDetailsReadonly({ invoice }: InvoiceDetailsReadonlyProps) {
  return (
    <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h2 className="mb-4 font-semibold text-slate-900 dark:text-slate-100">
        Invoice Details
      </h2>
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        {invoice.dueDate && (
          <div>
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Due Date
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">
              {formatDate(invoice.dueDate)}
            </dd>
          </div>
        )}
        {invoice.paymentTerms && (
          <div>
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Payment Terms
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">
              {invoice.paymentTerms}
            </dd>
          </div>
        )}
        {invoice.notes && (
          <div className="sm:col-span-2">
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Notes
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">
              {invoice.notes}
            </dd>
          </div>
        )}
      </dl>
    </div>
  );
}
