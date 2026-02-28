"use client";

import {
  CheckCircle2,
  Clock,
  XCircle,
  AlertCircle,
  CreditCard,
} from "lucide-react";

import type { PaymentEvent } from "@/lib/types";
import { formatCurrency, formatComplianceDateWithTime } from "@/lib/format";
import { cn } from "@/lib/utils";

interface PaymentHistoryProps {
  payments: PaymentEvent[];
  currency: string;
}

const statusConfig: Record<
  string,
  { icon: typeof CheckCircle2; color: string; label: string }
> = {
  COMPLETED: {
    icon: CheckCircle2,
    color: "text-emerald-600",
    label: "Completed",
  },
  PENDING: { icon: Clock, color: "text-amber-500", label: "Pending" },
  CREATED: { icon: CreditCard, color: "text-blue-500", label: "Created" },
  FAILED: { icon: XCircle, color: "text-red-500", label: "Failed" },
  EXPIRED: { icon: AlertCircle, color: "text-slate-400", label: "Expired" },
  CANCELLED: { icon: XCircle, color: "text-slate-400", label: "Cancelled" },
};

export function PaymentHistory({ payments, currency }: PaymentHistoryProps) {
  if (payments.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <CreditCard className="mb-3 size-10 text-slate-300" />
        <p className="text-sm font-medium text-slate-700">
          No payment events yet
        </p>
        <p className="mt-1 text-sm text-slate-500">
          Payment events will appear here once a payment is initiated or
          recorded.
        </p>
      </div>
    );
  }

  return (
    <div className="relative">
      {/* Timeline line */}
      <div className="absolute left-4 top-0 h-full w-px bg-slate-200" />

      <ul className="space-y-6">
        {payments
          .sort(
            (a, b) =>
              new Date(b.createdAt).getTime() -
              new Date(a.createdAt).getTime(),
          )
          .map((event) => {
            const config = statusConfig[event.status] ?? statusConfig.CREATED;
            const Icon = config.icon;

            return (
              <li key={event.id} className="relative flex gap-4 pl-4">
                {/* Dot */}
                <div
                  className={cn(
                    "relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full bg-white",
                    config.color,
                  )}
                >
                  <Icon className="size-4" />
                </div>

                {/* Content */}
                <div className="flex-1 pt-0.5">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-medium text-slate-900">
                      {config.label}
                    </p>
                    <span className="font-mono text-sm font-semibold tabular-nums text-slate-900">
                      {formatCurrency(event.amount, event.currency || currency)}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {formatComplianceDateWithTime(event.createdAt)}
                  </p>
                  {event.paymentReference && (
                    <p className="mt-1 text-xs text-slate-500">
                      Ref: {event.paymentReference}
                    </p>
                  )}
                  {event.providerSlug && (
                    <p className="mt-0.5 text-xs text-slate-400">
                      via {event.providerSlug}
                    </p>
                  )}
                </div>
              </li>
            );
          })}
      </ul>
    </div>
  );
}
