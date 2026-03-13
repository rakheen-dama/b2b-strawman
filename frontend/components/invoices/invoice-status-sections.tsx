"use client";

import { Button } from "@/components/ui/button";
import { Copy, Check, RefreshCw } from "lucide-react";
import { formatDate } from "@/lib/format";
import { PaymentEventHistory } from "@/components/invoices/PaymentEventHistory";
import type { InvoiceResponse, ValidationCheck, PaymentEvent } from "@/lib/types";

// --- Send Validation Override ---

interface SendOverrideProps {
  validationChecks: ValidationCheck[];
  isPending: boolean;
  onSendWithOverride: () => void;
  onCancel: () => void;
}

export function SendValidationOverride({
  validationChecks,
  isPending,
  onSendWithOverride,
  onCancel,
}: SendOverrideProps) {
  return (
    <div
      data-testid="send-override-dialog"
      className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-900 dark:bg-yellow-950/50"
    >
      <h3 className="mb-2 font-medium text-yellow-800 dark:text-yellow-200">
        Validation issues found
      </h3>
      <p className="mb-3 text-sm text-yellow-700 dark:text-yellow-300">
        The following issues were found. As an admin/owner, you can override
        and send anyway.
      </p>
      <ul className="mb-4 space-y-1">
        {validationChecks.map((check, idx) => (
          <li key={idx} className="flex items-center gap-2 text-sm">
            <span
              className={
                check.passed
                  ? "text-green-700 dark:text-green-300"
                  : "text-yellow-800 dark:text-yellow-200"
              }
            >
              {check.passed ? "\u2713" : "\u2717"} {check.message}
            </span>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <Button
          variant="accent"
          size="sm"
          onClick={onSendWithOverride}
          disabled={isPending}
        >
          Send Anyway
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onCancel}
          disabled={isPending}
        >
          Cancel
        </Button>
      </div>
    </div>
  );
}

// --- Payment Form ---

interface PaymentFormProps {
  paymentRef: string;
  onPaymentRefChange: (value: string) => void;
  isPending: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function InvoicePaymentForm({
  paymentRef,
  onPaymentRefChange,
  isPending,
  onConfirm,
  onCancel,
}: PaymentFormProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
      <h3 className="mb-3 font-medium text-slate-900 dark:text-slate-100">
        Record Payment
      </h3>
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <label className="mb-1 block text-sm text-slate-600 dark:text-slate-400">
            Payment Reference (optional)
          </label>
          <input
            type="text"
            value={paymentRef}
            onChange={(e) => onPaymentRefChange(e.target.value)}
            placeholder="e.g. CHK-12345, Wire transfer"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-600"
          />
        </div>
        <Button
          variant="accent"
          size="sm"
          onClick={onConfirm}
          disabled={isPending}
        >
          Confirm Payment
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={onCancel}
          disabled={isPending}
        >
          Cancel
        </Button>
      </div>
    </div>
  );
}

// --- Paid Indicator ---

export function PaidIndicator({ invoice }: { invoice: InvoiceResponse }) {
  return (
    <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-800 dark:bg-green-950">
      <h3 className="font-medium text-green-800 dark:text-green-200">
        Payment Received
      </h3>
      <div className="mt-1 space-y-1 text-sm text-green-700 dark:text-green-300">
        {invoice.paidAt && <p>Paid on: {formatDate(invoice.paidAt)}</p>}
        {invoice.paymentReference && (
          <p>Reference: {invoice.paymentReference}</p>
        )}
      </div>
    </div>
  );
}

// --- Payment Link Section ---

interface PaymentLinkSectionProps {
  paymentUrl: string;
  copied: boolean;
  isPending: boolean;
  onCopy: () => void;
  onRegenerate: () => void;
}

export function PaymentLinkSection({
  paymentUrl,
  copied,
  isPending,
  onCopy,
  onRegenerate,
}: PaymentLinkSectionProps) {
  return (
    <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950/50">
      <h3 className="mb-2 font-medium text-teal-800 dark:text-teal-200">
        Online Payment Link
      </h3>
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={paymentUrl}
          readOnly
          className="flex-1 rounded-md border border-teal-200 bg-white px-3 py-2 text-sm text-slate-700 dark:border-teal-800 dark:bg-slate-950 dark:text-slate-300"
        />
        <Button
          variant="outline"
          size="sm"
          onClick={onCopy}
          className="shrink-0"
        >
          {copied ? (
            <Check className="size-4" />
          ) : (
            <Copy className="size-4" />
          )}
          <span className="ml-1.5">{copied ? "Copied" : "Copy Link"}</span>
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={onRegenerate}
          disabled={isPending}
          className="shrink-0"
        >
          <RefreshCw
            className={`size-4 ${isPending ? "animate-spin" : ""}`}
          />
          <span className="ml-1.5">Regenerate</span>
        </Button>
      </div>
    </div>
  );
}

// --- Void Indicator ---

export function VoidIndicator() {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
      <p className="font-medium text-red-700 dark:text-red-300">
        This invoice has been voided.
      </p>
    </div>
  );
}

// --- Payment Event History (re-export wrapper) ---

interface PaymentHistorySectionProps {
  paymentEvents: PaymentEvent[];
}

export function PaymentHistorySection({ paymentEvents }: PaymentHistorySectionProps) {
  return <PaymentEventHistory events={paymentEvents} />;
}
