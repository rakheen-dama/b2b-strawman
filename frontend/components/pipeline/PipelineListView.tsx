"use client";

import Link from "next/link";
import { formatCurrency, formatDate } from "@/lib/format";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import type { DealResponse } from "@/lib/api/crm";

export interface PipelineListViewProps {
  slug: string;
  deals: DealResponse[];
  customerNames: Record<string, string>;
  ownerNames: Record<string, string>;
}

function StatusBadge({ status }: { status: DealResponse["status"] }) {
  const styles: Record<DealResponse["status"], string> = {
    OPEN: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
    WON: "bg-teal-100 text-teal-700 dark:bg-teal-900/50 dark:text-teal-300",
    LOST: "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${styles[status]}`}>
      {status}
    </span>
  );
}

export function PipelineListView({
  slug,
  deals,
  customerNames,
  ownerNames,
}: PipelineListViewProps) {
  if (deals.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-300 px-4 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        No deals match the current filters.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            {["Deal", "Customer", "Stage", "Status", "Value", "Prob.", "Owner", "Close"].map(
              (h) => (
                <th
                  key={h}
                  className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400"
                >
                  {h}
                </th>
              )
            )}
          </tr>
        </thead>
        <tbody>
          {deals.map((deal) => (
            <tr
              key={deal.id}
              className="group border-b border-slate-100 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
            >
              <td className="px-4 py-3">
                <Link
                  href={`/org/${slug}/pipeline/${deal.id}`}
                  className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                >
                  {deal.title}
                </Link>
                <p className="text-xs text-slate-400">{deal.dealNumber}</p>
              </td>
              <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                {customerNames[deal.customerId] ?? "—"}
              </td>
              <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                {deal.stageName ?? "—"}
              </td>
              <td className="px-4 py-3">
                <StatusBadge status={deal.status} />
              </td>
              <td className="px-4 py-3 text-sm text-slate-900 dark:text-slate-100">
                {deal.valueAmount != null
                  ? formatCurrency(deal.valueAmount, deal.valueCurrency)
                  : "—"}
              </td>
              <td className="px-4 py-3 text-sm text-slate-500 dark:text-slate-400">
                {deal.effectiveProbabilityPct}%
              </td>
              <td className="px-4 py-3">
                {deal.ownerId && ownerNames[deal.ownerId] ? (
                  <div className="flex items-center gap-1.5">
                    <AvatarCircle name={ownerNames[deal.ownerId]} size={20} />
                    <span className="truncate text-sm text-slate-700 dark:text-slate-300">
                      {ownerNames[deal.ownerId]}
                    </span>
                  </div>
                ) : (
                  <span className="text-sm text-slate-400">—</span>
                )}
              </td>
              <td className="px-4 py-3 text-sm text-slate-500 dark:text-slate-400">
                {deal.expectedCloseDate ? formatDate(deal.expectedCloseDate) : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
