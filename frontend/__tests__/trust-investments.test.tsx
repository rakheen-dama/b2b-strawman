import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

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

// Mock the trust actions
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) =>
    mockFetchTrustAccounts(...args),
}));

// Mock investment actions
const mockFetchInvestments = vi.fn();
const mockFetchMaturing = vi.fn();
const mockPlaceInvestment = vi.fn();
const mockRecordInterest = vi.fn();
const mockWithdrawInvestment = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/investments/actions",
  () => ({
    fetchInvestments: (...args: unknown[]) =>
      mockFetchInvestments(...args),
    fetchMaturing: (...args: unknown[]) => mockFetchMaturing(...args),
    placeInvestment: (...args: unknown[]) => mockPlaceInvestment(...args),
    recordInterest: (...args: unknown[]) => mockRecordInterest(...args),
    withdrawInvestment: (...args: unknown[]) =>
      mockWithdrawInvestment(...args),
  }),
);

// Mock report definitions API
const mockGetReportDefinitions = vi.fn();
vi.mock("@/lib/api/reports", () => ({
  getReportDefinitions: (...args: unknown[]) =>
    mockGetReportDefinitions(...args),
}));

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
  unstable_rethrow: vi.fn(),
}));

// Mock next/cache
vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}));

// Import pages after mocks
import InvestmentsPage from "@/app/(app)/org/[slug]/trust-accounting/investments/page";
import TrustReportsPage from "@/app/(app)/org/[slug]/trust-accounting/reports/page";

// ── Helpers ────────────────────────────────────────────────────────

const defaultAccount = {
  id: "acc-1",
  accountName: "Main Trust Account",
  bankName: "FNB",
  branchCode: "250655",
  accountNumber: "1234567890",
  accountType: "GENERAL" as const,
  isPrimary: true,
  requireDualApproval: true,
  paymentApprovalThreshold: 50000,
  status: "ACTIVE" as const,
  openedDate: "2025-01-01",
  closedDate: null,
  notes: null,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

function makeInvestment(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    institution: "FNB",
    accountNumber: "INV-001",
    principal: 100000,
    interestRate: 0.085,
    depositDate: "2026-01-15",
    maturityDate: "2026-07-15",
    interestEarned: 4250,
    status: "ACTIVE",
    withdrawalDate: null,
    withdrawalAmount: null,
    depositTransactionId: "tx-1",
    withdrawalTransactionId: null,
    notes: null,
    createdAt: "2026-01-15T10:00:00Z",
    updatedAt: "2026-04-01T10:00:00Z",
    ...overrides,
  };
}

function setupInvestmentMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  });
  mockFetchMyCapabilities.mockResolvedValue({
    isAdmin: true,
    isOwner: false,
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
  });
  mockFetchTrustAccounts.mockResolvedValue([defaultAccount]);
  mockFetchInvestments.mockResolvedValue([
    makeInvestment(),
    makeInvestment({
      id: "inv-2",
      customerName: "Beta LLC",
      institution: "ABSA",
      accountNumber: "INV-002",
      principal: 50000,
      interestRate: 0.065,
      depositDate: "2026-02-01",
      maturityDate: "2026-05-01",
      interestEarned: 812.5,
      status: "MATURED",
    }),
  ]);
  mockFetchMaturing.mockResolvedValue([
    makeInvestment({
      id: "inv-3",
      customerName: "Gamma Inc",
      institution: "Nedbank",
      accountNumber: "INV-003",
      principal: 75000,
      maturityDate: "2026-04-20",
    }),
  ]);
}

