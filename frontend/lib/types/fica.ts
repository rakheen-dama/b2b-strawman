// FICA status projection from GET /api/customers/{id}/fica-status
// (GAP-L-46).
//
// Info-request-only signal today; designed so later phases can fold in
// adapter outcomes + beneficial-owner coverage without schema change.

export type FicaOnboardingStatus = "NOT_STARTED" | "IN_PROGRESS" | "DONE";

export interface FicaStatus {
  customerId: string;
  status: FicaOnboardingStatus;
  /** ISO instant; populated only when status === "DONE". */
  lastVerifiedAt: string | null;
  /** Originating information-request id, if any. */
  requestId: string | null;
}
