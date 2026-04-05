import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";

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

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions",
  () => ({
    fetchReconciliations: (...args: unknown[]) =>
      mockFetchReconciliations(...args),
    uploadBankStatement: (...args: unknown[]) =>
      mockUploadBankStatement(...args),
    autoMatch: (...args: unknown[]) => mockAutoMatch(...args),
    manualMatch: (...args: unknown[]) => mockManualMatch(...args),
    unmatch: (...args: unknown[]) => mockUnmatch(...args),
    excludeLine: (...args: unknown[]) => mockExcludeLine(...args),
    createReconciliation: (...args: unknown[]) =>
      mockCreateReconciliation(...args),
    calculateReconciliation: (...args: unknown[]) =>
      mockCalculateReconciliation(...args),
    completeReconciliation: (...args: unknown[]) =>
      mockCompleteReconciliation(...args),
    fetchBankStatement: (...args: unknown[]) =>
      mockFetchBankStatement(...args),
    fetchBankStatements: (...args: unknown[]) =>
      mockFetchBankStatements(...args),
    fetchReconciliation: (...args: unknown[]) =>
      mockFetchReconciliation(...args),
  }),
);

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

// ── Mock transaction actions ─────────────────────────────────────
vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/transactions/actions",
  () => ({
    fetchTransactions: vi.fn().mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    }),
  }),
);

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
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";
import type { TrustReconciliationResponse } from "@/lib/types";

// ── Helpers ──────────────────────────────────────────────────────

function makeRecon(overrides: Record<string, unknown> = {}) {
  return {
    id: "recon-1",
    trustAccountId: "acc-1",
    periodEnd: "2026-03-31",
    bankStatementId: "stmt-1",
    bankBalance: 142500,
    cashbookBalance: 145000,
    clientLedgerTotal: 145000,
    outstandingDeposits: 2500,
    outstandingPayments: 0,
    adjustedBankBalance: 145000,
    isBalanced: true,
    status: "COMPLETED",
    completedBy: "member-1",
    completedAt: "2026-04-02T14:00:00Z",
    notes: null,
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-02T14:00:00Z",
    ...overrides,
  };
}

function makeBankLine(overrides: Record<string, unknown> = {}) {
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
    excludedReason: null,
    createdAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

function makeTx(overrides: Record<string, unknown> = {}) {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    amount: 15000,
    customerId: "cust-1",
    projectId: null,
    counterpartyCustomerId: null,
    invoiceId: null,
    reference: "DEP/2026/001",
    description: "Deposit from Smith & Co",
    transactionDate: "2026-03-01",
    status: "APPROVED",
    approvedBy: "member-1",
    approvedAt: "2026-03-01T10:00:00Z",
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-03-01T09:00:00Z",
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
      />,
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

    const transactions = [
      makeTx({ id: "tx-1", amount: 15000, reference: "DEP-001" }),
    ];

    render(
      <ReconciliationSplitPane
        lines={lines}
        unmatchedTransactions={transactions}
        reconciliation={makeRecon({ isBalanced: false }) as unknown as TrustReconciliationResponse}
        currency="ZAR"
        onMatch={vi.fn()}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />,
    );

    expect(
      screen.getByTestId("reconciliation-split-pane"),
    ).toBeInTheDocument();
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

    const transactions = [
      makeTx({ id: "tx-1", amount: 15000, reference: "DEP-001" }),
    ];

    render(
      <ReconciliationSplitPane
        lines={lines}
        unmatchedTransactions={transactions}
        reconciliation={makeRecon({ isBalanced: false }) as unknown as TrustReconciliationResponse}
        currency="ZAR"
        onMatch={mockOnMatch}
        onExclude={vi.fn()}
        onUnmatch={vi.fn()}
        onComplete={vi.fn()}
        isCompleting={false}
      />,
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
        onComplete={vi.fn()}
        isCompleting={false}
      />,
    );

    const completeBtn = screen.getByTestId("complete-reconciliation-btn");
    expect(completeBtn).toBeDisabled();
    expect(
      screen.getByText("Reconciliation is not balanced"),
    ).toBeInTheDocument();
  });
});
