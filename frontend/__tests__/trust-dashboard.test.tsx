import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatLocalDate } from "@/lib/format";
import { unserializableProps } from "./helpers/rsc-props";

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
const mockFetchTrustPosition = vi.fn();
const mockFetchTrustBalanceTrend = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
  fetchDashboardData: (...args: unknown[]) => mockFetchDashboardData(...args),
  fetchTrustPosition: (...args: unknown[]) => mockFetchTrustPosition(...args),
  fetchTrustBalanceTrend: (...args: unknown[]) => mockFetchTrustBalanceTrend(...args),
}));

// Mock next/navigation (DataTable calls useRouter)
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

// The chart has its own test (trust/trust-balance-trend-chart.test.tsx): a prop-capturing stub.
const chartProps = vi.hoisted(() => [] as Record<string, unknown>[]);
vi.mock("@/components/trust/trust-balance-trend-chart", () => ({
  TrustBalanceTrendChart: (props: Record<string, unknown>) => {
    chartProps.push(props);
    return <div data-testid="trust-balance-trend-stub" />;
  },
}));

// OQ #19: capture the props the server page hands TrustTransactionsTable (a client component)
// while still rendering the real table, so the RSC-boundary guard inspects what the page passes.
const tableProps = vi.hoisted(() => [] as Record<string, unknown>[]);
vi.mock("@/components/trust/trust-transactions-table", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/components/trust/trust-transactions-table")>();
  return {
    ...actual,
    TrustTransactionsTable: (props: React.ComponentProps<typeof actual.TrustTransactionsTable>) => {
      tableProps.push(props as unknown as Record<string, unknown>);
      return <actual.TrustTransactionsTable {...props} />;
    },
  };
});

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
    recentTransactions: [
      {
        id: "tx-1",
        trustAccountId: "acc-1",
        transactionType: "DEPOSIT" as const,
        amount: 50000,
        clientId: "cust-1",
        matterId: null,
        counterpartyClientId: null,
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
        clientId: "cust-1",
        matterId: "proj-1",
        counterpartyClientId: null,
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

function position(overrides: Record<string, unknown> = {}) {
  return {
    trustAccountId: "acc-1",
    accountName: "Main Trust Account",
    bankName: "FNB",
    cashbookBalance: 125000.5,
    clientLedgerTotal: 125000.5,
    difference: 0,
    lastReconciliationPeriodEnd: "2026-10-31",
    lastReconciliationStatus: "COMPLETED",
    lastReconciliationBalanced: true,
    daysSinceReconciliation: 13,
    pendingApprovalCount: 3,
    accounts: [{ id: "acc-1", accountName: "Main Trust Account", isPrimary: true }],
    ...overrides,
  };
}

function trend() {
  return {
    trustAccountId: "acc-1",
    accountName: "Main Trust Account",
    currency: "ZAR",
    asOf: "2026-11-13",
    openingCashbookBalance: 0,
    openingClientLedgerTotal: 0,
    months: [
      {
        month: "2026-11",
        periodEnd: "2026-11-13",
        cashbookBalance: 125000.5,
        clientLedgerTotal: 125000.5,
        partial: true,
      },
    ],
    accounts: [{ id: "acc-1", accountName: "Main Trust Account", isPrimary: true }],
  };
}

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
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
  mockFetchTrustPosition.mockResolvedValue({ state: "ok", data: position() });
  mockFetchTrustBalanceTrend.mockResolvedValue({ state: "ok", data: trend() });
}

function summaryCards() {
  return within(screen.getByTestId("summary-cards"));
}

function tile(label: string) {
  return within(screen.getByRole("group", { name: label }));
}

function tileValue(label: string) {
  return screen
    .getByRole("group", { name: label })
    .querySelector('[data-slot="stat-card-value"]') as HTMLElement;
}

// ── Tests ──────────────────────────────────────────────────────────

describe("TrustAccountingPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    chartProps.length = 0;
    tableProps.length = 0;
  });

  it("renders summary cards with correct data", async () => {
    setupDefaultMocks();
    await renderPage();

    // One h1 (PageHeader), title unchanged.
    expect(screen.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeInTheDocument();

    // The four frozen labels sit inside summary-cards (e2e day-07-14-work-trust).
    for (const label of [
      "Trust Balance",
      "Active Clients",
      "Pending Approvals",
      "Reconciliation",
    ]) {
      expect(summaryCards().getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByTestId("summary-cards")).toHaveTextContent(
      "Main Trust Account cashbook balance"
    );

    // Trust Balance = trust-position cashbook. locale-agnostic: en-ZA "R 125 000,50" or en-US
    expect(tileValue("Trust Balance")).toHaveTextContent(/R[\s\u00A0]125[\s\u00A0,]000[,.]50/);
    expect(mockFetchTrustPosition).toHaveBeenCalledWith("acc-1");
    expect(mockFetchTrustBalanceTrend).toHaveBeenCalledWith("acc-1", 12);

    // Active Clients card (still from the dashboard aggregation)
    expect(tileValue("Active Clients")).toHaveTextContent("8");

    // Pending Approvals card (trust-position)
    expect(tileValue("Pending Approvals")).toHaveTextContent("3");

    // Reconciliation card
    expect(screen.getByText("Reconciliation")).toBeInTheDocument();
  });

  // 643.1 (declared side effect): the Reconciliation tile reads the trust position, so a
  // reconciled account shows "Balanced" and its period end instead of "Not yet reconciled".
  it("a reconciled trust position renders Balanced and the period-end date", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({ state: "ok", data: position() });
    mockFetchTrustBalanceTrend.mockResolvedValue({ state: "absent" });
    await renderPage();

    const cards = within(screen.getByTestId("summary-cards"));
    expect(cards.getByText("Balanced")).toBeInTheDocument();
    expect(cards.getByText(/Last reconciled/)).toHaveTextContent(
      `Last reconciled ${formatLocalDate("2026-10-31")} · 13 days ago`
    );
    expect(cards.queryByText("Not yet reconciled")).not.toBeInTheDocument();
  });

  it("a balanced position shows the client-ledger total with no difference line", async () => {
    setupDefaultMocks();
    await renderPage();

    const balance = tile("Trust Balance");
    expect(balance.getByText(/^Client ledgers R/)).toHaveTextContent(
      /Client ledgers R[\s\u00A0]125[\s\u00A0,]000[,.]50/
    );
    expect(balance.queryByText(/^Difference/)).not.toBeInTheDocument();
    expect(tile("Reconciliation").queryByText(/in draft/)).not.toBeInTheDocument();
  });

  it("an unbalanced draft reconciliation renders Unbalanced, in draft, and the difference line", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({
        clientLedgerTotal: 124999.16,
        difference: 1.34,
        lastReconciliationPeriodEnd: "2026-11-13",
        lastReconciliationStatus: "DRAFT",
        lastReconciliationBalanced: false,
        daysSinceReconciliation: 0,
      }),
    });
    await renderPage();

    const recon = tile("Reconciliation");
    expect(recon.getByText("Unbalanced")).toBeInTheDocument();
    expect(recon.getByText(/Last reconciled/)).toHaveTextContent(
      `Last reconciled ${formatLocalDate("2026-11-13")} · 0 days ago · in draft`
    );
    expect(tile("Trust Balance").getByText(/^Difference/)).toHaveTextContent(
      /Difference R[\s\u00A0]1[,.]34/
    );
  });

  it("a never-reconciled account renders Not yet reconciled", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({
        lastReconciliationPeriodEnd: null,
        lastReconciliationStatus: null,
        lastReconciliationBalanced: null,
        daysSinceReconciliation: null,
      }),
    });
    await renderPage();

    const recon = tile("Reconciliation");
    expect(recon.getByText("Not yet reconciled")).toBeInTheDocument();
    expect(recon.getByText("No reconciliation records found")).toBeInTheDocument();
    expect(recon.queryByText("Balanced")).not.toBeInTheDocument();
  });

  it.each([["error"], ["absent"]])(
    "a trust-position %s read fails the three position tiles but keeps Active Clients",
    async (state) => {
      setupDefaultMocks();
      mockFetchTrustPosition.mockResolvedValue({ state });
      await renderPage();

      for (const label of ["Trust Balance", "Pending Approvals", "Reconciliation"]) {
        expect(tile(label).getByText("Couldn't load")).toBeInTheDocument();
      }
      expect(tile("Active Clients").queryByText("Couldn't load")).not.toBeInTheDocument();
      expect(tileValue("Active Clients")).toHaveTextContent("8");
      // The labels stay (e2e asserts them).
      expect(summaryCards().getByText("Trust Balance")).toBeInTheDocument();
    }
  );

  it("hands the trend chart the trend read as plain data", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByTestId("trust-balance-trend-stub")).toBeInTheDocument();
    expect(chartProps).toHaveLength(1);
    expect(chartProps[0]).toEqual({ trend: trend(), failed: false });
  });

  it.each([
    ["error", { trend: null, failed: true }],
    ["absent", { trend: null, failed: false }],
  ])("a trend %s read reaches the chart as its state", async (state, expected) => {
    setupDefaultMocks();
    mockFetchTrustBalanceTrend.mockResolvedValue({ state });
    await renderPage();

    expect(chartProps[0]).toEqual(expected);
    // The rest of the page still renders.
    expect(screen.getByTestId("transactions-table")).toBeInTheDocument();
  });

  it("passes the client components only serializable props (RSC boundary, OQ #19)", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(tableProps).toHaveLength(1);
    // Non-vacuous: the page really passed the data props and the empty slot.
    expect(tableProps[0]).toEqual(
      expect.objectContaining({ rows: expect.any(Array), currency: "ZAR" })
    );
    expect(tableProps[0].empty).toBeTruthy();
    expect(unserializableProps(tableProps[0], new Set(["empty"]))).toEqual([]);

    expect(chartProps).toHaveLength(1);
    expect(chartProps[0].trend).toBeTruthy();
    expect(unserializableProps(chartProps[0], new Set())).toEqual([]);
  });

  it("renders recent transactions table with data", async () => {
    setupDefaultMocks();
    await renderPage();

    // Table headers
    expect(screen.getByText("Recent Transactions")).toBeInTheDocument();
    // Frozen testid on the <table> element itself
    expect(screen.getByTestId("transactions-table").tagName).toBe("TABLE");

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

  it("renders the empty recent-transactions state without a table", async () => {
    setupDefaultMocks();
    mockFetchDashboardData.mockResolvedValue({ ...defaultDashboardData(), recentTransactions: [] });
    await renderPage();

    expect(screen.getByText("No transactions recorded yet")).toBeInTheDocument();
    expect(screen.queryByTestId("transactions-table")).not.toBeInTheDocument();
  });

  // G88-F23 (Phase 89 630C): REFUND moves money out of trust — outflow colour + arrow on the
  // dashboard's recent transactions, same as the transactions and client-ledger pages. 643A
  // restyled the colour onto a token, so the case asserts the stated direction instead.
  it("renders REFUND as an outflow in recent transactions (no green, up-right arrow)", async () => {
    setupDefaultMocks();
    const data = defaultDashboardData();
    const refund = {
      ...data.recentTransactions[0],
      id: "tx-3",
      transactionType: "REFUND" as const,
      amount: 1000,
      reference: "REF-001",
      description: "Refund to client",
    };
    mockFetchDashboardData.mockResolvedValue({
      ...data,
      recentTransactions: [...data.recentTransactions, refund],
    });
    await renderPage();

    const amountSpan = (reference: string) =>
      within(screen.getByText(reference).closest("tr") as HTMLElement)
        .getAllByRole("cell")[3]
        .querySelector("span") as HTMLElement;

    const refundAmount = amountSpan("REF-001");
    expect(refundAmount).toHaveAttribute("data-direction", "out");
    expect(refundAmount.className).not.toContain("text-success-foreground");
    expect(refundAmount.querySelector(".lucide-arrow-up-right")).not.toBeNull();
    expect(refundAmount.querySelector(".lucide-arrow-down-left")).toBeNull();

    const deposit = amountSpan("DEP-001");
    expect(deposit).toHaveAttribute("data-direction", "in");
    expect(deposit.className).toContain("text-success-foreground");
    expect(deposit.querySelector(".lucide-arrow-down-left")).not.toBeNull();
  });

  it("renders Module Not Available when trust_accounting module is disabled", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: [],
    });

    await renderPage();

    expect(screen.getByText("Module Not Available")).toBeInTheDocument();
    expect(
      screen.getByText("The Trust Accounting module is not enabled for your organization.")
    ).toBeInTheDocument();
    expect(mockNotFound).not.toHaveBeenCalled();
  });

  it("renders No Trust Accounts without reading the trust position when no account exists", async () => {
    setupDefaultMocks();
    mockFetchTrustAccounts.mockResolvedValue([]);
    await renderPage();

    expect(screen.getByText("No Trust Accounts")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Account" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Record Transaction" })).not.toBeInTheDocument();
    expect(mockFetchTrustPosition).not.toHaveBeenCalled();
    expect(mockFetchTrustBalanceTrend).not.toHaveBeenCalled();
  });

  it("keeps the header links and shows the error card when the dashboard read fails", async () => {
    setupDefaultMocks();
    mockFetchDashboardData.mockRejectedValue(new Error("boom"));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    await renderPage();

    expect(
      screen.getByText("Unable to load trust account data. Please try again later.")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Client Ledgers" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Record Transaction" })).toBeInTheDocument();
    expect(screen.queryByTestId("summary-cards")).not.toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("renders pending approvals warning badge when count > 0", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(tile("Pending Approvals").getByText("Needs attention")).toBeInTheDocument();
  });

  it("renders no warning badge when nothing awaits approval", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({ pendingApprovalCount: 0 }),
    });
    await renderPage();

    expect(tileValue("Pending Approvals")).toHaveTextContent("0");
    expect(screen.queryByText("Needs attention")).not.toBeInTheDocument();
  });

  it("renders trust sub-items in nav with VIEW_TRUST capability", async () => {
    // Import nav items to verify structure
    const { NAV_GROUPS } = await import("@/lib/nav-items");
    const trustZone = NAV_GROUPS.find((g) => g.id === "trust");

    expect(trustZone).toBeDefined();

    const trustItems = trustZone!.items.filter(
      (item) => item.requiredModule === "trust_accounting"
    );

    // Trust zone — 7 items
    expect(trustItems.length).toBe(7);

    // All trust items should require VIEW_TRUST capability
    for (const item of trustItems) {
      expect(item.requiredCapability).toBe("VIEW_TRUST");
    }

    // Verify sub-item labels
    const labels = trustItems.map((i) => i.label);
    expect(labels).toContain("Overview");
    expect(labels).toContain("Transactions");
    expect(labels).toContain("Client ledgers");
    expect(labels).toContain("Reconciliation");
    expect(labels).toContain("Interest");
    expect(labels).toContain("Investments");
    expect(labels).toContain("Trust reports");
  });
});
