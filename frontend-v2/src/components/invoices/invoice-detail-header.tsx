"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Send,
  Ban,
  CreditCard,
  Loader2,
  MoreHorizontal,
} from "lucide-react";

import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";
import { formatCurrency, formatLocalDate, isOverdue } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface InvoiceDetailHeaderProps {
  invoice: InvoiceResponse;
  orgSlug: string;
  onApprove?: () => Promise<void>;
  onSend?: () => Promise<void>;
  onVoid?: () => Promise<void>;
  onRecordPayment?: () => Promise<void>;
}

export function InvoiceDetailHeader({
  invoice,
  orgSlug,
  onApprove,
  onSend,
  onVoid,
  onRecordPayment,
}: InvoiceDetailHeaderProps) {
  const router = useRouter();
  const [loadingAction, setLoadingAction] = useState<string | null>(null);

  const executeAction = async (name: string, fn?: () => Promise<void>) => {
    if (!fn) return;
    setLoadingAction(name);
    try {
      await fn();
      router.refresh();
    } finally {
      setLoadingAction(null);
    }
  };

  const showOverdue =
    invoice.status === "SENT" &&
    invoice.dueDate &&
    isOverdue(invoice.dueDate);

  return (
    <div className="space-y-4">
      {/* Back link */}
      <Link
        href={`/org/${orgSlug}/invoices`}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 transition-colors hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Invoices
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        {/* Left: Title + metadata */}
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
              {invoice.invoiceNumber ?? "Draft Invoice"}
            </h1>
            <StatusBadge
              status={showOverdue ? "OVERDUE" : invoice.status}
            />
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
            <span>
              <span className="font-medium text-slate-700">
                {invoice.customerName}
              </span>
            </span>
            <span className="font-mono tabular-nums text-lg font-semibold text-slate-900">
              {formatCurrency(invoice.total, invoice.currency)}
            </span>
            {invoice.issueDate && (
              <span>Issued {formatLocalDate(invoice.issueDate)}</span>
            )}
            {invoice.dueDate && (
              <span>Due {formatLocalDate(invoice.dueDate)}</span>
            )}
          </div>
        </div>

        {/* Right: Action buttons */}
        <div className="flex items-center gap-2">
          {invoice.status === "DRAFT" && onApprove && (
            <Button
              size="sm"
              onClick={() => executeAction("approve", onApprove)}
              disabled={!!loadingAction}
            >
              {loadingAction === "approve" ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <CheckCircle2 className="mr-1.5 size-4" />
              )}
              Approve
            </Button>
          )}
          {invoice.status === "APPROVED" && onSend && (
            <Button
              size="sm"
              onClick={() => executeAction("send", onSend)}
              disabled={!!loadingAction}
            >
              {loadingAction === "send" ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <Send className="mr-1.5 size-4" />
              )}
              Send
            </Button>
          )}
          {invoice.status === "SENT" && onRecordPayment && (
            <Button
              size="sm"
              onClick={() => executeAction("pay", onRecordPayment)}
              disabled={!!loadingAction}
            >
              {loadingAction === "pay" ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <CreditCard className="mr-1.5 size-4" />
              )}
              Record Payment
            </Button>
          )}

          {/* Overflow menu */}
          {(invoice.status === "APPROVED" ||
            invoice.status === "SENT" ||
            invoice.status === "DRAFT") &&
            onVoid && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="icon-sm">
                    <MoreHorizontal className="size-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {invoice.status !== "DRAFT" && (
                    <>
                      <DropdownMenuItem
                        onClick={() => executeAction("void", onVoid)}
                        className="text-red-600 focus:text-red-600"
                      >
                        <Ban className="mr-2 size-4" />
                        Void Invoice
                      </DropdownMenuItem>
                    </>
                  )}
                  {invoice.status === "DRAFT" && (
                    <DropdownMenuItem
                      onClick={() => executeAction("void", onVoid)}
                      className="text-red-600 focus:text-red-600"
                    >
                      <Ban className="mr-2 size-4" />
                      Delete Draft
                    </DropdownMenuItem>
                  )}
                </DropdownMenuContent>
              </DropdownMenu>
            )}
        </div>
      </div>
    </div>
  );
}
