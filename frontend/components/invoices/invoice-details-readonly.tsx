"use client";

import { formatDate } from "@/lib/format";
import type { InvoiceResponse, TaxType } from "@/lib/types";

interface InvoiceDetailsReadonlyProps {
  invoice: InvoiceResponse;
}

const TAX_TYPE_LABEL: Record<TaxType, string> = {
  VAT: "VAT",
  GST: "GST",
  SALES_TAX: "Sales Tax",
  NONE: "None",
};

export function InvoiceDetailsReadonly({ invoice }: InvoiceDetailsReadonlyProps) {
  const hasBillingPeriod = Boolean(invoice.billingPeriodStart || invoice.billingPeriodEnd);

  return (
    <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h2 className="mb-4 font-semibold text-slate-900 dark:text-slate-100">Invoice Details</h2>
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        {invoice.dueDate && (
          <div>
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">Due Date</dt>
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
            <dd className="text-sm text-slate-900 dark:text-slate-100">{invoice.paymentTerms}</dd>
          </div>
        )}
        {invoice.poNumber && (
          <div>
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">PO Number</dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">{invoice.poNumber}</dd>
          </div>
        )}
        {invoice.taxType && (
          <div>
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">Tax Type</dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">
              {TAX_TYPE_LABEL[invoice.taxType]}
            </dd>
          </div>
        )}
        {hasBillingPeriod && (
          <div className="sm:col-span-2">
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">
              Billing Period
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">
              {invoice.billingPeriodStart ? formatDate(invoice.billingPeriodStart) : "—"}
              {" to "}
              {invoice.billingPeriodEnd ? formatDate(invoice.billingPeriodEnd) : "—"}
            </dd>
          </div>
        )}
        {invoice.notes && (
          <div className="sm:col-span-2">
            <dt className="text-sm font-medium text-slate-600 dark:text-slate-400">Notes</dt>
            <dd className="text-sm text-slate-900 dark:text-slate-100">{invoice.notes}</dd>
          </div>
        )}
      </dl>
    </div>
  );
}
