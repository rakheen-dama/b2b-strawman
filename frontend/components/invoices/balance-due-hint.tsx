import { formatCurrency } from "@/lib/format";
import type { InvoiceResponse } from "@/lib/types";

/**
 * Muted "Balance R x" sub-line under a fee-note row total while part-paid (ADR-347 balance
 * model). Renders nothing for any other status, or when the response has no balanceDue.
 */
export function BalanceDueHint({
  invoice,
}: {
  invoice: Pick<InvoiceResponse, "status" | "balanceDue" | "currency">;
}) {
  if (invoice.status !== "PARTIALLY_PAID" || typeof invoice.balanceDue !== "number") {
    return null;
  }
  return (
    <span
      data-testid="row-balance-due"
      className="block text-xs font-normal text-amber-700 dark:text-amber-400"
    >
      Balance {formatCurrency(invoice.balanceDue, invoice.currency)}
    </span>
  );
}
