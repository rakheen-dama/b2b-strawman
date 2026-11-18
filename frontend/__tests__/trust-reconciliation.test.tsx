import { Suspense } from "react";
import type React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock reconciliation actions ──────────────────────────────────
const mockFetchReconciliations = vi.fn();
const mockUploadBankStatement = vi.fn();
const mockAutoMatch = vi.fn();
const mockManualMatch = vi.fn();
const mockUnmatch = vi.fn();
const mockExcludeLine = vi.fn();
const mockCreateReconciliation = vi.fn();
const mockCalculateReconciliation = vi.fn();
const mockCompleteReconciliation = vi.fn();
const mockFetchBankStatement = vi.fn();
const mockFetchBankStatements = vi.fn();
const mockFetchReconciliation = vi.fn();
const mockRecordFromStatement = vi.fn();
const mockFetchMonthlyList = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions", () => ({
  fetchReconciliations: (...args: unknown[]) => mockFetchReconciliations(...args),
  uploadBankStatement: (...args: unknown[]) => mockUploadBankStatement(...args),
  autoMatch: (...args: unknown[]) => mockAutoMatch(...args),
  manualMatch: (...args: unknown[]) => mockManualMatch(...args),
  unmatch: (...args: unknown[]) => mockUnmatch(...args),
  excludeLine: (...args: unknown[]) => mockExcludeLine(...args),
  createReconciliation: (...args: unknown[]) => mockCreateReconciliation(...args),
  calculateReconciliation: (...args: unknown[]) => mockCalculateReconciliation(...args),
  completeReconciliation: (...args: unknown[]) => mockCompleteReconciliation(...args),
  fetchBankStatement: (...args: unknown[]) => mockFetchBankStatement(...args),
  fetchBankStatements: (...args: unknown[]) => mockFetchBankStatements(...args),
  fetchReconciliation: (...args: unknown[]) => mockFetchReconciliation(...args),
  recordFromStatement: (...args: unknown[]) => mockRecordFromStatement(...args),
  fetchMonthlyList: (...args: unknown[]) => mockFetchMonthlyList(...args),
}));

// ── Mock parent trust actions ────────────────────────────────────
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([
    {
      id: "acc-1",
      accountName: "Main Trust",
      bankName: "FNB",
      branchCode: "250655",
      accountNumber: "1234567890",
      accountType: "GENERAL",
      isPrimary: true,
      requireDualApproval: false,
      paymentApprovalThreshold: null,
      status: "ACTIVE",
      openedDate: "2025-01-01",
      closedDate: null,
      notes: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ]),
}));

// ── Mock investment actions (648B: the wizard lists a section 86(4) account's investments) ──
const mockFetchInterestEligibleInvestments = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/investments/actions", () => ({
  fetchInterestEligibleInvestments: (...args: unknown[]) =>
    mockFetchInterestEligibleInvestments(...args),
}));

// ── Mock transaction actions ─────────────────────────────────────
vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  fetchTransactions: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    pageSize: 20,
    pageNumber: 0,
  }),
}));

// ── Mock org settings & capabilities ─────────────────────────��───
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: vi.fn().mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  }),
}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  }),
}));

// ── Mock next/navigation ─────────────────────────────────────────
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: mockPush,
    refresh: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// ── Mock next/link ───────────────────────────────────────────────
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// ── Imports after mocks ──────────────────────────────────────────
import ReconciliationListPage from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/page";
import NewReconciliationPage from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page";
import MonthlyListPage from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/lists/[listId]/page";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";
import { fetchTransactions } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { recordFromStatementSchemaFor } from "@/lib/schemas/trust";
import { STATEMENT_LINE_CLASSIFICATIONS } from "@/lib/types";
import type {
  BankStatementLineResponse,
  MonthlyList,
  TrustAccountSection,
  TrustInvestment,
  TrustReconciliationResponse,
  TrustTransaction,
} from "@/lib/types";

// ── Helpers ──────────────────────────────────────────────────────

function makeRecon(overrides: Record<string, unknown> = {}) {
  return {
    id: "recon-1",
    trustAccountId: "acc-1",
    periodEnd: "2026-03-31",
    bankStatementId: "stmt-1",
    bankBalance: 142500,
    outstandingDeposits: 2500,
    outstandingPayments: 0,
    adjustedBankBalance: 145000,
    cashbookBalance: 145000,
    clientLedgerTotal: 145000,
    creditorBreakdown: {
      clientLedgers: 145000,
      investmentCreditors: 0,
      fundInterestAndCharges: 0,
      unidentifiedDeposits: 0,
      otherNonClient: 0,
      interAccount: 0,
      total: 145000,
    },
    bankToCashbookDifference: 0,
    cashbookToCreditorsDifference: 0,
    difference: 0,
    excludedLinesTotal: 0,
    unresolvedLineCount: 0,
    isBalanced: true,
    integrityFlags: [],
    status: "COMPLETED",
    completedBy: "member-1",
    completedAt: "2026-04-02T14:00:00Z",
    monthlyListId: null,
    notes: null,
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-02T14:00:00Z",
    ...overrides,
  };
}

function makeMonthlyList(overrides: Partial<MonthlyList> = {}): MonthlyList {
  return {
    id: "list-1",
    trustAccountId: "acc-1",
    trustAccountName: "Main Trust",
    periodEnd: "2026-03-31",
    reconciliationId: "recon-1",
    bankBalance: 142500,
    outstandingDeposits: 2500,
    outstandingPayments: 0,
    adjustedBankBalance: 145000,
    cashbookBalance: 145000,
    clientLedgerTotal: 143750,
    investmentCreditorTotal: 0,
    fundLedgerBalance: 0,
    unidentifiedDepositTotal: 1250,
    interAccountBalance: 0,
    creditorTotal: 145000,
    difference: 0,
    lines: [
      {
        kind: "CLIENT",
        clientId: "cust-1",
        name: "Ndlovu Family Trust",
        matters: [
          { matterId: "matter-1", name: "Estate late M Ndlovu", balance: 100000 },
          { matterId: null, name: null, balance: 3750 },
        ],
        balance: 103750,
        unidentifiedDepositId: null,
        receivedDate: null,
        bankDescription: null,
      },
      {
        kind: "CLIENT_INVESTMENT",
        clientId: "cust-2",
        name: "Khumalo Holdings",
        matters: [],
        balance: 40000,
        unidentifiedDepositId: null,
        receivedDate: null,
        bankDescription: null,
      },
      {
        kind: "FUND",
        clientId: null,
        name: "Legal Practitioners' Fidelity Fund — interest and charges",
        matters: [],
        balance: 0,
        unidentifiedDepositId: null,
        receivedDate: null,
        bankDescription: null,
      },
      {
        kind: "UNIDENTIFIED",
        clientId: null,
        name: "ACB CREDIT UNKNOWN",
        matters: [],
        balance: 1250,
        unidentifiedDepositId: "item-1",
        receivedDate: "2026-03-22",
        bankDescription: "ACB CREDIT UNKNOWN",
      },
    ],
    integrityFlags: [],
    lockedBy: "member-1",
    lockedByName: "Alice Owner",
    lockedAt: "2026-04-02T14:00:00Z",
    adjustmentsSinceLock: {
      entries: [
        {
          transactionId: "tx-late",
          transactionDate: "2026-03-15",
          postedAt: "2026-04-10T09:00:00Z",
          transactionType: "DEPOSIT",
          typeLabel: "Deposit",
          ledger: "CLIENT",
          clientId: "cust-1",
          clientName: "Ndlovu Family Trust",
          reference: "DEP-LATE",
          description: "Late deposit",
          cashbookEffect: 250,
          ledgerEffect: 250,
        },
      ],
      cashbook: 250,
      clientLedgers: 250,
      investmentCreditors: 0,
      fundLedger: 0,
      unidentifiedDeposits: 0,
      interAccount: 0,
      liveCreditorTotal: 145250,
      changeInCreditorTotal: 250,
    },
    ...overrides,
  };
}

function makeBankLine(
  overrides: Partial<BankStatementLineResponse> = {}
): BankStatementLineResponse {
  return {
    id: "line-1",
    lineNumber: 1,
    transactionDate: "2026-03-01",
    description: "DEPOSIT Smith & Co Ref DEP/2026/001",
    reference: "DEP/2026/001",
    amount: 15000,
    runningBalance: 140000,
    matchStatus: "UNMATCHED",
    trustTransactionId: null,
    matchConfidence: null,
    classification: null,
    classificationLabel: null,
    suggestedClassification: null,
    createdTransactionIds: [],
    ...overrides,
  };
}

function makeTx(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    typeLabel: "Deposit",
    direction: "IN",
    ledger: "CLIENT",
    amount: 15000,
    clientId: "cust-1",
    matterId: null,
    creditorAccountId: null,
    investmentId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "DEP/2026/001",
    description: "Deposit from Smith & Co",
    transactionDate: "2026-03-01",
    status: "APPROVED",
    postedAt: null,
    approvedBy: "member-1",
    approvedAt: "2026-03-01T10:00:00Z",
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    reversalPending: false,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-03-01T09:00:00Z",
    runningBalance: null,
    chargeRecoverable: null,
    chargeVatAmount: null,
    ...overrides,
  };
}

