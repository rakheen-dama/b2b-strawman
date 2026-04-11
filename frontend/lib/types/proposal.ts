// ---- Proposals (from ProposalController.java) ----

export type ProposalStatus = "DRAFT" | "SENT" | "ACCEPTED" | "DECLINED" | "EXPIRED";

export type FeeModel = "FIXED" | "HOURLY" | "RETAINER" | "CONTINGENCY";

export interface ProposalResponse {
  id: string;
  proposalNumber: string;
  title: string;
  customerId: string;
  portalContactId: string | null;
  status: ProposalStatus;
  feeModel: FeeModel;
  fixedFeeAmount: number | null;
  fixedFeeCurrency: string | null;
  hourlyRateNote: string | null;
  retainerAmount: number | null;
  retainerCurrency: string | null;
  retainerHoursIncluded: number | null;
  contingencyPercent: number | null;
  contingencyCapPercent: number | null;
  contingencyDescription: string | null;
  contentJson: Record<string, unknown> | null;
  projectTemplateId: string | null;
  sentAt: string | null;
  expiresAt: string | null;
  acceptedAt: string | null;
  declinedAt: string | null;
  declineReason: string | null;
  createdProjectId: string | null;
  createdRetainerId: string | null;
  createdById: string;
  createdAt: string;
  updatedAt: string;
}

export interface OverdueProposalDto {
  id: string;
  title: string;
  customerName: string;
  projectName: string | null;
  sentAt: string;
  daysSinceSent: number;
}

export interface ProposalSummaryDto {
  total: number;
  byStatus: Partial<Record<ProposalStatus, number>>;
  avgDaysToAcceptance: number;
  conversionRate: number;
  pendingOverdue: OverdueProposalDto[];
}
