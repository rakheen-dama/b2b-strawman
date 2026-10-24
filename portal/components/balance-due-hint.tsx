import { formatCurrency } from "@b2mash/shared/format";
import type { PortalInvoice } from "@/lib/types";
import { hasKnownPartPayment } from "@/lib/utils";

interface BalanceDueHintProps {
  invoice: Pick<
    PortalInvoice,
    "status" | "amountPaid" | "balanceDue" | "currency"
  >;
}

/**
 * Small amber "Balance R x" sub-line under a fee-note total while it is part-paid
 * (ADR-347; portal twin of the firm `BalanceDueHint`, whose amber-700 colour it keeps).
 * Renders nothing for other statuses, or when amountPaid is 0 — pre-V2 rows read 0
 * until re-synced (624A).
 */
export function BalanceDueHint({ invoice }: BalanceDueHintProps) {
  if (!hasKnownPartPayment(invoice)) {
    return null;
  }
  return (
    <span
      data-testid="row-balance-due"
      className="block text-xs font-normal text-amber-700"
    >
      Balance {formatCurrency(invoice.balanceDue, invoice.currency)}
    </span>
  );
}