async function renderReconciliationList() {
  const result = await ReconciliationListPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({}),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Trust Reconciliation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Reconciliation list renders
  it("renders reconciliation list with period data", async () => {
    mockFetchReconciliations.mockResolvedValue({
      content: [
        makeRecon({
          id: "recon-1",
          periodEnd: "2026-03-31",
          isBalanced: true,
          status: "COMPLETED",
        }),
        makeRecon({
          id: "recon-2",
          periodEnd: "2026-02-28",
          isBalanced: false,
          status: "DRAFT",
          completedAt: null,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderReconciliationList();

    expect(screen.getByTestId("reconciliations-table")).toBeInTheDocument();
    expect(screen.getByText("2 reconciliations found")).toBeInTheDocument();
    expect(screen.getByText("Balanced")).toBeInTheDocument();
    expect(screen.getByText("Unbalanced")).toBeInTheDocument();
    expect(screen.getByTestId("new-reconciliation-btn")).toBeInTheDocument();
  });

  // Test 2: Upload triggers import
  it("shows upload result after successful import", async () => {
    const mockUpload = vi.fn().mockResolvedValue({
      success: true,
      data: {
        id: "stmt-1",
        trustAccountId: "acc-1",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-31",
        openingBalance: 125000,
        closingBalance: 142500,
        fileName: "march-2026.csv",
        format: "CSV",
        lineCount: 47,
        matchedCount: 0,
        status: "IMPORTED",
        importedBy: "member-1",
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z",
        lines: [],
      },
    });
    const mockOnComplete = vi.fn();

    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={mockOnComplete}
        uploadAction={mockUpload}
      />
    );

    // Simulate file selection
    const fileInput = screen.getByTestId("file-input");
    const testFile = new File(["csv content"], "march-2026.csv", {
      type: "text/csv",
    });

    fireEvent.change(fileInput, { target: { files: [testFile] } });

    await waitFor(() => {
      expect(screen.getByTestId("upload-result")).toBeInTheDocument();
    });

    expect(screen.getByText("Lines imported: 47")).toBeInTheDocument();
    expect(mockOnComplete).toHaveBeenCalled();
  });

  // Test 3: Split pane renders bank lines and transactions
  it("renders split pane with bank lines and transactions", () => {
    const lines = [
      makeBankLine({
        id: "line-1",
        matchStatus: "UNMATCHED",
        amount: 15000,
      }),
      makeBankLine({
        id: "line-2",
        matchStatus: "AUTO_MATCHED",
        amount: 8000,
        lineNumber: 2,
      }),
    ];

    const transactions = [makeTx({ id: "tx-1", amount: 15000, reference: "DEP-001" })];

    render(
      <ReconciliationSplitPane
        lines={lines}
        unmatchedTransactions={transactions}
        reconciliation={makeRecon({ isBalanced: false }) as unknown as TrustReconciliationResponse}
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    expect(screen.getByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(screen.getByTestId("bank-line-line-1")).toBeInTheDocument();
    expect(screen.getByTestId("bank-line-line-2")).toBeInTheDocument();
    expect(screen.getByTestId("transaction-tx-1")).toBeInTheDocument();
    expect(screen.getByText("1 / 2 lines resolved")).toBeInTheDocument();
  });

  // Test 4: Manual match links items
  it("shows match button for candidate transactions when bank line selected", async () => {
    const mockOnMatch = vi.fn();
    const lines = [
      makeBankLine({
        id: "line-1",
        matchStatus: "UNMATCHED",
        amount: 15000,
      }),
    ];

    const transactions = [makeTx({ id: "tx-1", amount: 15000, reference: "DEP-001" })];

    render(
      <ReconciliationSplitPane
        lines={lines}
        unmatchedTransactions={transactions}
        reconciliation={makeRecon({ isBalanced: false }) as unknown as TrustReconciliationResponse}
        currency="ZAR"
        onMatch={mockOnMatch}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    // Click bank line to select it
    fireEvent.click(screen.getByTestId("bank-line-line-1"));

    // Should show match button on candidate transaction
    await waitFor(() => {
      expect(screen.getByTestId("match-btn-tx-1")).toBeInTheDocument();
    });
  });

  // Test 5: Complete button disabled when not balanced
  it("disables complete button when reconciliation is not balanced", () => {
    render(
      <ReconciliationSplitPane
        lines={[]}
        unmatchedTransactions={[]}
        reconciliation={
          makeRecon({
            isBalanced: false,
            adjustedBankBalance: 142500,
            cashbookBalance: 145000,
          }) as unknown as TrustReconciliationResponse
        }
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    const completeBtn = screen.getByTestId("complete-reconciliation-btn");
    expect(completeBtn).toBeDisabled();
    expect(screen.getByText("Reconciliation is not balanced")).toBeInTheDocument();
  });

  // 646C R8: the summary is two comparisons with their own differences, the
  // creditor breakdown, the unresolved-line count and the excluded-lines total.
  it("shows both comparisons, the creditor breakdown and the line totals as at the period end", () => {
    render(
      <ReconciliationSplitPane
        lines={[]}
        unmatchedTransactions={[]}
        reconciliation={
          makeRecon({
            status: "DRAFT",
            isBalanced: false,
            bankBalance: 101000,
            outstandingDeposits: 0,
            outstandingPayments: 500,
            adjustedBankBalance: 100500,
            cashbookBalance: 101630,
            clientLedgerTotal: 100380,
            creditorBreakdown: {
              clientLedgers: 100380,
              investmentCreditors: 0,
              fundInterestAndCharges: 0,
              unidentifiedDeposits: 1250,
              otherNonClient: 0,
              interAccount: -2000,
              total: 99630,
            },
            bankToCashbookDifference: -1130,
            cashbookToCreditorsDifference: 2000,
            difference: 870,
            excludedLinesTotal: -75,
            unresolvedLineCount: 2,
          }) as unknown as TrustReconciliationResponse
        }
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    const summary = screen.getByTestId("balance-summary");
    expect(summary).toHaveTextContent(/As at 31 Mar 2026/);
    expect(summary).not.toHaveTextContent("Three-Way Balance Check");

    const bankCashbook = screen.getByTestId("comparison-bank-cashbook");
    expect(bankCashbook).toHaveTextContent("Bank balance");
    expect(bankCashbook).toHaveTextContent("Adjusted bank balance");
    expect(bankCashbook).toHaveTextContent("Cashbook");
    expect(screen.getByTestId("bank-to-cashbook-difference").textContent).toContain(
      formatCurrency(-1130, "ZAR")
    );

    const cashbookCreditors = screen.getByTestId("comparison-cashbook-creditors");
    expect(cashbookCreditors).toHaveTextContent("Trust creditors");
    expect(cashbookCreditors.textContent).toContain(formatCurrency(99630, "ZAR"));
    expect(screen.getByTestId("cashbook-to-creditors-difference").textContent).toContain(
      formatCurrency(2000, "ZAR")
    );

    const breakdown = screen.getByTestId("creditor-breakdown");
    expect(breakdown).toHaveTextContent("Client ledgers");
    expect(breakdown).toHaveTextContent(
      "Legal Practitioners' Fidelity Fund — interest and charges"
    );
    expect(breakdown).toHaveTextContent("Unidentified deposits");
    expect(breakdown).toHaveTextContent("Placed in section 86(3) investment accounts");
    // zero terms shown only where non-zero
    expect(breakdown).not.toHaveTextContent("Investment creditors");
    expect(breakdown).not.toHaveTextContent("Other non-client trust creditors");

    expect(screen.getByTestId("unresolved-line-count")).toHaveTextContent(
      "2 statement lines are neither matched, recorded nor excluded"
    );
    expect(screen.getByTestId("excluded-lines-total").textContent).toContain(
      formatCurrency(-75, "ZAR")
    );
  });

  // 649C (ruling D-10; flips the 646C "keeps Complete enabled…" case): an unresolved line fails
  // gate G4, so Complete stays disabled even when the figures balance.
  it("disables Complete while a statement line is unresolved", () => {
    render(
      <ReconciliationSplitPane
        lines={[]}
        unmatchedTransactions={[]}
        reconciliation={
          makeRecon({
            status: "DRAFT",
            isBalanced: true,
            unresolvedLineCount: 1,
          }) as unknown as TrustReconciliationResponse
        }
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    expect(screen.getByTestId("unresolved-line-count")).toHaveTextContent(
      "1 statement line is neither matched, recorded nor excluded"
    );
    expect(screen.getByTestId("complete-reconciliation-btn")).toBeDisabled();
  });

  // 649C (ruling D-10): a stored blocking flag disables Complete; a warning never does. Each flag
  // reads by its label and sentence, never the raw code.
  it("disables Complete when a blocking integrity flag is stored", () => {
    const blocking = {
      code: "UNIDENTIFIED_ITEMS_MISMATCH",
      severity: "BLOCKING",
      message: "2 unidentified-deposit ledger rows name no deposit.",
      amount: null,
      subjects: [],
    };
    const warning = {
      code: "FUND_LEDGER_IN_DEBIT",
      severity: "WARNING",
      message: "The Fund interest and charges ledger is R57,50 in debit.",
      amount: 57.5,
      subjects: [],
    };
    const props = {
      lines: [],
      unmatchedTransactions: [],
      currency: "ZAR",
      onMatch: vi.fn(),
      onExclude: vi.fn(),
      onUnmatch: vi.fn(),
      onRecord: vi.fn(),
      onComplete: vi.fn(),
      isCompleting: false,
    };
    const { rerender } = render(
      <ReconciliationSplitPane
        {...props}
        reconciliation={
          makeRecon({
            status: "DRAFT",
            isBalanced: true,
            integrityFlags: [blocking, warning],
          }) as unknown as TrustReconciliationResponse
        }
      />
    );

    expect(screen.getByTestId("complete-reconciliation-btn")).toBeDisabled();
    const flag = within(screen.getByTestId("reconciliation-flag-UNIDENTIFIED_ITEMS_MISMATCH"));
    expect(flag.getByText("Blocking")).toBeInTheDocument();
    expect(
      flag.getByText("Unidentified deposits do not agree with their ledger")
    ).toBeInTheDocument();
    expect(flag.getByText(blocking.message)).toBeInTheDocument();
    expect(
      within(screen.getByTestId("reconciliation-flag-FUND_LEDGER_IN_DEBIT")).getByText("Warning")
    ).toBeInTheDocument();
    expect(screen.getByTestId("reconciliation-integrity-flags")).not.toHaveTextContent(
      /UNIDENTIFIED_ITEMS_MISMATCH|FUND_LEDGER_IN_DEBIT|BLOCKING|WARNING/
    );

    // A warning alone never blocks completion (ADR-377; ruling #5).
    rerender(
      <ReconciliationSplitPane
        {...props}
        reconciliation={
          makeRecon({
            status: "DRAFT",
            isBalanced: true,
            integrityFlags: [warning],
          }) as unknown as TrustReconciliationResponse
        }
      />
    );
    expect(screen.getByTestId("complete-reconciliation-btn")).toBeEnabled();
  });

  // 646C R9 (F-646E-1): an unmatched row prints its registry label, never the raw type.
  it("labels an unmatched transaction from its typeLabel, not the raw type", () => {
    render(
      <ReconciliationSplitPane
        lines={[]}
        unmatchedTransactions={[
          makeTx({
            id: "tx-ft",
            transactionType: "FEE_TRANSFER",
            typeLabel: "Fee transfer",
            direction: "OUT",
            reference: "FT-001",
          }),
        ]}
        reconciliation={null}
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onRecord={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />
    );

    const row = screen.getByTestId("transaction-tx-ft");
    expect(row).toHaveTextContent("Fee transfer");
    expect(row).not.toHaveTextContent("FEE TRANSFER");
  });
  // Test 6: the wizard reaches the review step after auto-match. The backend's
  // auto-match response is counts only (TrustReconciliationService.MatchResult);
  // it has no `lines`, so the review step must take its lines from the statement.
  it("reaches the review step with the statement's lines after auto-match", async () => {
    const statement = {
      id: "stmt-1",
      trustAccountId: "acc-1",
      periodStart: "2026-03-01",
      periodEnd: "2026-03-31",
      openingBalance: 125000,
      closingBalance: 142500,
      fileName: "march-2026.csv",
      format: "CSV",
      lineCount: 2,
      matchedCount: 0,
      status: "IMPORTED",
      importedBy: "member-1",
      createdAt: "2026-04-01T00:00:00Z",
      updatedAt: "2026-04-01T00:00:00Z",
      lines: [
        makeBankLine({ id: "line-1", lineNumber: 1 }),
        makeBankLine({ id: "line-2", lineNumber: 2, reference: "PAY/2026/002", amount: -2500 }),
      ],
    };
    mockUploadBankStatement.mockResolvedValue({ success: true, data: statement });
    // The real backend shape: counts only, no `lines`.
    mockAutoMatch.mockResolvedValue({
      success: true,
      data: { totalLines: 2, autoMatched: 1, alreadyMatched: 0, unmatched: 1 },
    });
    mockFetchBankStatement.mockResolvedValue({
      ...statement,
      matchedCount: 1,
      lines: [
        makeBankLine({
          id: "line-1",
          lineNumber: 1,
          matchStatus: "AUTO_MATCHED",
          trustTransactionId: "tx-1",
          matchConfidence: 0.8,
        }),
        makeBankLine({ id: "line-2", lineNumber: 2, reference: "PAY/2026/002", amount: -2500 }),
      ],
    });
    mockCreateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
    });
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
    });

    // act() lets React.use(params) resolve its promise before the assertions.
    await act(async () => {
      render(
        <Suspense fallback={null}>
          <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
        </Suspense>
      );
    });

    // Step 1: account (primary preselected) + period end
    await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
    fireEvent.change(screen.getByTestId("period-end-input"), {
      target: { value: "2026-03-31" },
    });
    fireEvent.click(screen.getByTestId("step-next-btn"));

    // Step 2: upload
    fireEvent.change(await screen.findByTestId("file-input"), {
      target: { files: [new File(["csv"], "march-2026.csv", { type: "text/csv" })] },
    });
    await waitFor(() => expect(screen.getByTestId("step-next-btn")).toBeEnabled());
    fireEvent.click(screen.getByTestId("step-next-btn"));

    // Step 3: auto-match
    fireEvent.click(await screen.findByTestId("auto-match-btn"));
    expect(await screen.findByText("Auto-matched 1 lines")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("step-next-btn"));

    // Step 4: the split pane shows both lines with their post-match statuses
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(screen.getByTestId("bank-line-line-1")).toBeInTheDocument();
    expect(screen.getByTestId("bank-line-line-2")).toBeInTheDocument();
    expect(screen.getByText("1 / 2 lines resolved")).toBeInTheDocument();
    expect(mockFetchBankStatement).toHaveBeenCalledWith("stmt-1");
  });
  // Test 7: while auto-match (and the statement re-read) is in flight, the user
  // cannot go back and change the account/period under it.
  it("disables Back on the auto-match step while auto-match is running", async () => {
    mockUploadBankStatement.mockResolvedValue({
      success: true,
      data: {
        id: "stmt-1",
        trustAccountId: "acc-1",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-31",
        openingBalance: 0,
        closingBalance: 15000,
        fileName: "march-2026.csv",
        format: "CSV",
        lineCount: 1,
        matchedCount: 0,
        status: "IMPORTED",
        importedBy: "member-1",
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z",
        lines: [makeBankLine()],
      },
    });
    let finishAutoMatch: (v: unknown) => void = () => {};
    mockAutoMatch.mockReturnValue(new Promise((resolve) => (finishAutoMatch = resolve)));
    let finishRefresh: (v: unknown) => void = () => {};
    mockFetchBankStatement.mockReturnValue(new Promise((resolve) => (finishRefresh = resolve)));

    await act(async () => {
      render(
        <Suspense fallback={null}>
          <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
        </Suspense>
      );
    });
    await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
    fireEvent.change(screen.getByTestId("period-end-input"), {
      target: { value: "2026-03-31" },
    });
    fireEvent.click(screen.getByTestId("step-next-btn"));
    fireEvent.change(await screen.findByTestId("file-input"), {
      target: { files: [new File(["csv"], "march-2026.csv", { type: "text/csv" })] },
    });
    await waitFor(() => expect(screen.getByTestId("step-next-btn")).toBeEnabled());
    fireEvent.click(screen.getByTestId("step-next-btn"));

    fireEvent.click(await screen.findByTestId("auto-match-btn"));
    await waitFor(() => expect(screen.getByTestId("auto-match-back-btn")).toBeDisabled());

    await act(async () => {
      finishAutoMatch({
        success: true,
        data: { totalLines: 1, autoMatched: 0, alreadyMatched: 0, unmatched: 1 },
      });
    });
    // Auto-match has resolved but the statement re-read has not: still blocked.
    await waitFor(() => expect(mockFetchBankStatement).toHaveBeenCalledWith("stmt-1"));
    expect(screen.getByTestId("auto-match-back-btn")).toBeDisabled();

    await act(async () => {
      finishRefresh({ id: "stmt-1", lines: [makeBankLine()] });
    });
    await waitFor(() => expect(screen.getByTestId("auto-match-back-btn")).toBeEnabled());
  });
  // Drives the wizard to step 3 with an auto-match that resolves every line.
  async function driveToReviewWithAllLinesAutoMatched() {
    const line = makeBankLine({ id: "line-1" });
    mockUploadBankStatement.mockResolvedValue({
      success: true,
      data: {
        id: "stmt-1",
        trustAccountId: "acc-1",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-31",
        openingBalance: 0,
        closingBalance: 15000,
        fileName: "march-2026.csv",
        format: "CSV",
        lineCount: 1,
        matchedCount: 0,
        status: "IMPORTED",
        importedBy: "member-1",
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z",
        lines: [line],
      },
    });
    mockAutoMatch.mockResolvedValue({
      success: true,
      data: { totalLines: 1, autoMatched: 1, alreadyMatched: 0, unmatched: 0 },
    });
    mockFetchBankStatement.mockResolvedValue({
      id: "stmt-1",
      lines: [{ ...line, matchStatus: "AUTO_MATCHED", trustTransactionId: "tx-1" }],
    });
    // What POST /api/trust-reconciliations returns: a DRAFT with every balance at zero.
    mockCreateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({
        status: "DRAFT",
        isBalanced: false,
        completedAt: null,
        completedBy: null,
        bankBalance: 0,
        cashbookBalance: 0,
        clientLedgerTotal: 0,
        outstandingDeposits: 0,
        outstandingPayments: 0,
        adjustedBankBalance: 0,
      }),
    });

    await act(async () => {
      render(
        <Suspense fallback={null}>
          <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
        </Suspense>
      );
    });
    await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
    fireEvent.change(screen.getByTestId("period-end-input"), {
      target: { value: "2026-03-31" },
    });
    fireEvent.click(screen.getByTestId("step-next-btn"));
    fireEvent.change(await screen.findByTestId("file-input"), {
      target: { files: [new File(["csv"], "march-2026.csv", { type: "text/csv" })] },
    });
    await waitFor(() => expect(screen.getByTestId("step-next-btn")).toBeEnabled());
    fireEvent.click(screen.getByTestId("step-next-btn"));
    fireEvent.click(await screen.findByTestId("auto-match-btn"));
    expect(await screen.findByText("Auto-matched 1 lines")).toBeInTheDocument();
  }

  // Test 8: the review step opens on calculated balances, so a statement that
  // auto-match fully resolved can be completed without a manual action.
  it("calculates the reconciliation on entering review so it can be completed", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({
        status: "DRAFT",
        isBalanced: true,
        completedAt: null,
        completedBy: null,
        bankBalance: 15000,
        cashbookBalance: 15000,
        clientLedgerTotal: 15000,
        outstandingDeposits: 0,
        outstandingPayments: 0,
        adjustedBankBalance: 15000,
      }),
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));

    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(mockCalculateReconciliation).toHaveBeenCalledWith("recon-1");
    expect(screen.getByText("1 / 1 lines resolved")).toBeInTheDocument();
    expect(screen.getByTestId("complete-reconciliation-btn")).toBeEnabled();
  });

  // Test 9: if that first calculation fails, the user stays on step 3 with the
  // error (retrying recalculates the existing reconciliation; no second draft).
  it("stays on the auto-match step with an error when the first calculation fails", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation.mockResolvedValueOnce({
      success: false,
      error: "Calculation failed",
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));

    expect(await screen.findByText("Calculation failed")).toBeInTheDocument();
    expect(screen.getByTestId("step-auto-match")).toBeInTheDocument();
    expect(screen.queryByTestId("reconciliation-split-pane")).not.toBeInTheDocument();

    mockCalculateReconciliation.mockResolvedValueOnce({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
    });
    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(mockCreateReconciliation).toHaveBeenCalledTimes(1);
    expect(mockCalculateReconciliation).toHaveBeenCalledTimes(2);
  });
  // 649C (§16.5.3, ruling D-9): the gate refuses once with every failed condition; the refusal
  // rolls back, so the wizard recalculates to show the figures the gate saw, then the sentences.
  it("shows the refusal's sentences and refreshes the figures when completion is refused", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    const refusal =
      "Adjusted bank balance R2 250,00 differs from the cashbook R1 000,00 by R1 250,00 as at 31 March 2026. 1 statement line is neither matched, recorded nor excluded.";
    mockCalculateReconciliation
      .mockResolvedValueOnce({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
      })
      .mockResolvedValueOnce({
        success: true,
        data: makeRecon({
          status: "DRAFT",
          completedAt: null,
          isBalanced: false,
          bankBalance: 2250,
          outstandingDeposits: 0,
          outstandingPayments: 0,
          adjustedBankBalance: 2250,
          cashbookBalance: 1000,
          bankToCashbookDifference: 1250,
          unresolvedLineCount: 1,
          integrityFlags: [
            {
              code: "BANK_DIFFERENCE",
              severity: "BLOCKING",
              message:
                "Adjusted bank balance R2 250,00 differs from the cashbook R1 000,00 by R1 250,00 as at 31 March 2026.",
              amount: 1250,
              subjects: [],
            },
            {
              code: "UNRESOLVED_STATEMENT_LINES",
              severity: "BLOCKING",
              message: "1 statement line is neither matched, recorded nor excluded.",
              amount: null,
              subjects: [],
            },
          ],
        }),
      });
    mockCompleteReconciliation.mockResolvedValue({ success: false, error: refusal });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(screen.getByTestId("complete-reconciliation-btn")).toBeEnabled();

    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    expect(await screen.findByTestId("reconciliation-error")).toHaveTextContent(refusal);
    expect(mockCompleteReconciliation).toHaveBeenCalledWith("recon-1");
    expect(mockCalculateReconciliation).toHaveBeenCalledTimes(2);
    // The summary now shows the recalculated figures and the gate's flags.
    expect(screen.getByTestId("bank-to-cashbook-difference").textContent).toBe(
      formatCurrency(1250, "ZAR")
    );
    expect(screen.getByTestId("unresolved-line-count")).toHaveTextContent(
      "1 statement line is neither matched, recorded nor excluded"
    );
    expect(screen.getByTestId("reconciliation-flag-BANK_DIFFERENCE")).toBeInTheDocument();
    expect(screen.getByTestId("complete-reconciliation-btn")).toBeDisabled();
    expect(screen.queryByTestId("step-complete")).not.toBeInTheDocument();
  });

  // F-OBS-1: a refusal can come from another user's change (here: line-1 unmatched elsewhere), so
  // the wizard also re-reads the statement lines and the candidate transactions — the progress
  // count, the line badge and the line's actions follow the database, not the wizard's old state.
  it("re-reads the statement lines and transactions when completion is refused", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation
      .mockResolvedValueOnce({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
      })
      .mockResolvedValueOnce({
        success: true,
        data: makeRecon({
          status: "DRAFT",
          completedAt: null,
          isBalanced: false,
          unresolvedLineCount: 1,
        }),
      });
    mockCompleteReconciliation.mockResolvedValue({
      success: false,
      error: "1 statement line is neither matched, recorded nor excluded.",
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    expect(screen.getByText("1 / 1 lines resolved")).toBeInTheDocument();
    expect(screen.getByTestId("unmatch-btn-line-1")).toBeInTheDocument();
    const statementReads = mockFetchBankStatement.mock.calls.length;
    const transactionReads = vi.mocked(fetchTransactions).mock.calls.length;

    // Another user unmatched line-1: the database now has it UNMATCHED and tx-1 free again.
    mockFetchBankStatement.mockResolvedValue({
      id: "stmt-1",
      lines: [makeBankLine({ id: "line-1", matchStatus: "UNMATCHED", trustTransactionId: null })],
    });
    vi.mocked(fetchTransactions).mockResolvedValueOnce({
      content: [makeTx({ id: "tx-1" })],
      totalElements: 1,
      totalPages: 1,
      pageSize: 1000,
      pageNumber: 0,
    });

    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    expect(await screen.findByTestId("reconciliation-error")).toBeInTheDocument();
    expect(mockFetchBankStatement).toHaveBeenCalledTimes(statementReads + 1);
    expect(mockFetchBankStatement).toHaveBeenLastCalledWith("stmt-1");
    expect(vi.mocked(fetchTransactions)).toHaveBeenCalledTimes(transactionReads + 1);
    expect(await screen.findByText("0 / 1 lines resolved")).toBeInTheDocument();
    const line = screen.getByTestId("bank-line-line-1");
    expect(within(line).getByText("Unmatched")).toBeInTheDocument();
    expect(screen.queryByTestId("unmatch-btn-line-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("transaction-tx-1")).toBeInTheDocument();
    // The line can be resolved again on screen: selecting it offers its actions.
    fireEvent.click(line);
    expect(screen.getByTestId("exclude-btn-line-1")).toBeInTheDocument();
    expect(screen.getByTestId("match-btn-tx-1")).toBeInTheDocument();
  });

  // F-OBS-1: if the re-read fails the refusal stays on screen and says the lines may be stale.
  it("keeps the refusal and says the lines may be out of date when the re-read fails", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
    });
    mockCompleteReconciliation.mockResolvedValue({
      success: false,
      error: "1 statement line is neither matched, recorded nor excluded.",
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    mockFetchBankStatement.mockRejectedValueOnce(new Error("Network down"));
    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    expect(await screen.findByTestId("reconciliation-error")).toHaveTextContent(
      "1 statement line is neither matched, recorded nor excluded. The statement lines could not be refreshed and may be out of date."
    );
    expect(screen.getByText("1 / 1 lines resolved")).toBeInTheDocument();
  });

  // O-OBS-2: the refusal's ProblemDetail title is the banner heading, its sentences beneath it.
  it("shows the refusal's title as the banner heading above its sentences", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    const sentences =
      "Adjusted bank balance R2 250,00 differs from the cashbook R1 000,00 by R1 250,00 as at 31 March 2026. 1 statement line is neither matched, recorded nor excluded.";
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
    });
    mockCompleteReconciliation.mockResolvedValue({
      success: false,
      error: sentences,
      errorTitle: "Reconciliation does not balance",
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    const banner = await screen.findByTestId("reconciliation-error");
    const title = within(banner).getByTestId("reconciliation-error-title");
    expect(title).toHaveTextContent(/^Reconciliation does not balance$/);
    expect(within(banner).getByText(sentences)).toBeInTheDocument();
    // The heading comes first, the sentences beneath it.
    expect(banner.firstElementChild).toBe(title);
  });

  // A banner error without a title (every other wizard error) stays a plain message.
  it("shows a refusal without a title as a plain message", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
    });
    mockCompleteReconciliation.mockResolvedValue({ success: false, error: "Network down" });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    const banner = await screen.findByTestId("reconciliation-error");
    expect(banner).toHaveTextContent(/^Network down$/);
    expect(within(banner).queryByTestId("reconciliation-error-title")).not.toBeInTheDocument();
  });

  it("completes when balanced with no unresolved lines and no blocking flag", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    mockCalculateReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
    });
    mockCompleteReconciliation.mockResolvedValue({
      success: true,
      data: makeRecon({ status: "COMPLETED" }),
    });

    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

    expect(await screen.findByTestId("step-complete")).toBeInTheDocument();
    expect(mockCompleteReconciliation).toHaveBeenCalledWith("recon-1");
    expect(mockCalculateReconciliation).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId("reconciliation-error")).not.toBeInTheDocument();
  });

  // 650A: the Rule 54.15 monthly list lock
  describe("monthly list lock (650A)", () => {
    /** The currency as the DOM's normalised text content reads it (non-breaking spaces → spaces). */
    const zar = (amount: number) => formatCurrency(amount, "ZAR").replace(/\s/g, " ");

    it("marks a month-end reconciliation's locked list on the list page, and nothing on a draft", async () => {
      mockFetchReconciliations.mockResolvedValue({
        content: [
          makeRecon({ id: "recon-1", monthlyListId: "list-1" }),
          makeRecon({
            id: "recon-2",
            periodEnd: "2026-04-15",
            status: "DRAFT",
            completedAt: null,
            monthlyListId: null,
          }),
        ],
        totalElements: 2,
        totalPages: 1,
        pageSize: 20,
        pageNumber: 0,
      });

      await renderReconciliationList();

      const marker = screen.getByTestId("list-locked-recon-1");
      expect(marker).toHaveTextContent("List locked");
      expect(marker).toHaveAttribute(
        "href",
        "/org/acme/trust-accounting/reconciliation/lists/list-1"
      );
      expect(screen.queryByTestId("list-locked-recon-2")).not.toBeInTheDocument();
    });

    it("says on step 5 that the month's list was locked and links to it", async () => {
      await driveToReviewWithAllLinesAutoMatched();
      mockCalculateReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
      });
      mockCompleteReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "COMPLETED", monthlyListId: "list-1" }),
      });

      fireEvent.click(screen.getByTestId("step-next-btn"));
      expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
      fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

      const locked = await screen.findByTestId("monthly-list-locked");
      expect(locked).toHaveTextContent("March 2026 list locked");
      expect(locked).toHaveTextContent(
        `${zar(145000)} agrees with the cashbook and the adjusted bank balance.`
      );
      expect(screen.getByTestId("view-locked-list")).toHaveAttribute(
        "href",
        "/org/acme/trust-accounting/reconciliation/lists/list-1"
      );
      expect(screen.queryByTestId("monthly-list-not-locked")).not.toBeInTheDocument();
    });

    it("says on step 5 that no list was locked for a mid-month period end", async () => {
      await driveToReviewWithAllLinesAutoMatched();
      mockCalculateReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
      });
      mockCompleteReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "COMPLETED", periodEnd: "2026-03-15", monthlyListId: null }),
      });

      fireEvent.click(screen.getByTestId("step-next-btn"));
      expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
      fireEvent.click(screen.getByTestId("complete-reconciliation-btn"));

      const notLocked = await screen.findByTestId("monthly-list-not-locked");
      expect(notLocked).toHaveTextContent(
        `No monthly list was locked: ${formatLocalDate("2026-03-15")} is not the last day of a month.`
      );
      expect(screen.queryByTestId("view-locked-list")).not.toBeInTheDocument();
    });

    it("shows a locked list's totals, creditors by kind label, matters and the entries made after the lock", async () => {
      mockFetchMonthlyList.mockResolvedValue(makeMonthlyList());

      render(
        await MonthlyListPage({ params: Promise.resolve({ slug: "acme", listId: "list-1" }) })
      );

      expect(mockFetchMonthlyList).toHaveBeenCalledWith("list-1");
      expect(
        screen.getByText(`Trust creditors list — ${formatLocalDate("2026-03-31")}`)
      ).toBeInTheDocument();
      const creditors = screen.getByTestId("monthly-list-creditors");
      expect(within(creditors).getByText("Ndlovu Family Trust")).toBeInTheDocument();
      expect(within(creditors).getByText("Invested (section 86(4))")).toBeInTheDocument();
      expect(within(creditors).getByText("Legal Practitioners' Fidelity Fund")).toBeInTheDocument();
      expect(within(creditors).getByText("Estate late M Ndlovu")).toBeInTheDocument();
      expect(within(creditors).getByText("Not linked to a matter")).toBeInTheDocument();
      expect(
        within(creditors).getByText(
          `ACB CREDIT UNKNOWN · received ${formatLocalDate("2026-03-22")}`
        )
      ).toBeInTheDocument();
      // No raw kind ever reaches the screen
      expect(creditors).not.toHaveTextContent("CLIENT_INVESTMENT");
      expect(creditors).not.toHaveTextContent("UNIDENTIFIED");
      expect(creditors).toHaveTextContent(zar(145000));

      const afterLock = screen.getByTestId("monthly-list-after-lock");
      expect(within(afterLock).getByText("Entered after this list was locked")).toBeInTheDocument();
      const entry = within(afterLock).getByTestId("after-lock-entry-tx-late");
      expect(entry).toHaveTextContent("Deposit");
      expect(entry).toHaveTextContent("DEP-LATE");
      expect(entry).toHaveTextContent(zar(250));
      expect(afterLock).toHaveTextContent(zar(145250));
    });

    it("says nothing was entered after the lock when there are no such entries", async () => {
      mockFetchMonthlyList.mockResolvedValue(
        makeMonthlyList({
          adjustmentsSinceLock: {
            entries: [],
            cashbook: 0,
            clientLedgers: 0,
            investmentCreditors: 0,
            fundLedger: 0,
            unidentifiedDeposits: 0,
            interAccount: 0,
            liveCreditorTotal: 145000,
            changeInCreditorTotal: 0,
          },
        })
      );

      render(
        await MonthlyListPage({ params: Promise.resolve({ slug: "acme", listId: "list-1" }) })
      );

      expect(
        within(screen.getByTestId("monthly-list-after-lock")).getByText(
          "Nothing has been entered after this list was locked."
        )
      ).toBeInTheDocument();
    });
  });

  // Test 10: while the review step is being prepared (transactions, create,
  // calculate), Back and Review Matches are disabled, so a double-click cannot
  // create a second draft and a late completion cannot land after Back.
  it("disables Back and Review Matches while the review step is being prepared", async () => {
    await driveToReviewWithAllLinesAutoMatched();
    let finishCalculate: (v: unknown) => void = () => {};
    mockCalculateReconciliation.mockReturnValue(
      new Promise((resolve) => (finishCalculate = resolve))
    );

    fireEvent.click(screen.getByTestId("step-next-btn"));
    await waitFor(() => expect(mockCalculateReconciliation).toHaveBeenCalledWith("recon-1"));
    expect(screen.getByTestId("auto-match-back-btn")).toBeDisabled();
    expect(screen.getByTestId("step-next-btn")).toBeDisabled();
    fireEvent.click(screen.getByTestId("step-next-btn"));
    expect(mockCreateReconciliation).toHaveBeenCalledTimes(1);

    await act(async () => {
      finishCalculate({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: true, completedAt: null }),
      });
    });
    expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();
  });

  // Test 11: `max` on the date input does not stop a typed future date, so Next
  // refuses a period end after today (South Africa time) and stays on step 1.
  describe("period end in the future", () => {
    beforeEach(() => {
      // 22:30 UTC on 17 Nov is 00:30 on 18 Nov in South Africa (UTC+2): today is 2026-11-18.
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date("2026-11-17T22:30:00Z"));
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    async function renderStepOne() {
      await act(async () => {
        render(
          <Suspense fallback={null}>
            <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
          </Suspense>
        );
      });
      await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
    }

    it("keeps step 1 with an error when the period end is after today", async () => {
      await renderStepOne();
      fireEvent.change(screen.getByTestId("period-end-input"), {
        target: { value: "2026-11-19" },
      });
      fireEvent.click(screen.getByTestId("step-next-btn"));

      expect(screen.getByTestId("step-select")).toBeInTheDocument();
      expect(screen.queryByTestId("file-input")).not.toBeInTheDocument();
      expect(screen.getByText("The period end cannot be in the future")).toBeInTheDocument();
    });

    it("advances to the upload step when the period end is today", async () => {
      await renderStepOne();
      fireEvent.change(screen.getByTestId("period-end-input"), {
        target: { value: "2026-11-18" },
      });
      fireEvent.click(screen.getByTestId("step-next-btn"));

      expect(await screen.findByTestId("file-input")).toBeInTheDocument();
      expect(screen.queryByText("The period end cannot be in the future")).not.toBeInTheDocument();
    });
  });

  // ── 647A: Record from statement ───────────────────────────────────

  describe("record from statement (647A)", () => {
    const FUND_INTEREST = "Interest received for the Legal Practitioners' Fidelity Fund";
    const CHARGE_REFUND = "Bank charge refunded from the business account";
    const FUND_SWEEP = "Interest paid to the Legal Practitioners' Fidelity Fund";
    const INVESTMENT_INTEREST = "Section 86(4) investment interest";
    const GROSS = "Gross — the Fund's 5% leaves in a separate debit";
    const NET = "Net of the Fund's 5%";

    function makeInvestment(overrides: Partial<TrustInvestment> = {}): TrustInvestment {
      return {
        id: "inv-1",
        trustAccountId: "acc-1",
        investmentAccountId: "acc-s864",
        investmentAccountName: "Section 86(4) Investment Account",
        clientId: "cust-1",
        clientName: "Sipho Dlamini",
        matterId: "matter-1",
        institution: "Investec",
        accountNumber: "INV-864",
        principal: 30000,
        interestRate: 0.065,
        depositDate: "2026-09-01",
        maturityDate: null,
        grossInterest: 0,
        fundShare: 0,
        netInterest: 0,
        balance: 30000,
        status: "ACTIVE",
        awaitingApproval: false,
        withdrawalDate: null,
        withdrawalAmount: null,
        depositTransactionId: "txn-dep",
        withdrawalTransactionId: null,
        notes: null,
        investmentBasis: "CLIENT_INSTRUCTION",
        createdAt: "2026-09-01T09:00:00Z",
        updatedAt: "2026-09-01T09:00:00Z",
        ...overrides,
      };
    }

    const classificationChoices = () =>
      within(screen.getByTestId("classification-radio"))
        .getAllByRole("radio")
        .map((r) => r.getAttribute("value"));

    function renderPane(
      lines: BankStatementLineResponse[],
      handlers: Partial<
        Pick<React.ComponentProps<typeof ReconciliationSplitPane>, "onRecord" | "onUnmatch">
      > = {},
      account: { accountSection?: TrustAccountSection; investments?: TrustInvestment[] } = {}
    ) {
      render(
        <ReconciliationSplitPane
          {...account}
          lines={lines}
          unmatchedTransactions={[]}
          reconciliation={null}
          currency="ZAR"
          onMatch={vi.fn()}
          onExclude={vi.fn()}
          onUnmatch={handlers.onUnmatch ?? vi.fn()}
          onRecord={handlers.onRecord ?? vi.fn().mockResolvedValue({ success: true })}
          onComplete={vi.fn()}
          isCompleting={false}
        />
      );
    }

    async function openRecordDialog(lineId: string) {
      fireEvent.click(screen.getByTestId(`bank-line-${lineId}`));
      fireEvent.click(screen.getByTestId(`record-btn-${lineId}`));
      return screen.findByTestId("record-from-statement-dialog");
    }

    it("pins the six classification labels to the registry's firm labels", () => {
      expect(STATEMENT_LINE_CLASSIFICATIONS.map((c) => [c.value, c.lineSign, c.label])).toEqual([
        ["FUND_INTEREST", 1, FUND_INTEREST],
        ["CHARGE_REFUND", 1, CHARGE_REFUND],
        ["UNIDENTIFIED_DEPOSIT", 1, "Unidentified deposit"],
        ["INVESTMENT_INTEREST", 1, INVESTMENT_INTEREST],
        ["FUND_SWEEP", -1, FUND_SWEEP],
        ["BANK_CHARGE", -1, "Bank charge"],
      ]);
    });

    // 648B (OQ-10 iii): the choices follow the line's sign AND the account's section
    it("on a section 86(4) account offers a credit investment interest and a charge refund, never Fund interest", async () => {
      renderPane(
        [makeBankLine({ id: "line-cr", amount: 400 })],
        {},
        {
          accountSection: "S86_4",
          investments: [makeInvestment()],
        }
      );
      await openRecordDialog("line-cr");

      expect(classificationChoices()).toEqual(["CHARGE_REFUND", "INVESTMENT_INTEREST"]);
      expect(screen.getByRole("radio", { name: INVESTMENT_INTEREST })).toBeInTheDocument();
      expect(screen.queryByRole("radio", { name: FUND_INTEREST })).not.toBeInTheDocument();
      expect(screen.queryByRole("radio", { name: "Unidentified deposit" })).not.toBeInTheDocument();
    });

    it("on a section 86(3) account offers a credit Fund interest and a charge refund, no unidentified deposit", async () => {
      renderPane([makeBankLine({ id: "line-cr", amount: 400 })], {}, { accountSection: "S86_3" });
      await openRecordDialog("line-cr");

      expect(classificationChoices()).toEqual(["FUND_INTEREST", "CHARGE_REFUND"]);
      cleanup();

      // A debit line offers the Fund payment and a bank charge on every section
      renderPane([makeBankLine({ id: "line-dr", amount: -20 })], {}, { accountSection: "S86_4" });
      await openRecordDialog("line-dr");
      expect(classificationChoices()).toEqual(["FUND_SWEEP", "BANK_CHARGE"]);
    });

    it("records a gross interest line against the investment: the line is the gross, Fund R20,00 / client R380,00", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane(
        [makeBankLine({ id: "line-cr", amount: 400 })],
        { onRecord },
        {
          accountSection: "S86_4",
          investments: [makeInvestment()],
        }
      );
      const dialog = await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: INVESTMENT_INTEREST }));
      const investment = within(dialog).getByLabelText("Investment");
      expect(within(investment).getByRole("option", { name: /Sipho Dlamini/ })).toBeInTheDocument();
      await user.selectOptions(investment, "inv-1");
      await user.click(screen.getByRole("radio", { name: GROSS }));

      const gross = within(dialog).getByLabelText("Gross interest") as HTMLInputElement;
      expect(gross.value).toBe("400");
      expect(gross.readOnly).toBe(true);
      expect(within(dialog).getByTestId("interest-fund-share").textContent).toMatch(/20,00/);
      expect(within(dialog).getByTestId("interest-client-share").textContent).toMatch(/380,00/);
      expect(within(dialog).queryByText(/\bGROSS\b|\bNET\b|INVESTMENT_INTEREST/)).toBeNull();

      await user.click(screen.getByTestId("record-submit"));
      await waitFor(() => expect(onRecord).toHaveBeenCalledTimes(1));
      expect(onRecord).toHaveBeenCalledWith(
        "line-cr",
        expect.objectContaining({
          classification: "INVESTMENT_INTEREST",
          investmentId: "inv-1",
          presentation: "GROSS",
        })
      );
    });

    it("records a net interest line with the gross the bank advised: gross R100,00 → R5,00 / R95,00", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane(
        [makeBankLine({ id: "line-cr", amount: 95 })],
        { onRecord },
        {
          accountSection: "S86_4",
          investments: [makeInvestment()],
        }
      );
      const dialog = await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: INVESTMENT_INTEREST }));
      await user.selectOptions(within(dialog).getByLabelText("Investment"), "inv-1");
      await user.click(screen.getByRole("radio", { name: NET }));
      const gross = within(dialog).getByLabelText("Gross interest") as HTMLInputElement;
      expect(gross.readOnly).toBe(false);
      await user.type(gross, "100");
      expect(within(dialog).getByTestId("interest-fund-share").textContent).toMatch(/5,00/);
      expect(within(dialog).getByTestId("interest-client-share").textContent).toMatch(/95,00/);

      await user.click(screen.getByTestId("record-submit"));
      await waitFor(() => expect(onRecord).toHaveBeenCalledTimes(1));
      expect(onRecord).toHaveBeenCalledWith(
        "line-cr",
        expect.objectContaining({
          classification: "INVESTMENT_INTEREST",
          investmentId: "inv-1",
          presentation: "NET",
          grossAmount: 100,
        })
      );
    });

    it("asks section 86(4) interest for the investment and the presentation before recording", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane(
        [makeBankLine({ id: "line-cr", amount: 95 })],
        { onRecord },
        {
          accountSection: "S86_4",
          investments: [makeInvestment()],
        }
      );
      await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: INVESTMENT_INTEREST }));
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByText("Choose the investment")).toBeInTheDocument();
      expect(
        screen.getByText("Say whether the bank credited the gross or the net")
      ).toBeInTheDocument();
      expect(onRecord).not.toHaveBeenCalled();
    });

    it("pre-selects a section 86(4) interest suggestion on a section 86(4) account (647C, 648B)", async () => {
      renderPane(
        [
          makeBankLine({
            id: "line-cr",
            amount: 400,
            suggestedClassification: "INVESTMENT_INTEREST",
          }),
        ],
        {},
        { accountSection: "S86_4", investments: [makeInvestment()] }
      );
      await openRecordDialog("line-cr");

      expect(screen.getByRole("radio", { name: INVESTMENT_INTEREST })).toBeChecked();
      expect(screen.getByTestId("classification-suggested-note")).toBeInTheDocument();
    });

    it("offers a credit line interest received, a charge refund and an unidentified deposit", async () => {
      renderPane([makeBankLine({ id: "line-cr", amount: 1234.56 })]);

      await openRecordDialog("line-cr");

      const radios = screen.getAllByRole("radio");
      expect(radios).toHaveLength(3);
      expect(screen.getByRole("radio", { name: FUND_INTEREST })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: CHARGE_REFUND })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "Unidentified deposit" })).toBeInTheDocument();
      expect(screen.queryByRole("radio", { name: "Bank charge" })).not.toBeInTheDocument();
    });

    it("records a credit as an unidentified deposit, with no charge fields (647B)", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane([makeBankLine({ id: "line-cr", amount: 300 })], { onRecord });
      await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: "Unidentified deposit" }));
      expect(screen.getByTestId("classification-unidentified-note")).toHaveTextContent(
        "Held on the unidentified deposits ledger until you identify the owner."
      );
      expect(screen.queryByTestId("charge-recoverable-radio")).not.toBeInTheDocument();
      expect(screen.queryByTestId("charge-vat-input")).not.toBeInTheDocument();
      await user.type(screen.getByTestId("record-notes-input"), "Ask the bank");
      await user.click(screen.getByTestId("record-submit"));

      await waitFor(() => expect(onRecord).toHaveBeenCalledTimes(1));
      expect(onRecord).toHaveBeenCalledWith(
        "line-cr",
        expect.objectContaining({ classification: "UNIDENTIFIED_DEPOSIT", notes: "Ask the bank" })
      );
      await waitFor(() =>
        expect(screen.queryByTestId("record-from-statement-dialog")).not.toBeInTheDocument()
      );
    });

    it("does not offer an unidentified deposit on a debit line", async () => {
      renderPane([makeBankLine({ id: "line-dr", amount: -300 })]);
      await openRecordDialog("line-dr");
      expect(screen.queryByRole("radio", { name: "Unidentified deposit" })).not.toBeInTheDocument();
    });

    it("offers a debit line only the Fund payment and a bank charge", async () => {
      renderPane([makeBankLine({ id: "line-dr", amount: -57.5 })]);

      await openRecordDialog("line-dr");

      expect(screen.getAllByRole("radio")).toHaveLength(2);
      expect(screen.getByRole("radio", { name: FUND_SWEEP })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "Bank charge" })).toBeInTheDocument();
      expect(screen.queryByRole("radio", { name: FUND_INTEREST })).not.toBeInTheDocument();
    });

    it("keeps the dialog open and the line selected when a radio inside it is clicked", async () => {
      renderPane([makeBankLine({ id: "line-dr", amount: -57.5 })]);
      await openRecordDialog("line-dr");

      fireEvent.click(screen.getByRole("radio", { name: "Bank charge" }));

      expect(screen.getByTestId("record-from-statement-dialog")).toBeInTheDocument();
      expect(screen.getByTestId("bank-line-line-dr").className).toContain("ring-2");
      expect(await screen.findByTestId("charge-vat-input")).toBeInTheDocument();
    });

    it("pre-selects the parser's suggestion and shows its fields, without recording (647C)", async () => {
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane(
        [makeBankLine({ id: "line-dr", amount: -57.5, suggestedClassification: "BANK_CHARGE" })],
        { onRecord }
      );
      await openRecordDialog("line-dr");

      expect(screen.getByRole("radio", { name: "Bank charge" })).toBeChecked();
      expect(screen.getByRole("radio", { name: FUND_SWEEP })).not.toBeChecked();
      expect(await screen.findByTestId("charge-vat-input")).toBeInTheDocument();
      expect(screen.getByTestId("classification-suggested-note")).toBeInTheDocument();
      expect(onRecord).not.toHaveBeenCalled();

      // The firm may choose otherwise: the note goes with the suggestion
      fireEvent.click(screen.getByRole("radio", { name: FUND_SWEEP }));
      expect(screen.getByRole("radio", { name: FUND_SWEEP })).toBeChecked();
      expect(screen.queryByTestId("classification-suggested-note")).not.toBeInTheDocument();
      expect(onRecord).not.toHaveBeenCalled();
    });

    it("pre-selects nothing without a suggestion, or for one the line's sign does not allow (647C)", async () => {
      const noneChecked = () =>
        screen.getAllByRole("radio").every((r) => r.getAttribute("aria-checked") !== "true");

      renderPane([
        makeBankLine({ id: "line-dr", amount: -57.5, suggestedClassification: "FUND_INTEREST" }),
      ]);
      await openRecordDialog("line-dr");
      expect(noneChecked()).toBe(true);
      expect(screen.queryByTestId("classification-suggested-note")).not.toBeInTheDocument();
      cleanup();

      renderPane([
        makeBankLine({
          id: "line-cr",
          amount: 400,
          suggestedClassification: "INVESTMENT_INTEREST",
        }),
      ]);
      await openRecordDialog("line-cr");
      expect(noneChecked()).toBe(true);
      cleanup();

      renderPane([makeBankLine({ id: "line-cr", amount: 1234.56, suggestedClassification: null })]);
      await openRecordDialog("line-cr");
      expect(noneChecked()).toBe(true);
    });

    it("asks a bank charge for recoverable and VAT, and refuses VAT at or above the charge", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane([makeBankLine({ id: "line-dr", amount: -57.5 })], { onRecord });
      await openRecordDialog("line-dr");

      await user.click(screen.getByRole("radio", { name: "Bank charge" }));
      expect(screen.getByTestId("charge-recoverable-radio")).toBeInTheDocument();
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByText("Say whether the charge is recoverable")).toBeInTheDocument();
      expect(screen.getByText("Enter the VAT amount (0 if none)")).toBeInTheDocument();

      await user.click(screen.getByRole("radio", { name: "Recoverable" }));
      await user.type(screen.getByTestId("charge-vat-input"), "57.50");
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByText("VAT must be less than the charge")).toBeInTheDocument();
      expect(onRecord).not.toHaveBeenCalled();
    });

    it("refuses a VAT amount with more than two decimals", async () => {
      const schema = recordFromStatementSchemaFor(7.5);
      const charge = { classification: "BANK_CHARGE" as const, chargeRecoverable: true };
      const tooPrecise = schema.safeParse({ ...charge, chargeVatAmount: 7.499 });
      expect(tooPrecise.success).toBe(false);
      expect(tooPrecise.error?.issues.map((i) => i.message)).toContain(
        "VAT has at most two decimal places"
      );
      expect(schema.safeParse({ ...charge, chargeVatAmount: 0.29 }).success).toBe(true);
      expect(schema.safeParse({ ...charge, chargeVatAmount: 7.49 }).success).toBe(true);

      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane([makeBankLine({ id: "line-dr", amount: -7.5 })], { onRecord });
      await openRecordDialog("line-dr");
      await user.click(screen.getByRole("radio", { name: "Bank charge" }));
      await user.click(screen.getByRole("radio", { name: "Recoverable" }));
      await user.type(screen.getByTestId("charge-vat-input"), "7.499");
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByText("VAT has at most two decimal places")).toBeInTheDocument();
      expect(onRecord).not.toHaveBeenCalled();
    });

    it("submits a valid bank charge and closes the dialog", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({ success: true });
      renderPane([makeBankLine({ id: "line-dr", amount: -57.5 })], { onRecord });
      await openRecordDialog("line-dr");

      await user.click(screen.getByRole("radio", { name: "Bank charge" }));
      await user.click(screen.getByRole("radio", { name: "Not recoverable" }));
      await user.type(screen.getByTestId("charge-vat-input"), "7.5");
      await user.click(screen.getByTestId("record-submit"));

      await waitFor(() => expect(onRecord).toHaveBeenCalledTimes(1));
      expect(onRecord).toHaveBeenCalledWith(
        "line-dr",
        expect.objectContaining({
          classification: "BANK_CHARGE",
          chargeRecoverable: false,
          chargeVatAmount: 7.5,
        })
      );
      await waitFor(() =>
        expect(screen.queryByTestId("record-from-statement-dialog")).not.toBeInTheDocument()
      );
    });

    it("shows a server refusal inside the dialog and keeps it open", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onRecord = vi.fn().mockResolvedValue({
        success: false,
        error: "This statement line is already matched, recorded or excluded",
      });
      renderPane([makeBankLine({ id: "line-cr", amount: 25 })], { onRecord });
      await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: FUND_INTEREST }));
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "This statement line is already matched, recorded or excluded"
      );
      expect(screen.getByTestId("record-from-statement-dialog")).toBeInTheDocument();
    });

    it("stays open on Escape while a record is in flight and shows its later refusal", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      let resolveRecord: (value: { success: boolean; error?: string }) => void = () => {};
      const onRecord = vi.fn(
        () =>
          new Promise<{ success: boolean; error?: string }>((resolve) => {
            resolveRecord = resolve;
          })
      );
      renderPane([makeBankLine({ id: "line-cr", amount: 25 })], { onRecord });
      await openRecordDialog("line-cr");

      await user.click(screen.getByRole("radio", { name: FUND_INTEREST }));
      await user.click(screen.getByTestId("record-submit"));
      await waitFor(() => expect(onRecord).toHaveBeenCalledTimes(1));

      await user.keyboard("{Escape}");
      expect(screen.getByTestId("record-from-statement-dialog")).toBeInTheDocument();

      await act(async () => {
        resolveRecord({ success: false, error: "Trust account is closed" });
      });
      expect(await screen.findByRole("alert")).toHaveTextContent("Trust account is closed");
      expect(screen.getByTestId("record-from-statement-dialog")).toBeInTheDocument();
    });

    it("labels the recoverable question for the Fidelity Fund's list", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      renderPane([makeBankLine({ id: "line-dr", amount: -57.5 })]);
      await openRecordDialog("line-dr");

      await user.click(screen.getByRole("radio", { name: "Bank charge" }));

      expect(screen.getByText("Recoverable from the Fidelity Fund?")).toBeInTheDocument();
      expect(
        screen.getByText(
          "Is this charge on the Legal Practitioners' Fidelity Fund's list of recoverable bank charges?"
        )
      ).toBeInTheDocument();
      expect(screen.queryByText(/from the bank\?/)).not.toBeInTheDocument();
    });

    it("shows a recorded line's classification label and counts it as resolved", () => {
      renderPane([
        makeBankLine({
          id: "line-rec",
          amount: -57.5,
          matchStatus: "RECORDED",
          classification: "BANK_CHARGE",
          classificationLabel: "Bank charge",
          trustTransactionId: "tx-9",
          createdTransactionIds: ["tx-9"],
        }),
      ]);

      expect(screen.getByTestId("recorded-badge-line-rec")).toHaveTextContent(
        "Recorded: Bank charge"
      );
      expect(screen.getByTestId("bank-line-line-rec").className).toContain("bg-teal-50");
      expect(screen.getByText("1 / 1 lines resolved")).toBeInTheDocument();
      expect(screen.queryByText("BANK_CHARGE")).not.toBeInTheDocument();
    });

    it("asks before unmatching a recorded line, saying its entry will be reversed", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const onUnmatch = vi.fn().mockResolvedValue(undefined);
      renderPane(
        [
          makeBankLine({
            id: "line-rec",
            amount: -300,
            transactionDate: "2026-03-01",
            matchStatus: "RECORDED",
            classification: "FUND_SWEEP",
            classificationLabel: FUND_SWEEP,
            trustTransactionId: "tx-9",
            createdTransactionIds: ["tx-9"],
          }),
        ],
        { onUnmatch }
      );

      await user.click(screen.getByTestId("unmatch-btn-line-rec"));
      const dialog = await screen.findByTestId("unmatch-recorded-dialog");
      expect(dialog).toHaveTextContent("reverses the entry it created");
      expect(dialog).toHaveTextContent(formatLocalDate("2026-03-01"));
      expect(dialog).toHaveTextContent(FUND_SWEEP);

      await user.click(screen.getByRole("button", { name: "Keep" }));
      await waitFor(() =>
        expect(screen.queryByTestId("unmatch-recorded-dialog")).not.toBeInTheDocument()
      );
      expect(onUnmatch).not.toHaveBeenCalled();

      await user.click(screen.getByTestId("unmatch-btn-line-rec"));
      await user.click(await screen.findByTestId("unmatch-recorded-confirm"));
      await waitFor(() => expect(onUnmatch).toHaveBeenCalledTimes(1));
      expect(onUnmatch).toHaveBeenCalledWith("line-rec");
    });

    it("unmatches a matched line at once, without a confirmation", () => {
      const onUnmatch = vi.fn().mockResolvedValue(undefined);
      renderPane(
        [
          makeBankLine({
            id: "line-m",
            matchStatus: "MANUALLY_MATCHED",
            trustTransactionId: "tx-1",
          }),
        ],
        { onUnmatch }
      );

      fireEvent.click(screen.getByTestId("unmatch-btn-line-m"));

      expect(onUnmatch).toHaveBeenCalledWith("line-m");
      expect(screen.queryByTestId("unmatch-recorded-dialog")).not.toBeInTheDocument();
    });

    it("reports a failed recalculation after a line is recorded", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const line = makeBankLine({ id: "line-1", amount: 1234.56, reference: "INT-OCT" });
      const statement = {
        id: "stmt-1",
        trustAccountId: "acc-1",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-31",
        openingBalance: 0,
        closingBalance: 1234.56,
        fileName: "march-2026.csv",
        format: "CSV",
        lineCount: 1,
        matchedCount: 0,
        status: "IMPORTED",
        importedBy: "member-1",
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z",
        lines: [line],
      };
      mockUploadBankStatement.mockResolvedValue({ success: true, data: statement });
      mockAutoMatch.mockResolvedValue({
        success: true,
        data: { totalLines: 1, autoMatched: 0, alreadyMatched: 0, unmatched: 1 },
      });
      mockFetchBankStatement.mockResolvedValue(statement);
      mockCreateReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
      });
      mockCalculateReconciliation
        .mockResolvedValueOnce({
          success: true,
          data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
        })
        .mockResolvedValueOnce({ success: false, error: "boom" });
      mockRecordFromStatement.mockResolvedValue({
        success: true,
        data: {
          line: {
            ...line,
            matchStatus: "RECORDED",
            classification: "FUND_INTEREST",
            classificationLabel: FUND_INTEREST,
            trustTransactionId: "tx-new",
            createdTransactionIds: ["tx-new"],
          },
          transactions: [],
          unidentifiedDepositId: null,
          investmentInterestId: null,
        },
      });

      await act(async () => {
        render(
          <Suspense fallback={null}>
            <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
          </Suspense>
        );
      });
      await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
      fireEvent.change(screen.getByTestId("period-end-input"), {
        target: { value: "2026-03-31" },
      });
      fireEvent.click(screen.getByTestId("step-next-btn"));
      fireEvent.change(await screen.findByTestId("file-input"), {
        target: { files: [new File(["csv"], "march-2026.csv", { type: "text/csv" })] },
      });
      await waitFor(() => expect(screen.getByTestId("step-next-btn")).toBeEnabled());
      fireEvent.click(screen.getByTestId("step-next-btn"));
      fireEvent.click(await screen.findByTestId("auto-match-btn"));
      expect(await screen.findByText("Auto-matched 0 lines")).toBeInTheDocument();
      fireEvent.click(screen.getByTestId("step-next-btn"));
      expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();

      await openRecordDialog("line-1");
      await user.click(screen.getByRole("radio", { name: FUND_INTEREST }));
      await user.click(screen.getByTestId("record-submit"));

      expect(await screen.findByTestId("reconciliation-error")).toHaveTextContent(
        "The line was recorded but the reconciliation could not be recalculated: boom"
      );
      expect(mockRecordFromStatement).toHaveBeenCalledWith(
        "line-1",
        expect.objectContaining({ classification: "FUND_INTEREST" })
      );
      expect(screen.getByTestId("recorded-badge-line-1")).toHaveTextContent(
        `Recorded: ${FUND_INTEREST}`
      );
    });

    it("ignores a recalculation that lands after the wizard was reset", async () => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      const line = makeBankLine({ id: "line-1", amount: 1234.56, reference: "INT-OCT" });
      const statement = {
        id: "stmt-1",
        trustAccountId: "acc-1",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-31",
        openingBalance: 0,
        closingBalance: 1234.56,
        fileName: "march-2026.csv",
        format: "CSV",
        lineCount: 1,
        matchedCount: 0,
        status: "IMPORTED",
        importedBy: "member-1",
        createdAt: "2026-04-01T00:00:00Z",
        updatedAt: "2026-04-01T00:00:00Z",
        lines: [line],
      };
      mockUploadBankStatement.mockResolvedValue({ success: true, data: statement });
      mockAutoMatch.mockResolvedValue({
        success: true,
        data: { totalLines: 1, autoMatched: 0, alreadyMatched: 0, unmatched: 1 },
      });
      mockFetchBankStatement.mockResolvedValue(statement);
      mockCreateReconciliation.mockResolvedValue({
        success: true,
        data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
      });
      let resolveRecalc: (value: { success: boolean; error?: string }) => void = () => {};
      mockCalculateReconciliation
        .mockResolvedValueOnce({
          success: true,
          data: makeRecon({ status: "DRAFT", isBalanced: false, completedAt: null }),
        })
        .mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              resolveRecalc = resolve;
            })
        );
      mockRecordFromStatement.mockResolvedValue({
        success: true,
        data: {
          line: {
            ...line,
            matchStatus: "RECORDED",
            classification: "FUND_INTEREST",
            classificationLabel: FUND_INTEREST,
            trustTransactionId: "tx-new",
            createdTransactionIds: ["tx-new"],
          },
          transactions: [],
          unidentifiedDepositId: null,
          investmentInterestId: null,
        },
      });

      await act(async () => {
        render(
          <Suspense fallback={null}>
            <NewReconciliationPage params={Promise.resolve({ slug: "acme" })} />
          </Suspense>
        );
      });
      await waitFor(() => expect(screen.getByTestId("account-select")).toHaveValue("acc-1"));
      fireEvent.change(screen.getByTestId("period-end-input"), {
        target: { value: "2026-03-31" },
      });
      fireEvent.click(screen.getByTestId("step-next-btn"));
      fireEvent.change(await screen.findByTestId("file-input"), {
        target: { files: [new File(["csv"], "march-2026.csv", { type: "text/csv" })] },
      });
      await waitFor(() => expect(screen.getByTestId("step-next-btn")).toBeEnabled());
      fireEvent.click(screen.getByTestId("step-next-btn"));
      fireEvent.click(await screen.findByTestId("auto-match-btn"));
      expect(await screen.findByText("Auto-matched 0 lines")).toBeInTheDocument();
      fireEvent.click(screen.getByTestId("step-next-btn"));
      expect(await screen.findByTestId("reconciliation-split-pane")).toBeInTheDocument();

      await openRecordDialog("line-1");
      await user.click(screen.getByRole("radio", { name: FUND_INTEREST }));
      await user.click(screen.getByTestId("record-submit"));
      await waitFor(() => expect(mockCalculateReconciliation).toHaveBeenCalledTimes(2));

      // Back to step 1 while the recalculation is in flight, and change the period (a reset).
      fireEvent.click(screen.getByRole("button", { name: "Back", hidden: true }));
      fireEvent.click(await screen.findByTestId("auto-match-back-btn"));
      fireEvent.click(await screen.findByRole("button", { name: "Back" }));
      fireEvent.change(await screen.findByTestId("period-end-input"), {
        target: { value: "2026-02-28" },
      });

      await act(async () => {
        resolveRecalc({ success: false, error: "boom" });
      });

      expect(screen.getByTestId("step-select")).toBeInTheDocument();
      expect(screen.queryByTestId("reconciliation-error")).not.toBeInTheDocument();
    });
  });
});
