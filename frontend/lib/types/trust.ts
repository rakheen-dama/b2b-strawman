// Trust account types

export type TrustAccountType = "GENERAL" | "INVESTMENT" | "SECTION_86";

export type TrustAccountStatus = "ACTIVE" | "CLOSED";

export interface TrustAccount {
  id: string;
  accountName: string;
  bankName: string;
  branchCode: string;
  accountNumber: string;
  accountType: TrustAccountType;
  isPrimary: boolean;
  requireDualApproval: boolean;
  paymentApprovalThreshold: number | null;
  status: TrustAccountStatus;
  openedDate: string;
  closedDate: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// LPFF rate types

export interface LpffRate {
  id: string;
  trustAccountId: string;
  effectiveFrom: string;
  ratePercent: number;
  lpffSharePercent: number;
  notes: string | null;
  createdAt: string;
}

// Trust transaction types

export type TrustTransactionType =
  | "DEPOSIT"
  | "PAYMENT"
  | "TRANSFER_IN"
  | "TRANSFER_OUT"
  | "FEE_TRANSFER"
  | "REFUND"
  | "INTEREST_CREDIT"
  | "INTEREST_LPFF"
  | "REVERSAL"
  | "DISBURSEMENT_PAYMENT";

const TRUST_TRANSACTION_TYPE_LABELS: Record<TrustTransactionType, string> = {
  DEPOSIT: "Deposit",
  PAYMENT: "Payment",
  TRANSFER_IN: "Transfer In",
  TRANSFER_OUT: "Transfer Out",
  FEE_TRANSFER: "Fee Transfer",
  REFUND: "Refund",
  INTEREST_CREDIT: "Interest Credit",
  INTEREST_LPFF: "Interest LPFF",
  REVERSAL: "Reversal",
  DISBURSEMENT_PAYMENT: "Disbursement Payment",
};

export function transactionTypeLabel(type: TrustTransactionType): string {
  return TRUST_TRANSACTION_TYPE_LABELS[type] ?? type;
}

/**
 * Transaction types that move money INTO the client's trust ledger — the credit set of the backend
 * balance queries (TrustTransactionRepository.calculateClientBalanceAsOfDate). Everything else
 * (PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_LPFF, …) renders as an outflow (G88-F23).
 */
const INFLOW_TRANSACTION_TYPES: ReadonlySet<TrustTransactionType> = new Set([
  "DEPOSIT",
  "TRANSFER_IN",
  "INTEREST_CREDIT",
]);

export function isInflowType(type: TrustTransactionType): boolean {
  return INFLOW_TRANSACTION_TYPES.has(type);
}

export type TrustTransactionStatus =
  | "RECORDED"
  | "AWAITING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "REVERSED";

export interface TrustTransaction {
  id: string;
  trustAccountId: string;
  transactionType: TrustTransactionType;
  amount: number;
  clientId: string;
  matterId: string | null;
  counterpartyClientId: string | null;
  invoiceId: string | null;
  reference: string;
  description: string | null;
  transactionDate: string;
  status: TrustTransactionStatus;
  approvedBy: string | null;
  approvedAt: string | null;
  secondApprovedBy: string | null;
  secondApprovedAt: string | null;
  rejectedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  reversalOf: string | null;
  reversedById: string | null;
  bankStatementLineId: string | null;
  recordedBy: string;
  createdAt: string;
  /**
   * ADR-361: server-computed on the client-ledger /history endpoint only; null on non-posted and
   * REVERSAL rows (and on every other endpoint).
   */
  runningBalance: number | null;
}

// Client ledger types

export interface ClientLedgerCard {
  id: string;
  trustAccountId: string;
  clientId: string;
  clientName: string;
  balance: number;
  // G89-F13: every total is derived from the posted ledger, so
  // deposits + interest + transfers in − payments − fee transfers − refunds − transfers out = balance
  totalDeposits: number;
  totalPayments: number;
  totalFeeTransfers: number;
  totalInterestCredited: number;
  totalRefunds: number;
  totalTransfersIn: number;
  totalTransfersOut: number;
  lastTransactionDate: string | null;
  createdAt: string;
  updatedAt: string;
}

// Balance types

export interface CashbookBalance {
  balance: number;
}

export interface TotalBalance {
  balance: number;
}

/**
 * `GET /api/matters/{matterId}/trust-balance` (Phase 89 §14.2.6, G88-M13): the matter's trust
 * balance — its RECORDED/APPROVED trust transactions only — in the org default currency.
 */
export interface MatterTrustBalance {
  matterId: string;
  balance: number;
  currency: string;
}

// Client ledger statement types

export interface LedgerStatementEntry {
  transactionId: string;
  transactionType: string;
  amount: number;
  reference: string;
  description: string | null;
  transactionDate: string;
  status: string;
  runningBalance: number;
}

export interface LedgerStatementResponse {
  openingBalance: number;
  closingBalance: number;
  transactions: LedgerStatementEntry[];
}

/** Alias for ClientLedgerCard — matches backend ClientLedgerCardResponse */
export type ClientLedgerResponse = ClientLedgerCard;

// Bank statement types

export type BankStatementFormat = "CSV" | "OFX";

export type BankStatementStatus = "IMPORTED" | "MATCHING_IN_PROGRESS" | "MATCHED" | "RECONCILED";

export type BankStatementLineMatchStatus =
  | "UNMATCHED"
  | "AUTO_MATCHED"
  | "MANUALLY_MATCHED"
  | "EXCLUDED";

export interface BankStatementLineResponse {
  id: string;
  lineNumber: number;
  transactionDate: string;
  description: string;
  reference: string | null;
  amount: number;
  runningBalance: number;
  matchStatus: BankStatementLineMatchStatus;
  trustTransactionId: string | null;
  matchConfidence: number | null;
  excludedReason: string | null;
  createdAt: string;
}

export interface BankStatementResponse {
  id: string;
  trustAccountId: string;
  periodStart: string;
  periodEnd: string;
  openingBalance: number;
  closingBalance: number;
  fileName: string;
  format: BankStatementFormat;
  lineCount: number;
  matchedCount: number;
  status: BankStatementStatus;
  importedBy: string;
  createdAt: string;
  updatedAt: string;
  lines: BankStatementLineResponse[];
}

// Reconciliation types

export type TrustReconciliationStatus = "DRAFT" | "COMPLETED";

export interface TrustReconciliationResponse {
  id: string;
  trustAccountId: string;
  periodEnd: string;
  bankStatementId: string;
  bankBalance: number;
  cashbookBalance: number;
  clientLedgerTotal: number;
  outstandingDeposits: number;
  outstandingPayments: number;
  adjustedBankBalance: number;
  isBalanced: boolean;
  status: TrustReconciliationStatus;
  completedBy: string | null;
  completedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MatchResultResponse {
  autoMatched: number;
  unmatched: number;
  lines: BankStatementLineResponse[];
}

// Dashboard types

export type TrustAlertType = "MATURING_INVESTMENT" | "OVERDUE_RECONCILIATION" | "AGING_APPROVAL";

export type TrustAlertSeverity = "info" | "warning" | "error";

export interface TrustAlert {
  type: TrustAlertType;
  message: string;
  severity: TrustAlertSeverity;
  relatedId?: string;
}

export interface TrustDashboardData {
  totalBalance: number;
  activeClients: number;
  pendingApprovals: number;
  lastReconciliationDate: string | null;
  lastReconciliationBalanced: boolean | null;
  recentTransactions: TrustTransaction[];
  alerts: TrustAlert[];
}

// Trust investment types

export type TrustInvestmentStatus = "ACTIVE" | "MATURED" | "WITHDRAWN";

export type InvestmentBasis = "FIRM_DISCRETION" | "CLIENT_INSTRUCTION";

export interface TrustInvestment {
  id: string;
  trustAccountId: string;
  clientId: string;
  clientName: string;
  institution: string;
  accountNumber: string;
  principal: number;
  interestRate: number;
  depositDate: string;
  maturityDate: string | null;
  interestEarned: number;
  status: TrustInvestmentStatus;
  withdrawalDate: string | null;
  withdrawalAmount: number | null;
  depositTransactionId: string;
  withdrawalTransactionId: string | null;
  notes: string | null;
  investmentBasis: InvestmentBasis;
  createdAt: string;
  updatedAt: string;
}

// Interest run types

export type InterestRunStatus = "DRAFT" | "APPROVED" | "POSTED";

/** Firm-side status words (ADR-362: never the raw enum). */
export const INTEREST_RUN_STATUS_LABELS: Record<InterestRunStatus, string> = {
  DRAFT: "Draft",
  APPROVED: "Approved",
  POSTED: "Posted",
};

export interface InterestRun {
  id: string;
  trustAccountId: string;
  periodStart: string;
  periodEnd: string;
  lpffRateId: string;
  totalInterest: number;
  totalLpffShare: number;
  totalClientShare: number;
  status: InterestRunStatus;
  createdBy: string | null;
  approvedBy: string | null;
  postedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Count of interest_allocations — 0 = never calculated (or calculated with nothing to allocate). */
  allocationCount: number;
}

export interface InterestAllocation {
  id: string;
  interestRunId: string;
  clientId: string;
  /** Null only when the client record is gone — the wizard then shows a short id (G89-F10). */
  clientName: string | null;
  averageDailyBalance: number;
  daysInPeriod: number;
  grossInterest: number;
  lpffShare: number;
  clientShare: number;
  trustTransactionId: string | null;
  lpffRateId: string | null;
  statutoryRateApplied: boolean;
  /** LPFF share fraction applied to this allocation (0.05 statutory, else the run's LPFF rate). */
  lpffSharePercent: number | null;
  createdAt: string;
  /**
   * The client share split across matters (ADR-366); empty for a run calculated before the split.
   * Not rendered by the interest-run wizard, which shows one row per client.
   */
  lines?: InterestAllocationLine[];
}

/** One matter's share of a client's interest allocation; `matterId` null = no matter. */
export interface InterestAllocationLine {
  id: string;
  matterId: string | null;
  matterName: string | null;
  balanceDays: number;
  clientShare: number;
  trustTransactionId: string | null;
}
