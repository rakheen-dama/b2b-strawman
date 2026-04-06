// ---- KYC Verification (from KycController.java, Epic 457) ----

export interface KycVerifyRequest {
  customerId: string;
  checklistInstanceItemId: string;
  idNumber: string;
  fullName: string;
  idDocumentType?: string;
  consentAcknowledged: boolean;
}

export interface KycVerifyResponse {
  status: "VERIFIED" | "NOT_VERIFIED" | "NEEDS_REVIEW" | "ERROR";
  providerName: string;
  providerReference: string | null;
  reasonCode: string | null;
  reasonDescription: string | null;
  verifiedAt: string | null;
  checklistItemUpdated: boolean;
}

export interface KycIntegrationStatus {
  configured: boolean;
  provider: string | null;
}
