import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// ── Mock org settings & capabilities ─────────────────────────────
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) =>
    mockFetchMyCapabilities(...args),
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

// ── Mock reconciliation actions ──────────────────────────────────
const mockFetchReconciliations = vi.fn();
const mockUploadBankStatement = vi.fn();
const mockManualMatch = vi.fn();
const mockUnmatch = vi.fn();
const mockExcludeLine = vi.fn();
const mockCompleteReconciliation = vi.fn();
const mockAutoMatch = vi.fn();
const mockFetchBankStatement = vi.fn();
const mockCreateReconciliation = vi.fn();
const mockCalculateReconciliation = vi.fn();
const mockFetchUnmatchedTransactions = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions",
  () => ({
    fetchReconciliations: (...args: unknown[]) =>
      mockFetchReconciliations(...args),
    uploadBankStatement: (...args: unknown[]) =>
      mockUploadBankStatement(...args),
    manualMatch: (...args: unknown[]) => mockManualMatch(...args),
    unmatch: (...args: unknown[]) => mockUnmatch(...args),
    excludeLine: (...args: unknown[]) => mockExcludeLine(...args),
    completeReconciliation: (...args: unknown[]) =>
      mockCompleteReconciliation(...args),
    autoMatch: (...args: unknown[]) => mockAutoMatch(...args),
    fetchBankStatement: (...args: unknown[]) =>
      mockFetchBankStatement(...args),
    fetchBankStatements: vi.fn().mockResolvedValue([]),
    createReconciliation: (...args: unknown[]) =>
      mockCreateReconciliation(...args),
    calculateReconciliation: (...args: unknown[]) =>
      mockCalculateReconciliation(...args),
    fetchReconciliation: vi.fn(),
    fetchUnmatchedTransactions: (...args: unknown[]) =>
      mockFetchUnmatchedTransactions(...args),
  }),
);

// ── Mock next/navigation ─────────────────────────────────────────
const mockPush = vi.fn();
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: mockPush,
    refresh: mockRefresh,
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

// ── Imports AFTER mocks (CRITICAL ORDER) ─────────────────────────
import ReconciliationPage from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/page";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";

// ── Helpers ──────────────────────────────────────────────────────

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  });
  mockFetchMyCapabilities.mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  });
}

