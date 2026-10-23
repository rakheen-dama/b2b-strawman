// FICA status projection from GET /api/clients/{clientId}/fica-status
// (GAP-L-46; checklist-first since Phase 88, ADR-348).
//
// Derived server-side: the client's onboarding checklist instances decide
// first; its FICA information requests are the fallback. Exactly one of
// `checklistInstanceId` / `requestId` is set (both null for NOT_STARTED).

export type FicaOnboardingStatus = "NOT_STARTED" | "IN_PROGRESS" | "DONE";

export interface FicaStatus {
  clientId: string;
  status: FicaOnboardingStatus;
  /** ISO instant; populated only when status === "DONE" (checklist or request completedAt). */
  lastVerifiedAt: string | null;
  /** Information-request id, set only when a FICA request decided the status. */
  requestId: string | null;
  /** Onboarding checklist instance id, set when the checklist decided the status. */
  checklistInstanceId: string | null;
}
