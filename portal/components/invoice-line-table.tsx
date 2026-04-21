import { formatCurrency } from "@/lib/format";
import type { PortalInvoiceLine, TaxBreakdownEntry } from "@/lib/types";

interface InvoiceLineTableProps {
  lines: PortalInvoiceLine[];
  currency: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  hasPerLineTax?: boolean;
  taxBreakdown?: TaxBreakdownEntry[] | null;
  taxLabel?: string | null;
  taxInclusive?: boolean;
}

export function InvoiceLineTable({
  lines,
  currency,
  subtotal,
  taxAmount,
  total,
  hasPerLineTax = false,
  taxBreakdown,
  taxLabel,
  taxInclusive = false,
}: InvoiceLineTableProps) {
  const colCount = hasPerLineTax ? 5 : 4;

  return (
    <>
      {/* Mobile: Card layout */}
      <div
        data-testid="invoice-lines-mobile"
        className="flex flex-col gap-3 md:hidden"
      >
        {lines.map((line) => (
          <div
            key={line.id}
            className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-4"
          >
            <p className="text-sm font-medium text-slate-900">
              {line.description}
            </p>
            <div className="flex items-center justify-between text-xs text-slate-500">
              <span>
                {line.quantity} &times; {formatCurrency(line.unitPrice, currency)}
              </span>
              {hasPerLineTax && (
                <span>
                  {line.taxExempt
                    ? "Exempt"
                    : line.taxRateName
                      ? `${line.taxRateName} ${line.taxRatePercent}%`
                      : ""}
                </span>
              )}
            </div>
            <div className="flex items-center justify-between border-t border-slate-100 pt-2">
              <span className="text-xs font-medium text-slate-500">Amount</span>
              <span className="text-sm font-semibold text-slate-900">
                {formatCurrency(line.amount, currency)}
              </span>
            </div>
          </div>
        ))}

        {/* Mobile totals block */}
        <div className="flex flex-col gap-1 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-slate-600">Subtotal</span>
            <span className="text-slate-900">
              {formatCurrency(subtotal, currency)}
            </span>
          </div>
          {hasPerLineTax && taxBreakdown && taxBreakdown.length > 0 ? (
            taxBreakdown.map((entry) => (
              <div
                key={entry.rateName}
                className="flex items-center justify-between"
              >
                <span className="text-slate-600">
                  {entry.rateName} ({entry.ratePercent}%)
                </span>
                <span className="text-slate-900">
                  {formatCurrency(entry.taxAmount, currency)}
                </span>
              </div>
            ))
          ) : (
            <div className="flex items-center justify-between">
              <span className="text-slate-600">Tax</span>
              <span className="text-slate-900">
                {formatCurrency(taxAmount, currency)}
              </span>
            </div>
          )}
          {taxInclusive && hasPerLineTax && (
            <p className="text-xs italic text-slate-500">
              All amounts include {taxLabel || "Tax"}
            </p>
          )}
          <div className="mt-1 flex items-center justify-between border-t border-slate-200 pt-2">
            <span className="font-semibold text-slate-900">Total</span>
            <span className="font-semibold text-slate-900">
              {formatCurrency(total, currency)}
            </span>
          </div>
        </div>
      </div>

      {/* Desktop: Table layout */}
      <div
        data-testid="invoice-lines-desktop"
        className="hidden overflow-x-auto rounded-lg border border-slate-200 md:block"
      >
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-4 py-3 text-left font-medium text-slate-600">
                Description
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600">
                Quantity
              </th>
              <th className="px-4 py-3 text-right font-medium text-slate-600">
                Rate
              </th>
              {hasPerLineTax && (
                <th className="px-4 py-3 text-right font-medium text-slate-600">
                  {taxLabel || "Tax"}
                </th>
              )}
              <th className="px-4 py-3 text-right font-medium text-slate-600">
                Amount
              </th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <tr
                key={line.id}
                className="border-b border-slate-100 last:border-b-0"
              >
                <td className="px-4 py-3 text-slate-900">{line.description}</td>
                <td className="px-4 py-3 text-right text-slate-700">
                  {line.quantity}
                </td>
                <td className="px-4 py-3 text-right text-slate-700">
                  {formatCurrency(line.unitPrice, currency)}
                </td>
                {hasPerLineTax && (
                  <td className="px-4 py-3 text-right text-slate-700">
                    {line.taxExempt
                      ? "Exempt"
                      : line.taxRateName
                        ? `${line.taxRateName} ${line.taxRatePercent}%`
                        : ""}
                  </td>
                )}
                <td className="px-4 py-3 text-right text-slate-900">
                  {formatCurrency(line.amount, currency)}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-slate-200">
              <td
                colSpan={colCount - 1}
                className="px-4 py-2 text-right text-slate-600"
              >
                Subtotal
              </td>
              <td className="px-4 py-2 text-right text-slate-900">
                {formatCurrency(subtotal, currency)}
              </td>
            </tr>
            {hasPerLineTax && taxBreakdown && taxBreakdown.length > 0 ? (
              taxBreakdown.map((entry) => (
                <tr key={entry.rateName}>
                  <td
                    colSpan={colCount - 1}
                    className="px-4 py-2 text-right text-slate-600"
                  >
                    {entry.rateName} ({entry.ratePercent}%)
                  </td>
                  <td className="px-4 py-2 text-right text-slate-900">
                    {formatCurrency(entry.taxAmount, currency)}
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td
                  colSpan={colCount - 1}
                  className="px-4 py-2 text-right text-slate-600"
                >
                  Tax
                </td>
                <td className="px-4 py-2 text-right text-slate-900">
                  {formatCurrency(taxAmount, currency)}
                </td>
              </tr>
            )}
            {taxInclusive && hasPerLineTax && (
              <tr>
                <td
                  colSpan={colCount}
                  className="px-4 py-2 text-right text-sm italic text-slate-500"
                >
                  All amounts include {taxLabel || "Tax"}
                </td>
              </tr>
            )}
            <tr className="border-t border-slate-200">
              <td
                colSpan={colCount - 1}
                className="px-4 py-3 text-right font-semibold text-slate-900"
              >
                Total
              </td>
              <td className="px-4 py-3 text-right font-semibold text-slate-900">
                {formatCurrency(total, currency)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </>
  );
}
