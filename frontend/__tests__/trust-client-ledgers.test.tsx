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

// ── Mock client ledger actions ───────────────────────────────────
const mockFetchClientLedgers = vi.fn();
const mockFetchClientLedger = vi.fn();
const mockFetchClientHistory = vi.fn();
const mockGenerateStatementPdf = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions",
  () => ({
    fetchClientLedgers: (...args: unknown[]) =>
      mockFetchClientLedgers(...args),
    fetchClientLedger: (...args: unknown[]) =>
      mockFetchClientLedger(...args),
    fetchClientHistory: (...args: unknown[]) =>
      mockFetchClientHistory(...args),
    fetchClientStatement: vi.fn(),
    generateStatementPdf: (...args: unknown[]) =>
      mockGenerateStatementPdf(...args),
  }),
);

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
import ClientDetailPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page";

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

function makeLedgerCard(overrides: Record<string, unknown> = {}) {
  return {
    id: "ledger-1",
    trustAccountId: "acc-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    balance: 50000,
    totalDeposits: 100000,
    totalPayments: 40000,
    totalFeeTransfers: 10000,
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

async function renderListPage(searchParams = {}) {
  const result = await ClientLedgersPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

async function renderDetailPage(searchParams = {}) {
  const result = await ClientDetailPage({
    params: Promise.resolve({ slug: "acme", customerId: "cust-1" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Client Ledgers", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  // Test 1: Ledger list renders client cards
  it("renders client ledger list with client cards", async () => {
    setupDefaultMocks();
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedgerCard({
          customerId: "cust-1",
          customerName: "Acme Corp",
          balance: 50000,
        }),
        makeLedgerCard({
          id: "ledger-2",
          customerId: "cust-2",
          customerName: "Beta Inc",
          balance: 25000,
          totalDeposits: 50000,
          totalPayments: 20000,
          totalFeeTransfers: 5000,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderListPage();

    expect(screen.getByTestId("client-ledgers-table")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("2 clients found")).toBeInTheDocument();
  });

  // Test 2: Non-zero balance filter
  it("passes nonZeroOnly filter to fetchClientLedgers", async () => {
    setupDefaultMocks();
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedgerCard({
          customerId: "cust-1",
          customerName: "Acme Corp",
          balance: 50000,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderListPage({ nonZeroOnly: "true" });

    expect(mockFetchClientLedgers).toHaveBeenCalledWith("acc-1", {
      page: 0,
      size: 20,
      nonZeroOnly: true,
      search: undefined,
    });
    expect(screen.getByTestId("client-ledgers-table")).toBeInTheDocument();
  });

  // Test 3: Detail page shows transaction history
  it("renders detail page with transaction history", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        customerId: "cust-1",
        customerName: "Acme Corp",
        balance: 50000,
      }),
    );
    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-1",
          reference: "DEP-001",
          transactionType: "DEPOSIT",
          amount: 50000,
          status: "APPROVED",
        }),
        makeTx({
          id: "tx-2",
          reference: "PAY-001",
          transactionType: "PAYMENT",
          amount: 10000,
          status: "APPROVED",
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    expect(screen.getByTestId("client-detail-page")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByTestId("history-table")).toBeInTheDocument();
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();
    // Running Balance column header
    expect(screen.getByText("Running Balance")).toBeInTheDocument();
    // Print statement section
    expect(screen.getByTestId("print-statement")).toBeInTheDocument();
  });

  // Test 4: Print statement triggers report generation
  it("print statement button triggers PDF generation", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        customerId: "cust-1",
        customerName: "Acme Corp",
        balance: 50000,
      }),
    );
    mockFetchClientHistory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });
    // Return a minimal base64 PDF
    mockGenerateStatementPdf.mockResolvedValue("JVBER");

    await renderDetailPage();

    const dateFrom = screen.getByTestId("statement-date-from");
    const dateTo = screen.getByTestId("statement-date-to");
    const printBtn = screen.getByTestId("print-statement-btn");

    // Set dates
    fireEvent.change(dateFrom, { target: { value: "2026-01-01" } });
    fireEvent.change(dateTo, { target: { value: "2026-03-31" } });

    // Click print
    fireEvent.click(printBtn);

    // Verify generateStatementPdf was called with correct params
    expect(mockGenerateStatementPdf).toHaveBeenCalledWith(
      "acc-1",
      "cust-1",
      "2026-01-01",
      "2026-03-31",
    );
  });
});
