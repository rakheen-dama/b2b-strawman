// FICA status projection from GET /api/clients/{clientId}/fica-status (CUSTOMER_MANAGEMENT, D-152)
// (GAP-L-46; checklist-first since Phase 88, ADR-348; attestations since Phase 93 655B, ADR-388).
//
// Derived server-side, in this order: an onboarding checklist IN_PROGRESS, the newest COMPLETED
// checklist, the client's FICA information requests, then the client's active FICA attestation
// from a take-on (ATTESTED, or ATTESTED_REVIEW when a review reason applies). At most one of
// `checklistInstanceId` / `requestId` / `attestationId` is set (all null for NOT_STARTED).

export type FicaOnboardingStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "DONE"
  | "ATTESTED"
  | "ATTESTED_REVIEW";

/**
 * §17.10 X3 wording for the two attested statuses — the one source for every FICA badge (client
 * list cell + CSV, hero tile, matter FicaStatusCard, dashboard widget, attestation card).
 */
export const FICA_ATTESTED_STATUS_LABEL = {
  ATTESTED: "Verified in prior system (attested)",
  ATTESTED_REVIEW: "Review due (attested)",
} as const satisfies Partial<Record<FicaOnboardingStatus, string>>;

/** Why an attested client is due for review (FD-30). Wire order = this declaration order. */
export type FicaReviewReason =
  | "UNRATED"
  | "BENEFICIAL_OWNERSHIP_OUTSTANDING"
  | "SOURCE_OF_FUNDS_OUTSTANDING"
  | "NOT_SCREENED"
  | "REVIEW_DATE_PASSED";

/** §17.10 X3 wording — never a raw enum on screen. */
export const FICA_REVIEW_REASON_LABEL: Record<FicaReviewReason, string> = {
  UNRATED: "Not risk-rated",
  BENEFICIAL_OWNERSHIP_OUTSTANDING: "Beneficial ownership outstanding",
  SOURCE_OF_FUNDS_OUTSTANDING: "Source of funds outstanding",
  NOT_SCREENED: "Not screened",
  REVIEW_DATE_PASSED: "Review date passed",
};

/** Reason labels in wire order; an unknown value is skipped, never shown raw. */
export function ficaReviewReasonLabels(reasons: readonly string[] | null | undefined): string[] {
  return (reasons ?? [])
    .map((r) => FICA_REVIEW_REASON_LABEL[r as FicaReviewReason])
    .filter((label): label is string => Boolean(label));
}

export interface FicaStatus {
  clientId: string;
  status: FicaOnboardingStatus;
  /** ISO instant; populated only when status === "DONE" (checklist or request completedAt). */
  lastVerifiedAt: string | null;
  /** Information-request id, set only when a FICA request decided the status. */
  requestId: string | null;
  /** Onboarding checklist instance id, set when the checklist decided the status. */
  checklistInstanceId: string | null;
  /**
   * Set for ATTESTED_REVIEW (never null; empty otherwise). The endpoint needs CUSTOMER_MANAGEMENT
   * (D-152: 403 otherwise), so a caller who gets a response sees the full detail.
   */
  reviewReasons: FicaReviewReason[];
  /** The active attestation that decided the status; null otherwise. */
  attestationId: string | null;
}

export type FicaRiskRating = "LOW" | "MEDIUM" | "HIGH" | "UNRATED";

/**
 * `GET /api/clients/{clientId}/fica-attestation` (`ClientFicaAttestationResponse`, Phase 93 655B,
 * CUSTOMER_MANAGEMENT; 404 when the client has no active attestation). Dates are "YYYY-MM-DD".
 */
export interface ClientFicaAttestation {
  id: string;
  clientId: string;
  status: "ACTIVE";
  takeOnBatchId: string;
  cddCompletedDate: string;
  lastReviewDate: string | null;
  riskRating: FicaRiskRating;
  ficaClientType:
    | "NATURAL_PERSON"
    | "COMPANY"
    | "CLOSE_CORPORATION"
    | "TRUST"
    | "PARTNERSHIP"
    | "OTHER";
  beneficialOwnershipStatus: "DONE" | "NOT_APPLICABLE" | "OUTSTANDING";
  screeningStatus: "SCREENED_CLEAR" | "SCREENED_MATCH" | "NOT_SCREENED";
  screeningDate: string | null;
  sourceOfFundsStatus: "DONE" | "NOT_REQUIRED" | "OUTSTANDING";
  /** The s22 pointer to the previous system's or archive's CDD records. */
  cddRecordsLocation: string;
  sourceSystemRef: string | null;
  /** null when UNRATED (no interval applies). */
  nextReviewDate: string | null;
  attestedBy: string;
  attestedByName: string;
  attestedByRole: string;
  statementVersion: string;
  /** ISO instant. */
  attestedAt: string;
}
