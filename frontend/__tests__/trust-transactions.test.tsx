import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock server actions for transactions ─────────────────────────
const mockFetchTransactions = vi.fn();
const mockRecordDeposit = vi.fn();
const mockRecordTransfer = vi.fn();
const mockApproveTransaction = vi.fn();
const mockRejectTransaction = vi.fn();
const mockReverseTransaction = vi.fn();
const mockRecordFeeTransfer = vi.fn();
const mockRouterRefresh = vi.fn();
const mockToastSuccess = vi.fn();

// ── Mock sonner (LZKC-016: approval feedback toast) ──────────────
vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: vi.fn(),
  },
}));

// ── Mock server-side auth (page resolves current member) ─────────
vi.mock("@/lib/auth", () => ({
  AUTH_MODE: "mock",
  getSessionIdentity: vi.fn().mockResolvedValue(null),
  getAuthContext: vi.fn().mockResolvedValue(null),
  getAuthToken: vi.fn().mockResolvedValue(null),
  getCurrentUserEmail: vi.fn().mockResolvedValue(null),
  getCurrentUserInfo: vi.fn().mockResolvedValue(null),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  fetchTransactions: (...args: unknown[]) => mockFetchTransactions(...args),
  recordDeposit: (...args: unknown[]) => mockRecordDeposit(...args),
  recordPayment: vi.fn(),
  recordTransfer: (...args: unknown[]) => mockRecordTransfer(...args),
  recordFeeTransfer: (...args: unknown[]) => mockRecordFeeTransfer(...args),
  recordRefund: vi.fn(),
  approveTransaction: (...args: unknown[]) => mockApproveTransaction(...args),
  rejectTransaction: (...args: unknown[]) => mockRejectTransaction(...args),
  reverseTransaction: (...args: unknown[]) => mockReverseTransaction(...args),
}));

// ── Registry types (646E): the page reads labels, pills and reversal rules from
// GET /api/trust-transaction-types — a subset of the real registry, real values.
const { mockFetchTrustTransactionTypes, mockFetchTrustAccounts } = vi.hoisted(() => ({
  mockFetchTrustTransactionTypes: vi.fn(),
  mockFetchTrustAccounts: vi.fn(),
}));

function typeInfo(
  code: string,
  label: string,
  direction: "IN" | "OUT" | "NEUTRAL",
  reversal: string,
  ledger: string | null = "CLIENT"
) {
  return {
    code,
    label,
    direction,
    ledger,
    origin: ledger === null ? null : "FIRM",
    requiresApproval: false,
    reversal,
    clientVisible: true,
  };
}

const REGISTRY_TYPES = [
  typeInfo("DEPOSIT", "Deposit", "IN", "NEEDS_APPROVAL"),
  typeInfo("PAYMENT", "Payment", "OUT", "IMMEDIATE"),
  typeInfo("FEE_TRANSFER", "Fee transfer", "OUT", "IMMEDIATE"),
  typeInfo("REFUND", "Refund", "OUT", "IMMEDIATE"),
  typeInfo("TRANSFER_OUT", "Transfer out", "OUT", "PAIR"),
  typeInfo("TRANSFER_IN", "Transfer in", "IN", "VIA_PAIR"),
  typeInfo("REVERSAL", "Reversal", "NEUTRAL", "NONE", null),
  {
    ...typeInfo(
      "FUND_INTEREST",
      "Interest received for the Legal Practitioners' Fidelity Fund",
      "IN",
      "IMMEDIATE",
      "NON_CLIENT"
    ),
    origin: "BANK",
    clientVisible: false,
  },
];

// ── Mock parent trust actions ────────────────────────────────────
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustTransactionTypes: (...args: unknown[]) => mockFetchTrustTransactionTypes(...args),
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
}));

const MAIN_TRUST_ACCOUNT = {
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
  lockedThrough: null,
};

