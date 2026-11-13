import { formatLocalDate } from "@/lib/format";

/**
 * The hint for a DRAFT trust reconciliation — "Period to 13 Nov 2026, in draft". Shared by the
 * Home trust-position widget and the trust dashboard's Reconciliation tile, so a draft never reads
 * as "Last reconciled …".
 */
export function draftReconciliationText(periodEnd: string): string {
  return `Period to ${formatLocalDate(periodEnd)}, in draft`;
}
