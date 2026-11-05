import { humaniseStatus } from "@/lib/utils";

/**
 * Phase 89 633C (arch §14.3.15; G88-F29, G88-F31): client-facing labels for the backend enums the
 * portal prints. Sentence case like the 624H badge maps (status-badge.tsx, invoice-status-badge.tsx);
 * an unknown value falls back to humaniseStatus — never the raw enum.
 */

/** Information request status (backend RequestStatus; SUBMITTED kept for the §14.3.15 list). */
export const REQUEST_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Draft",
  // Client perspective (scenario P-0.9a "Requested"): the firm has sent the request to the client.
  SENT: "Requested",
  IN_PROGRESS: "In progress",
  SUBMITTED: "Submitted",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

/** Information request item status (backend ItemStatus). */
export const REQUEST_ITEM_STATUS_LABELS: Record<string, string> = {
  PENDING: "Pending",
  SUBMITTED: "Submitted",
  ACCEPTED: "Accepted",
  REJECTED: "Rejected",
};

/** Trust transaction types (tenant chk_trust_txn_type), §14.3.15. */
export const TRUST_TRANSACTION_TYPE_LABELS: Record<string, string> = {
  DEPOSIT: "Deposit",
  PAYMENT: "Payment",
  REFUND: "Refund",
  FEE_TRANSFER: "Fee transfer",
  INTEREST_CREDIT: "Interest credited",
  INTEREST_LPFF: "Interest to LPFF",
  TRANSFER_IN: "Transfer in",
  TRANSFER_OUT: "Transfer out",
  REVERSAL: "Reversal",
};

/** The label for `value` in `map`, else the humanised enum ("SOME_VALUE" → "Some value"). */
export function labelFor(map: Record<string, string>, value: string): string {
  return map[value] ?? humaniseStatus(value);
}
