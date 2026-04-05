import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock client ledger actions ───────────────────────────────────
const mockFetchClientLedgers = vi.fn();
const mockFetchClientLedger = vi.fn();
const mockFetchClientHistory = vi.fn();
const mockFetchClientStatement = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions",
  () => ({
    fetchClientLedgers: (...args: unknown[]) =>
      mockFetchClientLedgers(...args),
    fetchClientLedger: (...args: unknown[]) =>
      mockFetchClientLedger(...args),
    fetchClientHistory: (...args: unknown[]) =>
      mockFetchClientHistory(...args),
    fetchClientStatement: (...args: unknown[]) =>
      mockFetchClientStatement(...args),
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
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
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
import ClientLedgersPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/page";
import ClientLedgerDetailPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page";

// ── Helpers ──────────────────────────────────────────────────────

function makeLedger(overrides: Record<string, unknown> = {}) {
  return {
    id: "ledger-1",
    trustAccountId: "acc-1",
    customerId: "cust-1",
    customerName: "Smith & Associates",
    balance: 125000,
    totalDeposits: 200000,
    totalPayments: 50000,
    totalFeeTransfers: 25000,
    totalInterestCredited: 0,
    lastTransactionDate: "2026-04-01",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

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
    description: "Client deposit",
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

async function renderLedgersPage() {
  const result = await ClientLedgersPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({}),
  });
  render(result);
}

async function renderLedgersPageWithFilter() {
  const result = await ClientLedgersPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({ nonZeroOnly: "true" }),
  });
  render(result);
}

async function renderDetailPage() {
  const result = await ClientLedgerDetailPage({
    params: Promise.resolve({ slug: "acme", customerId: "cust-1" }),
    searchParams: Promise.resolve({}),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Client Ledgers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Ledger list renders client cards
  it("renders client ledger list with balance data", async () => {
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedger({
          id: "ledger-1",
          customerId: "cust-1",
          customerName: "Smith & Associates",
          balance: 125000,
        }),
        makeLedger({
          id: "ledger-2",
          customerId: "cust-2",
          customerName: "Jones Properties",
          balance: 75000,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderLedgersPage();

    expect(screen.getByTestId("client-ledgers-table")).toBeInTheDocument();
    expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    expect(screen.getByText("Jones Properties")).toBeInTheDocument();
    expect(screen.getByText("2 clients found")).toBeInTheDocument();
  });

  // Test 2: Non-zero balance filter works
  it("passes nonZeroOnly filter to fetch action", async () => {
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedger({ balance: 125000 }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderLedgersPageWithFilter();

    expect(mockFetchClientLedgers).toHaveBeenCalledWith(
      "acc-1",
      expect.objectContaining({ nonZeroOnly: true }),
    );
  });

  // Test 3: Detail page shows transaction history
  it("renders client detail page with transaction history", async () => {
    mockFetchClientLedger.mockResolvedValue(
      makeLedger({
        customerId: "cust-1",
        customerName: "Smith & Associates",
        balance: 125000,
      }),
    );

    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-1",
          reference: "DEP-001",
          transactionType: "DEPOSIT",
          amount: 50000,
        }),
        makeTx({
          id: "tx-2",
          reference: "PAY-001",
          transactionType: "PAYMENT",
          amount: 3500,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    expect(
      screen.getByTestId("client-transactions-table"),
    ).toBeInTheDocument();
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();
    expect(screen.getByText("2 transactions found")).toBeInTheDocument();
  });

  // Test 4: Print statement button renders
  it("renders print statement button on detail page", async () => {
    mockFetchClientLedger.mockResolvedValue(
      makeLedger({
        customerId: "cust-1",
        customerName: "Smith & Associates",
      }),
    );

    mockFetchClientHistory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    const printBtn = screen.getByTestId("print-statement-btn");
    expect(printBtn).toBeInTheDocument();
    expect(printBtn).toHaveAttribute(
      "href",
      expect.stringContaining("customerId=cust-1"),
    );
  });
});