async function renderListPage() {
  const result = await ReconciliationPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

const mockBankLines = [
  {
    id: "line-1",
    bankStatementId: "stmt-1",
    lineNumber: 1,
    transactionDate: "2026-03-15",
    description: "Client deposit",
    reference: "DEP001",
    amount: 5000,
    runningBalance: 105000,
    matchStatus: "UNMATCHED" as const,
    trustTransactionId: null,
    matchConfidence: null,
    excludedReason: null,
    createdAt: "2026-03-15T00:00:00Z",
  },
  {
    id: "line-2",
    bankStatementId: "stmt-1",
    lineNumber: 2,
    transactionDate: "2026-03-16",
    description: "Auto-matched deposit",
    reference: "DEP002",
    amount: 3000,
    runningBalance: 108000,
    matchStatus: "AUTO_MATCHED" as const,
    trustTransactionId: "txn-99",
    matchConfidence: 0.95,
    excludedReason: null,
    createdAt: "2026-03-16T00:00:00Z",
  },
];

const mockTransactions = [
  {
    id: "txn-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT" as const,
    amount: 5000,
    customerId: "cust-1",
    projectId: null,
    counterpartyCustomerId: null,
    invoiceId: null,
    reference: "TXN-001",
    description: "Client deposit",
    transactionDate: "2026-03-15",
    status: "APPROVED" as const,
    approvedBy: "user-1",
    approvedAt: "2026-03-15T01:00:00Z",
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    bankStatementLineId: null,
    recordedBy: "user-1",
    createdAt: "2026-03-15T00:00:00Z",
  },
];

const mockReconciliation = {
  id: "rec-1",
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
  status: "DRAFT" as const,
  completedBy: null,
  completedAt: null,
  notes: null,
  createdAt: "2026-03-31T00:00:00Z",
  updatedAt: "2026-03-31T00:00:00Z",
};

// ── Tests ────────────────────────────────────────────────────────

describe("Trust Reconciliation", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders reconciliation list page with table", async () => {
    setupDefaultMocks();
    mockFetchReconciliations.mockResolvedValue([
      {
        ...mockReconciliation,
        id: "rec-1",
        periodEnd: "2026-03-31",
        isBalanced: true,
      },
      {
        ...mockReconciliation,
        id: "rec-2",
        periodEnd: "2026-02-28",
        isBalanced: false,
        bankBalance: 130000,
        adjustedBankBalance: 132000,
        cashbookBalance: 135000,
      },
    ]);

    await renderListPage();

    expect(screen.getByTestId("reconciliation-table")).toBeInTheDocument();
    expect(screen.getByText("Balanced")).toBeInTheDocument();
    expect(screen.getByText("Unbalanced")).toBeInTheDocument();
    expect(screen.getByText("Mar 2026")).toBeInTheDocument();
    expect(screen.getByText("Feb 2026")).toBeInTheDocument();
  });

  it("upload triggers bank statement import", () => {
    mockUploadBankStatement.mockResolvedValue({
      id: "stmt-1",
      trustAccountId: "acc-1",
      periodStart: "2026-03-01",
      periodEnd: "2026-03-31",
      openingBalance: 100000,
      closingBalance: 142500,
      fileKey: "key",
      fileName: "march-2026.csv",
      format: "CSV",
      lineCount: 45,
      matchedCount: 0,
      status: "IMPORTED",
      importedBy: "user-1",
      createdAt: "2026-03-31T00:00:00Z",
      updatedAt: "2026-03-31T00:00:00Z",
    });

    const onUploadComplete = vi.fn();
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={onUploadComplete}
      />,
    );

    const fileInput = screen.getByTestId("file-input");
    const file = new File(["date,amount\n2026-03-15,5000"], "test.csv", {
      type: "text/csv",
    });
    fireEvent.change(fileInput, { target: { files: [file] } });

    const uploadBtn = screen.getByTestId("upload-btn");
    fireEvent.click(uploadBtn);

    expect(mockUploadBankStatement).toHaveBeenCalledWith(
      "acc-1",
      expect.any(FormData),
    );
  });

  it("split pane renders bank lines and unmatched transactions", () => {
    render(
      <ReconciliationSplitPane
        reconciliationId="rec-1"
        accountId="acc-1"
        bankStatementLines={mockBankLines}
        unmatchedTransactions={mockTransactions}
        reconciliation={mockReconciliation}
        currency="ZAR"
        onComplete={vi.fn()}
      />,
    );

    expect(screen.getByTestId("bank-lines-panel")).toBeInTheDocument();
    expect(screen.getByTestId("transactions-panel")).toBeInTheDocument();

    // Verify UNMATCHED line has amber classes
    const unmatchedLine = screen.getByTestId("bank-line-line-1");
    expect(unmatchedLine.className).toContain("border-amber-400");

    // Verify AUTO_MATCHED line has green classes
    const matchedLine = screen.getByTestId("bank-line-line-2");
    expect(matchedLine.className).toContain("border-green-500");
  });

  it("manual match links a bank line to a transaction", async () => {
    mockManualMatch.mockResolvedValue({ success: true });

    render(
      <ReconciliationSplitPane
        reconciliationId="rec-1"
        accountId="acc-1"
        bankStatementLines={mockBankLines}
        unmatchedTransactions={mockTransactions}
        reconciliation={mockReconciliation}
        currency="ZAR"
        onComplete={vi.fn()}
      />,
    );

    // Click the UNMATCHED bank line
    fireEvent.click(screen.getByTestId("bank-line-line-1"));

    // Click the candidate transaction
    fireEvent.click(screen.getByTestId("txn-row-txn-1"));

    // Click Match
    fireEvent.click(screen.getByTestId("match-btn"));

    expect(mockManualMatch).toHaveBeenCalledWith("line-1", "txn-1");
  });

  it("complete button is disabled when reconciliation is not balanced", () => {
    const unbalancedRec = {
      ...mockReconciliation,
      isBalanced: false,
      adjustedBankBalance: 132000,
      cashbookBalance: 135000,
    };

    render(
      <ReconciliationSplitPane
        reconciliationId="rec-1"
        accountId="acc-1"
        bankStatementLines={mockBankLines}
        unmatchedTransactions={mockTransactions}
        reconciliation={unbalancedRec}
        currency="ZAR"
        onComplete={vi.fn()}
      />,
    );

    const completeBtn = screen.getByTestId("complete-btn");
    expect(completeBtn).toBeDisabled();
  });
});
