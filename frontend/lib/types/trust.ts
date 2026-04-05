// Trust account types

export type TrustAccountType = "GENERAL" | "INVESTMENT";

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
  | "REVERSAL";

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
  customerId: string;
  projectId: string | null;
  counterpartyCustomerId: string | null;
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
}

// Client ledger types

export interface ClientLedgerCard {
  id: string;
  trustAccountId: string;
  customerId: string;
  customerName: string;
  balance: number;
  totalDeposits: number;
  totalPayments: number;
  totalFeeTransfers: number;
  totalInterestCredited: number;
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

// Dashboard types

export type TrustAlertType =
  | "MATURING_INVESTMENT"
  | "OVERDUE_RECONCILIATION"
  | "AGING_APPROVAL";

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
