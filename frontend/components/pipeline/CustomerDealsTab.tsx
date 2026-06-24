"use client";

import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { IntakeDialog } from "@/components/pipeline/IntakeDialog";
import { formatCurrency, formatDate } from "@/lib/format";
import { KanbanSquare } from "lucide-react";
import type { DealResponse, DealStatus, StageDto } from "@/lib/api/crm";

const STATUS_BADGE: Record<
  DealStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" }
> = {
  OPEN: { label: "Open", variant: "lead" },
  WON: { label: "Won", variant: "success" },
  LOST: { label: "Lost", variant: "destructive" },
};

interface CustomerDealsTabProps {
  slug: string;
  customerId: string;
  customerName: string;
  deals: DealResponse[];
  stages: StageDto[];
  canManage: boolean;
}

export function CustomerDealsTab({
  slug,
  customerId,
  customerName,
  deals,
  stages,
  canManage,
}: CustomerDealsTabProps) {
  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">Deals</h2>
        {deals.length > 0 && <Badge variant="neutral">{deals.length}</Badge>}
      </div>
      {canManage && (
        <IntakeDialog
          slug={slug}
          customers={[{ id: customerId, name: customerName }]}
          stages={stages}
        />
      )}
    </div>
  );

  if (deals.length === 0) {
    return (
      <div className="space-y-4" data-testid="customer-deals-tab">
        {header}
        <EmptyState
          icon={KanbanSquare}
          title="No deals yet"
          description="Create an enquiry to start tracking a deal for this customer."
        />
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="customer-deals-tab">
      {header}
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Deal
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase sm:table-cell dark:text-slate-400">
                Stage
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Status
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Value
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase lg:table-cell dark:text-slate-400">
                Created
              </th>
            </tr>
          </thead>
          <tbody>
            {deals.map((deal) => {
              const badge = STATUS_BADGE[deal.status] ?? {
                label: deal.status,
                variant: "neutral" as const,
              };
              return (
                <tr
                  key={deal.id}
                  className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/pipeline/${deal.id}`}
                      className="font-medium text-slate-950 hover:text-teal-600 dark:text-slate-50 dark:hover:text-teal-400"
                    >
                      {deal.title}
                    </Link>
                    <p className="font-mono text-xs text-slate-500 dark:text-slate-400">
                      {deal.dealNumber}
                    </p>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                    {deal.stageName ?? "—"}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={badge.variant} data-testid="customer-deal-status-badge">
                      {badge.label}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 font-mono text-sm text-slate-600 tabular-nums dark:text-slate-400">
                    {deal.valueAmount != null
                      ? formatCurrency(deal.valueAmount, deal.valueCurrency || "ZAR")
                      : "—"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-slate-400 lg:table-cell dark:text-slate-600">
                    {formatDate(deal.createdAt)}
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
