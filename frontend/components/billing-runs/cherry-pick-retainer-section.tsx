"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { RetainerPeriodPreview } from "@/lib/api/billing-runs";

interface CherryPickRetainerSectionProps {
  retainers: RetainerPeriodPreview[];
  includedRetainerIds: Set<string>;
  currency: string;
  onToggleRetainer: (agreementId: string) => void;
}

export function CherryPickRetainerSection({
  retainers,
  includedRetainerIds,
  currency,
  onToggleRetainer,
}: CherryPickRetainerSectionProps) {
  return (
    <div className="border-t border-slate-200 p-6 dark:border-slate-700">
      <h3 className="mb-3 text-sm font-semibold text-slate-950 dark:text-slate-50">
        Retainer Agreements
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left dark:border-slate-700">
              <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Include</th>
              <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Customer</th>
              <th className="pb-2 pr-3 font-medium text-slate-500 dark:text-slate-400">Period</th>
              <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">Consumed Hours</th>
              <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">Estimated Amount</th>
            </tr>
          </thead>
          <tbody>
            {retainers.map((retainer) => (
              <tr key={retainer.agreementId} className="border-b border-slate-100 dark:border-slate-800">
                <td className="py-2 pr-3">
                  <Checkbox
                    checked={includedRetainerIds.has(retainer.agreementId)}
                    onCheckedChange={() => onToggleRetainer(retainer.agreementId)}
                    aria-label={`Include retainer for ${retainer.customerName}`}
                  />
                </td>
                <td className="py-2 pr-3 font-medium text-slate-950 dark:text-slate-50">{retainer.customerName}</td>
                <td className="py-2 pr-3 text-slate-600 dark:text-slate-400">
                  {formatLocalDate(retainer.periodStart)} &mdash; {formatLocalDate(retainer.periodEnd)}
                </td>
                <td className="py-2 pr-3 text-right text-slate-600 dark:text-slate-400">{retainer.consumedHours.toFixed(1)}</td>
                <td className="py-2 text-right font-medium text-slate-950 dark:text-slate-50">{formatCurrency(retainer.estimatedAmount, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
