// Trust account types

export type TrustAccountType = "GENERAL" | "INVESTMENT" | "SECTION_86";

export type TrustAccountStatus = "ACTIVE" | "CLOSED";

/**
 * The section of the Legal Practice Act an account reports under (ADR-379), derived on the backend
 * from the type and, for an investment account, its basis. A section 86 trust account reports
 * under section 86(2) like a general one (working assumption OL-1).
 */
export type TrustAccountSection = "S86_2" | "S86_3" | "S86_4";

/** Firm-side section words (ADR-362: never the raw enum). */
export const TRUST_ACCOUNT_SECTION_LABELS: Record<TrustAccountSection, string> = {
  S86_2: "Section 86(2) trust banking account",
  S86_3: "Section 86(3) investment account",
  S86_4: "Section 86(4) investment account",
};

/** How an investment account's money was invested, in the Act's terms (never the raw enum). */
export const INVESTMENT_BASIS_ACCOUNT_LABELS: Record<InvestmentBasis, string> = {
  FIRM_DISCRETION: "Invested at the firm's discretion",
  CLIENT_INSTRUCTION: "Invested on a client's instruction",
};

/** Mirrors TrustAccountService.TrustAccountResponse (pinned: trust-wire-shapes.test.ts). */
export interface TrustAccount {
  id: string;
  accountName: string;
  bankName: string;
  branchCode: string;
  accountNumber: string;
  accountType: TrustAccountType;
  /** Null unless the account is an investment account (ADR-379). */
  investmentBasis: InvestmentBasis | null;
  section: TrustAccountSection;
  isPrimary: boolean;
  requireDualApproval: boolean;
  paymentApprovalThreshold: number | null;
  status: TrustAccountStatus;
  openedDate: string;
  closedDate: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  /**
   * The latest period end (yyyy-MM-dd) of the account's locked Rule 54.15 lists; null when none
   * (650D, §16.3.10). Posting dialogs warn — never refuse — on a date on or before it.
   */
  lockedThrough: string | null;
}

/**
 * Mirrors TrustAccountService.CreateTrustAccountRequest (pinned: trust-wire-shapes.test.ts).
 * `investmentBasis` is required for an investment account and refused for any other type.
 */
export interface CreateTrustAccountRequest {
  accountName: string;
  bankName: string;
  branchCode: string;
  accountNumber: string;
  accountType?: TrustAccountType;
  isPrimary?: boolean;
  requireDualApproval?: boolean;
  paymentApprovalThreshold?: number | null;
  openedDate?: string;
  notes?: string;
  investmentBasis?: InvestmentBasis;
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

/** Firm-side status words for a trust transaction (ADR-362: never the raw enum). */
export const TRUST_TRANSACTION_STATUS_LABELS: Record<TrustTransactionStatus, string> = {
  RECORDED: "Recorded",
  AWAITING_APPROVAL: "Awaiting approval",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  REVERSED: "Reversed",
};

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
   * ADR-361: server-computed on the client-ledger /history endpoint (null on non-posted and
   * REVERSAL rows) and on the creditor-ledger /transactions endpoint (647C: null on unposted rows
   * only); null on every other endpoint.
   */
  runningBalance: number | null;
  /**
   * 647A (ADR-381): BANK_CHARGE only — the firm's assertion that the charge is on the Fidelity
   * Fund's recoverable list (Kazi does not check it); null on every other row.
   */
  chargeRecoverable: boolean | null;
  /** 647A: BANK_CHARGE only — the VAT included in `amount`; null on every other row. */
  chargeVatAmount: number | null;
}

// Client ledger types

