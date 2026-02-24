import { formatCurrency } from "@/lib/format";
import type { PortalInvoiceLine } from "@/lib/types";

interface InvoiceLineTableProps {
  lines: PortalInvoiceLine[];
  currency: string;
  subtotal: number;
  taxAmount: number;
  total: number;
}

export function InvoiceLineTable({
  lines,
  currency,
  subtotal,
  taxAmount,
  total,
}: InvoiceLineTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
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
              <td className="px-4 py-3 text-right text-slate-900">
                {formatCurrency(line.amount, currency)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t border-slate-200">
            <td colSpan={3} className="px-4 py-2 text-right text-slate-600">
              Subtotal
            </td>
            <td className="px-4 py-2 text-right text-slate-900">
              {formatCurrency(subtotal, currency)}
            </td>
          </tr>
          <tr>
            <td colSpan={3} className="px-4 py-2 text-right text-slate-600">
              Tax
            </td>
            <td className="px-4 py-2 text-right text-slate-900">
              {formatCurrency(taxAmount, currency)}
            </td>
          </tr>
          <tr className="border-t border-slate-200">
            <td
              colSpan={3}
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
  );
}
