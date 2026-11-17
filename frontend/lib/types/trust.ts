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

/**
 * A trust transaction type code — a key of the backend registry (`TrustTransactionType`, 25 types).
 * No local list or label map (Phase 92 646E): labels, direction and reversal rules arrive from the
 * API — on every transaction (`typeLabel`, `direction`) and from `GET /api/trust-transaction-types`.
 */
export type TrustTransactionType = string;

/** Which way a row moves its own ledger; a reversal is the opposite of what it reverses. */
export type TrustTransactionDirection = "IN" | "OUT" | "NEUTRAL";

/** The creditor ledger a row moves (ADR-378). */
export type TrustLedger = "CLIENT" | "CLIENT_INVESTMENT" | "NON_CLIENT" | "INTER_ACCOUNT";

/** How a posted row of a type is reversed (registry `reversal`). Never rendered. */
export type TrustReversalRule =
  | "IMMEDIATE"
  | "NEEDS_APPROVAL"
  | "PAIR"
  | "VIA_PAIR"
  | "OWNER"
  | "NONE";

/** One registry entry — `GET /api/trust-transaction-types` (§16.4.1), declaration order. */
export interface TrustTransactionTypeInfo {
  code: string;
  /** The firm label — the only text a screen prints for the type. */
  label: string;
  direction: TrustTransactionDirection;
  /** Null for REVERSAL (it moves its original's ledger). */
  ledger: TrustLedger | null;
  /** Null for REVERSAL (it inherits its original's origin). */
  origin: "FIRM" | "BANK" | null;
  requiresApproval: boolean;
  reversal: TrustReversalRule;
  clientVisible: boolean;
}

/** The registry's reversal rule for a type code; a code the list does not carry is "NONE". */
export function reversalRuleFor(
  code: string,
  types: readonly TrustTransactionTypeInfo[]
): TrustReversalRule {
  return types.find((t) => t.code === code)?.reversal ?? "NONE";
}

/**
 * Whether the generic Reverse action is offered for a row: any posted row (RECORDED or APPROVED —
 * founder ruling #1) not yet reversed and with no contra awaiting approval (R1 — the backend
 * refuses both), whose type reverses on the generic endpoint (at once, after approval, or with
 * its paired transfer leg).
 */
export function canOfferReversal(
  tx: {
    transactionType: string;
    status: TrustTransactionStatus;
    reversedById: string | null;
    reversalPending: boolean;
  },
  types: readonly TrustTransactionTypeInfo[]
): boolean {
  if (tx.status !== "RECORDED" && tx.status !== "APPROVED") return false;
  if (tx.reversedById !== null || tx.reversalPending) return false;
  const rule = reversalRuleFor(tx.transactionType, types);
  return rule === "IMMEDIATE" || rule === "NEEDS_APPROVAL" || rule === "PAIR";
}

export type TrustTransactionStatus =
  | "RECORDED"
  | "AWAITING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "REVERSED";

/**
 * `TrustTransactionService.TrustTransactionResponse` — keys in the Java record's order (pinned by
 * `__tests__/trust-wire-shapes.test.ts`, ADR-365).
 */
export interface TrustTransaction {
  id: string;
  trustAccountId: string;
  transactionType: TrustTransactionType;
  /** The registry's firm label — print this, never the code. */
  typeLabel: string;
  direction: TrustTransactionDirection;
  ledger: TrustLedger;
  amount: number;
  clientId: string;
  matterId: string | null;
  creditorAccountId: string | null;
  investmentId: string | null;
  counterpartyClientId: string | null;
  invoiceId: string | null;
  reference: string;
  description: string | null;
  transactionDate: string;
  status: TrustTransactionStatus;
  /** When the row posted its effects; null while unposted (awaiting approval, rejected). */
  postedAt: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  secondApprovedBy: string | null;
  secondApprovedAt: string | null;
  rejectedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  reversalOf: string | null;
  reversedById: string | null;
  /** A contra of this row awaits approval — a second reversal is refused (R1). */
  reversalPending: boolean;
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
  // deposits + transfers in − payments − fee transfers − refunds − transfers out = balance
  totalDeposits: number;
  totalPayments: number;
  totalFeeTransfers: number;
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

/**
 * Mirrors backend `ClientLedgerService.TotalBalanceResponse` (646D): `balance` is the client
 * ledgers (meaning unchanged); `creditorBreakdown` is every trust creditor as at the requested date.
 */
export interface TotalBalance {
  balance: number;
  creditorBreakdown: TrustCreditorBreakdown;
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

/**
 * The creditor side of a reconciliation as at its period end — mirrors backend
 * `TrustReconciliationService.CreditorBreakdown` (646C; key order = Java component order).
 */
export interface TrustCreditorBreakdown {
  clientLedgers: number;
  investmentCreditors: number;
  fundInterestAndCharges: number;
  unidentifiedDeposits: number;
  otherNonClient: number;
  interAccount: number;
  total: number;
}

/**
 * Mirrors backend `TrustReconciliationService.TrustReconciliationResponse` (646C; key order =
 * Java component order). Every figure is as at `periodEnd`.
 */
export interface TrustReconciliationResponse {
  id: string;
  trustAccountId: string;
  periodEnd: string;
  bankStatementId: string | null;
  bankBalance: number;
  outstandingDeposits: number;
  outstandingPayments: number;
  adjustedBankBalance: number;
  cashbookBalance: number;
  clientLedgerTotal: number;
  creditorBreakdown: TrustCreditorBreakdown;
  /** adjusted bank − cashbook (EQ-1) */
  bankToCashbookDifference: number;
  /** cashbook − trust creditors (EQ-2) */
  cashbookToCreditorsDifference: number;
  /** adjusted bank − trust creditors */
  difference: number;
  /** signed total of the statement's excluded lines (adjusts nothing) */
  excludedLinesTotal: number;
  /** statement lines neither matched nor excluded */
  unresolvedLineCount: number;
  isBalanced: boolean;
  status: TrustReconciliationStatus;
  completedBy: string | null;
  completedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Counts only — mirrors backend `TrustReconciliationService.MatchResult`. The updated lines come from `GET /api/bank-statements/{id}`. */
export interface MatchResultResponse {
  totalLines: number;
  autoMatched: number;
  alreadyMatched: number;
  unmatched: number;
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
  recentTransactions: TrustTransaction[];
  alerts: TrustAlert[];
}

// Trust investment types

/** CANCELLED: the placement payment was rejected, so nothing was invested (G89-F87). */
export type TrustInvestmentStatus = "ACTIVE" | "MATURED" | "WITHDRAWN" | "CANCELLED";

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

// Non-client trust creditors (Phase 92 646B, ADR-376) — GET /api/trust-accounts/{id}/creditor-accounts

export type TrustCreditorAccountType = "FUND_INTEREST_AND_CHARGES" | "UNIDENTIFIED_DEPOSITS";

/** One non-client creditor ledger of a trust account, its balance derived as at `asAt`. */
export interface TrustCreditorAccount {
  id: string;
  trustAccountId: string;
  /** Logic only (which card shows the in-debit notice) — never rendered; `name` is the label. */
  accountType: TrustCreditorAccountType;
  name: string;
  balance: number;
  inDebit: boolean;
  asAt: string;
}