// OBS-1001: dependent matter combobox calls fetchCustomerProjects via SWR.
// Stub the server action so happy-dom never loads `server-only`.
vi.mock("@/app/(app)/org/[slug]/clients/[id]/actions", () => ({
  fetchCustomerProjects: vi.fn().mockResolvedValue([]),
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
    refresh: mockRouterRefresh,
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
import { RecordPaymentDialog } from "@/components/trust/RecordPaymentDialog";
import { RecordRefundDialog } from "@/components/trust/RecordRefundDialog";
import { RecordTransferDialog } from "@/components/trust/RecordTransferDialog";
import { RecordFeeTransferDialog } from "@/components/trust/RecordFeeTransferDialog";
import { todayInSA } from "@b2mash/shared/date";
import { ApprovalBadge } from "@/components/trust/approval-badge";
import { ReversalButton } from "@/components/trust/reversal-button";

// ── Helpers ──────────────────────────────────────────────────────

function makeTx(overrides: Record<string, unknown> = {}) {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    // 646E: label and direction arrive on the DTO — the page never maps a type code itself.
    typeLabel: "Deposit",
    direction: "IN",
    ledger: "CLIENT",
    amount: 50000,
    clientId: "cust-1",
    matterId: null,
    counterpartyClientId: null,
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
    reversalPending: false,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-04-01T09:00:00Z",
    creditorAccountId: null,
    investmentId: null,
    postedAt: "2026-04-01T10:00:00Z",
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
    mockFetchTrustTransactionTypes.mockResolvedValue(REGISTRY_TYPES);
    mockFetchTrustAccounts.mockResolvedValue([MAIN_TRUST_ACCOUNT]);
  });

  // 648A review (CodeRabbit): an investment account marked primary is never the page's account —
  // placements are requested and approved on the banking account the money leaves
  it("lists the trust banking account's transactions, never an investment account's", async () => {
    mockFetchTrustAccounts.mockResolvedValue([
      {
        ...MAIN_TRUST_ACCOUNT,
        id: "acc-investment",
        accountName: "Call Account",
        accountType: "INVESTMENT",
        isPrimary: true,
      },
      { ...MAIN_TRUST_ACCOUNT, isPrimary: false },
    ]);
    mockFetchTransactions.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    expect(mockFetchTransactions).toHaveBeenCalledWith("acc-1", expect.anything());
    expect(mockFetchTransactions).not.toHaveBeenCalledWith("acc-investment", expect.anything());
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

  // Phase 92 647D: a pending allocation of an unidentified deposit sits in the approvals list with
  // its label and a line saying where the money goes; no other row prints its description here.
  it("shows a pending unidentified-deposit allocation with its label, destination and Approve/Reject", async () => {
    mockFetchTrustTransactionTypes.mockResolvedValue([
      ...REGISTRY_TYPES,
      {
        ...typeInfo(
          "UNIDENT_ALLOC_OUT",
          "Unidentified deposit allocated",
          "NEUTRAL",
          "PAIR",
          "NON_CLIENT"
        ),
        requiresApproval: true,
        clientVisible: false,
      },
    ]);
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-alloc",
          transactionType: "UNIDENT_ALLOC_OUT",
          typeLabel: "Unidentified deposit allocated",
          direction: "NEUTRAL",
          ledger: "NON_CLIENT",
          amount: 300,
          clientId: null,
          creditorAccountId: "ca-unidentified",
          reference: "UID-1A2B3C4D",
          description:
            "To Sipho Dlamini — Estate late M Dlamini: deposit received 3 November 2026 — CREDIT TRANSFER J SMITH",
          status: "AWAITING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
          postedAt: null,
        }),
        makeTx({ id: "tx-dep", reference: "DEP-001" }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const row = screen.getByTestId("transaction-row-tx-alloc");
    expect(within(row).getByText("Unidentified deposit allocated")).toBeInTheDocument();
    expect(within(row).getByTestId("transaction-description-tx-alloc")).toHaveTextContent(
      "To Sipho Dlamini — Estate late M Dlamini: deposit received 3 November 2026"
    );
    expect(within(row).getByTestId("approve-button")).toBeInTheDocument();
    expect(within(row).getByTestId("reject-button")).toBeInTheDocument();
    expect(row).not.toHaveTextContent("UNIDENT_ALLOC_OUT");
    // A deposit's description stays off this list.
    expect(screen.queryByTestId("transaction-description-tx-dep")).not.toBeInTheDocument();
    expect(screen.queryByText("Initial deposit")).not.toBeInTheDocument();
  });

  // Phase 92 648A: a pending investment placement sits in the approvals list with its registry
  // label and where the money goes (the investment account).
  it("shows a pending section 86(4) placement with its label, destination and Approve/Reject", async () => {
    mockFetchTrustTransactionTypes.mockResolvedValue([
      ...REGISTRY_TYPES,
      {
        ...typeInfo("INV_PLACE", "Placed in section 86(4) investment", "OUT", "NONE", "CLIENT"),
        requiresApproval: true,
      },
    ]);
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-place",
          transactionType: "INV_PLACE",
          typeLabel: "Placed in section 86(4) investment",
          direction: "OUT",
          ledger: "CLIENT",
          amount: 200000,
          reference: "INV-1A2B3C4D",
          description:
            "Placed in section 86(4) investment at Investec (Section 86(4) Fixed Deposit)",
          status: "AWAITING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
          postedAt: null,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const row = screen.getByTestId("transaction-row-tx-place");
    expect(within(row).getByText("Placed in section 86(4) investment")).toBeInTheDocument();
    expect(within(row).getByTestId("transaction-description-tx-place")).toHaveTextContent(
      "at Investec (Section 86(4) Fixed Deposit)"
    );
    expect(within(row).getByTestId("approve-button")).toBeInTheDocument();
    expect(within(row).getByTestId("reject-button")).toBeInTheDocument();
    expect(row).not.toHaveTextContent("INV_PLACE");
  });

  // 647D OBS h: at 1280 px the nowrap type badge of "Unidentified deposit paid to the Fund" widened
  // the table past its scroll wrapper and pushed Approve/Reject off-screen; type badges now wrap.
  it("lets a long type label wrap so the Actions column stays on-screen (647D OBS h)", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-fund",
          transactionType: "UNIDENT_FUND_PAY",
          typeLabel: "Unidentified deposit paid to the Fund",
          direction: "NEUTRAL",
          ledger: "NON_CLIENT",
          clientId: null,
          status: "AWAITING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
          postedAt: null,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const badge = screen.getByTestId("transaction-type-tx-fund");
    expect(badge).toHaveTextContent("Unidentified deposit paid to the Fund");
    expect(badge).toHaveClass("shrink", "whitespace-normal");
    expect(badge).not.toHaveClass("whitespace-nowrap");
    expect(badge).not.toHaveClass("shrink-0");
  });

  // G88-F23 (Phase 89 630C): REFUND moves money out of trust — outflow colour + arrow.
  it("renders REFUND as an outflow (no green, up-right arrow)", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-ref",
          reference: "REF-001",
          transactionType: "REFUND",
          typeLabel: "Refund",
          direction: "OUT",
          amount: 1000,
        }),
        makeTx({ id: "tx-dep", reference: "DEP-001", transactionType: "DEPOSIT" }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const refundAmount = within(screen.getByTestId("transaction-row-tx-ref"))
      .getAllByRole("cell")[3]
      .querySelector("span") as HTMLElement;
    expect(refundAmount.className).not.toContain("text-green-600");
    expect(refundAmount.querySelector(".lucide-arrow-up-right")).not.toBeNull();
    expect(refundAmount.querySelector(".lucide-arrow-down-left")).toBeNull();

    const depositAmount = within(screen.getByTestId("transaction-row-tx-dep"))
      .getAllByRole("cell")[3]
      .querySelector("span") as HTMLElement;
    expect(depositAmount.className).toContain("text-green-600");
  });

  // 646E F8: in/out styling comes from the row's `direction` — a REVERSAL of a PAYMENT puts money
  // back (IN), whatever its type code; a NEUTRAL row (orphan reversal) draws no arrow.
  it("styles a payment's reversal as an inflow and a neutral row without an arrow (F8)", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-rev-pay",
          reference: "PAY-001",
          transactionType: "REVERSAL",
          typeLabel: "Reversal",
          direction: "IN",
          status: "RECORDED",
          reversalOf: "tx-pay",
        }),
        makeTx({
          id: "tx-orphan",
          reference: "REV-002",
          transactionType: "REVERSAL",
          typeLabel: "Reversal",
          direction: "NEUTRAL",
          status: "RECORDED",
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const reversalAmount = within(screen.getByTestId("transaction-row-tx-rev-pay"))
      .getAllByRole("cell")[3]
      .querySelector("span") as HTMLElement;
    expect(reversalAmount.className).toContain("text-green-600");
    expect(reversalAmount.querySelector(".lucide-arrow-down-left")).not.toBeNull();
    expect(reversalAmount.querySelector(".lucide-arrow-up-right")).toBeNull();

    const neutralAmount = within(screen.getByTestId("transaction-row-tx-orphan"))
      .getAllByRole("cell")[3]
      .querySelector("span") as HTMLElement;
    expect(neutralAmount.className).not.toContain("text-green-600");
    expect(neutralAmount.querySelector("svg")).toBeNull();
  });

  // 646E: the type badge prints the DTO's typeLabel verbatim; the pills list the registry types.
  it("labels rows from typeLabel and lists the registry types as filter pills", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-fee",
          reference: "FEE-001",
          transactionType: "FEE_TRANSFER",
          typeLabel: "Fee transfer",
          direction: "OUT",
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    const row = screen.getByTestId("transaction-row-tx-fee");
    expect(within(row).getByText("Fee transfer")).toBeInTheDocument();
    expect(within(row).queryByText("FEE_TRANSFER")).not.toBeInTheDocument();

    const pills = within(screen.getByTestId("type-filters")).getAllByRole("link");
    expect(pills.map((p) => p.textContent)).toEqual(["All", ...REGISTRY_TYPES.map((t) => t.label)]);
    expect(pills[1]).toHaveAttribute("href", expect.stringContaining("type=DEPOSIT"));
    expect(screen.queryByText("Interest Credit")).not.toBeInTheDocument();
    expect(screen.queryByText("Interest LPFF")).not.toBeInTheDocument();
  });

  // 646E: a failed type fetch leaves the page usable — rows still render, only the pills go.
  it("renders rows when the type registry cannot be fetched", async () => {
    mockFetchTrustTransactionTypes.mockRejectedValue(new Error("boom"));
    mockFetchTransactions.mockResolvedValue({
      content: [makeTx({ id: "tx-1", reference: "DEP-001" })],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("type-filters"))
        .getAllByRole("link")
        .map((p) => p.textContent)
    ).toEqual(["All"]);
  });

  // Test 2: Deposit dialog submits correctly with the locked-picker pattern.
  // OBS-1001 replaced the raw <Input placeholder="Client UUID"/> field with a
  // Popover+Command combobox; we drive the form via `defaultCustomerId` so the
  // submit path is exercised without scripting the popover open/select.
  it("submits deposit dialog with valid form data", async () => {
    mockRecordDeposit.mockResolvedValue({ success: true });

    render(
      <RecordDepositDialog
        accountId="acc-1"
        lockedThrough={null}
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
        customers={[
          {
            id: "550e8400-e29b-41d4-a716-446655440000",
            name: "Sipho Dlamini",
            email: "sipho@example.com",
          },
        ]}
        defaultCustomerId="550e8400-e29b-41d4-a716-446655440000"
        defaultProjectId="770e8400-e29b-41d4-a716-446655440002"
      />
    );

    expect(
      screen.getByText("Record a trust deposit from a client into the trust account.")
    ).toBeInTheDocument();

    // Customer trigger should be disabled (locked) and show the customer name,
    // not a UUID.
    const customerTrigger = screen.getByTestId("trust-deposit-customer-trigger");
    expect(customerTrigger).toBeDisabled();
    expect(customerTrigger).toHaveTextContent("Sipho Dlamini");

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
        expect.objectContaining({
          customerId: "550e8400-e29b-41d4-a716-446655440000",
          projectId: "770e8400-e29b-41d4-a716-446655440002",
          reference: "DEP/2026/TEST",
        }),
        expect.any(String)
      );
    });
  });

  // G89-F28 (D36): a client deposit without a matter never reached the portal, so the matter is
  // required — the dialog shows a field error under the Matter picker and does not submit.
  it("refuses a deposit without a matter with a field error (G89-F28)", async () => {
    mockRecordDeposit.mockClear();
    render(
      <RecordDepositDialog
        accountId="acc-1"
        lockedThrough={null}
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
        customers={[
          {
            id: "550e8400-e29b-41d4-a716-446655440000",
            name: "Sipho Dlamini",
            email: "sipho@example.com",
          },
        ]}
        defaultCustomerId="550e8400-e29b-41d4-a716-446655440000"
      />
    );

    expect(screen.getByText("Matter")).toBeInTheDocument();
    expect(screen.queryByText("Matter (Optional)")).toBeNull();

    const amountInput = screen.getByPlaceholderText("0.00");
    fireEvent.change(amountInput, { target: { value: "15000" } });
    fireEvent.change(screen.getByPlaceholderText("e.g. DEP/2026/001"), {
      target: { value: "DEP/2026/NO-MATTER" },
    });
    fireEvent.submit(amountInput.closest("form")!);

    expect(
      await screen.findByText("A matter is required for client trust transactions")
    ).toBeInTheDocument();
    expect(mockRecordDeposit).not.toHaveBeenCalled();
  });

  // PR #1757 review (D39): a transfer names the target client's matter as well as the source's;
  // without it the dialog shows a field error under Target Matter and does not submit.
  it("refuses a transfer without a target matter with a field error (D39)", async () => {
    mockRecordTransfer.mockClear();
    render(
      <RecordTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough={null}
      />
    );

    fireEvent.change(screen.getByPlaceholderText("Source client UUID"), {
      target: { value: "550e8400-e29b-41d4-a716-446655440000" },
    });
    fireEvent.change(screen.getByPlaceholderText("Target client UUID"), {
      target: { value: "660e8400-e29b-41d4-a716-446655440001" },
    });
    fireEvent.change(screen.getByPlaceholderText("Source client's matter UUID"), {
      target: { value: "770e8400-e29b-41d4-a716-446655440002" },
    });
    const amountInput = screen.getByPlaceholderText("0.00");
    fireEvent.change(amountInput, { target: { value: "100" } });
    fireEvent.change(screen.getByPlaceholderText("e.g. TRF/2026/001"), {
      target: { value: "TRF/2026/NO-TARGET-MATTER" },
    });
    fireEvent.submit(amountInput.closest("form")!);

    expect(screen.getByText("Target Matter")).toBeInTheDocument();
    expect(
      await screen.findByText("A matter is required for client trust transactions")
    ).toBeInTheDocument();
    expect(mockRecordTransfer).not.toHaveBeenCalled();
  });

  // PR #1757 review round 2: the transfer dialog grew to nine fields, so its content scrolls at
  // short viewport heights like PlaceInvestmentDialog and the repo's other tall dialogs.
  it("transfer dialog scrolls at short viewport heights", () => {
    render(
      <RecordTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough={null}
      />
    );

    // DialogContent puts its className on the panel inside the positioned dialog element
    const panel = screen.getByRole("dialog").firstElementChild as HTMLElement;
    expect(panel.className).toContain("max-h-[90vh]");
    expect(panel.className).toContain("overflow-y-auto");
    expect(panel.className).toContain("sm:max-w-lg");
  });

  it("submits a transfer with both matters (D39)", async () => {
    mockRecordTransfer.mockResolvedValue({ success: true });
    render(
      <RecordTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough={null}
      />
    );

    fireEvent.change(screen.getByPlaceholderText("Source client UUID"), {
      target: { value: "550e8400-e29b-41d4-a716-446655440000" },
    });
    fireEvent.change(screen.getByPlaceholderText("Target client UUID"), {
      target: { value: "660e8400-e29b-41d4-a716-446655440001" },
    });
    fireEvent.change(screen.getByPlaceholderText("Source client's matter UUID"), {
      target: { value: "770e8400-e29b-41d4-a716-446655440002" },
    });
    fireEvent.change(screen.getByPlaceholderText("Target client's matter UUID"), {
      target: { value: "880e8400-e29b-41d4-a716-446655440003" },
    });
    const amountInput = screen.getByPlaceholderText("0.00");
    fireEvent.change(amountInput, { target: { value: "100" } });
    fireEvent.change(screen.getByPlaceholderText("e.g. TRF/2026/001"), {
      target: { value: "TRF/2026/BOTH" },
    });
    fireEvent.submit(amountInput.closest("form")!);

    await waitFor(() => {
      expect(mockRecordTransfer).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({
          projectId: "770e8400-e29b-41d4-a716-446655440002",
          targetProjectId: "880e8400-e29b-41d4-a716-446655440003",
        }),
        expect.any(String)
      );
    });
  });

  // OBS-1001: when no `defaultCustomerId` is supplied the customer trigger
  // is enabled, shows the placeholder text, and the legacy raw UUID inputs
  // are gone. Driving the Radix Popover open is unreliable in happy-dom, so
  // the deeper select-and-fill flow is exercised by the locked-picker
  // submission test above.
  it("renders the customer combobox (not raw UUID inputs) (OBS-1001)", () => {
    render(
      <RecordDepositDialog
        accountId="acc-1"
        lockedThrough={null}
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
        customers={[
          {
            id: "550e8400-e29b-41d4-a716-446655440000",
            name: "Sipho Dlamini",
            email: "sipho@example.com",
          },
          {
            id: "660e8400-e29b-41d4-a716-446655440001",
            name: "Thandi Khumalo",
            email: "thandi@example.com",
          },
        ]}
      />
    );

    const customerTrigger = screen.getByTestId("trust-deposit-customer-trigger");
    expect(customerTrigger).not.toBeDisabled();
    expect(customerTrigger).toHaveTextContent("Select a client...");

    // The old raw UUID inputs must be gone.
    expect(screen.queryByPlaceholderText("Client UUID")).toBeNull();
    expect(screen.queryByPlaceholderText("Matter UUID (optional)")).toBeNull();
  });

  // OBS-1001: matter trigger is disabled with helper text until a client is
  // selected.
  it("disables the matter trigger until a client is selected (OBS-1001)", () => {
    render(
      <RecordDepositDialog
        accountId="acc-1"
        lockedThrough={null}
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
        customers={[
          {
            id: "550e8400-e29b-41d4-a716-446655440000",
            name: "Sipho Dlamini",
            email: "sipho@example.com",
          },
        ]}
      />
    );

    const matterTrigger = screen.getByTestId("trust-deposit-matter-trigger");
    expect(matterTrigger).toBeDisabled();
    expect(matterTrigger).toHaveTextContent("Select a client first");
  });

  // Test 3: Approval badge shows buttons for AWAITING_APPROVAL
  it("shows Approve and Reject buttons for AWAITING_APPROVAL transactions", () => {
    render(<ApprovalBadge transactionId="tx-pending" status="AWAITING_APPROVAL" />);

    expect(screen.getByTestId("approve-button")).toBeInTheDocument();
    expect(screen.getByTestId("reject-button")).toBeInTheDocument();
  });

  // ── LZKC-016: dual-approval first Approve must not register silently ──

  it("shows a 1-of-2 toast and refreshes after a first dual-mode approve (LZKC-016)", async () => {
    // Backend keeps status AWAITING_APPROVAL after the first dual-mode
    // approval and returns the updated transaction; the badge must surface it.
    mockApproveTransaction.mockResolvedValue({
      success: true,
      transaction: makeTx({
        id: "tx-pending",
        status: "AWAITING_APPROVAL",
        approvedBy: "member-1",
        approvedAt: "2026-06-01T10:00:00Z",
      }),
    });
    const user = userEvent.setup();

    render(<ApprovalBadge transactionId="tx-pending" status="AWAITING_APPROVAL" />);
    await user.click(screen.getByTestId("approve-button"));

    await waitFor(() => {
      expect(mockToastSuccess).toHaveBeenCalledWith(expect.stringMatching(/1 of 2/i));
    });
    expect(mockRouterRefresh).toHaveBeenCalled();
  });

  it("shows an approved toast when the approve completes the transaction (LZKC-016)", async () => {
    mockApproveTransaction.mockResolvedValue({
      success: true,
      transaction: makeTx({
        id: "tx-pending",
        status: "APPROVED",
        approvedBy: "member-1",
        secondApprovedBy: "member-2",
      }),
    });
    const user = userEvent.setup();

    render(<ApprovalBadge transactionId="tx-pending" status="AWAITING_APPROVAL" />);
    await user.click(screen.getByTestId("approve-button"));

    await waitFor(() => {
      expect(mockToastSuccess).toHaveBeenCalledWith(expect.stringMatching(/approved/i));
    });
    expect(mockRouterRefresh).toHaveBeenCalled();
  });

  it("disables Approve for the member who already gave the first approval (LZKC-016)", () => {
    render(
      <ApprovalBadge
        transactionId="tx-pending"
        status="AWAITING_APPROVAL"
        firstApprovedBy="member-1"
        currentMemberId="member-1"
      />
    );

    const approveButton = screen.getByTestId("approve-button");
    expect(approveButton).toBeDisabled();
    expect(approveButton).toHaveTextContent(/approved by you/i);
  });

  it("keeps Approve enabled for a different second approver (LZKC-016)", () => {
    render(
      <ApprovalBadge
        transactionId="tx-pending"
        status="AWAITING_APPROVAL"
        firstApprovedBy="member-1"
        currentMemberId="member-2"
      />
    );

    expect(screen.getByTestId("approve-button")).not.toBeDisabled();
  });

  it("renders a 1-of-2-approvals indicator on partially approved rows (LZKC-016)", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-partial",
          reference: "PAY/2026/001",
          transactionType: "PAYMENT",
          amount: 70000,
          status: "AWAITING_APPROVAL",
          approvedBy: "member-1",
          approvedAt: "2026-06-01T10:00:00Z",
        }),
        makeTx({
          id: "tx-fresh",
          reference: "PAY/2026/002",
          transactionType: "PAYMENT",
          amount: 70000,
          status: "AWAITING_APPROVAL",
          approvedBy: null,
          approvedAt: null,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    // Partially approved row shows progress; fresh row does not.
    expect(screen.getByTestId("approval-progress-tx-partial")).toHaveTextContent(
      "1 of 2 approvals"
    );
    expect(screen.queryByTestId("approval-progress-tx-fresh")).toBeNull();
  });

  // Test 4: Reject dialog requires reason
  it("requires a reason when rejecting a transaction", async () => {
    const user = userEvent.setup();

    render(<ApprovalBadge transactionId="tx-pending" status="AWAITING_APPROVAL" />);

    // Click reject to open the dialog
    await user.click(screen.getByTestId("reject-button"));

    await waitFor(() => {
      expect(screen.getByText("Reject Transaction", { selector: "h2" })).toBeInTheDocument();
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
      expect(screen.getByText("Reverse Transaction", { selector: "h2" })).toBeInTheDocument();
    });

    // Fill in reason
    const reasonInput = screen.getByTestId("reversal-reason-input");
    await user.type(reasonInput, "Duplicate entry");

    // Submit
    await user.click(screen.getByTestId("confirm-reverse-button"));

    await waitFor(() => {
      expect(mockReverseTransaction).toHaveBeenCalledWith(
        "tx-approved",
        {
          reason: "Duplicate entry",
          reversalDate: "",
        },
        expect.any(String)
      );
    });
  });

  // Phase 92 649B: optional reversal date — the date the bank shows the reversal.
  it("submits reversal with a reversal date", async () => {
    mockReverseTransaction.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<ReversalButton transactionId="tx-approved" transactionDate="2026-03-01" />);

    await user.click(screen.getByTestId("reverse-button"));
    await waitFor(() => {
      expect(screen.getByText("Reverse Transaction", { selector: "h2" })).toBeInTheDocument();
    });

    expect(screen.getByText("Reversal date (optional)")).toBeInTheDocument();
    const dateInput = screen.getByTestId("reversal-date-input");
    expect(dateInput).toHaveAttribute("min", "2026-03-01");
    fireEvent.change(dateInput, { target: { value: "2026-04-15" } });
    await user.type(screen.getByTestId("reversal-reason-input"), "Bank returned the payment");

    await user.click(screen.getByTestId("confirm-reverse-button"));

    await waitFor(() => {
      expect(mockReverseTransaction).toHaveBeenCalledWith(
        "tx-approved",
        {
          reason: "Bank returned the payment",
          reversalDate: "2026-04-15",
        },
        expect.any(String)
      );
    });
  });

  it("explains that a deposit reversal needs another member's approval", async () => {
    const user = userEvent.setup();

    render(<ReversalButton transactionId="tx-deposit" needsApproval />);

    await user.click(screen.getByTestId("reverse-button"));

    await waitFor(() => {
      expect(
        screen.getByText(/must be approved by another member before it takes effect/)
      ).toBeInTheDocument();
    });
    expect(screen.queryByText(/NEEDS_APPROVAL/)).not.toBeInTheDocument();
  });

  // Phase 92 649B (ruling #1): Reverse is offered for any posted row per its type's rule.
  it("offers Reverse for posted rows per the reversal rule", async () => {
    mockFetchTransactions.mockResolvedValue({
      content: [
        makeTx({ id: "tx-dep", transactionType: "DEPOSIT", status: "RECORDED" }),
        makeTx({ id: "tx-pay", transactionType: "PAYMENT", status: "APPROVED" }),
        makeTx({ id: "tx-out", transactionType: "TRANSFER_OUT", status: "RECORDED" }),
        makeTx({ id: "tx-in", transactionType: "TRANSFER_IN", status: "RECORDED" }),
        makeTx({ id: "tx-rev", transactionType: "REVERSAL", status: "RECORDED" }),
        makeTx({
          id: "tx-reversed",
          transactionType: "PAYMENT",
          status: "REVERSED",
          reversedById: "tx-rev",
        }),
        makeTx({ id: "tx-await", transactionType: "PAYMENT", status: "AWAITING_APPROVAL" }),
        makeTx({ id: "tx-rej", transactionType: "REFUND", status: "REJECTED" }),
        // 646E R1: a deposit whose contra awaits approval — the backend refuses a second reversal
        makeTx({
          id: "tx-pending",
          transactionType: "DEPOSIT",
          status: "RECORDED",
          reversalPending: true,
        }),
        // A type the registry list does not carry: no rule, so no Reverse
        makeTx({ id: "tx-unknown", transactionType: "SOMETHING_NEW", status: "RECORDED" }),
      ],
      totalElements: 10,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    for (const id of ["tx-dep", "tx-pay", "tx-out"]) {
      expect(screen.getByTestId(`reversal-action-${id}`)).toBeInTheDocument();
    }
    for (const id of [
      "tx-in",
      "tx-rev",
      "tx-reversed",
      "tx-await",
      "tx-rej",
      "tx-pending",
      "tx-unknown",
    ]) {
      expect(screen.queryByTestId(`reversal-action-${id}`)).not.toBeInTheDocument();
    }
  });

  // Phase 92 646D (ruling #6): optional fee-transfer date — the date the bank shows the transfer.
  it("submits a back-dated fee transfer and refuses a future date", async () => {
    mockRecordFeeTransfer.mockResolvedValue({ success: true });
    render(
      <RecordFeeTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough={null}
      />
    );

    expect(screen.getByText("Fee transfer date (optional)")).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText("Client UUID"), {
      target: { value: "550e8400-e29b-41d4-a716-446655440000" },
    });
    fireEvent.change(screen.getByPlaceholderText("Fee note UUID"), {
      target: { value: "660e8400-e29b-41d4-a716-446655440001" },
    });
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "1150" } });
    fireEvent.change(screen.getByPlaceholderText("e.g. FEE/2026/001"), {
      target: { value: "FEE/2026/BACK" },
    });
    const dateInput = screen.getByTestId("fee-transfer-date-input");
    expect(dateInput).toHaveAttribute("max", todayInSA());

    fireEvent.change(dateInput, { target: { value: "2999-01-01" } });
    fireEvent.submit(dateInput.closest("form")!);
    expect(
      await screen.findByText("The fee transfer date cannot be in the future")
    ).toBeInTheDocument();
    expect(mockRecordFeeTransfer).not.toHaveBeenCalled();

    fireEvent.change(dateInput, { target: { value: "2026-03-15" } });
    fireEvent.submit(dateInput.closest("form")!);
    await waitFor(() =>
      expect(mockRecordFeeTransfer).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({ transactionDate: "2026-03-15", reference: "FEE/2026/BACK" }),
        expect.any(String)
      )
    );
  });
  // Phase 92 650D (§16.3.10, ADR-382): a date on or before the account's latest locked list
  // period end warns — never refuses.
  const LOCKED =
    "This date falls in a month whose list is locked. The entry will be recorded and shown as a later adjustment.";
  const SIPHO = {
    id: "550e8400-e29b-41d4-a716-446655440000",
    name: "Sipho Dlamini",
    email: "sipho@example.com",
  };

  it("warns on a date on or before the locked month end and still records the deposit (650D)", async () => {
    mockRecordDeposit.mockResolvedValue({ success: true });
    render(
      <RecordDepositDialog
        accountId="acc-1"
        slug="acme"
        open={true}
        onOpenChange={vi.fn()}
        customers={[SIPHO]}
        defaultCustomerId={SIPHO.id}
        defaultProjectId="770e8400-e29b-41d4-a716-446655440002"
        lockedThrough="2026-03-31"
      />
    );
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "750" } });
    fireEvent.change(screen.getByPlaceholderText("e.g. DEP/2026/001"), {
      target: { value: "DEP/2026/BACK" },
    });
    const date = screen.getByLabelText("Transaction Date");

    fireEvent.change(date, { target: { value: "2026-04-01" } });
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();

    // The boundary: equal to lockedThrough is "on or before"
    fireEvent.change(date, { target: { value: "2026-03-31" } });
    const warning = screen.getByTestId("locked-month-warning");
    expect(warning).toHaveTextContent(LOCKED);
    expect(warning).toHaveAttribute("role", "status");
    expect(screen.getByRole("status")).toBe(warning);
    const form = date.closest("form")!;
    expect(form.querySelector('button[type="submit"]')).not.toBeDisabled();

    fireEvent.change(date, { target: { value: "2026-03-15" } });
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    fireEvent.submit(form);
    await waitFor(() =>
      expect(mockRecordDeposit).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({ transactionDate: "2026-03-15", reference: "DEP/2026/BACK" }),
        expect.any(String)
      )
    );
  });

  it("shows the locked-month warning in all five posting dialogs only on or before lockedThrough (650D)", () => {
    mockRecordDeposit.mockClear();
    mockRecordTransfer.mockClear();
    mockRecordFeeTransfer.mockClear();
    const dialogs: Array<[string, () => ReturnType<typeof render>]> = [
      [
        "deposit",
        () =>
          render(
            <RecordDepositDialog
              accountId="acc-1"
              slug="acme"
              open={true}
              onOpenChange={vi.fn()}
              customers={[SIPHO]}
              defaultCustomerId={SIPHO.id}
              lockedThrough="2026-03-31"
            />
          ),
      ],
      [
        "payment",
        () =>
          render(
            <RecordPaymentDialog
              accountId="acc-1"
              open={true}
              onOpenChange={vi.fn()}
              customers={[SIPHO]}
              defaultCustomerId={SIPHO.id}
              lockedThrough="2026-03-31"
            />
          ),
      ],
      [
        "refund",
        () =>
          render(
            <RecordRefundDialog
              accountId="acc-1"
              open={true}
              onOpenChange={vi.fn()}
              customers={[SIPHO]}
              defaultCustomerId={SIPHO.id}
              lockedThrough="2026-03-31"
            />
          ),
      ],
      [
        "transfer",
        () =>
          render(
            <RecordTransferDialog
              accountId="acc-1"
              open={true}
              onOpenChange={vi.fn()}
              lockedThrough="2026-03-31"
            />
          ),
      ],
    ];
    for (const [name, renderDialog] of dialogs) {
      const { unmount } = renderDialog();
      const date = screen.getByLabelText("Transaction Date");
      fireEvent.change(date, { target: { value: "2026-03-20" } });
      expect(screen.getByTestId("locked-month-warning"), name).toHaveTextContent(LOCKED);
      fireEvent.change(date, { target: { value: "2026-04-15" } });
      expect(screen.queryByTestId("locked-month-warning"), name).not.toBeInTheDocument();
      unmount();
    }

    // Fee transfer: a blank date means today, which is after the lock — no warning
    const fee = render(
      <RecordFeeTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough="2026-03-31"
      />
    );
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();
    const feeDate = screen.getByTestId("fee-transfer-date-input");
    fireEvent.change(feeDate, { target: { value: "2026-03-20" } });
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    fireEvent.change(feeDate, { target: { value: "2026-04-15" } });
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();
    fee.unmount();

    // null: nothing is locked, so even an old date shows no warning
    render(
      <RecordTransferDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        lockedThrough={null}
      />
    );
    fireEvent.change(screen.getByLabelText("Transaction Date"), {
      target: { value: "2020-01-31" },
    });
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();

    expect(mockRecordDeposit).not.toHaveBeenCalled();
    expect(mockRecordTransfer).not.toHaveBeenCalled();
    expect(mockRecordFeeTransfer).not.toHaveBeenCalled();
  });

  // PR #1807 review: the page reads the account's lockedThrough and TransactionActions hands it to
  // the dialogs — a hard-coded null at either mount site fails here.
  it("the transactions page passes the account's lockedThrough into its posting dialogs (650D)", async () => {
    const user = userEvent.setup();
    mockFetchTrustAccounts.mockResolvedValue([
      { ...MAIN_TRUST_ACCOUNT, lockedThrough: "2026-03-31" },
    ]);
    mockFetchTransactions.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderTransactionsPage();

    await user.click(screen.getByTestId("record-transaction-button"));
    await user.click(await screen.findByRole("menuitem", { name: "Record Deposit" }));
    const depositDate = await screen.findByLabelText("Transaction Date");
    fireEvent.change(depositDate, { target: { value: "2026-04-01" } });
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();
    fireEvent.change(depositDate, { target: { value: "2026-03-31" } });
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByLabelText("Transaction Date")).toBeNull());

    await user.click(screen.getByTestId("record-transaction-button"));
    await user.click(await screen.findByRole("menuitem", { name: "Record Transfer" }));
    fireEvent.change(await screen.findByLabelText("Transaction Date"), {
      target: { value: "2026-03-15" },
    });
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    expect(mockRecordDeposit).not.toHaveBeenCalled();
    expect(mockRecordTransfer).not.toHaveBeenCalled();
  });
});