async function renderInvestmentsPage() {
  const result = await InvestmentsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

async function renderReportsPage() {
  const result = await TrustReportsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

// ── Tests ──────────────────────────────────────────────────────────

describe("InvestmentsPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders investment table with correct data", async () => {
    setupInvestmentMocks();
    await renderInvestmentsPage();

    expect(screen.getByText("Investment Register")).toBeInTheDocument();
    expect(screen.getByTestId("investments-table")).toBeInTheDocument();

    // Client names
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta LLC")).toBeInTheDocument();

    // Institutions
    expect(screen.getByText("FNB")).toBeInTheDocument();
    expect(screen.getByText("ABSA")).toBeInTheDocument();

    // Statuses
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(screen.getByText("MATURED")).toBeInTheDocument();
  });

  it("shows Place Investment button when user has MANAGE_TRUST", async () => {
    setupInvestmentMocks();
    await renderInvestmentsPage();

    expect(screen.getByText("Place Investment")).toBeInTheDocument();
  });

  it("shows maturity alert when investments maturing within 30 days", async () => {
    setupInvestmentMocks();
    await renderInvestmentsPage();

    expect(screen.getByTestId("maturity-alert")).toBeInTheDocument();
    expect(
      screen.getByText(/investment.*maturing within 30 days/i),
    ).toBeInTheDocument();
  });

  it("calls notFound when trust_accounting module is disabled", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: [],
    });

    await expect(renderInvestmentsPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockNotFound).toHaveBeenCalled();
  });

  it("hides Place Investment button when user has only VIEW_TRUST capability", async () => {
    setupInvestmentMocks();
    mockFetchMyCapabilities.mockResolvedValue({
      isAdmin: false,
      isOwner: false,
      capabilities: ["VIEW_TRUST"],
    });
    await renderInvestmentsPage();

    expect(screen.queryByText("Place Investment")).not.toBeInTheDocument();
  });
});

describe("TrustReportsPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all trust report types", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: ["trust_accounting"],
    });
    mockFetchMyCapabilities.mockResolvedValue({
      isAdmin: true,
      isOwner: false,
      capabilities: ["VIEW_TRUST"],
    });
    mockGetReportDefinitions.mockResolvedValue({
      categories: [
        {
          category: "TRUST",
          label: "Trust Accounting",
          reports: [
            {
              slug: "trust-receipts-payments",
              name: "Trust Receipts & Payments",
              description:
                "Chronological journal of receipts and payments",
            },
            {
              slug: "client-trust-balances",
              name: "Client Trust Balances",
              description: "Point-in-time balances per client",
            },
            {
              slug: "client-ledger-statement",
              name: "Client Ledger Statement",
              description:
                "Per-client transaction history with running balance",
            },
            {
              slug: "trust-reconciliation",
              name: "Trust Reconciliation",
              description:
                "Three-way reconciliation: bank vs cashbook vs client ledger",
            },
            {
              slug: "investment-register",
              name: "Investment Register",
              description:
                "All trust investments with status, principal, interest",
            },
            {
              slug: "interest-allocation",
              name: "Interest Allocation",
              description:
                "Per-client interest allocation for a specific run",
            },
            {
              slug: "section-35-data-pack",
              name: "Section 35 Data Pack",
              description:
                "Composite report for Section 35 compliance",
            },
          ],
        },
      ],
    });

    await renderReportsPage();

    expect(screen.getByText("Trust Reports")).toBeInTheDocument();

    // All 7 report names
    expect(
      screen.getByText("Trust Receipts & Payments"),
    ).toBeInTheDocument();
    expect(screen.getByText("Client Trust Balances")).toBeInTheDocument();
    expect(
      screen.getByText("Client Ledger Statement"),
    ).toBeInTheDocument();
    expect(screen.getByText("Trust Reconciliation")).toBeInTheDocument();
    expect(screen.getByText("Investment Register")).toBeInTheDocument();
    expect(screen.getByText("Interest Allocation")).toBeInTheDocument();
    expect(screen.getByText("Section 35 Data Pack")).toBeInTheDocument();

    // Each should have "Run Report" link
    const runReportLinks = screen.getAllByText(/Run Report/);
    expect(runReportLinks).toHaveLength(7);
  });
});
