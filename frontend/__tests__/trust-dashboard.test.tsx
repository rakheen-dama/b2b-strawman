import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock org settings API
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

// Mock the trust actions
const mockFetchTrustAccounts = vi.fn();
const mockFetchDashboardData = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/actions",
  () => ({
    fetchTrustAccounts: (...args: unknown[]) =>
      mockFetchTrustAccounts(...args),
    fetchDashboardData: (...args: unknown[]) =>
      mockFetchDashboardData(...args),
  }),
);

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
}));

// Import page after mocks
import TrustAccountingPage from "@/app/(app)/org/[slug]/trust-accounting/page";

// ── Helpers ────────────────────────────────────────────────────────

async function renderPage() {
  const result = await TrustAccountingPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

function defaultDashboardData() {
  return {
    totalBalance: 125000.5,
    activeClients: 8,
    pendingApprovals: 3,
    lastReconciliationDate: null,
    lastReconciliationBalanced: null,
    recentTransactions: [
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
    alerts: [
      {
        type: "AGING_APPROVAL" as const,
        message: "3 transactions awaiting approval",
        severity: "warning" as const,
      },
    ],
  };
}

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
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
  mockFetchDashboardData.mockResolvedValue(defaultDashboardData());
}

// ── Tests ──────────────────────────────────────────────────────────

describe("TrustAccountingPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders summary cards with correct data", async () => {
    setupDefaultMocks();
    await renderPage();

    // Trust Balance card
    expect(screen.getByText("Trust Balance")).toBeInTheDocument();
    // en-ZA currency uses non-breaking space (U+00A0) between R and digits
    expect(screen.getByText(/R[\s\u00A0]125,000\.50/)).toBeInTheDocument();

    // Active Clients card
    expect(screen.getByText("Active Clients")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();

    // Pending Approvals card
    expect(screen.getByText("Pending Approvals")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();

    // Reconciliation card
    expect(screen.getByText("Reconciliation")).toBeInTheDocument();
    expect(screen.getByText("Not yet reconciled")).toBeInTheDocument();
  });

  it("renders recent transactions table with data", async () => {
    setupDefaultMocks();
    await renderPage();

    // Table headers
    expect(screen.getByText("Recent Transactions")).toBeInTheDocument();

    // Transaction references
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();

    // Transaction type badges
    expect(screen.getByText("Deposit")).toBeInTheDocument();
    expect(screen.getByText("Payment")).toBeInTheDocument();

    // Status badges
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
    expect(screen.getByText("AWAITING APPROVAL")).toBeInTheDocument();
  });

  it("calls notFound when trust_accounting module is disabled", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: [],
    });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockNotFound).toHaveBeenCalled();
  });

  it("renders pending approvals warning badge when count > 0", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByText("Needs attention")).toBeInTheDocument();
  });

  it("renders trust sub-items in nav with VIEW_TRUST capability", async () => {
    // Import nav items to verify structure
    const { NAV_GROUPS } = await import("@/lib/nav-items");
    const financeGroup = NAV_GROUPS.find((g) => g.id === "finance");

    expect(financeGroup).toBeDefined();

    const trustItems = financeGroup!.items.filter(
      (item) => item.requiredModule === "trust_accounting",
    );

    // Should have parent + 6 sub-items = 7 total trust items
    expect(trustItems.length).toBe(7);

    // All trust items should require VIEW_TRUST capability
    for (const item of trustItems) {
      expect(item.requiredCapability).toBe("VIEW_TRUST");
    }

    // Verify sub-item labels
    const labels = trustItems.map((i) => i.label);
    expect(labels).toContain("Trust Accounting");
    expect(labels).toContain("Transactions");
    expect(labels).toContain("Client Ledgers");
    expect(labels).toContain("Reconciliation");
    expect(labels).toContain("Interest");
    expect(labels).toContain("Investments");
    expect(labels).toContain("Reports");
  });
});
