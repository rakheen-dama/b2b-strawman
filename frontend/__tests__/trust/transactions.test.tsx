import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock org settings API
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

// Mock capabilities API
const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) =>
    mockFetchMyCapabilities(...args),
}));

// Mock trust account actions (dashboard)
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) =>
    mockFetchTrustAccounts(...args),
}));

// Mock transaction actions
const mockFetchTransactions = vi.fn();
const mockApproveTransaction = vi.fn();
const mockRejectTransaction = vi.fn();
const mockReverseTransaction = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/transactions/actions",
  () => ({
    fetchTransactions: (...args: unknown[]) =>
      mockFetchTransactions(...args),
    recordDeposit: vi.fn(),
    recordPayment: vi.fn(),
    recordTransfer: vi.fn(),
    recordFeeTransfer: vi.fn(),
    recordRefund: vi.fn(),
    approveTransaction: (...args: unknown[]) =>
      mockApproveTransaction(...args),
    rejectTransaction: (...args: unknown[]) =>
      mockRejectTransaction(...args),
    reverseTransaction: (...args: unknown[]) =>
      mockReverseTransaction(...args),
  }),
);

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

// Mock next/cache
vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}));

// Mock next/link
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

// Import components after mocks
import TransactionsPage from "@/app/(app)/org/[slug]/trust-accounting/transactions/page";
import { ApprovalBadge } from "@/components/trust/approval-badge";
import { ReversalButton } from "@/components/trust/reversal-button";

// ── Helpers ───────────────────────────────────────────────────────

function defaultTransactionData() {
  return {
    content: [
      {
        id: "tx-1",
        trustAccountId: "acc-1",
        transactionType: "DEPOSIT" as const,
        amount: 50000,
        customerId: "cust-1",
        projectId: null,
        counterpartyCustomerId: null,
        invoiceId: null,
        reference: "DEP-001",
        description: "Initial deposit",
        transactionDate: "2026-04-01",
        status: "APPROVED" as const,
        approvedBy: "member-1",
        approvedAt: "2026-04-01T10:00:00Z",
        secondApprovedBy: null,
        secondApprovedAt: null,
        rejectedBy: null,
        rejectedAt: null,
        rejectionReason: null,
        reversalOf: null,
        reversedById: null,
        bankStatementLineId: null,
        recordedBy: "member-2",
        createdAt: "2026-04-01T09:00:00Z",
      },
      {
        id: "tx-2",
        trustAccountId: "acc-1",
        transactionType: "PAYMENT" as const,
        amount: 15000,
        customerId: "cust-1",
        projectId: "proj-1",
        counterpartyCustomerId: null,
        invoiceId: "inv-1",
        reference: "PAY-001",
        description: "Counsel fee",
        transactionDate: "2026-04-02",
        status: "AWAITING_APPROVAL" as const,
        approvedBy: null,
        approvedAt: null,
        secondApprovedBy: null,
        secondApprovedAt: null,
        rejectedBy: null,
        rejectedAt: null,
        rejectionReason: null,
        reversalOf: null,
        reversedById: null,
        bankStatementLineId: null,
        recordedBy: "member-2",
        createdAt: "2026-04-02T09:00:00Z",
      },
    ],
    totalElements: 2,
    totalPages: 1,
    pageSize: 20,
    pageNumber: 0,
  };
}

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  });
  mockFetchMyCapabilities.mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST", "APPROVE_TRUST_PAYMENT"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  });
  mockFetchTrustAccounts.mockResolvedValue([
    {
      id: "acc-1",
      accountName: "Main Trust Account",
      bankName: "FNB",
      branchCode: "250655",
      accountNumber: "1234567890",
      accountType: "GENERAL",
      isPrimary: true,
      requireDualApproval: true,
      paymentApprovalThreshold: 50000,
      status: "ACTIVE",
      openedDate: "2025-01-01",
      closedDate: null,
      notes: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ]);
  mockFetchTransactions.mockResolvedValue(defaultTransactionData());
}

