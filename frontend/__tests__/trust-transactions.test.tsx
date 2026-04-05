import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock server actions for transactions ─────────────────────────
const mockFetchTransactions = vi.fn();
const mockRecordDeposit = vi.fn();
const mockApproveTransaction = vi.fn();
const mockRejectTransaction = vi.fn();
const mockReverseTransaction = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/transactions/actions",
  () => ({
    fetchTransactions: (...args: unknown[]) =>
      mockFetchTransactions(...args),
    recordDeposit: (...args: unknown[]) => mockRecordDeposit(...args),
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

// ── Mock org settings & capabilities ─────────────────────────────
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: vi.fn().mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  }),
}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST", "APPROVE_TRUST_PAYMENT"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  }),
}));

// ── Mock next/navigation ─────────────────────────────────────────
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: vi.fn(),
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
import TransactionsPage from "@/app/(app)/org/[slug]/trust-accounting/transactions/page";
import { RecordDepositDialog } from "@/components/trust/RecordDepositDialog";
import { ApprovalBadge } from "@/components/trust/approval-badge";
import { ReversalButton } from "@/components/trust/reversal-button";

// ── Helpers ──────────────────────────────────────────────────────

function makeTx(overrides: Record<string, unknown> = {}) {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    amount: 50000,
    customerId: "cust-1",
    projectId: null,
    counterpartyCustomerId: null,
    invoiceId: null,
    reference: "DEP-001",
    description: "Initial deposit",
    transactionDate: "2026-04-01",
    status: "APPROVED",
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
    ...overrides,
  };
}

async function renderTransactionsPage() {
  const result = await TransactionsPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({}),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Trust Transactions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Table renders with mock data
  it("renders transactions table with mock data", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-1",
          reference: "DEP-001",
          transactionType: "DEPOSIT",
          status: "APPROVED",
        }),
        makeTx({
          id: "tx-2",
          reference: "PAY-001",
          transactionType: "PAYMENT",
          amount: 3500,
          status: "AWAITING_APPROVAL",
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    expect(screen.getByTestId("transactions-table")).toBeInTheDocument();
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();
    expect(screen.getByText("2 transactions found")).toBeInTheDocument();
  });

  // Test 2: Deposit dialog submits correctly
  it("submits deposit dialog with valid form data", async () => {
    mockRecordDeposit.mockResolvedValue({ success: true });

    render(
      <RecordDepositDialog
        accountId="acc-1"
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(
      screen.getByText(
        "Record a trust deposit from a client into the trust account.",
      ),
    ).toBeInTheDocument();

    // Fill the client ID field
    const clientInput = screen.getByPlaceholderText("Client UUID");
    fireEvent.change(clientInput, {
      target: { value: "550e8400-e29b-41d4-a716-446655440000" },
    });

    // Fill amount using fireEvent for number input
    const amountInput = screen.getByPlaceholderText("0.00");
    fireEvent.change(amountInput, { target: { value: "15000" } });

    // Fill reference
    const refInput = screen.getByPlaceholderText("e.g. DEP/2026/001");
    fireEvent.change(refInput, { target: { value: "DEP/2026/TEST" } });

    // Submit via the form submit button
    const form = amountInput.closest("form")!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(mockRecordDeposit).toHaveBeenCalledWith(
        "acc-1",
        "acme",
        expect.objectContaining({
          customerId: "550e8400-e29b-41d4-a716-446655440000",
          reference: "DEP/2026/TEST",
        }),
      );
    });
  });

  // Test 3: Approval badge shows buttons for AWAITING_APPROVAL
  it("shows Approve and Reject buttons for AWAITING_APPROVAL transactions", () => {
    render(
      <ApprovalBadge
        transactionId="tx-pending"
        status="AWAITING_APPROVAL"
      />,
    );

    expect(screen.getByTestId("approve-button")).toBeInTheDocument();
    expect(screen.getByTestId("reject-button")).toBeInTheDocument();
  });

  // Test 4: Reject dialog requires reason
  it("requires a reason when rejecting a transaction", async () => {
    const user = userEvent.setup();

    render(
      <ApprovalBadge
        transactionId="tx-pending"
        status="AWAITING_APPROVAL"
      />,
    );

    // Click reject to open the dialog
    await user.click(screen.getByTestId("reject-button"));

    await waitFor(() => {
      expect(
        screen.getByText("Reject Transaction", { selector: "h2" }),
      ).toBeInTheDocument();
    });

    // Try to submit with empty reason -- click the confirm button
    await user.click(screen.getByTestId("confirm-reject-button"));

    // Validation should prevent submission -- reason is required
    await waitFor(() => {
      expect(screen.getByText("Reason is required")).toBeInTheDocument();
    });

    // Confirm the server action was NOT called
    expect(mockRejectTransaction).not.toHaveBeenCalled();
  });

  // Test 5: Reversal dialog creates reversal
  it("submits reversal with reason", async () => {
    mockReverseTransaction.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<ReversalButton transactionId="tx-approved" />);

    // Click reverse button to open dialog
    await user.click(screen.getByTestId("reverse-button"));

    await waitFor(() => {
      expect(
        screen.getByText("Reverse Transaction", { selector: "h2" }),
      ).toBeInTheDocument();
    });

    // Fill in reason
    const reasonInput = screen.getByTestId("reversal-reason-input");
    await user.type(reasonInput, "Duplicate entry");

    // Submit
    await user.click(screen.getByTestId("confirm-reverse-button"));

    await waitFor(() => {
      expect(mockReverseTransaction).toHaveBeenCalledWith(
        "tx-approved",
        "Duplicate entry",
      );
    });
  });
});
