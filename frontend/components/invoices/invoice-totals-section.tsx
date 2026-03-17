"use client";

import { formatCurrency } from "@/lib/format";
import type { InvoiceResponse } from "@/lib/types";

interface InvoiceTotalsSectionProps {
  invoice: InvoiceResponse;
}

export function InvoiceTotalsSection({ invoice }: InvoiceTotalsSectionProps) {
  return (
    <div className="flex justify-end">
      <div className="w-full max-w-xs space-y-2">
        <div className="flex justify-between text-sm text-slate-600 dark:text-slate-400">
          <span>Subtotal</span>
          <span>{formatCurrency(invoice.subtotal, invoice.currency)}</span>
        </div>

        {/* Tax Breakdown (when hasPerLineTax) */}
        {invoice.hasPerLineTax && invoice.taxBreakdown?.length > 0 ? (
          invoice.taxBreakdown.map((entry, idx) => (
            <div
              key={idx}
              className="flex justify-between text-sm text-slate-600 dark:text-slate-400"
            >
              <span>
                {entry.taxRateName} ({entry.taxRatePercent}%)
              </span>
              <span>
                {formatCurrency(entry.taxAmount, invoice.currency)}
              </span>
            </div>
          ))
        ) : (
          <div className="flex justify-between text-sm text-slate-600 dark:text-slate-400">
            <span>{invoice.taxLabel ?? "Tax"}</span>
            <span>
              {formatCurrency(invoice.taxAmount, invoice.currency)}
            </span>
          </div>
        )}

        {/* Tax-inclusive indicator */}
        {invoice.taxInclusive && invoice.taxLabel && (
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Prices include {invoice.taxLabel}
          </p>
        )}

        <div className="flex justify-between border-t border-slate-200 pt-2 font-semibold text-slate-900 dark:border-slate-800 dark:text-slate-100">
          <span>Total</span>
          <span>{formatCurrency(invoice.total, invoice.currency)}</span>
        </div>
      </div>
    </div>
  );
}