async function renderPage(searchParams = {}) {
  const result = await TransactionsPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

// ── Tests ─────────────────────────────────────────────────────────

describe("Trust Transactions Page", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders transactions table with data", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByText("Transactions")).toBeInTheDocument();
    expect(screen.getByText("Transaction History")).toBeInTheDocument();
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();
    // Use getAllByText since type filter pills and table badges both have "Deposit"/"Payment"
    expect(screen.getAllByText("Deposit").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Payment").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
    expect(screen.getByText("AWAITING APPROVAL")).toBeInTheDocument();
    expect(screen.getByTestId("transactions-table")).toBeInTheDocument();
  });

  it("deposit dialog renders form fields and submit button", async () => {
    // Render the deposit dialog directly (client component)
    const { RecordDepositDialog } = await import(
      "@/components/trust/RecordDepositDialog"
    );

    const onOpenChange = vi.fn();
    render(
      <RecordDepositDialog
        accountId="acc-1"
        open={true}
        onOpenChange={onOpenChange}
      />,
    );

    // The dialog should be visible with all expected fields
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    // Verify form fields are present
    expect(screen.getByPlaceholderText("Client UUID")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Matter UUID (optional)")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("0.00")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("e.g. DEP/2026/001")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("Additional details about this deposit"),
    ).toBeInTheDocument();

    // Verify submit and cancel buttons
    expect(
      screen.getByRole("button", { name: /Record Deposit/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Cancel/i }),
    ).toBeInTheDocument();

    // Verify field labels
    expect(screen.getByText("Client ID")).toBeInTheDocument();
    expect(screen.getByText("Amount")).toBeInTheDocument();
    expect(screen.getByText("Reference")).toBeInTheDocument();
    expect(screen.getByText("Transaction Date")).toBeInTheDocument();
  });

  it("approval badge shows approve/reject for AWAITING_APPROVAL", () => {
    render(
      <ApprovalBadge transactionId="tx-2" status="AWAITING_APPROVAL" />,
    );

    expect(screen.getByTestId("approve-button")).toBeInTheDocument();
    expect(screen.getByTestId("reject-button")).toBeInTheDocument();
  });

  it("reject dialog requires reason", async () => {
    const user = userEvent.setup();

    render(
      <ApprovalBadge transactionId="tx-2" status="AWAITING_APPROVAL" />,
    );

    // Click reject to open dialog
    await user.click(screen.getByTestId("reject-button"));

    // Wait for the dialog to appear
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    // Try to submit without reason - validation should prevent it
    const confirmButton = screen.getByTestId("confirm-reject-button");
    await user.click(confirmButton);

    await waitFor(() => {
      expect(screen.getByText("Reason is required")).toBeInTheDocument();
    });

    // Now provide a reason and submit
    mockRejectTransaction.mockResolvedValue({ success: true });
    const reasonInput = screen.getByTestId("rejection-reason-input");
    await user.type(reasonInput, "Insufficient documentation");
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockRejectTransaction).toHaveBeenCalledWith(
        "tx-2",
        "Insufficient documentation",
      );
    });
  });

  it("reverse dialog creates reversal with reason", async () => {
    const user = userEvent.setup();
    mockReverseTransaction.mockResolvedValue({ success: true });

    render(<ReversalButton transactionId="tx-1" />);

    // Click reverse to open dialog
    await user.click(screen.getByTestId("reverse-button"));

    // Wait for the dialog to appear
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    // Fill in reason
    const reasonInput = screen.getByTestId("reversal-reason-input");
    await user.type(reasonInput, "Duplicate entry");

    const confirmButton = screen.getByTestId("confirm-reverse-button");
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockReverseTransaction).toHaveBeenCalledWith(
        "tx-1",
        "Duplicate entry",
      );
    });
  });
});