export interface ClientLedgerCard {
  id: string;
  trustAccountId: string;
  clientId: string;
  clientName: string;
  balance: number;
  // G89-F13 / 648E: every total is derived from the posted ledger, so
  // deposits + transfers in + investment returns − payments − fee transfers − refunds
  // − transfers out − investment placements = balance (general);
  // balance + investedBalance = total held in trust
  totalDeposits: number;
  totalPayments: number;
  totalFeeTransfers: number;
  totalRefunds: number;
  totalTransfersIn: number;
  totalTransfersOut: number;
  /** 648E: INV_PLACE — moved from the general balance to the client's section 86(4) investments */
  totalInvestmentPlacements: number;
  /** 648E: INV_RETURN — returned from section 86(4) investments to the general balance */
  totalInvestmentReturns: number;
  /** 648E: held for the client in section 86(4) investments placed from this account (ADR-379) */
  investedBalance: number;
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
  | "EXCLUDED"
  /** 647A: posted from — the firm recorded the line as a classification. */
  | "RECORDED";

/**
 * Mirrors backend `StatementLineClassification` (647A: the four Fund classifications; 647B: an
 * unidentified deposit — a credit whose owner is not yet known, held as an item; 648B: section
 * 86(4) interest, recorded against a named investment).
 */
export type StatementLineClassification =
  | "FUND_INTEREST"
  | "FUND_SWEEP"
  | "BANK_CHARGE"
  | "CHARGE_REFUND"
  | "UNIDENTIFIED_DEPOSIT"
  | "INVESTMENT_INTEREST";

/**
 * The classifications the Record from statement dialog offers, with the line sign each accepts
 * (+1 credit, -1 debit) and the account sections it is offered on — mirrors the backend enum. The
 * labels are the registry's firm labels (`TrustTransactionType.label()`), byte-identical (pinned
 * in `trust-reconciliation.test.tsx`). A stored line's badge shows the server's
 * `classificationLabel`, never this list.
 *
 * Sections (648B, §16.6.1): a section 86(2) banking account's credit can be Fund interest, a
 * charge refund or an unidentified deposit; a section 86(3) investment account's, Fund interest or
 * a charge refund; a section 86(4) investment account's, investment interest (the client's, with
 * the Fund's 5%) or a charge refund. Debits are the Fund payment or a bank charge everywhere. The
 * server's refusals remain the authority.
 */
export const STATEMENT_LINE_CLASSIFICATIONS: ReadonlyArray<{
  value: StatementLineClassification;
  lineSign: 1 | -1;
  label: string;
  sections: ReadonlyArray<TrustAccountSection>;
}> = [
  {
    value: "FUND_INTEREST",
    lineSign: 1,
    label: "Interest received for the Legal Practitioners' Fidelity Fund",
    sections: ["S86_2", "S86_3"],
  },
  {
    value: "CHARGE_REFUND",
    lineSign: 1,
    label: "Bank charge refunded from the business account",
    sections: ["S86_2", "S86_3", "S86_4"],
  },
  {
    value: "UNIDENTIFIED_DEPOSIT",
    lineSign: 1,
    label: "Unidentified deposit",
    sections: ["S86_2"],
  },
  {
    value: "INVESTMENT_INTEREST",
    lineSign: 1,
    label: "Section 86(4) investment interest",
    sections: ["S86_4"],
  },
  {
    value: "FUND_SWEEP",
    lineSign: -1,
    label: "Interest paid to the Legal Practitioners' Fidelity Fund",
    sections: ["S86_2", "S86_3", "S86_4"],
  },
  {
    value: "BANK_CHARGE",
    lineSign: -1,
    label: "Bank charge",
    sections: ["S86_2", "S86_3", "S86_4"],
  },
];

/**
 * `StatementLinePostingService.RecordFromLineRequest` — keys in the Java record's order (pinned by
 * `__tests__/trust-wire-shapes.test.ts`). The charge fields are a bank charge's only; the
 * investment, gross, Fund share and presentation are section 86(4) interest's only (648B).
 */
export interface RecordFromLineRequest {
  classification: StatementLineClassification;
  chargeRecoverable?: boolean | null;
  chargeVatAmount?: number | null;
  investmentId?: string | null;
  /** NET: the gross the bank advised (the line is the client's 95%); GROSS: omitted (the line). */
  grossAmount?: number | null;
  /** The Fund's 5% as the bank shows it — within R0,01 of 5% of the gross; omitted: computed. */
  fundShareAmount?: number | null;
  presentation?: InterestPresentation | null;
  notes?: string | null;
}

/**
 * `TrustReconciliationService.BankStatementLineResponse` — keys in the Java record's order
 * (pinned by `__tests__/trust-wire-shapes.test.ts`, ADR-365).
 */
export interface BankStatementLineResponse {
  id: string;
  lineNumber: number;
  transactionDate: string;
  description: string;
  reference: string | null;
  amount: number;
  runningBalance: number | null;
  matchStatus: BankStatementLineMatchStatus;
  trustTransactionId: string | null;
  matchConfidence: number | null;
  /** The confirmed classification code of a RECORDED line (a string on the wire); else null. */
  classification: string | null;
  /** The server's label for `classification` — print this, never the code. */
  classificationLabel: string | null;
  /**
   * The parser's suggestion (647C, §16.3.5): stored at import, null once auto-matched. It only
   * pre-selects the Record from statement dialog; nothing is posted without the firm's confirmation.
   */
  suggestedClassification: string | null;
  /** The rows a RECORDED line created; [] for every other line. */
  createdTransactionIds: string[];
}

/** `StatementLinePostingService.RecordFromLineResponse` (647A). */
export interface RecordFromLineResponse {
  line: BankStatementLineResponse;
  transactions: TrustTransaction[];
  unidentifiedDepositId: string | null;
  investmentInterestId: string | null;
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

// Integrity check types (649C) — mirror backend `books.TrustIntegrityService` and
// `books.TrustBooksService` records (key order = Java component order).

/** The flag codes of the integrity check (§16.3.9, plus FU-9's CASHBOOK_IN_DEBIT). */
export type IntegrityFlagCode =
  | "BANK_DIFFERENCE"
  | "BOOKS_OUT_OF_BALANCE"
  | "INTER_ACCOUNT_NOT_ZERO"
  | "UNIDENTIFIED_ITEMS_MISMATCH"
  | "INVESTMENT_BALANCE_MISMATCH"
  | "UNRESOLVED_STATEMENT_LINES"
  | "FUND_LEDGER_IN_DEBIT"
  | "CLIENT_LEDGER_IN_DEBIT"
  | "CASHBOOK_IN_DEBIT"
  | "UNIDENTIFIED_OVER_ONE_YEAR"
  | "CHARGES_TO_REFUND"
  | "BACKDATED_AFTER_LOCK";

/** A BLOCKING flag means the books are not provably right; a WARNING never blocks completion. */
export type IntegritySeverity = "BLOCKING" | "WARNING";

/** What a flag is about (`TrustIntegrityService.SUBJECT_*`). */
export type FlagSubjectKind =
  | "CLIENT"
  | "CREDITOR_ACCOUNT"
  | "UNIDENTIFIED_DEPOSIT"
  | "INVESTMENT"
  | "BANK_STATEMENT"
  | "TRUST_ACCOUNT"
  | "MONTHLY_LIST";

/** Mirrors `books.FlagSubject`; `amount` is a positive magnitude or null. */
export interface FlagSubject {
  kind: FlagSubjectKind;
  id: string | null;
  name: string | null;
  amount: number | null;
}

/**
 * Mirrors `books.IntegrityFlag`. `message` is a sentence for the firm (a blocking
 * flag's message is the completion gate's sentence); `amount` is a positive magnitude or null.
 */
export interface IntegrityFlag {
  code: IntegrityFlagCode;
  severity: IntegritySeverity;
  message: string;
  amount: number | null;
  subjects: FlagSubject[];
}

/** Mirrors `TrustBooksService.TrustPosition` — the EQ-2 terms for one account as at `asAt`. */
export interface TrustPosition {
  trustAccountId: string;
  asAt: string;
  cashbook: number;
  clientLedgers: number;
  investmentCreditors: number;
  fundLedger: number;
  unidentifiedDeposits: number;
  otherNonClient: number;
  interAccount: number;
  creditorTotal: number;
  difference: number;
}

/** Mirrors `TrustBooksService.BankPosition` — the EQ-1 terms at a reconciliation's period end. */
export interface BankPosition {
  bankBalance: number;
  outstandingDeposits: number;
  outstandingPayments: number;
  adjustedBankBalance: number;
}

/** Mirrors `TrustIntegrityService.TrustIntegrityReport` — one trust account as at `asAt`. */
export interface TrustIntegrityReport {
  trustAccountId: string;
  asAt: string;
  position: TrustPosition;
  /** Null unless a reconciliation of the account has period end `asAt`. */
  bank: BankPosition | null;
  /** True when no flag is BLOCKING. */
  balanced: boolean;
  flags: IntegrityFlag[];
  lastCompletedReconciliationId: string | null;
  lastReconciledThrough: string | null;
}

/**
 * Mirrors `TrustIntegrityService.FirmIntegrityReport` — `GET /api/trust-integrity`: every trust
 * account, the firm totals of each term and the firm-level flags (`INTER_ACCOUNT_NOT_ZERO`).
 */
export interface FirmIntegrityReport {
  asAt: string;
  /** True when no flag is BLOCKING, firm-level or in any account. */
  balanced: boolean;
  cashbook: number;
  clientLedgers: number;
  investmentCreditors: number;
  fundLedger: number;
  unidentifiedDeposits: number;
  otherNonClient: number;
  interAccount: number;
  creditorTotal: number;
  difference: number;
  flags: IntegrityFlag[];
  accounts: TrustIntegrityReport[];
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
  /** statement lines neither matched, recorded nor excluded (gate G4) */
  unresolvedLineCount: number;
  isBalanced: boolean;
  /** The integrity check's flags at the last calculation, blocking first (649C). */
  integrityFlags: IntegrityFlag[];
  status: TrustReconciliationStatus;
  completedBy: string | null;
  completedAt: string | null;
  /** The Rule 54.15 list this reconciliation locked on completing to a month end (650A); else null. */
  monthlyListId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// Rule 54.15 monthly trust creditor list (650A, ADR-382) — mirror backend
// `books.CreditorLine` and `books.TrustMonthlyListService` records (key order = Java order).

/** What a creditor line is a balance of (`CreditorLine.Kind`). */
export type CreditorLineKind =
  | "CLIENT"
  | "CLIENT_INVESTMENT"
  | "FUND"
  | "UNIDENTIFIED"
  | "INTER_ACCOUNT";

/** No raw kind on screen (guarded by the wire test's union check). */
export const CREDITOR_LINE_KIND_LABELS: Record<CreditorLineKind, string> = {
  CLIENT: "Client",
  CLIENT_INVESTMENT: "Invested (section 86(4))",
  FUND: "Legal Practitioners' Fidelity Fund",
  UNIDENTIFIED: "Unidentified deposit",
  INTER_ACCOUNT: "Section 86(3) placements",
};

/** Mirrors `CreditorLine.MatterBalance`; `matterId` null = rows not linked to a matter. */
export interface CreditorMatterBalance {
  matterId: string | null;
  name: string | null;
  balance: number;
}

/** Mirrors `books.CreditorLine` — one trust creditor as at the list's date (signed balance). */
export interface CreditorLine {
  kind: CreditorLineKind;
  clientId: string | null;
  name: string | null;
  matters: CreditorMatterBalance[];
  balance: number;
  unidentifiedDepositId: string | null;
  /** ISO date (`yyyy-MM-dd`), unidentified deposits only. */
  receivedDate: string | null;
  bankDescription: string | null;
  /**
   * The client's posted totals and last posted date (ISO) on the account as at the list's date,
   * frozen with a locked list — on the client's first line only, null on every other line.
   */
  totalDeposits: number | null;
  totalPayments: number | null;
  totalFeeTransfers: number | null;
  lastTransactionDate: string | null;
}

/** Mirrors `TrustMonthlyListService.MonthlyListSummary` — one locked list without its lines. */
export interface MonthlyListSummary {
  id: string;
  trustAccountId: string;
  periodEnd: string;
  reconciliationId: string;
  adjustedBankBalance: number;
  cashbookBalance: number;
  creditorTotal: number;
  difference: number;
  lockedBy: string;
  lockedByName: string | null;
  lockedAt: string;
  adjustmentCount: number;
}

/** Mirrors `TrustMonthlyListService.AdjustmentEntry` — a row entered after the list was locked. */
export interface MonthlyListAdjustmentEntry {
  transactionId: string;
  transactionDate: string;
  postedAt: string;
  transactionType: string;
  /** The registry label — render this, never `transactionType`. */
  typeLabel: string;
  ledger: TrustLedger;
  clientId: string | null;
  clientName: string | null;
  reference: string | null;
  description: string | null;
  cashbookEffect: number;
  ledgerEffect: number;
}

/**
 * Mirrors `TrustMonthlyListService.AdjustmentsSinceLock` — rows dated on or before the period end
 * and posted after the lock; each term is the live position at the period end minus the locked one.
 */
export interface MonthlyListAdjustments {
  entries: MonthlyListAdjustmentEntry[];
  cashbook: number;
  clientLedgers: number;
  investmentCreditors: number;
  fundLedger: number;
  unidentifiedDeposits: number;
  interAccount: number;
  liveCreditorTotal: number;
  changeInCreditorTotal: number;
}

/** Mirrors `TrustMonthlyListService.MonthlyListResponse` — `GET /api/trust-monthly-lists/{id}`. */
export interface MonthlyList {
  id: string;
  trustAccountId: string;
  trustAccountName: string | null;
  periodEnd: string;
  reconciliationId: string;
  bankBalance: number;
  outstandingDeposits: number;
  outstandingPayments: number;
  adjustedBankBalance: number;
  cashbookBalance: number;
  clientLedgerTotal: number;
  investmentCreditorTotal: number;
  fundLedgerBalance: number;
  unidentifiedDepositTotal: number;
  interAccountBalance: number;
  creditorTotal: number;
  difference: number;
  lines: CreditorLine[];
  integrityFlags: IntegrityFlag[];
  lockedBy: string;
  lockedByName: string | null;
  lockedAt: string;
  adjustmentsSinceLock: MonthlyListAdjustments;
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

/** CANCELLED: the placement was rejected, so nothing was invested (G89-F87, 648A). */
export type TrustInvestmentStatus = "ACTIVE" | "MATURED" | "WITHDRAWN" | "CANCELLED";

export type InvestmentBasis = "FIRM_DISCRETION" | "CLIENT_INSTRUCTION";

/**
 * An investment (648A, ADR-379): placed from the funding trust banking account
 * (`trustAccountId`) into an INVESTMENT trust account (`investmentAccountId`) of the same basis.
 * The client and matter are null for section 86(3); `balance` is what a withdrawal would return
 * now (zero until the placement is approved, and after the withdrawal).
 */
export interface TrustInvestment {
  id: string;
  trustAccountId: string;
  investmentAccountId: string;
  investmentAccountName: string | null;
  clientId: string | null;
  clientName: string | null;
  matterId: string | null;
  institution: string;
  accountNumber: string;
  principal: number;
  interestRate: number;
  depositDate: string;
  maturityDate: string | null;
  /** Σ of the POSTED interest entries (648B, section 86(4)): gross = Fund 5% + net. */
  grossInterest: number;
  fundShare: number;
  netInterest: number;
  balance: number;
  status: TrustInvestmentStatus;
  /** True while the placement awaits approval — nothing has moved yet (648A, server-computed). */
  awaitingApproval: boolean;
  withdrawalDate: string | null;
  withdrawalAmount: number | null;
  depositTransactionId: string | null;
  withdrawalTransactionId: string | null;
  notes: string | null;
  investmentBasis: InvestmentBasis;
  createdAt: string;
  updatedAt: string;
}

/** `POST /api/trust-accounts/{fundingAccountId}/investments` (648A). */
export interface PlaceInvestmentRequest {
  investmentAccountId: string;
  /** Section 86(4) only; omitted for section 86(3). */
  clientId?: string | null;
  /** Section 86(4) only; omitted for section 86(3). */
  matterId?: string | null;
  institution: string;
  accountNumber: string;
  principal: number;
  /** A decimal (0.075 = 7.5%). */
  interestRate: number;
  depositDate: string;
  maturityDate?: string | null;
  notes?: string | null;
  investmentBasis: InvestmentBasis;
}

/** `POST /api/trust-investments/{id}/withdraw` (648A): the amount is derived, never typed. */
export interface WithdrawInvestmentRequest {
  withdrawalDate: string;
}

/**
 * How the bank credited section 86(4) interest (648B, ADR-379 A2): GROSS — the whole interest
 * reached the investment account and the Fund's 5% leaves in a separate debit; NET — the 5% went
 * to the Fund at source and only the client's 95% reached the account.
 */
export type InterestPresentation = "GROSS" | "NET";

/** Firm-side words for a presentation (never the raw code on screen). */
export const INTEREST_PRESENTATION_LABELS: Record<InterestPresentation, string> = {
  GROSS: "Gross — the Fund's 5% leaves in a separate debit",
  NET: "Net of the Fund's 5%",
};

export type InvestmentInterestStatus = "POSTED" | "REVERSED";

/** `TrustInvestmentService.InvestmentInterestResponse` (648B): one section 86(4) interest entry. */
export interface InvestmentInterest {
  id: string;
  investmentId: string;
  entryDate: string;
  grossAmount: number;
  fundShareAmount: number;
  netAmount: number;
  presentation: InterestPresentation;
  status: InvestmentInterestStatus;
  netTransactionId: string;
  fundShareTransactionId: string | null;
  bankStatementLineId: string | null;
  clientTaxReference: string | null;
}

/** `POST /api/trust-investments/{id}/interest` (648B). */
export interface RecordInvestmentInterestRequest {
  entryDate: string;
  grossAmount: number;
  /** The Fund's 5% as the bank shows it — within R0,01 of 5% of the gross; omitted: computed. */
  fundShareAmount?: number | null;
  presentation: InterestPresentation;
  /** The investment account's UNMATCHED credit line it was credited on; omitted: a bank advice. */
  bankStatementLineId?: string | null;
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

/**
 * `GET /api/trust-creditor-accounts/{id}/transactions` (647C, §16.4.2) — one creditor ledger's rows,
 * newest first, each with its running balance; a Spring Data VIA_DTO page.
 */
export interface CreditorLedgerPage {
  content: TrustTransaction[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// Unidentified deposits (Phase 92 647B, ADR-376) — GET /api/trust-accounts/{id}/unidentified-deposits

/** `UnidentifiedDepositStatus` (647B, ADR-376) — the item's present state; never printed raw. */
export type UnidentifiedDepositStatus = "HELD" | "ALLOCATED" | "PAID_TO_FUND" | "REVERSED";

export const UNIDENTIFIED_DEPOSIT_STATUS_LABELS: Record<UnidentifiedDepositStatus, string> = {
  HELD: "Held",
  ALLOCATED: "Allocated to a client",
  PAID_TO_FUND: "Paid to the Fund",
  REVERSED: "Reversed",
};

/**
 * `UnidentifiedDepositService.UnidentifiedDepositResponse` (647B, §16.3.6) — keys in the Java
 * record's order (pinned by `__tests__/trust-wire-shapes.test.ts`, ADR-365). `status` is a String
 * on the wire, typed here as the union. `ageDays` counts from the received date to today;
 * `olderThanOneYear` flags only a held item older than 365 days. From 647D the allocated client and
 * matter (with names) are set from an allocation's request until it is rejected or undone, and
 * `resolutionStatus` is the pending or approved resolution row's status (`AWAITING_APPROVAL` /
 * `APPROVED`), null when there is none — a held item with a `resolutionTransactionId` is awaiting
 * approval.
 */
export interface UnidentifiedDeposit {
  id: string;
  trustAccountId: string;
  receivedDate: string;
  bankDescription: string;
  bankReference: string | null;
  amount: number;
  status: UnidentifiedDepositStatus;
  ageDays: number;
  olderThanOneYear: boolean;
  allocatedClientId: string | null;
  allocatedClientName: string | null;
  allocatedMatterId: string | null;
  allocatedMatterName: string | null;
  resolutionTransactionId: string | null;
  resolutionStatus: string | null;
  resolvedDate: string | null;
  bankStatementLineId: string;
  notes: string | null;
}

/**
 * `UnidentifiedDepositService.AllocateRequest` (647D) — `POST /api/unidentified-deposits/{id}/allocate`
 * body, keys in the Java record's order (pinned, ADR-365). `allocationDate` null = today.
 */
export interface AllocateUnidentifiedRequest {
  clientId: string;
  matterId: string;
  allocationDate: string | null;
  notes: string | null;
}

/**
 * `UnidentifiedDepositService.PayToFundRequest` (647D) — `POST /api/unidentified-deposits/{id}/pay-to-fund`
 * body, keys in the Java record's order (pinned, ADR-365).
 */
export interface PayToFundRequest {
  reference: string;
  transactionDate: string;
  notes: string | null;
}

/** `GET /api/trust-accounts/{id}/unidentified-deposits` (647B) — a Spring Data VIA_DTO page. */
export interface UnidentifiedDepositPage {
  content: UnidentifiedDeposit[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// At-source memo records (Phase 92 650B, ADR-384) — GET/PUT /api/trust-accounts/{id}/interest-memos

/**
 * `TrustInterestMemoService.InterestMemoResponse` (650B, §16.3.13) — keys in the Java record's
 * order (pinned by `__tests__/trust-wire-shapes.test.ts`, ADR-365). Rule 54.28 figures for
 * interest and charges that never reached the trust account; no effect on any balance.
 * `periodMonth` is the month's first day (yyyy-MM-dd); `recordedBy` the member who saved it last.
 */
export interface InterestMemo {
  id: string;
  trustAccountId: string;
  periodMonth: string;
  grossInterest: number;
  grossCharges: number;
  chargesVat: number;
  sourceNote: string;
  recordedBy: string;
  recordedByName: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * `TrustInterestMemoService.SaveInterestMemoRequest` (650B) —
 * `PUT /api/trust-accounts/{id}/interest-memos/{yyyy-MM}` body, keys in the Java record's order
 * (pinned, ADR-365). The VAT is included in the gross charges.
 */
export interface SaveInterestMemoRequest {
  grossInterest: number;
  grossCharges: number;
  chargesVat: number;
  sourceNote: string;
}

/**
 * `TrustReconciliationService.ExpectedStatementRequest` (652C) — the body of
 * `POST /api/trust-reconciliations/{id}/calculate` and `…/complete`: the statement the wizard
 * reviewed (pinned, ADR-365). A draft re-pointed to another statement since (a continuation in
 * another tab) refuses 409 "This reconciliation now uses a different statement".
 */
export interface ExpectedStatementRequest {
  expectedBankStatementId: string;
}
